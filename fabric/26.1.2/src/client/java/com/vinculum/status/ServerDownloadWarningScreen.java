package com.vinculum.status;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Requires an explicit, per-file decision before unverified JARs are fetched from a game server. */
final class ServerDownloadWarningScreen extends Screen {
	private final Screen parent;
	private final String serverAddress;
	private final List<ModEntry> mods;
	private final Runnable trustAndDownload;
	private final Runnable cancel;
	private ModList modList;

	ServerDownloadWarningScreen(Screen parent, String serverAddress, List<ModEntry> mods, Runnable trustAndDownload, Runnable cancel) {
		super(Component.translatable("vinculum.warning.title"));
		this.parent = parent;
		this.serverAddress = serverAddress;
		this.mods = List.copyOf(mods);
		this.trustAndDownload = trustAndDownload;
		this.cancel = cancel;
	}

	@Override
	protected void init() {
		this.modList = this.addRenderableWidget(new ModList(this.minecraft, this.width, Math.max(40, this.height - 136), 78, this.mods));
		this.addRenderableWidget(Button.builder(Component.translatable("vinculum.warning.back"), button -> this.onClose())
			.bounds(this.width / 2 - 204, this.height - 32, 200, 20)
			.build());
		this.addRenderableWidget(Button.builder(Component.translatable("vinculum.warning.trust_download"), button -> {
			this.minecraft.setScreen(this.parent);
			this.trustAndDownload.run();
		})
			.bounds(this.width / 2 + 4, this.height - 32, 200, 20)
			.build());
	}

	@Override
	protected void repositionElements() {
		this.modList.updateSizeAndPosition(this.width, Math.max(40, this.height - 136), 0, 78);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xFF101010);
		this.minecraft.gui.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFF5555);
		graphics.centeredText(this.font, Component.translatable("vinculum.warning.security_risk"), this.width / 2, 31, 0xFFFFAA00);
		graphics.centeredText(this.font, Component.translatable("vinculum.warning.only_if_trusted"), this.width / 2, 44, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("vinculum.warning.direct_files", this.mods.size(), this.serverAddress), this.width / 2, 61, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
		this.cancel.run();
	}

	private static String formatSize(long bytes) {
		if (bytes < 0) {
			return Component.translatable("vinculum.warning.unknown_size").getString();
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		double value = bytes;
		String[] units = {"KiB", "MiB", "GiB"};
		int unit = -1;
		do {
			value /= 1024.0;
			unit++;
		} while (value >= 1024.0 && unit < units.length - 1);
		return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}

	private static final class ModList extends ObjectSelectionList<ModList.Entry> {
		private ModList(Minecraft minecraft, int width, int height, int y, List<ModEntry> mods) {
			super(minecraft, width, height, y, 42);
			mods.forEach(mod -> this.addEntry(new Entry(mod)));
		}

		@Override
		public int getRowWidth() {
			return Math.min(520, this.width - 32);
		}

		private static final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final ModEntry mod;

			private Entry(ModEntry mod) {
				this.mod = mod;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
				Minecraft minecraft = Minecraft.getInstance();
				String authors = this.mod.authors().isEmpty()
					? Component.translatable("vinculum.warning.unknown_author").getString()
					: String.join(", ", this.mod.authors());
				String filename = this.mod.fileName().isBlank() ? this.mod.id() + "-" + this.mod.version() + ".jar" : this.mod.fileName();
				graphics.text(minecraft.font, Component.literal(this.mod.name() + "  [" + this.mod.id() + "]"), this.getContentX() + 4, this.getContentY() + 3, 0xFFFFFFFF);
				graphics.text(minecraft.font, Component.translatable("vinculum.warning.metadata", this.mod.version(), authors, this.mod.environment()), this.getContentX() + 4, this.getContentY() + 15, 0xFFCCCCCC);
				graphics.text(minecraft.font, Component.translatable("vinculum.warning.file_metadata", filename, formatSize(this.mod.fileSize())), this.getContentX() + 4, this.getContentY() + 27, 0xFFAAAAAA);
			}

			@Override
			public Component getNarration() {
				return CommonComponents.joinForNarration(
					Component.literal(this.mod.name()),
					Component.literal(this.mod.version()),
					Component.literal(String.join(", ", this.mod.authors())),
					Component.literal(formatSize(this.mod.fileSize()))
				);
			}
		}
	}
}
