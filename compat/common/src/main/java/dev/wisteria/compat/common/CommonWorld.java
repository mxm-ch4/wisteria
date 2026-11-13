package dev.wisteria.compat.common;

import dev.wisteria.compat.WorldBackend;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class CommonWorld implements WorldBackend {
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
				entity == minecraft.player
			));
		}

		return result;
	}

	private static EntityKind kindOf(net.minecraft.world.entity.Entity entity) {
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
