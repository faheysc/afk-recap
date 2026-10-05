package com.afkrecap;

import net.runelite.api.gameval.ItemID;

/** RuneLite ClueScrollPlugin's parameter identification covers step variants; see META-INF/NOTICE. */
final class ClueDropItems
{
	private ClueDropItems()
	{
	}

	static boolean isClue(int id, int clueParameter)
	{
		return id == ItemID.TRAIL_CLUE_BEGINNER || id == ItemID.TRAIL_CLUE_MASTER || clueParameter != -1;
	}
}
