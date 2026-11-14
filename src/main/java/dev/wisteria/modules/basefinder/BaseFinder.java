package dev.wisteria.modules.basefinder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.wisteria.event.Events;
import dev.wisteria.hud.Toasts;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.modules.client.SeedsSection;
import dev.wisteria.render.BoxBatch;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.EnumSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.setting.ListSetting;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * Finds signs of players in every chunk the server sends: storage, redstone, utility blocks, crafted building blocks,
 * lit portals, spawners. Rates each chunk, keeps the best per server in the profile, lists them in the menu and marks
 * them in the world. Scans a few chunks per frame, nearest first, within a time budget. Passive: it only reads chunks
 * the client already has; nothing is sent to the server.
 */
public final class BaseFinder extends Module {
	private static final int SPOTS_PER_ENTRY = 48;
	private static final long SAVE_EVERY_MS = 5_000L;

	private final DoubleSetting range = this.add(new DoubleSetting("Range",
		"Scan chunks this close, in blocks. Only chunks the server sends can be scanned: the server decides how far you see", 256.0, 32.0, 1024.0, 16.0));
	private final DoubleSetting minScore = this.add(new DoubleSetting("Min Score", "A chunk is listed from this rating up", 10.0, 1.0, 100.0, 0.5));
	private final IntSetting minBlocks = this.add(new IntSetting("Min Blocks", "…and with at least this many signs", 3, 1, 64));
	private final IntSetting limit = this.add(new IntSetting("Limit", "Keep at most this many per server, best first", 100, 10, 1000));
	private final DoubleSetting budget = this.add(new DoubleSetting("Budget", "Milliseconds per frame spent scanning", 1.5, 0.25, 8.0, 0.25));
	private final Map<BaseLogic.Feature, BoolSetting> features = new EnumMap<>(BaseLogic.Feature.class);
	private final EnumSetting<BaseLogic.Sort> sort = this.add(new EnumSetting<>("Sort", "Order of the list: best rating or nearest", BaseLogic.Sort.SCORE));
	private final BoolSetting highlight = this.add(new BoolSetting("Highlight", "Box the signs of listed chunks, through walls", true));
	private final BoolSetting beams = this.add(new BoolSetting("Beams", "A tall line over each listed chunk, to find it from afar", true));
	private final BoolSetting notify = this.add(new BoolSetting("Notify", "A toast when a new chunk is listed", true));
	private final ColorSetting colour = this.add(new ColorSetting("Color", "Boxes and beams", 0xFFFFB86B));

	private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
	private final Map<Long, BaseLogic.Entry> entries = new LinkedHashMap<>();
	private final Map<Long, List<int[]>> spots = new LinkedHashMap<>();
	private final Map<Block, BaseLogic.@Nullable Feature> byBlock = new IdentityHashMap<>();
	private final Predicate<BlockState> isSign = state -> this.featureOf(state.getBlock()) != null;
	private final BoxBatch batch = new BoxBatch(1.5F, true);
	private @Nullable String server;
	private boolean dirty;
	private boolean batchDirty = true;
	private long lastSave;
	private long scanned;
	private long scanNanos;
	private double lastFrameMillis;

	public BaseFinder() {
		super("BaseFinder", "Finds and rates chunks with signs of players, from what the server already sent", Category.WORLD);
		for (BaseLogic.Feature feature : BaseLogic.Feature.values()) {
			BoolSetting toggle = this.add(new BoolSetting(feature.title(), "Count " + feature.description(), true));
			toggle.onChange(value -> this.rescan());
			this.features.put(feature, toggle);
		}

		this.minScore.onChange(value -> this.rescan());
		this.minBlocks.onChange(value -> this.rescan());
		this.colour.onChange(value -> this.batchDirty = true);
		this.add(new ListSetting("Found", "Listed chunks: rating, distance, where, what; remove one with its button", "found by scanning",
			this::listLines, text -> {
				throw new IllegalArgumentException("Chunks are added by scanning, not by hand");
			}, this::removeLine));
		this.add(new ActionSetting("Rescan", "Scan every loaded chunk again", "Rescan", this::rescan));
		this.add(new ActionSetting("Clear", "Forget everything found on this server", "Clear", this::clear));
		this.add(new ActionSetting("Mark Waypoints", "Add the listed chunks as waypoints, in the group \"bases\"", "Mark", this::markWaypoints));
		this.add(new InfoSetting("Status", "What the scan is doing", this::status));

		this.on(Events.Join.class, event -> this.joined());
		this.on(Events.Leave.class, event -> this.left());
		this.on(Events.ChunkLoad.class, event -> this.pending.add(event.chunk().getPos().pack()));
		this.on(Events.ChunkUnload.class, event -> this.pending.remove(event.chunk().getPos().pack()));
		// A sign placed or broken in view: that chunk is rated again.
		this.on(Events.BlockUpdate.class, event -> {
			if (this.featureOf(event.newState().getBlock()) != null || this.featureOf(event.oldState().getBlock()) != null) {
				this.pending.add(ChunkPos.pack(event.pos().getX() >> 4, event.pos().getZ() >> 4));
			}
		});
		this.on(Events.Tick.class, event -> this.tick());
		this.on(Events.Render3D.class, event -> {
			this.scanWithinBudget();
			this.draw(event);
		});
	}

	@Override
	protected void onEnable() {
		this.joined();
	}

	@Override
	protected void onDisable() {
		this.left();
	}

	@Override
	protected void onDataCleared() {
		// The files are gone; the lists in memory go too, without being written back.
		this.dirty = false;
		this.entries.clear();
		this.spots.clear();
		this.pending.clear();
		this.batchDirty = true;
	}

	// ----- Results, for the menu and tests -----

	/** Everything listed on this server, in the menu's order. */
	public List<BaseLogic.Entry> found() {
		LocalPlayer player = this.player();
		double x = player == null ? 0.0 : player.getX();
		double z = player == null ? 0.0 : player.getZ();
		return BaseLogic.sorted(new ArrayList<>(this.entries.values()), this.sort.get(), x, z);
	}

	/** Chunks waiting to be scanned. */
	public int pendingCount() {
		return this.pending.size();
	}

	/** Mean time to scan one chunk, in milliseconds. */
	public double millisPerChunk() {
		return this.scanned == 0 ? 0.0 : this.scanNanos / 1e6 / this.scanned;
	}

	/** Time spent scanning in the last frame, in milliseconds. */
	public double lastFrameMillis() {
		return this.lastFrameMillis;
	}

	private String status() {
		if (this.server == null) {
			return "not on a server";
		}

		return String.format(Locale.ROOT, "%d listed, %d waiting, %.2f ms per chunk", this.entries.size(), this.pending.size(), this.millisPerChunk());
	}

	private List<String> listLines() {
		List<String> lines = new ArrayList<>();
		LocalPlayer player = this.player();
		for (BaseLogic.Entry entry : this.found()) {
			String distance = player == null ? "" : String.format(Locale.ROOT, " · %d m", Math.round(entry.distanceTo(player.getX(), player.getZ())));
			lines.add(String.format(Locale.ROOT, "%.1f%s · %d %d %d · %s", entry.score(), distance, entry.x(), entry.y(), entry.z(), entry.summary()));
		}

		return lines;
	}

	private void removeLine(String line) {
		String[] parts = line.split(" · ");
		for (String part : parts) {
			String[] numbers = part.trim().split(" ");
			if (numbers.length == 3) {
				try {
					int x = Integer.parseInt(numbers[0]);
					int z = Integer.parseInt(numbers[2]);
					long key = ChunkPos.pack(x >> 4, z >> 4);
					this.entries.remove(key);
					this.spots.remove(key);
					this.changed();
					return;
				} catch (NumberFormatException notCoordinates) {
					// the score or the distance; keep looking
				}
			}
		}
	}

	// ----- Lifecycle -----

	private void joined() {
		this.left();
		String now = SeedsSection.serverOf(this.mc());
		if (now == null || !this.isEnabled()) {
			return;
		}

		this.server = now;
		this.load();
		this.queueLoaded();
	}

	private void left() {
		if (this.server != null && this.dirty) {
			this.save();
		}

		this.server = null;
		this.pending.clear();
		this.entries.clear();
		this.spots.clear();
		this.batch.clear();
	}

	private void rescan() {
		this.queueLoaded();
	}

	private void markWaypoints() {
		dev.wisteria.modules.waypoints.Waypoints waypoints = this.client().modules().get(dev.wisteria.modules.waypoints.Waypoints.class);
		if (waypoints == null || this.level() == null) {
			return;
		}

		int marked = 0;
		for (BaseLogic.Entry entry : this.found()) {
			waypoints.add(String.format(Locale.ROOT, "Base %d %d", entry.x(), entry.z()), entry.x(), entry.y(), entry.z(), "bases");
			marked++;
		}

		this.info(marked + " chunks marked as waypoints (group \"bases\")");
	}

	private void clear() {
		this.entries.clear();
		this.spots.clear();
		this.changed();
		this.queueLoaded();
	}

	private void queueLoaded() {
		ClientLevel level = this.level();
		LocalPlayer player = this.player();
		if (level == null || player == null) {
			return;
		}

		int reach = (int) Math.ceil(this.range.get() / 16.0);
		int centreX = player.blockPosition().getX() >> 4;
		int centreZ = player.blockPosition().getZ() >> 4;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (level.getChunkSource().getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false) != null) {
					this.pending.add(ChunkPos.pack(centreX + dx, centreZ + dz));
				}
			}
		}
	}

	private void tick() {
		if (this.server == null && this.player() != null) {
			this.joined();
		}

		if (this.dirty && System.currentTimeMillis() - this.lastSave > SAVE_EVERY_MS) {
			this.save();
		}
	}

	private void changed() {
		this.dirty = true;
		this.batchDirty = true;
	}

	// ----- Scanning -----

	private BaseLogic.@Nullable Feature featureOf(Block block) {
		if (!this.byBlock.containsKey(block)) {
			this.byBlock.put(block, BaseLogic.featureOf(BuiltInRegistries.BLOCK.getKey(block).toString()));
		}

		return this.byBlock.get(block);
	}

	/** Scans the nearest waiting chunks until this frame's budget is spent. */
	private void scanWithinBudget() {
		this.lastFrameMillis = 0.0;
		ClientLevel level = this.level();
		LocalPlayer player = this.player();
		if (level == null || player == null || this.pending.isEmpty() || this.server == null) {
			return;
		}

		long start = System.nanoTime();
		long deadline = start + (long) (this.budget.get() * 1_000_000.0);
		int centreX = player.blockPosition().getX() >> 4;
		int centreZ = player.blockPosition().getZ() >> 4;
		int reach = (int) Math.ceil(this.range.get() / 16.0);

		// Nearest first: order what is waiting by distance once per frame.
		long[] waiting = this.pending.toLongArray();
		Long[] boxed = new Long[waiting.length];
		for (int i = 0; i < waiting.length; i++) {
			boxed[i] = waiting[i];
		}

		java.util.Arrays.sort(boxed, java.util.Comparator.comparingLong(key -> {
			long dx = ChunkPos.getX(key) - centreX;
			long dz = ChunkPos.getZ(key) - centreZ;
			return dx * dx + dz * dz;
		}));

		for (Long key : boxed) {
			if (System.nanoTime() >= deadline) {
				break;
			}

			this.pending.remove(key.longValue());
			int chunkX = ChunkPos.getX(key);
			int chunkZ = ChunkPos.getZ(key);
			if (Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ)) > reach) {
				continue;
			}

			LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
			if (chunk != null) {
				long before = System.nanoTime();
				this.scan(level, chunk);
				this.scanNanos += System.nanoTime() - before;
				this.scanned++;
			}
		}

		this.lastFrameMillis = (System.nanoTime() - start) / 1e6;
	}

	private void scan(ClientLevel level, LevelChunk chunk) {
		Map<BaseLogic.Feature, Integer> counts = new EnumMap<>(BaseLogic.Feature.class);
		List<int[]> found = new ArrayList<>();
		long sumX = 0;
		long sumY = 0;
		long sumZ = 0;
		int blocks = 0;
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(this.isSign)) {
				continue;
			}

			int baseY = level.getMinY() + i * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BaseLogic.Feature feature = this.featureOf(section.getBlockState(x, y, z).getBlock());
						if (feature == null || !this.features.get(feature).get()) {
							continue;
						}

						counts.merge(feature, 1, Integer::sum);
						blocks++;
						sumX += baseX + x;
						sumY += baseY + y;
						sumZ += baseZ + z;
						if (found.size() < SPOTS_PER_ENTRY) {
							found.add(new int[] {baseX + x, baseY + y, baseZ + z});
						}
					}
				}
			}
		}

		long key = chunk.getPos().pack();
		Map<BaseLogic.Feature, Boolean> enabled = new EnumMap<>(BaseLogic.Feature.class);
		this.features.forEach((feature, toggle) -> enabled.put(feature, toggle.get()));
		double score = BaseLogic.score(counts, enabled);
		boolean listed = this.entries.containsKey(key);
		if (score < this.minScore.get() || blocks < this.minBlocks.get()) {
			if (listed) {
				this.entries.remove(key);
				this.spots.remove(key);
				this.changed();
			}

			return;
		}

		BaseLogic.Entry entry = new BaseLogic.Entry(chunk.getPos().x(), chunk.getPos().z(), score, blocks, counts,
			(int) (sumX / blocks), (int) (sumY / blocks), (int) (sumZ / blocks), listed ? this.entries.get(key).foundAt() : System.currentTimeMillis());
		this.entries.put(key, entry);
		this.spots.put(key, found);
		List<BaseLogic.Entry> kept = BaseLogic.capped(new ArrayList<>(this.entries.values()), this.limit.get());
		if (kept.size() < this.entries.size()) {
			java.util.Set<Long> keep = new java.util.HashSet<>();
			kept.forEach(item -> keep.add(ChunkPos.pack(item.chunkX(), item.chunkZ())));
			this.entries.keySet().retainAll(keep);
			this.spots.keySet().retainAll(keep);
		}

		this.changed();
		if (!listed && this.entries.containsKey(key) && this.notify.get()) {
			this.client().hud().notify("BaseFinder", String.format(Locale.ROOT, "%.1f at %d %d: %s", score, entry.x(), entry.z(), entry.summary()),
				Toasts.Kind.INFO);
		}
	}

	// ----- Drawing -----

	private void draw(Events.Render3D event) {
		if (this.batchDirty) {
			this.batchDirty = false;
			this.batch.clear();
			int stroke = this.colour.get();
			int fill = (0x30 << 24) | (stroke & 0xFFFFFF);
			for (List<int[]> list : this.spots.values()) {
				for (int[] spot : list) {
					this.batch.add(spot[0] + 0.02, spot[1] + 0.02, spot[2] + 0.02, spot[0] + 0.98, spot[1] + 0.98, spot[2] + 0.98, stroke, fill);
				}
			}
		}

		if (this.highlight.get() && this.batch.size() > 0) {
			event.renderer().boxes(this.batch);
		}

		if (this.beams.get()) {
			for (BaseLogic.Entry entry : this.entries.values()) {
				event.renderer().line(entry.x() + 0.5, entry.y(), entry.z() + 0.5, entry.x() + 0.5, entry.y() + 96.0, entry.z() + 0.5, this.colour.get(),
					2.0F, true);
			}
		}
	}

	// ----- Storage: per server, in the profile -----

	private String fileFor(String serverId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(serverId.getBytes(StandardCharsets.UTF_8));
			return "found/" + HexFormat.of().formatHex(digest, 0, 8) + ".json";
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private void save() {
		String serverId = this.server;
		this.dirty = false;
		this.lastSave = System.currentTimeMillis();
		if (serverId == null) {
			return;
		}

		JsonObject json = new JsonObject();
		json.addProperty("server", serverId);
		JsonArray list = new JsonArray();
		for (Map.Entry<Long, BaseLogic.Entry> item : this.entries.entrySet()) {
			BaseLogic.Entry entry = item.getValue();
			JsonObject one = new JsonObject();
			one.addProperty("chunkX", entry.chunkX());
			one.addProperty("chunkZ", entry.chunkZ());
			one.addProperty("score", entry.score());
			one.addProperty("blocks", entry.blocks());
			one.addProperty("x", entry.x());
			one.addProperty("y", entry.y());
			one.addProperty("z", entry.z());
			one.addProperty("foundAt", entry.foundAt());
			JsonObject counts = new JsonObject();
			entry.counts().forEach((feature, count) -> counts.addProperty(feature.name(), count));
			one.add("counts", counts);
			JsonArray spotList = new JsonArray();
			for (int[] spot : this.spots.getOrDefault(item.getKey(), List.of())) {
				JsonArray xyz = new JsonArray();
				xyz.add(spot[0]);
				xyz.add(spot[1]);
				xyz.add(spot[2]);
				spotList.add(xyz);
			}

			one.add("spots", spotList);
			list.add(one);
		}

		json.add("entries", list);
		this.storage().writeJson(this.fileFor(serverId), json);
	}

	private void load() {
		this.entries.clear();
		this.spots.clear();
		String serverId = this.server;
		if (serverId == null) {
			return;
		}

		this.storage().readJson(this.fileFor(serverId)).filter(JsonElement::isJsonObject).ifPresent(json -> {
			JsonArray list = json.getAsJsonObject().getAsJsonArray("entries");
			if (list == null) {
				return;
			}

			for (JsonElement element : list) {
				try {
					JsonObject one = element.getAsJsonObject();
					Map<BaseLogic.Feature, Integer> counts = new EnumMap<>(BaseLogic.Feature.class);
					for (Map.Entry<String, JsonElement> count : one.getAsJsonObject("counts").entrySet()) {
						counts.put(BaseLogic.Feature.valueOf(count.getKey()), count.getValue().getAsInt());
					}

					BaseLogic.Entry entry = new BaseLogic.Entry(one.get("chunkX").getAsInt(), one.get("chunkZ").getAsInt(), one.get("score").getAsDouble(),
						one.get("blocks").getAsInt(), counts, one.get("x").getAsInt(), one.get("y").getAsInt(), one.get("z").getAsInt(),
						one.get("foundAt").getAsLong());
					long key = ChunkPos.pack(entry.chunkX(), entry.chunkZ());
					List<int[]> spotList = new ArrayList<>();
					for (JsonElement spot : one.getAsJsonArray("spots")) {
						JsonArray xyz = spot.getAsJsonArray();
						spotList.add(new int[] {xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()});
					}

					this.entries.put(key, entry);
					this.spots.put(key, spotList);
				} catch (RuntimeException broken) {
					// a damaged entry: skip it
				}
			}
		});
		this.batchDirty = true;
		this.dirty = false;
	}
}
