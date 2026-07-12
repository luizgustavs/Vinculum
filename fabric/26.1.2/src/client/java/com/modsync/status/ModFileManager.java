package com.modsync.status;

import com.modsync.JarFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

final class ModFileManager {
	private static final Set<Path> RESERVED_BACKUPS = new HashSet<>();

	private ModFileManager() {
	}

	static Path modsDirectory() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	static Optional<Path> localJar(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.flatMap(container -> container.getOrigin().getPaths().stream()
				.filter(path -> Files.isRegularFile(path) && path.getFileName() != null)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
				.findFirst());
	}

	static boolean backup(String modId) throws IOException {
		Optional<Path> jar = localJar(modId);
		if (jar.isEmpty()) {
			return false;
		}
		backup(jar.get());
		return true;
	}

	static void backup(Path path) throws IOException {
		if (!JarFilter.shouldManage(path)) {
			return;
		}
		Path destination = backupPath(path);
		try {
			Files.move(path, destination);
		} catch (IOException exception) {
			scheduleBackupAfterExit(path, destination, exception);
		}
	}

	private static Path backupPath(Path path) {
		Path candidate = path.resolveSibling(path.getFileName() + ".bak");
		int copy = 1;
		while (Files.exists(candidate) || RESERVED_BACKUPS.contains(candidate)) {
			candidate = path.resolveSibling(path.getFileName() + ".bak." + copy);
			copy++;
		}
		RESERVED_BACKUPS.add(candidate);
		return candidate;
	}

	private static void scheduleBackupAfterExit(Path source, Path destination, IOException moveException) throws IOException {
		ProcessHandle current = ProcessHandle.current();
		if (isWindows()) {
			startWindowsMoveAfterExit(current.pid(), source, destination, moveException);
			return;
		}
		startUnixMoveAfterExit(current.pid(), source, destination, moveException);
	}

	private static void startWindowsMoveAfterExit(long pid, Path source, Path destination, IOException moveException) throws IOException {
		String script = "$ErrorActionPreference = 'SilentlyContinue'; "
			+ "Wait-Process -Id " + pid + "; "
			+ "Start-Sleep -Milliseconds 500; "
			+ "Move-Item -LiteralPath '" + powerShell(source) + "' -Destination '" + powerShell(destination) + "' -Force";
		String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		try {
			new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encoded).start();
		} catch (IOException exception) {
			exception.addSuppressed(moveException);
			throw exception;
		}
	}

	private static void startUnixMoveAfterExit(long pid, Path source, Path destination, IOException moveException) throws IOException {
		String script = "while kill -0 " + pid + " 2>/dev/null; do sleep 1; done; mv -f \"$1\" \"$2\"";
		try {
			new ProcessBuilder("sh", "-c", script, "modsync-backup", source.toString(), destination.toString()).start();
		} catch (IOException exception) {
			exception.addSuppressed(moveException);
			throw exception;
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static String powerShell(Path path) {
		return path.toAbsolutePath().toString().replace("'", "''");
	}

	static boolean isUserMod(ModContainer container) {
		String id = container.getMetadata().getId();
		return !id.equals("minecraft")
			&& !id.equals("java")
			&& !id.equals("fabricloader")
			&& container.getContainingMod().isEmpty();
	}
}
