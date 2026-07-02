package com.modsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ModSyncConfig {
	public static final int DEFAULT_TRANSFER_PORT = 9123;
	public static final boolean DEFAULT_ALLOW_SERVER_TRANSFERS = true;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config config;

	private ModSyncConfig() {
	}

	public static synchronized Config get() {
		if (config == null) {
			config = load();
		}
		return config;
	}

	private static Config load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("modsync.json");
		Config loaded = new Config(DEFAULT_TRANSFER_PORT, DEFAULT_ALLOW_SERVER_TRANSFERS);
		if (Files.exists(path)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
				loaded = new Config(
					validPort(json.has("transferPort") ? json.get("transferPort").getAsInt() : DEFAULT_TRANSFER_PORT, DEFAULT_TRANSFER_PORT),
					json.has("allowServerTransfers") ? json.get("allowServerTransfers").getAsBoolean() : DEFAULT_ALLOW_SERVER_TRANSFERS
				);
			} catch (RuntimeException | IOException exception) {
				ModSync.LOGGER.warn("Could not read Vinculum config from {}, using defaults", path, exception);
			}
		}

		Config effective = new Config(systemTransferPort(loaded.transferPort()), systemAllowServerTransfers(loaded.allowServerTransfers()));
		write(path, loaded);
		return effective;
	}

	private static int systemTransferPort(int fallback) {
		String property = System.getProperty("modsync.transferPort", "");
		String value = property.isBlank() ? System.getenv().getOrDefault("MODSYNC_TRANSFER_PORT", "") : property;
		if (value.isBlank()) {
			return fallback;
		}
		try {
			return validPort(Integer.parseInt(value), fallback);
		} catch (NumberFormatException exception) {
			ModSync.LOGGER.warn("Invalid Vinculum transfer port '{}', using {}", value, fallback);
			return fallback;
		}
	}

	private static boolean systemAllowServerTransfers(boolean fallback) {
		String property = System.getProperty("modsync.allowServerTransfers", "");
		String value = property.isBlank() ? System.getenv().getOrDefault("MODSYNC_ALLOW_SERVER_TRANSFERS", "") : property;
		return value.isBlank() ? fallback : Boolean.parseBoolean(value);
	}

	private static int validPort(int value, int fallback) {
		if (value > 0 && value <= 65535) {
			return value;
		}
		ModSync.LOGGER.warn("Invalid Vinculum transfer port '{}', using {}", value, fallback);
		return fallback;
	}

	private static void write(Path path, Config loaded) {
		try {
			Files.createDirectories(path.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("transferPort", loaded.transferPort());
			json.addProperty("allowServerTransfers", loaded.allowServerTransfers());
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			ModSync.LOGGER.warn("Could not write Vinculum config to {}", path, exception);
		}
	}

	public record Config(int transferPort, boolean allowServerTransfers) {
	}
}
