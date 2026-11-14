package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.storage.StorageEsp;
import dev.wisteria.platform.game.ShulkerTooltip;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Storage ESP and Shulker Preview in a real world: a double chest with something in it, a barrel, a red shulker box and
 * an ender chest; opening the chest teaches the ESP how full it is; a shulker box item with diamonds shows its grid.
 */
public final class StorageGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		StorageEsp esp = (StorageEsp) client.modules().get("storageesp");
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ() + 4;
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (feet.getZ() + 0.5) + " 0 25");
		world.getServer().runCommand("setblock " + (x - 1) + " " + y + " " + z + " minecraft:chest[facing=north,type=left]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}");
		world.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:chest[facing=north,type=right]");
		world.getServer().runCommand("setblock " + (x + 2) + " " + y + " " + z + " minecraft:barrel");
		world.getServer().runCommand("setblock " + (x + 3) + " " + y + " " + z + " minecraft:red_shulker_box");
		world.getServer().runCommand("setblock " + (x - 3) + " " + y + " " + z + " minecraft:ender_chest");
		context.waitTicks(20);

		chat(context, ".toggle storageesp on");
		context.waitTicks(15);
		check("storage: every container is boxed (" + esp.shown() + ")", context.computeOnClient(minecraft -> esp.shown()) == 5);

		// Opening the chest (a real click, the server answers with its contents).
		context.runOnClient(minecraft -> minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(new Vec3(x - 0.5, y + 0.5, z), Direction.NORTH, new BlockPos(x - 1, y, z), false)));
		context.waitTicks(10);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(15);
		Float clicked = context.computeOnClient(minecraft -> esp.fullnessAt(x - 1, y, z));
		Float otherHalf = context.computeOnClient(minecraft -> esp.fullnessAt(x, y, z));
		check("storage: the opened double chest is remembered as 1 of 54 slots (" + clicked + ")",
			clicked != null && Math.abs(clicked - 1.0F / 54.0F) < 1e-4F);
		check("storage: both halves of the double chest", otherHalf != null && otherHalf.equals(clicked));
		check("storage: an unopened barrel is unknown", context.computeOnClient(minecraft -> esp.fullnessAt(x + 2, y, z)) == null);
		grabLastFrame(context, client.platform(), "storage-esp");

		chat(context, ".storageesp barrels off");
		context.waitTicks(15);
		check("storage: a kind switched off is not boxed", context.computeOnClient(minecraft -> esp.shown()) == 4);

		// Shulker Preview: an item with diamonds in it.
		world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:red_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:12}},"
			+ "{slot:13,item:{id:\"minecraft:golden_apple\",count:3}}]]");
		context.waitTicks(10);
		check("storage: no grid while Shulker Preview is off", context.computeOnClient(minecraft ->
			minecraft.player.getInventory().getItem(0).getTooltipImage().isEmpty()));
		chat(context, ".toggle shulkerpreview on");
		ShulkerTooltip.Data data = context.computeOnClient(minecraft ->
			(ShulkerTooltip.Data) minecraft.player.getInventory().getItem(0).getTooltipImage().orElse(null));
		check("storage: the grid holds all 27 slots, diamonds first", data != null && data.slots().size() == 27
			&& data.slots().get(0).getCount() == 12 && data.slots().get(13).getCount() == 3);
		check("storage: the summary counts them (" + (data == null ? null : data.summary()) + ")",
			data != null && "2 of 27 slots · 15 items".equals(data.summary()));

		// The picture: the inventory open, the cursor on the box.
		context.getInput().pressKey(InputConstants.KEY_E);
		context.waitTicks(10);
		float scale = GameTestKit.guiScale(context);
		float[] slot = context.computeOnClient(minecraft -> new float[] {
			(minecraft.getWindow().getGuiScaledWidth() - 176) / 2.0F + 8 + 8, (minecraft.getWindow().getGuiScaledHeight() - 166) / 2.0F + 142 + 8});
		context.getInput().setCursorPos(slot[0] * scale, slot[1] * scale);
		context.waitTicks(10);
		grabLastFrame(context, client.platform(), "shulker-preview");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);

		chat(context, ".toggle shulkerpreview off");
		chat(context, ".toggle storageesp off");
		chat(context, ".reset storageesp");
		world.getServer().runCommand("clear @a");
		GameTestKit.hideProbe(false);
	}
}
