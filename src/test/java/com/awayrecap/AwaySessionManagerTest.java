package com.awayrecap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Skill;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

public class AwaySessionManagerTest
{
	private final AwayRecapConfig config = new AwayRecapConfig() {};

	private void ticks(AwaySessionManager manager, int count)
	{
		for (int i = 0; i < count; i++)
		{
			manager.gameTick(config);
		}
	}

	@Test
	public void relevantIdleSessionRearmsFromTheInputThatEndsIt()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.baseline(Collections.singletonMap(Skill.WOODCUTTING, 100));
		manager.manualInput();
		ticks(manager, 10);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		manager.statChanged(Skill.WOODCUTTING, 110);
		assertEquals(Collections.singleton(Skill.WOODCUTTING), manager.relevantSkills());

		manager.manualInput();
		assertNull(manager.trigger());
		// Continued skilling must neither reset inactivity nor keep the old session alive.
		for (int i = 0; i < 9; i++)
		{
			manager.statChanged(Skill.WOODCUTTING, 120 + i * 10);
			manager.gameTick(config);
			assertNull(manager.trigger());
		}
		ticks(manager, 1);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(Skill.WOODCUTTING, 210);
		assertEquals(Collections.singleton(Skill.WOODCUTTING), manager.relevantSkills());
	}

	@Test
	public void discardedIdleCandidateRearmsFromTheInputThatEndsIt()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.manualInput();
		ticks(manager, 10);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());

		manager.manualInput();
		assertNull(manager.trigger());
		ticks(manager, 9);
		assertNull(manager.trigger());
		ticks(manager, 1);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());
	}

	@Test
	public void idleStartsAtThresholdWithoutRecentXp()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.baseline(Collections.singletonMap(Skill.FISHING, 100));
		ticks(manager, 9);
		assertNull(manager.trigger());
		ticks(manager, 1);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(Skill.FISHING, 110);
		assertTrue(manager.relevantSkills().contains(Skill.FISHING));
		manager.manualInput();
		assertNull(manager.trigger());
	}

	@Test
	public void manualInputResetsInactivityAndEndsEmptyCandidate()
	{
		AwaySessionManager manager = new AwaySessionManager();
		ticks(manager, 9);
		manager.manualInput();
		ticks(manager, 9);
		assertNull(manager.trigger());
		ticks(manager, 1);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().isEmpty());
		manager.manualInput();
		assertNull(manager.trigger());
		ticks(manager, 9);
		assertNull(manager.trigger());
	}

	@Test
	public void onlyActualEnabledXpIncreasesMakeIdleRelevant()
	{
		AwayRecapConfig fishingOnly = new AwayRecapConfig()
		{
			@Override public boolean idleMining() { return false; }
		};
		AwaySessionManager manager = new AwaySessionManager();
		manager.statChanged(Skill.FISHING, 100);
		manager.statChanged(Skill.MINING, 100);
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(fishingOnly);
		}
		manager.statChanged(Skill.MINING, 110);
		manager.statChanged(Skill.FISHING, 100);
		manager.statChanged(Skill.FISHING, 90);
		assertTrue(manager.relevantSkills().isEmpty());
		manager.statChanged(Skill.FISHING, 95);
		assertEquals(Collections.singleton(Skill.FISHING), manager.relevantSkills());
	}

	@Test
	public void initialStatAndPreSessionXpAreNotActivity()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.statChanged(Skill.FISHING, 100);
		manager.statChanged(Skill.FISHING, 110);
		ticks(manager, 10);
		manager.statChanged(Skill.MINING, 100);
		assertTrue(manager.relevantSkills().isEmpty());
	}

	@Test
	public void focusDoesNotReplaceOrEndIdleCandidate()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.statChanged(Skill.WOODCUTTING, 100);
		ticks(manager, 10);
		manager.statChanged(Skill.WOODCUTTING, 110);
		manager.focusChanged(false, config);
		manager.focusChanged(true, config);
		assertEquals(AwaySessionTrigger.IDLE, manager.trigger());
		assertEquals(Collections.singleton(Skill.WOODCUTTING), manager.relevantSkills());
		manager.manualInput();
		assertNull(manager.trigger());
	}

	@Test
	public void idleAndManualInputDoNotReplaceOrEndFocusSession()
	{
		AwaySessionManager manager = new AwaySessionManager();
		manager.focusChanged(false, config);
		ticks(manager, 20);
		manager.focusChanged(false, config);
		manager.manualInput();
		assertEquals(AwaySessionTrigger.FOCUS, manager.trigger());
		manager.focusChanged(true, config);
		assertNull(manager.trigger());
		ticks(manager, 9);
		assertNull(manager.trigger());
	}

	@Test
	public void disabledTriggersDoNotStartSessions()
	{
		AwayRecapConfig disabled = new AwayRecapConfig()
		{
			@Override public boolean startOnFocusLoss() { return false; }
			@Override public boolean startOnIdle() { return false; }
		};
		AwaySessionManager manager = new AwaySessionManager();
		manager.focusChanged(false, disabled);
		for (int i = 0; i < 100; i++)
		{
			manager.gameTick(disabled);
		}
		assertNull(manager.trigger());
	}

	@Test
	public void enabledSkillsAreSnapshottedForEachCandidate()
	{
		AwayRecapConfig mutable = new AwayRecapConfig()
		{
			@Override public boolean idleFishing() { return fishingEnabled; }
		};
		AwaySessionManager manager = new AwaySessionManager();
		manager.statChanged(Skill.FISHING, 100);
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(mutable);
		}
		fishingEnabled = false;
		manager.statChanged(Skill.FISHING, 110);
		assertTrue(manager.relevantSkills().contains(Skill.FISHING));
		manager.manualInput();
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(mutable);
		}
		manager.statChanged(Skill.FISHING, 120);
		assertTrue(manager.relevantSkills().isEmpty());
		manager.reset("test shutdown");
		assertNull(manager.trigger());
	}

	@Test
	public void endLogsReportOutcomeAndElapsedTicksAndTime()
	{
		Logger logger = (Logger) LoggerFactory.getLogger(AwaySessionManager.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try
		{
			AtomicLong now = new AtomicLong();
			AwaySessionManager manager = new AwaySessionManager(now::get);
			manager.statChanged(Skill.FISHING, 100);
			ticks(manager, 10);
			ticks(manager, 3);
			now.set(1_800_000_000L);
			manager.statChanged(Skill.FISHING, 110);
			manager.manualInput();
			String relevant = lastSessionEnd(appender);
			assertTrue(relevant.contains("trigger=IDLE"));
			assertTrue(relevant.contains("outcome=relevant"));
			assertTrue(relevant.contains("elapsedGameTicks=3, elapsedMs=1800"));
			ticks(manager, 10);
			manager.manualInput();
			String discarded = lastSessionEnd(appender);
			assertTrue(discarded.contains("outcome=discarded"));
			assertTrue(discarded.contains("skills=[]"));
		}
		finally
		{
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private String lastSessionEnd(ListAppender<ILoggingEvent> appender)
	{
		return appender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.filter(message -> message.startsWith("Away session ended:"))
			.reduce((previous, latest) -> latest)
			.orElseThrow(AssertionError::new);
	}

	private boolean fishingEnabled = true;
}
