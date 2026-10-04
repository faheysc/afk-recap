package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class IdleCombatSlayerTest
{
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
		recaps::add, Collections::emptyMap);

	private AfkRecapConfig settings(boolean combat, boolean slayer)
	{
		return new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return combat; }
			@Override public boolean idleSlayer() { return slayer; }
		};
	}

	private void startIdle(AfkRecapConfig config)
	{
		manager.baseline(Map.of(Skill.ATTACK, 1000, Skill.HITPOINTS, 1000, Skill.SLAYER, 1000));
		manager.baselinePrayer(99);
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
	}

	private void qualifies(boolean combat, boolean slayer, Skill skill, boolean expected)
	{
		startIdle(settings(combat, slayer));
		manager.statChanged(skill, 1010);
		assertEquals(expected, manager.relevantSkills().contains(skill));
		manager.manualInput();
		assertEquals(expected ? 1 : 0, recaps.size());
	}

	@Test public void combatOnSlayerOffAttackQualifies() { qualifies(true, false, Skill.ATTACK, true); }
	@Test public void combatOffSlayerOnAttackDoesNotQualify() { qualifies(false, true, Skill.ATTACK, false); }
	@Test public void combatOffSlayerOnSlayerQualifies() { qualifies(false, true, Skill.SLAYER, true); }
	@Test public void combatOnSlayerOffSlayerDoesNotQualify() { qualifies(true, false, Skill.SLAYER, false); }
	@Test public void bothOnAttackQualifies() { qualifies(true, true, Skill.ATTACK, true); }
	@Test public void bothOnSlayerQualifies() { qualifies(true, true, Skill.SLAYER, true); }
	@Test public void bothOffAttackDoesNotQualify() { qualifies(false, false, Skill.ATTACK, false); }
	@Test public void bothOffSlayerDoesNotQualify() { qualifies(false, false, Skill.SLAYER, false); }

	private void combatMetrics()
	{
		manager.statChanged(Skill.ATTACK, 1040);
		manager.statChanged(Skill.HITPOINTS, 1013);
		manager.inventoryChanged(Map.of(ItemID.COINS, 100));
		Object npc = new Object();
		manager.npcDamage(npc, 10, true, false);
		manager.npcDeath(npc, "Cow");
		manager.prayerChanged(90);
		manager.playerHitsplat(new Hitsplat()
		{
			@Override public int getHitsplatType() { return HitsplatID.DAMAGE_OTHER; }
			@Override public int getAmount() { return 12; }
			@Override public int getDisappearsOnGameCycle() { return 0; }
		});
	}

	private void assertMetrics(AfkRecapSession recap)
	{
		assertEquals(Map.of(Skill.ATTACK, 40L, Skill.HITPOINTS, 13L, Skill.SLAYER, 20L), recap.getXpGained());
		assertEquals(Map.of(ItemID.COINS, 100), recap.getItemGains());
		assertEquals(Map.of("Cow", 1), recap.getNpcKills());
		assertEquals(9, recap.getPrayerUsed());
		assertEquals(12, recap.getDamageTaken());
	}

	@Test public void delayedSlayerRetainsEarlierCombatMetrics()
	{
		AfkRecapConfig config = settings(false, true);
		startIdle(config); combatMetrics();
		for (int i = 0; i < 100; i++) { manager.gameTick(config); }
		assertTrue(recaps.isEmpty());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(Skill.SLAYER, 1020);
		manager.manualInput();
		assertEquals(Collections.singleton(Skill.SLAYER), recaps.get(0).getRelevantIdleSkills());
		assertMetrics(recaps.get(0));
	}

	@Test public void disabledSlayerXpStillRecordedInCombatRecap()
	{
		startIdle(settings(true, false)); combatMetrics();
		manager.statChanged(Skill.SLAYER, 1020); manager.manualInput();
		assertFalse(recaps.get(0).getRelevantIdleSkills().contains(Skill.SLAYER));
		assertMetrics(recaps.get(0));
	}

	@Test public void focusIgnoresBothActivityToggles()
	{
		AfkRecapConfig config = settings(false, false);
		manager.baseline(Map.of(Skill.ATTACK, 1000, Skill.HITPOINTS, 1000, Skill.SLAYER, 1000));
		manager.baselinePrayer(99); manager.focusChanged(false, config);
		combatMetrics(); manager.statChanged(Skill.SLAYER, 1020); manager.focusChanged(true, config);
		assertEquals(AfkSessionTrigger.FOCUS, recaps.get(0).getTrigger());
		assertTrue(recaps.get(0).getRelevantIdleSkills().isEmpty());
		assertMetrics(recaps.get(0));
	}

	@Test public void freshDefaultsEnableBoth()
	{
		AfkRecapConfig defaults = new AfkRecapConfig() {};
		assertTrue(defaults.idleCombat()); assertTrue(defaults.idleSlayer());
	}

	@Test public void absentSlayerDefaultInheritsLegacyOptOut()
	{
		AfkRecapConfig legacy = new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return false; }
		};
		assertFalse(legacy.idleSlayer());
	}

	@Test public void savedSlayerPreferenceIsIndependentOfLegacyCombat()
	{
		assertTrue(settings(false, true).idleSlayer());
		assertFalse(settings(true, false).idleSlayer());
	}

	@Test public void slayerSynchronizationAndXpDecreaseDoNotQualify()
	{
		startIdle(settings(false, true));
		manager.statChanged(Skill.SLAYER, 1000); manager.statChanged(Skill.SLAYER, 900);
		assertTrue(manager.relevantSkills().isEmpty()); manager.manualInput(); assertTrue(recaps.isEmpty());
	}
}
