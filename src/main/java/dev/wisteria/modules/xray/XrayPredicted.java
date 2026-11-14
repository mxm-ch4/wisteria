package dev.wisteria.modules.xray;

import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.client.SeedsSection;
import dev.wisteria.render.BoxBatch;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.FloatSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.IntSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Shows where the world seed puts ores, through walls, generated on the client by the game's own ore code (variant D
 * of SPEC_PASSIVE). Each spot is checked against what the client sees (SPEC_AMENDMENT_5.1 §6): the ore itself is
 * <em>confirmed</em>, rock or a different ore (a server's fake) is <em>predicted</em>, an unloaded chunk is
 * <em>unverified</em>, air or anything else is dropped. The seed is set per server under Client → Seeds.
 * Passive: nothing is sent to the server.
 */
public final class XrayPredicted extends Module {
	/** A predicted ore, for tests and other modules. */
	public record Spot(int x, int y, int z, OreKind kind) {
	}

	/** How many of each verdict the last rebuild drew. */
	public record Counts(int confirmed, int predicted, int unverified) {
	}

	private static final int REBUILD_TICKS = 10;

	private final DoubleSetting range = this.add(new DoubleSetting("Range", "How far around you, in blocks (the server decides how many chunks you get)",
		48.0, 16.0, 160.0, 8.0));
	private final Map<OreKind, BoolSetting> oreToggles = new EnumMap<>(OreKind.class);
	private final BoolSetting showConfirmed = this.add(new BoolSetting("Show Confirmed", "Also mark ores the client already sees", true));
	private final BoolSetting showUnverified = this.add(new BoolSetting("Show Unverified", "Mark predictions in chunks the client does not have", true));
	private final ColorSetting confirmedColor = this.add(new ColorSetting("Confirmed Color", "The client sees this very ore", 0xFF8CE0B5));
	private final ColorSetting predictedColor = this.add(new ColorSetting("Predicted Color", "The seed says ore, the client shows rock or a fake", 0xFFB79CFF));
	private final ColorSetting unverifiedColor = this.add(new ColorSetting("Unverified Color", "The seed says ore; the chunk is not loaded", 0xFF9A92AD));
	private final IntSetting fill = this.add(new IntSetting("Fill", "Opacity of the box faces, 0 for outline only", 40, 0, 255));
	private final FloatSetting width = this.add(new FloatSetting("Line Width", "Box outline thickness", 1.5F, 0.5F, 4.0F, 0.5F));
	private final BoolSetting throughWalls = this.add(new BoolSetting("Through Walls", "Draw over terrain; off to let blocks hide them", true));
	private final IntSetting limit = this.add(new IntSetting("Limit", "At most this many boxes, nearest first", 4000, 100, 20000));

	private BoxBatch batch = new BoxBatch(1.5F, true);
	private final Map<Block, String> blockIds = new IdentityHashMap<>();
	private @Nullable XrayEngine engine;
	private String status = "off";
	private int builtVersion = -1;
	private int ticksSinceBuild;
	private boolean dirty;
	private double builtX;
	private double builtZ;
	private Counts counts = new Counts(0, 0, 0);

	public XrayPredicted() {
		super("Xray Predicted", "Ores where the world seed puts them, checked against what you see; set the seed under Client > Seeds",
			Category.RENDER);
		for (OreKind kind : OreKind.values()) {
			boolean on = kind == OreKind.DIAMOND || kind == OreKind.EMERALD || kind == OreKind.GOLD || kind == OreKind.LAPIS || kind == OreKind.DEBRIS;
			BoolSetting toggle = this.add(new BoolSetting(kind.title(), "Show " + kind.title().toLowerCase(Locale.ROOT) + " ore", on));
			toggle.onChange(value -> this.dirty = true);
			this.oreToggles.put(kind, toggle);
		}

		this.add(new InfoSetting("Status", "What the prediction is doing", () -> this.status));
		this.range.onChange(value -> this.dirty = true);
		this.showConfirmed.onChange(value -> this.dirty = true);
		this.showUnverified.onChange(value -> this.dirty = true);
		this.confirmedColor.onChange(value -> this.dirty = true);
		this.predictedColor.onChange(value -> this.dirty = true);
		this.unverifiedColor.onChange(value -> this.dirty = true);
		this.fill.onChange(value -> this.dirty = true);
		this.limit.onChange(value -> this.dirty = true);
		this.width.onChange(value -> this.dirty = true);
		this.throughWalls.onChange(value -> this.batch.setThroughWalls(value));

		this.on(Events.Tick.class, event -> this.tick());
		this.on(Events.ChunkLoad.class, event -> this.offer(event.chunk()));
		this.on(Events.BlockUpdate.class, event -> this.dirty = true);
		this.on(Events.Leave.class, event -> this.stopEngine("not in a world"));
		this.on(Events.Render3D.class, event -> {
			if (this.batch.size() > 0) {
				event.renderer().boxes(this.batch);
			}
		});
	}

	@Override
	protected void onEnable() {
		this.builtVersion = -1;
		this.dirty = true;
	}

	@Override
	protected void onDisable() {
		this.stopEngine("off");
		this.batch.clear();
		this.counts = new Counts(0, 0, 0);
	}

	/** One line on what it is doing: waiting for a seed, building, how many chunks and how long each took. */
	public String status() {
		return this.status;
	}

	public Counts counts() {
		return this.counts;
	}

	/** True once predictions are being made for the current world. */
	public boolean ready() {
		XrayEngine current = this.engine;
		return current != null && current.ready() && current.error() == null;
	}

	/** The ores predicted in a chunk, before any filter; null until it has been predicted. */
	public @Nullable List<Spot> spotsIn(int chunkX, int chunkZ) {
		XrayEngine current = this.engine;
		List<OrePredictor.Ore> ores = current == null ? null : current.ores(chunkX, chunkZ);
		if (ores == null) {
			return null;
		}

		List<Spot> spots = new ArrayList<>(ores.size());
		for (OrePredictor.Ore ore : ores) {
			spots.add(new Spot(ore.x(), ore.y(), ore.z(), ore.kind()));
		}

		return spots;
	}

	/**
	 * How a predicted spot reads right now against what the client sees, ignoring filters and range; null if nothing is
	 * predicted there (or its chunk has not been predicted yet).
	 */
	public XrayLogic.@Nullable Verdict verdictAt(int x, int y, int z) {
		XrayEngine current = this.engine;
		ClientLevel level = this.level();
		List<OrePredictor.Ore> ores = current == null || level == null ? null : current.ores(x >> 4, z >> 4);
		if (ores == null) {
			return null;
		}

		for (OrePredictor.Ore ore : ores) {
			if (ore.x() == x && ore.y() == y && ore.z() == z) {
				return XrayLogic.classify(this.seenAt(level, ore));
			}
		}

		return null;
	}

	private XrayLogic.Seen seenAt(ClientLevel level, OrePredictor.Ore ore) {
		if (level.getChunkSource().getChunk(ore.x() >> 4, ore.z() >> 4, ChunkStatus.FULL, false) == null) {
			return XrayLogic.Seen.UNLOADED;
		}

		return XrayLogic.see(this.idOf(level.getBlockState(new BlockPos(ore.x(), ore.y(), ore.z())).getBlock()), ore.kind());
	}

	/** Mean time to run every ore feature for one chunk, in milliseconds; 0 before the first. */
	public double millisPerChunk() {
		XrayEngine current = this.engine;
		return current == null ? 0.0 : current.millisPerDecoration();
	}

	/** Mean time to turn a received chunk back into rock for the simulation, in milliseconds. */
	public double millisPerPrepare() {
		XrayEngine current = this.engine;
		return current == null ? 0.0 : current.millisPerPrepare();
	}

	private int radiusChunks() {
		return (int) Math.ceil(this.range.get() / 16.0);
	}

	private void tick() {
		ClientLevel level = this.level();
		LocalPlayer player = this.player();
		if (level == null || player == null) {
			return;
		}

		XrayEngine current = this.ensureEngine(level);
		if (current == null) {
			if (this.batch.size() > 0) {
				this.batch.clear();
				this.counts = new Counts(0, 0, 0);
			}

			return;
		}

		if (current.error() != null) {
			String error = current.error();
			this.stopEngine("stopped: " + error);
			this.client().hud().notify("Xray Predicted", "Stopped: " + error, dev.wisteria.hud.Toasts.Kind.WARNING);
			this.info("Xray Predicted stopped: " + error);
			return;
		}

		current.focus(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, this.radiusChunks());
		this.ticksSinceBuild++;
		double moved = Math.hypot(player.getX() - this.builtX, player.getZ() - this.builtZ);
		boolean changed = current.version() != this.builtVersion || this.dirty || moved > 4.0;
		if (changed && (this.ticksSinceBuild >= REBUILD_TICKS || this.builtVersion < 0)) {
			this.rebuild(level, player, current);
		}

		this.status = current.ready()
			? String.format(Locale.ROOT, "%d confirmed, %d predicted, %d unverified; %.1f ms per chunk", this.counts.confirmed(),
				this.counts.predicted(), this.counts.unverified(), current.millisPerDecoration())
			: "building the game's worldgen data…";
	}

	private @Nullable XrayEngine ensureEngine(ClientLevel level) {
		OrePredictor.Dimension dimension = level.dimension() == Level.OVERWORLD ? OrePredictor.Dimension.OVERWORLD
			: level.dimension() == Level.NETHER ? OrePredictor.Dimension.NETHER : null;
		if (dimension == null) {
			this.stopEngine("no ores to predict in this dimension");
			return null;
		}

		SeedsSection seeds = this.client().modules().get(SeedsSection.class);
		OptionalLong seed = seeds == null ? OptionalLong.empty() : seeds.current();
		if (seed.isEmpty()) {
			this.stopEngine("no seed for this server: set it under Client > Seeds");
			return null;
		}

		XrayEngine current = this.engine;
		if (current != null && current.dimension() == dimension && current.seed() == seed.getAsLong()) {
			return current;
		}

		this.stopEngine("starting");
		current = new XrayEngine(dimension, seed.getAsLong(), level.getMinY(), level.getHeight(), level.palettedContainerFactory());
		this.engine = current;
		this.builtVersion = -1;

		// Everything already loaded around the player.
		LocalPlayer player = this.player();
		if (player != null) {
			int centreX = player.blockPosition().getX() >> 4;
			int centreZ = player.blockPosition().getZ() >> 4;
			int reach = this.radiusChunks() + 2;
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false);
					if (chunk != null) {
						current.offer(snapshot(chunk));
					}
				}
			}
		}

		return current;
	}

	private void offer(LevelChunk chunk) {
		XrayEngine current = this.engine;
		ClientLevel level = this.level();
		if (current != null && level != null && chunk.getLevel() == level) {
			current.offer(snapshot(chunk));
		}
	}

	private static OrePredictor.Base snapshot(LevelChunk chunk) {
		int[] surface = new int[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				surface[x + z * 16] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
			}
		}

		return OrePredictor.snapshot(chunk.getPos().x(), chunk.getPos().z(), chunk.getSections(), surface);
	}

	private void stopEngine(String why) {
		XrayEngine current = this.engine;
		if (current != null) {
			current.stop();
			this.engine = null;
		}

		this.status = why;
	}

	private record Candidate(int x, int y, int z, XrayLogic.Verdict verdict, double distance) {
	}

	private void rebuild(ClientLevel level, LocalPlayer player, XrayEngine current) {
		this.builtVersion = current.version();
		this.ticksSinceBuild = 0;
		this.dirty = false;
		this.builtX = player.getX();
		this.builtZ = player.getZ();

		double range = this.range.get();
		int radius = this.radiusChunks();
		int centreX = player.blockPosition().getX() >> 4;
		int centreZ = player.blockPosition().getZ() >> 4;
		List<Candidate> candidates = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int chunkX = centreX + dx;
				int chunkZ = centreZ + dz;
				List<OrePredictor.Ore> ores = current.ores(chunkX, chunkZ);
				if (ores == null) {
					continue;
				}

				boolean loaded = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
				for (OrePredictor.Ore ore : ores) {
					if (!this.oreToggles.get(ore.kind()).get() || !XrayLogic.inRange(ore.x(), ore.z(), player.getX(), player.getZ(), range)) {
						continue;
					}

					XrayLogic.Seen seen;
					if (loaded) {
						pos.set(ore.x(), ore.y(), ore.z());
						seen = XrayLogic.see(this.idOf(level.getBlockState(pos).getBlock()), ore.kind());
					} else {
						seen = XrayLogic.Seen.UNLOADED;
					}

					XrayLogic.Verdict verdict = XrayLogic.classify(seen);
					if (verdict == XrayLogic.Verdict.NONE
						|| verdict == XrayLogic.Verdict.CONFIRMED && !this.showConfirmed.get()
						|| verdict == XrayLogic.Verdict.UNVERIFIED && !this.showUnverified.get()) {
						continue;
					}

					double distance = Math.hypot(ore.x() + 0.5 - player.getX(), ore.z() + 0.5 - player.getZ()) + Math.abs(ore.y() + 0.5 - player.getY()) * 0.5;
					candidates.add(new Candidate(ore.x(), ore.y(), ore.z(), verdict, distance));
				}
			}
		}

		if (candidates.size() > this.limit.get()) {
			candidates.sort(Comparator.comparingDouble(Candidate::distance));
			candidates = candidates.subList(0, this.limit.get());
		}

		if (this.batch.strokeWidth() != this.width.get()) {
			this.batch = new BoxBatch(this.width.get(), this.throughWalls.get());
		}

		this.batch.clear();
		this.batch.setThroughWalls(this.throughWalls.get());
		int confirmed = 0;
		int predicted = 0;
		int unverified = 0;
		int alpha = this.fill.get();
		for (Candidate candidate : candidates) {
			int stroke = switch (candidate.verdict()) {
				case CONFIRMED -> {
					confirmed++;
					yield this.confirmedColor.get();
				}
				case UNVERIFIED -> {
					unverified++;
					yield this.unverifiedColor.get();
				}
				default -> {
					predicted++;
					yield this.predictedColor.get();
				}
			};
			int fillArgb = (alpha << 24) | (stroke & 0xFFFFFF);
			this.batch.add(candidate.x() + 0.05, candidate.y() + 0.05, candidate.z() + 0.05,
				candidate.x() + 0.95, candidate.y() + 0.95, candidate.z() + 0.95, stroke, fillArgb);
		}

		this.counts = new Counts(confirmed, predicted, unverified);
	}

	private String idOf(Block block) {
		return this.blockIds.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
	}
}
