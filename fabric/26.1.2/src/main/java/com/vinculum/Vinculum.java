package com.vinculum;

import com.vinculum.transfer.ModTransferServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Vinculum implements ModInitializer {
	public static final String MOD_ID = "vinculum";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModTransferServer.startIfDedicatedServer();
	}
}
