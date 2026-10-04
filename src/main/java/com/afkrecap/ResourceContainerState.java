package com.afkrecap;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Small inventory/equipment snapshot, never estimated container contents. */
final class ResourceContainerState
{
	private final Set<Integer> items;

	ResourceContainerState(Set<Integer> items)
	{
		this.items = items;
	}

	boolean hasContainer(ResourceAcquisitionMessages.Family family)
	{
		return items.stream().anyMatch(id -> ResourceAcquisitionMessages.container(id) == family);
	}

	boolean hiddenFish()
	{
		return (items.contains(ItemID.FISH_BARREL_OPEN) || items.contains(ItemID.FISH_SACK_BARREL_OPEN))
			&& !items.contains(ItemID.INFERNAL_HARPOON);
	}

	boolean hiddenLogs()
	{
		// Felling ration/XP-only messages are not acquisitions. Typed log messages still are.
		return (items.contains(ItemID.LOG_BASKET_OPEN) || items.contains(ItemID.FORESTRY_BASKET_OPEN))
			&& !items.contains(ItemID.INFERNAL_AXE);
	}
}
