package com.modsync;

import com.modsync.transfer.ModTransferServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModSync implements ModInitializer {
	public static final String MOD_ID = "modsync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModTransferServer.startIfDedicatedServer();
	}
}
