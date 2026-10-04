package com.afkrecap;

import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResourceContainerStateTest
{
	@Test public void bothOpenFishContainersEnableEvidence()
	{
		assertTrue(new ResourceContainerState(Set.of(ItemID.FISH_BARREL_OPEN)).hiddenFish());
		assertTrue(new ResourceContainerState(Set.of(ItemID.FISH_SACK_BARREL_OPEN)).hiddenFish());
	}
	@Test public void bothOpenLogContainersEnableEvidence()
	{
		assertTrue(new ResourceContainerState(Set.of(ItemID.LOG_BASKET_OPEN)).hiddenLogs());
		assertTrue(new ResourceContainerState(Set.of(ItemID.FORESTRY_BASKET_OPEN)).hiddenLogs());
	}
	@Test public void closedContainersDoNotEnableEvidence()
	{
		ResourceContainerState state = new ResourceContainerState(Set.of(ItemID.FISH_BARREL_CLOSED,
			ItemID.FISH_SACK_BARREL_CLOSED, ItemID.LOG_BASKET_CLOSED, ItemID.FORESTRY_BASKET_CLOSED));
		assertFalse(state.hiddenFish()); assertFalse(state.hiddenLogs());
	}
	@Test public void consumingToolsDisableUncertainHiddenGains()
	{
		assertFalse(new ResourceContainerState(Set.of(ItemID.FISH_BARREL_OPEN, ItemID.INFERNAL_HARPOON)).hiddenFish());
		assertFalse(new ResourceContainerState(Set.of(ItemID.LOG_BASKET_OPEN, ItemID.INFERNAL_AXE)).hiddenLogs());
	}
	@Test public void fellingAxesAllowConfirmedLogEvidence()
	{
		assertTrue(new ResourceContainerState(Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE_2H)).hiddenLogs());
		assertTrue(new ResourceContainerState(Set.of(ItemID.FORESTRY_BASKET_OPEN, ItemID.DRAGON_AXE_2H)).hiddenLogs());
	}

	@Test public void transferDetectionIncludesClosedContainersButNotOrdinaryItems()
	{
		ResourceContainerState state = new ResourceContainerState(Set.of(ItemID.FISH_BARREL_CLOSED));
		assertTrue(state.hasContainer(ResourceAcquisitionMessages.Family.FISH));
		assertFalse(state.hasContainer(ResourceAcquisitionMessages.Family.LOG));
		assertFalse(new ResourceContainerState(Set.of(ItemID.OAK_LOGS)).hasContainer(ResourceAcquisitionMessages.Family.LOG));
	}

	@Test public void depletedInfernalToolsAllowEvidence()
	{
		assertTrue(new ResourceContainerState(Set.of(ItemID.FISH_BARREL_OPEN, ItemID.INFERNAL_HARPOON_EMPTY)).hiddenFish());
		assertTrue(new ResourceContainerState(Set.of(ItemID.LOG_BASKET_OPEN, ItemID.INFERNAL_AXE_EMPTY)).hiddenLogs());
	}
}
