package com.awayrecap;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;

public final class AwayRecapPanel extends PluginPanel
{
	private final AwayRecapHistory history;
	private final JPanel entries = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JButton clearHistory = new JButton("Clear History");

	AwayRecapPanel(AwayRecapHistory history)
	{
		this.history = history;
		setLayout(new BorderLayout(0, 8));
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);
		JLabel title = new JLabel("Away Recap");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(title, BorderLayout.NORTH);
		header.add(clearHistory, BorderLayout.SOUTH);
		clearHistory.addActionListener(event ->
		{
			history.clear();
			refresh();
		});
		entries.setOpaque(false);
		add(header, BorderLayout.NORTH);
		add(entries, BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		assert SwingUtilities.isEventDispatchThread();
		List<AwayRecapSession> recaps = history.snapshot();
		entries.removeAll();
		clearHistory.setEnabled(!recaps.isEmpty());
		if (recaps.isEmpty())
		{
			JLabel empty = new JLabel("No completed recaps yet.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			entries.add(empty);
		}
		else
		{
			for (AwayRecapSession recap : recaps)
			{
				entries.add(entry(recap));
			}
		}
		entries.revalidate();
		entries.repaint();
	}

	private JPanel entry(AwayRecapSession recap)
	{
		JPanel card = new JPanel(new BorderLayout(0, 5));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		JLabel heading = new JLabel(AwayRecapPanelPresentation.header(recap, ZoneId.systemDefault()));
		heading.setForeground(ColorScheme.TEXT_COLOR);
		card.add(heading, BorderLayout.NORTH);
		JPanel xpRows = new JPanel(new GridLayout(0, 1, 0, 2));
		xpRows.setOpaque(false);
		recap.getXpGained().forEach((skill, gained) ->
		{
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(new JLabel(skill.getName()), BorderLayout.WEST);
			row.add(new JLabel(AwayRecapPanelPresentation.xp(gained)), BorderLayout.EAST);
			xpRows.add(row);
		});
		card.add(xpRows, BorderLayout.CENTER);
		return card;
	}

	static BufferedImage navigationIcon()
	{
		// Small code-drawn clock icon; no file I/O or large raster asset.
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(ColorScheme.BRAND_ORANGE);
			graphics.drawOval(1, 1, 13, 13);
			graphics.drawLine(8, 4, 8, 8);
			graphics.drawLine(8, 8, 11, 10);
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}
}
