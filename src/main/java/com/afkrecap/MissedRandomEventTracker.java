package com.afkrecap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

// Client-thread, session-scoped evidence. NPC identity avoids index reuse and duplicate signals.
final class MissedRandomEventTracker
{
	private final Map<Object, Event> events = new IdentityHashMap<>();
	private final Map<String, Integer> misses = new TreeMap<>();

	void targeted(Object npc, int id, String name, boolean targetsLocal, boolean localInteractingBack)
	{
		if (npc != null && targetsLocal && !localInteractingBack && RandomEventTypes.contains(id)
			&& name != null && !name.trim().isEmpty())
		{
			events.putIfAbsent(npc, new Event(name));
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
		for (Event event : events.values())
		{
			if (event.despawned && !event.resolved)
			{
				event.resolved = true;
				if (!event.handled)
				{
					misses.merge(event.name, 1, Integer::sum);
				}
			}
		}
	}

	Map<String, Integer> misses()
	{
		return misses;
	}

	void clear()
	{
		events.clear();
		misses.clear();
	}

	private static final class Event
	{
		private final String name;
		private boolean handled;
		private boolean despawned;
		private boolean resolved;

		private Event(String name)
		{
			this.name = name;
		}
	}
}
