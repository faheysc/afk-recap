package com.afkrecap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;

/** Acquisition deltas across inventory snapshots; null means the baseline is unavailable. */
final class InventoryGainTracker
{
	private Map<Integer, Integer> previous;
	private final Map<Integer, Integer> gains = new HashMap<>();

	InventoryGainTracker(Map<Integer, Integer> baseline)
	{
		previous = baseline == null ? null : new HashMap<>(baseline);
	}

	void update(Map<Integer, Integer> current)
	{
		if (current == null)
		{
			previous = null;
			return;
		}
		if (previous != null)
		{
			current.forEach((id, quantity) ->
			{
				int difference = quantity - previous.getOrDefault(id, 0);
				if (difference > 0)
				{
					gains.merge(id, difference, InventoryGainTracker::addQuantities);
				}
			});
		}
		previous = new HashMap<>(current);
	}

	Map<Integer, Integer> gains()
	{
		return Collections.unmodifiableMap(new HashMap<>(gains));
	}

	static Map<Integer, Integer> totals(Item[] items)
	{
		if (items == null)
		{
			return null;
		}
		Map<Integer, Integer> quantities = new HashMap<>();
		for (Item item : items)
		{
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
			{
				quantities.merge(item.getId(), item.getQuantity(), InventoryGainTracker::addQuantities);
			}
		}
		return quantities;
	}

	private static int addQuantities(int first, int second)
	{
		// The recap contract uses Integer quantities; do not overflow into negative gains.
		return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
	}
}
