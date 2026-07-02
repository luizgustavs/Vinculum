package com.modsync.mixin.client;

import com.modsync.status.ClientServerInfoExtension;
import com.modsync.status.ServerStatusExtension;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public class ServerStatusListenerMixin {
	@Shadow @Final
	private ServerData val$data;

	@Inject(method = "handleStatusResponse", at = @At("HEAD"))
	private void modsync$captureServerInfo(ClientboundStatusResponsePacket packet, CallbackInfo ci) {
		((ClientServerInfoExtension) this.val$data).modsync$setModInfo(
			((ServerStatusExtension) (Object) packet.status()).modsync$getModInfo()
		);
	}
}
