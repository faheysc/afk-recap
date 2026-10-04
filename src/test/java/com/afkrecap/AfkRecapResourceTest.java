package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapResourceTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
		recaps::add, Collections::emptyMap);
	private void fish() { manager.acquisitionMessage("You catch a trout.", true, false); }
	private void start() { manager.focusChanged(false, config); }
	private void end() { manager.focusChanged(true, config); }

	@Test public void sessionEndFlushesPendingIntoExistingItemModel()
	{
		start(); fish(); manager.acquisitionMessage("You get some oak logs.", false, true); end();
		assertEquals(Map.of(ItemID.RAW_TROUT, 1, ItemID.OAK_LOGS, 1), recaps.get(0).getItemGains());
	}
	@Test public void sessionEndClearsPendingMatchingEvidence()
	{
		start(); fish(); end(); start(); manager.inventoryChanged(Map.of(ItemID.RAW_TROUT, 1)); end();
		assertEquals(Map.of(ItemID.RAW_TROUT, 1), recaps.get(0).getItemGains());
		assertEquals(Map.of(ItemID.RAW_TROUT, 1), recaps.get(1).getItemGains());
	}
	@Test public void outsideSessionMessagesIgnored() { fish(); start(); end(); assertTrue(recaps.isEmpty()); }
	@Test public void logoutFinalizesPendingEvidence() { assertBoundary(GameState.LOGIN_SCREEN); }
	@Test public void worldHopFinalizesPendingEvidence() { assertBoundary(GameState.HOPPING); }
	@Test public void shutdownDiscardsPendingEvidence()
	{
		start(); fish(); manager.suspend(); manager.loggedIn(Collections.emptyMap(), 99);
		start(); end(); assertTrue(recaps.isEmpty());
	}
	private void assertBoundary(GameState state)
	{
		start(); fish(); manager.gameStateChanged(state); fish();
		manager.loggedIn(Collections.emptyMap(), 99); start(); end(); assertEquals(1, recaps.size());
		assertEquals(Map.of(ItemID.RAW_TROUT, 1), recaps.get(0).getItemGains());
		start(); manager.inventoryChanged(Map.of(ItemID.RAW_TROUT, 1)); end();
		assertEquals(Map.of(ItemID.RAW_TROUT, 1), recaps.get(1).getItemGains());
	}
	@Test public void hiddenGainsDoNotQualifyIdleCandidate()
	{
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		fish(); manager.manualInput(); assertTrue(recaps.isEmpty());
	}
	@Test public void fishingXpRetainsHiddenAcquisitions()
	{
		manager.baseline(Map.of(Skill.FISHING, 1000));
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		fish(); manager.statChanged(Skill.FISHING, 1050); manager.manualInput();
		assertEquals(Map.of(ItemID.RAW_TROUT, 1), recaps.get(0).getItemGains());
		assertEquals(Map.of(Skill.FISHING, 50L), recaps.get(0).getXpGained());
	}
}
