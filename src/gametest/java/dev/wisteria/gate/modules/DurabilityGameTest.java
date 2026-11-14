package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.hud.DurabilityLogic;
import dev.wisteria.modules.hud.Durability;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Durability in a real world: a worn helmet, a nearly broken elytra and a new pickaxe; the alarm on each crossing. */
public final class DurabilityGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		Durability durability = (Durability) client.modules().get("durability");
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=300]");
		world.getServer().runCommand("item replace entity @a armor.chest with minecraft:elytra[damage=410]");
		world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
		context.waitTicks(10);
		context.runOnClient(minecraft -> client.hud().toasts().clear());

		chat(context, ".toggle durability on");
		context.waitTicks(10);
		List<DurabilityLogic.Piece> pieces = context.computeOnClient(minecraft -> durability.pieces());
		check("durability: helmet, elytra and pickaxe are shown (" + pieces.size() + ")", pieces.size() == 3);
		check("durability: the helmet has 63 uses left", pieces.stream().anyMatch(piece -> piece.slot().equals("head") && piece.remaining() == 63));
		List<String> toasts = context.computeOnClient(minecraft -> client.hud().toasts().shown().stream().map(shown -> shown.toast().title()).toList());
		check("durability: the alarm toasts the low helmet and the critical elytra (" + toasts + ")",
			toasts.contains("Durability low") && toasts.contains("Durability critical"));
		grabLastFrame(context, client.platform(), "durability");

		// Wearing the helmet down further: one more toast, critical.
		context.runOnClient(minecraft -> client.hud().toasts().clear());
		world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=350]");
		context.waitTicks(10);
		List<String> after = context.computeOnClient(minecraft -> client.hud().toasts().shown().stream().map(shown -> shown.toast().text()).toList());
		check("durability: the helmet crossing into critical toasts once (" + after + ")", after.size() == 1 && after.get(0).startsWith("Diamond Helmet"));
		context.waitTicks(10);
		check("durability: no repeat while it stays critical", context.computeOnClient(minecraft -> client.hud().toasts().shown().size()) == 1);

		chat(context, ".durability percent off");
		chat(context, ".durability hands off");
		check("durability: Hands off leaves the armour", context.computeOnClient(minecraft -> durability.pieces().size()) == 2);

		chat(context, ".toggle durability off");
		chat(context, ".reset durability");
		world.getServer().runCommand("clear @a");
		GameTestKit.hideProbe(false);
	}
}
