package com.afkrecap;

import java.util.Map;
import net.runelite.api.TileItem;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotableDropTrackerTest
{
	private final NotableDropTracker tracker = new NotableDropTracker();
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private void spawn(int id, int quantity, int ownership, long price, boolean clue)
	{
		tracker.spawn(new Object(), id, quantity, ownership, price, clue, config);
	}

	@Test public void valuableOwnedDrop() { spawn(ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false); assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 1), tracker.totals()); }
	@Test public void lowValueIgnored() { spawn(ItemID.COINS, 10, TileItem.OWNERSHIP_SELF, 1, false); assertTrue(tracker.totals().isEmpty()); }
	@Test public void thresholdIncludesEntireNewStack() { spawn(ItemID.COINS, 100000, TileItem.OWNERSHIP_SELF, 1, false); assertEquals(Map.of(ItemID.COINS, 100000), tracker.totals()); }
	@Test public void clueBelowThreshold() { spawn(ItemID.TRAIL_CLUE_HARD_MAP001, 1, TileItem.OWNERSHIP_SELF, 0, true); assertEquals(Map.of(ItemID.TRAIL_CLUE_HARD_MAP001, 1), tracker.totals()); }
	@Test public void cluePreferenceDisabled()
	{
		tracker.spawn(new Object(), ItemID.TRAIL_CLUE_HARD_MAP001, 1, TileItem.OWNERSHIP_SELF, 0, true,
			new AfkRecapConfig() { @Override public boolean alwaysTrackClues() { return false; } });
		assertTrue(tracker.totals().isEmpty());
	}
	@Test public void globallyDisabledIncludesClues()
	{
		tracker.spawn(new Object(), ItemID.TRAIL_CLUE_MASTER, 1, TileItem.OWNERSHIP_SELF, 2000000, true,
			new AfkRecapConfig() { @Override public boolean trackNotableDrops() { return false; } });
		assertTrue(tracker.totals().isEmpty());
	}
	@Test public void otherUnownedAndGroupPilesIgnored()
	{
		for (int ownership : new int[]{TileItem.OWNERSHIP_OTHER, TileItem.OWNERSHIP_NONE, TileItem.OWNERSHIP_GROUP})
		{ spawn(ItemID.ABYSSAL_WHIP, 1, ownership, 1600000, false); }
		assertTrue(tracker.totals().isEmpty());
	}
	@Test public void repeatedSameResourceAggregates()
	{
		spawn(ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false);
		spawn(ItemID.ABYSSAL_WHIP, 2, TileItem.OWNERSHIP_SELF, 1600000, false);
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 3), tracker.totals());
	}
	@Test public void multipleTypes()
	{
		spawn(ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false);
		spawn(ItemID.TRAIL_CLUE_BEGINNER, 1, TileItem.OWNERSHIP_SELF, 0, true);
		assertEquals(2, tracker.totals().size());
	}
	@Test public void duplicateSpawnAndQuantityChangeCountOnce()
	{
		Object pile = new Object();
		tracker.spawn(pile, ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		tracker.spawn(pile, ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		tracker.quantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		tracker.quantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 2), tracker.totals());
	}
	@Test public void pickupDecreaseDoesNotSubtractAndDespawnRetainsTotals()
	{
		Object pile = new Object();
		tracker.spawn(pile, ItemID.ABYSSAL_WHIP, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		tracker.quantityChanged(pile, ItemID.ABYSSAL_WHIP, 2, 1, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		tracker.despawn(pile);
		tracker.quantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 2), tracker.totals());
	}
	@Test public void preSessionPileAdditionIgnored()
	{
		tracker.quantityChanged(new Object(), ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		assertTrue(tracker.totals().isEmpty());
	}
	@Test public void stackAdditionThresholdUsesAddedQuantity()
	{
		Object pile = new Object();
		tracker.spawn(pile, ItemID.COINS, 90000, TileItem.OWNERSHIP_SELF, 1, false, config);
		tracker.quantityChanged(pile, ItemID.COINS, 90000, 110000, TileItem.OWNERSHIP_SELF, 1, false, config);
		assertTrue(tracker.totals().isEmpty());
		tracker.quantityChanged(pile, ItemID.COINS, 110000, 210000, TileItem.OWNERSHIP_SELF, 1, false, config);
		assertEquals(Map.of(ItemID.COINS, 100000), tracker.totals());
	}
	@Test public void valueAndQuantityOverflowAreSafe()
	{
		spawn(ItemID.ABYSSAL_WHIP, Integer.MAX_VALUE, TileItem.OWNERSHIP_SELF, Long.MAX_VALUE, false);
		spawn(ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, Long.MAX_VALUE, false);
		assertEquals(Integer.valueOf(Integer.MAX_VALUE), tracker.totals().get(ItemID.ABYSSAL_WHIP));
	}
	@Test public void failedPriceStillAllowsConfirmedClue()
	{
		spawn(ItemID.TRAIL_CLUE_MASTER, 1, TileItem.OWNERSHIP_SELF, -1, true);
		spawn(ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, -1, false);
		assertEquals(Map.of(ItemID.TRAIL_CLUE_MASTER, 1), tracker.totals());
	}
	@Test public void supportedClueVariantsUseItemParameterOrKnownBaseId()
	{
		assertTrue(ClueDropItems.isClue(ItemID.TRAIL_CLUE_BEGINNER, -1));
		assertTrue(ClueDropItems.isClue(ItemID.TRAIL_CLUE_MASTER, -1));
		for (int id : new int[]{ItemID.TRAIL_CLUE_EASY_SIMPLE001, ItemID.TRAIL_CLUE_EASY_SIMPLE002,
			ItemID.TRAIL_CLUE_MEDIUM_SEXTANT001, ItemID.TRAIL_CLUE_HARD_MAP001, ItemID.TRAIL_CLUE_HARD_SEXTANT001,
			ItemID.TRAIL_CLUE_ELITE_MUSIC001, ItemID.TRAIL_CLUE_ELITE_MUSIC002})
		{ assertTrue(ClueDropItems.isClue(id, 1)); }
		assertFalse(ClueDropItems.isClue(ItemID.ABYSSAL_WHIP, -1));
		assertFalse(ClueDropItems.isClue(ItemID.TRAIL_CLUE_HARD_SEXTANT001_CASKET, -1));
	}
}
