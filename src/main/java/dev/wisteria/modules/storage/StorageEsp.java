package dev.wisteria.modules.storage;

import dev.wisteria.event.Events;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.platform.WorldBackend;
import dev.wisteria.render.BoxBatch;
import dev.wisteria.setting.ActionSetting;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.ColorSetting;
import dev.wisteria.setting.DoubleSetting;
import dev.wisteria.setting.InfoSetting;
import dev.wisteria.setting.IntSetting;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Highlights storage by kind (chests, barrels, shulker boxes in their own dye, ender chests, the rest) and by how full
 * it is: a container you have opened is filled in proportion to its used slots, one you have not is only outlined.
 * Passive: what it knows of the contents is what the server showed you when you opened it; nothing is sent.
 */
public final class StorageEsp extends Module {
	private static final int REBUILD_TICKS = 10;

	private final DoubleSetting range = this.add(new DoubleSetting("Range", "How far, in blocks", 64.0, 8.0, 256.0, 4.0));
	private final IntSetting limit = this.add(new IntSetting("Limit", "At most this many, nearest first", 512, 16, 4096));
	private final Map<StorageLogic.Kind, BoolSetting> kinds = new EnumMap<>(StorageLogic.Kind.class);
	private final Map<StorageLogic.Kind, ColorSetting> colours = new EnumMap<>(StorageLogic.Kind.class);
	private final BoolSetting dyeColours = this.add(new BoolSetting("Dye Colors", "Shulker boxes in their own colour", true));
	private final BoolSetting fullness = this.add(new BoolSetting("Fullness", "Fill opened containers by how full they were", true));
	private final IntSetting fill = this.add(new IntSetting("Fill", "Opacity of a full container's faces", 120, 0, 255));
	private final BoolSetting throughWalls = this.add(new BoolSetting("Through Walls", "Draw over terrain; off to let blocks hide them", true));

	/** Block → share of its slots that held something when it was last opened. This session, this dimension. */
	private final Map<Long, Float> seen = new HashMap<>();
	private final BoxBatch batch = new BoxBatch(1.5F, true);
	private int ticks;
	private int shown;
	private @Nullable String dimension;

	public StorageEsp() {
		super("Storage ESP", "Chests, barrels, shulkers by kind and by how full they were when opened", Category.RENDER);
		for (StorageLogic.Kind kind : StorageLogic.Kind.values()) {
			this.kinds.put(kind, this.add(new BoolSetting(kind.title(), "Show " + kind.title().toLowerCase(java.util.Locale.ROOT), kind != StorageLogic.Kind.OTHER)));
		}

		for (StorageLogic.Kind kind : StorageLogic.Kind.values()) {
			this.colours.put(kind, this.add(new ColorSetting(kind.title() + " Color", "Colour of " + kind.title().toLowerCase(java.util.Locale.ROOT),
				kind.defaultColour())));
		}

		this.add(new InfoSetting("Remembered", "Containers whose contents you have seen this session", () -> Integer.toString(this.seen.size())));
		this.add(new ActionSetting("Forget", "Forget how full every container was", "Forget", this.seen::clear));
		this.throughWalls.onChange(this.batch::setThroughWalls);
		this.on(Events.ContainerContent.class, this::opened);
		this.on(Events.Tick.class, event -> {
			if (++this.ticks >= REBUILD_TICKS) {
				this.ticks = 0;
				this.rebuild();
			}
		});
		this.on(Events.Render3D.class, event -> {
			if (this.batch.size() > 0) {
				event.renderer().boxes(this.batch);
			}
		});
		this.on(Events.Leave.class, event -> this.seen.clear());
	}

	@Override
	protected void onEnable() {
		this.ticks = REBUILD_TICKS;
	}

	@Override
	protected void onDisable() {
		this.batch.clear();
		this.shown = 0;
	}

	/** How full a block's container was when last opened, or null if unseen; for tests. */
	public @Nullable Float fullnessAt(int x, int y, int z) {
		return this.seen.get(BlockPos.asLong(x, y, z));
	}

	/** How many boxes the last rebuild drew. */
	public int shown() {
		return this.shown;
	}

	private void opened(Events.ContainerContent event) {
		BlockPos pos = event.pos();
		ClientLevel level = this.level();
		if (pos == null || level == null) {
			return;
		}

		this.checkDimension(level);
		List<Boolean> used = new ArrayList<>();
		for (ItemStack stack : event.containerItems()) {
			used.add(!stack.isEmpty());
		}

		float share = StorageLogic.fullness(used);
		this.seen.put(pos.asLong(), share);
		// A double chest is one container over two blocks.
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			Direction other = ChestBlock.getConnectedDirection(state);
			this.seen.put(pos.relative(other).asLong(), share);
		}

		this.ticks = REBUILD_TICKS;
	}

	private void checkDimension(ClientLevel level) {
		String now = level.dimension().identifier().toString();
		if (!now.equals(this.dimension)) {
			this.seen.clear();
			this.dimension = now;
		}
	}

	private void rebuild() {
		LocalPlayer player = this.player();
		ClientLevel level = this.level();
		this.batch.clear();
		this.shown = 0;
		if (player == null || level == null) {
			return;
		}

		this.checkDimension(level);
		this.batch.setThroughWalls(this.throughWalls.get());
		List<WorldBackend.Container> found = this.platform().world().containers(player.getX(), player.getY(), player.getZ(), this.range.get(),
			this.limit.get());
		for (WorldBackend.Container container : found) {
			StorageLogic.Kind kind = StorageLogic.kindOf(container.block());
			if (!this.kinds.get(kind).get()) {
				continue;
			}

			int colour = this.colours.get(kind).get();
			Integer dye = StorageLogic.shulkerColour(container.block());
			if (kind == StorageLogic.Kind.SHULKER && dye != null && this.dyeColours.get()) {
				colour = dye;
			}

			Float share = this.fullness.get() ? this.seen.get(BlockPos.asLong(container.x(), container.y(), container.z())) : null;
			int alpha = StorageLogic.fillAlpha(share, this.fill.get());
			double inset = 0.06;
			this.batch.add(container.x() + inset, container.y() + inset, container.z() + inset, container.x() + 1 - inset, container.y() + 1 - inset,
				container.z() + 1 - inset, colour, (alpha << 24) | (colour & 0xFFFFFF));
			this.shown++;
		}
	}
}
