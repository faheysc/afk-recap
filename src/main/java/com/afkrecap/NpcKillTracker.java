package com.afkrecap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Session-local evidence; NPC object identity avoids index reuse and morph issues. */
final class NpcKillTracker
{
	private final Map<Object, Evidence> evidence = new IdentityHashMap<>();
	private final Map<String, Integer> totals = new TreeMap<>();

	void damage(Object npc, int amount, boolean mine, boolean others)
	{
		if (npc == null || amount <= 0 || (!mine && !others))
		{
			return;
		}
		Evidence hit = evidence.computeIfAbsent(npc, ignored -> new Evidence());
		hit.mine |= mine;
		hit.others |= others;
	}

	void death(Object npc, String name)
	{
		Evidence hit = evidence.get(npc);
		if (hit == null || hit.dead)
		{
			return;
		}
		hit.dead = true;
		if (hit.mine && !hit.others && name != null && !name.trim().isEmpty())
		{
			totals.merge(name, 1, (count, added) -> count == Integer.MAX_VALUE ? count : count + added);
		}
	}

	void despawn(Object npc)
	{
		evidence.remove(npc);
	}

	void clearEvidence()
	{
		evidence.clear();
	}

	Map<String, Integer> totals()
	{
		return totals;
	}

	private static final class Evidence
	{
		private boolean mine;
		private boolean others;
		private boolean dead;
	}
}
