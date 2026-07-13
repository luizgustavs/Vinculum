package com.vinculum.mixin.client;

import com.vinculum.status.ServerModInfo;
import com.vinculum.transfer.ModTransferServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LanServerPinger.class)
public class LanServerPingerMixin {
	@Inject(method = "createPingString", at = @At("RETURN"), cancellable = true)
	private static void vinculum$addServerInfo(String motd, String address, CallbackInfoReturnable<String> cir) {
		ModTransferServer.start();
		cir.setReturnValue(cir.getReturnValue() + "[VINCULUM]" + ServerModInfo.local().toLanPayload() + "[/VINCULUM]");
	}
}
