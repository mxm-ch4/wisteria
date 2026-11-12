package dev.wisteria.probe;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/** Drives the probe in a real client: title screen, a fresh world, input, packets, audio; then checks the counters. */
public final class ProbeGameTest implements FabricClientGameTest {
	private static final String TONE = "/assets/wisteria-probe/probe_tone.ogg";

	@Override
	public void runTest(ClientGameTestContext context) {
		String backend = context.computeOnClient(minecraft -> minecraft.getWindow().backend().getClass().getSimpleName());
		Probe.LOG.info("graphics backend: {}", backend);
		context.takeScreenshot("probe-title");

		this.audio(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			context.runOnClient(minecraft -> {
				BlockPos feet = minecraft.player.blockPosition();
				Probe.espTarget = feet.relative(minecraft.player.getDirection(), 5).above(1);
			});
			context.waitTicks(20);
			context.takeScreenshot("probe-world");

			int keysBefore = Probe.KEY_EVENTS.get();
			context.getInput().pressKey(GLFW.GLFW_KEY_G);
			context.waitTicks(5);
			check("key mixin", Probe.KEY_EVENTS.get() > keysBefore);

			// Cancellation: drop our own swing packets and count them.
			Probe.dropOutgoing = ServerboundSwingPacket.class;
			context.runOnClient(minecraft -> minecraft.player.swing(InteractionHand.MAIN_HAND));
			context.waitTicks(5);
			Probe.dropOutgoing = null;
			check("packet cancel", Probe.PACKETS_CANCELLED.get() > 0);

			context.setScreen(ProbeScreen::new);
			context.waitTicks(10);
			context.takeScreenshot("probe-screen");
			context.setScreen(() -> null);
			context.waitTicks(5);

			Probe.LOG.info("tick: mixin={} fabric={}", Probe.TICKS_MIXIN.get(), Probe.TICKS_FABRIC.get());
			Probe.LOG.info("render2D: hudMixin={} hudFabric={}", Probe.HUD_MIXIN.get(), Probe.HUD_FABRIC.get());
			Probe.LOG.info("render3D: levelExtractions={}", Probe.LEVEL_EXTRACTIONS.get());
			Probe.LOG.info("key: events={} last=[{}]", Probe.KEY_EVENTS.get(), Probe.lastKey);
			Probe.LOG.info("packet: sent={} received={} cancelled={}", Probe.PACKETS_SENT.get(), Probe.PACKETS_RECEIVED.get(), Probe.PACKETS_CANCELLED.get());
			Probe.LOG.info("packet types sent: {}", Probe.SENT_TYPES.stream().sorted().toList());
			Probe.LOG.info("packet types received: {} kinds, e.g. {}", Probe.RECEIVED_TYPES.size(), Probe.RECEIVED_TYPES.stream().sorted().limit(8).toList());

			check("tick mixin", Probe.TICKS_MIXIN.get() > 0);
			check("tick event", Probe.TICKS_FABRIC.get() > 0);
			check("hud mixin", Probe.HUD_MIXIN.get() > 0);
			check("hud element", Probe.HUD_FABRIC.get() > 0);
			check("level extraction", Probe.LEVEL_EXTRACTIONS.get() > 0);
			check("packets sent", Probe.PACKETS_SENT.get() > 0);
			check("packets received", Probe.PACKETS_RECEIVED.get() > 0);
			Probe.espTarget = null;
		}

		Probe.LOG.info("probe finished: all checks passed");
	}

	/** 2 s at full volume, 2 s at a quarter, then 2 s with the vanilla music slider at zero and a UI click on top. */
	private void audio(ClientGameTestContext context) {
		ProbeTrack track = new ProbeTrack(TONE, 1.0F);
		String result = context.computeOnClient(minecraft -> minecraft.getSoundManager().play(track).toString());
		Probe.LOG.info("audio: play -> {} (volume 1.0)", result);
		context.waitTicks(40);

		context.runOnClient(minecraft -> track.setVolume(0.25F));
		Probe.LOG.info("audio: volume -> 0.25");
		context.waitTicks(40);

		context.runOnClient(minecraft -> {
			minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
			track.setVolume(1.0F);
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		});
		Probe.LOG.info("audio: music slider -> 0, volume -> 1.0, vanilla UI click played on top");
		context.waitTicks(40);

		boolean active = context.computeOnClient(minecraft -> minecraft.getSoundManager().isActive(track));
		Probe.LOG.info("audio: still active before stop = {}", active);
		context.runOnClient(minecraft -> track.stopPlaying());
		context.waitTicks(10);
		boolean activeAfter = context.computeOnClient(minecraft -> minecraft.getSoundManager().isActive(track));
		Probe.LOG.info("audio: stopped, active = {}", activeAfter);
	}

	private static void check(String what, boolean ok) {
		if (!ok) {
			throw new AssertionError("Probe failed: " + what);
		}

		Probe.LOG.info("ok: {}", what);
	}
}
