package com.awayrecap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Skill;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

public class AwayRecapControllerTest
{
	private final AtomicLong now = new AtomicLong();
	private final AwayRecapController controller = new AwayRecapController(now::get);

	private AwayRecapSession recap(AwaySessionTrigger trigger, long durationMillis,
		Map<Skill, Long> xp, Set<Skill> relevant)
	{
		return new AwayRecapSession(trigger, 0, Instant.EPOCH, 33,
			Instant.EPOCH.plusMillis(durationMillis), durationMillis, xp, relevant);
	}

	private AwayRecapSession focusRecap(long xp)
	{
		return recap(AwaySessionTrigger.FOCUS, 19900,
			Collections.singletonMap(Skill.WOODCUTTING, xp), Collections.emptySet());
	}

	private void seconds(long seconds)
	{
		now.set(TimeUnit.SECONDS.toNanos(seconds));
	}

	@Test
	public void noCompletedRecapMeansNoPresentation()
	{
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void defaultDurationExpiresExactlyAtTenSeconds()
	{
		controller.show(focusRecap(337), true, new AwayRecapConfig() {}.overlayDurationSeconds());
		now.set(TimeUnit.SECONDS.toNanos(10) - 1);
		assertNotNull(controller.visiblePresentation(true));
		seconds(10);
		assertNull(controller.visiblePresentation(true));
		seconds(100);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void newestRecapReplacesPreviousAndRestartsTimer()
	{
		controller.show(focusRecap(337), true, 10);
		seconds(9);
		controller.show(focusRecap(525), true, 10);
		seconds(10);
		assertEquals("+525 XP", controller.visiblePresentation(true).getXpRows().get(0).getGained());
		seconds(18);
		assertNotNull(controller.visiblePresentation(true));
		seconds(19);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void configuredDurationAndBoundsAreRespected()
	{
		for (int configured : new int[]{1, 3, 20, 60, 100})
		{
			now.set(0);
			controller.show(focusRecap(337), true, configured);
			int expected = Math.max(3, Math.min(60, configured));
			now.set(TimeUnit.SECONDS.toNanos(expected) - 1);
			assertNotNull(controller.visiblePresentation(true));
			seconds(expected);
			assertNull(controller.visiblePresentation(true));
		}
	}

	@Test
	public void disablingClearsAndReenablingDoesNotResurrectRecap()
	{
		controller.show(focusRecap(337), true, 10);
		assertNull(controller.visiblePresentation(false));
		assertNull(controller.visiblePresentation(true));
		controller.show(focusRecap(525), true, 10);
		assertNotNull(controller.visiblePresentation(true));
	}

	@Test
	public void recapReceivedWhileDisabledIsNotQueued()
	{
		controller.show(focusRecap(337), false, 10);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void emptyFocusRecapIsNotShown()
	{
		controller.show(focusRecap(0), true, 10);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void discardedIdleCandidateIsNotShown()
	{
		controller.show(recap(AwaySessionTrigger.IDLE, 19900,
			Collections.singletonMap(Skill.HITPOINTS, 12L), Collections.emptySet()), true, 10);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void discardedCandidateDoesNotReplaceOrExtendCurrentRecap()
	{
		controller.show(focusRecap(337), true, 10);
		seconds(9);
		controller.show(recap(AwaySessionTrigger.IDLE, 19900,
			Collections.singletonMap(Skill.HITPOINTS, 12L), Collections.emptySet()), true, 10);
		assertEquals("Focus", controller.visiblePresentation(true).getTrigger());
		seconds(10);
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void labelsAndXpRowsAreHumanReadableAndExcludeZeroXp()
	{
		Map<Skill, Long> xp = new EnumMap<>(Skill.class);
		xp.put(Skill.WOODCUTTING, 337L);
		xp.put(Skill.HITPOINTS, 12L);
		xp.put(Skill.MINING, 0L);
		controller.show(recap(AwaySessionTrigger.IDLE, 19900, xp,
			Collections.singleton(Skill.WOODCUTTING)), true, 10);
		AwayRecapController.Presentation view = controller.visiblePresentation(true);
		assertEquals("Idle", view.getTrigger());
		assertEquals("19.9s", view.getDuration());
		assertEquals(2, view.getXpRows().size());
		assertTrue(view.getXpRows().stream().anyMatch(row ->
			row.getSkill().equals("Woodcutting") && row.getGained().equals("+337 XP")));
		assertTrue(view.getXpRows().stream().anyMatch(row ->
			row.getSkill().equals("Hitpoints") && row.getGained().equals("+12 XP")));
		controller.show(focusRecap(337), true, 10);
		assertEquals("Focus", controller.visiblePresentation(true).getTrigger());
	}

	@Test
	public void durationFormatsAtMinuteBoundary()
	{
		assertEquals("0.0s", AwayRecapController.formatDuration(0));
		assertEquals("19.9s", AwayRecapController.formatDuration(19900));
		assertEquals("59.9s", AwayRecapController.formatDuration(59900));
		assertEquals("1m 0s", AwayRecapController.formatDuration(60000));
		assertEquals("2m 14s", AwayRecapController.formatDuration(134900));
	}

	@Test
	public void clearRemovesPresentationForLifecycleCleanup()
	{
		controller.show(focusRecap(337), true, 10);
		controller.clear();
		assertNull(controller.visiblePresentation(true));
	}

	@Test
	public void disabledOverlayDoesNotStopXpCollectionOrRecapLogging()
	{
		List<AwayRecapSession> completed = new ArrayList<>();
		AwaySessionManager manager = new AwaySessionManager(now::get, () -> Instant.EPOCH, recap ->
		{
			completed.add(recap);
			controller.show(recap, false, 10);
		});
		Logger logger = (Logger) LoggerFactory.getLogger(AwaySessionManager.class);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try
		{
			AwayRecapConfig config = new AwayRecapConfig() {};
			manager.baseline(Collections.singletonMap(Skill.WOODCUTTING, 1000));
			manager.focusChanged(false, config);
			manager.statChanged(Skill.WOODCUTTING, 1337);
			manager.focusChanged(true, config);
			assertEquals(1, completed.size());
			assertEquals(Long.valueOf(337), completed.get(0).getXpGained().get(Skill.WOODCUTTING));
			assertTrue(logs.list.stream().map(ILoggingEvent::getFormattedMessage)
				.anyMatch(message -> message.startsWith("Away Recap\n")));
			assertNull(controller.visiblePresentation(true));
		}
		finally
		{
			logger.detachAppender(logs);
			logs.stop();
		}
	}
}
