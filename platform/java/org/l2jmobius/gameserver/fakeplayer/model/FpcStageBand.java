package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcStageBand
{
	private final String _id;
	private final String _displayName;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _representativeLevel;
	private final String _loadoutProfile;
	private final String _consumableProfile;
	private final String _combatProfile;
	private final String _notes;

	public FpcStageBand(String id, String displayName, int minLevel, int maxLevel, int representativeLevel, String loadoutProfile, String consumableProfile, String combatProfile, String notes)
	{
		_id = id;
		_displayName = displayName;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_representativeLevel = representativeLevel;
		_loadoutProfile = loadoutProfile;
		_consumableProfile = consumableProfile;
		_combatProfile = combatProfile;
		_notes = notes;
	}

	public String getId()
	{
		return _id;
	}

	public String getDisplayName()
	{
		return _displayName;
	}

	public int getMinLevel()
	{
		return _minLevel;
	}

	public int getMaxLevel()
	{
		return _maxLevel;
	}

	public int getRepresentativeLevel()
	{
		return _representativeLevel;
	}

	public String getLoadoutProfile()
	{
		return _loadoutProfile;
	}

	public String getConsumableProfile()
	{
		return _consumableProfile;
	}

	public String getCombatProfile()
	{
		return _combatProfile;
	}

	public String getNotes()
	{
		return _notes;
	}
}
