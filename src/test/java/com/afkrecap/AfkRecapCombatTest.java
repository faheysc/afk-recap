package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapCombatTest
{
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, Instant::now,
		recaps::add, () -> Collections.emptyMap());

	private void start()
	{
		manager.baseline(Collections.singletonMap(Skill.ATTACK, 100));
		manager.baselinePrayer(100);
		manager.focusChanged(false, config);
	}

	private AfkRecapSession end()
	{
		manager.statChanged(Skill.ATTACK, 110);
		manager.focusChanged(true, config);
		assertEquals(1, recaps.size());
		return recaps.get(0);
	}

	private void kill(String name)
	{
		Object npc = new Object();
		manager.npcDamage(npc, 10, true, false);
		manager.npcDeath(npc, name);
	}

	@Test public void cumulativePrayerDecreases()
	{
		start();
		manager.prayerChanged(90);
		manager.prayerChanged(80);
		assertEquals(20, end().getPrayerUsed());
	}

	@Test public void restorationNeverSubtracts()
	{
		start();
		manager.prayerChanged(90);
		manager.prayerChanged(115);
		assertEquals(10, end().getPrayerUsed());
	}

	@Test public void multipleDrainRestoreCycles()
	{
		start();
		manager.prayerChanged(90);
		manager.prayerChanged(115);
		manager.prayerChanged(100);
		manager.prayerChanged(120);
		manager.prayerChanged(120);
		manager.prayerChanged(110);
		assertEquals(35, end().getPrayerUsed());
	}

	@Test public void zeroPrayerAndEmptyKillsOmittedFromPresentation()
	{
		start();
		manager.prayerChanged(115);
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(end(), true, 10);
		assertNull(controller.visiblePresentation(true).getPrayerUsed());
		assertTrue(controller.visiblePresentation(true).getNpcKills().isEmpty());
	}

	@Test public void oneAttributedKill()
	{
		start();
		kill("Abyssal demon");
		assertEquals(Collections.singletonMap("Abyssal demon", 1), end().getNpcKills());
	}

	@Test public void multipleKillsOfSameNpc()
	{
		start();
		kill("Abyssal demon");
		kill("Abyssal demon");
		assertEquals(Collections.singletonMap("Abyssal demon", 2), end().getNpcKills());
	}

	@Test public void multipleNpcTypes()
	{
		start();
		kill("Abyssal demon");
		kill("Cow");
		AfkRecapSession recap = end();
		assertEquals(2, recap.getNpcKills().size());
		assertEquals(Integer.valueOf(1), recap.getNpcKills().get("Cow"));
	}

	@Test public void unrelatedAndOtherPlayerDeathsExcluded()
	{
		start();
		manager.npcDeath(new Object(), "Cow");
		Object npc = new Object();
		manager.npcDamage(npc, 10, false, true);
		manager.npcDeath(npc, "Cow");
		assertTrue(end().getNpcKills().isEmpty());
	}

	@Test public void contestedKillExcludedEvenWhenOwnHitIsLast()
	{
		start();
		Object npc = new Object();
		manager.npcDamage(npc, 10, false, true);
		manager.npcDamage(npc, 10, true, false);
		manager.npcDeath(npc, "Cow");
		assertTrue(end().getNpcKills().isEmpty());
	}

	@Test public void zeroDamageIsInsufficientAndDuplicateDeathCountedOnce()
	{
		start();
		Object blocked = new Object();
		manager.npcDamage(blocked, 0, true, false);
		manager.npcDeath(blocked, "Cow");
		Object npc = new Object();
		manager.npcDamage(npc, 1, true, false);
		manager.npcDeath(npc, "Cow");
		manager.npcDeath(npc, "Cow");
		assertEquals(Collections.singletonMap("Cow", 1), end().getNpcKills());
	}

	@Test public void despawnClearsEvidence()
	{
		start();
		Object npc = new Object();
		manager.npcDamage(npc, 1, true, false);
		manager.npcDespawned(npc);
		manager.npcDeath(npc, "Cow");
		assertTrue(end().getNpcKills().isEmpty());
	}

	@Test public void preSessionDamageAndPrayerExcluded()
	{
		Object npc = new Object();
		manager.npcDamage(npc, 1, true, false);
		manager.prayerChanged(50);
		start();
		manager.npcDeath(npc, "Cow");
		AfkRecapSession recap = end();
		assertTrue(recap.getNpcKills().isEmpty());
		assertEquals(0, recap.getPrayerUsed());
	}

	@Test public void metricsAloneNeverMakeIdleRelevant()
	{
		manager.baselinePrayer(100);
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		kill("Cow");
		manager.prayerChanged(90);
		assertTrue(manager.relevantSkills().isEmpty());
		manager.manualInput();
		assertTrue(recaps.isEmpty());
	}

	@Test public void relevantCombatRecapContainsAllMetrics()
	{
		manager.baseline(Collections.singletonMap(Skill.ATTACK, 100));
		manager.baselinePrayer(100);
		for (int i = 0; i < 10; i++)
		{
			manager.gameTick(config);
		}
		manager.statChanged(Skill.ATTACK, 125);
		kill("Abyssal demon");
		manager.prayerChanged(57);
		manager.inventoryChanged(Collections.singletonMap(ItemID.COINS, 100));
		manager.manualInput();
		AfkRecapSession recap = recaps.get(0);
		assertEquals(Long.valueOf(25), recap.getXpGained().get(Skill.ATTACK));
		assertEquals(Integer.valueOf(1), recap.getNpcKills().get("Abyssal demon"));
		assertEquals(43, recap.getPrayerUsed());
		assertEquals(Integer.valueOf(100), recap.getItemGains().get(ItemID.COINS));
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(recap, true, 10);
		assertEquals("43", controller.visiblePresentation(true).getPrayerUsed());
		assertEquals(recap.getNpcKills(), controller.visiblePresentation(true).getNpcKills());
		AfkRecapHistory history = new AfkRecapHistory();
		history.add(recap);
		assertSame(recap, history.snapshot().get(0));
	}

	@Test public void focusPrayerOnlyRecapAndNextSessionIndependent()
	{
		start();
		manager.prayerChanged(90);
		manager.focusChanged(true, config);
		assertEquals(10, recaps.get(0).getPrayerUsed());
		manager.prayerChanged(80);
		manager.focusChanged(false, config);
		manager.prayerChanged(75);
		manager.focusChanged(true, config);
		assertEquals(5, recaps.get(1).getPrayerUsed());
	}

	@Test public void missingPrayerBaselineAndExplicitSynchronizationAreNotUsage()
	{
		manager.focusChanged(false, config);
		manager.prayerChanged(100);
		manager.baselinePrayer(50);
		manager.prayerChanged(45);
		assertEquals(5, end().getPrayerUsed());
	}

	@Test public void panelShowsMetricsAndOmitsEmptySections() throws Exception
	{
		start();
		kill("Cow");
		manager.prayerChanged(90);
		AfkRecapSession recap = end();
		AfkRecapHistory history = new AfkRecapHistory();
		history.add(recap);
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			AfkRecapPanel panel = new AfkRecapPanel(history, new AfkRecapItemPresentation(id -> null));
			List<String> labels = new ArrayList<>();
			labels(panel, labels);
			assertTrue(labels.contains("Kills"));
			assertTrue(labels.contains("Cow"));
			assertTrue(labels.contains("Prayer used: 10"));
			history.clear();
			history.add(new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
				1, Instant.EPOCH, 600, Collections.singletonMap(Skill.ATTACK, 1L),
				Collections.emptySet()));
			panel.refresh();
			labels.clear();
			labels(panel, labels);
			assertFalse(labels.contains("Kills"));
			assertFalse(labels.stream().anyMatch(label -> label.startsWith("Prayer used:")));
		});
	}

	private static void labels(java.awt.Container container, List<String> labels)
	{
		for (java.awt.Component component : container.getComponents())
		{
			if (component instanceof javax.swing.JLabel)
			{
				labels.add(((javax.swing.JLabel) component).getText());
			}
			if (component instanceof java.awt.Container)
			{
				labels((java.awt.Container) component, labels);
			}
		}
	}

	@Test(expected = UnsupportedOperationException.class)
	public void killSnapshotImmutable()
	{
		start();
		kill("Cow");
		end().getNpcKills().put("Cow", 99);
	}
}
