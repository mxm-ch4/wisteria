package dev.wisteria.modules.xray;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.jspecify.annotations.Nullable;

/**
 * Runs the ore prediction on its own thread so the game never waits for it: chunks the client receives come in as
 * snapshots, the chunks nearest the player are predicted first, and each prediction is published per chunk. A chunk's
 * ores come from decorating it and its eight neighbours (features reach across chunk borders); each decoration is
 * done once and redone only when a neighbour it had to assume solid arrives.
 */
final class XrayEngine {
	private static final long IDLE_NANOS = 50_000_000L;

	private final OrePredictor.Dimension dimension;
	private final long seed;
	private final int minY;
	private final int height;
	private final PalettedContainerFactory containers;
	private final Thread thread;
	private final ConcurrentLinkedQueue<OrePredictor.Base> incoming = new ConcurrentLinkedQueue<>();
	private final Map<Long, List<OrePredictor.Ore>> predictions = new ConcurrentHashMap<>();
	private final AtomicInteger version = new AtomicInteger();
	private final AtomicLong decorations = new AtomicLong();
	private final AtomicLong decorationNanos = new AtomicLong();
	private final AtomicLong preparedNanos = new AtomicLong();
	private final AtomicLong prepared = new AtomicLong();
	private volatile boolean running = true;
	private volatile boolean ready;
	private volatile @Nullable String error;
	private volatile long focus = ChunkPos.pack(0, 0);
	private volatile int radius = 3;

	// Worker thread only.
	private final Map<Long, OrePredictor.Base> bases = new HashMap<>();
	private final Map<Long, OrePredictor.Base> solids = new HashMap<>();
	private final Map<Long, OrePredictor.Decoration> decorated = new HashMap<>();
	private final LongOpenHashSet done = new LongOpenHashSet();
	private OrePredictor predictor;

	XrayEngine(OrePredictor.Dimension dimension, long seed, int minY, int height, PalettedContainerFactory containers) {
		this.dimension = dimension;
		this.seed = seed;
		this.minY = minY;
		this.height = height;
		this.containers = containers;
		this.thread = new Thread(this::run, "Wisteria Xray Predicted");
		this.thread.setDaemon(true);
		this.thread.setPriority(Thread.MIN_PRIORITY + 1);
		this.thread.start();
	}

	OrePredictor.Dimension dimension() {
		return this.dimension;
	}

	long seed() {
		return this.seed;
	}

	/** A chunk the client now has. Any thread. */
	void offer(OrePredictor.Base snapshot) {
		this.incoming.add(snapshot);
		LockSupport.unpark(this.thread);
	}

	/** Where the player is and how far out to predict, in chunks. Any thread. */
	void focus(int chunkX, int chunkZ, int radiusChunks) {
		long packed = ChunkPos.pack(chunkX, chunkZ);
		if (packed != this.focus || radiusChunks != this.radius) {
			this.focus = packed;
			this.radius = radiusChunks;
			LockSupport.unpark(this.thread);
		}
	}

	/** Predicted ores that lie in a chunk, or null if it has not been predicted yet. */
	@Nullable List<OrePredictor.Ore> ores(int chunkX, int chunkZ) {
		return this.predictions.get(ChunkPos.pack(chunkX, chunkZ));
	}

	/** Goes up each time a chunk's prediction is published. */
	int version() {
		return this.version.get();
	}

	/** True once the worldgen data is built and predicting has started. */
	boolean ready() {
		return this.ready;
	}

	/** Why the engine stopped, or null while it is fine. */
	@Nullable String error() {
		return this.error;
	}

	/** Mean time to decorate one chunk with every ore feature, in milliseconds. */
	double millisPerDecoration() {
		long count = this.decorations.get();
		return count == 0 ? 0.0 : this.decorationNanos.get() / 1e6 / count;
	}

	/** Mean time to turn a received chunk back into what generation saw, in milliseconds. */
	double millisPerPrepare() {
		long count = this.prepared.get();
		return count == 0 ? 0.0 : this.preparedNanos.get() / 1e6 / count;
	}

	long decorationCount() {
		return this.decorations.get();
	}

	void stop() {
		this.running = false;
		LockSupport.unpark(this.thread);
	}

	private void run() {
		OrePredictor predictor;
		try {
			predictor = this.predictor = new OrePredictor(this.dimension, this.seed, this.minY, this.height, this.containers);
			this.ready = true;
		} catch (RuntimeException | LinkageError failed) {
			this.error = "could not build the game's worldgen data: " + failed;
			return;
		}

		try {
			while (this.running) {
				this.drain(predictor);
				Long target = this.nextTarget();
				if (target == null) {
					this.evict();
					LockSupport.parkNanos(IDLE_NANOS);
					continue;
				}

				this.predict(predictor, ChunkPos.getX(target), ChunkPos.getZ(target));
			}
		} catch (RuntimeException | LinkageError failed) {
			this.error = "ore simulation failed: " + failed;
		}
	}

	private void drain(OrePredictor predictor) {
		OrePredictor.Base raw;
		while ((raw = this.incoming.poll()) != null) {
			long start = System.nanoTime();
			OrePredictor.Base base = predictor.prepare(raw);
			this.preparedNanos.addAndGet(System.nanoTime() - start);
			this.prepared.incrementAndGet();
			long key = ChunkPos.pack(base.chunkX(), base.chunkZ());
			this.bases.put(key, base);
			boolean wasAssumed = this.solids.remove(key) != null;
			if (wasAssumed) {
				// Decorations that assumed this chunk solid are redone, and every chunk they fed.
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long neighbour = ChunkPos.pack(base.chunkX() + dx, base.chunkZ() + dz);
						OrePredictor.Decoration decoration = this.decorated.get(neighbour);
						if (decoration != null && decoration.assumed().contains(key)) {
							this.decorated.remove(neighbour);
						}
					}
				}

				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						this.done.remove(ChunkPos.pack(base.chunkX() + dx, base.chunkZ() + dz));
					}
				}
			}

			// The chunk itself was predicted as unverified; now it can be checked: republish so it is reclassified.
			this.version.incrementAndGet();
		}
	}

	/** The nearest chunk within range of the player with no current prediction. */
	private @Nullable Long nextTarget() {
		int centreX = ChunkPos.getX(this.focus);
		int centreZ = ChunkPos.getZ(this.focus);
		int range = this.radius;
		for (int ring = 0; ring <= range; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					long key = ChunkPos.pack(centreX + dx, centreZ + dz);
					if (!this.done.contains(key)) {
						return key;
					}
				}
			}
		}

		return null;
	}

	private void predict(OrePredictor predictor, int chunkX, int chunkZ) {
		List<OrePredictor.Ore> inside = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int decoratedX = chunkX + dx;
				int decoratedZ = chunkZ + dz;
				long key = ChunkPos.pack(decoratedX, decoratedZ);
				OrePredictor.Decoration decoration = this.decorated.get(key);
				if (decoration == null) {
					long start = System.nanoTime();
					decoration = predictor.decorate(decoratedX, decoratedZ, this::base);
					this.decorationNanos.addAndGet(System.nanoTime() - start);
					this.decorations.incrementAndGet();
					this.decorated.put(key, decoration);
				}

				for (OrePredictor.Ore ore : decoration.ores()) {
					if (ore.x() >> 4 == chunkX && ore.z() >> 4 == chunkZ) {
						inside.add(ore);
					}
				}
			}
		}

		this.predictions.put(ChunkPos.pack(chunkX, chunkZ), List.copyOf(inside));
		this.done.add(ChunkPos.pack(chunkX, chunkZ));
		this.version.incrementAndGet();
	}

	private OrePredictor.Base base(int chunkX, int chunkZ) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		OrePredictor.Base base = this.bases.get(key);
		return base != null ? base : this.solids.computeIfAbsent(key, k -> this.predictor.solid(chunkX, chunkZ));
	}

	/** Forgets everything well outside the range, so walking far does not grow memory. */
	private void evict() {
		int centreX = ChunkPos.getX(this.focus);
		int centreZ = ChunkPos.getZ(this.focus);
		int keep = this.radius + 4;
		this.bases.keySet().removeIf(key -> far(key, centreX, centreZ, keep));
		this.solids.keySet().removeIf(key -> far(key, centreX, centreZ, keep));
		this.decorated.keySet().removeIf(key -> far(key, centreX, centreZ, keep));
		this.done.removeIf(key -> far(key, centreX, centreZ, keep));
		this.predictions.keySet().removeIf(key -> far(key, centreX, centreZ, keep));
	}

	private static boolean far(long key, int centreX, int centreZ, int keep) {
		return Math.abs(ChunkPos.getX(key) - centreX) > keep || Math.abs(ChunkPos.getZ(key) - centreZ) > keep;
	}
}
