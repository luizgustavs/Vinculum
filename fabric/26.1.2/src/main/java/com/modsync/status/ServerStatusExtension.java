package com.modsync.status;

import org.jspecify.annotations.Nullable;

public interface ServerStatusExtension {
	@Nullable
	ServerModInfo modsync$getModInfo();

	void modsync$setModInfo(@Nullable ServerModInfo info);
}
