package com.afkrecap;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Skill;

// Add future idle skills here alongside their config toggle.
enum IdleSkill
{
	FISHING(Skill.FISHING, AfkRecapConfig::idleFishing),
	MINING(Skill.MINING, AfkRecapConfig::idleMining),
	WOODCUTTING(Skill.WOODCUTTING, AfkRecapConfig::idleWoodcutting),
	SAILING(Skill.SAILING, AfkRecapConfig::idleSailing),
	SLAYER(Skill.SLAYER, AfkRecapConfig::idleSlayer);

	final Skill skill;
	private final Predicate<AfkRecapConfig> enabled;

	IdleSkill(Skill skill, Predicate<AfkRecapConfig> enabled)
	{
		this.skill = skill;
		this.enabled = enabled;
	}

	static Set<Skill> enabledSkills(AfkRecapConfig config)
	{
		Set<Skill> skills = EnumSet.noneOf(Skill.class);
		for (IdleSkill candidate : values())
		{
			if (candidate.enabled.test(config))
			{
				skills.add(candidate.skill);
			}
		}
		return skills;
	}
}
