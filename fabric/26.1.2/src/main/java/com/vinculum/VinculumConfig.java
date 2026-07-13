package com.vinculum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class VinculumConfig {
	public static final int DEFAULT_TRANSFER_PORT = 9123;
	public static final boolean DEFAULT_ALLOW_SERVER_TRANSFERS = true;
	public static final FilterMode DEFAULT_FILTER_MODE = FilterMode.NONE;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config config;

	private VinculumConfig() {
	}

	public static synchronized Config get() {
		if (config == null) {
			config = load();
		}
		return config;
	}

	private static Config load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("vinculum.json");
		Config loaded = new Config(DEFAULT_TRANSFER_PORT, DEFAULT_ALLOW_SERVER_TRANSFERS, DEFAULT_FILTER_MODE, List.of());
		if (Files.exists(path)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
				loaded = new Config(
					validPort(json.has("transferPort") ? json.get("transferPort").getAsInt() : DEFAULT_TRANSFER_PORT, DEFAULT_TRANSFER_PORT),
					json.has("allowServerTransfers") ? json.get("allowServerTransfers").getAsBoolean() : DEFAULT_ALLOW_SERVER_TRANSFERS,
					filterMode(json.has("filterMode") ? json.get("filterMode").getAsString() : "none"),
					filterRules(json)
				);
			} catch (RuntimeException | IOException exception) {
				Vinculum.LOGGER.warn("Could not read Vinculum config from {}, using defaults", path, exception);
			}
		}

		Config effective = new Config(systemTransferPort(loaded.transferPort()), systemAllowServerTransfers(loaded.allowServerTransfers()), loaded.filterMode(), loaded.filterRules());
		write(path, loaded);
		return effective;
	}

	private static FilterMode filterMode(String value) {
		try {
			return FilterMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			Vinculum.LOGGER.warn("Invalid Vinculum filter mode '{}', using none", value);
			return DEFAULT_FILTER_MODE;
		}
	}

	private static List<String> filterRules(JsonObject json) {
		if (!json.has("filterRules") || !json.get("filterRules").isJsonArray()) {
			return List.of();
		}
		List<String> rules = new ArrayList<>();
		json.getAsJsonArray("filterRules").forEach(element -> {
			if (element.isJsonPrimitive()) {
				String rule = element.getAsString().trim();
				if (!rule.isEmpty() && !rule.startsWith("#")) {
					rules.add(rule);
				}
			}
		});
		return List.copyOf(rules);
	}

	private static int systemTransferPort(int fallback) {
		String property = System.getProperty("vinculum.transferPort", "");
		String value = property.isBlank() ? System.getenv().getOrDefault("VINCULUM_TRANSFER_PORT", "") : property;
		if (value.isBlank()) {
			return fallback;
		}
		try {
			return validPort(Integer.parseInt(value), fallback);
		} catch (NumberFormatException exception) {
			Vinculum.LOGGER.warn("Invalid Vinculum transfer port '{}', using {}", value, fallback);
			return fallback;
		}
	}

	private static boolean systemAllowServerTransfers(boolean fallback) {
		String property = System.getProperty("vinculum.allowServerTransfers", "");
		String value = property.isBlank() ? System.getenv().getOrDefault("VINCULUM_ALLOW_SERVER_TRANSFERS", "") : property;
		return value.isBlank() ? fallback : Boolean.parseBoolean(value);
	}

	private static int validPort(int value, int fallback) {
		if (value > 0 && value <= 65535) {
			return value;
		}
		Vinculum.LOGGER.warn("Invalid Vinculum transfer port '{}', using {}", value, fallback);
		return fallback;
	}

	private static void write(Path path, Config loaded) {
		try {
			Files.createDirectories(path.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("transferPort", loaded.transferPort());
			json.addProperty("allowServerTransfers", loaded.allowServerTransfers());
			json.addProperty("filterMode", loaded.filterMode().name().toLowerCase(Locale.ROOT));
			json.add("filterRules", GSON.toJsonTree(loaded.filterRules()));
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			Vinculum.LOGGER.warn("Could not write Vinculum config to {}", path, exception);
		}
	}

	public enum FilterMode {
		NONE,
		BLACKLIST,
		WHITELIST
	}

	public record Config(int transferPort, boolean allowServerTransfers, FilterMode filterMode, List<String> filterRules) {
	}
}
