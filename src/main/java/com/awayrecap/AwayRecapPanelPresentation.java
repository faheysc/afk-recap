package com.awayrecap;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class AwayRecapPanelPresentation
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private AwayRecapPanelPresentation()
	{
	}

	static String header(AwayRecapSession recap, ZoneId zone)
	{
		String trigger = recap.getTrigger() == AwaySessionTrigger.FOCUS ? "Focus" : "Idle";
		long seconds = recap.getElapsedMillis() / 1000;
		String duration = seconds < 60 ? AwayRecapController.formatDuration(recap.getElapsedMillis())
			: String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
		return TIME.format(recap.getEndTimestamp().atZone(zone)) + " — " + trigger + " — " + duration;
	}

	static String xp(long gained)
	{
		return String.format(Locale.US, "+%,d XP", gained);
	}
}
