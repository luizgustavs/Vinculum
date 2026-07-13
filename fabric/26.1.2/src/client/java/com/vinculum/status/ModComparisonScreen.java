package com.vinculum.status;

import com.vinculum.Vinculum;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ModComparisonScreen extends Screen {
	private static final int MATCH_COLOR = 0xFF55FF55;
	private static final int MISSING_COLOR = 0xFFFF5555;
	private static final int CLIENT_ONLY_COLOR = 0xFF55AAFF;
	private static final int VERSION_MISMATCH_COLOR = 0xFFFFFF55;
	private final Screen parent;
	private final ServerModInfo serverInfo;
	private final String serverAddress;
	private final String minecraftVersion;
	private final List<Comparison> comparisons;
	private ModList modList;
	private Button necessarySyncButton;
	private Button fullSyncButton;
	private Button backButton;
	private Button closeGameButton;
	private Component syncStatus = Component.empty();
	private int progressCompleted;
	private int progressTotal;
	private boolean showProgress;
	private boolean syncing;

	private ModComparisonScreen(Screen parent, ServerModInfo serverInfo, String serverAddress, List<Comparison> comparisons) {
		super(Component.translatable("vinculum.mods.title"));
		this.parent = parent;
		this.serverInfo = serverInfo;
		this.serverAddress = serverAddress;
		this.minecraftVersion = serverInfo.minecraftVersion();
		this.comparisons = comparisons;
	}

	public static boolean openIfNeeded(Screen parent, ServerModInfo serverInfo, String serverAddress) {
		Map<String, ModEntry> localMods = ServerModInfo.local().mods().stream()
			.collect(Collectors.toMap(ModEntry::id, Function.identity(), (first, second) -> first));
		List<Comparison> comparisons = serverInfo.mods().stream()
			.map(serverMod -> new Comparison(serverMod, localMods.get(serverMod.id())))
			.toList();
		if (comparisons.stream().noneMatch(Comparison::blocksJoin)) {
			return false;
		}

		Minecraft.getInstance().setScreen(new ModComparisonScreen(parent, serverInfo, serverAddress, comparisons));
		return true;
	}

	@Override
	protected void init() {
		this.modList = this.addRenderableWidget(new ModList(this, this.minecraft, this.width, this.height - 129, 54, 32, this.comparisons));
		this.necessarySyncButton = this.addRenderableWidget(Button.builder(Component.translatable("vinculum.mods.sync_necessary"), button -> this.syncNecessary())
			.bounds(this.width / 2 - 200, this.height - 60, 196, 20)
			.build());
		this.fullSyncButton = this.addRenderableWidget(Button.builder(Component.translatable("vinculum.mods.full_sync"), button -> this.fullSync())
			.bounds(this.width / 2 + 4, this.height - 60, 196, 20)
			.build());
		this.backButton = this.addRenderableWidget(Button.builder(Component.translatable("vinculum.mods.back"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 36, 200, 20)
			.build());
		this.closeGameButton = this.addRenderableWidget(Button.builder(Component.translatable("vinculum.mods.close_game"), button -> this.minecraft.stop())
			.bounds(this.width / 2 + 4, this.height - 36, 196, 20)
			.build());
		this.updateBottomButtons();
		this.checkModrinthAvailability();
	}

	@Override
	protected void repositionElements() {
		this.modList.updateSizeAndPosition(this.width, this.height - 129, 0, 54);
		this.updateBottomButtons();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xFF101010);
		this.minecraft.gui.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		long matched = this.comparisons.stream().filter(Comparison::satisfied).count();
		graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
		graphics.centeredText(
			this.font,
			this.syncStatus.getString().isBlank() ? Component.translatable("vinculum.mods.subtitle", matched, this.comparisons.size()) : this.syncStatus,
			this.width / 2,
			34,
			this.syncStatus.getString().isBlank() ? MISSING_COLOR : 0xFFFFFFFF
		);
		this.renderProgress(graphics);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private void download(Comparison comparison) {
		if (!comparison.canDownload(this.serverInfo.allowServerTransfers())) {
			return;
		}
		if (comparison.modrinthState == ModrinthState.UNAVAILABLE) {
			this.minecraft.setScreen(new ServerDownloadWarningScreen(
				this,
				this.serverAddress,
				List.of(comparison.serverMod()),
				() -> this.downloadFromServer(comparison),
				() -> {
				}
			));
			return;
		}
		comparison.state = DownloadState.DOWNLOADING;
		this.startProgress(0, 1);
		ModDownloader.download(comparison.serverMod(), this.minecraftVersion).thenAccept(result -> this.minecraft.execute(() -> {
			comparison.detail = result.detail();
			comparison.state = result.success() ? DownloadState.DOWNLOADED : DownloadState.FAILED;
			this.updateProgress(result.success() ? 1 : 0, 1);
			this.updateBottomButtons();
		}));
	}

	private void downloadFromServer(Comparison comparison) {
		comparison.state = DownloadState.DOWNLOADING;
		this.startProgress(0, 1);
		ModDownloader.downloadFromServer(comparison.serverMod(), this.serverAddress, this.serverInfo.transferPort()).thenAccept(result -> this.minecraft.execute(() -> {
			comparison.detail = result.detail();
			comparison.state = result.success() ? DownloadState.DOWNLOADED : DownloadState.FAILED;
			this.updateProgress(result.success() ? 1 : 0, 1);
			this.updateBottomButtons();
		}));
	}

	private void syncNecessary() {
		this.prepareSync(false);
	}

	private void fullSync() {
		this.prepareSync(true);
	}

	private void prepareSync(boolean fullSync) {
		if (this.syncing) {
			return;
		}
		this.syncing = true;
		this.syncStatus = Component.translatable("vinculum.mods.checking_sources");
		this.showProgress = false;
		this.updateBottomButtons();
		VinculumService.serverDownloadsFor(this.serverInfo, fullSync).thenAccept(serverMods -> this.minecraft.execute(() -> {
			if (!serverMods.isEmpty() && !this.serverInfo.allowServerTransfers()) {
				this.syncing = false;
				this.syncStatus = Component.translatable("vinculum.mods.sync_failed", Component.translatable("vinculum.mods.server_transfers_disabled"));
				this.updateBottomButtons();
				return;
			}
			Set<String> trustedMods = serverMods.stream().map(ModEntry::id).collect(Collectors.toUnmodifiableSet());
			if (serverMods.isEmpty()) {
				this.runSync(fullSync, trustedMods);
				return;
			}
			this.minecraft.setScreen(new ServerDownloadWarningScreen(
				this,
				this.serverAddress,
				serverMods,
				() -> this.runSync(fullSync, trustedMods),
				this::cancelPreparedSync
			));
		})).exceptionally(throwable -> {
			Vinculum.LOGGER.error("Could not determine sync download sources", throwable);
			this.minecraft.execute(() -> {
				this.syncing = false;
				this.syncStatus = Component.translatable("vinculum.mods.sync_failed", detail(throwable));
				this.updateBottomButtons();
			});
			return null;
		});
	}

	private void cancelPreparedSync() {
		this.syncing = false;
		this.syncStatus = Component.empty();
		this.showProgress = false;
		this.updateBottomButtons();
	}

	private void runSync(boolean fullSync, Set<String> trustedServerMods) {
		this.syncStatus = Component.translatable("vinculum.mods.syncing");
		this.startProgress(0, this.expectedDownloads(fullSync));
		this.updateBottomButtons();
		Consumer<VinculumService.Result> progress = result -> this.minecraft.execute(() -> this.updateProgress(result.completed(), result.total()));
		java.util.concurrent.CompletableFuture<VinculumService.Result> task = fullSync
			? VinculumService.fullSync(this.serverInfo, this.serverAddress, trustedServerMods, progress)
			: VinculumService.syncNecessary(this.serverInfo, this.serverAddress, trustedServerMods, progress);
		task.exceptionally(throwable -> {
			Vinculum.LOGGER.error("Sync task failed unexpectedly", throwable);
			return new VinculumService.Result(false, 0, 0, "failed", detail(throwable));
		}).thenAccept(result -> this.minecraft.execute(() -> {
			this.syncing = false;
			this.syncStatus = result.success()
				? Component.translatable("vinculum.mods.sync_done", result.completed(), result.total())
				: syncFailed(result);
			if (result.success()) {
				this.comparisons.stream()
					.filter(comparison -> comparison.downloadRequired() || comparison.versionMismatch() || (fullSync && comparison.clientOnlyMissing()))
					.forEach(Comparison::markDownloaded);
			}
			this.updateBottomButtons();
		}));
	}

	private int expectedDownloads(boolean includeClientOnlyMissing) {
		return (int) this.comparisons.stream()
			.filter(comparison -> comparison.downloadRequired() || comparison.versionMismatch() || (includeClientOnlyMissing && comparison.clientOnlyMissing()))
			.count();
	}

	private void startProgress(int completed, int total) {
		this.showProgress = total > 0;
		this.updateProgress(completed, total);
	}

	private void updateProgress(int completed, int total) {
		this.progressCompleted = Math.max(0, completed);
		this.progressTotal = Math.max(0, total);
		this.showProgress = this.progressTotal > 0;
	}

	private void renderProgress(GuiGraphicsExtractor graphics) {
		if (!this.showProgress || this.progressTotal <= 0) {
			return;
		}

		int barWidth = Math.min(360, this.width - 40);
		int left = (this.width - barWidth) / 2;
		int top = 43;
		int right = left + barWidth;
		int bottom = top + 9;
		float progress = Math.min(1.0F, (float) this.progressCompleted / (float) this.progressTotal);
		int filledRight = left + Math.round(barWidth * progress);
		int fillColor = this.progressCompleted >= this.progressTotal ? MATCH_COLOR : 0xFF55AAFF;

		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF555555);
		graphics.fill(left, top, right, bottom, 0xFF181818);
		graphics.fill(left, top, filledRight, bottom, fillColor);
		graphics.fill(left, top, filledRight, top + 2, 0x66FFFFFF);
		graphics.centeredText(
			this.font,
			Component.translatable("vinculum.mods.progress", this.progressCompleted, this.progressTotal, Math.round(progress * 100.0F)),
			this.width / 2,
			top,
			0xFFFFFFFF
		);
	}

	private static String detail(Throwable throwable) {
		String message = throwable.getMessage();
		return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
	}

	private void checkModrinthAvailability() {
		this.comparisons.stream()
			.filter(Comparison::shouldCheckModrinth)
			.forEach(comparison -> {
				comparison.modrinthState = ModrinthState.CHECKING;
				ModDownloader.existsOnModrinth(comparison.serverMod(), this.minecraftVersion).thenAccept(available -> Minecraft.getInstance().execute(() -> {
					comparison.modrinthState = available ? ModrinthState.AVAILABLE : ModrinthState.UNAVAILABLE;
					this.updateBottomButtons();
				}));
			});
	}

	private void updateBottomButtons() {
		if (this.backButton == null || this.closeGameButton == null || this.necessarySyncButton == null || this.fullSyncButton == null) {
			return;
		}
		this.necessarySyncButton.setPosition(this.width / 2 - 200, this.height - 60);
		this.fullSyncButton.setPosition(this.width / 2 + 4, this.height - 60);
		this.necessarySyncButton.active = !this.syncing;
		this.fullSyncButton.active = !this.syncing;
		boolean allDownloaded = this.comparisons.stream().filter(Comparison::downloadRequired).allMatch(Comparison::downloaded)
			&& this.comparisons.stream().anyMatch(Comparison::downloaded);
		this.closeGameButton.visible = allDownloaded;
		if (allDownloaded) {
			this.backButton.setWidth(196);
			this.backButton.setPosition(this.width / 2 - 200, this.height - 36);
			this.closeGameButton.setPosition(this.width / 2 + 4, this.height - 36);
		} else {
			this.backButton.setWidth(200);
			this.backButton.setPosition(this.width / 2 - 100, this.height - 36);
		}
	}

	private static Component syncFailed(VinculumService.Result result) {
		if ("server_transfers_disabled".equals(result.messageKey())) {
			return Component.translatable("vinculum.mods.sync_failed", Component.translatable("vinculum.mods.server_transfers_disabled"));
		}
		if ("server_download_not_trusted".equals(result.messageKey())) {
			return Component.translatable("vinculum.mods.sync_failed", Component.translatable("vinculum.mods.server_download_not_trusted"));
		}
		return Component.translatable("vinculum.mods.sync_failed", result.detail());
	}

	private static final class Comparison {
		private final ModEntry serverMod;
		private final @Nullable ModEntry localMod;
		private DownloadState state = DownloadState.IDLE;
		private ModrinthState modrinthState = ModrinthState.UNKNOWN;
		private String detail = "";

		private Comparison(ModEntry serverMod, @Nullable ModEntry localMod) {
			this.serverMod = serverMod;
			this.localMod = localMod;
		}

		private ModEntry serverMod() {
			return this.serverMod;
		}

		private @Nullable ModEntry localMod() {
			return this.localMod;
		}

		private boolean matches() {
			return this.localMod != null && this.serverMod.version().equals(this.localMod.version());
		}

		private boolean satisfied() {
			return this.matches() || this.downloaded() || this.clientOnlyMissing();
		}

		private boolean blocksJoin() {
			return !this.satisfied();
		}

		private boolean missing() {
			return this.localMod == null;
		}

		private boolean clientOnlyMissing() {
			return this.serverMod.clientOnly() && this.missing();
		}

		private boolean versionMismatch() {
			return this.localMod != null && !this.matches();
		}

		private boolean downloadRequired() {
			return this.missing() && !this.clientOnlyMissing();
		}

		private boolean downloadableMissing() {
			return this.missing();
		}

		private boolean canDownload(boolean allowServerTransfers) {
			return this.downloadableMissing()
				&& (this.modrinthState == ModrinthState.AVAILABLE || (this.modrinthState == ModrinthState.UNAVAILABLE && allowServerTransfers))
				&& this.state != DownloadState.DOWNLOADING && this.state != DownloadState.DOWNLOADED;
		}

		private boolean downloaded() {
			return this.state == DownloadState.DOWNLOADED;
		}

		private void markDownloaded() {
			this.state = DownloadState.DOWNLOADED;
		}

		private boolean shouldCheckModrinth() {
			return this.downloadableMissing() && this.modrinthState == ModrinthState.UNKNOWN;
		}

		private boolean showDownloadButton() {
			return this.downloadableMissing() && (this.modrinthState == ModrinthState.AVAILABLE || this.modrinthState == ModrinthState.UNAVAILABLE || this.state == DownloadState.DOWNLOADING || this.state == DownloadState.DOWNLOADED || this.state == DownloadState.FAILED);
		}
	}

	private enum DownloadState { IDLE, DOWNLOADING, DOWNLOADED, FAILED }

	private enum ModrinthState { UNKNOWN, CHECKING, AVAILABLE, UNAVAILABLE }

	private static final class ModList extends ObjectSelectionList<ModList.Entry> {
		private ModList(ModComparisonScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight, List<Comparison> comparisons) {
			super(minecraft, width, height, y, itemHeight);
			comparisons.forEach(comparison -> this.addEntry(new Entry(screen, comparison)));
		}

		@Override
		public int getRowWidth() {
			return Math.min(430, this.width - 40);
		}

		private static final class Entry extends ObjectSelectionList.Entry<Entry> {
			private static final int BUTTON_WIDTH = 82;
			private final ModComparisonScreen screen;
			private final Comparison comparison;

			private Entry(ModComparisonScreen screen, Comparison comparison) {
				this.screen = screen;
				this.comparison = comparison;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
				Minecraft minecraft = Minecraft.getInstance();
				int color = this.color();
				ModEntry serverMod = this.comparison.serverMod();
				int textRight = this.comparison.showDownloadButton() ? this.getContentRight() - BUTTON_WIDTH - 8 : this.getContentRight() - 4;
				graphics.text(minecraft.font, Component.literal(serverMod.name() + " (" + serverMod.id() + ")"), this.getContentX() + 4, this.getContentY() + 3, color);

				Component versionText;
				Component status;
				if (this.comparison.matches()) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.installed");
				} else if (this.comparison.clientOnlyMissing()) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.client_only");
				} else if (this.comparison.downloaded()) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.downloaded");
					color = MATCH_COLOR;
				} else if (this.comparison.state == DownloadState.DOWNLOADING) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.downloading");
				} else if (this.comparison.state == DownloadState.FAILED) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.download_failed");
				} else if (this.comparison.modrinthState == ModrinthState.CHECKING) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.checking_modrinth");
				} else if (this.comparison.modrinthState == ModrinthState.UNAVAILABLE) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.not_on_modrinth");
				} else if (this.comparison.localMod() == null) {
					versionText = Component.translatable("vinculum.mods.server_version", serverMod.version());
					status = Component.translatable("vinculum.mods.missing");
				} else {
					versionText = Component.translatable("vinculum.mods.version_comparison", serverMod.version(), this.comparison.localMod().version());
					status = Component.translatable("vinculum.mods.wrong_version");
				}

				graphics.text(minecraft.font, versionText, this.getContentX() + 4, this.getContentY() + 16, 0xFFAAAAAA);
				graphics.text(minecraft.font, status, textRight - minecraft.font.width(status), this.getContentY() + 9, color);
				if (this.comparison.showDownloadButton()) {
					this.renderDownloadButton(graphics, mouseX, mouseY);
				}
			}

			private int color() {
				if (this.comparison.matches() || this.comparison.downloaded()) {
					return MATCH_COLOR;
				}
				if (this.comparison.clientOnlyMissing()) {
					return CLIENT_ONLY_COLOR;
				}
				if (this.comparison.versionMismatch()) {
					return VERSION_MISMATCH_COLOR;
				}
				return MISSING_COLOR;
			}

			private void renderDownloadButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
				int left = this.getContentRight() - BUTTON_WIDTH - 3;
				int top = this.getContentY() + 5;
				int right = this.getContentRight() - 3;
				int bottom = top + 20;
				boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
				boolean enabled = this.comparison.canDownload(this.screen.serverInfo.allowServerTransfers());
				graphics.fill(left, top, right, bottom, enabled ? (hovered ? 0xFF8A8A8A : 0xFF6A6A6A) : 0xFF3A3A3A);
				graphics.fill(left + 1, top + 1, right - 1, bottom - 1, enabled ? 0xFF202020 : 0xFF181818);
				Component label = Component.translatable(this.downloadLabelKey());
				graphics.centeredText(Minecraft.getInstance().font, label, (left + right) / 2, top + 6, enabled ? 0xFFFFFFFF : 0xFFAAAAAA);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				int left = this.getContentRight() - BUTTON_WIDTH - 3;
				int top = this.getContentY() + 5;
				if (event.button() == 0 && event.x() >= left && event.x() < this.getContentRight() - 3 && event.y() >= top && event.y() < top + 20 && this.comparison.canDownload(this.screen.serverInfo.allowServerTransfers())) {
					this.screen.download(this.comparison);
					return true;
				}
				return super.mouseClicked(event, doubleClick);
			}

			private String downloadLabelKey() {
				return switch (this.comparison.state) {
					case DOWNLOADING -> "vinculum.mods.downloading";
					case DOWNLOADED -> "vinculum.mods.downloaded";
					case FAILED -> "vinculum.mods.retry";
					case IDLE -> this.comparison.modrinthState == ModrinthState.UNAVAILABLE ? "vinculum.mods.download_from_server" : "vinculum.mods.download";
				};
			}

			@Override
			public Component getNarration() {
				return CommonComponents.joinForNarration(
					Component.literal(this.comparison.serverMod().name()),
					Component.translatable(this.statusKey())
				);
			}

			private String statusKey() {
				if (this.comparison.matches()) {
					return "vinculum.mods.installed";
				}
				if (this.comparison.clientOnlyMissing()) {
					return "vinculum.mods.client_only";
				}
				return this.comparison.localMod() == null ? "vinculum.mods.missing" : "vinculum.mods.wrong_version";
			}
		}
	}
}
