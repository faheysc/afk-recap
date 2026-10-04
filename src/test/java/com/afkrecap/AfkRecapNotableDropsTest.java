package com.afkrecap;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapNotableDropsTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
		recaps::add, Collections::emptyMap);
	private final Object pile = new Object();
	private void focus() { manager.focusChanged(false, config); }
	private void drop() { manager.groundSpawn(pile, ItemID.ABYSSAL_WHIP, 1, TileItem.OWNERSHIP_SELF, 1600000, false, config); }
	private AfkRecapSession end() { manager.focusChanged(true, config); return recaps.get(recaps.size() - 1); }

	@Test public void focusWithOnlyNotableDropProducesRecap()
	{
		focus(); drop(); AfkRecapSession recap = end();
		assertTrue(recap.getItemGains().isEmpty()); assertTrue(recap.getXpGained().isEmpty());
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 1), recap.getNotableDrops());
		AfkRecapHistory history = new AfkRecapHistory(); assertTrue(history.add(recap));
	}
	@Test public void laterPickupIntentionallyAppearsInBothMaps()
	{
		focus(); drop(); manager.groundDespawn(pile); manager.inventoryChanged(Map.of(ItemID.ABYSSAL_WHIP, 1));
		AfkRecapSession recap = end();
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 1), recap.getNotableDrops());
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 1), recap.getItemGains());
	}
	@Test public void notableDropAloneNeverQualifiesIdle()
	{
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		drop(); assertTrue(manager.relevantSkills().isEmpty()); manager.manualInput(); assertTrue(recaps.isEmpty());
	}
	@Test public void laterCombatXpRetainsEarlierDrop()
	{
		manager.baseline(Map.of(Skill.ATTACK, 1000));
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		drop(); manager.statChanged(Skill.ATTACK, 1010); manager.manualInput();
		assertEquals(Map.of(ItemID.ABYSSAL_WHIP, 1), recaps.get(0).getNotableDrops());
	}
	@Test public void outsideSessionDropAndOldPileAdditionIgnored()
	{
		drop(); focus(); manager.groundQuantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		manager.focusChanged(true, config); assertTrue(recaps.isEmpty());
	}
	@Test public void sessionBoundaryClearsPileEvidence()
	{
		focus(); drop(); end(); focus();
		manager.groundQuantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		manager.focusChanged(true, config); assertEquals(1, recaps.size());
	}
	@Test public void logoutFinalizesDropThenClearsEvidence() { boundary(GameState.LOGIN_SCREEN); }
	@Test public void worldHopFinalizesDropThenClearsEvidence() { boundary(GameState.HOPPING); }
	private void boundary(GameState state)
	{
		focus(); drop(); manager.gameStateChanged(state); assertEquals(1, recaps.size());
		manager.loggedIn(Collections.emptyMap(), 99); focus();
		manager.groundQuantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		manager.focusChanged(true, config); assertEquals(1, recaps.size());
	}
	@Test public void shutdownDropsEvidenceWithoutPublishing()
	{
		focus(); drop(); manager.suspend(); manager.loggedIn(Collections.emptyMap(), 99); focus();
		manager.groundQuantityChanged(pile, ItemID.ABYSSAL_WHIP, 1, 2, TileItem.OWNERSHIP_SELF, 1600000, false, config);
		manager.focusChanged(true, config); assertTrue(recaps.isEmpty());
	}
	@Test public void overlayAndPanelUseSeparateNotableRowsAndOmitEmptySection() throws Exception
	{
		focus(); drop(); manager.groundSpawn(new Object(), ItemID.TRAIL_CLUE_HARD_MAP001, 1, TileItem.OWNERSHIP_SELF, 0, true, config);
		manager.inventoryChanged(Map.of(ItemID.ABYSSAL_WHIP, 1)); AfkRecapSession recap = end();
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id -> new AfkRecapItemPresentation.ItemDetails(
			id == ItemID.ABYSSAL_WHIP ? "Abyssal whip" : "Clue scroll (hard)", id == ItemID.ABYSSAL_WHIP ? 1600000 : 0));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap);
		AfkRecapController overlay = new AfkRecapController(() -> 0L); overlay.show(recap, true, 10, display);
		assertEquals(2, overlay.visiblePresentation(true).getNotableDropRows().size());
		assertEquals(1, overlay.visiblePresentation(true).getItemRows().size());
		assertEquals("1,600,000 gp", display.getTotalValue()); // Drops never inflate inventory value.
		assertTrue(display.getNotableRows().stream().anyMatch(row -> row.getName().equals("Clue scroll (hard)") && row.getValue() == null));
		assertTrue(display.getNotableRows().stream().anyMatch(row -> row.getQuantityAndValue().equals("1  1,600,000 gp")));
		AfkRecapHistory history = new AfkRecapHistory(); history.add(recap);
		SwingUtilities.invokeAndWait(() ->
		{
			AfkRecapPanel panel = new AfkRecapPanel(history, presenter); List<String> labels = new ArrayList<>(); labels(panel, labels);
			assertTrue(labels.contains("Notable drops")); assertTrue(labels.contains("Items gained"));
			assertTrue(labels.contains("Clue scroll (hard)")); assertTrue(labels.contains("1  1,600,000 gp"));
			history.clear();
			AfkRecapSession xpOnly = new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH, 1, Instant.EPOCH,
				600, Map.of(Skill.ATTACK, 1L), Collections.emptySet());
			history.add(xpOnly); panel.refresh(); labels.clear(); labels(panel, labels);
			assertFalse(labels.contains("Notable drops"));
			overlay.show(xpOnly, true, 10, presenter.prepare(xpOnly));
			assertTrue(overlay.visiblePresentation(true).getNotableDropRows().isEmpty());
		});
	}
	@Test(expected = UnsupportedOperationException.class) public void completedDropsAreImmutable()
	{
		focus(); drop(); end().getNotableDrops().put(ItemID.COINS, 1);
	}
	private static void labels(Container container, List<String> result)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel) { result.add(((JLabel) child).getText()); }
			if (child instanceof Container) { labels((Container) child, result); }
		}
	}
}
