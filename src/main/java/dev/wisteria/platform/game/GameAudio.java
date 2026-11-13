package dev.wisteria.platform.game;

import dev.wisteria.platform.AudioBackend;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
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

/** Client audio through the game's sound engine: a streamed, non-positional sound whose data we supply. */
public final class GameAudio implements AudioBackend {
	private static final AtomicInteger COUNTER = new AtomicInteger();

	@Override
	public Track play(Source source, float volume) {
		StreamTrack track = new StreamTrack(source, volume);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			minecraft.getSoundManager().play(track);
			track.started = true;
		});
		return track;
	}

	/**
	 * A sound that is not in any sounds.json. Category MASTER: only the master slider applies, and the
	 * engine re-reads {@link #getVolume()} every tick because the instance is tickable.
	 */
	private static final class StreamTrack extends AbstractSoundInstance implements TickableSoundInstance, Track {
		private final Source source;
		private volatile boolean stopped;
		private volatile boolean started;

		StreamTrack(Source source, float volume) {
			super(Identifier.fromNamespaceAndPath("wisteria", "track/" + COUNTER.incrementAndGet()), SoundSource.MASTER, SoundInstance.createUnseededRandom());
			this.source = source;
			this.volume = clamp(volume);
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			// stream = true: decoded in chunks on the sound thread instead of being loaded whole.
			this.sound = new Sound(this.identifier, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
		}

		// The game asks the instance for its sound event before playing. Up to 26.2 the method is called
		// resolve, from 26.3 getOrResolve; declaring both without @Override keeps this file common.
		public WeighedSoundEvents resolve(SoundManager soundManager) {
			return new WeighedSoundEvents(this.identifier, null);
		}

		public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
			return this.resolve(soundManager);
		}

		// Fabric API hook (FabricSoundInstance): supplies the stream instead of a resource-pack lookup.
		@Override
		public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return new JOrbisAudioStream(this.source.open());
				} catch (Exception e) {
					this.stopped = true;
					throw new IllegalStateException("Could not open audio source " + this.source.name(), e);
				}
			}, Util.nonCriticalIoPool());
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public boolean isStopped() {
			return this.stopped;
		}

		@Override
		public void tick() {
		}

		@Override
		public void setVolume(float volume) {
			this.volume = clamp(volume);
		}

		@Override
		public float volume() {
			return this.volume;
		}

		@Override
		public void stop() {
			this.stopped = true;
		}

		@Override
		public boolean isActive() {
			if (this.stopped) {
				return false;
			}

			return !this.started || Minecraft.getInstance().getSoundManager().isActive(this);
		}

		private static float clamp(float volume) {
			return Math.max(0.0F, Math.min(1.0F, volume));
		}
	}
}
