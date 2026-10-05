package com.afkrecap;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Skill;

// XP-driven categories declare skills; signal-driven categories qualify explicitly.
enum IdleActivity
{
	FISHING(AfkRecapConfig::idleFishing, Skill.FISHING),
	MINING(AfkRecapConfig::idleMining, Skill.MINING),
	WOODCUTTING(AfkRecapConfig::idleWoodcutting, Skill.WOODCUTTING),
	SAILING_SALVAGING(AfkRecapConfig::idleSailingSalvaging, false, Skill.SAILING),
	SAILING_SORTING(AfkRecapConfig::idleSailingSorting, false, Skill.SAILING),
	COMBAT(AfkRecapConfig::idleCombat, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS),
	SLAYER(AfkRecapConfig::idleSlayer, Skill.SLAYER);

	private final Set<Skill> skills;
	private final boolean xpDriven;
	private final Predicate<AfkRecapConfig> enabled;

	IdleActivity(Predicate<AfkRecapConfig> enabled, Skill... skills)
	{
		this(enabled, true, skills);
	}

	IdleActivity(Predicate<AfkRecapConfig> enabled, boolean xpDriven, Skill... skills)
	{
		this.xpDriven = xpDriven;
		this.skills = EnumSet.noneOf(Skill.class);
		this.skills.addAll(Arrays.asList(skills));
		this.enabled = enabled;
	}

	static Set<IdleActivity> enabledActivities(AfkRecapConfig config)
	{
		Set<IdleActivity> activities = EnumSet.noneOf(IdleActivity.class);
		for (IdleActivity activity : values())
		{
			if (activity.enabled.test(config))
			{
				activities.add(activity);
			}
		}
		return activities;
	}

	void addRelevantSkills(Set<Skill> relevantSkills)
	{
		relevantSkills.addAll(skills);
	}

	static Set<Skill> enabledSkills(AfkRecapConfig config)
	{
		Set<Skill> skills = EnumSet.noneOf(Skill.class);
		for (IdleActivity activity : values())
		{
			if (activity.xpDriven && activity.enabled.test(config))
			{
				skills.addAll(activity.skills);
			}
		}
		return skills;
	}
}
