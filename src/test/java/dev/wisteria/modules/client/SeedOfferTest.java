package dev.wisteria.modules.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What happens to a seed SeedcrackerX hands over, against what is already saved for the server. */
class SeedOfferTest {
	private static final String SX = SeedsSection.FROM_SEEDCRACKERX;

	@Test
	void nothingSavedYetSavesIt() {
		assertEquals(SeedsSection.Offer.SAVE, SeedsSection.decide(null, null, SX, 42L));
	}

	@Test
	void theSameSeedChangesNothing() {
		assertEquals(SeedsSection.Offer.SAME, SeedsSection.decide(42L, "", SX, 42L));
		assertEquals(SeedsSection.Offer.SAME, SeedsSection.decide(42L, SX, SX, 42L));
	}

	@Test
	void aNewerCrackReplacesAnOlderCrack() {
		assertEquals(SeedsSection.Offer.REPLACE, SeedsSection.decide(42L, SX, SX, 43L));
	}

	@Test
	void thePlayersOwnSeedIsNeverOverwrittenWithoutAsking() {
		assertEquals(SeedsSection.Offer.ASK, SeedsSection.decide(42L, "", SX, 43L));
		assertEquals(SeedsSection.Offer.ASK, SeedsSection.decide(42L, null, SX, 43L));
	}
}
