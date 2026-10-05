package com.afkrecap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.TreeMap;

// Client-thread, session-scoped evidence. NPC identity avoids index reuse and duplicate signals.
final class MissedRandomEventTracker
{
	private final Map<Object, Event> events = new IdentityHashMap<>();
	// Resolved NPCs are tombstones only; values never retain NPCs. RuneLite NPCs use object equality.
	private final Map<Object, Boolean> resolved = new WeakHashMap<>();
	private final Map<String, Integer> misses = new TreeMap<>();

	void targeted(Object npc, int id, String name, boolean targetsLocal, boolean localInteractingBack)
	{
		if (npc != null && targetsLocal && !localInteractingBack && RandomEventTypes.contains(id)
			&& name != null && !name.trim().isEmpty() && !resolved.containsKey(npc) && !events.containsKey(npc))
		{
			events.put(npc, new Event(name));
		}
	}

	void handled(Object npc)
	{
		Event event = events.get(npc);
		if (event != null)
		{
			event.handled = true;
		}
	}

	void despawned(Object npc)
	{
		Event event = events.get(npc);
		if (event != null)
		{
			event.despawned = true;
		}
	}

	void gameTick()
	{
		Iterator<Map.Entry<Object, Event>> iterator = events.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Object, Event> entry = iterator.next();
			Event event = entry.getValue();
			if (event.despawned)
			{
				resolved.put(entry.getKey(), Boolean.TRUE);
				iterator.remove();
				if (!event.handled)
				{
					misses.merge(event.name, 1, InventoryGainTracker::addQuantities);
				}
			}
		}
	}

	int activeEventCount()
	{
		return events.size();
	}

	Map<String, Integer> misses()
	{
		return misses;
	}

	void clearEvidence()
	{
		events.clear();
		resolved.clear();
	}

	void clear()
	{
		clearEvidence();
		misses.clear();
	}

	private static final class Event
	{
		private final String name;
		private boolean handled;
		private boolean despawned;

		private Event(String name)
		{
			this.name = name;
		}
	}
}
