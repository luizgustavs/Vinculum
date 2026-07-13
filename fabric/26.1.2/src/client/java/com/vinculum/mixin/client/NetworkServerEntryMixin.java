package com.vinculum.mixin.client;

import com.vinculum.status.ClientServerInfoExtension;
import com.vinculum.status.EntryPosition;
import com.vinculum.status.ModComparisonScreen;
import com.vinculum.status.ServerInfoRenderer;
import com.vinculum.status.ServerModInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.server.LanServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$NetworkServerEntry")
public abstract class NetworkServerEntryMixin {
	@Shadow
	protected LanServer serverData;
	@Shadow
	private JoinMultiplayerScreen screen;

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void vinculum$renderServerInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
		ServerModInfo info = ((ClientServerInfoExtension) this.serverData).vinculum$getModInfo();
		if (info != null) {
			EntryPosition entry = (EntryPosition) this;
			ServerInfoRenderer.render(graphics, entry.vinculum$getContentX() + 35, entry.vinculum$getContentY(), entry.vinculum$getContentWidth() - 40, mouseX, mouseY, info);
		}
	}

	@Inject(method = "join", at = @At("HEAD"), cancellable = true)
	private void vinculum$checkModsBeforeJoining(CallbackInfo ci) {
		ServerModInfo info = ((ClientServerInfoExtension) this.serverData).vinculum$getModInfo();
		if (info != null && ModComparisonScreen.openIfNeeded(this.screen, info, this.serverData.getAddress())) {
			ci.cancel();
		}
	}
}
