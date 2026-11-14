package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.event.Events;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.gate.GameTestKit.Frame;
import dev.wisteria.gate.ModuleGameTest;
import dev.wisteria.modules.waypoints.Waypoint;
import dev.wisteria.modules.waypoints.Waypoints;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.ListSetting;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Waypoints in a real world: added from chat and from the menu, drawn where they are with a label, kept per server. */
public final class WaypointsGameTest extends ModuleGameTest {
	@Override
	protected void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		Waypoints waypoints = (Waypoints) client.modules().get("waypoints");
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 0");
		Frame off = grabLastFrame(context, client.platform(), "waypoints-off");

		chat(context, ".toggle waypoints on");
		// A fresh list (an earlier run of the gate may have left some in this world's file).
		context.runOnClient(minecraft -> waypoints.all().forEach(waypoint -> waypoints.remove(waypoint.name())));
		chat(context, ".waypoints waypoints add Tower " + x + " " + y + " " + (z + 10) + " @test");
		chat(context, ".waypoints add-here Spawn");
		List<Waypoint> all = context.computeOnClient(minecraft -> waypoints.all());
		Waypoint tower = all.stream().filter(waypoint -> waypoint.name().equals("Tower")).findFirst().orElse(null);
		check("waypoints: added from chat with coordinates and a group", tower != null && tower.z() == z + 10 && tower.group().equals("test"));
		check("waypoints: added where you stand from the menu field", all.stream().anyMatch(waypoint -> waypoint.name().equals("Spawn")
			&& waypoint.x() == x && waypoint.z() == z));
		check("waypoints: kept in the dimension it was set in", tower.dimension().equals(Waypoint.OVERWORLD));

		// Seen ahead: the beam and box in the waypoint's colour, the label projected onto the block.
		context.waitTicks(10);
		Frame on = grabLastFrame(context, client.platform(), "waypoints");
		float scale = GameTestKit.guiScale(context);
		float[] label = context.computeOnClient(minecraft -> client.platform().visuals().project(x + 0.5, y + 1.4, z + 10.5));
		check("waypoints: the point projects onto the screen ahead", label != null && Math.abs(label[0] * scale - on.width() / 2.0F) < 20.0F);
		int left = (int) (label[0] * scale) - 60;
		int top = (int) (label[1] * scale) - 40;
		int violet = on.count(left, top, 120, 80, GameTestKit::isViolet);
		int violetOff = off.count(left, top, 120, 80, GameTestKit::isViolet);
		check("waypoints: label and box drawn where the point is (" + violetOff + " -> " + violet + " violet pixels)", violet > violetOff + 40);

		// A death marks itself.
		context.runOnClient(minecraft -> client.bus().post(new Events.Death(new Vec3(x + 3.2, y, z - 7.8), Waypoint.OVERWORLD)));
		check("waypoints: a death leaves a mark", context.computeOnClient(minecraft -> waypoints.all().stream()
			.anyMatch(waypoint -> waypoint.group().equals(Waypoint.DEATHS) && waypoint.x() == x + 3 && waypoint.z() == z - 8)));

		// Kept per server in the profile: off and on finds them; export writes a file.
		chat(context, ".toggle waypoints off");
		chat(context, ".toggle waypoints on");
		check("waypoints: still there after switching off and on", context.computeOnClient(minecraft -> waypoints.all().size()) == 3);
		context.runOnClient(minecraft -> ((ActionSetting) waypoints.setting("export")).press());
		check("waypoints: Export writes a file", context.computeOnClient(minecraft -> {
			try (var files = Files.walk(minecraft.gameDirectory.toPath().resolve("wisteria"))) {
				return files.anyMatch(file -> file.getParent().getFileName().toString().equals("exports")
					&& file.getFileName().toString().startsWith("waypoints-"));
			} catch (java.io.IOException e) {
				return false;
			}
		}));

		// Removing from the menu list.
		ListSetting list = (ListSetting) waypoints.setting("waypoints");
		String line = context.computeOnClient(minecraft -> list.entries().stream().filter(entry -> entry.startsWith("Tower ")).findFirst().orElseThrow());
		context.runOnClient(minecraft -> list.remove(line));
		check("waypoints: removed from the menu", context.computeOnClient(minecraft -> waypoints.all().stream().noneMatch(w -> w.name().equals("Tower"))));

		context.runOnClient(minecraft -> {
			waypoints.remove("Spawn");
			((ActionSetting) waypoints.setting("clear-deaths")).press();
		});
		chat(context, ".toggle waypoints off");
		chat(context, ".reset waypoints");
		GameTestKit.hideProbe(false);
	}
}
