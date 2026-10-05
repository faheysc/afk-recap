package com.afkrecap;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Map;
import net.runelite.api.TileItem;

/** Session-local, owned ground piles only. No scene scans or loot-event guesses. */
final class NotableDropTracker
{
	// Scene resets can implicitly despawn piles without ItemDespawned; never retain those objects.
	private final Map<Object, Integer> quantities = new WeakHashMap<>();
	private final Map<Integer, Integer> totals = new HashMap<>();

	static boolean attributed(int ownership)
	{
		return ownership == TileItem.OWNERSHIP_SELF;
	}

	void spawn(Object pile, int id, int quantity, int ownership, long price, boolean clue, AfkRecapConfig config)
	{
		if (pile == null || id < 0 || quantity <= 0 || !attributed(ownership)
			|| !config.trackNotableDrops() || quantities.containsKey(pile))
		{
			return;
		}
		quantities.put(pile, quantity);
		credit(id, quantity, price, clue, config);
	}

	void quantityChanged(Object pile, int id, int oldQuantity, int newQuantity, int ownership,
		long price, boolean clue, AfkRecapConfig config)
	{
		Integer previous = quantities.get(pile);
		// A pre-session pile or out-of-order event is insufficient acquisition evidence.
		if (previous == null || previous != oldQuantity || newQuantity < 0)
		{
			return;
		}
		quantities.put(pile, newQuantity);
		if (attributed(ownership) && config.trackNotableDrops() && newQuantity > oldQuantity)
		{
			credit(id, newQuantity - oldQuantity, price, clue, config);
		}
	}

	void despawn(Object pile)
	{
		quantities.remove(pile);
	}

	private void credit(int id, int quantity, long price, boolean clue, AfkRecapConfig config)
	{
		long threshold = Math.max(0, config.notableDropValue());
		// Compare stack value without multiplying a potentially large price/quantity.
		boolean valuable = price >= 0 && price >= (threshold + quantity - 1L) / quantity;
		if (valuable || (clue && config.alwaysTrackClues()))
		{
			totals.merge(id, quantity, InventoryGainTracker::addQuantities);
		}
	}

	void clearEvidence()
	{
		quantities.clear();
	}

	Map<Integer, Integer> totals()
	{
		return totals;
	}
}
