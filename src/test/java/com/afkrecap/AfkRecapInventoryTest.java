package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapInventoryTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private Map<Integer, Integer> inventory = new HashMap<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L,
		() -> Instant.EPOCH, recaps::add, () -> inventory);

	@Before
	public void baselineXp()
	{
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			xp.put(skill, 1000);
		}
		manager.baseline(xp);
	}

	private void ticks(int count, AfkRecapConfig settings)
	{
		for (int i = 0; i < count; i++)
		{
			manager.gameTick(settings);
		}
	}

	private void gainItem(int quantity)
	{
		inventory = Collections.singletonMap(ItemID.YEW_LOGS, quantity);
		manager.inventoryChanged(inventory);
	}

	@Test
	public void focusWithOnlyItemsProducesRecapAndIsAcceptedByHistoryAndOverlay()
	{
		manager.focusChanged(false, config);
		gainItem(12);
		manager.focusChanged(true, config);
		assertEquals(1, recaps.size());
		AfkRecapSession recap = recaps.get(0);
		assertTrue(recap.getXpGained().isEmpty());
		assertEquals(Collections.singletonMap(ItemID.YEW_LOGS, 12), recap.getItemGains());
		AfkRecapHistory history = new AfkRecapHistory();
		assertTrue(history.add(recap));
		AfkRecapItemPresentation items = new AfkRecapItemPresentation(id ->
			new AfkRecapItemPresentation.ItemDetails("Yew logs", 270));
		AfkRecapController overlay = new AfkRecapController(() -> 0L);
		overlay.show(recap, true, 10, items.prepare(recap));
		assertNotNull(overlay.visiblePresentation(true));
		assertTrue(overlay.visiblePresentation(true).getXpRows().isEmpty());
		assertEquals(1, overlay.visiblePresentation(true).getItemRows().size());
	}

	@Test
	public void inventoryIsSnapshottedAtEverySessionStart()
	{
		gainItem(10); // Outside a session, no acquisitions are recorded.
		manager.focusChanged(false, config);
		gainItem(12);
		manager.focusChanged(true, config);
		manager.focusChanged(false, config);
		gainItem(15);
		manager.focusChanged(true, config);
		assertEquals(Integer.valueOf(2), recaps.get(0).getItemGains().get(ItemID.YEW_LOGS));
		assertEquals(Integer.valueOf(3), recaps.get(1).getItemGains().get(ItemID.YEW_LOGS));
	}

	@Test
	public void missingInventoryAtStartDoesNotCountFirstSynchronization()
	{
		inventory = null;
		manager.focusChanged(false, config);
		gainItem(100);
		manager.focusChanged(true, config);
		assertTrue(recaps.isEmpty());
		inventory = null;
		manager.focusChanged(false, config);
		gainItem(100);
		gainItem(102);
		manager.focusChanged(true, config);
		assertEquals(Collections.singletonMap(ItemID.YEW_LOGS, 2), recaps.get(0).getItemGains());
	}

	@Test
	public void idleWithItemsButNoQualifyingXpIsDiscarded()
	{
		ticks(10, config);
		gainItem(12);
		manager.statChanged(Skill.HITPOINTS, 1012);
		manager.manualInput();
		assertNull(manager.trigger());
		assertTrue(recaps.isEmpty());
		ticks(10, config);
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
	}

	@Test
	public void relevantIdleIncludesBothXpAndEarlierItemGains()
	{
		ticks(10, config);
		gainItem(12);
		ticks(70, config);
		assertTrue(recaps.isEmpty());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(Skill.WOODCUTTING, 1525);
		manager.manualInput();
		assertEquals(Collections.singletonMap(Skill.WOODCUTTING, 525L), recaps.get(0).getXpGained());
		assertEquals(Collections.singletonMap(ItemID.YEW_LOGS, 12), recaps.get(0).getItemGains());
	}

	@Test
	public void repeatedIdleSessionsKeepSeparateAcquisitionTotals()
	{
		ticks(10, config);
		gainItem(2);
		manager.statChanged(Skill.MINING, 1010);
		manager.manualInput();
		ticks(10, config);
		gainItem(5);
		manager.statChanged(Skill.MINING, 1020);
		manager.manualInput();
		assertEquals(Integer.valueOf(2), recaps.get(0).getItemGains().get(ItemID.YEW_LOGS));
		assertEquals(Integer.valueOf(3), recaps.get(1).getItemGains().get(ItemID.YEW_LOGS));
	}

	private void assertDelayedSkillRelevance(Skill skill)
	{
		ticks(10, config);
		gainItem(12);
		ticks(90, config);
		assertTrue(manager.relevantSkills().isEmpty());
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
		manager.statChanged(skill, 1050);
		assertTrue(manager.relevantSkills().contains(skill));
		manager.manualInput();
		assertEquals(Collections.singletonMap(skill, 50L), recaps.get(0).getXpGained());
		assertEquals(Collections.singletonMap(ItemID.YEW_LOGS, 12), recaps.get(0).getItemGains());
	}

	@Test
	public void sailingXpCanQualifyDelayedCandidateAndRetainsEarlierItems()
	{
		assertTrue(config.idleSailing());
		assertTrue(IdleSkill.enabledSkills(config).contains(Skill.SAILING));
		assertDelayedSkillRelevance(Skill.SAILING);
	}

	@Test
	public void slayerXpCanQualifyDelayedCandidateAndRetainsEarlierItems()
	{
		assertTrue(config.idleSlayer());
		assertTrue(IdleSkill.enabledSkills(config).contains(Skill.SLAYER));
		assertDelayedSkillRelevance(Skill.SLAYER);
	}

	@Test
	public void disabledSailingDoesNotQualifyCandidate()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleSailing() { return false; }
		};
		assertFalse(IdleSkill.enabledSkills(disabled).contains(Skill.SAILING));
		ticks(10, disabled);
		gainItem(12);
		manager.statChanged(Skill.SAILING, 1050);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void disabledSlayerDoesNotQualifyCandidate()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleSlayer() { return false; }
		};
		assertFalse(IdleSkill.enabledSkills(disabled).contains(Skill.SLAYER));
		ticks(10, disabled);
		gainItem(12);
		manager.statChanged(Skill.SLAYER, 1050);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void itemGainsAreDefensiveSnapshots()
	{
		Map<Integer, Integer> gains = new HashMap<>();
		gains.put(ItemID.LOGS, 12);
		AfkRecapSession recap = new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
			1, Instant.EPOCH, 600, Collections.emptyMap(), Collections.emptySet(), gains);
		gains.clear();
		assertEquals(Collections.singletonMap(ItemID.LOGS, 12), recap.getItemGains());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void itemGainsAreImmutable()
	{
		manager.focusChanged(false, config);
		gainItem(12);
		manager.focusChanged(true, config);
		recaps.get(0).getItemGains().put(ItemID.COINS, 1);
	}
}
