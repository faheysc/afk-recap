# AFK Recap

A RuneLite plugin that summarizes XP and inventory items gained during focus-loss
or manual-input inactivity sessions.

- Focus sessions start when RuneLite loses focus and finish when focus returns.
- Idle candidates start after the configured number of game ticks without a mouse
  press or key press. Mouse movement does not reset inactivity.
- Fishing, Mining, Woodcutting, Sailing, and Combat XP can make an idle candidate
  relevant. Each activity has an enabled-by-default toggle. Qualifying XP can arrive
  at any point during the candidate; earlier inventory gains remain included.
  Combat qualifies through Attack, Strength, Defence, Ranged, Magic, Hitpoints, or
  Slayer XP; being on a Slayer task is not required.
- Completed relevant recaps appear in a transient overlay and an optional sidebar
  with bounded, in-memory history and a Clear History button.
- Inventory gains count positive quantity changes across all inventory slots.
  Items present at session start are excluded. Item names and approximate values
  use RuneLite ItemManager data; unpriced items still show their quantities.

No bank, ground-item, or NPC loot tracking, chat parsing, or permanent storage is
implemented. XP accounting, overlay expiry, and session re-arming remain shared
across supported activities.

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
preferences carry over to the expanded activity category.
