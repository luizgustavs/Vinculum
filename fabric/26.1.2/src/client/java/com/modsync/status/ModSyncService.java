package com.modsync.status;

import com.modsync.ModSync;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ModSyncService {
	private ModSyncService() {
	}

	static CompletableFuture<Result> syncNecessary(ServerModInfo serverInfo, String serverAddress) {
		return syncNecessary(serverInfo, serverAddress, result -> {
		});
	}

	static CompletableFuture<Result> syncNecessary(ServerModInfo serverInfo, String serverAddress, Consumer<Result> progress) {
		ModSync.LOGGER.info("Starting necessary sync for server {} with {} advertised mods, transfer port {}, server transfers {}", serverAddress, serverInfo.mods().size(), serverInfo.transferPort(), serverInfo.allowServerTransfers());
		return CompletableFuture.supplyAsync(() -> plan(serverInfo, Mode.NECESSARY))
			.thenCompose(plan -> runNecessary(plan, serverInfo.minecraftVersion(), serverAddress, serverInfo.transferPort(), serverInfo.allowServerTransfers(), progress));
	}

	static CompletableFuture<Result> fullSync(ServerModInfo serverInfo, String serverAddress) {
		return fullSync(serverInfo, serverAddress, result -> {
		});
	}

	static CompletableFuture<Result> fullSync(ServerModInfo serverInfo, String serverAddress, Consumer<Result> progress) {
		ModSync.LOGGER.info("Starting full sync for server {} with {} advertised mods, transfer port {}, server transfers {}", serverAddress, serverInfo.mods().size(), serverInfo.transferPort(), serverInfo.allowServerTransfers());
		if (!serverInfo.allowServerTransfers()) {
			return CompletableFuture.completedFuture(new Result(false, 0, 0, "server_transfers_disabled", ""));
		}
		return CompletableFuture.supplyAsync(() -> plan(serverInfo, Mode.FULL))
			.thenCompose(plan -> runFull(plan, serverAddress, serverInfo.transferPort(), progress));
	}

	private static CompletableFuture<Result> runNecessary(Plan plan, String minecraftVersion, String serverAddress, int transferPort, boolean allowServerTransfers, Consumer<Result> progress) {
		ModSync.LOGGER.info("Necessary sync plan: {} downloads, {} mismatches, {} extras", plan.downloads().size(), plan.mismatches().size(), plan.extras().size());
		return CompletableFuture.supplyAsync(() -> backupMismatches(plan))
			.thenCompose(result -> {
				if (!result.success()) {
					progress.accept(result);
					return CompletableFuture.completedFuture(result);
				}
				return sequence(plan.downloads(), progress, mod -> ModDownloader.downloadFromModrinth(mod, minecraftVersion)
					.thenCompose(modrinth -> {
						if (modrinth.success()) {
							return CompletableFuture.completedFuture(modrinth);
						}
						ModSync.LOGGER.info("Modrinth did not provide {} {} during necessary sync ({}: {}). Falling back to server {}",
							mod.id(), mod.version(), modrinth.messageKey(), emptyToFallback(modrinth.detail(), "no detail"), serverAddress);
						if (!allowServerTransfers) {
							ModSync.LOGGER.info("Server download fallback is disabled for {} {}", mod.id(), mod.version());
							return CompletableFuture.completedFuture(new ModDownloader.Result(false, "server_transfers_disabled", ""));
						}
						return ModDownloader.downloadFromServer(mod, serverAddress, transferPort);
					}));
			});
	}

	private static CompletableFuture<Result> runFull(Plan plan, String serverAddress, int transferPort, Consumer<Result> progress) {
		ModSync.LOGGER.info("Full sync plan: {} downloads, {} mismatches, {} extras", plan.downloads().size(), plan.mismatches().size(), plan.extras().size());
		return CompletableFuture.supplyAsync(() -> backupAll(plan))
			.thenCompose(result -> result.success()
				? sequence(plan.downloads(), progress, mod -> ModDownloader.downloadFromServer(mod, serverAddress, transferPort))
				: failed(result, progress));
	}

	private static Plan plan(ServerModInfo serverInfo, Mode mode) {
		Map<String, ModEntry> serverMods = serverInfo.mods().stream()
			.collect(Collectors.toMap(ModEntry::id, Function.identity(), (first, second) -> first));
		Map<String, ModEntry> localMods = ServerModInfo.local().mods().stream()
			.collect(Collectors.toMap(ModEntry::id, Function.identity(), (first, second) -> first));

		List<ModEntry> downloads = new ArrayList<>();
		List<String> mismatches = new ArrayList<>();
		for (ModEntry serverMod : serverInfo.mods()) {
			ModEntry localMod = localMods.get(serverMod.id());
			boolean missing = localMod == null;
			boolean mismatch = localMod != null && !serverMod.version().equals(localMod.version());
			if (mismatch) {
				mismatches.add(serverMod.id());
			}
			if (mode == Mode.FULL && (missing || mismatch)) {
				downloads.add(serverMod);
			}
			if (mode == Mode.NECESSARY && ((missing && !serverMod.clientOnly()) || mismatch)) {
				downloads.add(serverMod);
			}
		}

		List<String> extras = mode == Mode.FULL
			? localMods.keySet().stream().filter(id -> !serverMods.containsKey(id)).toList()
			: List.of();
		ModSync.LOGGER.info("Built {} sync plan. downloads={}, mismatches={}, extras={}", mode, ids(downloads), mismatches, extras);
		return new Plan(downloads, mismatches, extras);
	}

	private static Result backupMismatches(Plan plan) {
		for (String modId : plan.mismatches()) {
			try {
				ModSync.LOGGER.info("Backing up mismatched mod {}", modId);
				ModFileManager.backup(modId);
			} catch (IOException exception) {
				ModSync.LOGGER.error("Failed to back up mismatched mod {}", modId, exception);
				return new Result(false, 0, plan.downloads().size(), "backup_failed", detail(exception));
			}
		}
		return new Result(true, 0, plan.downloads().size(), "backup_done", "");
	}

	private static Result backupAll(Plan plan) {
		for (String modId : plan.extras()) {
			try {
				ModSync.LOGGER.info("Backing up extra local mod {}", modId);
				ModFileManager.backup(modId);
			} catch (IOException exception) {
				ModSync.LOGGER.error("Failed to back up extra local mod {}", modId, exception);
				return new Result(false, 0, plan.downloads().size(), "backup_failed", detail(exception));
			}
		}
		return backupMismatches(plan);
	}

	private static CompletableFuture<Result> failed(Result result, Consumer<Result> progress) {
		progress.accept(result);
		return CompletableFuture.completedFuture(result);
	}

	private static CompletableFuture<Result> sequence(List<ModEntry> mods, Consumer<Result> progress, Downloader downloader) {
		CompletableFuture<Result> result = CompletableFuture.completedFuture(new Result(true, 0, mods.size(), "sync_done", ""));
		progress.accept(new Result(true, 0, mods.size(), "sync_done", ""));
		for (ModEntry mod : mods) {
			result = result.thenCompose(previous -> {
				if (!previous.success()) {
					return CompletableFuture.completedFuture(previous);
				}
				ModSync.LOGGER.info("Syncing mod {} {} ({}/{})", mod.id(), mod.version(), previous.completed() + 1, mods.size());
				return downloader.download(mod).thenApply(download -> {
					Result next = download.success()
						? new Result(true, previous.completed() + 1, mods.size(), "sync_done", download.detail())
						: new Result(false, previous.completed(), mods.size(), download.messageKey(), download.detail());
					progress.accept(next);
					return next;
				});
			});
		}
		return result;
	}

	private static List<String> ids(List<ModEntry> mods) {
		return mods.stream().map(ModEntry::id).toList();
	}

	private static String detail(Throwable throwable) {
		return emptyToFallback(throwable.getMessage(), throwable.getClass().getSimpleName());
	}

	private static String emptyToFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private enum Mode { NECESSARY, FULL }

	private interface Downloader {
		CompletableFuture<ModDownloader.Result> download(ModEntry mod);
	}

	record Result(boolean success, int completed, int total, String messageKey, String detail) {
	}

	private record Plan(List<ModEntry> downloads, List<String> mismatches, List<String> extras) {
	}
}
