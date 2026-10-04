package com.afkrecap;

import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;

/** Explicit HP-damage types from the installed API; resource drains are not HP damage. */
final class PlayerDamage
{
	private PlayerDamage()
	{
	}

	static boolean isDamage(Hitsplat hit)
	{
		if (hit == null || hit.getAmount() <= 0)
		{
			return false;
		}
		switch (hit.getHitsplatType())
		{
			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_ME_CYAN:
			case HitsplatID.DAMAGE_OTHER_CYAN:
			case HitsplatID.DAMAGE_ME_ORANGE:
			case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_ME_YELLOW:
			case HitsplatID.DAMAGE_OTHER_YELLOW:
			case HitsplatID.DAMAGE_ME_WHITE:
			case HitsplatID.DAMAGE_OTHER_WHITE:
			case HitsplatID.DAMAGE_MAX_ME:
			case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE:
			case HitsplatID.DAMAGE_MAX_ME_YELLOW:
			case HitsplatID.DAMAGE_MAX_ME_WHITE:
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
			case HitsplatID.BLEED:
			case HitsplatID.BURN:
				return true;
			default:
				return false;
		}
	}
}
