package com.modsync.mixin.client;

import com.modsync.status.EntryPosition;
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
	public int modsync$getContentX() {
		return this.getContentX();
	}

	@Override
	public int modsync$getContentY() {
		return this.getContentY();
	}

	@Override
	public int modsync$getContentWidth() {
		return this.getContentWidth();
	}
}
