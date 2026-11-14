package dev.wisteria.modules.hud;

import dev.wisteria.event.Events;
import dev.wisteria.hud.Anchor;
import dev.wisteria.hud.HudContext;
import dev.wisteria.hud.HudElement;
import dev.wisteria.hud.Toasts;
import dev.wisteria.setting.BoolSetting;
import dev.wisteria.setting.IntSetting;
import dev.wisteria.ui.Metrics.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Durability of armour, elytra and what is in your hands: a bar per piece, amber when low, red when critical, and an
 * alarm (a toast and a local sound) once when a piece crosses into a worse band. Reads only your own items.
 */
public final class Durability extends HudElement {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private final BoolSetting armor = this.add(new BoolSetting("Armor", "Helmet, chestplate or elytra, leggings, boots", true));
	private final BoolSetting hands = this.add(new BoolSetting("Hands", "Tools and weapons in your hands", true));
	private final BoolSetting percent = this.add(new BoolSetting("Percent", "Show a percentage; off for the uses left", true));
	private final IntSetting warn = this.add(new IntSetting("Warn At", "Percent left that counts as low (critical is half of it)", 20, 1, 90));
	private final BoolSetting alarm = this.add(new BoolSetting("Alarm", "A toast when a piece gets low or critical", true));
	private final BoolSetting sound = this.add(new BoolSetting("Alarm Sound", "And a sound, heard only by you", true));

	private final List<DurabilityLogic.Piece> rows = new ArrayList<>();
	private List<DurabilityLogic.Piece> last = List.of();

	public Durability() {
		super("Durability", "Armour, elytra and held items: durability bars and an alarm when low", Anchor.BOTTOM_RIGHT, -6.0F, -60.0F);
		this.on(Events.Tick.class, event -> this.tick());
	}

	/** The pieces shown now; for tests. */
	public List<DurabilityLogic.Piece> pieces() {
		return this.collect();
	}

	private List<DurabilityLogic.Piece> collect() {
		List<DurabilityLogic.Piece> found = new ArrayList<>();
		LocalPlayer player = this.player();
		if (player == null) {
			return found;
		}

		for (EquipmentSlot slot : SLOTS) {
			boolean armour = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
			if (armour ? !this.armor.get() : !this.hands.get()) {
				continue;
			}

			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				found.add(new DurabilityLogic.Piece(slot.getName(), stack.getHoverName().getString(), stack.getDamageValue(), stack.getMaxDamage()));
			}
		}

		return found;
	}

	private void tick() {
		List<DurabilityLogic.Piece> now = this.collect();
		if (this.alarm.get()) {
			for (DurabilityLogic.Piece piece : DurabilityLogic.newlyWorse(this.last, now, this.warn.get())) {
				boolean critical = DurabilityLogic.band(piece.left(), this.warn.get()) == DurabilityLogic.Band.CRITICAL;
				this.client().hud().notify(critical ? "Durability critical" : "Durability low",
					piece.name() + ": " + DurabilityLogic.label(piece, true) + " left", Toasts.Kind.WARNING);
				if (this.sound.get() && !this.client().panic().active()) {
					this.mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), critical ? 0.6F : 1.0F));
				}
			}
		}

		this.last = now;
	}

	@Override
	protected float @Nullable [] measure(HudContext c) {
		this.rows.clear();
		this.rows.addAll(this.collect());
		if (this.rows.isEmpty() && c.preview()) {
			this.rows.add(new DurabilityLogic.Piece("head", "Diamond Helmet", 40, 363));
			this.rows.add(new DurabilityLogic.Piece("chest", "Elytra", 380, 432));
			this.rows.add(new DurabilityLogic.Piece("mainhand", "Netherite Pickaxe", 1980, 2031));
		}

		if (this.rows.isEmpty()) {
			return null;
		}

		float names = 0.0F;
		for (DurabilityLogic.Piece piece : this.rows) {
			names = Math.max(names, c.width(Type.BODY, piece.name()));
		}

		float row = c.line(Type.BODY) + 5.0F;
		return new float[] {HudContext.PAD * 2.0F + names + 8.0F + 46.0F + 6.0F + c.width(Type.MONO, "100%"), HudContext.PAD * 2.0F + row * this.rows.size() - 2.0F};
	}

	@Override
	protected void draw(HudContext c, float width, float height) {
		c.panel(0.0F, 0.0F, width, height);
		float line = c.line(Type.BODY);
		float row = line + 5.0F;
		float y = HudContext.PAD;
		boolean blinkOff = (System.currentTimeMillis() / 400L) % 2L == 0L;
		for (DurabilityLogic.Piece piece : this.rows) {
			DurabilityLogic.Band band = DurabilityLogic.band(piece.left(), this.warn.get());
			int colour = switch (band) {
				case GOOD -> c.theme().success();
				case LOW -> c.theme().warning();
				case CRITICAL -> c.theme().danger();
			};
			if (band == DurabilityLogic.Band.CRITICAL && blinkOff) {
				colour = c.theme().textMuted();
			}

			String label = DurabilityLogic.label(piece, this.percent.get());
			c.text(Type.BODY, piece.name(), HudContext.PAD, y, c.theme().text());
			float labelWidth = c.width(Type.MONO, label);
			c.text(Type.MONO, label, width - HudContext.PAD - labelWidth, y, colour);
			float barRight = width - HudContext.PAD - c.width(Type.MONO, "100%") - 6.0F;
			c.bar(barRight - 46.0F, y + line / 2.0F - 1.5F, 46.0F, 3.0F, piece.left(), colour);
			y += row;
		}
	}
}
