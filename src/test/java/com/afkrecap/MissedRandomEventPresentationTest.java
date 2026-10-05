package com.afkrecap;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class MissedRandomEventPresentationTest
{
	private AfkRecapSession recap(Map<String, Integer> misses)
	{
		return new AfkRecapSession(AfkSessionTrigger.IDLE, 0, Instant.EPOCH, 2, Instant.EPOCH,
			1200, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, AfkSessionEndReason.MANUAL_INPUT, Collections.emptyMap(), misses);
	}

	@Test
	public void missOnlyRecapIsAcceptedAndFormattedByOverlay()
	{
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(recap(Collections.singletonMap("Genie", 2)), true, 10);
		assertNotNull(controller.visiblePresentation(true));
		AfkRecapOverlay overlay = new AfkRecapOverlay(new AfkRecapPlugin(), new AfkRecapConfig() {}, controller);
		overlay.setClearChildren(false);
		Graphics2D graphics = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { assertNotNull(overlay.render(graphics)); }
		finally { graphics.dispose(); }
		assertEquals(Collections.singletonMap("Genie", 2), controller.visiblePresentation(true).getMissedRandomEvents());
		assertEquals(2, overlay.getPanelComponent().getChildren().stream()
			.filter(component -> component instanceof TitleComponent).count());
		assertEquals(3, overlay.getPanelComponent().getChildren().stream()
			.filter(component -> component instanceof LineComponent).count());
	}

	@Test
	public void emptyMissSectionIsOmittedFromOverlay()
	{
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		AfkRecapSession focus = new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH,
			2, Instant.EPOCH, 1200, Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), Collections.emptyMap(), 1);
		controller.show(focus, true, 10);
		AfkRecapOverlay overlay = new AfkRecapOverlay(new AfkRecapPlugin(), new AfkRecapConfig() {}, controller);
		overlay.setClearChildren(false);
		Graphics2D graphics = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { overlay.render(graphics); }
		finally { graphics.dispose(); }
		assertTrue(controller.visiblePresentation(true).getMissedRandomEvents().isEmpty());
		assertEquals(1, overlay.getPanelComponent().getChildren().stream()
			.filter(component -> component instanceof TitleComponent).count());
	}

	private void labels(Component component, List<String> labels)
	{
		if (component instanceof JLabel) { labels.add(((JLabel) component).getText()); }
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents()) { labels(child, labels); }
		}
	}

	@Test
	public void historyPanelShowsMissNamesAndCounts() throws Exception
	{
		AfkRecapHistory history = new AfkRecapHistory();
		assertTrue(history.add(recap(Collections.singletonMap("Drunken dwarf", 3))));
		List<String> labels = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			AfkRecapPanel panel = new AfkRecapPanel(history, new AfkRecapItemPresentation(id -> null));
			labels(panel, labels);
		});
		assertTrue(labels.contains("Missed random events"));
		assertTrue(labels.contains("Drunken dwarf"));
		assertTrue(labels.contains("3"));
	}

	@Test
	public void emptyMissSectionIsOmittedFromHistoryPanel() throws Exception
	{
		AfkRecapHistory history = new AfkRecapHistory();
		history.add(new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH, 2, Instant.EPOCH,
			1200, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), 1));
		List<String> labels = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> labels(new AfkRecapPanel(history,
			new AfkRecapItemPresentation(id -> null)), labels));
		assertFalse(labels.contains("Missed random events"));
	}
	@Test
	public void largeOverlayIsBoundedWithoutTruncatingHistoryData()
	{
		Map<String, Integer> misses = new TreeMap<>();
		for (int i = 0; i < 100; i++) { misses.put("Event " + i, 1); }
		AfkRecapSession recap = recap(misses);
		AfkRecapController controller = new AfkRecapController(() -> 0L);
		controller.show(recap, true, 10);
		AfkRecapOverlay overlay = new AfkRecapOverlay(new AfkRecapPlugin(), new AfkRecapConfig() {}, controller);
		overlay.setClearChildren(false);
		Graphics2D graphics = new BufferedImage(400, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { assertNotNull(overlay.render(graphics)); }
		finally { graphics.dispose(); }
		assertEquals(AfkRecapOverlay.MAX_ROWS, overlay.getPanelComponent().getChildren().size());
		assertEquals(100, recap.getMissedRandomEvents().size());
		AfkRecapHistory history = new AfkRecapHistory();
		assertTrue(history.add(recap));
		assertEquals(100, history.snapshot().get(0).getMissedRandomEvents().size());
	}

}
