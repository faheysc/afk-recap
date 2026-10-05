package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SailingIdleActivityTest
{
	private AfkRecapConfig config(boolean salvaging, boolean sorting)
	{
		return new AfkRecapConfig()
		{
			@Override public boolean idleSailingSalvaging() { return salvaging; }
			@Override public boolean idleSailingSorting() { return sorting; }
		};
	}

	private AfkSessionManager candidate(AfkRecapConfig config, List<AfkRecapSession> recaps)
	{
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
			recaps::add, Collections::emptyMap);
		manager.baseline(Collections.singletonMap(Skill.SAILING, 1000));
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		return manager;
	}

	private void combination(boolean salvaging, boolean sorting)
	{
		AfkRecapConfig config = config(salvaging, sorting);
		for (IdleActivity signal : new IdleActivity[] {
			SailingActivitySignals.animation(AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_IDLE01),
			SailingActivitySignals.animation(AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_INTERACT01),
			SailingActivitySignals.message("You sort through the small salvage and find some loot.") })
		{
			List<AfkRecapSession> recaps = new ArrayList<>();
			AfkSessionManager manager = candidate(config, recaps);
			manager.inventoryChanged(Collections.singletonMap(ItemID.COINS, 20));
			manager.statChanged(Skill.SAILING, 1050);
			assertTrue("Generic Sailing XP cannot identify an activity", manager.relevantSkills().isEmpty());
			manager.activityDetected(signal);
			boolean enabled = signal == IdleActivity.SAILING_SALVAGING ? salvaging : sorting;
			assertEquals(enabled, manager.relevantSkills().contains(Skill.SAILING));
			manager.manualInput();
			assertEquals(enabled ? 1 : 0, recaps.size());
			if (enabled)
			{
				assertEquals(Collections.singletonMap(Skill.SAILING, 50L), recaps.get(0).getXpGained());
				assertEquals(Collections.singletonMap(ItemID.COINS, 20), recaps.get(0).getItemGains());
			}
		}
	}

	@Test public void salvagingOnly() { combination(true, false); }
	@Test public void sortingOnly() { combination(false, true); }
	@Test public void bothEnabled() { combination(true, true); }
	@Test public void bothDisabled() { combination(false, false); }

	@Test
	public void genericSailingXpAloneNeverQualifies()
	{
		for (boolean salvaging : new boolean[] { false, true })
		{
			for (boolean sorting : new boolean[] { false, true })
			{
				List<AfkRecapSession> recaps = new ArrayList<>();
				AfkSessionManager manager = candidate(config(salvaging, sorting), recaps);
				manager.statChanged(Skill.SAILING, 1050);
				manager.manualInput();
				assertTrue(recaps.isEmpty());
			}
		}
	}

	@Test
	public void disabledSailingXpStillAppearsInAnotherRelevantIdleRecap()
	{
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkSessionManager manager = candidate(config(false, false), recaps);
		manager.statChanged(Skill.SAILING, 1050);
		manager.statChanged(Skill.MINING, 1000); // Baseline, not an acquisition.
		manager.statChanged(Skill.MINING, 1010);
		manager.manualInput();
		assertEquals(1, recaps.size());
		assertEquals(Long.valueOf(50), recaps.get(0).getXpGained().get(Skill.SAILING));
		assertEquals(Collections.singleton(Skill.MINING), recaps.get(0).getRelevantIdleSkills());
	}

	@Test
	public void verifiedGatheringAnimationsAreDistinctFromSorting()
	{
		for (int animation : new int[] {
			AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_IDLE01,
			AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_IDLE01,
			AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_IDLE01,
			AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_SALVAGING01 })
		{
			assertEquals(IdleActivity.SAILING_SALVAGING, SailingActivitySignals.animation(animation));
		}
		assertEquals(IdleActivity.SAILING_SORTING,
			SailingActivitySignals.animation(AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_INTERACT01));
		assertNull(SailingActivitySignals.animation(-1));
		assertNull(SailingActivitySignals.animation(AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_RESET01));
		assertNull(SailingActivitySignals.animation(AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_DROP01_CREW));
	}

	@Test
	public void sortingMessagesUseRuneLiteLootTrackerPattern()
	{
		for (String tier : new String[] { "small", "fishy", "barracuda", "large", "plundered", "martial", "fremennik", "opulent" })
		{
			assertEquals(IdleActivity.SAILING_SORTING,
				SailingActivitySignals.message("You sort through the " + tier + " salvage and find some loot."));
		}
		assertNull(SailingActivitySignals.message(null));
		assertNull(SailingActivitySignals.message("You gain some Sailing experience."));
		assertNull(SailingActivitySignals.message("You gather some small salvage."));
	}

	@Test
	public void focusCollectsSailingWithEveryToggleCombination()
	{
		for (boolean salvaging : new boolean[] { false, true })
		{
			for (boolean sorting : new boolean[] { false, true })
			{
				AfkRecapConfig config = config(salvaging, sorting);
				List<AfkRecapSession> recaps = new ArrayList<>();
				AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH, recaps::add);
				manager.baseline(Collections.singletonMap(Skill.SAILING, 1000));
				manager.focusChanged(false, config);
				manager.activityDetected(IdleActivity.SAILING_SALVAGING);
				manager.activityDetected(IdleActivity.SAILING_SORTING);
				manager.statChanged(Skill.SAILING, 1050);
				manager.focusChanged(true, config);
				assertEquals(1, recaps.size());
				assertTrue(recaps.get(0).getRelevantIdleSkills().isEmpty());
				assertEquals(Long.valueOf(50), recaps.get(0).getXpGained().get(Skill.SAILING));
			}
		}
	}

	@Test
	public void activitySignalsDoNotLeakAcrossCandidatesOrLogout()
	{
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkRecapConfig config = config(true, false);
		AfkSessionManager manager = candidate(config, recaps);
		manager.activityDetected(IdleActivity.SAILING_SALVAGING);
		manager.statChanged(Skill.SAILING, 1050);
		manager.gameStateChanged(GameState.LOGIN_SCREEN);
		assertEquals(1, recaps.size());
		manager.activityDetected(IdleActivity.SAILING_SALVAGING);
		manager.loggedIn(Collections.singletonMap(Skill.SAILING, 1050), 99);
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		manager.statChanged(Skill.SAILING, 1100);
		manager.manualInput();
		assertEquals(1, recaps.size());
	}

	@Test
	public void defaultsPreserveSalvagingAndOptOutOfSorting()
	{
		AfkRecapConfig defaults = new AfkRecapConfig() {};
		assertTrue(defaults.idleSailingSalvaging());
		assertFalse(defaults.idleSailingSorting());
		assertFalse(IdleActivity.enabledSkills(defaults).contains(Skill.SAILING));
	}
}
