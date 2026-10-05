package com.afkrecap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public final class AfkRecapOverlay extends OverlayPanel
{
	// Reserve one row for a history hint; never discard collected recap data.
	static final int MAX_ROWS = 24;
	private final AfkRecapConfig config;
	private final AfkRecapController controller;

	@Inject
	AfkRecapOverlay(AfkRecapPlugin plugin, AfkRecapConfig config, AfkRecapController controller)
	{
		super(plugin);
		this.config = config;
		this.controller = controller;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(220, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		AfkRecapController.Presentation recap = controller.visiblePresentation(config.showOverlay());
		if (recap == null)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("AFK Recap").color(Color.YELLOW).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Away:").right(recap.getDuration()).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Trigger:").right(recap.getTrigger()).build());
		if (!recap.getXpRows().isEmpty() && hasRowSpace())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("XP gained").build());
		}
		for (AfkRecapController.XpRow row : recap.getXpRows())
		{
			if (!hasRowSpace())
			{
				break;
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(row.getSkill()).right(row.getGained()).build());
		}
		if (!recap.getMissedRandomEvents().isEmpty() && hasRowSpace())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Missed random events").build());
			for (Map.Entry<String, Integer> entry : recap.getMissedRandomEvents().entrySet())
			{
				if (!hasRowSpace())
				{
					break;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left(entry.getKey()).right(entry.getValue().toString()).build());
			}
		}

		if (!recap.getNpcKills().isEmpty() && hasRowSpace())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Kills").build());
			for (Map.Entry<String, Integer> entry : recap.getNpcKills().entrySet())
			{
				if (!hasRowSpace())
				{
					break;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left(entry.getKey()).right(entry.getValue().toString()).build());
			}
		}
		if (recap.getPrayerUsed() != null && hasRowSpace())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Prayer used:").right(recap.getPrayerUsed()).build());
		}

		if (recap.getDamageTaken() != null && hasRowSpace())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Damage taken:").right(recap.getDamageTaken()).build());
		}

		if (!recap.getNotableDropRows().isEmpty() && hasRowSpace())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Notable drops").build());
			for (AfkRecapItemPresentation.ItemRow row : recap.getNotableDropRows())
			{
				if (!hasRowSpace())
				{
					break;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left(row.getName()).right(row.getQuantityAndValue()).build());
			}
		}

		if (!recap.getItemRows().isEmpty() && hasRowSpace())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Items gained").build());
			for (AfkRecapItemPresentation.ItemRow row : recap.getItemRows())
			{
				if (!hasRowSpace())
				{
					break;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left(row.getName()).right(row.getQuantityAndValue()).build());
			}
			if (recap.getTotalValue() != null && hasRowSpace())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Total value:").right(recap.getTotalValue()).build());
			}
		}
		int totalRows = 3 + sectionRows(recap.getXpRows().size())
			+ sectionRows(recap.getMissedRandomEvents().size()) + sectionRows(recap.getNpcKills().size())
			+ sectionRows(recap.getNotableDropRows().size()) + sectionRows(recap.getItemRows().size())
			+ (recap.getPrayerUsed() == null ? 0 : 1) + (recap.getDamageTaken() == null ? 0 : 1)
			+ (recap.getItemRows().isEmpty() || recap.getTotalValue() == null ? 0 : 1);
		if (totalRows > panelComponent.getChildren().size())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("More details in history").build());
		}
		return super.render(graphics);
	}
	private boolean hasRowSpace()
	{
		return panelComponent.getChildren().size() < MAX_ROWS - 1;
	}

	private static int sectionRows(int size)
	{
		return size == 0 ? 0 : size + 1;
	}

}
