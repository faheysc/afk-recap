package com.afkrecap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Skill;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

public class AfkRecapCollectionTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final AtomicLong now = new AtomicLong();
	private final Instant epoch = Instant.parse("2026-10-04T00:00:00Z");
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(now::get,
		() -> epoch.plusNanos(now.get()), recaps::add);
	private final Logger logger = (Logger) LoggerFactory.getLogger(AfkSessionManager.class);
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@Before
	public void setUp()
	{
		Map<Skill, Integer> baseline = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			baseline.put(skill, 1000);
		}
		manager.baseline(baseline);
		logs.start();
		logger.addAppender(logs);
	}

	@After
	public void tearDown()
	{
		logger.detachAppender(logs);
		logs.stop();
	}

	private void startFocus()
	{
		manager.focusChanged(false, config);
	}

	private void endFocus()
	{
		manager.focusChanged(true, config);
	}

	private void ticks(int count)
	{
		for (int i = 0; i < count; i++)
		{
			manager.gameTick(config);
		}
	}

	private void startIdle()
	{
		manager.manualInput();
		ticks(10);
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
	}

	private AfkRecapSession onlyRecap()
	{
		assertEquals(1, recaps.size());
		return recaps.get(0);
	}

	private long recapLogCount()
	{
		return logs.list.stream().map(ILoggingEvent::getFormattedMessage)
			.filter(message -> message.startsWith("AFK Recap\n")).count();
	}

	@Test
	public void oneXpGainProducesFocusRecap()
	{
		startFocus();
		manager.statChanged(Skill.ATTACK, 1025);
		endFocus();
		assertEquals(AfkSessionTrigger.FOCUS, onlyRecap().getTrigger());
		assertEquals(Collections.singletonMap(Skill.ATTACK, 25L), onlyRecap().getXpGained());
		assertEquals(1, recapLogCount());
	}

	@Test
	public void multipleXpGainsInOneSkillAccumulate()
	{
		startFocus();
		manager.statChanged(Skill.WOODCUTTING, 1025);
		manager.statChanged(Skill.WOODCUTTING, 1075);
		manager.statChanged(Skill.WOODCUTTING, 1100);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.WOODCUTTING, 100L), onlyRecap().getXpGained());
	}

	@Test
	public void multipleSkillsAccumulateIndependently()
	{
		startFocus();
		manager.statChanged(Skill.WOODCUTTING, 1525);
		manager.statChanged(Skill.HITPOINTS, 1012);
		manager.statChanged(Skill.SAILING, 1030);
		manager.statChanged(Skill.HITPOINTS, 1020);
		endFocus();
		Map<Skill, Long> xp = onlyRecap().getXpGained();
		assertEquals(3, xp.size());
		assertEquals(Long.valueOf(525), xp.get(Skill.WOODCUTTING));
		assertEquals(Long.valueOf(20), xp.get(Skill.HITPOINTS));
		assertEquals(Long.valueOf(30), xp.get(Skill.SAILING));
	}

	@Test
	public void duplicateStatValuesDoNotDoubleCount()
	{
		startFocus();
		manager.statChanged(Skill.MINING, 1000);
		manager.statChanged(Skill.MINING, 1025);
		manager.statChanged(Skill.MINING, 1025);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.MINING, 25L), onlyRecap().getXpGained());
	}

	@Test
	public void firstStatSynchronizationIsOnlyABaseline()
	{
		manager.baseline(Collections.emptyMap());
		startFocus();
		manager.statChanged(Skill.WOODCUTTING, 50000);
		manager.statChanged(Skill.HITPOINTS, 20000);
		manager.statChanged(Skill.WOODCUTTING, 50025);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.WOODCUTTING, 25L), onlyRecap().getXpGained());
	}

	@Test
	public void explicitBaselineSynchronizationDoesNotAddXp()
	{
		startFocus();
		manager.baseline(Collections.singletonMap(Skill.MINING, 50000));
		manager.statChanged(Skill.MINING, 50000);
		manager.statChanged(Skill.MINING, 50010);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.MINING, 10L), onlyRecap().getXpGained());
	}

	@Test
	public void xpOutsideASessionIsExcluded()
	{
		manager.statChanged(Skill.ATTACK, 1100);
		startFocus();
		manager.statChanged(Skill.ATTACK, 1110);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.ATTACK, 10L), onlyRecap().getXpGained());
	}

	@Test
	public void decreasingXpNeverSubtractsFromRecordedGains()
	{
		startFocus();
		manager.statChanged(Skill.MINING, 1025);
		manager.statChanged(Skill.MINING, 900);
		manager.statChanged(Skill.MINING, 905);
		endFocus();
		assertEquals(Collections.singletonMap(Skill.MINING, 30L), onlyRecap().getXpGained());
	}

	@Test
	public void discardedIdleCandidateDoesNotEmitRecapEvenWithOtherXp()
	{
		startIdle();
		manager.statChanged(Skill.PRAYER, 1012);
		manager.manualInput();
		assertNull(manager.trigger());
		assertTrue(recaps.isEmpty());
		assertEquals(0, recapLogCount());
		assertTrue(logs.list.stream().map(ILoggingEvent::getFormattedMessage)
			.anyMatch(message -> message.contains("outcome=discarded")));
	}

	@Test
	public void disabledIdleSkillDoesNotMakeCandidateRelevant()
	{
		AfkRecapConfig noWoodcutting = new AfkRecapConfig()
		{
			@Override public boolean idleWoodcutting() { return false; }
		};
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(noWoodcutting);
		}
		manager.statChanged(Skill.WOODCUTTING, 1525);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
		assertEquals(0, recapLogCount());
	}

	@Test
	public void relevantIdleIncludesAllXpAndLogsDuration()
	{
		startIdle();
		manager.statChanged(Skill.HITPOINTS, 1012);
		manager.statChanged(Skill.WOODCUTTING, 1525);
		ticks(38);
		now.set(23_300_000_000L);
		manager.manualInput();
		AfkRecapSession recap = onlyRecap();
		assertEquals(AfkSessionTrigger.IDLE, recap.getTrigger());
		assertEquals(10, recap.getStartGameTick());
		assertEquals(48, recap.getEndGameTick());
		assertEquals(38, recap.getElapsedGameTicks());
		assertEquals(epoch, recap.getStartTimestamp());
		assertEquals(epoch.plusMillis(23300), recap.getEndTimestamp());
		assertEquals(23300, recap.getElapsedMillis());
		assertEquals(java.util.EnumSet.of(Skill.WOODCUTTING, Skill.HITPOINTS), recap.getRelevantIdleSkills());
		assertEquals(Long.valueOf(525), recap.getXpGained().get(Skill.WOODCUTTING));
		assertEquals(Long.valueOf(12), recap.getXpGained().get(Skill.HITPOINTS));
		String message = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
			.filter(value -> value.startsWith("AFK Recap\n")).findFirst().get();
		assertTrue(message.contains("Trigger: IDLE\nDuration: 38 ticks / 23.3 seconds"));
		assertTrue(message.contains("- Woodcutting: 525"));
		assertTrue(message.contains("- Hitpoints: 12"));
		assertFalse(message.contains("- Mining:"));
	}

	@Test
	public void focusWithNoXpDoesNotEmitEmptyRecap()
	{
		startFocus();
		manager.statChanged(Skill.MINING, 1000);
		manager.statChanged(Skill.FISHING, 900);
		endFocus();
		assertNull(manager.trigger());
		assertTrue(recaps.isEmpty());
		assertEquals(0, recapLogCount());
	}

	@Test
	public void repeatIdleRecapsHaveIndependentTotalsAndSnapshots()
	{
		startIdle();
		manager.statChanged(Skill.WOODCUTTING, 1025);
		manager.manualInput();
		ticks(9);
		assertNull(manager.trigger());
		ticks(1);
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
		manager.statChanged(Skill.WOODCUTTING, 1075);
		manager.manualInput();
		assertEquals(2, recaps.size());
		assertEquals(Long.valueOf(25), recaps.get(0).getXpGained().get(Skill.WOODCUTTING));
		assertEquals(Long.valueOf(50), recaps.get(1).getXpGained().get(Skill.WOODCUTTING));
		manager.reset("test shutdown");
		assertEquals(Long.valueOf(25), recaps.get(0).getXpGained().get(Skill.WOODCUTTING));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void completedXpMapIsImmutable()
	{
		startFocus();
		manager.statChanged(Skill.ATTACK, 1025);
		endFocus();
		onlyRecap().getXpGained().put(Skill.MINING, 1L);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void completedRelevantSkillsAreImmutable()
	{
		startIdle();
		manager.statChanged(Skill.FISHING, 1025);
		manager.manualInput();
		onlyRecap().getRelevantIdleSkills().add(Skill.MINING);
	}
}
