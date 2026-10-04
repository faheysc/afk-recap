package com.afkrecap;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;

// All calls run on the client thread. No rendering or persistent storage belongs here.
@Slf4j
final class AfkSessionManager
{
	private final LongSupplier nanoTime;
	private final Supplier<Instant> timestamp;
	private final Consumer<AfkRecapSession> recapConsumer;
	private final Supplier<Map<Integer, Integer>> inventorySnapshot;
	private final Map<Skill, Integer> experience = new EnumMap<>(Skill.class);
	private long gameTicks;
	private int inactiveTicks;
	private Session session;
	private Integer prayerPoints;
	private boolean ready = true;

	AfkSessionManager()
	{
		this(System::nanoTime);
	}

	AfkSessionManager(LongSupplier nanoTime)
	{
		this(nanoTime, Instant::now, recap -> {});
	}

	AfkSessionManager(LongSupplier nanoTime, Supplier<Instant> timestamp,
		Consumer<AfkRecapSession> recapConsumer)
	{
		this(nanoTime, timestamp, recapConsumer, () -> null);
	}

	AfkSessionManager(LongSupplier nanoTime, Supplier<Instant> timestamp,
		Consumer<AfkRecapSession> recapConsumer, Supplier<Map<Integer, Integer>> inventorySnapshot)
	{
		this.inventorySnapshot = inventorySnapshot;
		this.nanoTime = nanoTime;
		this.timestamp = timestamp;
		this.recapConsumer = recapConsumer;
	}

	// Login, loading, hopping and reconnecting discard unfinished data without publishing it.
	void suspend()
	{
		ready = false;
		session = null;
		inactiveTicks = 0;
		experience.clear();
		prayerPoints = null;
	}

	void gameStateChanged(GameState state)
	{
		// Even LOGGED_IN waits for the first stable game tick before accepting activity.
		suspend();
		log.debug("Recap collection suspended for game state {}", state);
	}

	void loggedIn(Map<Skill, Integer> xp, int prayer)
	{
		suspend();
		baseline(xp);
		baselinePrayer(prayer);
		ready = true;
	}

	boolean isReady()
	{
		return ready;
	}

	void playerHitsplat(Hitsplat hit)
	{
		if (session != null && PlayerDamage.isDamage(hit))
		{
			session.damageTaken += hit.getAmount();
		}
	}

	void baseline(Map<Skill, Integer> xp)
	{
		experience.clear();
		experience.putAll(xp);
	}

	void gameTick(AfkRecapConfig config)
	{
		if (!ready)
		{
			return;
		}
		if (session != null)
		{
			session.inventory.gameTick();
		}
		gameTicks++;
		inactiveTicks = Math.min(100, inactiveTicks + 1);
		int threshold = Math.max(1, Math.min(100, config.idleGameTicks()));
		if (session == null && config.startOnIdle() && inactiveTicks >= threshold)
		{
			log.debug("Idle threshold reached: {} inactive game ticks (~{} ms)", inactiveTicks, inactiveTicks * 600L);
			start(AfkSessionTrigger.IDLE, IdleActivity.enabledSkills(config));
		}
	}

	void focusChanged(boolean focused, AfkRecapConfig config)
	{
		if (!ready)
		{
			return;
		}
		if (focused)
		{
			if (trigger() == AfkSessionTrigger.FOCUS)
			{
				end("focus regained");
				inactiveTicks = 0;
			}
		}
		else if (config.startOnFocusLoss() && session == null)
		{
			start(AfkSessionTrigger.FOCUS, EnumSet.noneOf(Skill.class));
		}
	}

	void manualInput()
	{
		if (!ready)
		{
			return;
		}
		if (trigger() == AfkSessionTrigger.IDLE)
		{
			end("manual input");
		}
		// This same input begins the next inactivity period, even when it ends a session.
		inactiveTicks = 0;
	}

	void statChanged(Skill skill, int xp)
	{
		if (!ready)
		{
			return;
		}
		Integer previous = experience.put(skill, xp);
		// A missing baseline is synchronization, not a gain. Decreases rebaseline only.
		if (session == null || previous == null || xp <= previous)
		{
			return;
		}
		long gained = (long) xp - previous;
		session.xpGained.merge(skill, gained, Long::sum);
		if (trigger() == AfkSessionTrigger.IDLE && session.enabledSkills.contains(skill)
			&& session.relevantSkills.add(skill))
		{
			log.debug("IDLE session enabled skill activity detected: {} (+{} XP)", skill, gained);
		}
	}

	void baselinePrayer(int points)
	{
		prayerPoints = points;
	}

	void prayerChanged(int points)
	{
		if (!ready)
		{
			return;
		}
		if (session != null && prayerPoints != null && points < prayerPoints)
		{
			session.prayerUsed += (long) prayerPoints - points;
		}
		prayerPoints = points;
	}

	void npcDamage(Object npc, int amount, boolean mine, boolean others)
	{
		if (session != null)
		{
			session.kills.damage(npc, amount, mine, others);
		}
	}

	void npcDeath(Object npc, String name)
	{
		if (session != null)
		{
			session.kills.death(npc, name);
		}
	}

	void npcDespawned(Object npc)
	{
		if (session != null)
		{
			session.kills.despawn(npc);
		}
	}

	void inventoryChanged(Map<Integer, Integer> inventory)
	{
		if (session != null)
		{
			session.inventory.inventoryChanged(inventory);
		}
	}

	void acquisitionMessage(String message, boolean fishStorage, boolean logStorage)
	{
		if (session != null)
		{
			session.inventory.message(message, fishStorage, logStorage);
		}
	}

	void resourceTransfer(ResourceAcquisitionMessages.Family family)
	{
		if (session != null)
		{
			session.inventory.transfer(family);
		}
	}

	void reset(String reason)
	{
		if (session != null)
		{
			end(reason);
		}
		inactiveTicks = 0;
		experience.clear();
		prayerPoints = null;
	}

	AfkSessionTrigger trigger()
	{
		return session == null ? null : session.trigger;
	}

	Set<Skill> relevantSkills()
	{
		return session == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(session.relevantSkills);
	}

	private void start(AfkSessionTrigger trigger, Set<Skill> enabledSkills)
	{
		session = new Session(trigger, gameTicks, nanoTime.getAsLong(), timestamp.get(), enabledSkills, inventorySnapshot.get());
		log.debug("Away session started: trigger={}, gameTick={}, enabledSkills={}", trigger, gameTicks, enabledSkills);
	}

	private void end(String reason)
	{
		Session ended = session;
		session = null;
		if (ended.trigger == AfkSessionTrigger.IDLE)
		{
			// Re-arm every idle-session ending centrally, before diagnostics run.
			inactiveTicks = 0;
		}
		Instant endTimestamp = timestamp.get();
		long ticks = gameTicks - ended.startTick;
		long elapsedMillis = (nanoTime.getAsLong() - ended.startNanos) / 1_000_000L;
		String outcome = ended.trigger == AfkSessionTrigger.IDLE
			? (ended.relevantSkills.isEmpty() ? "discarded" : "relevant") : "ended";
		log.debug("Away session ended: trigger={}, reason={}, outcome={}, skills={}, elapsedGameTicks={}, elapsedMs={}",
			ended.trigger, reason, outcome, ended.relevantSkills, ticks, elapsedMillis);
		Map<Integer, Integer> itemGains = ended.inventory.gains();
		if ((!ended.xpGained.isEmpty() || !itemGains.isEmpty() || !ended.kills.totals().isEmpty()
			|| ended.prayerUsed > 0 || ended.damageTaken > 0)
			&& (ended.trigger == AfkSessionTrigger.FOCUS || !ended.relevantSkills.isEmpty()))
		{
			AfkRecapSession recap = new AfkRecapSession(ended.trigger, ended.startTick,
				ended.startTimestamp, gameTicks, endTimestamp, elapsedMillis,
				ended.xpGained, ended.relevantSkills, itemGains, ended.kills.totals(), ended.prayerUsed, ended.damageTaken);
			logRecap(recap);
			// Hand off an immutable snapshot; no session history or permanent storage.
			recapConsumer.accept(recap);
		}
	}

	private void logRecap(AfkRecapSession recap)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		StringBuilder message = new StringBuilder("AFK Recap\nTrigger: ")
			.append(recap.getTrigger())
			.append("\nDuration: ").append(recap.getElapsedGameTicks()).append(" ticks / ")
			.append(String.format(Locale.ROOT, "%.1f", recap.getElapsedMillis() / 1000.0))
			.append(" seconds");
		if (!recap.getXpGained().isEmpty())
		{
			message.append("\nXP gained:");
		}
		recap.getXpGained().forEach((skill, gained) ->
			message.append("\n- ").append(skill.getName()).append(": ").append(gained));
		if (!recap.getItemGains().isEmpty())
		{
			message.append("\nItems gained:");
			recap.getItemGains().forEach((id, quantity) ->
				message.append("\n- Item ").append(id).append(": ").append(quantity));
		}
		log.debug("{}", message);
	}

	private static final class Session
	{
		private final AfkSessionTrigger trigger;
		private final long startTick;
		private final long startNanos;
		private final Instant startTimestamp;
		private final Map<Skill, Long> xpGained = new EnumMap<>(Skill.class);
		private final Set<Skill> enabledSkills;
		private final ResourceAcquisitionTracker inventory;
		private final NpcKillTracker kills = new NpcKillTracker();
		private long prayerUsed;
		private long damageTaken;
		private final EnumSet<Skill> relevantSkills = EnumSet.noneOf(Skill.class);

		private Session(AfkSessionTrigger trigger, long startTick, long startNanos,
			Instant startTimestamp, Set<Skill> enabledSkills, Map<Integer, Integer> inventoryBaseline)
		{
			this.trigger = trigger;
			this.startTick = startTick;
			this.startNanos = startNanos;
			this.startTimestamp = startTimestamp;
			this.inventory = new ResourceAcquisitionTracker(inventoryBaseline);
			this.enabledSkills = EnumSet.noneOf(Skill.class);
			this.enabledSkills.addAll(enabledSkills);
		}
	}
}
