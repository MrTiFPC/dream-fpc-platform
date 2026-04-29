package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcGearSet;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.model.actor.enums.creature.AttributeType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

public class FakePlayerGearData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerGearData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, FpcGearSet> _gearSets = new LinkedHashMap<>();

	public FakePlayerGearData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_gearSets.clear();

		try
		{
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getGearPath());
			if ((raw == null) || raw.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No gear set catalog found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getGearPath()) + ". Continuing.");
				return;
			}

			for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
			{
				final FpcGearSet gearSet = parseGearSet(objectJson);
				_gearSets.put(FakePlayerJsonDataSupport.normalizeKey(gearSet.getId()), gearSet);
			}

			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded FPC gear sets=" + _gearSets.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getGearPath()));
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading gear sets from " + _config.getGearPath() + " -> " + e.getMessage());
			_gearSets.clear();
		}
	}

	public FpcGearSet getGearSet(String id)
	{
		return _gearSets.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public int getGearSetCount()
	{
		return _gearSets.size();
	}

	public FpcGearSet resolveGearSet(ResolvedFpcCombatPower combatPower)
	{
		if ((combatPower == null) || (combatPower.getResolvedPlayerClass() == null))
		{
			return null;
		}

		final String explicitProfileId = combatPower.getResolvedLoadoutProfile();
		if ((explicitProfileId != null) && !explicitProfileId.isBlank())
		{
			final FpcGearSet explicitSet = getGearSet(explicitProfileId);
			if (explicitSet != null)
			{
				return explicitSet;
			}
		}

		for (FpcGearSet gearSet : _gearSets.values())
		{
			if (gearSet.matches(combatPower.getResolvedPlayerClass(), combatPower.getEffectiveLevel()))
			{
				return gearSet;
			}
		}
		return null;
	}

	private FpcGearSet parseGearSet(String objectJson)
	{
		final Map<Integer, Integer> paperdollItems = new LinkedHashMap<>();
		final Map<Integer, Integer> slotEnchantLevels = parseSlotEnchantLevels(optionalObject(objectJson, "slotEnchantLevels"));
		final Map<AttributeType, Integer> attackElementPowers = parseElementValues(optionalObject(objectJson, "attackElementPowers"));
		final Map<AttributeType, Integer> defenseElementResists = parseElementValues(optionalObject(objectJson, "defenseElementResists"));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_REAR, optionalInt(objectJson, "equipRear", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_LEAR, optionalInt(objectJson, "equipLear", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_NECK, optionalInt(objectJson, "equipNeck", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_RFINGER, optionalInt(objectJson, "equipRFinger", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_LFINGER, optionalInt(objectJson, "equipLFinger", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_HEAD, optionalInt(objectJson, "equipHead", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_RHAND, optionalInt(objectJson, "equipRHand", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_LHAND, optionalInt(objectJson, "equipLHand", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_GLOVES, optionalInt(objectJson, "equipGloves", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_CHEST, optionalInt(objectJson, "equipChest", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_LEGS, optionalInt(objectJson, "equipLegs", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_FEET, optionalInt(objectJson, "equipFeet", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_CLOAK, optionalInt(objectJson, "equipCloak", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_HAIR, optionalInt(objectJson, "equipHair", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_HAIR2, optionalInt(objectJson, "equipHair2", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_LBRACELET, optionalInt(objectJson, "equipLBracelet", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_RBRACELET, optionalInt(objectJson, "equipRBracelet", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_AGATHION1, optionalInt(objectJson, "equipAgathion1", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_AGATHION2, optionalInt(objectJson, "equipAgathion2", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_AGATHION3, optionalInt(objectJson, "equipAgathion3", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_AGATHION4, optionalInt(objectJson, "equipAgathion4", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_AGATHION5, optionalInt(objectJson, "equipAgathion5", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO1, optionalInt(objectJson, "equipDeco1", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO2, optionalInt(objectJson, "equipDeco2", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO3, optionalInt(objectJson, "equipDeco3", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO4, optionalInt(objectJson, "equipDeco4", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO5, optionalInt(objectJson, "equipDeco5", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_DECO6, optionalInt(objectJson, "equipDeco6", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BELT, optionalInt(objectJson, "equipBelt", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH, optionalInt(objectJson, "equipBrooch", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL1, optionalInt(objectJson, "equipBroochJewel1", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL2, optionalInt(objectJson, "equipBroochJewel2", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL3, optionalInt(objectJson, "equipBroochJewel3", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL4, optionalInt(objectJson, "equipBroochJewel4", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL5, optionalInt(objectJson, "equipBroochJewel5", 0));
		putIfPositive(paperdollItems, Inventory.PAPERDOLL_BROOCH_JEWEL6, optionalInt(objectJson, "equipBroochJewel6", 0));
		return new FpcGearSet(
			requiredString(objectJson, "id"),
			optionalString(objectJson, "classRole", "all"),
			optionalInt(objectJson, "minLevel", 1),
			optionalInt(objectJson, "maxLevel", 999),
			paperdollItems,
			slotEnchantLevels,
			optionalInt(objectJson, "weaponEnchantLevel", 0),
			optionalInt(objectJson, "armorEnchantLevel", 0),
			optionalInt(objectJson, "strBonus", 0),
			optionalInt(objectJson, "dexBonus", 0),
			optionalInt(objectJson, "conBonus", 0),
			optionalInt(objectJson, "intBonus", 0),
			optionalInt(objectJson, "witBonus", 0),
			optionalInt(objectJson, "menBonus", 0),
			attackElementPowers,
			defenseElementResists,
			parseIntArray(optionalArray(objectJson, "passiveSkillIds")),
			optionalInt(objectJson, "combatSkillCadenceMs", 0),
			optionalString(objectJson, "notes", ""));
	}

	private Map<Integer, Integer> parseSlotEnchantLevels(String objectJson)
	{
		if ((objectJson == null) || objectJson.isBlank())
		{
			return Map.of();
		}

		final Map<Integer, Integer> result = new LinkedHashMap<>();
		putIfPositive(result, Inventory.PAPERDOLL_REAR, optionalInt(objectJson, "rear", 0));
		putIfPositive(result, Inventory.PAPERDOLL_LEAR, optionalInt(objectJson, "lear", 0));
		putIfPositive(result, Inventory.PAPERDOLL_NECK, optionalInt(objectJson, "neck", 0));
		putIfPositive(result, Inventory.PAPERDOLL_RFINGER, optionalInt(objectJson, "rFinger", 0));
		putIfPositive(result, Inventory.PAPERDOLL_LFINGER, optionalInt(objectJson, "lFinger", 0));
		putIfPositive(result, Inventory.PAPERDOLL_HEAD, optionalInt(objectJson, "head", 0));
		putIfPositive(result, Inventory.PAPERDOLL_RHAND, optionalInt(objectJson, "rHand", 0));
		putIfPositive(result, Inventory.PAPERDOLL_LHAND, optionalInt(objectJson, "lHand", 0));
		putIfPositive(result, Inventory.PAPERDOLL_GLOVES, optionalInt(objectJson, "gloves", 0));
		putIfPositive(result, Inventory.PAPERDOLL_CHEST, optionalInt(objectJson, "chest", 0));
		putIfPositive(result, Inventory.PAPERDOLL_LEGS, optionalInt(objectJson, "legs", 0));
		putIfPositive(result, Inventory.PAPERDOLL_FEET, optionalInt(objectJson, "feet", 0));
		putIfPositive(result, Inventory.PAPERDOLL_CLOAK, optionalInt(objectJson, "cloak", 0));
		putIfPositive(result, Inventory.PAPERDOLL_HAIR, optionalInt(objectJson, "hair", 0));
		putIfPositive(result, Inventory.PAPERDOLL_HAIR2, optionalInt(objectJson, "hair2", 0));
		putIfPositive(result, Inventory.PAPERDOLL_LBRACELET, optionalInt(objectJson, "lBracelet", 0));
		putIfPositive(result, Inventory.PAPERDOLL_RBRACELET, optionalInt(objectJson, "rBracelet", 0));
		putIfPositive(result, Inventory.PAPERDOLL_AGATHION1, optionalInt(objectJson, "agathion1", 0));
		putIfPositive(result, Inventory.PAPERDOLL_AGATHION2, optionalInt(objectJson, "agathion2", 0));
		putIfPositive(result, Inventory.PAPERDOLL_AGATHION3, optionalInt(objectJson, "agathion3", 0));
		putIfPositive(result, Inventory.PAPERDOLL_AGATHION4, optionalInt(objectJson, "agathion4", 0));
		putIfPositive(result, Inventory.PAPERDOLL_AGATHION5, optionalInt(objectJson, "agathion5", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO1, optionalInt(objectJson, "deco1", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO2, optionalInt(objectJson, "deco2", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO3, optionalInt(objectJson, "deco3", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO4, optionalInt(objectJson, "deco4", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO5, optionalInt(objectJson, "deco5", 0));
		putIfPositive(result, Inventory.PAPERDOLL_DECO6, optionalInt(objectJson, "deco6", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BELT, optionalInt(objectJson, "belt", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH, optionalInt(objectJson, "brooch", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL1, optionalInt(objectJson, "broochJewel1", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL2, optionalInt(objectJson, "broochJewel2", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL3, optionalInt(objectJson, "broochJewel3", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL4, optionalInt(objectJson, "broochJewel4", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL5, optionalInt(objectJson, "broochJewel5", 0));
		putIfPositive(result, Inventory.PAPERDOLL_BROOCH_JEWEL6, optionalInt(objectJson, "broochJewel6", 0));
		return result;
	}

	private void putIfPositive(Map<Integer, Integer> paperdollItems, int slot, int itemId)
	{
		if (itemId > 0)
		{
			paperdollItems.put(slot, itemId);
		}
	}

	private Map<AttributeType, Integer> parseElementValues(String objectJson)
	{
		if ((objectJson == null) || objectJson.isBlank())
		{
			return Map.of();
		}

		final Map<AttributeType, Integer> result = new LinkedHashMap<>();
		putIfPositive(result, AttributeType.FIRE, optionalInt(objectJson, "fire", 0));
		putIfPositive(result, AttributeType.WATER, optionalInt(objectJson, "water", 0));
		putIfPositive(result, AttributeType.WIND, optionalInt(objectJson, "wind", 0));
		putIfPositive(result, AttributeType.EARTH, optionalInt(objectJson, "earth", 0));
		putIfPositive(result, AttributeType.HOLY, optionalInt(objectJson, "holy", 0));
		putIfPositive(result, AttributeType.DARK, optionalInt(objectJson, "dark", 0));
		return result;
	}

	private void putIfPositive(Map<AttributeType, Integer> elementValues, AttributeType attributeType, int value)
	{
		if ((attributeType != null) && (value > 0))
		{
			elementValues.put(attributeType, value);
		}
	}

	private String optionalObject(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalObject(json, key);
	}

	private String optionalArray(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalArray(json, key);
	}

	private List<Integer> parseIntArray(String json)
	{
		return FakePlayerJsonDataSupport.parseIntegerArray(json);
	}

	private String requiredString(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredString(json, key);
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	private int optionalInt(String json, String key, int defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalInt(json, key, defaultValue);
	}
}
