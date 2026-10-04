package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class IdleActivityTest
{
	private final Set<Skill> combatSkills = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER);
	private final AfkRecapConfig combatOnly = new AfkRecapConfig()
	{
		@Override public boolean idleFishing() { return false; }
		@Override public boolean idleMining() { return false; }
		@Override public boolean idleWoodcutting() { return false; }
		@Override public boolean idleSailing() { return false; }
	};

	private void ticks(AfkSessionManager manager, AfkRecapConfig config)
	{
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
	}

	@Test
	public void combatCategoryIsEnabledByDefaultAndExcludesPrayer()
	{
		assertTrue(combatOnly.idleCombat());
		assertEquals(combatSkills, IdleActivity.enabledSkills(combatOnly));
		assertFalse(IdleActivity.enabledSkills(combatOnly).contains(Skill.PRAYER));
	}

	@Test
	public void nonCombatSkillsDoNotQualifyCombat()
	{
		for (Skill skill : Skill.values())
		{
			if (combatSkills.contains(skill))
			{
				continue;
			}
			AfkSessionManager manager = new AfkSessionManager();
			manager.baseline(Collections.singletonMap(skill, 1000));
			ticks(manager, combatOnly);
			manager.statChanged(skill, 1050);
			assertTrue(skill + " must not qualify Combat", manager.relevantSkills().isEmpty());
			manager.manualInput();
			assertNull(manager.trigger());
		}
	}

	@Test
	public void otherActivitiesStillQualifyWithCombatDisabled()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return false; }
		};
		for (Skill skill : EnumSet.of(Skill.FISHING, Skill.MINING, Skill.WOODCUTTING, Skill.SAILING))
		{
			AfkSessionManager manager = new AfkSessionManager();
			manager.baseline(Collections.singletonMap(skill, 1000));
			ticks(manager, disabled);
			manager.statChanged(skill, 1050);
			assertEquals(Collections.singleton(skill), manager.relevantSkills());
		}
	}

	@Test
	public void offTaskRecapIncludesAttackHitpointsAndEarlierItemGains()
	{
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
			recaps::add, Collections::emptyMap);
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		xp.put(Skill.ATTACK, 1000);
		xp.put(Skill.HITPOINTS, 1000);
		xp.put(Skill.SLAYER, 1000);
		manager.baseline(xp);
		ticks(manager, combatOnly);
		manager.inventoryChanged(Collections.singletonMap(ItemID.COINS, 100));
		manager.statChanged(Skill.ATTACK, 1040);
		manager.statChanged(Skill.HITPOINTS, 1013);
		manager.manualInput();
		assertEquals(1, recaps.size());
		AfkRecapSession recap = recaps.get(0);
		assertEquals(Long.valueOf(40), recap.getXpGained().get(Skill.ATTACK));
		assertEquals(Long.valueOf(13), recap.getXpGained().get(Skill.HITPOINTS));
		assertFalse(recap.getXpGained().containsKey(Skill.SLAYER));
		assertEquals(Collections.singletonMap(ItemID.COINS, 100), recap.getItemGains());
	}

	@Test
	public void focusStillCollectsCombatXpWithCombatToggleDisabled()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return false; }
		};
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH, recaps::add);
		manager.baseline(Collections.singletonMap(Skill.ATTACK, 1000));
		manager.focusChanged(false, disabled);
		manager.statChanged(Skill.ATTACK, 1050);
		manager.focusChanged(true, disabled);
		assertEquals(1, recaps.size());
		assertEquals(AfkSessionTrigger.FOCUS, recaps.get(0).getTrigger());
		assertEquals(Collections.singletonMap(Skill.ATTACK, 50L), recaps.get(0).getXpGained());
	}
}
