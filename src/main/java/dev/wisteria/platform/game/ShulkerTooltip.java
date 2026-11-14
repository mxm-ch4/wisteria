package dev.wisteria.platform.game;

import dev.wisteria.modules.storage.ShulkerLayout;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Shulker Preview's tooltip: the data (every slot of the box) and how it is drawn. */
public final class ShulkerTooltip {
	private static final int BACKGROUND = 0xC0201C2A;
	private static final int SLOT = 0x60433C55;

	private ShulkerTooltip() {
	}

	/** What the item hands to the tooltip: every slot, empty ones included, and whether to add the summary line. */
	public record Data(List<ItemStack> slots, @Nullable String summary) implements TooltipComponent {
	}

	/** The drawn part. */
	public record Grid(Data data) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			int grid = ShulkerLayout.rows(this.data.slots().size()) * ShulkerLayout.SLOT + 4;
			return this.data.summary() == null ? grid : grid + font.lineHeight + 2;
		}

		@Override
		public int getWidth(Font font) {
			int grid = ShulkerLayout.COLUMNS * ShulkerLayout.SLOT + 2;
			return this.data.summary() == null ? grid : Math.max(grid, font.width(this.data.summary()));
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
			int rows = ShulkerLayout.rows(this.data.slots().size());
			graphics.fill(x, y, x + ShulkerLayout.COLUMNS * ShulkerLayout.SLOT + 2, y + rows * ShulkerLayout.SLOT + 2, BACKGROUND);
			for (int i = 0; i < this.data.slots().size(); i++) {
				int[] cell = ShulkerLayout.cell(i);
				int cx = x + 1 + cell[0];
				int cy = y + 1 + cell[1];
				graphics.fill(cx + 1, cy + 1, cx + ShulkerLayout.SLOT - 1, cy + ShulkerLayout.SLOT - 1, SLOT);
				ItemStack stack = this.data.slots().get(i);
				if (!stack.isEmpty()) {
					graphics.item(stack, cx + 1, cy + 1);
					graphics.itemDecorations(font, stack, cx + 1, cy + 1);
				}
			}

			if (this.data.summary() != null) {
				graphics.text(font, this.data.summary(), x, y + rows * ShulkerLayout.SLOT + 5, 0xFFB8B0CC);
			}
		}
	}
}
