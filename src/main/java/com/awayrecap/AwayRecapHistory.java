package com.awayrecap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.inject.Singleton;

/** Bounded, in-memory completed recap history, independent of panel visibility and Swing. */
@Singleton
public final class AwayRecapHistory
{
	private final List<AwayRecapSession> recaps = new ArrayList<>();
	private int limit = 20;

	synchronized boolean add(AwayRecapSession recap)
	{
		if (recap.getXpGained().isEmpty()
			|| (recap.getTrigger() == AwaySessionTrigger.IDLE && recap.getRelevantIdleSkills().isEmpty()))
		{
			return false;
		}
		recaps.add(0, recap);
		// Stable sort keeps the newest arrival first when completion timestamps tie.
		recaps.sort(Comparator.comparing(AwayRecapSession::getEndTimestamp).reversed());
		trim();
		return true;
	}

	synchronized void setLimit(int limit)
	{
		this.limit = Math.max(1, Math.min(100, limit));
		trim();
	}

	synchronized List<AwayRecapSession> snapshot()
	{
		return Collections.unmodifiableList(new ArrayList<>(recaps));
	}

	synchronized void clear()
	{
		recaps.clear();
	}

	private void trim()
	{
		if (recaps.size() > limit)
		{
			recaps.subList(limit, recaps.size()).clear();
		}
	}
}
