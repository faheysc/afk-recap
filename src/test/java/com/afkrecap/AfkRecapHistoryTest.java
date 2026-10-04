package com.afkrecap;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapHistoryTest
{
	private final AfkRecapHistory history = new AfkRecapHistory();

	private AfkRecapSession focus(long timestamp)
	{
		return new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH, 33,
			Instant.ofEpochSecond(timestamp), 19900,
			Collections.singletonMap(Skill.WOODCUTTING, 337L), Collections.emptySet());
	}

	@Test
	public void addsCompletedRecaps()
	{
		AfkRecapSession recap = focus(1);
		assertTrue(history.add(recap));
		assertEquals(Collections.singletonList(recap), history.snapshot());
	}

	@Test
	public void sortsReverseChronologicallyEvenWhenArrivalsAreOutOfOrder()
	{
		AfkRecapSession oldest = focus(1);
		AfkRecapSession newest = focus(3);
		AfkRecapSession middle = focus(2);
		history.add(oldest);
		history.add(newest);
		history.add(middle);
		List<AfkRecapSession> recaps = history.snapshot();
		assertSame(newest, recaps.get(0));
		assertSame(middle, recaps.get(1));
		assertSame(oldest, recaps.get(2));
	}

	@Test
	public void timestampTiesKeepNewestArrivalFirst()
	{
		AfkRecapSession first = focus(1);
		AfkRecapSession second = focus(1);
		history.add(first);
		history.add(second);
		assertSame(second, history.snapshot().get(0));
	}

	@Test
	public void historyLimitDiscardsOldest()
	{
		history.setLimit(2);
		history.add(focus(1));
		AfkRecapSession middle = focus(2);
		AfkRecapSession newest = focus(3);
		history.add(middle);
		history.add(newest);
		assertEquals(2, history.snapshot().size());
		assertSame(newest, history.snapshot().get(0));
		assertSame(middle, history.snapshot().get(1));
		// An older delayed arrival must not evict a more recent completion.
		history.add(focus(0));
		assertSame(middle, history.snapshot().get(1));
	}

	@Test
	public void defaultLimitIsTwenty()
	{
		assertEquals(20, new AfkRecapConfig() {}.recentRecapLimit());
		for (int i = 0; i < 25; i++)
		{
			history.add(focus(i));
		}
		assertEquals(20, history.snapshot().size());
		assertEquals(Instant.ofEpochSecond(5), history.snapshot().get(19).getEndTimestamp());
	}

	@Test
	public void reducingLimitTrimsImmediatelyAndIncreasingDoesNotRestoreDiscardedEntries()
	{
		for (int i = 0; i < 5; i++)
		{
			history.add(focus(i));
		}
		history.setLimit(2);
		assertEquals(2, history.snapshot().size());
		assertEquals(Instant.ofEpochSecond(3), history.snapshot().get(1).getEndTimestamp());
		history.setLimit(10);
		assertEquals(2, history.snapshot().size());
	}

	@Test
	public void limitIsClampedToConfiguredBounds()
	{
		history.setLimit(200);
		for (int i = 0; i < 110; i++)
		{
			history.add(focus(i));
		}
		assertEquals(100, history.snapshot().size());
		history.setLimit(0);
		assertEquals(1, history.snapshot().size());
		assertEquals(Instant.ofEpochSecond(109), history.snapshot().get(0).getEndTimestamp());
	}

	@Test
	public void clearingHistoryAllowsFreshRecaps()
	{
		history.add(focus(1));
		history.clear();
		assertTrue(history.snapshot().isEmpty());
		AfkRecapSession next = focus(2);
		history.add(next);
		assertEquals(Collections.singletonList(next), history.snapshot());
	}

	@Test
	public void disabledSidePanelRetainsHistoryAndOverlayStillReceivesRecaps()
	{
		AtomicLong now = new AtomicLong();
		AfkRecapController overlay = new AfkRecapController(now::get);
		AfkRecapConfig config = new AfkRecapConfig()
		{
			@Override public boolean enableSidePanel() { return sidePanelEnabled; }
		};
		AfkSessionManager manager = new AfkSessionManager(now::get, () -> Instant.EPOCH, recap ->
		{
			overlay.show(recap, config.showOverlay(), config.overlayDurationSeconds());
			history.add(recap);
		});
		manager.baseline(Collections.singletonMap(Skill.WOODCUTTING, 1000));
		manager.focusChanged(false, config);
		manager.statChanged(Skill.WOODCUTTING, 1337);
		manager.focusChanged(true, config);
		sidePanelEnabled = false;
		manager.focusChanged(false, config);
		manager.statChanged(Skill.WOODCUTTING, 1387);
		manager.focusChanged(true, config);
		assertFalse(config.enableSidePanel());
		assertEquals(2, history.snapshot().size());
		assertEquals("+50 XP", overlay.visiblePresentation(true).getXpRows().get(0).getGained());
		sidePanelEnabled = true;
		assertTrue(config.enableSidePanel());
		assertEquals(2, history.snapshot().size());
	}

	@Test
	public void sessionPipelineOnlyAddsRelevantCompletedRecaps()
	{
		AfkRecapConfig config = new AfkRecapConfig() {};
		AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH, history::add);
		Map<Skill, Integer> baseline = new EnumMap<>(Skill.class);
		baseline.put(Skill.WOODCUTTING, 1000);
		baseline.put(Skill.PRAYER, 1000);
		manager.baseline(baseline);
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		manager.statChanged(Skill.PRAYER, 1012);
		manager.manualInput();
		assertTrue(history.snapshot().isEmpty());
		manager.focusChanged(false, config);
		manager.focusChanged(true, config);
		assertTrue(history.snapshot().isEmpty());
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		manager.statChanged(Skill.WOODCUTTING, 1050);
		assertTrue(history.snapshot().isEmpty());
		manager.manualInput();
		assertEquals(1, history.snapshot().size());
		assertEquals(AfkSessionTrigger.IDLE, history.snapshot().get(0).getTrigger());
		manager.focusChanged(false, config);
		manager.statChanged(Skill.PRAYER, 1020);
		manager.focusChanged(true, config);
		assertEquals(2, history.snapshot().size());
		assertEquals(AfkSessionTrigger.FOCUS, history.snapshot().get(0).getTrigger());
	}

	@Test
	public void historyDefensivelyRejectsEmptyAndDiscardedRecaps()
	{
		AfkRecapSession empty = new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
			1, Instant.EPOCH, 600, Collections.emptyMap(), Collections.emptySet());
		AfkRecapSession discarded = new AfkRecapSession(AfkSessionTrigger.IDLE, 0, Instant.EPOCH,
			1, Instant.EPOCH, 600, Collections.singletonMap(Skill.HITPOINTS, 12L), Collections.emptySet());
		assertFalse(history.add(empty));
		assertFalse(history.add(discarded));
		assertTrue(history.snapshot().isEmpty());
	}

	@Test
	public void snapshotIsDetachedFromLaterChanges()
	{
		history.add(focus(1));
		List<AfkRecapSession> snapshot = history.snapshot();
		history.clear();
		assertEquals(1, snapshot.size());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void snapshotsAreImmutable()
	{
		history.snapshot().add(focus(1));
	}

	@Test
	public void panelFormattingUsesLocalCompletionTimeAndGroupedXp()
	{
		AfkRecapSession focus = new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
			33, Instant.parse("2026-10-04T02:09:00Z"), 19900,
			Collections.singletonMap(Skill.WOODCUTTING, 337L), Collections.emptySet());
		assertEquals("22:09 — Focus — 19.9s", AfkRecapPanelPresentation.header(focus,
			ZoneId.of("America/New_York")));
		AfkRecapSession idle = new AfkRecapSession(AfkSessionTrigger.IDLE, 0, Instant.EPOCH,
			110, Instant.parse("2026-10-04T02:05:00Z"), 66000,
			Collections.singletonMap(Skill.WOODCUTTING, 1050L), Collections.singleton(Skill.WOODCUTTING));
		assertEquals("22:05 — Idle — 1m 06s", AfkRecapPanelPresentation.header(idle,
			ZoneId.of("America/New_York")));
		assertEquals("+1,050 XP", AfkRecapPanelPresentation.xp(1050));
	}

	private boolean sidePanelEnabled = true;
}
