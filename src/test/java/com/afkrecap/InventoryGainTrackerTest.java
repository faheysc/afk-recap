package com.afkrecap;

import java.util.Collections;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryGainTrackerTest
{
	private Map<Integer, Integer> totals(Item... items)
	{
		return InventoryGainTracker.totals(items);
	}

	@Test
	public void existingInventoryIsNotCounted()
	{
		Map<Integer, Integer> existing = totals(new Item(ItemID.COINS, 100));
		InventoryGainTracker tracker = new InventoryGainTracker(existing);
		tracker.update(existing);
		assertTrue(tracker.gains().isEmpty());
	}

	@Test
	public void oneNewStackableItemIsCounted()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(Collections.emptyMap());
		tracker.update(totals(new Item(ItemID.FEATHER, 50)));
		assertEquals(Collections.singletonMap(ItemID.FEATHER, 50), tracker.gains());
	}

	@Test
	public void stackIncreaseOnlyCountsDifference()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(new Item(ItemID.FEATHER, 50)));
		tracker.update(totals(new Item(ItemID.FEATHER, 75)));
		tracker.update(totals(new Item(ItemID.FEATHER, 100)));
		assertEquals(Collections.singletonMap(ItemID.FEATHER, 50), tracker.gains());
	}

	@Test
	public void multipleNonStackableCopiesAreTotaledAcrossSlots()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(new Item(ItemID.LOGS, 1)));
		tracker.update(totals(new Item(ItemID.LOGS, 1), new Item(ItemID.LOGS, 1), new Item(ItemID.LOGS, 1)));
		assertEquals(Collections.singletonMap(ItemID.LOGS, 2), tracker.gains());
	}

	@Test
	public void multipleItemIdsAreTrackedIndependently()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(Collections.emptyMap());
		tracker.update(totals(new Item(ItemID.YEW_LOGS, 1), new Item(ItemID.BIRD_NEST_EMPTY, 1)));
		assertEquals(2, tracker.gains().size());
		assertEquals(Integer.valueOf(1), tracker.gains().get(ItemID.YEW_LOGS));
		assertEquals(Integer.valueOf(1), tracker.gains().get(ItemID.BIRD_NEST_EMPTY));
	}

	@Test
	public void quantityDecreasesAreIgnored()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(new Item(ItemID.COINS, 100)));
		tracker.update(totals(new Item(ItemID.COINS, 50)));
		tracker.update(Collections.emptyMap());
		assertTrue(tracker.gains().isEmpty());
	}

	@Test
	public void decreaseThenIncreaseCountsTheLaterPositiveIncrease()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(new Item(ItemID.FEATHER, 100)));
		tracker.update(totals(new Item(ItemID.FEATHER, 25)));
		tracker.update(totals(new Item(ItemID.FEATHER, 40)));
		assertEquals(Collections.singletonMap(ItemID.FEATHER, 15), tracker.gains());
	}

	@Test
	public void removalThenReturnIsAnAcquisition()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(new Item(ItemID.TINDERBOX, 1)));
		tracker.update(Collections.emptyMap());
		tracker.update(totals(new Item(ItemID.TINDERBOX, 1)));
		assertEquals(Collections.singletonMap(ItemID.TINDERBOX, 1), tracker.gains());
	}

	@Test
	public void duplicateOrReorderedSlotsProduceNoGain()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(totals(
			new Item(ItemID.LOGS, 1), new Item(ItemID.TINDERBOX, 1)));
		tracker.update(totals(new Item(ItemID.TINDERBOX, 1), new Item(ItemID.LOGS, 1)));
		assertTrue(tracker.gains().isEmpty());
	}

	@Test
	public void missingBaselineUsesFirstObservationWithoutCountingIt()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(null);
		tracker.update(totals(new Item(ItemID.FEATHER, 100)));
		assertTrue(tracker.gains().isEmpty());
		tracker.update(totals(new Item(ItemID.FEATHER, 125)));
		assertEquals(Collections.singletonMap(ItemID.FEATHER, 25), tracker.gains());
	}

	@Test
	public void emptySlotsAndInvalidQuantitiesAreIgnored()
	{
		assertEquals(Collections.singletonMap(ItemID.LOGS, 1), totals(null,
			new Item(-1, 1), new Item(ItemID.COINS, 0), new Item(ItemID.FEATHER, -1), new Item(ItemID.LOGS, 1)));
	}

	@Test
	public void integerQuantitiesCannotWrapNegative()
	{
		InventoryGainTracker tracker = new InventoryGainTracker(Collections.emptyMap());
		tracker.update(totals(new Item(ItemID.COINS, Integer.MAX_VALUE), new Item(ItemID.COINS, 10)));
		tracker.update(Collections.emptyMap());
		tracker.update(totals(new Item(ItemID.COINS, 100)));
		assertEquals(Integer.valueOf(Integer.MAX_VALUE), tracker.gains().get(ItemID.COINS));
	}
}
