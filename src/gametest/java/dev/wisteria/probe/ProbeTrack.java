package dev.wisteria.probe;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Plays an ogg that is not in sounds.json: the stream comes from wherever we like (here the classpath,
 * later a file in .minecraft/wisteria/music/), and the volume can change while it plays.
 */
public final class ProbeTrack extends AbstractSoundInstance implements TickableSoundInstance {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(Probe.ID, "probe_tone");
	private final String resource;
	private volatile boolean stopped;

	public ProbeTrack(String resource, float volume) {
		super(ID, SoundSource.MASTER, SoundInstance.createUnseededRandom());
		this.resource = resource;
		this.volume = volume;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		// stream = true: decoded in chunks on the sound thread, never loaded whole.
		this.sound = new Sound(ID, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
	}

	public void setVolume(float volume) {
		this.volume = volume;
	}

	public void stopPlaying() {
		this.stopped = true;
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager soundManager) {
		return new WeighedSoundEvents(ID, null);
	}

	// Fabric API hook (FabricSoundInstance): replaces the lookup of the sound in resource packs.
	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				InputStream input = ProbeTrack.class.getResourceAsStream(this.resource);
				if (input == null) {
					throw new IllegalStateException("Missing " + this.resource);
				}

				return new JOrbisAudioStream(input);
			} catch (Exception e) {
				throw new IllegalStateException("Could not open " + this.resource, e);
			}
		}, Util.nonCriticalIoPool());
	}

	@Override
	public boolean isStopped() {
		return this.stopped;
	}

	@Override
	public void tick() {
	}
}
