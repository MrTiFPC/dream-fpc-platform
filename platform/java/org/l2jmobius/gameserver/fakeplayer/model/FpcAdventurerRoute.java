package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcAdventurerRoute
{
	private final String _id;
	private final String _displayName;
	private final int _priority;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _teleportId;
	private final String _arrivalZoneId;
	private final String _farmZoneId;
	private final int _searchRadius;
	private final int _leashRadius;
	private final boolean _enabled;
	private final String _notes;

	public FpcAdventurerRoute(String id, String displayName, int priority, int minLevel, int maxLevel, int teleportId, String arrivalZoneId, String farmZoneId, int searchRadius, int leashRadius, boolean enabled, String notes)
	{
		_id = sanitize(id);
		_displayName = sanitize(displayName);
		_priority = priority;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_teleportId = Math.max(0, teleportId);
		_arrivalZoneId = sanitize(arrivalZoneId);
		_farmZoneId = sanitize(farmZoneId);
		_searchRadius = Math.max(300, searchRadius);
		_leashRadius = Math.max(_searchRadius, leashRadius);
		_enabled = enabled;
		_notes = sanitize(notes);
	}

	private String sanitize(String value)
	{
		return (value == null) ? "" : value.trim();
	}

	public String getId()
	{
		return _id;
	}

	public String getDisplayName()
	{
		return _displayName;
	}

	public int getPriority()
	{
		return _priority;
	}

	public int getMinLevel()
	{
		return _minLevel;
	}

	public int getMaxLevel()
	{
		return _maxLevel;
	}

	public int getTeleportId()
	{
		return _teleportId;
	}

	public String getArrivalZoneId()
	{
		return _arrivalZoneId;
	}

	public String getFarmZoneId()
	{
		return _farmZoneId;
	}

	public int getSearchRadius()
	{
		return _searchRadius;
	}

	public int getLeashRadius()
	{
		return _leashRadius;
	}

	public boolean isEnabled()
	{
		return _enabled;
	}

	public String getNotes()
	{
		return _notes;
	}

	public boolean matchesLevel(int level)
	{
		return (level >= _minLevel) && (level <= _maxLevel);
	}
}
