package com.vinculum.status;

import com.vinculum.Vinculum;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

final class ModDownloader {
	private static final String USER_AGENT = "Vinculum/1.0.2 (Minecraft Fabric mod)";
	// Keep the integration available while CurseForge client authentication is investigated.
	private static final boolean CURSEFORGE_DOWNLOADS_ENABLED = false;
	private static final int CURSEFORGE_GAME_ID = 432;
	private static final int CURSEFORGE_MOD_CLASS_ID = 6;
	private static final int CURSEFORGE_FABRIC_LOADER = 4;
	private static final Object MODRINTH_RATE_LIMIT_LOCK = new Object();
	private static final ConcurrentHashMap<String, CompletableFuture<Optional<Download>>> MODRINTH_DOWNLOADS = new ConcurrentHashMap<>();
	private static Instant nextModrinthRequest = Instant.EPOCH;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private ModDownloader() {
	}

	static CompletableFuture<Result> download(ModEntry mod, String minecraftVersion) {
		return downloadFromModrinth(mod, minecraftVersion);
	}

	static CompletableFuture<Result> downloadFromModrinth(ModEntry mod, String minecraftVersion) {
		Vinculum.LOGGER.info("Trying Modrinth download for {} {} on Minecraft {}", mod.id(), mod.version(), minecraftVersion);
		return modrinthDownload(mod, minecraftVersion).handle((modrinth, throwable) -> {
			try {
				if (throwable != null) {
					Vinculum.LOGGER.warn("Modrinth lookup failed for {} {}", mod.id(), mod.version(), unwrap(throwable));
					return new Result(false, "failed", detail(throwable));
				}
				if (modrinth.isPresent()) {
					Vinculum.LOGGER.info("Modrinth resolved {} {} to {}", mod.id(), mod.version(), modrinth.get().url());
					return save(modrinth.get());
				}

				String curseForgeKey = curseForgeApiKey();
				if (CURSEFORGE_DOWNLOADS_ENABLED && !curseForgeKey.isBlank()) {
					Vinculum.LOGGER.info("Trying CurseForge download for {} {}", mod.id(), mod.version());
					Optional<Download> curseForge = findOnCurseForge(mod, minecraftVersion, curseForgeKey);
					if (curseForge.isPresent()) {
						Vinculum.LOGGER.info("CurseForge resolved {} {} to {}", mod.id(), mod.version(), curseForge.get().url());
						return save(curseForge.get());
					}
				}

				Vinculum.LOGGER.info("No third-party download found for {} {}", mod.id(), mod.version());
				return new Result(false, "not_found", "");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				Vinculum.LOGGER.warn("Interrupted while downloading {} {} from Modrinth/CurseForge", mod.id(), mod.version(), exception);
				return new Result(false, "failed", detail(exception));
			} catch (IOException | RuntimeException exception) {
				Vinculum.LOGGER.warn("Failed to download {} {} from Modrinth/CurseForge", mod.id(), mod.version(), exception);
				return new Result(false, "failed", detail(exception));
			}
		});
	}

	static CompletableFuture<Result> downloadFromServer(ModEntry mod, String serverAddress, int transferPort) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Download download = serverDownload(mod, serverAddress, transferPort);
				Vinculum.LOGGER.info("Trying server download for {} {} from {}", mod.id(), mod.version(), download.url());
				return save(download);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				Vinculum.LOGGER.warn("Interrupted while downloading {} {} from server {}", mod.id(), mod.version(), serverAddress, exception);
				return new Result(false, "failed", detail(exception));
			} catch (IOException | RuntimeException exception) {
				Vinculum.LOGGER.warn("Failed to download {} {} from server {}", mod.id(), mod.version(), serverAddress, exception);
				return new Result(false, "failed", detail(exception));
			}
		});
	}

	static CompletableFuture<Boolean> existsOnModrinth(ModEntry mod, String minecraftVersion) {
		return modrinthDownload(mod, minecraftVersion).thenApply(Optional::isPresent).exceptionally(throwable -> false);
	}

	private static CompletableFuture<Optional<Download>> modrinthDownload(ModEntry mod, String minecraftVersion) {
		return MODRINTH_DOWNLOADS.computeIfAbsent(mod.id() + "\n" + mod.version() + "\n" + minecraftVersion, ignored -> CompletableFuture.supplyAsync(() -> {
			try {
				return findOnModrinth(mod, minecraftVersion);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new CompletionException(exception);
			} catch (IOException | RuntimeException exception) {
				throw new CompletionException(exception);
			}
		}));
	}

	private static Optional<Download> findOnModrinth(ModEntry mod, String minecraftVersion) throws IOException, InterruptedException {
		for (String project : modrinthProjects(mod, minecraftVersion)) {
			String filters = "?loaders=" + encode("[\"fabric\"]") + "&game_versions=" + encode("[\"" + minecraftVersion + "\"]");
			Vinculum.LOGGER.debug("Checking Modrinth project {} for {} {}", project, mod.id(), mod.version());
			HttpResponse<String> response = getModrinth("https://api.modrinth.com/v2/project/" + encodePath(project) + "/version" + filters);
			if (response.statusCode() == 404) {
				Vinculum.LOGGER.debug("Modrinth project {} returned 404", project);
				continue;
			}
			ensureSuccess(response);
			Optional<Download> download = selectModrinthVersion(JsonParser.parseString(response.body()).getAsJsonArray(), mod.version());
			if (download.isPresent()) {
				return download;
			}
		}
		return Optional.empty();
	}

	private static Download serverDownload(ModEntry mod, String serverAddress, int transferPort) {
		String filename = mod.fileName().isBlank() ? mod.id() + "-" + mod.version() + ".jar" : mod.fileName();
		return new Download("http://" + httpHost(serverAddress) + ":" + transferPort + "/mods/" + encodePath(mod.id()), filename, "server");
	}

	private static java.util.List<String> modrinthProjects(ModEntry mod, String minecraftVersion) throws IOException, InterruptedException {
		java.util.LinkedHashSet<String> projects = new java.util.LinkedHashSet<>();
		projects.add(mod.id());
		String facets = "[[\"project_type:mod\"],[\"versions:" + minecraftVersion + "\"]]";
		HttpResponse<String> response = getModrinth("https://api.modrinth.com/v2/search?query=" + encode(mod.name()) + "&limit=10&facets=" + encode(facets));
		ensureSuccess(response);
		for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("hits")) {
			JsonObject hit = element.getAsJsonObject();
			String slug = string(hit, "slug");
			String title = string(hit, "title");
			if (sameIdentifier(slug, mod.id()) || sameIdentifier(title, mod.name())) {
				projects.add(string(hit, "project_id"));
			}
		}
		Vinculum.LOGGER.debug("Modrinth candidates for {} {}: {}", mod.id(), mod.version(), projects);
		return projects.stream().filter(value -> !value.isBlank()).toList();
	}

	private static Optional<Download> selectModrinthVersion(JsonArray versions, String requiredVersion) {
		for (JsonElement element : versions) {
			JsonObject version = element.getAsJsonObject();
			if (!requiredVersion.equals(string(version, "version_number")) && !requiredVersion.equals(string(version, "name"))) {
				continue;
			}
			JsonArray files = version.getAsJsonArray("files");
			JsonObject file = java.util.stream.StreamSupport.stream(files.spliterator(), false)
				.map(JsonElement::getAsJsonObject)
				.min(Comparator.comparing(value -> !booleanValue(value, "primary")))
				.orElse(null);
			if (file != null) {
				return Optional.of(new Download(string(file, "url"), string(file, "filename"), "Modrinth"));
			}
		}
		return Optional.empty();
	}

	private static Optional<Download> findOnCurseForge(ModEntry mod, String minecraftVersion, String apiKey) throws IOException, InterruptedException {
		String searchUrl = "https://api.curseforge.com/v1/mods/search?gameId=" + CURSEFORGE_GAME_ID
			+ "&classId=" + CURSEFORGE_MOD_CLASS_ID + "&modLoaderType=" + CURSEFORGE_FABRIC_LOADER
			+ "&gameVersion=" + encode(minecraftVersion) + "&searchFilter=" + encode(mod.name()) + "&pageSize=10";
		HttpResponse<String> search = get(searchUrl, apiKey);
		ensureSuccess(search);
		for (JsonElement element : JsonParser.parseString(search.body()).getAsJsonObject().getAsJsonArray("data")) {
			JsonObject project = element.getAsJsonObject();
			if (!sameIdentifier(string(project, "slug"), mod.id()) && !sameIdentifier(string(project, "name"), mod.name())) {
				continue;
			}
			int projectId = project.get("id").getAsInt();
			String filesUrl = "https://api.curseforge.com/v1/mods/" + projectId + "/files?gameVersion=" + encode(minecraftVersion)
				+ "&modLoaderType=" + CURSEFORGE_FABRIC_LOADER + "&pageSize=50";
			HttpResponse<String> files = get(filesUrl, apiKey);
			ensureSuccess(files);
			for (JsonElement fileElement : JsonParser.parseString(files.body()).getAsJsonObject().getAsJsonArray("data")) {
				JsonObject file = fileElement.getAsJsonObject();
				if (!requiredCurseForgeVersion(file, mod.version())) {
					continue;
				}
				String url = string(file, "downloadUrl");
				if (url.isBlank()) {
					int fileId = file.get("id").getAsInt();
					HttpResponse<String> downloadUrl = get("https://api.curseforge.com/v1/mods/" + projectId + "/files/" + fileId + "/download-url", apiKey);
					ensureSuccess(downloadUrl);
					url = string(JsonParser.parseString(downloadUrl.body()).getAsJsonObject(), "data");
				}
				if (!url.isBlank()) {
					return Optional.of(new Download(url, string(file, "fileName"), "CurseForge"));
				}
			}
		}
		return Optional.empty();
	}

	private static boolean requiredCurseForgeVersion(JsonObject file, String requiredVersion) {
		if (requiredVersion.equals(string(file, "displayName")) || requiredVersion.equals(string(file, "fileName"))) {
			return true;
		}
		return string(file, "displayName").contains(requiredVersion) || string(file, "fileName").contains(requiredVersion);
	}

	private static Result save(Download download) throws IOException, InterruptedException {
		Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
		Files.createDirectories(modsDirectory);
		String filename = Path.of(download.filename()).getFileName().toString();
		if (!filename.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new IOException("The resolved file is not a JAR");
		}
		Path destination = modsDirectory.resolve(filename);
		Path temporary = modsDirectory.resolve(filename + ".part");
		Files.deleteIfExists(temporary);
		HttpRequest request = HttpRequest.newBuilder(URI.create(download.url()))
			.timeout(Duration.ofMinutes(2))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		HttpResponse<Path> response;
		try {
			Vinculum.LOGGER.info("Downloading {} file {} to temporary path {}", download.source(), filename, temporary);
			response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
		} catch (IOException | InterruptedException exception) {
			Files.deleteIfExists(temporary);
			Vinculum.LOGGER.warn("Request failed for {}", download.url(), exception);
			throw exception;
		}
		long size = Files.exists(temporary) ? Files.size(temporary) : 0;
		Vinculum.LOGGER.info("Download response from {}: HTTP {}, {} bytes", download.source(), response.statusCode(), size);
		if (response.statusCode() < 200 || response.statusCode() >= 300 || size == 0) {
			Files.deleteIfExists(temporary);
			throw new IOException("Download returned HTTP " + response.statusCode() + " with " + size + " bytes from " + download.url());
		}
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		Vinculum.LOGGER.info("Saved {} download to {}", download.source(), destination);
		return new Result(true, "downloaded", download.source());
	}

	private static HttpResponse<String> get(String url, String apiKey) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.GET();
		if (apiKey != null && !apiKey.isBlank()) {
			builder.header("x-api-key", apiKey);
		}
		return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> getModrinth(String url) throws IOException, InterruptedException {
		waitForModrinthQuota();
		HttpResponse<String> response = get(url, null);
		updateModrinthQuota(response);
		if (response.statusCode() == 429) {
			waitForModrinthQuota();
			response = get(url, null);
			updateModrinthQuota(response);
		}
		return response;
	}

	private static void waitForModrinthQuota() throws InterruptedException {
		synchronized (MODRINTH_RATE_LIMIT_LOCK) {
			long waitMillis = Duration.between(Instant.now(), nextModrinthRequest).toMillis();
			if (waitMillis > 0) {
				Thread.sleep(waitMillis);
			}
		}
	}

	private static void updateModrinthQuota(HttpResponse<?> response) {
		int remaining = response.headers().firstValue("X-Ratelimit-Remaining").map(ModDownloader::parseInt).orElse(1);
		int resetSeconds = response.headers().firstValue("X-Ratelimit-Reset").map(ModDownloader::parseInt).orElse(1);
		if (response.statusCode() == 429 || remaining <= 1) {
			synchronized (MODRINTH_RATE_LIMIT_LOCK) {
				nextModrinthRequest = Instant.now().plusSeconds(Math.max(1, resetSeconds));
			}
		}
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			return 1;
		}
	}

	private static void ensureSuccess(HttpResponse<?> response) throws IOException {
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("API returned HTTP " + response.statusCode() + " for " + response.uri());
		}
	}

	private static String detail(Throwable throwable) {
		Throwable cause = unwrap(throwable);
		String message = cause.getMessage();
		return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
	}

	private static Throwable unwrap(Throwable throwable) {
		if (throwable instanceof CompletionException && throwable.getCause() != null) {
			return throwable.getCause();
		}
		return throwable;
	}

	private static String curseForgeApiKey() {
		String property = System.getProperty("vinculum.curseforgeApiKey", "");
		return property.isBlank() ? System.getenv().getOrDefault("VINCULUM_CURSEFORGE_API_KEY", "") : property;
	}

	private static boolean sameIdentifier(String first, String second) {
		return normalize(first).equals(normalize(second));
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String string(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static boolean booleanValue(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value != null && !value.isJsonNull() && value.getAsBoolean();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String encodePath(String value) {
		return encode(value).replace("+", "%20");
	}

	private static String httpHost(String address) {
		String host = address.trim();
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			return end > 0 ? host.substring(0, end + 1) : host;
		}
		int firstColon = host.indexOf(':');
		int lastColon = host.lastIndexOf(':');
		if (firstColon >= 0 && firstColon == lastColon) {
			host = host.substring(0, firstColon);
		}
		return host.contains(":") ? "[" + host + "]" : host;
	}

	record Result(boolean success, String messageKey, String detail) {
	}

	private record Download(String url, String filename, String source) {
	}
}
