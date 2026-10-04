package com.afkrecap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/** Resolve on the client thread once; UI code only reads immutable cached presentation data. */
@Singleton
@Slf4j
public final class AfkRecapItemPresentation
{
	private static final Display EMPTY = new Display(Collections.emptyList(), null);
	private final IntFunction<ItemDetails> lookup;
	// Values never reference the key: presentations live only as long as history/overlay need them.
	private final Map<AfkRecapSession, Display> cache = new WeakHashMap<>();

	@Inject
	AfkRecapItemPresentation(ItemManager itemManager)
	{
		this(id ->
		{
			ItemComposition definition = itemManager.getItemComposition(id);
			String name = definition == null ? null : definition.getName();
			if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name))
			{
				return new ItemDetails(null, 0);
			}
			long price = 0;
			try
			{
				price = itemManager.getItemPrice(id);
			}
			catch (RuntimeException exception)
			{
				log.debug("Unable to price recap item {}", id, exception);
			}
			return new ItemDetails(name, price);
		});
	}

	AfkRecapItemPresentation(IntFunction<ItemDetails> lookup)
	{
		this.lookup = lookup;
	}

	synchronized Display prepare(AfkRecapSession recap)
	{
		if (recap.getItemGains().isEmpty())
		{
			return EMPTY;
		}
		List<ItemRow> rows = new ArrayList<>();
		BigInteger total = BigInteger.ZERO;
		boolean priced = false;
		for (Map.Entry<Integer, Integer> entry : recap.getItemGains().entrySet())
		{
			int id = entry.getKey();
			ItemDetails details;
			try
			{
				details = lookup.apply(id);
			}
			catch (RuntimeException exception)
			{
				log.debug("Unable to resolve recap item {}", id, exception);
				details = null;
			}
			String name = details == null ? null : details.name;
			if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name))
			{
				name = "Item " + id;
			}
			String value = null;
			if (details != null && details.unitPrice > 0)
			{
				BigInteger itemValue = BigInteger.valueOf(details.unitPrice)
					.multiply(BigInteger.valueOf(entry.getValue()));
				total = total.add(itemValue);
				value = gp(itemValue);
				priced = true;
			}
			rows.add(new ItemRow(name, String.format(Locale.US, "%,d", entry.getValue()), value));
		}
		Display display = new Display(rows, priced ? gp(total) : null);
		cache.put(recap, display);
		return display;
	}

	synchronized Display get(AfkRecapSession recap)
	{
		return cache.getOrDefault(recap, EMPTY);
	}

	static Display empty()
	{
		return EMPTY;
	}

	private static String gp(BigInteger value)
	{
		return String.format(Locale.US, "%,d gp", value);
	}

	static final class ItemDetails
	{
		private final String name;
		private final long unitPrice;

		ItemDetails(String name, long unitPrice)
		{
			this.name = name;
			this.unitPrice = unitPrice;
		}
	}

	@Getter
	static final class Display
	{
		private final List<ItemRow> rows;
		private final String totalValue;

		private Display(List<ItemRow> rows, String totalValue)
		{
			this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
			this.totalValue = totalValue;
		}
	}

	@Getter
	static final class ItemRow
	{
		private final String name;
		private final String quantity;
		private final String value;
		private final String quantityAndValue;

		private ItemRow(String name, String quantity, String value)
		{
			this.name = name;
			this.quantity = quantity;
			this.value = value;
			this.quantityAndValue = quantity + (value == null ? "" : "  " + value);
		}
	}
}
