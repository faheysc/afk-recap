package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapDamageLifecycleTest
{
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, Instant::now, recaps::add);

	private static Hitsplat hit(int type, int amount)
	{
		return new Hitsplat()
		{
			@Override public int getHitsplatType() { return type; }
			@Override public int getAmount() { return amount; }
			@Override public int getDisappearsOnGameCycle() { return 0; }
		};
	}

	private void login(int xp)
	{
		manager.loggedIn(Collections.singletonMap(Skill.ATTACK, xp), 99);
	}

	private void start()
	{
		login(1000);
		manager.focusChanged(false, config);
	}

	private AfkRecapSession end()
	{
		manager.focusChanged(true, config);
		assertEquals(1, recaps.size());
		return recaps.get(0);
	}

	@Test public void oneDamageEvent()
	{
		start();
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 40));
		assertEquals(40, end().getDamageTaken());
	}

	@Test public void severalDamageEventsIncludingPoisonAndVenom()
	{
		start();
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_ME, 40));
		manager.playerHitsplat(hit(HitsplatID.POISON, 6));
		manager.playerHitsplat(hit(HitsplatID.VENOM, 20));
		manager.playerHitsplat(hit(HitsplatID.BURN, 1));
		assertEquals(67, end().getDamageTaken());
	}

	@Test public void healingDoesNotReduceDamage()
	{
		start();
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 40));
		manager.playerHitsplat(hit(HitsplatID.HEAL, 30));
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 20));
		assertEquals(60, end().getDamageTaken());
	}

	@Test public void zeroAndNonDamageHitsplatsIgnored()
	{
		start();
		for (int type : new int[]{HitsplatID.HEAL, HitsplatID.PRAYER_DRAIN,
			HitsplatID.DISEASE, HitsplatID.SANITY_DRAIN, HitsplatID.SANITY_RESTORE,
			HitsplatID.BLOCK_ME, HitsplatID.DAMAGE_ME_POISE})
		{
			manager.playerHitsplat(hit(type, 5));
		}
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 0));
		manager.playerHitsplat(null);
		manager.statChanged(Skill.ATTACK, 1010);
		AfkRecapSession recap = end();
		assertEquals(0, recap.getDamageTaken());
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(recap, true, 10);
		assertNull(controller.visiblePresentation(true).getDamageTaken());
	}

	@Test public void damageOutsideSessionIgnored()
	{
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 50));
		start();
		manager.statChanged(Skill.ATTACK, 1010);
		assertEquals(0, end().getDamageTaken());
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 50));
		assertEquals(0, recaps.get(0).getDamageTaken());
	}

	@Test public void damageAloneDoesNotMakeIdleRelevant()
	{
		login(1000);
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 40));
		assertTrue(manager.relevantSkills().isEmpty());
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test public void loginSynchronizationAndStaleFocusProduceNoRecap()
	{
		manager.gameStateChanged(GameState.LOGGED_IN);
		manager.baseline(Collections.singletonMap(Skill.ATTACK, 0));
		manager.focusChanged(false, config);
		manager.statChanged(Skill.ATTACK, 500000);
		manager.statChanged(Skill.HITPOINTS, 200000);
		for (int i = 0; i < 20; i++) { manager.gameTick(config); }
		assertNull(manager.trigger());
		login(500000);
		assertNull(manager.trigger());
		manager.statChanged(Skill.ATTACK, 500000);
		manager.focusChanged(true, config);
		assertTrue(recaps.isEmpty());
	}

	@Test public void firstLegitimateXpGainAfterLoginCounts()
	{
		manager.suspend();
		manager.statChanged(Skill.ATTACK, 500000);
		login(500000);
		manager.focusChanged(false, config);
		manager.statChanged(Skill.ATTACK, 500000);
		manager.statChanged(Skill.ATTACK, 500025);
		assertEquals(Long.valueOf(25), end().getXpGained().get(Skill.ATTACK));
	}

	@Test public void logoutFinalizesActiveSession()
	{
		start();
		manager.statChanged(Skill.ATTACK, 1010);
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 40));
		manager.gameStateChanged(GameState.LOGIN_SCREEN);
		manager.focusChanged(true, config);
		assertNull(manager.trigger());
		assertEquals(1, recaps.size());
		assertEquals(AfkSessionEndReason.LOGOUT_OR_DISCONNECT, recaps.get(0).getEndReason());
		assertEquals(40, recaps.get(0).getDamageTaken());
	}

	@Test public void worldHopFinalizesAndNewWorldHasFreshBaselinesAndTrackers()
	{
		start();
		Object npc = new Object();
		manager.npcDamage(npc, 10, true, false);
		manager.statChanged(Skill.ATTACK, 1010);
		manager.prayerChanged(80);
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 40));
		manager.inventoryChanged(Collections.singletonMap(net.runelite.api.gameval.ItemID.COINS, 10));
		manager.gameStateChanged(GameState.HOPPING);
		assertNull(manager.trigger());
		assertEquals(1, recaps.size());
		assertEquals(AfkSessionEndReason.WORLD_HOP, recaps.get(0).getEndReason());
		recaps.clear();
		manager.statChanged(Skill.ATTACK, 2000);
		manager.gameStateChanged(GameState.LOGGED_IN);
		login(2000);
		assertNull(manager.trigger());
		manager.focusChanged(false, config);
		manager.npcDeath(npc, "Cow");
		manager.statChanged(Skill.ATTACK, 2000);
		manager.statChanged(Skill.ATTACK, 2010);
		AfkRecapSession recap = end();
		assertEquals(Long.valueOf(10), recap.getXpGained().get(Skill.ATTACK));
		assertEquals(0, recap.getPrayerUsed());
		assertEquals(0, recap.getDamageTaken());
		assertTrue(recap.getNpcKills().isEmpty());
		assertTrue(recap.getItemGains().isEmpty());
	}

	@Test public void idleThresholdStartsFreshAfterReconnect()
	{
		login(1000);
		for (int i = 0; i < 9; i++) { manager.gameTick(config); }
		manager.gameStateChanged(GameState.CONNECTION_LOST);
		login(1000);
		for (int i = 0; i < 9; i++) { manager.gameTick(config); }
		assertNull(manager.trigger());
		manager.gameTick(config);
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
	}

	@Test public void damageShownInOverlayAndPanel() throws Exception
	{
		start();
		manager.playerHitsplat(hit(HitsplatID.DAMAGE_OTHER, 67));
		AfkRecapSession recap = end();
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(recap, true, 10);
		assertEquals("67", controller.visiblePresentation(true).getDamageTaken());
		AfkRecapHistory history = new AfkRecapHistory();
		history.add(recap);
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			AfkRecapPanel panel = new AfkRecapPanel(history, new AfkRecapItemPresentation(id -> null));
			assertTrue(hasLabel(panel, "Damage taken: 67"));
			history.clear();
			history.add(new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
				1, Instant.EPOCH, 600, Collections.singletonMap(Skill.ATTACK, 1L),
				Collections.emptySet()));
			panel.refresh();
			assertFalse(hasLabel(panel, "Damage taken: 0"));
			assertFalse(hasLabel(panel, "Damage taken: 67"));
		});
	}

	private static boolean hasLabel(java.awt.Container container, String text)
	{
		for (java.awt.Component child : container.getComponents())
		{
			if (child instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) child).getText()))
			{ return true; }
			if (child instanceof java.awt.Container && hasLabel((java.awt.Container) child, text))
			{ return true; }
		}
		return false;
	}
}
