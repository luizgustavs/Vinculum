package com.vinculum.mixin.client;

import com.vinculum.status.ClientServerInfoExtension;
import com.vinculum.status.ServerModInfo;
import net.minecraft.client.server.LanServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LanServer.class)
public class LanServerMixin implements ClientServerInfoExtension {
	@Unique @Nullable
	private ServerModInfo vinculum$modInfo;

	@Override
	public @Nullable ServerModInfo vinculum$getModInfo() {
		return this.vinculum$modInfo;
	}

	@Override
	public void vinculum$setModInfo(@Nullable ServerModInfo info) {
		this.vinculum$modInfo = info;
	}
}
