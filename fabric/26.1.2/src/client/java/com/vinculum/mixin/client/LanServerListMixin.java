package com.vinculum.mixin.client;

import com.vinculum.status.ClientServerInfoExtension;
import com.vinculum.status.ServerModInfo;
import java.net.InetAddress;
import java.util.List;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanServerDetection.LanServerList.class)
public class LanServerListMixin {
	@Shadow
	private List<LanServer> servers;

	@Inject(method = "addServer", at = @At("TAIL"))
	private void vinculum$captureServerInfo(String pingData, InetAddress socketAddress, CallbackInfo ci) {
		ServerModInfo info = ServerModInfo.fromLanPing(pingData);
		String port = LanServerPinger.parseAddress(pingData);
		if (info == null || port == null) {
			return;
		}

		String address = socketAddress.getHostAddress() + ":" + port;
		this.servers.stream()
			.filter(server -> server.getAddress().equals(address))
			.findFirst()
			.ifPresent(server -> ((ClientServerInfoExtension) server).vinculum$setModInfo(info));
	}
}
