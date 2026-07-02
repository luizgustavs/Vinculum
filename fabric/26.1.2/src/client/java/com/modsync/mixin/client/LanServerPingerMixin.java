package com.modsync.mixin.client;

import com.modsync.status.ServerModInfo;
import com.modsync.transfer.ModTransferServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LanServerPinger.class)
public class LanServerPingerMixin {
	@Inject(method = "createPingString", at = @At("RETURN"), cancellable = true)
	private static void modsync$addServerInfo(String motd, String address, CallbackInfoReturnable<String> cir) {
		ModTransferServer.start();
		cir.setReturnValue(cir.getReturnValue() + "[MODSYNC]" + ServerModInfo.local().toLanPayload() + "[/MODSYNC]");
	}
}
