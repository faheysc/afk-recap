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

	@ConfigItem(keyName = "idleSailing", name = "Idle Sailing", description = "Sailing XP makes an idle session relevant", position = 6)
	default boolean idleSailing()
	{
		return true;
	}

	@ConfigItem(keyName = "idleSlayer", name = "Idle Slayer", description = "Slayer XP makes an idle session relevant", position = 7)
	default boolean idleSlayer()
	{
		return true;
	}

	@ConfigItem(keyName = "showOverlay", name = "Show recap overlay", description = "Show completed recaps in a temporary overlay; collection and debug logging continue when disabled", position = 8)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(min = 3, max = 60)
	@ConfigItem(keyName = "overlayDurationSeconds", name = "Overlay duration", description = "Seconds to display each completed recap (applies to the next recap)", position = 9)
	default int overlayDurationSeconds()
	{
		return 10;
	}

	@ConfigItem(keyName = "enableSidePanel", name = "Enable side panel", description = "Show recent recaps in the sidebar; history continues collecting when disabled", position = 10)
	default boolean enableSidePanel()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "recentRecapLimit", name = "Recent recap limit", description = "Maximum recent recaps retained in memory; reducing this trims history immediately", position = 11)
	default int recentRecapLimit()
	{
		return 20;
	}
}
