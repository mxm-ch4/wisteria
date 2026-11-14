package dev.wisteria.gate.modules;

import static dev.wisteria.gate.GameTestKit.chat;
import static dev.wisteria.gate.GameTestKit.check;
import static dev.wisteria.gate.GameTestKit.grabLastFrame;

import dev.wisteria.Client;
import dev.wisteria.Wisteria;
import dev.wisteria.gate.GameTestKit;
import dev.wisteria.modules.client.SeedsSection;
import dev.wisteria.modules.xray.OreKind;
import dev.wisteria.modules.xray.XrayLogic;
import dev.wisteria.modules.xray.XrayPredicted;
import dev.wisteria.setting.PasswordSetting;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Variant D probe and Xray Predicted test (SPEC_PASSIVE): a default world with a known, made-up seed; the module
 * predicts 20 chunks from the seed alone; the server's real blocks are the answer key. Then the A5.1 §6 table on live
 * blocks: an ore hidden as stone stays marked as predicted, an ore swapped for another as a fake, a mined one goes.
 */
public final class XrayGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("WisteriaGate");
	/** A made-up seed for a throwaway test world; no server's seed. */
	private static final long SEED = 20261114L;
	/** Ores that only features make; iron and copper also come in noise veins, which the prediction leaves out. */
	private static final Set<OreKind> FEATURE_ONLY = Set.of(OreKind.COAL, OreKind.GOLD, OreKind.REDSTONE, OreKind.LAPIS, OreKind.DIAMOND,
		OreKind.EMERALD);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (GameTestKit.isAuditPhase() || GameTestKit.isRestartPhase()) {
			return;
		}

		Client client = Wisteria.client();
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(XrayGameTest::defaultWorld).create()) {
			world.getConnection().waitForChunksRender();
			this.test(context, world, client);
		}
	}

	private static void defaultWorld(WorldCreationUiState state) {
		state.setWorldType(new WorldCreationUiState.WorldTypeEntry(
			state.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL)));
		state.setSeed(Long.toString(SEED));
	}

	private void test(ClientGameTestContext context, TestSingleplayerContext world, Client client) {
		GameTestKit.hideProbe(true);
		world.getServer().runCommand("time set noon");
		XrayPredicted xray = (XrayPredicted) client.modules().get("xraypredicted");
		SeedsSection seeds = client.modules().get(SeedsSection.class);

		// No seed yet: on, but nothing to draw, and it says why.
		chat(context, ".toggle xraypredicted on");
		context.waitTicks(10);
		check("xray: without a seed it says where to set one (" + xray.status() + ")", xray.status().contains("Seeds"));

		// The seed goes in through the masked field, the only way in; chat refuses it.
		PasswordSetting field = (PasswordSetting) seeds.setting("seed");
		boolean chatRefused;
		try {
			field.parse("123456789");
			chatRefused = false;
		} catch (IllegalArgumentException expected) {
			chatRefused = true;
		}

		check("xray: the seed field refuses chat input", chatRefused);
		context.runOnClient(minecraft -> ((dev.wisteria.setting.ActionSetting) seeds.setting("use-world-seed")).press());
		check("xray: Use World Seed takes this world's seed", context.computeOnClient(minecraft -> seeds.current().orElse(0L)) == SEED);
		context.runOnClient(minecraft -> field.store(""));
		check("xray: an empty field forgets it", context.computeOnClient(minecraft -> seeds.current().isEmpty()));
		context.runOnClient(minecraft -> field.store(Long.toString(SEED)));
		check("xray: the seed is filed for this world", context.computeOnClient(minecraft -> seeds.current().orElse(0L)) == SEED);
		check("xray: the chat guard holds back a line with the seed",
			context.computeOnClient(minecraft -> client.privacy().guard("the seed is " + SEED)) != null);

		// Predict: 5 × 4 chunks around the player, every neighbour loaded so nothing is assumed.
		BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
		int centreX = feet.getX() >> 4;
		int centreZ = feet.getZ() >> 4;
		List<int[]> chunks = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 1; dz++) {
				chunks.add(new int[] {centreX + dx, centreZ + dz});
			}
		}

		long started = System.nanoTime();
		int waited = 0;
		while (waited < 2400 && !this.allPredicted(context, xray, chunks)) {
			context.waitTicks(20);
			waited += 20;
		}

		check("xray: worldgen built and 20 chunks predicted within " + waited / 20 + " s (" + xray.status() + ")", this.allPredicted(context, xray, chunks));
		double seconds = (System.nanoTime() - started) / 1e9;
		context.waitTicks(40);

		// The answer key: the server's own blocks.
		Map<Long, OreKind> predicted = new HashMap<>();
		context.runOnClient(minecraft -> {
			for (int[] chunk : chunks) {
				for (XrayPredicted.Spot spot : xray.spotsIn(chunk[0], chunk[1])) {
					predicted.put(BlockPos.asLong(spot.x(), spot.y(), spot.z()), spot.kind());
				}
			}
		});
		Map<Long, OreKind> actual = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Map<Long, OreKind> ores = new HashMap<>();
			for (int[] chunk : chunks) {
				LevelChunk real = level.getChunk(chunk[0], chunk[1]);
				LevelChunkSection[] sections = real.getSections();
				for (int i = 0; i < sections.length; i++) {
					int baseY = level.getMinY() + i * 16;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								OreKind kind = OreKind.ofBlock(BuiltInRegistries.BLOCK.getKey(sections[i].getBlockState(x, y, z).getBlock()).toString());
								if (kind != null) {
									ores.put(BlockPos.asLong(chunk[0] * 16 + x, baseY + y, chunk[1] * 16 + z), kind);
								}
							}
						}
					}
				}
			}

			return ores;
		});

		int hits = 0;
		int featureActual = 0;
		int featureHits = 0;
		Map<OreKind, int[]> byKind = new java.util.EnumMap<>(OreKind.class);
		for (Map.Entry<Long, OreKind> entry : predicted.entrySet()) {
			boolean hit = actual.get(entry.getKey()) == entry.getValue();
			hits += hit ? 1 : 0;
			byKind.computeIfAbsent(entry.getValue(), k -> new int[3])[0]++;
			byKind.get(entry.getValue())[1] += hit ? 1 : 0;
		}

		for (Map.Entry<Long, OreKind> entry : actual.entrySet()) {
			byKind.computeIfAbsent(entry.getValue(), k -> new int[3])[2]++;
			if (FEATURE_ONLY.contains(entry.getValue())) {
				featureActual++;
				featureHits += predicted.get(entry.getKey()) == entry.getValue() ? 1 : 0;
			}
		}

		double precision = predicted.isEmpty() ? 0.0 : (double) hits / predicted.size();
		double recall = actual.isEmpty() ? 0.0 : (double) hits / actual.size();
		double featureRecall = featureActual == 0 ? 0.0 : (double) featureHits / featureActual;
		StringBuilder report = new StringBuilder();
		report.append(String.format(Locale.ROOT, "seed %d, 20 chunks around %d %d%n", SEED, centreX, centreZ));
		report.append(String.format(Locale.ROOT, "predicted %d, real %d, both %d%n", predicted.size(), actual.size(), hits));
		report.append(String.format(Locale.ROOT, "precision %.4f, recall %.4f, recall of feature-only ores %.4f%n", precision, recall, featureRecall));
		report.append(String.format(Locale.ROOT, "%.2f ms per chunk decoration, %.2f ms per received chunk prepared, first 20 chunks in %.1f s (with worldgen data)%n",
			xray.millisPerChunk(), xray.millisPerPrepare(), seconds));
		byKind.forEach((kind, counts) -> report.append(String.format(Locale.ROOT, "  %-14s predicted %5d  hit %5d  real %5d%n", kind.title(), counts[0], counts[1], counts[2])));
		// Where the misses are: by height, and what the server has at a predicted-only spot.
		Map<String, Integer> misses = new java.util.TreeMap<>();
		List<Long> extra = new ArrayList<>();
		for (Map.Entry<Long, OreKind> entry : predicted.entrySet()) {
			if (actual.get(entry.getKey()) != entry.getValue()) {
				misses.merge(entry.getValue().title() + " extra y" + (BlockPos.getY(entry.getKey()) >> 4 << 4), 1, Integer::sum);
				extra.add(entry.getKey());
			}
		}

		for (Map.Entry<Long, OreKind> entry : actual.entrySet()) {
			if (predicted.get(entry.getKey()) != entry.getValue()) {
				misses.merge(entry.getValue().title() + " missed y" + (BlockPos.getY(entry.getKey()) >> 4 << 4), 1, Integer::sum);
			}
		}

		Map<String, Integer> found = world.getServer().computeOnServer(server -> {
			Map<String, Integer> blocks = new java.util.TreeMap<>();
			for (long key : extra) {
				blocks.merge(BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(BlockPos.of(key)).getBlock()).toString(), 1, Integer::sum);
			}

			return blocks;
		});
		misses.forEach((what, count) -> report.append("  miss ").append(what).append(": ").append(count).append('\n'));
		found.forEach((what, count) -> report.append("  at extra spots the server has ").append(what).append(": ").append(count).append('\n'));
		LOG.info("Xray variant D probe:\n{}", report);
		try {
			java.nio.file.Path file = GameTestKit.gateDirectory(context).resolve("xray-probe.txt");
			Files.createDirectories(file.getParent());
			Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
		} catch (java.io.IOException failed) {
			throw new AssertionError("could not write xray-probe.txt", failed);
		}

		check("xray: predictions match the real world (precision " + precision + ")", precision >= 0.9);
		check("xray: feature ores are found (recall " + featureRecall + ")", featureRecall >= 0.9);

		// The table on live blocks: pick three confirmed ores and change what the client sees.
		List<XrayPredicted.Spot> confirmed = new ArrayList<>();
		context.runOnClient(minecraft -> {
			for (int[] chunk : chunks) {
				for (XrayPredicted.Spot spot : xray.spotsIn(chunk[0], chunk[1])) {
					if (confirmed.size() < 3 && xray.verdictAt(spot.x(), spot.y(), spot.z()) == XrayLogic.Verdict.CONFIRMED) {
						confirmed.add(spot);
					}
				}
			}
		});
		check("xray: the client sees predicted ores as confirmed", confirmed.size() == 3);
		XrayPredicted.Spot hidden = confirmed.get(0);
		XrayPredicted.Spot faked = confirmed.get(1);
		XrayPredicted.Spot mined = confirmed.get(2);
		String fake = hidden.kind() == OreKind.EMERALD || faked.kind() == OreKind.EMERALD ? "minecraft:diamond_ore" : "minecraft:emerald_ore";
		world.getServer().runCommand("setblock " + hidden.x() + " " + hidden.y() + " " + hidden.z() + " minecraft:stone");
		world.getServer().runCommand("setblock " + faked.x() + " " + faked.y() + " " + faked.z() + " " + fake);
		world.getServer().runCommand("setblock " + mined.x() + " " + mined.y() + " " + mined.z() + " minecraft:air");
		context.waitTicks(10);
		check("xray: an ore the server shows as stone is still predicted",
			context.computeOnClient(minecraft -> xray.verdictAt(hidden.x(), hidden.y(), hidden.z())) == XrayLogic.Verdict.PREDICTED);
		check("xray: an ore swapped for another (a fake) is predicted",
			context.computeOnClient(minecraft -> xray.verdictAt(faked.x(), faked.y(), faked.z())) == XrayLogic.Verdict.PREDICTED);
		check("xray: a mined-out ore is dropped",
			context.computeOnClient(minecraft -> xray.verdictAt(mined.x(), mined.y(), mined.z())) == XrayLogic.Verdict.NONE);

		// The picture: every ore on, looking down into the ground.
		context.runOnClient(minecraft -> {
			for (OreKind kind : OreKind.values()) {
				xray.settings().stream().filter(setting -> setting.name().equals(kind.title())).findFirst().orElseThrow().parse("true");
			}
		});
		world.getServer().runCommand("tp @a " + feet.getX() + " " + (feet.getY() + 6) + " " + feet.getZ() + " 30 60");
		context.waitTicks(40);
		XrayPredicted.Counts counts = context.computeOnClient(minecraft -> xray.counts());
		grabLastFrame(context, client.platform(), "xray-predicted");
		check("xray: boxes are drawn (" + counts + ")", counts.confirmed() + counts.predicted() > 50);

		context.runOnClient(minecraft -> field.store(""));
		check("xray: Forget removes the seed", context.computeOnClient(minecraft -> seeds.current().isEmpty()));
		chat(context, ".toggle xraypredicted off");
		chat(context, ".reset xraypredicted");
		GameTestKit.hideProbe(false);
	}

	private boolean allPredicted(ClientGameTestContext context, XrayPredicted xray, List<int[]> chunks) {
		return context.computeOnClient(minecraft -> {
			for (int[] chunk : chunks) {
				if (xray.spotsIn(chunk[0], chunk[1]) == null) {
					return false;
				}
			}

			return true;
		});
	}
}
