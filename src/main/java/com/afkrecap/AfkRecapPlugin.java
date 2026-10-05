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
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.TileItem;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;
import net.runelite.api.NPC;
import net.runelite.api.MenuAction;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.AnimationChanged;
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
	description = "Tracks what happens while you are AFK or away from RuneLite and summarizes XP, items, combat activity, and other session data when you return."
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

	@Inject
	private ItemManager itemManager;

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
				recapHistory.add(recap);
				AfkRecapItemPresentation.Display items = itemPresentation.prepare(recap);
				recapController.show(recap, config.showOverlay(), config.overlayDurationSeconds(), items);
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
			clientThread.invoke(stopped::suspend);
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
					current.configureRandomEvents(config.trackMissedRandomEvents());
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
			current.configureRandomEvents(config.trackMissedRandomEvents());
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
			if (current.trigger() == AfkSessionTrigger.IDLE && client.getLocalPlayer() != null)
			{
				current.activityDetected(SailingActivitySignals.animation(client.getLocalPlayer().getAnimation()));
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		AfkSessionManager current = sessions;
		if (current != null && current.trigger() == AfkSessionTrigger.IDLE && current.isReady()
			&& client.getGameState() == GameState.LOGGED_IN && event.getActor() != null && event.getActor() == client.getLocalPlayer())
		{
			current.activityDetected(SailingActivitySignals.animation(event.getActor().getAnimation()));
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
		if (current != null && current.trigger() != null && client.getGameState() == GameState.LOGGED_IN)
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
		if (current != null && current.trigger() != null && client.getGameState() == GameState.LOGGED_IN
			&& event.getActor() instanceof NPC)
		{
			current.npcDeath(event.getActor(), event.getActor().getName());
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		AfkSessionManager current = sessions;
		if (current == null || !current.isReady() || current.trigger() == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		current.configureRandomEvents(config.trackMissedRandomEvents());
		if (!config.trackMissedRandomEvents() || client.getLocalPlayer() == null)
		{
			return;
		}
		if (event.getSource() == client.getLocalPlayer())
		{
			current.randomEventHandled(event.getTarget());
		}
		else if (event.getSource() instanceof NPC)
		{
			NPC npc = (NPC) event.getSource();
			// Ownership predicate adapted from RuneLite RandomEventPlugin; see META-INF/NOTICE.
			if (event.getTarget() == client.getLocalPlayer() && RandomEventTypes.contains(npc.getId()))
			{
				current.randomEventTargeted(npc, npc.getId(), npc.getName(), true,
					client.getLocalPlayer().getInteracting() == npc);
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		AfkSessionManager current = sessions;
		if (current != null && current.trigger() != null)
		{
			current.npcDespawned(event.getNpc());
			current.configureRandomEvents(config.trackMissedRandomEvents());
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				current.randomEventDespawned(event.getNpc());
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		AfkSessionManager current = sessions;
		if (current != null && current.trigger() != null && client.getGameState() == GameState.LOGGED_IN
			&& event.getContainerId() == InventoryID.INV)
		{
			ItemContainer inventory = event.getItemContainer();
			current.inventoryChanged(inventory == null ? null : InventoryGainTracker.totals(inventory.getItems()));
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		trackGroundItem(event.getItem(), -1, event.getItem() == null ? 0 : event.getItem().getQuantity());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		trackGroundItem(event.getItem(), event.getOldQuantity(), event.getNewQuantity());
	}

	private void trackGroundItem(TileItem item, int oldQuantity, int quantity)
	{
		AfkSessionManager current = sessions;
		if (current == null || current.trigger() == null || !current.isReady()
			|| client.getGameState() != GameState.LOGGED_IN || !config.trackNotableDrops()
			|| item == null || quantity < 0 || item.getId() < 0
			|| !NotableDropTracker.attributed(item.getOwnership()))
		{
			return;
		}
		if (oldQuantity >= 0 && quantity <= oldQuantity)
		{
			current.groundQuantityChanged(item, item.getId(), oldQuantity, quantity, item.getOwnership(), 0, false, config);
			return;
		}
		try
		{
			int id = item.getId();
			ItemComposition definition = itemManager.getItemComposition(id);
			if (definition == null)
			{
				return;
			}
			boolean clue = ClueDropItems.isClue(id, definition.getIntValue(ParamID.CLUE_SCROLL));
			long price = -1;
			try
			{
				price = itemManager.getItemPrice(id);
			}
			catch (RuntimeException exception)
			{
				log.debug("Unable to price notable ground item {}", id, exception);
			}
			if (oldQuantity < 0)
			{
				current.groundSpawn(item, id, quantity, item.getOwnership(), price, clue, config);
			}
			else
			{
				current.groundQuantityChanged(item, id, oldQuantity, quantity, item.getOwnership(), price, clue, config);
			}
		}
		catch (RuntimeException exception)
		{
			log.debug("Unable to resolve notable ground item {}", item.getId(), exception);
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		AfkSessionManager current = sessions;
		if (current != null && current.trigger() != null)
		{
			current.groundDespawn(event.getItem());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		AfkSessionManager current = sessions;
		if (current == null || current.trigger() == null || client.getGameState() != GameState.LOGGED_IN || !current.isReady())
		{
			return;
		}
		if (!ResourceAcquisitionMessages.acceptsChat(Family.LOG, event.getType()) || event.getMessage() == null)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (current.trigger() == AfkSessionTrigger.IDLE)
		{
			current.activityDetected(SailingActivitySignals.message(message));
		}
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
		if (!ResourceAcquisitionMessages.mayAcquire(message))
		{
			// Preserve bonus-context invalidation without inspecting inventory/equipment.
			current.acquisitionMessage(message, false, false);
			return;
		}
		ResourceContainerState storage = resourceContainerState();
		current.acquisitionMessage(message,
			ResourceAcquisitionMessages.acceptsChat(Family.FISH, event.getType()) && storage.hiddenFish(),
			storage.hiddenLogs());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		AfkSessionManager current = sessions;
		if (current == null || current.trigger() == null || client.getGameState() != GameState.LOGGED_IN || !current.isReady())
		{
			return;
		}
		if (event.getMenuOption() == null || event.getMenuAction() == null)
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		current.configureRandomEvents(config.trackMissedRandomEvents());
		if (!event.isConsumed() && (option.equals("talk-to") || option.equals("dismiss"))
			&& event.getMenuAction().getId() >= MenuAction.NPC_FIRST_OPTION.getId()
			&& event.getMenuAction().getId() <= MenuAction.NPC_FIFTH_OPTION.getId())
		{
			current.randomEventHandled(event.getMenuEntry().getNpc());
		}
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
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			// An earlier return overlay must not remain on the login/reconnect screen either.
			recapController.clear();
		}
		AfkSessionManager current = sessions;
		if (current != null)
		{
			current.configureRandomEvents(config.trackMissedRandomEvents());
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
			if ("trackMissedRandomEvents".equals(event.getKey()))
			{
				clientThread.invoke(() ->
				{
					if (sessions != null)
					{
						sessions.configureRandomEvents(config.trackMissedRandomEvents());
					}
				});
			}
			else if ("recentRecapLimit".equals(event.getKey()))
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
