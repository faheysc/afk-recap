package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class IdleCombatSkillTest
{
	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> combatSkills()
	{
		return Arrays.asList(new Object[][]{
			{Skill.ATTACK}, {Skill.STRENGTH}, {Skill.DEFENCE}, {Skill.RANGED},
			{Skill.MAGIC}, {Skill.HITPOINTS}, {Skill.SLAYER}
		});
	}

	private final Skill skill;

	public IdleCombatSkillTest(Skill skill)
	{
		this.skill = skill;
	}

	@Test
	public void positiveXpQualifiesWithoutSlayerTaskOrRecentXp()
	{
		AfkRecapConfig config = new AfkRecapConfig() {};
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH, recaps::add);
		manager.baseline(Collections.singletonMap(skill, 1000));
		for (int i = 0; i < 100; i++)
		{
			manager.gameTick(config);
		}
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(skill, 1050);
		assertEquals(Collections.singleton(skill), manager.relevantSkills());
		manager.manualInput();
		assertEquals(1, recaps.size());
		assertEquals(Collections.singletonMap(skill, 50L), recaps.get(0).getXpGained());
		assertNull(manager.trigger());
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
	}

	@Test
	public void disabledCombatDoesNotQualifyViaThisSkill()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return false; }
		};
		List<AfkRecapSession> recaps = new ArrayList<>();
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH, recaps::add);
		manager.baseline(Collections.singletonMap(skill, 1000));
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(disabled);
		}
		manager.statChanged(skill, 1050);
		assertTrue(manager.relevantSkills().isEmpty());
		manager.manualInput();
		assertTrue(recaps.isEmpty());
		assertNull(manager.trigger());
	}
}
