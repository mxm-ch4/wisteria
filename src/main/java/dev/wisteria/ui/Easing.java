package dev.wisteria.ui;

/** Timing curves: each maps linear progress 0..1 to eased progress, with f(0) = 0 and f(1) = 1. */
public enum Easing {
	LINEAR {
		@Override
		public float apply(float t) {
			return t;
		}
	},
	/** The default for things appearing or moving into place: fast start, soft landing. */
	OUT_CUBIC {
		@Override
		public float apply(float t) {
			float inverse = 1.0F - t;
			return 1.0F - inverse * inverse * inverse;
		}
	},
	/** For things that move from one resting place to another, like a slider thumb. */
	IN_OUT_CUBIC {
		@Override
		public float apply(float t) {
			if (t < 0.5F) {
				return 4.0F * t * t * t;
			}

			float inverse = -2.0F * t + 2.0F;
			return 1.0F - inverse * inverse * inverse / 2.0F;
		}
	},
	/** A longer, softer landing than OUT_CUBIC: opening a whole screen. */
	OUT_QUINT {
		@Override
		public float apply(float t) {
			float inverse = 1.0F - t;
			return 1.0F - inverse * inverse * inverse * inverse * inverse;
		}
	},
	/** Overshoots slightly and settles: toggles and other small, playful confirmations. */
	OUT_BACK {
		@Override
		public float apply(float t) {
			float c1 = 1.70158F;
			float c3 = c1 + 1.0F;
			float shifted = t - 1.0F;
			return 1.0F + c3 * shifted * shifted * shifted + c1 * shifted * shifted;
		}
	};

	/** @param t linear progress; values outside 0..1 are the caller's mistake and are not clamped here */
	public abstract float apply(float t);
}
