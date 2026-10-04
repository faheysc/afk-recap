package com.afkrecap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
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
		update(current, id -> true);
	}

	/** Return positive deltas while allowing the caller to defer selected resources. */
	Map<Integer, Integer> update(Map<Integer, Integer> current, IntPredicate countImmediately)
	{
		Map<Integer, Integer> additions = new HashMap<>();
		if (current == null)
		{
			previous = null;
			return additions;
		}
		if (previous != null)
		{
			current.forEach((id, quantity) ->
			{
				int difference = quantity - previous.getOrDefault(id, 0);
				if (difference > 0)
				{
					additions.put(id, difference);
					if (countImmediately.test(id))
					{
						addGain(id, difference);
					}
				}
			});
		}
		previous = new HashMap<>(current);
		return additions;
	}

	void addGain(int id, int quantity)
	{
		if (quantity > 0)
		{
			gains.merge(id, quantity, InventoryGainTracker::addQuantities);
		}
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

	static int addQuantities(int first, int second)
	{
		// The recap contract uses Integer quantities; do not overflow into negative gains.
		return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
	}
}
