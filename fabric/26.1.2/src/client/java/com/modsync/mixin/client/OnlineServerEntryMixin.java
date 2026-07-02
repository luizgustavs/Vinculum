package com.modsync.mixin.client;

import com.modsync.status.ClientServerInfoExtension;
import com.modsync.status.EntryPosition;
import com.modsync.status.ServerInfoRenderer;
import com.modsync.status.ServerModInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class OnlineServerEntryMixin {
	@Shadow
	private ServerData serverData;

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void modsync$renderServerInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
		ServerModInfo info = ((ClientServerInfoExtension) this.serverData).modsync$getModInfo();
		if (info != null) {
			EntryPosition entry = (EntryPosition) this;
			ServerInfoRenderer.render(graphics, entry.modsync$getContentX() + 35, entry.modsync$getContentY(), entry.modsync$getContentWidth() - 55, mouseX, mouseY, info);
		}
	}
}
