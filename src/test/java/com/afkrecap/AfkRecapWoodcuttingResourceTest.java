package com.afkrecap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static com.afkrecap.ResourceAcquisitionMessages.Family.*;
import static org.junit.Assert.*;

public class AfkRecapWoodcuttingResourceTest
{
	private final AfkRecapConfig config = new AfkRecapConfig() {};
	private final List<AfkRecapSession> recaps = new ArrayList<>();
	private final AfkSessionManager manager = new AfkSessionManager(() -> 0L, () -> Instant.EPOCH,
		recaps::add, Collections::emptyMap);

	private void startWoodcutting()
	{
		manager.baseline(Map.of(Skill.WOODCUTTING, 1000));
		for (int i = 0; i < 10; i++) { manager.gameTick(config); }
		manager.statChanged(Skill.WOODCUTTING, 1050);
	}

	private void chat(ChatMessageType type, String message, Set<Integer> items)
	{
		// The same channel/state gates used by AfkRecapPlugin.onChatMessage.
		if (ResourceAcquisitionMessages.acceptsChat(LOG, type))
		{
			ResourceContainerState storage = new ResourceContainerState(items);
			manager.acquisitionMessage(message,
				ResourceAcquisitionMessages.acceptsChat(FISH, type) && storage.hiddenFish(), storage.hiddenLogs());
		}
	}

	private void assertOakRecap()
	{
		manager.manualInput();
		assertEquals(Map.of(ItemID.OAK_LOGS, 1), recaps.get(0).getItemGains());
		assertEquals(Map.of(Skill.WOODCUTTING, 50L), recaps.get(0).getXpGained());
	}

	@Test public void normalAxeDirectToBasketRegression()
	{
		startWoodcutting();
		chat(ChatMessageType.SPAM, "You get some oak logs.", Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE));
		manager.inventoryChanged(Collections.emptyMap()); assertOakRecap();
	}

	@Test public void fellingAxeDirectToBasketRegression()
	{
		startWoodcutting();
		Set<Integer> items = Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE_2H);
		chat(ChatMessageType.SPAM, "You get some oak logs.", items);
		chat(ChatMessageType.GAMEMESSAGE, "You consume a Forester's ration to fuel a mighty chop.", items);
		manager.inventoryChanged(Collections.emptyMap()); assertOakRecap();
	}

	@Test public void fellingAxeForestryBasketGameMessage()
	{
		startWoodcutting();
		chat(ChatMessageType.GAMEMESSAGE, "You get some oak logs.", Set.of(ItemID.FORESTRY_BASKET_OPEN, ItemID.DRAGON_AXE_2H));
		assertOakRecap();
	}

	@Test public void messageBoxAcquisitionMatchesRuneliteChannel()
	{
		startWoodcutting();
		chat(ChatMessageType.MESBOX, "You get some oak logs.", Set.of(ItemID.LOG_BASKET_OPEN)); assertOakRecap();
	}

	@Test public void fellingAxeInventoryAndMessageCountOnce()
	{
		startWoodcutting();
		chat(ChatMessageType.SPAM, "You get some oak logs.", Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE_2H));
		manager.inventoryChanged(Map.of(ItemID.OAK_LOGS, 1)); assertOakRecap();
	}

	@Test public void emptyingBasketDoesNotRecountFellingAcquisition()
	{
		startWoodcutting();
		chat(ChatMessageType.SPAM, "You get some oak logs.", Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE_2H));
		manager.gameTick(config); manager.gameTick(config);
		manager.resourceTransfer(LOG); manager.inventoryChanged(Map.of(ItemID.OAK_LOGS, 1)); assertOakRecap();
	}

	@Test public void rationAndXpWithoutAcquisitionNeverCreateLogs()
	{
		startWoodcutting();
		chat(ChatMessageType.SPAM, "You consume a Forester's ration to fuel a mighty chop.", Set.of(ItemID.LOG_BASKET_OPEN, ItemID.RUNE_AXE_2H));
		manager.manualInput(); assertTrue(recaps.get(0).getItemGains().isEmpty());
	}

	@Test public void resourceIdentityAndUnknownResourceSafety()
	{
		assertEquals(Integer.valueOf(ItemID.ARCTIC_PINE_LOG), ResourceAcquisitionMessages.resource("You get an arctic pine log.", LOG));
		assertEquals(Integer.valueOf(ItemID.YEW_LOGS), ResourceAcquisitionMessages.resource("You get some yew logs.", LOG));
		assertNull(ResourceAcquisitionMessages.resource("You get some unknown logs.", LOG));
		assertNull(ResourceAcquisitionMessages.resource("You get some mushrooms.", LOG));
	}

	@Test public void fishingChannelsUnchanged()
	{
		assertTrue(ResourceAcquisitionMessages.acceptsChat(FISH, ChatMessageType.SPAM));
		assertFalse(ResourceAcquisitionMessages.acceptsChat(FISH, ChatMessageType.GAMEMESSAGE));
		assertFalse(ResourceAcquisitionMessages.acceptsChat(FISH, ChatMessageType.MESBOX));
		assertFalse(ResourceAcquisitionMessages.acceptsChat(LOG, ChatMessageType.PUBLICCHAT));
	}
}
