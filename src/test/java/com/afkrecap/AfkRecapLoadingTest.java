package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapLoadingTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AtomicLong nanos = new AtomicLong();
	private Map<Integer, Integer> inventory = Map.of(ItemID.COINS, 0);
	private final AfkSessionManager manager = new AfkSessionManager(nanos::get,
		() -> Instant.EPOCH, recaps::add, () -> inventory);

	private void login(int xp, int prayer)
	{
		manager.loggedIn(Map.of(Skill.ATTACK, xp, Skill.HITPOINTS, xp), prayer);
	}
	private void ticks(int count)
	{
		for (int i = 0; i < count; i++) { manager.gameTick(config); }
	}
	private void idle() { login(1000, 99); ticks(10); }
	private void focus() { login(1000, 99); manager.focusChanged(false, config); }
	private void items(int count)
	{
		inventory = Map.of(ItemID.COINS, count); manager.inventoryChanged(inventory);
	}
	private void resume()
	{
		manager.gameStateChanged(GameState.LOGGED_IN);
		assertFalse(manager.isReady());
		login(9000, 50);
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

	@Test public void relevantIdleSurvivesLoadingAndResumesSameSession()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1010);
		manager.gameStateChanged(GameState.LOADING);
		assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
		assertTrue(manager.relevantSkills().contains(Skill.ATTACK));
		assertTrue(recaps.isEmpty()); resume(); ticks(2); nanos.set(7_000_000_000L);
		manager.manualInput();
		AfkRecapSession recap = recaps.get(0);
		assertEquals(AfkSessionTrigger.IDLE, recap.getTrigger());
		assertEquals(10, recap.getStartGameTick()); assertEquals(2, recap.getElapsedGameTicks());
		assertEquals(Instant.EPOCH, recap.getStartTimestamp()); assertEquals(7000, recap.getElapsedMillis());
		assertEquals(Map.of(Skill.ATTACK, 10L), recap.getXpGained());
	}

	@Test public void focusSurvivesLoading()
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOADING);
		manager.focusChanged(true, config); assertTrue(recaps.isEmpty());
		assertEquals(AfkSessionTrigger.FOCUS, manager.trigger()); resume(); manager.focusChanged(true, config);
		assertEquals(AfkSessionTrigger.FOCUS, recaps.get(0).getTrigger());
		assertEquals(Map.of(ItemID.COINS, 10), recaps.get(0).getItemGains());
	}

	@Test public void everyAccumulatedMetricAndPendingResourceSurvivesLoading()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1040); manager.statChanged(Skill.HITPOINTS, 1013);
		items(10); Object cow = new Object(); manager.npcDamage(cow, 10, true, false); manager.npcDeath(cow, "Cow");
		manager.prayerChanged(90); damage(12);
		manager.groundSpawn(new Object(), ItemID.COINS, 1, TileItem.OWNERSHIP_SELF, 1000000, false, config);
		Object genie = new Object(); manager.randomEventTargeted(genie, NpcID.MACRO_GENI, "Genie", true, false);
		manager.randomEventDespawned(genie); ticks(1);
		manager.acquisitionMessage("You get some oak logs.", false, true);
		manager.gameStateChanged(GameState.LOADING); items(100); resume(); manager.manualInput();
		AfkRecapSession recap = recaps.get(0);
		assertEquals(Map.of(Skill.ATTACK, 40L, Skill.HITPOINTS, 13L), recap.getXpGained());
		assertEquals(Map.of(ItemID.COINS, 10, ItemID.OAK_LOGS, 1), recap.getItemGains());
		assertEquals(Map.of(ItemID.COINS, 1), recap.getNotableDrops());
		assertEquals(Map.of("Cow", 1), recap.getNpcKills()); assertEquals(9, recap.getPrayerUsed());
		assertEquals(12, recap.getDamageTaken()); assertEquals(Map.of("Genie", 1), recap.getMissedRandomEvents());
	}

	@Test public void suspendedEventsAndRefreshedBaselinesDoNotCreateGains()
	{
		idle(); manager.statChanged(Skill.ATTACK, 1010); items(10);
		manager.gameStateChanged(GameState.LOADING);
		manager.statChanged(Skill.ATTACK, 5000); manager.prayerChanged(0); damage(100); items(100);
		manager.acquisitionMessage("You get some oak logs.", false, true);
		manager.resourceTransfer(ResourceAcquisitionMessages.Family.LOG);
		Object npc = new Object(); manager.npcDamage(npc, 10, true, false); manager.npcDeath(npc, "Cow");
		manager.groundSpawn(new Object(), ItemID.COINS, 5, TileItem.OWNERSHIP_SELF, 1000000, false, config);
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", true, false); manager.randomEventDespawned(npc);
		ticks(10); manager.manualInput(); assertTrue(recaps.isEmpty());
		manager.gameStateChanged(GameState.LOGGED_IN); manager.statChanged(Skill.ATTACK, 6000); items(200);
		login(9000, 50); manager.statChanged(Skill.ATTACK, 9005); items(202); manager.prayerChanged(49);
		manager.manualInput(); AfkRecapSession recap = recaps.get(0);
		assertEquals(Map.of(Skill.ATTACK, 15L), recap.getXpGained());
		assertEquals(Map.of(ItemID.COINS, 12), recap.getItemGains()); assertEquals(1, recap.getPrayerUsed());
		assertEquals(0, recap.getDamageTaken()); assertTrue(recap.getNpcKills().isEmpty());
		assertTrue(recap.getNotableDrops().isEmpty()); assertTrue(recap.getMissedRandomEvents().isEmpty());
	}

	private void loadingThenExit(GameState state, AfkSessionEndReason reason)
	{
		idle(); manager.statChanged(Skill.ATTACK, 1010); items(10);
		manager.gameStateChanged(GameState.LOADING); manager.gameStateChanged(state);
		manager.gameStateChanged(GameState.LOGIN_SCREEN); manager.gameStateChanged(GameState.LOGGING_IN);
		assertEquals(1, recaps.size()); assertEquals(reason, recaps.get(0).getEndReason());
		assertEquals(Map.of(Skill.ATTACK, 10L), recaps.get(0).getXpGained());
		assertEquals(Map.of(ItemID.COINS, 10), recaps.get(0).getItemGains());
		assertNull(manager.trigger()); assertFalse(manager.isReady());
	}
	@Test public void loadingThenHopFinalizesOnce() { loadingThenExit(GameState.HOPPING, AfkSessionEndReason.WORLD_HOP); }
	@Test public void loadingThenDisconnectFinalizesOnce() { loadingThenExit(GameState.CONNECTION_LOST, AfkSessionEndReason.DISCONNECT); }

	@Test public void loadingAloneNeverPublishesRecap()
	{
		focus(); items(10); manager.gameStateChanged(GameState.LOADING);
		manager.gameStateChanged(GameState.LOADING); ticks(100);
		assertTrue(recaps.isEmpty()); assertEquals(AfkSessionTrigger.FOCUS, manager.trigger());
	}

	@Test public void trueExitClearsPreservedSessionAndBaselines()
	{
		loadingThenExit(GameState.CONNECTION_LOST, AfkSessionEndReason.DISCONNECT);
		items(100); manager.gameStateChanged(GameState.LOADING); resume();
		assertNull(manager.trigger()); manager.focusChanged(false, config);
		manager.statChanged(Skill.ATTACK, 9000); items(100); manager.focusChanged(true, config);
		assertEquals(1, recaps.size());
		manager.focusChanged(false, config); manager.statChanged(Skill.ATTACK, 9001); items(101);
		manager.focusChanged(true, config); assertEquals(2, recaps.size());
		assertEquals(Map.of(Skill.ATTACK, 1L), recaps.get(1).getXpGained());
		assertEquals(Map.of(ItemID.COINS, 1), recaps.get(1).getItemGains());
	}

	@Test public void loadingPreservesInactivityBeforeCandidateStarts()
	{
		login(1000, 99); ticks(9); manager.gameStateChanged(GameState.LOADING); ticks(100);
		resume(); assertNull(manager.trigger()); ticks(1); assertEquals(AfkSessionTrigger.IDLE, manager.trigger());
	}

	@Test public void irrelevantCandidateRetainsDataUntilLaterActivityQualifies()
	{
		idle(); items(10); manager.gameStateChanged(GameState.LOADING); items(100); resume();
		assertTrue(manager.relevantSkills().isEmpty()); manager.statChanged(Skill.ATTACK, 9001); manager.manualInput();
		assertEquals(Map.of(ItemID.COINS, 10), recaps.get(0).getItemGains());
	}

	@Test public void loadingClearsUnconfirmedSceneEvidenceWithoutInventingMissesOrKills()
	{
		focus(); items(10); Object npc = new Object(); manager.npcDamage(npc, 10, true, false);
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", true, false);
		manager.randomEventDespawned(npc); manager.gameStateChanged(GameState.LOADING);
		resume(); manager.npcDeath(npc, "Cow"); ticks(1); manager.focusChanged(true, config);
		assertTrue(recaps.get(0).getNpcKills().isEmpty()); assertTrue(recaps.get(0).getMissedRandomEvents().isEmpty());
	}
}
