package dev.wisteria.modules.newchunks;

/**
 * How a chunk is judged from what the server sent, as pure functions. Two signs, both about liquids:
 * <ul>
 * <li>a chunk that <b>arrives already holding flowing water or lava</b> has been ticked before: it is <b>old</b>;</li>
 * <li>a chunk whose liquids <b>start flowing just after it arrives</b> is having its first ticks: it is <b>new</b>.</li>
 * </ul>
 * A chunk with no liquids that move shows neither and stays <b>unknown</b>. The first sign wins: an old chunk where
 * someone pours a bucket later stays old.
 */
public final class NewChunkLogic {
	public enum State {
		UNKNOWN, NEW, OLD
	}

	private NewChunkLogic() {
	}

	/** On arrival: flowing liquid already in it means old; otherwise unknown until it moves. */
	public static State onArrival(boolean holdsFlowingLiquid) {
		return holdsFlowingLiquid ? State.OLD : State.UNKNOWN;
	}

	/**
	 * A liquid in the chunk changed to or from flowing.
	 *
	 * @param sinceArrivalMillis how long after the chunk arrived
	 * @param windowMillis how long after arrival a flow still counts as the chunk's first ticks
	 */
	public static State onFlow(State current, long sinceArrivalMillis, long windowMillis) {
		if (current != State.UNKNOWN) {
			return current;
		}

		return sinceArrivalMillis >= 0L && sinceArrivalMillis <= windowMillis ? State.NEW : State.UNKNOWN;
	}
}
