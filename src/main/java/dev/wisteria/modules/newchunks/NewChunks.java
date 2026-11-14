package dev.wisteria.modules.newchunks;

import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.render.BoxBatch;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.IntSetting;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Tells freshly generated chunks from ones generated before, from what the server sends about liquids (see
 * {@link NewChunkLogic}); old chunks among new ones show where players went before. Paints a plate over each chunk.
 * Passive: nothing is sent to the server; remembered for this session only.
 */
public final class NewChunks extends Module {
	private static final int MAX_CHUNKS = 50_000;

	private final BoolSetting showNew = this.add(new BoolSetting("Show New", "Chunks generated just now (their liquids start flowing as they arrive)", true));
	private final BoolSetting showOld = this.add(new BoolSetting("Show Old", "Chunks generated before (they arrive with liquids already flowing): trails", true));
	private final ColorSetting newColor = this.add(new ColorSetting("New Color", "Plate over new chunks", 0xFFFF7A93));
	private final ColorSetting oldColor = this.add(new ColorSetting("Old Color", "Plate over old chunks", 0xFF8CE0B5));
	private final IntSetting fill = this.add(new IntSetting("Fill", "Opacity of the plates", 50, 0, 255));
	private final BoolSetting followPlayer = this.add(new BoolSetting("Follow Height", "Plates just under your feet; off for a fixed height", true));
	private final IntSetting height = this.add(new IntSetting("Height", "Fixed height of the plates, when Follow Height is off", 0, -64, 320));
	private final DoubleSetting window = this.add(new DoubleSetting("Flow Window",
		"Seconds after a chunk arrives in which a flowing liquid counts as its first ticks", 5.0, 1.0, 600.0, 1.0));
	private final IntSetting range = this.add(new IntSetting("Range", "Plates this many chunks around you (the server decides how far it sends)", 16, 2, 64));

	/** Chunk → what it is and when it arrived, for the dimension the player is in. */
	private final Map<Long, Seen> chunks = new LinkedHashMap<>(16, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Seen> eldest) {
			return this.size() > MAX_CHUNKS;
		}
	};
	private final Predicate<BlockState> flowing = state -> isFlowing(state.getFluidState());
	private BoxBatch batch = new BoxBatch(1.0F, false);
	private boolean dirty = true;
	private int builtY = Integer.MIN_VALUE;
	private long builtCentre = Long.MIN_VALUE;
	private @Nullable String dimension;

	private record Seen(NewChunkLogic.State state, long arrived) {
	}

	public NewChunks() {
		super("NewChunks", "Freshly generated chunks against old ones, from how their liquids flow: trails of other players", Category.WORLD);
		this.add(new InfoSetting("Counted", "Chunks judged this session in this dimension", this::counts));
		this.add(new ActionSetting("Clear", "Forget every chunk judged so far", "Clear", () -> {
			this.chunks.clear();
			this.dirty = true;
		}));
		for (var setting : new dev.wisteria.setting.Setting<?>[] {this.showNew, this.showOld, this.newColor, this.oldColor, this.fill, this.followPlayer,
			this.height, this.range}) {
			setting.onChange(value -> this.dirty = true);
		}

		this.on(Events.ChunkLoad.class, event -> this.arrived(event.chunk()));
		this.on(Events.BlockUpdate.class, this::blockUpdated);
		this.on(Events.Leave.class, event -> this.forget());
		this.on(Events.Tick.class, event -> this.tick());
		this.on(Events.Render3D.class, event -> {
			if (this.batch.size() > 0) {
				event.renderer().boxes(this.batch);
			}
		});
	}

	@Override
	protected void onEnable() {
		this.forget();
	}

	@Override
	protected void onDisable() {
		this.forget();
	}

	/** What a chunk was judged, or UNKNOWN; for tests. */
	public NewChunkLogic.State stateOf(int chunkX, int chunkZ) {
		Seen seen = this.chunks.get(ChunkPos.pack(chunkX, chunkZ));
		return seen == null ? NewChunkLogic.State.UNKNOWN : seen.state();
	}

	/** How many plates are drawn now. */
	public int plates() {
		return this.batch.size();
	}

	private void forget() {
		this.chunks.clear();
		this.batch.clear();
		this.dirty = true;
		this.dimension = null;
	}

	private static boolean isFlowing(FluidState fluid) {
		return !fluid.isEmpty() && !fluid.isSource();
	}

	private void arrived(LevelChunk chunk) {
		this.checkDimension();
		long key = chunk.getPos().pack();
		Seen known = this.chunks.get(key);
		if (known != null && known.state() != NewChunkLogic.State.UNKNOWN) {
			return;
		}

		this.chunks.put(key, new Seen(NewChunkLogic.onArrival(holdsFlowing(chunk, this.flowing)), System.currentTimeMillis()));
		this.dirty = true;
	}

	private static boolean holdsFlowing(LevelChunk chunk, Predicate<BlockState> flowing) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.hasOnlyAir() || !section.maybeHas(flowing)) {
				continue;
			}

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (flowing.test(section.getBlockState(x, y, z))) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	private void blockUpdated(Events.BlockUpdate event) {
		if (!isFlowing(event.newState().getFluidState()) && !isFlowing(event.oldState().getFluidState())) {
			return;
		}

		long key = ChunkPos.pack(event.pos().getX() >> 4, event.pos().getZ() >> 4);
		Seen seen = this.chunks.get(key);
		if (seen == null) {
			return;
		}

		NewChunkLogic.State next = NewChunkLogic.onFlow(seen.state(), System.currentTimeMillis() - seen.arrived(),
			(long) (this.window.get() * 1000.0));
		if (next != seen.state()) {
			this.chunks.put(key, new Seen(next, seen.arrived()));
			this.dirty = true;
		}
	}

	private void checkDimension() {
		ClientLevel level = this.level();
		String now = level == null ? null : level.dimension().identifier().toString();
		if (now != null && !now.equals(this.dimension)) {
			this.chunks.clear();
			this.dimension = now;
			this.dirty = true;
		}
	}

	private String counts() {
		int fresh = 0;
		int old = 0;
		for (Seen seen : this.chunks.values()) {
			fresh += seen.state() == NewChunkLogic.State.NEW ? 1 : 0;
			old += seen.state() == NewChunkLogic.State.OLD ? 1 : 0;
		}

		return String.format(Locale.ROOT, "%d new, %d old, %d unknown", fresh, old, this.chunks.size() - fresh - old);
	}

	private void tick() {
		LocalPlayer player = this.player();
		if (player == null) {
			return;
		}

		this.checkDimension();
		int y = this.followPlayer.get() ? (int) Math.floor(player.getY()) - 1 : this.height.get();
		long centre = ChunkPos.pack(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
		if (!this.dirty && y == this.builtY && centre == this.builtCentre) {
			return;
		}

		this.dirty = false;
		this.builtY = y;
		this.builtCentre = centre;
		this.batch.clear();
		int centreX = ChunkPos.getX(centre);
		int centreZ = ChunkPos.getZ(centre);
		int reach = this.range.get();
		int alpha = this.fill.get();
		for (Map.Entry<Long, Seen> entry : this.chunks.entrySet()) {
			NewChunkLogic.State state = entry.getValue().state();
			boolean show = state == NewChunkLogic.State.NEW ? this.showNew.get() : state == NewChunkLogic.State.OLD && this.showOld.get();
			int chunkX = ChunkPos.getX(entry.getKey());
			int chunkZ = ChunkPos.getZ(entry.getKey());
			if (!show || Math.abs(chunkX - centreX) > reach || Math.abs(chunkZ - centreZ) > reach) {
				continue;
			}

			int colour = state == NewChunkLogic.State.NEW ? this.newColor.get() : this.oldColor.get();
			double minX = chunkX * 16.0 + 0.3;
			double minZ = chunkZ * 16.0 + 0.3;
			this.batch.add(minX, y + 0.02, minZ, minX + 15.4, y + 0.08, minZ + 15.4, colour, (alpha << 24) | (colour & 0xFFFFFF));
		}
	}
}
