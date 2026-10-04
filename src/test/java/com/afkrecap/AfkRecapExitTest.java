package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapExitTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final AtomicLong nanos = new AtomicLong();
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkRecapHistory history = new AfkRecapHistory();
	private final AfkRecapController overlay = new AfkRecapController(nanos::get);
	private Map<Integer, Integer> inventory = Collections.emptyMap();
	private final AfkSessionManager manager = new AfkSessionManager(nanos::get, () -> Instant.EPOCH,
		recap -> { history.add(recap); overlay.show(recap, true, 10); recaps.add(recap); }, () -> inventory);

	private void login(int xp)
	{
		manager.loggedIn(Map.of(Skill.ATTACK, xp, Skill.HITPOINTS, xp), 99);
	}
	private void ticks(int count)
	{
		for (int i = 0; i < count; i++) { manager.gameTick(config); }
	}
	private void idle() { login(1000); ticks(10); }
	private void focus() { login(1000); manager.focusChanged(false, config); }
	private void items(int count)
	{
		inventory = Map.of(ItemID.COINS, count); manager.inventoryChanged(inventory);
	}
	private void damage(int amount)
	{
		manager.playerHitsplat(new Hitsplat()
		{
			@Override public int getHitsplatType() { return HitsplatID.DAMAGE_OTHER; }
			@Override public int getAmount() { return amount; }
			@Override public int getDisappearsOnGameCycle() { return 0; }
		});
	}

	@Test public void relevantIdleLogoutStoredImmediately()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1010); manager.gameStateChanged(GameState.LOGIN_SCREEN);
		assertEquals(1, history.snapshot().size());
		assertEquals(AfkSessionTrigger.IDLE, recaps.get(0).getTrigger());
		assertEquals(AfkSessionEndReason.LOGOUT_OR_DISCONNECT, recaps.get(0).getEndReason());
		assertNull(manager.trigger()); assertFalse(manager.isReady());
	}

	@Test public void focusLogoutStored()
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOGIN_SCREEN);
		assertEquals(AfkSessionTrigger.FOCUS, history.snapshot().get(0).getTrigger());
	}

	@Test public void irrelevantIdleWithCombatAndItemsIsDiscarded()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean idleCombat() { return false; }
			@Override public boolean idleSlayer() { return false; }
		};
		login(1000); for (int i = 0; i < 10; i++) { manager.gameTick(disabled); }
		manager.statChanged(Skill.ATTACK, 1010); items(10); damage(20);
		manager.gameStateChanged(GameState.LOGIN_SCREEN);
		assertTrue(history.snapshot().isEmpty()); assertNull(manager.trigger());
	}

	@Test public void emptyFocusLogoutDiscarded()
	{
		focus(); manager.gameStateChanged(GameState.LOGIN_SCREEN); assertTrue(history.snapshot().isEmpty());
	}

	@Test public void everyMetricAndPendingResourceSurvivesFinalization()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1040); manager.statChanged(Skill.HITPOINTS, 1013);
		items(10); manager.acquisitionMessage("You get some oak logs.", false, true);
		Object npc = new Object(); manager.npcDamage(npc, 10, true, false); manager.npcDeath(npc, "Cow");
		manager.prayerChanged(90); damage(12); ticks(3); nanos.set(5_000_000_000L);
		manager.gameStateChanged(GameState.LOGIN_SCREEN);
		AfkRecapSession recap = history.snapshot().get(0);
		assertEquals(Map.of(Skill.ATTACK, 40L, Skill.HITPOINTS, 13L), recap.getXpGained());
		assertEquals(Map.of(ItemID.COINS, 10, ItemID.OAK_LOGS, 1), recap.getItemGains());
		assertEquals(Map.of("Cow", 1), recap.getNpcKills()); assertEquals(9, recap.getPrayerUsed());
		assertEquals(12, recap.getDamageTaken()); assertEquals(3, recap.getElapsedGameTicks());
		assertEquals(5000, recap.getElapsedMillis()); assertEquals(AfkSessionTrigger.IDLE, recap.getTrigger());
	}

	@Test public void gameExitNeverOpensOverlay()
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOGIN_SCREEN);
		assertNull(overlay.visiblePresentation(true)); assertEquals(1, history.snapshot().size());
	}

	@Test public void disconnectClearsPreviouslyVisibleOverlay()
	{
		focus(); items(10); manager.focusChanged(true, config); assertNotNull(overlay.visiblePresentation(true));
		manager.focusChanged(false, config); items(12); manager.gameStateChanged(GameState.CONNECTION_LOST);
		assertNull(overlay.visiblePresentation(true)); assertEquals(2, history.snapshot().size());
		assertEquals(AfkSessionEndReason.DISCONNECT, history.snapshot().get(0).getEndReason());
	}

	@Test public void worldHopFinalizesOnlyOnceAndNewWorldStartsClean()
	{
		focus(); manager.statChanged(Skill.ATTACK, 1010); items(10); damage(20);
		Object npc = new Object(); manager.npcDamage(npc, 10, true, false);
		manager.gameStateChanged(GameState.HOPPING); manager.gameStateChanged(GameState.LOGGING_IN);
		assertEquals(1, recaps.size()); assertEquals(AfkSessionEndReason.WORLD_HOP, recaps.get(0).getEndReason());
		manager.gameStateChanged(GameState.LOGGED_IN); manager.statChanged(Skill.ATTACK, 9000); login(9000);
		assertNull(manager.trigger()); manager.focusChanged(false, config);
		manager.npcDeath(npc, "Cow"); manager.statChanged(Skill.ATTACK, 9005); items(12);
		manager.focusChanged(true, config);
		AfkRecapSession next = recaps.get(1);
		assertEquals(Map.of(Skill.ATTACK, 5L), next.getXpGained()); assertEquals(Map.of(ItemID.COINS, 2), next.getItemGains());
		assertTrue(next.getNpcKills().isEmpty()); assertEquals(0, next.getPrayerUsed()); assertEquals(0, next.getDamageTaken());
	}

	@Test public void reconnectHasNoXpItemOrPendingMessageCarryOver()
	{
		focus(); manager.statChanged(Skill.ATTACK, 1010); items(10);
		manager.acquisitionMessage("You get some oak logs.", false, true);
		manager.gameStateChanged(GameState.CONNECTION_LOST);
		manager.statChanged(Skill.ATTACK, 2000); items(100); damage(30);
		manager.gameStateChanged(GameState.LOGGED_IN); login(2000); manager.focusChanged(false, config);
		manager.statChanged(Skill.ATTACK, 2000); items(100); manager.focusChanged(true, config);
		assertEquals(1, recaps.size()); assertEquals(1, history.snapshot().size());
		manager.focusChanged(false, config); manager.statChanged(Skill.ATTACK, 2001); items(101);
		manager.focusChanged(true, config);
		assertEquals(Map.of(Skill.ATTACK, 1L), recaps.get(1).getXpGained());
		assertEquals(Map.of(ItemID.COINS, 1), recaps.get(1).getItemGains());
	}

	@Test public void nextLoginSynchronizationAndStaleFocusCannotCreateRecap()
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOGIN_SCREEN);
		manager.gameStateChanged(GameState.LOGGED_IN); manager.focusChanged(false, config);
		manager.statChanged(Skill.ATTACK, 500000); items(100); login(500000);
		manager.statChanged(Skill.ATTACK, 500000); manager.focusChanged(true, config);
		assertEquals(1, recaps.size()); assertNull(manager.trigger());
		ticks(9); assertNull(manager.trigger()); ticks(1); assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
		manager.manualInput(); assertEquals(1, recaps.size());
	}

	@Test public void ambiguousLoginScreenCauseIsNotClaimedAsManualLogout()
	{
		assertEquals(AfkSessionEndReason.LOGOUT_OR_DISCONNECT, AfkSessionEndReason.forGameState(GameState.LOGIN_SCREEN));
		assertEquals(AfkSessionEndReason.DISCONNECT, AfkSessionEndReason.forGameState(GameState.CONNECTION_LOST));
		assertEquals(AfkSessionEndReason.WORLD_HOP, AfkSessionEndReason.forGameState(GameState.HOPPING));
		assertNull(AfkSessionEndReason.forGameState(GameState.LOADING));
	}

	@Test public void manualInputAndFocusReturnReasonsPreserved()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1010); manager.manualInput();
		assertEquals(AfkSessionEndReason.MANUAL_INPUT, recaps.get(0).getEndReason());
		manager.focusChanged(false, config); manager.statChanged(Skill.ATTACK, 1020); manager.focusChanged(true, config);
		assertEquals(AfkSessionEndReason.FOCUS_RETURN, recaps.get(1).getEndReason());
		assertNotNull(overlay.visiblePresentation(true));
	}

	@Test public void historyPanelAvailableWithoutRelogging() throws Exception
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOGIN_SCREEN);
		SwingUtilities.invokeAndWait(() ->
		{
			AfkRecapPanel panel = new AfkRecapPanel(history, new AfkRecapItemPresentation(id -> null));
			panel.refresh(); assertEquals(1, history.snapshot().size());
		});
		assertFalse(manager.isReady());
	}
}
