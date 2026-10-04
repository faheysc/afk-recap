package com.awayrecap;

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

// All calls run on the client thread. No rendering or persistent storage belongs here.
@Slf4j
final class AwaySessionManager
{
	private final LongSupplier nanoTime;
	private final Supplier<Instant> timestamp;
	private final Consumer<AwayRecapSession> recapConsumer;
	private final Map<Skill, Integer> experience = new EnumMap<>(Skill.class);
	private long gameTicks;
	private int inactiveTicks;
	private Session session;

	AwaySessionManager()
	{
		this(System::nanoTime);
	}

	AwaySessionManager(LongSupplier nanoTime)
	{
		this(nanoTime, Instant::now, recap -> {});
	}

	AwaySessionManager(LongSupplier nanoTime, Supplier<Instant> timestamp,
		Consumer<AwayRecapSession> recapConsumer)
	{
		this.nanoTime = nanoTime;
		this.timestamp = timestamp;
		this.recapConsumer = recapConsumer;
	}

	void baseline(Map<Skill, Integer> xp)
	{
		experience.clear();
		experience.putAll(xp);
	}

	void gameTick(AwayRecapConfig config)
	{
		gameTicks++;
		inactiveTicks = Math.min(100, inactiveTicks + 1);
		int threshold = Math.max(1, Math.min(100, config.idleGameTicks()));
		if (session == null)
		{
			log.debug("Idle detection progress: gameTick={}, inactiveTicks={}, threshold={}, enabled={}",
				gameTicks, inactiveTicks, threshold, config.startOnIdle());
		}
		if (session == null && config.startOnIdle() && inactiveTicks >= threshold)
		{
			log.debug("Idle threshold reached: {} inactive game ticks (~{} ms)", inactiveTicks, inactiveTicks * 600L);
			start(AwaySessionTrigger.IDLE, IdleSkill.enabledSkills(config));
		}
	}

	void focusChanged(boolean focused, AwayRecapConfig config)
	{
		if (focused)
		{
			if (trigger() == AwaySessionTrigger.FOCUS)
			{
				end("focus regained");
				inactiveTicks = 0;
			}
		}
		else if (config.startOnFocusLoss() && session == null)
		{
			start(AwaySessionTrigger.FOCUS, EnumSet.noneOf(Skill.class));
		}
	}

	void manualInput()
	{
		if (trigger() == AwaySessionTrigger.IDLE)
		{
			end("manual input");
		}
		// This same input begins the next inactivity period, even when it ends a session.
		inactiveTicks = 0;
		log.debug("Manual input reset inactivity: gameTick={}, inactiveTicks=0, activeTrigger={}",
			gameTicks, trigger());
	}

	void statChanged(Skill skill, int xp)
	{
		Integer previous = experience.put(skill, xp);
		// A missing baseline is synchronization, not a gain. Decreases rebaseline only.
		if (session == null || previous == null || xp <= previous)
		{
			return;
		}
		long gained = (long) xp - previous;
		session.xpGained.merge(skill, gained, Long::sum);
		if (trigger() == AwaySessionTrigger.IDLE && session.enabledSkills.contains(skill)
			&& session.relevantSkills.add(skill))
		{
			log.debug("IDLE session enabled skill activity detected: {} (+{} XP)", skill, gained);
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
	}

	AwaySessionTrigger trigger()
	{
		return session == null ? null : session.trigger;
	}

	Set<Skill> relevantSkills()
	{
		return session == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(session.relevantSkills);
	}

	private void start(AwaySessionTrigger trigger, Set<Skill> enabledSkills)
	{
		session = new Session(trigger, gameTicks, nanoTime.getAsLong(), timestamp.get(), enabledSkills);
		log.debug("Away session started: trigger={}, gameTick={}, enabledSkills={}", trigger, gameTicks, enabledSkills);
	}

	private void end(String reason)
	{
		Session ended = session;
		session = null;
		if (ended.trigger == AwaySessionTrigger.IDLE)
		{
			// Re-arm every idle-session ending centrally, before diagnostics run.
			inactiveTicks = 0;
		}
		Instant endTimestamp = timestamp.get();
		long ticks = gameTicks - ended.startTick;
		long elapsedMillis = (nanoTime.getAsLong() - ended.startNanos) / 1_000_000L;
		String outcome = ended.trigger == AwaySessionTrigger.IDLE
			? (ended.relevantSkills.isEmpty() ? "discarded" : "relevant") : "ended";
		log.debug("Away session ended: trigger={}, reason={}, outcome={}, skills={}, elapsedGameTicks={}, elapsedMs={}",
			ended.trigger, reason, outcome, ended.relevantSkills, ticks, elapsedMillis);
		if (!ended.xpGained.isEmpty()
			&& (ended.trigger == AwaySessionTrigger.FOCUS || !ended.relevantSkills.isEmpty()))
		{
			AwayRecapSession recap = new AwayRecapSession(ended.trigger, ended.startTick,
				ended.startTimestamp, gameTicks, endTimestamp, elapsedMillis,
				ended.xpGained, ended.relevantSkills);
			logRecap(recap);
			// Hand off an immutable snapshot; no session history or permanent storage.
			recapConsumer.accept(recap);
		}
	}

	private void logRecap(AwayRecapSession recap)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		StringBuilder message = new StringBuilder("Away Recap\nTrigger: ")
			.append(recap.getTrigger())
			.append("\nDuration: ").append(recap.getElapsedGameTicks()).append(" ticks / ")
			.append(String.format(Locale.ROOT, "%.1f", recap.getElapsedMillis() / 1000.0))
			.append(" seconds\nXP gained:");
		recap.getXpGained().forEach((skill, gained) ->
			message.append("\n- ").append(skill.getName()).append(": ").append(gained));
		log.debug("{}", message);
	}

	private static final class Session
	{
		private final AwaySessionTrigger trigger;
		private final long startTick;
		private final long startNanos;
		private final Instant startTimestamp;
		private final Map<Skill, Long> xpGained = new EnumMap<>(Skill.class);
		private final Set<Skill> enabledSkills;
		private final EnumSet<Skill> relevantSkills = EnumSet.noneOf(Skill.class);

		private Session(AwaySessionTrigger trigger, long startTick, long startNanos,
			Instant startTimestamp, Set<Skill> enabledSkills)
		{
			this.trigger = trigger;
			this.startTick = startTick;
			this.startNanos = startNanos;
			this.startTimestamp = startTimestamp;
			this.enabledSkills = EnumSet.noneOf(Skill.class);
			this.enabledSkills.addAll(enabledSkills);
		}
	}
}
