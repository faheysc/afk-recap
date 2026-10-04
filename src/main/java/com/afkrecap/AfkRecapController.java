package com.afkrecap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;

/** Presentation state shared by client events and overlay rendering; no game state mutations. */
@Singleton
public final class AfkRecapController
{
	private final LongSupplier nanoTime;
	private Presentation presentation;
	private long displayedAtNanos;
	private long displayDurationNanos;

	@Inject
	public AfkRecapController()
	{
		this(System::nanoTime);
	}

	AfkRecapController(LongSupplier nanoTime)
	{
		this.nanoTime = nanoTime;
	}

	synchronized void show(AfkRecapSession recap, boolean enabled, int durationSeconds)
	{
		show(recap, enabled, durationSeconds, AfkRecapItemPresentation.empty());
	}

	synchronized void show(AfkRecapSession recap, boolean enabled, int durationSeconds,
		AfkRecapItemPresentation.Display items)
	{
		if (!enabled)
		{
			clear();
			return;
		}
		if (!recap.hasGains()
			|| (recap.getTrigger() == AfkSessionTrigger.IDLE && recap.getRelevantIdleSkills().isEmpty()))
		{
			return;
		}
		presentation = new Presentation(recap, items);
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
		private final List<AfkRecapItemPresentation.ItemRow> itemRows;
		private final String totalValue;
		private final Map<String, Integer> npcKills;
		private final String prayerUsed;
		private final String damageTaken;

		private Presentation(AfkRecapSession recap, AfkRecapItemPresentation.Display items)
		{
			damageTaken = recap.getDamageTaken() > 0 ? Long.toString(recap.getDamageTaken()) : null;
			npcKills = recap.getNpcKills();
			prayerUsed = recap.getPrayerUsed() > 0 ? Long.toString(recap.getPrayerUsed()) : null;
			itemRows = items.getRows();
			totalValue = items.getTotalValue();
			duration = formatDuration(recap.getElapsedMillis());
			trigger = recap.getTrigger() == AfkSessionTrigger.FOCUS ? "Focus" : "Idle";
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
