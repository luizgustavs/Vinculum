package com.vinculum.mixin.client;

import com.vinculum.status.ClientServerInfoExtension;
import com.vinculum.status.ModComparisonScreen;
import com.vinculum.status.ServerModInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
	@Inject(method = "join", at = @At("HEAD"), cancellable = true)
	private void vinculum$checkModsBeforeJoining(ServerData data, CallbackInfo ci) {
		ServerModInfo info = ((ClientServerInfoExtension) data).vinculum$getModInfo();
		if (info != null && ModComparisonScreen.openIfNeeded((Screen) (Object) this, info, data.ip)) {
			ci.cancel();
		}
	}
}
