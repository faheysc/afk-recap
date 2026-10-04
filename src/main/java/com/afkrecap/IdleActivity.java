package com.afkrecap;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Skill;

// Each category declares its toggle and all XP skills that can make a candidate relevant.
enum IdleActivity
{
	FISHING(AfkRecapConfig::idleFishing, Skill.FISHING),
	MINING(AfkRecapConfig::idleMining, Skill.MINING),
	WOODCUTTING(AfkRecapConfig::idleWoodcutting, Skill.WOODCUTTING),
	SAILING(AfkRecapConfig::idleSailing, Skill.SAILING),
	COMBAT(AfkRecapConfig::idleCombat, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER);

	private final Set<Skill> skills;
	private final Predicate<AfkRecapConfig> enabled;

	IdleActivity(Predicate<AfkRecapConfig> enabled, Skill... skills)
	{
		this.skills = EnumSet.copyOf(Arrays.asList(skills));
		this.enabled = enabled;
	}

	static Set<Skill> enabledSkills(AfkRecapConfig config)
	{
		Set<Skill> skills = EnumSet.noneOf(Skill.class);
		for (IdleActivity activity : values())
		{
			if (activity.enabled.test(config))
			{
				skills.addAll(activity.skills);
			}
		}
		return skills;
	}
}
