package dev.wisteria.platform.game;

import dev.wisteria.Wisteria;
import dev.wisteria.event.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The world, chat and player events of {@link Events} that come from Fabric API callbacks or from watching the
 * player each tick. The rest are fired by mixins in dev.wisteria.mixin.
 */
public final class GameEvents {
	/** How long after using a block a container screen still counts as that block's. */
	private static final int CONTAINER_WINDOW_TICKS = 40;

	private static @Nullable BlockPos lastUsedBlock;
	private static long lastUsedTick = Long.MIN_VALUE;
	private static long ticks;
	private static int openContainerId = -1;
	private static @Nullable BlockPos openContainerPos;
	private static boolean dead;

	private GameEvents() {
	}

	static void install() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> Wisteria.post(new Events.ChunkLoad(chunk)));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> Wisteria.post(new Events.ChunkUnload(chunk)));
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> Wisteria.post(new Events.EntityAdd(entity)));
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Wisteria.post(new Events.EntityRemove(entity)));

		// Returning false hides the message.
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, type, time) ->
			!Wisteria.post(new Events.ChatReceive(message, false, sender != null ? sender.name() : null, false)).isCancelled());
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
			!Wisteria.post(new Events.ChatReceive(message, true, null, overlay)).isCancelled());

		ClientTickEvents.END_CLIENT_TICK.register(GameEvents::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			dead = false;
			openContainerId = -1;
			openContainerPos = null;
			lastUsedBlock = null;
		});
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		boolean nowDead = player != null && player.isDeadOrDying();
		if (nowDead && !dead) {
			Wisteria.post(new Events.Death(player.position(), player.level().dimension().identifier().toString()));
		} else if (!nowDead && dead && player != null) {
			Wisteria.post(Events.Respawn.INSTANCE);
		}

		dead = nowDead;
	}

	/** Called by the mixin on MultiPlayerGameMode.useItemOn. */
	public static void blockUsed(BlockPos pos) {
		lastUsedBlock = pos.immutable();
		lastUsedTick = ticks;
	}

	/** Called by the packet listener mixin once a container screen has been opened. */
	public static void containerOpened(int containerId, String menu, net.minecraft.network.chat.Component title) {
		BlockPos pos = ticks - lastUsedTick <= CONTAINER_WINDOW_TICKS ? lastUsedBlock : null;
		openContainerId = containerId;
		openContainerPos = pos;
		Wisteria.post(new Events.ContainerOpen(containerId, menu, title, pos));
	}

	/** Called by the packet listener mixin once a container's contents have been applied. */
	public static void containerContent(int containerId, java.util.List<net.minecraft.world.item.ItemStack> items) {
		java.util.List<net.minecraft.world.item.ItemStack> copy = items.stream().map(net.minecraft.world.item.ItemStack::copy).toList();
		// Every container menu ends with the 36 slots of the player's inventory; container 0 is that inventory.
		int own = containerId == 0 ? copy.size() : Math.max(0, copy.size() - 36);
		BlockPos pos = containerId == openContainerId ? openContainerPos : null;
		Wisteria.post(new Events.ContainerContent(containerId, copy, own, pos));
	}
}
