package com.awayrecap;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Away Recap",
	description = "Records relevant events while RuneLite is unfocused and summarizes them when the player returns."
)
public class AwayRecapPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AwayRecapConfig config;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AwayRecapOverlay overlay;

	@Inject
	private AwayRecapController recapController;

	@Inject
	private AwayRecapHistory recapHistory;

	@Inject
	private ClientToolbar clientToolbar;

	// Panel and navigation state are accessed only on the Swing EDT.
	private AwayRecapPanel sidePanel;
	private NavigationButton navigationButton;
	private boolean navigationAdded;

	private volatile Object recapLifecycle;

	// Input callbacks are on the AWT thread; state mutations are queued on the client thread.
	private volatile AwaySessionManager sessions;

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			manualInput();
			return event;
		}
	};

	private final KeyListener keyListener = new KeyListener()
	{
		@Override
		public void keyPressed(KeyEvent event)
		{
			manualInput();
		}

		@Override
		public void keyReleased(KeyEvent event)
		{
		}

		@Override
		public void keyTyped(KeyEvent event)
		{
		}
	};

	@Provides
	AwayRecapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AwayRecapConfig.class);
	}

	@Override
	protected void startUp()
	{
		Object lifecycle = new Object();
		recapLifecycle = lifecycle;
		recapController.clear();
		recapHistory.setLimit(config.recentRecapLimit());
		AwaySessionManager started = new AwaySessionManager(System::nanoTime, Instant::now, recap ->
		{
			if (recapLifecycle == lifecycle)
			{
				recapController.show(recap, config.showOverlay(), config.overlayDurationSeconds());
				recapHistory.add(recap);
				refreshSidePanel();
			}
		});
		sessions = started;
		clientThread.invoke(() ->
		{
			if (sessions == started && client.getGameState() == GameState.LOGGED_IN)
			{
				baselineExperience(started);
			}
		});
		mouseManager.registerMouseListener(mouseListener);
		keyManager.registerKeyListener(keyListener);
		overlayManager.add(overlay);
		updateSidePanel();
	}

	@Override
	protected void shutDown()
	{
		recapLifecycle = null;
		updateSidePanel();
		overlayManager.remove(overlay);
		recapController.clear();
		AwaySessionManager stopped = sessions;
		sessions = null;
		mouseManager.unregisterMouseListener(mouseListener);
		keyManager.unregisterKeyListener(keyListener);
		if (stopped != null)
		{
			clientThread.invoke(() -> stopped.reset("plugin shutdown"));
		}
	}

	private void manualInput()
	{
		AwaySessionManager current = sessions;
		if (current != null)
		{
			clientThread.invoke(() ->
			{
				// Ignore callbacks belonging to a previous enable/disable cycle.
				if (sessions == current)
				{
					current.manualInput();
				}
			});
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		log.debug("RuneLite {} focus", event.isFocused() ? "gained" : "lost");
		AwaySessionManager current = sessions;
		if (current != null)
		{
			current.focusChanged(event.isFocused(), config);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		AwaySessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			current.gameTick(config);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		AwaySessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			current.statChanged(event.getSkill(), event.getXp());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		AwaySessionManager current = sessions;
		if (current != null && event.getGameState() == GameState.LOGGED_IN)
		{
			baselineExperience(current);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("away-recap".equals(event.getGroup()) && "showOverlay".equals(event.getKey())
			&& !config.showOverlay())
		{
			recapController.clear();
		}
		if ("away-recap".equals(event.getGroup()))
		{
			if ("recentRecapLimit".equals(event.getKey()))
			{
				recapHistory.setLimit(config.recentRecapLimit());
				refreshSidePanel();
			}
			else if ("enableSidePanel".equals(event.getKey()))
			{
				updateSidePanel();
			}
		}
	}

	private void refreshSidePanel()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (recapLifecycle != null && config.enableSidePanel() && sidePanel != null)
			{
				sidePanel.refresh();
			}
		});
	}

	private void updateSidePanel()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (recapLifecycle == null || !config.enableSidePanel())
			{
				if (navigationAdded)
				{
					clientToolbar.removeNavigation(navigationButton);
					navigationAdded = false;
				}
				return;
			}
			if (sidePanel == null)
			{
				sidePanel = new AwayRecapPanel(recapHistory);
				navigationButton = NavigationButton.builder()
					.tooltip("Away Recap")
					.icon(AwayRecapPanel.navigationIcon())
					.priority(6)
					.panel(sidePanel)
					.build();
			}
			sidePanel.refresh();
			if (!navigationAdded)
			{
				clientToolbar.addNavigation(navigationButton);
				navigationAdded = true;
			}
		});
	}

	private void baselineExperience(AwaySessionManager current)
	{
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			xp.put(skill, client.getSkillExperience(skill));
		}
		current.baseline(xp);
	}
}
