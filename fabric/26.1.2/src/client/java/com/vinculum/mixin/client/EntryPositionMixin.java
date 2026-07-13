package com.vinculum.mixin.client;

import com.vinculum.status.EntryPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public abstract class EntryPositionMixin implements EntryPosition {
	@Shadow
	public abstract int getContentX();

	@Shadow
	public abstract int getContentY();

	@Shadow
	public abstract int getContentWidth();

	@Override
	public int vinculum$getContentX() {
		return this.getContentX();
	}

	@Override
	public int vinculum$getContentY() {
		return this.getContentY();
	}

	@Override
	public int vinculum$getContentWidth() {
		return this.getContentWidth();
	}
}
