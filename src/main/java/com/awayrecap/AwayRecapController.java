package com.awayrecap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;

/** Presentation state shared by client events and overlay rendering; no game state mutations. */
@Singleton
public final class AwayRecapController
{
	private final LongSupplier nanoTime;
	private Presentation presentation;
	private long displayedAtNanos;
	private long displayDurationNanos;

	@Inject
	public AwayRecapController()
	{
		this(System::nanoTime);
	}

	AwayRecapController(LongSupplier nanoTime)
	{
		this.nanoTime = nanoTime;
	}

	synchronized void show(AwayRecapSession recap, boolean enabled, int durationSeconds)
	{
		if (!enabled)
		{
			clear();
			return;
		}
		if (recap.getXpGained().isEmpty()
			|| (recap.getTrigger() == AwaySessionTrigger.IDLE && recap.getRelevantIdleSkills().isEmpty()))
		{
			return;
		}
		presentation = new Presentation(recap);
		displayedAtNanos = nanoTime.getAsLong();
		displayDurationNanos = TimeUnit.SECONDS.toNanos(Math.max(3, Math.min(60, durationSeconds)));
	}

	synchronized Presentation visiblePresentation(boolean enabled)
	{
		if (!enabled || (presentation != null
			&& nanoTime.getAsLong() - displayedAtNanos >= displayDurationNanos))
		{
			clear();
		}
		return presentation;
	}

	synchronized void clear()
	{
		presentation = null;
	}

	static String formatDuration(long elapsedMillis)
	{
		if (elapsedMillis < 60_000)
		{
			return String.format(Locale.ROOT, "%.1fs", elapsedMillis / 1000.0);
		}
		long seconds = elapsedMillis / 1000;
		return seconds / 60 + "m " + seconds % 60 + "s";
	}

	@Getter
	static final class Presentation
	{
		private final String duration;
		private final String trigger;
		private final List<XpRow> xpRows;

		private Presentation(AwayRecapSession recap)
		{
			duration = formatDuration(recap.getElapsedMillis());
			trigger = recap.getTrigger() == AwaySessionTrigger.FOCUS ? "Focus" : "Idle";
			List<XpRow> rows = new ArrayList<>();
			recap.getXpGained().forEach((skill, gained) ->
			{
				if (gained > 0)
				{
					rows.add(new XpRow(skill.getName(), "+" + gained + " XP"));
				}
			});
			xpRows = Collections.unmodifiableList(rows);
		}
	}

	@Getter
	static final class XpRow
	{
		private final String skill;
		private final String gained;

		private XpRow(String skill, String gained)
		{
			this.skill = skill;
			this.gained = gained;
		}
	}
}
