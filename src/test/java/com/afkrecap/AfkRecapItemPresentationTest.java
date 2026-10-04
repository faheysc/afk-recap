package com.afkrecap;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AfkRecapItemPresentationTest
{
	private AfkRecapSession recap(Map<Integer, Integer> items)
	{
		return new AfkRecapSession(AfkSessionTrigger.FOCUS, 0, Instant.EPOCH, 1, Instant.EPOCH,
			600, Collections.emptyMap(), Collections.emptySet(), items);
	}

	@Test
	public void itemNamesQuantitiesAndPerItemValuesAreFormatted()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
			new AfkRecapItemPresentation.ItemDetails("Yew logs", 270));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.YEW_LOGS, 12)));
		AfkRecapItemPresentation.ItemRow row = display.getRows().get(0);
		assertEquals("Yew logs", row.getName());
		assertEquals("12", row.getQuantity());
		assertEquals("3,240 gp", row.getValue());
		assertEquals("12  3,240 gp", row.getQuantityAndValue());
		assertEquals("3,240 gp", display.getTotalValue());
	}

	@Test
	public void totalGpAddsOnlyPricedItems()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
			id == ItemID.YEW_LOGS ? new AfkRecapItemPresentation.ItemDetails("Yew logs", 270)
			: id == ItemID.BIRD_NEST_EMPTY ? new AfkRecapItemPresentation.ItemDetails("Bird nest", 4812)
			: new AfkRecapItemPresentation.ItemDetails("Unpriced item", 0));
		Map<Integer, Integer> items = new HashMap<>();
		items.put(ItemID.YEW_LOGS, 12);
		items.put(ItemID.BIRD_NEST_EMPTY, 1);
		items.put(ItemID.TINDERBOX, 1);
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(items));
		assertEquals(3, display.getRows().size());
		assertEquals("8,052 gp", display.getTotalValue());
		assertTrue(display.getRows().stream().anyMatch(row ->
			row.getName().equals("Unpriced item") && row.getValue() == null));
	}

	@Test
	public void knownUnpricedItemStillShowsNameAndQuantityWithoutTotal()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
			new AfkRecapItemPresentation.ItemDetails("Untradeable resource", 0));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.LOGS, 1050)));
		assertEquals("Untradeable resource", display.getRows().get(0).getName());
		assertEquals("1,050", display.getRows().get(0).getQuantityAndValue());
		assertNull(display.getRows().get(0).getValue());
		assertNull(display.getTotalValue());
	}

	@Test
	public void unknownItemUsesIdFallbackAndNoInventedValue()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id -> null);
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.LOGS, 2)));
		assertEquals("Item " + ItemID.LOGS, display.getRows().get(0).getName());
		assertEquals("2", display.getRows().get(0).getQuantity());
		assertNull(display.getTotalValue());
	}

	@Test
	public void lookupFailureDoesNotFailRecap()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
		{
			throw new IllegalArgumentException("Missing item definition");
		});
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.LOGS, 2)));
		assertEquals(1, display.getRows().size());
		assertNull(display.getTotalValue());
	}

	@Test
	public void negativePriceIsNotUseful()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
			new AfkRecapItemPresentation.ItemDetails("Logs", -1));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.LOGS, 2)));
		assertNull(display.getRows().get(0).getValue());
		assertNull(display.getTotalValue());
	}

	@Test
	public void noItemGainsProduceNoItemSection()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
		{
			fail("No lookup should occur for an XP-only recap");
			return null;
		});
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.emptyMap()));
		assertTrue(display.getRows().isEmpty());
		assertNull(display.getTotalValue());
	}

	@Test
	public void cachedPresentationCanBeReadWithoutCallingClientApisAgain()
	{
		AtomicInteger lookups = new AtomicInteger();
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
		{
			lookups.incrementAndGet();
			return new AfkRecapItemPresentation.ItemDetails("Yew logs", 270);
		});
		AfkRecapSession recap = recap(Collections.singletonMap(ItemID.YEW_LOGS, 12));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap);
		assertSame(display, presenter.get(recap));
		assertSame(display, presenter.get(recap));
		assertEquals(1, lookups.get());
	}

	@Test
	public void largeValuesDoNotOverflowGpTotals()
	{
		AfkRecapItemPresentation presenter = new AfkRecapItemPresentation(id ->
			new AfkRecapItemPresentation.ItemDetails("Resource", Long.MAX_VALUE));
		AfkRecapItemPresentation.Display display = presenter.prepare(recap(Collections.singletonMap(ItemID.LOGS, 2)));
		assertEquals("18,446,744,073,709,551,614 gp", display.getTotalValue());
	}
}
