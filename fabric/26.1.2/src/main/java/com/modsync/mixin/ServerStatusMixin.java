package com.modsync.mixin;

import com.modsync.status.ServerModInfo;
import com.modsync.status.ServerStatusExtension;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.status.ServerStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatus.class)
public abstract class ServerStatusMixin implements ServerStatusExtension {
	@Shadow @Final @Mutable
	public static Codec<ServerStatus> CODEC;

	@Unique @Nullable
	private ServerModInfo modsync$modInfo;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void modsync$extendStatusCodec(CallbackInfo ci) {
		CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ComponentSerialization.CODEC.lenientOptionalFieldOf("description", CommonComponents.EMPTY).forGetter(ServerStatus::description),
			ServerStatus.Players.CODEC.lenientOptionalFieldOf("players").forGetter(ServerStatus::players),
			ServerStatus.Version.CODEC.lenientOptionalFieldOf("version").forGetter(ServerStatus::version),
			ServerStatus.Favicon.CODEC.lenientOptionalFieldOf("favicon").forGetter(ServerStatus::favicon),
			Codec.BOOL.lenientOptionalFieldOf("enforcesSecureChat", false).forGetter(ServerStatus::enforcesSecureChat),
			ServerModInfo.CODEC.lenientOptionalFieldOf("modsync").forGetter(ServerStatusMixin::modsync$getInfoForEncoding)
		).apply(instance, ServerStatusMixin::modsync$create));
	}

	@Unique
	private static Optional<ServerModInfo> modsync$getInfoForEncoding(ServerStatus status) {
		ServerModInfo info = ((ServerStatusExtension) (Object) status).modsync$getModInfo();
		return Optional.of(info == null ? ServerModInfo.local() : info);
	}

	@Unique
	private static ServerStatus modsync$create(
			Component description,
			Optional<ServerStatus.Players> players,
			Optional<ServerStatus.Version> version,
			Optional<ServerStatus.Favicon> favicon,
			boolean enforcesSecureChat,
			Optional<ServerModInfo> modInfo) {
		ServerStatus status = new ServerStatus(description, players, version, favicon, enforcesSecureChat);
		((ServerStatusExtension) (Object) status).modsync$setModInfo(modInfo.orElse(null));
		return status;
	}

	@Override
	public @Nullable ServerModInfo modsync$getModInfo() {
		return this.modsync$modInfo;
	}

	@Override
	public void modsync$setModInfo(@Nullable ServerModInfo info) {
		this.modsync$modInfo = info;
	}
}
