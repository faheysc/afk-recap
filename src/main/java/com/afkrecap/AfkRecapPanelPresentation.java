package com.afkrecap;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class AfkRecapPanelPresentation
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private AfkRecapPanelPresentation()
	{
	}

	static String header(AfkRecapSession recap, ZoneId zone)
	{
		String trigger = recap.getTrigger() == AfkSessionTrigger.FOCUS ? "Focus" : "Idle";
		long seconds = recap.getElapsedMillis() / 1000;
		String duration = seconds < 60 ? AfkRecapController.formatDuration(recap.getElapsedMillis())
			: String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
		return TIME.format(recap.getEndTimestamp().atZone(zone)) + " — " + trigger + " — " + duration;
	}

	static String xp(long gained)
	{
		return String.format(Locale.US, "+%,d XP", gained);
	}
}
