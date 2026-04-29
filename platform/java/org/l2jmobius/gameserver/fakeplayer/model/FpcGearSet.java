package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.l2jmobius.gameserver.model.actor.enums.creature.AttributeType;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;

public class FpcGearSet
{
	private final String _id;
	private final String _classRole;
	private final int _minLevel;
	private final int _maxLevel;
	private final Map<Integer, Integer> _paperdollItems;
	private final Map<Integer, Integer> _slotEnchantLevels;
	private final int _weaponEnchantLevel;
	private final int _armorEnchantLevel;
	private final int _strBonus;
	private final int _dexBonus;
	private final int _conBonus;
	private final int _intBonus;
	private final int _witBonus;
	private final int _menBonus;
	private final Map<AttributeType, Integer> _attackElementPowers;
	private final Map<AttributeType, Integer> _defenseElementResists;
	private final List<Integer> _passiveSkillIds;
	private final int _combatSkillCadenceMs;
	private final String _notes;

	public FpcGearSet(String id, String classRole, int minLevel, int maxLevel, Map<Integer, Integer> paperdollItems, Map<Integer, Integer> slotEnchantLevels, int weaponEnchantLevel, int armorEnchantLevel, int strBonus, int dexBonus, int conBonus, int intBonus, int witBonus, int menBonus, Map<AttributeType, Integer> attackElementPowers, Map<AttributeType, Integer> defenseElementResists, List<Integer> passiveSkillIds, int combatSkillCadenceMs, String notes)
	{
		_id = ((id == null) || id.isBlank()) ? "unnamed" : id;
		_classRole = ((classRole == null) || classRole.isBlank()) ? "all" : classRole;
		_minLevel = Math.max(1, minLevel);
		_maxLevel = Math.max(_minLevel, maxLevel);
		_paperdollItems = Map.copyOf(paperdollItems == null ? Map.of() : new LinkedHashMap<>(paperdollItems));
		_slotEnchantLevels = Map.copyOf(slotEnchantLevels == null ? Map.of() : new LinkedHashMap<>(slotEnchantLevels));
		_weaponEnchantLevel = Math.max(0, weaponEnchantLevel);
		_armorEnchantLevel = Math.max(0, armorEnchantLevel);
		_strBonus = strBonus;
		_dexBonus = dexBonus;
		_conBonus = conBonus;
		_intBonus = intBonus;
		_witBonus = witBonus;
		_menBonus = menBonus;
		_attackElementPowers = Map.copyOf(attackElementPowers == null ? Map.of() : new LinkedHashMap<>(attackElementPowers));
		_defenseElementResists = Map.copyOf(defenseElementResists == null ? Map.of() : new LinkedHashMap<>(defenseElementResists));
		_passiveSkillIds = List.copyOf(passiveSkillIds == null ? List.of() : passiveSkillIds);
		_combatSkillCadenceMs = Math.max(0, combatSkillCadenceMs);
		_notes = (notes == null) ? "" : notes;
	}

	public String getId()
	{
		return _id;
	}

	public String getClassRole()
	{
		return _classRole;
	}

	public int getMinLevel()
	{
		return _minLevel;
	}

	public int getMaxLevel()
	{
		return _maxLevel;
	}

	public int getPaperdollItem(int slot)
	{
		return _paperdollItems.getOrDefault(slot, 0);
	}

	public Map<Integer, Integer> getPaperdollItems()
	{
		return _paperdollItems;
	}

	public boolean hasPaperdollItem(int slot)
	{
		return _paperdollItems.containsKey(slot);
	}

	public int getSlotEnchantLevel(int slot)
	{
		return _slotEnchantLevels.getOrDefault(slot, 0);
	}

	public int getResolvedEnchantLevel(int slot, boolean weapon)
	{
		final int slotLevel = getSlotEnchantLevel(slot);
		if (slotLevel > 0)
		{
			return slotLevel;
		}
		return weapon ? _weaponEnchantLevel : _armorEnchantLevel;
	}

	public boolean hasSlotEnchantLevel(int slot)
	{
		return _slotEnchantLevels.containsKey(slot);
	}

	public int getWeaponEnchantLevel()
	{
		return _weaponEnchantLevel;
	}

	public int getArmorEnchantLevel()
	{
		return _armorEnchantLevel;
	}

	public int getStrBonus()
	{
		return _strBonus;
	}

	public int getDexBonus()
	{
		return _dexBonus;
	}

	public int getConBonus()
	{
		return _conBonus;
	}

	public int getIntBonus()
	{
		return _intBonus;
	}

	public int getWitBonus()
	{
		return _witBonus;
	}

	public int getMenBonus()
	{
		return _menBonus;
	}

	public int getAttackElementPower(AttributeType type)
	{
		return _attackElementPowers.getOrDefault(type, 0);
	}

	public int getDefenseElementResist(AttributeType type)
	{
		return _defenseElementResists.getOrDefault(type, 0);
	}

	public List<Integer> getPassiveSkillIds()
	{
		return _passiveSkillIds;
	}

	public int getCombatSkillCadenceMs()
	{
		return _combatSkillCadenceMs;
	}

	public String getNotes()
	{
		return _notes;
	}

	public boolean matches(PlayerClass playerClass, int effectiveLevel)
	{
		if ((playerClass == null) || (effectiveLevel < _minLevel) || (effectiveLevel > _maxLevel))
		{
			return false;
		}

		return switch (_classRole.toLowerCase(Locale.ROOT))
		{
			case "", "all" -> true;
			case "mage" -> playerClass.isMage();
			case "summoner" -> playerClass.isSummoner();
			case "fighter" -> !playerClass.isMage();
			default -> playerClass.name().equalsIgnoreCase(_classRole);
		};
	}
}
