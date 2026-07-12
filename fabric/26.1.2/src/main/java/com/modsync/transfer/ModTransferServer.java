package com.modsync.transfer;

import com.modsync.ModSync;
import com.modsync.ModSyncConfig;
import com.modsync.JarFilter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class ModTransferServer {
	public static final int DEFAULT_PORT = ModSyncConfig.DEFAULT_TRANSFER_PORT;
	private static final AtomicBoolean STARTED = new AtomicBoolean();

	private ModTransferServer() {
	}

	public static void startIfDedicatedServer() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
			return;
		}
		start();
	}

	public static void start() {
		if (!ModSyncConfig.get().allowServerTransfers()) {
			ModSync.LOGGER.info("Vinculum transfer server is disabled by config");
			return;
		}
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		Thread thread = new Thread(ModTransferServer::run, "Vinculum Transfer Server");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run() {
		int port = port();
		try (ServerSocket server = new ServerSocket(port)) {
			ModSync.LOGGER.info("Vinculum transfer server listening on port {}", port);
			while (!Thread.currentThread().isInterrupted()) {
				Socket socket = server.accept();
				ModSync.LOGGER.debug("Accepted Vinculum transfer connection from {}", socket.getRemoteSocketAddress());
				Thread client = new Thread(() -> handle(socket), "Vinculum Transfer Client");
				client.setDaemon(true);
				client.start();
			}
		} catch (IOException exception) {
			ModSync.LOGGER.error("Could not start transfer server on port {}", port, exception);
		}
	}

	public static int port() {
		return ModSyncConfig.get().transferPort();
	}

	private static void handle(Socket socket) {
		try (socket; BufferedInputStream input = new BufferedInputStream(socket.getInputStream()); BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
			String requestLine = readLine(input);
			if (requestLine == null) {
				return;
			}
			while (readLine(input) != null) {
				// Headers are not needed for this tiny read-only endpoint.
			}

			String[] parts = requestLine.split(" ");
			if (parts.length < 2 || !"GET".equals(parts[0])) {
				ModSync.LOGGER.warn("Rejected invalid transfer request from {}: {}", socket.getRemoteSocketAddress(), requestLine);
				writeStatus(output, 405, "Method Not Allowed");
				return;
			}

			String id = modId(parts[1]);
			if (id.isBlank()) {
				ModSync.LOGGER.warn("Transfer request from {} did not target a mod: {}", socket.getRemoteSocketAddress(), parts[1]);
				writeStatus(output, 404, "Not Found");
				return;
			}

			ModSync.LOGGER.info("Transfer request from {} for mod {}", socket.getRemoteSocketAddress(), id);
			Optional<Path> file = findModJar(id);
			if (file.isEmpty()) {
				ModSync.LOGGER.warn("Could not find a transferable JAR for mod {}", id);
				writeStatus(output, 404, "Not Found");
				return;
			}

			writeFile(output, id, file.get());
		} catch (IOException exception) {
			// The client may close the socket during a cancelled download; nothing to recover here.
			ModSync.LOGGER.debug("Transfer connection ended with an I/O error", exception);
		}
	}

	private static Optional<Path> findModJar(String id) {
		return FabricLoader.getInstance().getModContainer(id)
			.filter(ModTransferServer::isUserMod)
			.filter(JarFilter::shouldManage)
			.flatMap(container -> container.getOrigin().getPaths().stream()
				.filter(path -> Files.isRegularFile(path) && path.getFileName() != null)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
				.findFirst());
	}

	private static boolean isUserMod(ModContainer container) {
		String id = container.getMetadata().getId();
		return !id.equals("minecraft")
			&& !id.equals("java")
			&& !id.equals("fabricloader")
			&& container.getContainingMod().isEmpty();
	}

	private static String modId(String target) {
		String path = target.split("\\?", 2)[0];
		if (!path.startsWith("/mods/")) {
			return "";
		}
		return URLDecoder.decode(path.substring("/mods/".length()), StandardCharsets.UTF_8);
	}

	private static void writeFile(BufferedOutputStream output, String id, Path file) throws IOException {
		String filename = file.getFileName().toString().replace("\"", "");
		long size = Files.size(file);
		ModSync.LOGGER.info("Serving mod {} from {} ({} bytes)", id, file, size);
		writeText(output, "HTTP/1.1 200 OK\r\n");
		writeText(output, "Content-Type: application/java-archive\r\n");
		writeText(output, "Content-Length: " + size + "\r\n");
		writeText(output, "Content-Disposition: attachment; filename=\"" + filename + "\"\r\n");
		writeText(output, "X-Vinculum-Mod-Id: " + id + "\r\n");
		writeText(output, "Connection: close\r\n\r\n");
		try (BufferedInputStream fileInput = new BufferedInputStream(Files.newInputStream(file))) {
			fileInput.transferTo(output);
		}
		output.flush();
	}

	private static void writeStatus(BufferedOutputStream output, int status, String message) throws IOException {
		byte[] body = message.getBytes(StandardCharsets.UTF_8);
		writeText(output, "HTTP/1.1 " + status + " " + message + "\r\n");
		writeText(output, "Content-Type: text/plain; charset=utf-8\r\n");
		writeText(output, "Content-Length: " + body.length + "\r\n");
		writeText(output, "Connection: close\r\n\r\n");
		output.write(body);
		output.flush();
	}

	private static void writeText(BufferedOutputStream output, String value) throws IOException {
		output.write(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String readLine(BufferedInputStream input) throws IOException {
		StringBuilder line = new StringBuilder();
		int previous = -1;
		int current;
		while ((current = input.read()) != -1) {
			if (previous == '\r' && current == '\n') {
				line.setLength(line.length() - 1);
				return line.isEmpty() ? null : line.toString();
			}
			line.append((char) current);
			previous = current;
		}
		return line.isEmpty() ? null : line.toString();
	}
}
