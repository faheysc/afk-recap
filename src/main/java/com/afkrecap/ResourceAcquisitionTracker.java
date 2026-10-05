package com.afkrecap;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.afkrecap.ResourceAcquisitionMessages.Family;

/** Session-local reconciliation. Hold one completed tick for delayed inventory/transfer events. */
final class ResourceAcquisitionTracker
{
	private final InventoryGainTracker inventory;
	private final Map<Family, Long> transferUntil = new EnumMap<>(Family.class);
	private Batch previous;
	private Batch current = new Batch();
	private long tick;
	private Integer lastResource;
	private Family lastFamily;
	private final Set<String> bonuses = new HashSet<>();

	ResourceAcquisitionTracker(Map<Integer, Integer> baseline)
	{
		inventory = new InventoryGainTracker(baseline);
	}

	void inventoryChanged(Map<Integer, Integer> snapshot)
	{
		inventory.update(snapshot, id -> ResourceAcquisitionMessages.family(id) == null)
			.forEach((id, count) ->
			{
				Family family = ResourceAcquisitionMessages.family(id);
				if (family != null && !transferring(family))
				{
					current.inventory.merge(id, count, InventoryGainTracker::addQuantities);
					current.available.merge(id, count, InventoryGainTracker::addQuantities);
				}
			});
	}

	void message(String text, boolean fishStorage, boolean logStorage)
	{
		if (text == null)
		{
			return;
		}
		Family bonus = ResourceAcquisitionMessages.bonus(text);
		if (bonus != null)
		{
			// Only an immediately preceding typed acquisition can identify this extra item.
			if (lastResource != null && lastFamily == bonus
				&& (bonus == Family.FISH ? fishStorage : logStorage) && bonuses.add(text))
			{
				current.messages.merge(lastResource, 1, InventoryGainTracker::addQuantities);
			}
			return;
		}
		clearContext();
		for (Family family : Family.values())
		{
			Integer id = ResourceAcquisitionMessages.resource(text, family);
			if (id != null && (family == Family.FISH ? fishStorage : logStorage))
			{
				current.messages.merge(id, 1, InventoryGainTracker::addQuantities);
				lastResource = id;
				lastFamily = family;
				return;
			}
		}
	}

	void transfer(Family family)
	{
		transferUntil.put(family, tick + 2);
		// Completion messages can arrive after the inventory event, including near a tick boundary.
		removeTransfers(current, family);
		if (previous != null)
		{
			removeTransfers(previous, family);
		}
		clearContext();
	}

	void gameTick()
	{
		if (previous != null)
		{
			reconcile();
			commit(previous);
		}
		previous = current;
		current = new Batch();
		tick++;
		clearContext();
	}

	// Commit already observed acquisitions before replacing synchronization baselines.
	void rebaseline(Map<Integer, Integer> snapshot)
	{
		gains();
		inventory.baseline(snapshot);
		transferUntil.clear();
	}

	Map<Integer, Integer> gains()
	{
		reconcile();
		if (previous != null)
		{
			commit(previous);
			previous = null;
		}
		commit(current);
		current = new Batch();
		clearContext();
		return inventory.gains();
	}

	private boolean transferring(Family family)
	{
		Long until = transferUntil.get(family);
		return until != null && tick <= until;
	}

	private void reconcile()
	{
		if (previous != null)
		{
			match(previous, previous);
			match(previous, current);
			match(current, previous);
		}
		match(current, current);
	}

	private void match(Batch messages, Batch additions)
	{
		messages.messages.replaceAll((id, count) ->
		{
			int available = additions.available.getOrDefault(id, 0);
			int matched = Math.min(count, available);
			additions.available.put(id, available - matched);
			// Matched evidence confirms an acquisition now; a subsequent Fill must not erase it.
			additions.inventory.computeIfPresent(id, (item, quantity) -> quantity - matched);
			inventory.addGain(id, matched);
			return count - matched;
		});
	}

	private void commit(Batch batch)
	{
		batch.inventory.forEach(inventory::addGain);
		batch.messages.forEach(inventory::addGain);
	}

	private static void removeTransfers(Batch batch, Family family)
	{
		// Keep confirmed acquisitions; discard uncertain inventory additions during a transfer.
		batch.inventory.keySet().removeIf(id -> ResourceAcquisitionMessages.family(id) == family);
		batch.available.keySet().removeIf(id -> ResourceAcquisitionMessages.family(id) == family);
	}

	private void clearContext()
	{
		lastResource = null;
		lastFamily = null;
		bonuses.clear();
	}

	private static final class Batch
	{
		private final Map<Integer, Integer> inventory = new HashMap<>();
		private final Map<Integer, Integer> available = new HashMap<>();
		private final Map<Integer, Integer> messages = new HashMap<>();
	}
}
