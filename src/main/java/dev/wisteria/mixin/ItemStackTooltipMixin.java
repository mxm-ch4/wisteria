package dev.wisteria.mixin;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.modules.storage.ShulkerLayout;
import dev.wisteria.modules.storage.ShulkerPreview;
import dev.wisteria.platform.game.ShulkerTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Asks Shulker Preview whether a shulker box item shows its contents as a grid; nothing decided here. */
@Mixin(ItemStack.class)
abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void wisteria$shulkerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		Client client = Wisteria.clientOrNull();
		ItemStack self = (ItemStack) (Object) this;
		if (client == null || !(self.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) {
			return;
		}

		ShulkerPreview preview = client.modules().get(ShulkerPreview.class);
		ItemContainerContents contents = self.get(DataComponents.CONTAINER);
		if (preview == null || !preview.isEnabled() || contents == null || contents.equals(ItemContainerContents.EMPTY)) {
			return;
		}

		NonNullList<ItemStack> slots = NonNullList.withSize(net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.CONTAINER_SIZE, ItemStack.EMPTY);
		contents.copyInto(slots);
		List<Integer> counts = new ArrayList<>();
		slots.forEach(stack -> counts.add(stack.getCount()));
		cir.setReturnValue(Optional.of(new ShulkerTooltip.Data(List.copyOf(slots), preview.summary() ? ShulkerLayout.summary(counts) : null)));
	}
}
