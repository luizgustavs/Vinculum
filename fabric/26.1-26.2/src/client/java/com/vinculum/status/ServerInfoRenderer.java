package com.vinculum.status;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ServerInfoRenderer {
	private ServerInfoRenderer() {
	}

	public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, ServerModInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		Component version = Component.translatable("vinculum.server.version", info.fabricVersion());
		Component modCount = Component.translatable("vinculum.server.mod_count", info.mods().size());
		int right = x + width;
		int boxWidth = Math.max(minecraft.font.width(version), minecraft.font.width(modCount));
		int left = right - boxWidth - 3;
		graphics.fill(left, y, right + 2, y + 20, 0xA0000000);
		graphics.text(minecraft.font, version, right - minecraft.font.width(version), y + 1, 0xFF55FF55);
		graphics.text(minecraft.font, modCount, right - minecraft.font.width(modCount), y + 11, 0xFF55FF55);

		if (mouseX >= left && mouseX <= right + 2 && mouseY >= y && mouseY <= y + 20) {
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.translatable("vinculum.server.minecraft", info.minecraftVersion()));
			tooltip.add(Component.translatable("vinculum.server.fabric", info.fabricVersion()));
			tooltip.add(Component.translatable("vinculum.server.mods", info.mods().size()));
			for (ModEntry mod : info.mods()) {
				tooltip.add(Component.literal(mod.name() + " (" + mod.id() + ") " + mod.version()));
			}
			graphics.setTooltipForNextFrame(tooltip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
		}
	}
}
