package com.vinculum.mixin.client;

import net.minecraft.client.server.LanServerDetection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LanServerDetection.LanServerDetector.class)
public class LanServerDetectorMixin {
	@ModifyConstant(method = "run", constant = @Constant(intValue = 1024))
	private int vinculum$increasePacketBuffer(int original) {
		return 32767;
	}
}
