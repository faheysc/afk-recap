package com.afkrecap;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import com.afkrecap.ResourceAcquisitionMessages.Family;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;
import net.runelite.api.NPC;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
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
	name = "AFK Recap",
	description = "Records relevant events while RuneLite is unfocused and summarizes them when the player returns."
)
public class AfkRecapPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AfkRecapConfig config;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AfkRecapOverlay overlay;

	@Inject
	private AfkRecapController recapController;

	@Inject
	private AfkRecapHistory recapHistory;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private AfkRecapItemPresentation itemPresentation;

	// Panel and navigation state are accessed only on the Swing EDT.
	private AfkRecapPanel sidePanel;
	private NavigationButton navigationButton;
	private boolean navigationAdded;

	private volatile Object recapLifecycle;

	// Input callbacks are on the AWT thread; state mutations are queued on the client thread.
	private volatile AfkSessionManager sessions;

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
	AfkRecapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AfkRecapConfig.class);
	}

	@Override
	protected void startUp()
	{
		Object lifecycle = new Object();
		recapLifecycle = lifecycle;
		recapController.clear();
		recapHistory.setLimit(config.recentRecapLimit());
		AfkSessionManager started = new AfkSessionManager(System::nanoTime, Instant::now, recap ->
		{
			if (recapLifecycle == lifecycle)
			{
				AfkRecapItemPresentation.Display items = itemPresentation.prepare(recap);
				recapController.show(recap, config.showOverlay(), config.overlayDurationSeconds(), items);
				recapHistory.add(recap);
				refreshSidePanel();
			}
		}, this::inventorySnapshot);
		started.suspend();
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
		AfkSessionManager stopped = sessions;
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
		AfkSessionManager current = sessions;
		if (current != null)
		{
			clientThread.invoke(() ->
			{
				// Ignore callbacks belonging to a previous enable/disable cycle.
				if (sessions == current && client.getGameState() == GameState.LOGGED_IN)
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
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			current.focusChanged(event.isFocused(), config);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			if (!current.isReady())
			{
				// All login stat synchronization has been processed before this tick.
				current.loggedIn(experienceSnapshot(), client.getBoostedSkillLevel(Skill.PRAYER));
				return;
			}
			current.gameTick(config);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			current.statChanged(event.getSkill(), event.getXp());
			if (event.getSkill() == Skill.PRAYER)
			{
				current.prayerChanged(event.getBoostedLevel());
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN)
		{
			Hitsplat hit = event.getHitsplat();
			if (event.getActor() != null && event.getActor() == client.getLocalPlayer())
			{
				current.playerHitsplat(hit);
			}
			else if (event.getActor() instanceof NPC && hit != null)
			{
				current.npcDamage(event.getActor(), hit.getAmount(), hit.isMine(), hit.isOthers());
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN
			&& event.getActor() instanceof NPC)
		{
			current.npcDeath(event.getActor(), event.getActor().getName());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		AfkSessionManager current = sessions;
		if (current != null)
		{
			current.npcDespawned(event.getNpc());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		AfkSessionManager current = sessions;
		if (current != null && client.getGameState() == GameState.LOGGED_IN
			&& event.getContainerId() == InventoryID.INV)
		{
			ItemContainer inventory = event.getItemContainer();
			current.inventoryChanged(inventory == null ? null : InventoryGainTracker.totals(inventory.getItems()));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		AfkSessionManager current = sessions;
		if (current == null || client.getGameState() != GameState.LOGGED_IN || !current.isReady())
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			switch (message)
			{
				case "You empty your basket.":
				case "You empty as many logs as you can carry.":
				case "You empty your basket into the bank.":
					current.resourceTransfer(Family.LOG);
					break;
				case "You empty all of your containers into the bank.":
					current.resourceTransfer(Family.FISH);
					current.resourceTransfer(Family.LOG);
					break;
				default:
					break;
			}
		}
		if (ResourceAcquisitionMessages.acceptsChat(Family.LOG, event.getType()))
		{
			ResourceContainerState storage = resourceContainerState();
			current.acquisitionMessage(message,
				ResourceAcquisitionMessages.acceptsChat(Family.FISH, event.getType()) && storage.hiddenFish(),
				storage.hiddenLogs());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		AfkSessionManager current = sessions;
		if (current == null || client.getGameState() != GameState.LOGGED_IN || !current.isReady())
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		Widget widget = event.getWidget();
		Family container = widget == null ? null : ResourceAcquisitionMessages.container(widget.getItemId());
		if (container == null)
		{
			container = ResourceAcquisitionMessages.container(event.getItemId());
		}
		// Item-on-container and container-on-bank-stand transfers have different target widgets.
		switch (event.getMenuAction())
		{
			case WIDGET_TARGET_ON_WIDGET:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_NPC:
				Widget selected = client.getSelectedWidget();
				Family selectedContainer = selected == null ? null
					: ResourceAcquisitionMessages.container(selected.getItemId());
				if (container != null || selectedContainer != null)
				{
					current.resourceTransfer(container != null ? container : selectedContainer);
				}
				return;
			default:
				break;
		}
		if (container != null && (option.equals("fill") || option.startsWith("empty")))
		{
			current.resourceTransfer(container);
		}
		else if (widget != null && (option.startsWith("withdraw") || option.startsWith("deposit")
			|| option.equals("empty basket")))
		{
			// Bank buttons and the forestry equipment interface may not carry an item ID.
			ResourceContainerState storage = resourceContainerState();
			for (Family family : Family.values())
			{
				if (storage.hasContainer(family))
				{
					current.resourceTransfer(family);
				}
			}
		}
		// Check dialogs are intentionally not subscribed to: their contents are synchronization.
	}

	private ResourceContainerState resourceContainerState()
	{
		Set<Integer> items = new HashSet<>();
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container != null)
			{
				for (Item item : container.getItems())
				{
					if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
					{
						items.add(item.getId());
					}
				}
			}
		}
		return new ResourceContainerState(items);
	}

	private Map<Integer, Integer> inventorySnapshot()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory == null ? null : InventoryGainTracker.totals(inventory.getItems());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		AfkSessionManager current = sessions;
		if (current != null)
		{
			current.gameStateChanged(event.getGameState());
			if (event.getGameState() == GameState.LOGGED_IN)
			{
				baselineExperience(current);
			}
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
				sidePanel = new AfkRecapPanel(recapHistory, itemPresentation);
				navigationButton = NavigationButton.builder()
					.tooltip("AFK Recap")
					.icon(AfkRecapPanel.navigationIcon())
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

	private void baselineExperience(AfkSessionManager current)
	{
		current.baseline(experienceSnapshot());
		current.baselinePrayer(client.getBoostedSkillLevel(Skill.PRAYER));
	}

	private Map<Skill, Integer> experienceSnapshot()
	{
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			xp.put(skill, client.getSkillExperience(skill));
		}
		return xp;
	}
}
