package dev.wisteria.platform.game;

import dev.wisteria.platform.WorldBackend;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class GameWorld implements WorldBackend {
	@Override
	public boolean isLoaded() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.player != null;
	}

	@Override
	public List<Entity> entities(float tickDelta) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return List.of();
		}

		List<Entity> result = new ArrayList<>();
		for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
			Vec3 position = entity.getPosition(tickDelta);
			boolean player = entity instanceof Player;
			result.add(new Entity(
				entity.getId(),
				kindOf(entity),
				player ? entity.getScoreboardName() : entity.getName().getString(),
				position.x, position.y, position.z,
				entity.getBbWidth(), entity.getBbHeight(),
				entity == minecraft.player,
				entity.getEyeHeight(), look(entity, tickDelta).x, look(entity, tickDelta).y, look(entity, tickDelta).z
			));
		}

		return result;
	}

	private static Vec3 look(net.minecraft.world.entity.Entity entity, float tickDelta) {
		return entity.getViewVector(tickDelta);
	}

	@Override
	public List<Container> containers(double x, double y, double z, double range, int limit) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return List.of();
		}

		List<Container> found = new ArrayList<>();
		int radius = (int)Math.ceil(range / 16.0);
		int centreX = (int)Math.floor(x) >> 4;
		int centreZ = (int)Math.floor(z) >> 4;
		double rangeSquared = range * range;
		for (int cx = centreX - radius; cx <= centreX + radius; cx++) {
			for (int cz = centreZ - radius; cz <= centreZ + radius; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}

				for (net.minecraft.world.level.block.entity.BlockEntity block : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (!(block instanceof net.minecraft.world.Container) && !(block instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity)) {
						continue;
					}

					net.minecraft.core.BlockPos pos = block.getBlockPos();
					double dx = pos.getX() + 0.5 - x;
					double dy = pos.getY() + 0.5 - y;
					double dz = pos.getZ() + 0.5 - z;
					if (dx * dx + dy * dy + dz * dz <= rangeSquared) {
						found.add(new Container(pos.getX(), pos.getY(), pos.getZ(),
							net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock()).toString()));
					}
				}
			}
		}

		found.sort(java.util.Comparator.comparingDouble(c -> (c.x() + 0.5 - x) * (c.x() + 0.5 - x) + (c.y() + 0.5 - y) * (c.y() + 0.5 - y) + (c.z() + 0.5 - z) * (c.z() + 0.5 - z)));
		return found.size() > limit ? found.subList(0, limit) : found;
	}

	/** The kind ESP and the rest of the client sort an entity into. */
	public static EntityKind kindOf(net.minecraft.world.entity.Entity entity) {
		if (entity instanceof Player) {
			return EntityKind.PLAYER;
		}

		if (entity instanceof Enemy) {
			return EntityKind.HOSTILE;
		}

		if (entity instanceof ItemEntity) {
			return EntityKind.ITEM;
		}

		return entity instanceof LivingEntity ? EntityKind.PASSIVE : EntityKind.OTHER;
	}
}
