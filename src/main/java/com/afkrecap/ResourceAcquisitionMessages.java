package com.afkrecap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.ItemID;

/** A deliberately small vocabulary: no XP inference, Check text, or generic success messages. */
final class ResourceAcquisitionMessages
{
	enum Family { FISH, LOG }

	private static final Pattern CATCH = Pattern.compile("^You catch (?:a|an|some)(?: raw)? ([A-Za-z ]+)[.!]$");
	// Mirror RuneLite 1.13.1 WoodcuttingPlugin's acquisition pattern; capture the item name.
	private static final Pattern CHOP = Pattern.compile("^You get (?:some|an)([\\w ]+(?:logs?|mushrooms))\\.$");
	private static final Map<String, Integer> FISH = fish();
	private static final Map<String, Integer> LOGS = logs();

	private ResourceAcquisitionMessages()
	{
	}

	static boolean acceptsChat(Family family, ChatMessageType type)
	{
		return type == ChatMessageType.SPAM || (family == Family.LOG
			&& (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.MESBOX));
	}

	static Integer resource(String message, Family family)
	{
		if (message == null)
		{
			return null;
		}
		Matcher match = (family == Family.FISH ? CATCH : CHOP).matcher(message);
		return match.matches() ? (family == Family.FISH ? FISH : LOGS)
			.get(match.group(1).trim().toLowerCase(Locale.ROOT)) : null;
	}

	static Family family(int id)
	{
		return FISH.containsValue(id) ? Family.FISH : LOGS.containsValue(id) ? Family.LOG : null;
	}

	static Family container(int id)
	{
		switch (id)
		{
			case ItemID.FISH_BARREL_OPEN:
			case ItemID.FISH_BARREL_CLOSED:
			case ItemID.FISH_SACK_BARREL_OPEN:
			case ItemID.FISH_SACK_BARREL_CLOSED:
				return Family.FISH;
			case ItemID.LOG_BASKET_OPEN:
			case ItemID.LOG_BASKET_CLOSED:
			case ItemID.FORESTRY_BASKET_OPEN:
			case ItemID.FORESTRY_BASKET_CLOSED:
				return Family.LOG;
			default:
				return null;
		}
	}

	static Family bonus(String message)
	{
		switch (message)
		{
			case "Rada's blessing enabled you to catch an extra fish.":
			case "The spirit flakes enabled you to catch an extra fish.":
				return Family.FISH;
			case "Your Kandarin headgear provides you with an additional log.":
			case "The nature offerings enabled you to chop an extra log.":
				return Family.LOG;
			default:
				return null;
		}
	}

	private static Map<String, Integer> fish()
	{
		Map<String, Integer> ids = new HashMap<>();
		ids.put("shrimp", ItemID.RAW_SHRIMP);
		ids.put("shrimps", ItemID.RAW_SHRIMP);
		ids.put("sardine", ItemID.RAW_SARDINE);
		ids.put("herring", ItemID.RAW_HERRING);
		ids.put("anchovies", ItemID.RAW_ANCHOVIES);
		ids.put("mackerel", ItemID.RAW_MACKEREL);
		ids.put("trout", ItemID.RAW_TROUT);
		ids.put("cod", ItemID.RAW_COD);
		ids.put("pike", ItemID.RAW_PIKE);
		ids.put("salmon", ItemID.RAW_SALMON);
		ids.put("tuna", ItemID.RAW_TUNA);
		ids.put("lobster", ItemID.RAW_LOBSTER);
		ids.put("bass", ItemID.RAW_BASS);
		ids.put("swordfish", ItemID.RAW_SWORDFISH);
		ids.put("monkfish", ItemID.RAW_MONKFISH);
		ids.put("karambwan", ItemID.TBWT_RAW_KARAMBWAN);
		ids.put("shark", ItemID.RAW_SHARK);
		ids.put("anglerfish", ItemID.RAW_ANGLERFISH);
		ids.put("dark crab", ItemID.RAW_DARK_CRAB);
		return Collections.unmodifiableMap(ids);
	}

	private static Map<String, Integer> logs()
	{
		Map<String, Integer> ids = new HashMap<>();
		ids.put("logs", ItemID.LOGS);
		ids.put("achey tree logs", ItemID.ACHEY_TREE_LOGS);
		ids.put("oak logs", ItemID.OAK_LOGS);
		ids.put("willow logs", ItemID.WILLOW_LOGS);
		ids.put("teak logs", ItemID.TEAK_LOGS);
		ids.put("maple logs", ItemID.MAPLE_LOGS);
		ids.put("mahogany logs", ItemID.MAHOGANY_LOGS);
		ids.put("arctic pine logs", ItemID.ARCTIC_PINE_LOG);
		ids.put("yew logs", ItemID.YEW_LOGS);
		ids.put("magic logs", ItemID.MAGIC_LOGS);
		ids.put("redwood logs", ItemID.REDWOOD_LOGS);
		new HashMap<>(ids).forEach((name, id) -> ids.put(name.substring(0, name.length() - 1), id));
		return Collections.unmodifiableMap(ids);
	}
}
