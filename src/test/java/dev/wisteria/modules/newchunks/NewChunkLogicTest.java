package dev.wisteria.modules.newchunks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The NewChunks rules on synthetic chunks: what arrives, and what flows when. */
class NewChunkLogicTest {
	private static final long WINDOW = 5_000L;

	@Test
	void flowingLiquidOnArrivalMeansOld() {
		assertEquals(NewChunkLogic.State.OLD, NewChunkLogic.onArrival(true));
		assertEquals(NewChunkLogic.State.UNKNOWN, NewChunkLogic.onArrival(false));
	}

	@Test
	void flowSoonAfterArrivalMeansNew() {
		assertEquals(NewChunkLogic.State.NEW, NewChunkLogic.onFlow(NewChunkLogic.State.UNKNOWN, 200L, WINDOW));
		assertEquals(NewChunkLogic.State.NEW, NewChunkLogic.onFlow(NewChunkLogic.State.UNKNOWN, WINDOW, WINDOW));
	}

	@Test
	void flowLongAfterArrivalIsSomeoneElse() {
		// A bucket poured a minute later says nothing about when the chunk was made.
		assertEquals(NewChunkLogic.State.UNKNOWN, NewChunkLogic.onFlow(NewChunkLogic.State.UNKNOWN, 60_000L, WINDOW));
	}

	@Test
	void theFirstSignWins() {
		assertEquals(NewChunkLogic.State.OLD, NewChunkLogic.onFlow(NewChunkLogic.State.OLD, 100L, WINDOW));
		assertEquals(NewChunkLogic.State.NEW, NewChunkLogic.onFlow(NewChunkLogic.State.NEW, 60_000L, WINDOW));
	}

	@Test
	void aTrailIsOldChunksAmongNewOnes() {
		// A row of synthetic chunks as they arrive while flying: liquids flowing on arrival or just after.
		boolean[] flowingOnArrival = {false, true, true, false, false};
		long[] firstFlowAfter = {300L, -1L, -1L, 900L, -1L};
		NewChunkLogic.State[] expected = {NewChunkLogic.State.NEW, NewChunkLogic.State.OLD, NewChunkLogic.State.OLD, NewChunkLogic.State.NEW,
			NewChunkLogic.State.UNKNOWN};
		for (int i = 0; i < expected.length; i++) {
			NewChunkLogic.State state = NewChunkLogic.onArrival(flowingOnArrival[i]);
			if (firstFlowAfter[i] >= 0L) {
				state = NewChunkLogic.onFlow(state, firstFlowAfter[i], WINDOW);
			}

			assertEquals(expected[i], state, "chunk " + i);
		}
	}
}
