package com.afkrecap;

import java.util.Collections;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static com.afkrecap.ResourceAcquisitionMessages.Family.*;
import static org.junit.Assert.*;

public class ResourceAcquisitionTrackerTest
{
	private final ResourceAcquisitionTracker tracker = new ResourceAcquisitionTracker(Collections.emptyMap());
	private void fish() { tracker.message("You catch a trout.", true, false); }
	private void log() { tracker.message("You get some oak logs.", false, true); }
	private void inventory(int id, int count) { tracker.inventoryChanged(Collections.singletonMap(id, count)); }
	private void total(int id, int count) { assertEquals(Collections.singletonMap(id, count), tracker.gains()); }
	private void settle() { tracker.gameTick(); tracker.gameTick(); }

	@Test public void normalFish() { inventory(ItemID.RAW_TROUT, 1); total(ItemID.RAW_TROUT, 1); }
	@Test public void hiddenFish() { fish(); total(ItemID.RAW_TROUT, 1); }
	@Test public void normalLog() { inventory(ItemID.OAK_LOGS, 1); total(ItemID.OAK_LOGS, 1); }
	@Test public void hiddenLog() { log(); total(ItemID.OAK_LOGS, 1); }
	@Test public void fishMessageAndInventoryCountOnce() { fish(); inventory(ItemID.RAW_TROUT, 1); total(ItemID.RAW_TROUT, 1); }
	@Test public void logInventoryAndMessageCountOnce() { inventory(ItemID.OAK_LOGS, 1); log(); total(ItemID.OAK_LOGS, 1); }
	@Test public void multipleFishWithPartialInventory() { fish(); fish(); fish(); inventory(ItemID.RAW_TROUT, 2); total(ItemID.RAW_TROUT, 3); }
	@Test public void multipleLogsWithPartialInventory() { log(); log(); inventory(ItemID.OAK_LOGS, 1); total(ItemID.OAK_LOGS, 2); }
	@Test public void differentResourcesNeverMatch()
	{
		fish(); inventory(ItemID.RAW_SALMON, 1);
		assertEquals(Map.of(ItemID.RAW_TROUT, 1, ItemID.RAW_SALMON, 1), tracker.gains());
	}
	@Test public void inventoryArrivingNextTickMatches() { fish(); tracker.gameTick(); inventory(ItemID.RAW_TROUT, 1); settle(); total(ItemID.RAW_TROUT, 1); }
	@Test public void messageArrivingNextTickMatches() { inventory(ItemID.OAK_LOGS, 1); tracker.gameTick(); log(); settle(); total(ItemID.OAK_LOGS, 1); }
	@Test public void inventoryThenAutomaticStorageCountsOnce()
	{
		fish(); inventory(ItemID.RAW_TROUT, 1); tracker.inventoryChanged(Collections.emptyMap());
		total(ItemID.RAW_TROUT, 1);
	}
	@Test public void emptyFishBarrelDoesNotCountAgain()
	{
		fish(); settle(); tracker.transfer(FISH); inventory(ItemID.RAW_TROUT, 1); total(ItemID.RAW_TROUT, 1);
	}
	@Test public void emptyLogBasketDoesNotCountAgain()
	{
		log(); settle(); tracker.transfer(LOG); inventory(ItemID.OAK_LOGS, 1); total(ItemID.OAK_LOGS, 1);
	}
	@Test public void delayedEmptyConfirmationRemovesUncertainInventory()
	{
		inventory(ItemID.OAK_LOGS, 20); tracker.gameTick(); tracker.transfer(LOG); assertTrue(tracker.gains().isEmpty());
	}
	@Test public void fillFromExistingInventoryIsNotAcquisition()
	{
		ResourceAcquisitionTracker existing = new ResourceAcquisitionTracker(Map.of(ItemID.RAW_TROUT, 20));
		existing.transfer(FISH); existing.inventoryChanged(Collections.emptyMap()); assertTrue(existing.gains().isEmpty());
	}
	@Test public void fillPreservesConfirmedAcquisition()
	{
		fish(); inventory(ItemID.RAW_TROUT, 1); tracker.transfer(FISH); tracker.inventoryChanged(Collections.emptyMap()); total(ItemID.RAW_TROUT, 1);
	}
	@Test public void delayedFillPreservesAlreadyReconciledAcquisitions()
	{
		fish(); tracker.gameTick(); inventory(ItemID.RAW_TROUT, 1); tracker.gameTick();
		tracker.transfer(FISH); tracker.inventoryChanged(Collections.emptyMap()); total(ItemID.RAW_TROUT, 1);
	}

	@Test public void transferWindowExpires()
	{
		tracker.transfer(LOG); settle(); tracker.gameTick(); inventory(ItemID.OAK_LOGS, 1); total(ItemID.OAK_LOGS, 1);
	}
	@Test public void transferDoesNotSuppressOtherFamily() { tracker.transfer(FISH); inventory(ItemID.OAK_LOGS, 1); total(ItemID.OAK_LOGS, 1); }
	@Test public void ambiguousAndCheckMessagesIgnored()
	{
		for (String message : new String[]{"You catch a fish.", "You catch something.", "You get some wood.",
			"The basket contains 20 oak logs.", "You empty your basket.", "You catch 2 trout.", "You catch a sacred eel."})
		{ tracker.message(message, true, true); }
		assertTrue(tracker.gains().isEmpty());
	}
	@Test public void closedStorageDoesNotSupplyMessageEvidence()
	{
		tracker.message("You catch a trout.", false, false); tracker.message("You get some oak logs.", false, false);
		assertTrue(tracker.gains().isEmpty());
	}
	@Test public void typedFishBonusCountsExtra()
	{
		fish(); tracker.message("Rada's blessing enabled you to catch an extra fish.", true, false); total(ItemID.RAW_TROUT, 2);
	}
	@Test public void typedLogBonusReconcilesWithInventory()
	{
		log(); tracker.message("Your Kandarin headgear provides you with an additional log.", false, true);
		inventory(ItemID.OAK_LOGS, 2); total(ItemID.OAK_LOGS, 2);
	}
	@Test public void secondSupportedBonuses()
	{
		fish(); tracker.message("The spirit flakes enabled you to catch an extra fish.", true, false);
		log(); tracker.message("The nature offerings enabled you to chop an extra log.", false, true);
		assertEquals(Map.of(ItemID.RAW_TROUT, 2, ItemID.OAK_LOGS, 2), tracker.gains());
	}
	@Test public void bonusWithoutTypedContextIgnored()
	{
		tracker.message("Rada's blessing enabled you to catch an extra fish.", true, false); assertTrue(tracker.gains().isEmpty());
	}
	@Test public void bonusAfterTickOrAmbiguityIgnored()
	{
		fish(); tracker.gameTick(); tracker.message("Rada's blessing enabled you to catch an extra fish.", true, false);
		fish(); tracker.message("You catch something.", true, false);
		tracker.message("The spirit flakes enabled you to catch an extra fish.", true, false); total(ItemID.RAW_TROUT, 2);
	}
	@Test public void bonusWrongFamilyAndDuplicateIgnored()
	{
		fish(); tracker.message("Your Kandarin headgear provides you with an additional log.", true, true);
		tracker.message("Rada's blessing enabled you to catch an extra fish.", true, false);
		tracker.message("Rada's blessing enabled you to catch an extra fish.", true, false); total(ItemID.RAW_TROUT, 2);
	}
	@Test public void unrelatedItemsRemainNormal() { inventory(ItemID.COINS, 200); total(ItemID.COINS, 200); }
}
