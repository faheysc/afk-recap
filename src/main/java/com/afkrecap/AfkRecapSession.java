package com.afkrecap;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import net.runelite.api.Skill;

/** Immutable completed-session data, independent of logging and rendering. */
@Getter
public final class AfkRecapSession
{
	private final AfkSessionTrigger trigger;
	private final long startGameTick;
	private final Instant startTimestamp;
	private final long endGameTick;
	private final Instant endTimestamp;
	private final long elapsedMillis;
	private final Map<Skill, Long> xpGained;
	private final Set<Skill> relevantIdleSkills;
	private final Map<Integer, Integer> itemGains;

	AfkRecapSession(AfkSessionTrigger trigger, long startGameTick, Instant startTimestamp,
		long endGameTick, Instant endTimestamp, long elapsedMillis,
		Map<Skill, Long> xpGained, Set<Skill> relevantIdleSkills)
	{
		this(trigger, startGameTick, startTimestamp, endGameTick, endTimestamp, elapsedMillis,
			xpGained, relevantIdleSkills, Collections.emptyMap());
	}

	AfkRecapSession(AfkSessionTrigger trigger, long startGameTick, Instant startTimestamp,
		long endGameTick, Instant endTimestamp, long elapsedMillis,
		Map<Skill, Long> xpGained, Set<Skill> relevantIdleSkills, Map<Integer, Integer> itemGains)
	{
		this.trigger = trigger;
		this.startGameTick = startGameTick;
		this.startTimestamp = startTimestamp;
		this.endGameTick = endGameTick;
		this.endTimestamp = endTimestamp;
		// Use monotonic elapsed time independently of wall-clock timestamp adjustments.
		this.elapsedMillis = Math.max(0, elapsedMillis);
		EnumMap<Skill, Long> xp = new EnumMap<>(Skill.class);
		xpGained.forEach((skill, gained) ->
		{
			if (gained > 0)
			{
				xp.put(skill, gained);
			}
		});
		this.xpGained = Collections.unmodifiableMap(xp);
		EnumSet<Skill> relevant = EnumSet.noneOf(Skill.class);
		relevant.addAll(relevantIdleSkills);
		this.relevantIdleSkills = Collections.unmodifiableSet(relevant);
		Map<Integer, Integer> items = new TreeMap<>();
		itemGains.forEach((id, quantity) ->
		{
			if (id >= 0 && quantity > 0)
			{
				items.put(id, quantity);
			}
		});
		this.itemGains = Collections.unmodifiableMap(items);
	}

	public boolean hasGains()
	{
		return !xpGained.isEmpty() || !itemGains.isEmpty();
	}

	public long getElapsedGameTicks()
	{
		return endGameTick - startGameTick;
	}
}
