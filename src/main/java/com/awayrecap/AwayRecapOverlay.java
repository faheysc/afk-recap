package com.awayrecap;

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
public final class AwayRecapOverlay extends OverlayPanel
{
	private final AwayRecapConfig config;
	private final AwayRecapController controller;

	@Inject
	AwayRecapOverlay(AwayRecapPlugin plugin, AwayRecapConfig config, AwayRecapController controller)
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
		AwayRecapController.Presentation recap = controller.visiblePresentation(config.showOverlay());
		if (recap == null)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Away Recap").color(Color.YELLOW).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Away:").right(recap.getDuration()).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Trigger:").right(recap.getTrigger()).build());
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("XP gained").build());
		for (AwayRecapController.XpRow row : recap.getXpRows())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(row.getSkill()).right(row.getGained()).build());
		}
		return super.render(graphics);
	}
}
