package com.afkrecap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

// Retain the persisted group so existing settings survive the AFK Recap rename.
@ConfigGroup("away-recap")
public interface AfkRecapConfig extends Config
{
	@ConfigItem(keyName = "startOnFocusLoss", name = "Start on focus loss", description = "Start an away session when RuneLite loses focus", position = 0)
	default boolean startOnFocusLoss()
	{
		return true;
	}

	@ConfigItem(keyName = "startOnIdle", name = "Start on idle", description = "Start a candidate session after no mouse clicks or key presses", position = 1)
	default boolean startOnIdle()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "idleGameTicks", name = "Idle game ticks", description = "Game ticks without manual input (approximately 600 ms per tick)", position = 2)
	default int idleGameTicks()
	{
		return 10;
	}

	@ConfigItem(keyName = "idleFishing", name = "Idle Fishing", description = "Fishing XP makes an idle session relevant", position = 3)
	default boolean idleFishing()
	{
		return true;
	}

	@ConfigItem(keyName = "idleMining", name = "Idle Mining", description = "Mining XP makes an idle session relevant", position = 4)
	default boolean idleMining()
	{
		return true;
	}

	@ConfigItem(keyName = "idleWoodcutting", name = "Idle Woodcutting", description = "Woodcutting XP makes an idle session relevant", position = 5)
	default boolean idleWoodcutting()
	{
		return true;
	}

	// Keep the legacy key: existing Sailing preferences now control Salvaging only.
	@ConfigItem(keyName = "idleSailing", name = "Idle Sailing Salvaging", description = "Gathering salvage makes an idle session relevant", position = 6)
	default boolean idleSailingSalvaging()
	{
		return true;
	}

	@ConfigItem(keyName = "idleSailingSorting", name = "Idle Sailing Sorting", description = "Sorting salvage makes an idle session relevant", position = 7)
	default boolean idleSailingSorting()
	{
		return false;
	}

	// Retain the former Slayer key so saved opt-outs carry over to Combat.
	@ConfigItem(keyName = "idleSlayer", name = "Idle Combat", description = "Attack, Strength, Defence, Ranged, Magic, or Hitpoints XP makes an idle session relevant", position = 8)
	default boolean idleCombat()
	{
		return true;
	}

	@ConfigItem(keyName = "idleSlayerActivity", name = "Idle Slayer", description = "Slayer XP makes an idle session relevant independently of Combat", position = 9)
	default boolean idleSlayer()
	{
		// RuneLite persists absent defaults when loading plugin/profile configuration.
		// Seed the split from the legacy combined preference once; saved values override this.
		return idleCombat();
	}

	@ConfigItem(keyName = "showOverlay", name = "Show recap overlay", description = "Show completed recaps in a temporary overlay; collection and debug logging continue when disabled", position = 10)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(min = 3, max = 60)
	@ConfigItem(keyName = "overlayDurationSeconds", name = "Overlay duration", description = "Seconds to display each completed recap (applies to the next recap)", position = 11)
	default int overlayDurationSeconds()
	{
		return 10;
	}

	@ConfigItem(keyName = "enableSidePanel", name = "Enable side panel", description = "Show recent recaps in the sidebar; history continues collecting when disabled", position = 12)
	default boolean enableSidePanel()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "recentRecapLimit", name = "Recent recap limit", description = "Maximum recent recaps retained in memory; reducing this trims history immediately", position = 13)
	default int recentRecapLimit()
	{
		return 20;
	}
	@ConfigItem(keyName = "trackNotableDrops", name = "Track notable drops", description = "Record notable ground drops owned by the local player during a session", position = 14)
	default boolean trackNotableDrops()
	{
		return true;
	}

	@Range(min = 0, max = Integer.MAX_VALUE)
	@ConfigItem(keyName = "notableDropValue", name = "Notable drop value", description = "Minimum approximate GE value of a newly spawned stack or stack addition", position = 15)
	default int notableDropValue()
	{
		return 100000;
	}

	@ConfigItem(keyName = "alwaysTrackClues", name = "Always track clues", description = "Include owned clue scroll drops regardless of value when notable-drop tracking is enabled", position = 16)
	default boolean alwaysTrackClues()
	{
		return true;
	}

	@ConfigItem(keyName = "trackMissedRandomEvents", name = "Track missed random events", description = "Record unhandled random events targeting you that disappear while away", position = 17)
	default boolean trackMissedRandomEvents()
	{
		return true;
	}

}
