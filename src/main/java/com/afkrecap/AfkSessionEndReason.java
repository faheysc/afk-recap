package com.afkrecap;

import net.runelite.api.GameState;

/** Completed-session metadata; only classify causes the API actually identifies. */
public enum AfkSessionEndReason
{
	MANUAL_INPUT,
	FOCUS_RETURN,
	LOGOUT,
	DISCONNECT,
	WORLD_HOP,
	LOGOUT_OR_DISCONNECT,
	UNSPECIFIED;

	static AfkSessionEndReason forGameState(GameState state)
	{
		if (state == null)
		{
			return null;
		}
		switch (state)
		{
			case HOPPING: return WORLD_HOP;
			case CONNECTION_LOST: return DISCONNECT;
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
			case LOGGING_IN:
				return LOGOUT_OR_DISCONNECT;
			default: return null;
		}
	}

	boolean isGameExit()
	{
		return this == LOGOUT || this == DISCONNECT || this == WORLD_HOP || this == LOGOUT_OR_DISCONNECT;
	}
}
