# AFK Recap

AFK Recap tracks what happens while you are AFK or away from RuneLite and
summarizes your session when you return. It only observes game events and user
input; it does not send game inputs or automate gameplay.

## Features

- **Focus-loss sessions:** start when RuneLite loses focus and finish when focus returns.
- **Configurable idle sessions:** start after the configured number of game ticks
  without a mouse press or key press and finish on the next press. Mouse movement
  does not reset inactivity.
- **Independent activity toggles:** Fishing, Mining, Woodcutting, Sailing, Combat,
  and Slayer XP can qualify an idle candidate. All are enabled by default for new
  installations. Combat uses Attack, Strength, Defence, Ranged, Magic, or Hitpoints
  XP; Slayer uses only Slayer XP.
- **XP and item/resource gains:** collect gains throughout the candidate, including
  before an enabled activity makes it relevant. Activity toggles only affect idle
  relevance; they do not filter collected data or affect focus sessions.
- **Hidden resource tracking:** confirmed catch/chop messages reconcile with
  inventory gains for open fish barrels, fish sack barrels, log baskets, and
  forestry baskets. Supported direct-to-log-basket gains, including with felling
  axes, appear in the same item rows as normal inventory gains.
- **Combat metrics:** attributed NPC kill counts, cumulative Prayer points used,
  and cumulative damage taken by the local player. Restoration and healing do not
  reduce these totals. These metrics alone do not qualify an idle candidate.
- **Recap presentation:** a transient overlay and an optional recent-recap side
  panel with Clear History. History defaults to 20 entries, is capped at 100, and
  is not saved across client restarts. Items show quantities and approximate values
  where available.

Logout, disconnects, and world hops finish qualifying sessions and immediately
save them in recent history without opening an overlay on the login screen.
Irrelevant idle candidates and empty focus sessions are discarded. Fresh login
baselines prevent synchronization gains or data carrying over between worlds.

## Known limitations

- Fish-barrel and fish-sack-barrel behavior is covered by automated tests but has
  not been manually verified. Hidden log-basket gains have been manually verified.
- Sailing XP is tracked, but crewmate salvage item types and quantities are not.
  Ship cargo additions do not contribute to item totals.
- Shared or uncertain NPC kills may be conservatively undercounted when visible
  damage evidence is insufficient or includes another player's damage.
- Ambiguous hidden-resource messages, unknown item types, cormorant catches, and
  other unsupported special cases are omitted rather than guessed. Charged
  infernal-tool hidden gains are excluded; felling ration/XP-only messages do not
  create logs.
- Inventory gains count positive quantity changes. Recognized container transfers
  are suppressed, and Check-dialog contents never create gains, but other transfers
  into inventory can appear as gains. Delayed events or overlapping transfers and
  gathering may not reconcile accurately.

Licensed under the [BSD 2-Clause License](LICENSE).
