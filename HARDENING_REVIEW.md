# AFK Recap hardening review

Historical initial-release review. Its event descriptions and test counts describe
the earlier checkpoint, not the current update candidate. See
[RELEASE_CANDIDATE_AUDIT.md](RELEASE_CANDIDATE_AUDIT.md) for the update audit.

Review of the committed checkpoint and low-risk changes, 2026-10-04.
Packaging/readme readiness notes were updated during initial submission preparation.
This is a source/API review with automated tests, not a gameplay profiler or a
Plugin Hub approval. No new gameplay features or commits were introduced.

## Event and rendering paths

| Path | Expected frequency | Work and outcome |
| --- | --- | --- |
| ClientTick | None | No handler registered. |
| GameTick | Once per server tick, about 600 ms | Idle counter/config check; active-session reconciliation across two batches. No scene/widget/metadata scans. Login initializes skill/Prayer baselines once on the first stable tick. Removed per-tick idle debug logging. |
| StatChanged | Each XP/current-level change, potentially several per tick | EnumMap baseline update; active-session XP and Prayer accumulation. Baselines must still update outside sessions. Duplicate XP/Prayer values add zero. |
| HitsplatApplied | Each actor hitsplat in the loaded scene | Now exits without an active session. Constant-time actor/type checks and identity-map evidence updates; local-player damage uses explicit damage types. No actor collection scan. |
| ActorDeath | Each actor death | Now exits without an active session; only NPC deaths considered. Identity evidence and dead flag avoid counting duplicate deaths. |
| NpcDespawned | Each NPC leaving the scene | Now exits without an active session; removes NPC identity evidence, including dead tombstones. |
| ItemContainerChanged | Each container update | Now avoids snapshot allocation without an active session; only INV snapshots are processed. At most 28 inventory slots; duplicate snapshots add zero. Equipment is read only when checking potential acquisitions/transfers. |
| ChatMessage | Every chat line | Now exits without an active session, ignores unrelated channels/null messages before stripping tags, and skips inventory/equipment scans for unrelated text. Cheap prefix/exact-bonus prefilter does not credit anything itself. Exact resource parser remains authoritative. Unrelated accepted-channel text still invalidates bonus context. Potential acquisitions scan only INV/WORN, not the scene or widget trees. |
| MenuOptionClicked | User menu actions | Now exits without an active session and handles absent option/action safely. Reads target/selected widget only; relevant transfers may inspect INV/WORN. No menu mutation or game action. |
| FocusChanged | Window focus transitions | Logged-in/readiness guards; starts/ends focus sessions. No repeated polling. |
| GameStateChanged | Login/loading/hop/reconnect transitions | Discards the active candidate/session and resets transient baselines. One-time XP/Prayer snapshots. |
| ConfigChanged | User setting changes | Only the legacy plugin config group is handled; history limit/visibility changes refresh UI. No renames. |
| Mouse pressed / key pressed | User presses and key auto-repeat | Return/pass through the original input; queue inactivity reset on the client thread with a session-instance guard. Release/typed/movement do no work. Removed per-input debug logging. |
| Overlay render | Every frame while registered | Expiry check and standard OverlayPanel row components only. Names, prices, item quantities, XP and duration are prepared outside rendering. Small kill-count toString calls and component allocation remain; no metadata, parsing, I/O or scene work. |
| Side-panel refresh / Clear History | Recap completion, relevant config changes, user clear | EDT only; rebuilds up to the capped history size. Item display reads cached immutable presentations and performs no client lookup on EDT. No tick/frame refresh. |
| Startup/shutdown | Plugin enable/disable | Register/unregister input listeners and overlay; EDT navigation updates; lifecycle guards reject stale callbacks. Shutdown now suspends/discards session state directly instead of finalizing a recap that the disabled lifecycle would reject. No blocking waits. |

## Memory and lifetime

- History clamps the configured limit to 1–100, trims immediately, and defaults to
  20. History intentionally survives visibility toggles and plugin disable/enable
  within the same application lifetime; it is not a session-local structure.
- XP maps and relevance sets are bounded by Skill values. Inventory baselines are
  bounded by slots; item totals aggregate by item ID, not by event. Completed item
  maps are bounded by the game's finite item vocabulary, rather than by session
  length. No arbitrary truncation of legitimate item totals was added.
- NPC evidence uses object identity, not reusable NPC indices. It retains only
  damaged NPCs still in the loaded scene (including dead NPCs until despawn), not
  a list of historical kills. NpcDespawned removes entries; every session boundary
  discards the entire tracker. This bound depends on normal RuneLite despawn-event
  delivery. No arbitrary TTL/cap was added, which could forget other-player damage
  and falsely attribute a shared kill. Kill totals aggregate by finite NPC names.
- Resource pending evidence is exactly current/previous batches, each with maps
  keyed by the fixed supported resource vocabulary. Counts aggregate instead of
  retaining messages. GameTick rotates/commits even when no matching event arrives;
  session end flushes; discard/logout/hop/shutdown drops the tracker. Transfer state
  has at most two family entries; bonus state has at most four known messages and
  resets each tick/acquisition/transfer. No per-event buffer can grow without bound.
- The presentation cache has weak recap keys; values never reference those keys.
  History/overlay control live recap reachability. Garbage collection reclaims
  discarded presentations; this is not an unbounded strong item/price cache.
  ItemManager owns definition/price caching. Its installed getItemPrice reads the
  existing price map; the plugin performs no network request itself.
- Presentation preparation now returns its existing cached value if called again
  for the same immutable recap, avoiding repeat lookups/formatting.

## Correctness, threads and arithmetic

- Client event handling owns collection state. AWT input callbacks queue mutations
  through ClientThread with an instance guard; Swing widgets/navigation refresh on
  EDT. History, presentation cache and overlay controller synchronize cross-thread
  access. Immutable recap/display snapshots cross these boundaries.
- Inventory/chat reconciliation accepts either event order within the same or
  adjacent tick. Confirmed matched quantities are committed once; transfers remove
  uncertain inventory evidence without erasing confirmed acquisitions. Tests retain
  normal/direct-to-storage/Fill/Empty/session/world-boundary cases.
- No duplicate-processing fault requiring changed acquisition architecture was
  found. Repeated chat text can represent separate legitimate acquisitions and is
  not globally deduplicated. Hitsplat events do not have a stable plugin-level ID;
  deduplicating by amount/tick would incorrectly drop real repeated damage.
- Known residual order risks: inventory events delayed beyond the reconciliation
  window, transfers delayed beyond suppression, ambiguous transfer/gather overlap,
  and despawn before death/hitsplat evidence. These are documented limitations;
  changing windows/attribution would need separate manual testing.
- Login/hop readiness gates and XP baselines remain unchanged. Stat/Prayer updates
  outside sessions remain necessary. Null inventory resets its baseline; first
  available inventory after an unavailable baseline is synchronization. Null NPC,
  hit, metadata and container paths are guarded. Chat/menu null handling was added.
- Item/resource sums already saturate at Integer.MAX_VALUE; kill counts now do too
  rather than becoming negative. XP/Prayer/damage/time use long; practical gameplay
  cannot approach their limits. GP multiplication/sums use BigInteger. Overlay and
  session duration use monotonic nanoTime subtraction rather than wall-clock deltas.
- No INFO logging on hot paths. Continuous tick/input DEBUG logs were removed;
  remaining diagnostics concern lifecycle, session transitions, first relevance,
  completed recaps, or metadata failures.

## Plugin Hub readiness

Sources checked:
[Plugin Hub submission guide](https://github.com/runelite/plugin-hub/blob/master/README.md),
[Plugin Hub review policy](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review),
[rejected/restricted features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features),
and [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1).

- Runtime code is observational: no synthetic input, input consumption, menu-action
  modification, gameplay automation, NPC-target/player-focus display, combat
  prediction, prayer recommendations, or opponent/group statistics. Prayer/damage
  summarize the local player's completed session; kills summarize attributed NPCs.
- No reflection, JNI/JNA/Unsafe/native access, subprocess execution, runtime
  classloading/code downloads, serialization, custom network requests or gameplay
  filesystem I/O. No permissions or third-party-server toggles are requested.
  The test development entry point and Gradle JavaExec/shadowJar tasks are tooling,
  not production plugin behaviors. Only the normal Gradle wrapper binary is tracked;
  no plugin native binaries, obfuscated sources, service registration or build
  artifacts are tracked. The optional shadow JAR is not a Hub submission artifact.
- build.gradle targets Java 11 and follows the template dependency separation:
  RuneLite client compileOnly, Lombok compileOnly/annotationProcessor, JUnit and
  RuneLite client/jshell test-only. No transitive client libraries redeclared.
  latest.release and mavenLocal follow template conventions; local resolution is
  not proof of Hub CI resolution. No dependency change was warranted in this task.
- Package/project/group/main class are renamed. Metadata names the correct plugin
  class; displayName/build are populated; blank version is allowed by the Hub guide.
  Initial submission preparation sets author to faheysc and fills the requested
  tags and description, covering AFK/away sessions. PluginDescriptor matches that
  description. The owner supplied the metadata explicitly.
- Initial submission preparation adds a BSD 2-Clause LICENSE with copyright
  (c) 2026, faheysc. The original licensing/author submission blockers are resolved.
- away-recap and idleSlayer are deliberately retained for existing settings; no config
  group/key changed. No extra config permissions or persistence were introduced.
- README's obsolete claim that chat parsing was absent was corrected. Added the
  fish-barrel manual-verification gap, deferred salvage item tracking, conservative
  shared-kill attribution, unsupported/ambiguous hidden cases, transfer/window
  limitations, and observational-only behavior.

Assessment: metadata and licensing are now suitable for an initial Plugin Hub
submission review. Manual verification limitations remain documented in README.
RuneLite reviewers make the final rule-compliance judgment; this source review
does not constitute their approval.

## Hardening checkpoint validation

- `JAVA_HOME=<Java 11 installation> ./gradlew clean test build`
  passed: 192 tests, zero failures/errors/skips. All 188 checkpoint tests were
  retained; four new tests and one strengthened existing cache test were included.
- `git diff --check` passed. The build reported the existing development-entry-point
  unchecked warning and Gradle 9 deprecation notice; no compile/test failures.
- New targeted coverage: acquisition/bonus prefilter, unrelated-chat bonus-context
  invalidation, sustained unmatched acquisition evidence, and kill-count saturation.
  Existing presentation-cache test now also verifies repeated prepare avoids lookup.
- No architecture rewrite, arbitrary total truncation, cargo tracking or new feature.

## Initial submission preparation validation

- Java 11 `clean test build` passed: 207 tests, zero failures/errors/skips.
- `git diff --check` passed. Only metadata, license, documentation and the matching
  PluginDescriptor description changed; no gameplay behavior changed.
