package dev.wisteria.modules.hud;

import java.util.List;

/** Durability HUD as pure functions: share left, colour band, and when to warn. */
public final class DurabilityLogic {
	public enum Band {
		GOOD, LOW, CRITICAL
	}

	/** One worn or held item: what it is, damage taken and the most it can take. */
	public record Piece(String slot, String name, int damage, int maxDamage) {
		public float left() {
			return maxDamage <= 0 ? 1.0F : Math.clamp(1.0F - (float) this.damage / this.maxDamage, 0.0F, 1.0F);
		}

		public int remaining() {
			return Math.max(0, this.maxDamage - this.damage);
		}
	}

	private DurabilityLogic() {
	}

	/** Under {@code warnPercent} it is low; under half of that, critical. */
	public static Band band(float left, int warnPercent) {
		float warn = warnPercent / 100.0F;
		if (left < warn / 2.0F) {
			return Band.CRITICAL;
		}

		return left < warn ? Band.LOW : Band.GOOD;
	}

	/**
	 * Pieces that have just crossed into a worse band since the last look, so the alarm sounds once per crossing,
	 * not every frame.
	 */
	public static List<Piece> newlyWorse(List<Piece> before, List<Piece> now, int warnPercent) {
		return now.stream().filter(piece -> {
			Band was = before.stream().filter(old -> old.slot().equals(piece.slot()) && old.name().equals(piece.name())).findFirst()
				.map(old -> band(old.left(), warnPercent)).orElse(Band.GOOD);
			Band is = band(piece.left(), warnPercent);
			return is.ordinal() > was.ordinal();
		}).toList();
	}

	/** "87%" or "312" left. */
	public static String label(Piece piece, boolean percent) {
		return percent ? Math.round(piece.left() * 100.0F) + "%" : Integer.toString(piece.remaining());
	}
}
