package com.modsync.status;

import com.modsync.ModSyncConfig;
import com.modsync.JarFilter;
import com.modsync.transfer.ModTransferServer;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

public record ServerModInfo(String minecraftVersion, String fabricVersion, int transferPort, boolean allowServerTransfers, List<ModEntry> mods) {
	public static final Codec<ServerModInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("minecraft").forGetter(ServerModInfo::minecraftVersion),
		Codec.STRING.fieldOf("fabric").forGetter(ServerModInfo::fabricVersion),
		Codec.INT.optionalFieldOf("transferPort", ModTransferServer.DEFAULT_PORT).forGetter(ServerModInfo::transferPort),
		Codec.BOOL.optionalFieldOf("allowServerTransfers", ModSyncConfig.DEFAULT_ALLOW_SERVER_TRANSFERS).forGetter(ServerModInfo::allowServerTransfers),
		ModEntry.CODEC.listOf().fieldOf("mods").forGetter(ServerModInfo::mods)
	).apply(instance, ServerModInfo::new));

	private static final ServerModInfo LOCAL = createLocal();

	public static ServerModInfo local() {
		return LOCAL;
	}

	private static ServerModInfo createLocal() {
		FabricLoader loader = FabricLoader.getInstance();
		String fabricVersion = loader.getModContainer("fabricloader")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
		List<ModEntry> mods = loader.getAllMods().stream()
			.filter(ServerModInfo::isUserMod)
			.filter(JarFilter::shouldManage)
			.map(container -> new ModEntry(
				container.getMetadata().getId(),
				container.getMetadata().getName(),
				container.getMetadata().getVersion().getFriendlyString(),
				environmentName(container.getMetadata().getEnvironment()),
				fileName(container),
				container.getMetadata().getAuthors().stream().map(author -> author.getName()).toList(),
				fileSize(container)
			))
			.sorted(Comparator.comparing(ModEntry::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new ServerModInfo(SharedConstants.getCurrentVersion().name(), fabricVersion, ModTransferServer.port(), ModSyncConfig.get().allowServerTransfers(), mods);
	}

	private static String fileName(ModContainer container) {
		return container.getOrigin().getPaths().stream()
			.map(path -> path.getFileName() == null ? "" : path.getFileName().toString())
			.filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
			.findFirst()
			.orElse("");
	}

	private static long fileSize(ModContainer container) {
		return container.getOrigin().getPaths().stream()
			.filter(path -> Files.isRegularFile(path) && path.getFileName() != null)
			.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
			.findFirst()
			.map(path -> {
				try {
					return Files.size(path);
				} catch (IOException exception) {
					return -1L;
				}
			})
			.orElse(-1L);
	}

	private static String environmentName(ModEnvironment environment) {
		return switch (environment) {
			case CLIENT -> "client";
			case SERVER -> "server";
			case UNIVERSAL -> "universal";
		};
	}

	private static boolean isUserMod(ModContainer container) {
		String id = container.getMetadata().getId();
		return !id.equals("minecraft")
			&& !id.equals("java")
			&& !id.equals("fabricloader")
			&& container.getContainingMod().isEmpty();
	}

	public String toLanPayload() {
		String json = CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow().toString();
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(json.getBytes(StandardCharsets.UTF_8));
			}
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException exception) {
			throw new IllegalStateException("Could not encode LAN server mod information", exception);
		}
	}

	@Nullable
	public static ServerModInfo fromLanPing(String pingData) {
		int start = pingData.indexOf("[MODSYNC]");
		int end = pingData.indexOf("[/MODSYNC]");
		if (start < 0 || end <= start) {
			return null;
		}

		try {
			String payload = pingData.substring(start + "[MODSYNC]".length(), end);
			byte[] compressed = Base64.getUrlDecoder().decode(payload);
			String json;
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
				json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
			}
			return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(null);
		} catch (IllegalArgumentException | IOException exception) {
			return null;
		}
	}
}
