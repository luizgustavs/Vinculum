package com.vinculum.status;

import org.jspecify.annotations.Nullable;

public interface ClientServerInfoExtension {
	@Nullable
	ServerModInfo vinculum$getModInfo();

	void vinculum$setModInfo(@Nullable ServerModInfo info);
}
