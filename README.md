# AFK Recap

A RuneLite plugin that summarizes XP and inventory items gained during focus-loss
or manual-input inactivity sessions.

- Focus sessions start when RuneLite loses focus and finish when focus returns.
- Idle candidates start after the configured number of game ticks without a mouse
  press or key press. Mouse movement does not reset inactivity.
- Fishing, Mining, Woodcutting, Sailing, Combat, and Slayer XP can make an idle candidate
  relevant. Each activity has an enabled-by-default toggle. Qualifying XP can arrive
  at any point during the candidate; earlier inventory gains remain included.
  Combat qualifies through Attack, Strength, Defence, Ranged, Magic, or Hitpoints XP.
  Slayer qualifies only through Slayer XP, using an independent toggle. Both default
  to enabled. Disabling either affects only idle relevance, not collection: all XP,
  items, kills, Prayer usage, and damage collected before relevance remain available
  if an enabled activity later qualifies the candidate. Focus sessions are unaffected.
- Completed relevant recaps appear in a transient overlay and an optional sidebar
  with bounded, in-memory history and a Clear History button.
- Inventory gains count positive quantity changes across all inventory slots.
  Items present at session start are excluded. Item names and approximate values
  use RuneLite ItemManager data; unpriced items still show their quantities.

- Combat recaps can include attributed NPC kills, cumulative Prayer points used,
  and cumulative local-player damage taken. Restoration/healing does not subtract
  from usage or damage totals. These metrics do not independently qualify idle candidates.
- Confirmed, resource-specific catch/chop messages reconcile with inventory gains
  for open fish barrels, fish sack barrels, log baskets, and forestry baskets.
  Supported direct-to-storage gains use the same item rows as inventory gains.
- Login, logout, reconnects, and world hops discard unfinished sessions and reset
  transient baselines; login XP synchronization is not counted as a gain.

## Tracking limitations

- Fish barrel and fish sack barrel tracking has not yet been manually verified.
  Hidden log-basket gains have been manually verified.
- Sailing XP is supported, but crewmate salvage item-type/quantity tracking is deferred.
  Ship cargo additions do not contribute to item totals.
- Kill attribution uses local-player-owned damage hitsplats and excludes NPCs with
  observed damage from other players during the session. Shared kills and kills
  without sufficient visible hitsplat evidence may be conservatively undercounted.
- Hidden resources require a supported, explicit acquisition message and known item
  type. Ambiguous messages, unknown resources, cormorant catches, and other unsupported
  special cases are omitted. Charged infernal-tool hidden gains remain excluded.
  Felling ration/XP-only messages do not create logs.
- Reconciliation matches inventory additions in the same or adjacent game tick.
  Transfer suppression covers nearby Fill/Empty/deposit/withdrawal events; ambiguous
  overlap with gathering can undercount, and unusually delayed events are not guaranteed
  to reconcile accurately. Check-dialog contents never create acquisitions.
- General inventory tracking counts positive deltas, not provenance. Outside the
  recognized resource-container transfer paths, withdrawals or other transfers into
  inventory can appear as gains.
- History is memory-only, capped at 100 recaps (20 by default); no permanent storage,
  bank/ground-item loot collection, or generic cargo tracking is implemented.

The plugin only observes game events and user input; it does not send game inputs,
change menu actions, or perform gameplay automation.

## Development

```sh
JAVA_HOME=/var/home/sfahey/.jdks/temurin-11.0.32.1 ./gradlew test build
JAVA_HOME=/var/home/sfahey/.jdks/temurin-11.0.32.1 ./gradlew run
```

For development-client login, follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions. Plugin behavior must be verified manually in RuneLite.

The legacy `away-recap` config group is intentionally retained so the AFK Recap
rename preserves existing saved settings. Recap history is not stored in config.

The Combat toggle retains the legacy `idleSlayer` config key so existing saved
preferences remain intact. The new Slayer toggle uses `idleSlayerActivity`. When
RuneLite initializes plugin/profile defaults, an absent Slayer setting inherits the
existing Combat setting once (including saved opt-outs). Explicit Slayer settings
are never overwritten; after initialization the toggles are independent. Fresh
installations enable both.
