package com.afkrecap;

import java.util.regex.Pattern;
import net.runelite.api.gameval.AnimationID;

// Signals adapted from RuneLite 1.13.1 IdleNotifierPlugin and LootTrackerPlugin; see META-INF/NOTICE.
final class SailingActivitySignals
{
	private static final Pattern SORTED = Pattern.compile("You sort through the\\s+\\S+\\s+salvage.*");

	private SailingActivitySignals()
	{
	}

	static IdleActivity animation(int animation)
	{
		switch (animation)
		{
			case AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_IDLE01:
			case AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_IDLE01:
			case AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_IDLE01:
			case AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_SALVAGING01:
				return IdleActivity.SAILING_SALVAGING;
			case AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_INTERACT01:
				return IdleActivity.SAILING_SORTING;
			default:
				// Setup/reset/crew/boat animations do not positively identify gathering.
				return null;
		}
	}

	static IdleActivity message(String message)
	{
		return message != null && SORTED.matcher(message).matches() ? IdleActivity.SAILING_SORTING : null;
	}
}
