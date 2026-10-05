package com.afkrecap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public final class AfkRecapOverlay extends OverlayPanel
{
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
		if (!recap.getXpRows().isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("XP gained").build());
		}
		for (AfkRecapController.XpRow row : recap.getXpRows())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(row.getSkill()).right(row.getGained()).build());
		}
		if (!recap.getMissedRandomEvents().isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Missed random events").build());
			recap.getMissedRandomEvents().forEach((name, count) -> panelComponent.getChildren().add(
				LineComponent.builder().left(name).right(count.toString()).build()));
		}

		if (!recap.getNpcKills().isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Kills").build());
			recap.getNpcKills().forEach((name, count) -> panelComponent.getChildren().add(
				LineComponent.builder().left(name).right(count.toString()).build()));
		}
		if (recap.getPrayerUsed() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Prayer used:").right(recap.getPrayerUsed()).build());
		}

		if (recap.getDamageTaken() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Damage taken:").right(recap.getDamageTaken()).build());
		}

		if (!recap.getNotableDropRows().isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Notable drops").build());
			for (AfkRecapItemPresentation.ItemRow row : recap.getNotableDropRows())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(row.getName()).right(row.getQuantityAndValue()).build());
			}
		}

		if (!recap.getItemRows().isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Items gained").build());
			for (AfkRecapItemPresentation.ItemRow row : recap.getItemRows())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(row.getName()).right(row.getQuantityAndValue()).build());
			}
			if (recap.getTotalValue() != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Total value:").right(recap.getTotalValue()).build());
			}
		}
		return super.render(graphics);
	}
}
