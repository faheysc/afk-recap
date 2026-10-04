package com.awayrecap;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Skill;

// Add future idle skills here alongside their config toggle.
enum IdleSkill
{
	FISHING(Skill.FISHING, AwayRecapConfig::idleFishing),
	MINING(Skill.MINING, AwayRecapConfig::idleMining),
	WOODCUTTING(Skill.WOODCUTTING, AwayRecapConfig::idleWoodcutting);

	final Skill skill;
	private final Predicate<AwayRecapConfig> enabled;

	IdleSkill(Skill skill, Predicate<AwayRecapConfig> enabled)
	{
		this.skill = skill;
		this.enabled = enabled;
	}

	static Set<Skill> enabledSkills(AwayRecapConfig config)
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
