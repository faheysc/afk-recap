package com.afkrecap;

import net.runelite.api.gameval.ItemID;

/** RuneLite's clue-scroll item parameter covers step variants; never use name/ID ranges. */
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
