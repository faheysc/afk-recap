package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MissedRandomEventTest
{
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L,
		() -> Instant.EPOCH, recaps::add, Collections::emptyMap);
	private final AfkRecapConfig config = new AfkRecapConfig()
	{
		@Override public int idleGameTicks() { return 1; }
		@Override public boolean idleFishing() { return false; }
		@Override public boolean idleMining() { return false; }
		@Override public boolean idleWoodcutting() { return false; }
		@Override public boolean idleCombat() { return false; }
		@Override public boolean idleSlayer() { return false; }
		@Override public boolean idleSailingSalvaging() { return false; }
		@Override public boolean idleSailingSorting() { return false; }
	};

	private Object targeted(int id, String name)
	{
		Object npc = new Object();
		manager.randomEventTargeted(npc, id, name, true, false);
		return npc;
	}

	private void miss(Object npc)
	{
		manager.randomEventDespawned(npc);
		manager.gameTick(config);
	}

	private AfkRecapSession finish()
	{
		manager.manualInput();
		assertEquals(1, recaps.size());
		return recaps.get(0);
	}

	@Test
	public void localTargetedRandomProducesMissOnlyAfterDespawn()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.gameTick(config);
		assertTrue(recaps.isEmpty());
		miss(npc);
		assertEquals(Collections.singletonMap("Genie", 1), finish().getMissedRandomEvents());
	}

	@Test
	public void anotherPlayersRandomIsIgnored()
	{
		manager.gameTick(config);
		Object npc = new Object();
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", false, false);
		miss(npc);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void nearbyRandomWithoutOwnershipIsIgnored()
	{
		manager.gameTick(config);
		miss(new Object());
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void talkingToOthersRandomCannotCreateOwnership()
	{
		manager.gameTick(config);
		Object npc = new Object();
		manager.randomEventHandled(npc);
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", true, true);
		miss(npc);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void unrelatedNpcTargetingLocalIsIgnored()
	{
		manager.gameTick(config);
		miss(targeted(-1, "Unrelated NPC"));
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void completedInteractionIsHandledAndDoesNotQualifyIdle()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventHandled(npc); // Local player's InteractingChanged / Talk-to evidence.
		miss(npc);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void dismissedEventIsHandledAndDoesNotQualifyIdle()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_DWARF, "Drunken dwarf");
		manager.randomEventHandled(npc); // Dismiss menu entry for this exact NPC.
		miss(npc);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void duplicateTargetDespawnAndLateSignalsCountOnce()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", true, false);
		manager.randomEventDespawned(npc);
		manager.randomEventDespawned(npc);
		manager.gameTick(config);
		manager.randomEventTargeted(npc, NpcID.MACRO_GENI, "Genie", true, false);
		miss(npc);
		assertEquals(Collections.singletonMap("Genie", 1), finish().getMissedRandomEvents());
	}

	@Test
	public void repeatedSameTypeAggregatesIncludingUnderwaterVariant()
	{
		manager.gameTick(config);
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		miss(targeted(NpcID.MACRO_GENI_UNDERWATER, "Genie"));
		assertEquals(Collections.singletonMap("Genie", 2), finish().getMissedRandomEvents());
	}

	@Test
	public void multipleTypesRemainSeparate()
	{
		manager.gameTick(config);
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		miss(targeted(NpcID.MACRO_DWARF, "Drunken dwarf"));
		Map<String, Integer> expected = new HashMap<>();
		expected.put("Genie", 1);
		expected.put("Drunken dwarf", 1);
		assertEquals(expected, finish().getMissedRandomEvents());
	}

	@Test
	public void missedRandomAloneQualifiesIdleAndKeepsEarlierData()
	{
		manager.baseline(Collections.singletonMap(Skill.SAILING, 1000));
		manager.gameTick(config);
		manager.statChanged(Skill.SAILING, 1050);
		manager.inventoryChanged(Collections.singletonMap(ItemID.COINS, 10));
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		AfkRecapSession recap = finish();
		assertTrue(recap.isIdleRelevant());
		assertTrue(recap.getRelevantIdleSkills().isEmpty());
		assertEquals(Long.valueOf(50), recap.getXpGained().get(Skill.SAILING));
		assertEquals(Integer.valueOf(10), recap.getItemGains().get(ItemID.COINS));
		assertTrue(new AfkRecapHistory().add(recap));
	}

	@Test
	public void disabledTrackingIgnoresAllSignals()
	{
		AfkRecapConfig disabled = new AfkRecapConfig()
		{
			@Override public boolean trackMissedRandomEvents() { return false; }
			@Override public int idleGameTicks() { return 1; }
		};
		manager.gameTick(disabled);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventDespawned(npc);
		manager.gameTick(disabled);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void disablingMidSessionClearsEvidenceAndMisses()
	{
		manager.gameTick(config);
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		Object active = targeted(NpcID.MACRO_DWARF, "Drunken dwarf");
		manager.configureRandomEvents(false);
		manager.randomEventDespawned(active);
		manager.configureRandomEvents(true);
		manager.gameTick(config);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void focusCanRecordMissWithoutAnyXp()
	{
		manager.focusChanged(false, config);
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		manager.focusChanged(true, config);
		assertEquals(1, recaps.size());
		assertEquals(AfkSessionTrigger.FOCUS, recaps.get(0).getTrigger());
		assertEquals(Collections.singletonMap("Genie", 1), recaps.get(0).getMissedRandomEvents());
	}

	@Test
	public void handledFocusEventDoesNotAddMiss()
	{
		manager.focusChanged(false, config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventHandled(npc);
		miss(npc);
		manager.focusChanged(true, config);
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void activeAndPendingEventsAreNotMissesOnLogoutDisconnectOrHop()
	{
		for (GameState state : new GameState[] { GameState.LOGIN_SCREEN, GameState.CONNECTION_LOST, GameState.HOPPING })
		{
			for (boolean despawnFirst : new boolean[] { false, true })
			{
				manager.loggedIn(Collections.emptyMap(), 99);
				manager.gameTick(config);
				Object npc = targeted(NpcID.MACRO_GENI, "Genie");
				if (despawnFirst) { manager.randomEventDespawned(npc); }
				manager.gameStateChanged(state);
				manager.randomEventDespawned(npc);
				manager.loggedIn(Collections.emptyMap(), 99);
				manager.gameTick(config);
				manager.randomEventDespawned(npc);
				manager.gameTick(config);
				manager.manualInput();
				assertTrue(recaps.isEmpty());
			}
		}
	}

	@Test
	public void alreadyConfirmedMissSurvivesLogoutFinalization()
	{
		manager.gameTick(config);
		miss(targeted(NpcID.MACRO_GENI, "Genie"));
		manager.gameStateChanged(GameState.CONNECTION_LOST);
		assertEquals(1, recaps.size());
		assertEquals(Collections.singletonMap("Genie", 1), recaps.get(0).getMissedRandomEvents());
	}

	@Test
	public void sessionCompletionAndDiscardClearActiveEvidence()
	{
		manager.gameTick(config);
		Object first = targeted(NpcID.MACRO_GENI, "Genie");
		manager.manualInput(); // Discarded candidate.
		manager.gameTick(config);
		miss(first);
		assertTrue(recaps.isEmpty());
		Object second = targeted(NpcID.MACRO_GENI, "Genie");
		miss(second);
		manager.manualInput();
		manager.gameTick(config);
		miss(second);
		manager.manualInput();
		assertEquals(1, recaps.size());
	}

	@Test
	public void shutdownClearsActiveEvidenceWithoutPublishing()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.suspend();
		manager.randomEventDespawned(npc);
		manager.loggedIn(Collections.emptyMap(), 99);
		manager.gameTick(config);
		miss(npc);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void handlingBeforeDespawnConfirmationSuppressesMiss()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventDespawned(npc);
		manager.randomEventHandled(npc);
		manager.gameTick(config);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void missDataIsDefensivelyCopiedAndImmutable()
	{
		Map<String, Integer> misses = new HashMap<>();
		misses.put("Genie", 2);
		AfkRecapSession recap = new AfkRecapSession(AfkSessionTrigger.IDLE, 0, Instant.EPOCH,
			1, Instant.EPOCH, 600, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, AfkSessionEndReason.MANUAL_INPUT, Collections.emptyMap(), misses);
		misses.clear();
		assertEquals(Collections.singletonMap("Genie", 2), recap.getMissedRandomEvents());
		try
		{
			recap.getMissedRandomEvents().put("Genie", 10);
			fail("Expected immutable map");
		}
		catch (UnsupportedOperationException expected) { }
	}

	@Test
	public void unconfirmedDespawnAtNormalSessionBoundaryIsOmitted()
	{
		manager.gameTick(config);
		Object npc = targeted(NpcID.MACRO_GENI, "Genie");
		manager.randomEventDespawned(npc);
		manager.manualInput();
		manager.gameTick(config);
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void missingNpcOrNameCannotCreateAttributedMiss()
	{
		manager.gameTick(config);
		manager.randomEventTargeted(null, NpcID.MACRO_GENI, "Genie", true, false);
		miss(null);
		miss(targeted(NpcID.MACRO_GENI, null));
		miss(targeted(NpcID.MACRO_GENI, " "));
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test
	public void trackingIsEnabledByDefault()
	{
		assertTrue(config.trackMissedRandomEvents());
	}
}
