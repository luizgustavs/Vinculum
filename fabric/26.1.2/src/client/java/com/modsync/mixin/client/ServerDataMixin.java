package com.modsync.mixin.client;

import com.modsync.status.ClientServerInfoExtension;
import com.modsync.status.ServerModInfo;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerData.class)
public class ServerDataMixin implements ClientServerInfoExtension {
	@Unique @Nullable
	private ServerModInfo modsync$modInfo;

	@Override
	public @Nullable ServerModInfo modsync$getModInfo() {
		return this.modsync$modInfo;
	}

	@Override
	public void modsync$setModInfo(@Nullable ServerModInfo info) {
		this.modsync$modInfo = info;
	}
}
