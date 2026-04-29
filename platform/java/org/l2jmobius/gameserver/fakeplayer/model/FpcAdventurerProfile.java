package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcAdventurerProfile
{
	private final String _tier;
	private final String _carrierMode;
	private final String _inventoryMode;
	private final String _pickupPolicy;
	private final String _combatMode;
	private final String _partyMode;
	private final String _clanMode;
	private final String _matchmakingMode;
	private final String _progressionMode;
	private final String _stageBandId;
	private final int _fixedLevel;
	private final String _loadoutProfile;
	private final String _consumableProfile;
	private final String _routeId;

	public FpcAdventurerProfile(String tier, String carrierMode, String inventoryMode, String pickupPolicy, String combatMode, String partyMode, String clanMode, String matchmakingMode, String progressionMode, String stageBandId, int fixedLevel, String loadoutProfile, String consumableProfile, String routeId)
	{
		_tier = sanitize(tier, "social");
		_carrierMode = sanitize(carrierMode, "npc_backed");
		_inventoryMode = sanitize(inventoryMode, "none");
		_pickupPolicy = sanitize(pickupPolicy, "disabled");
		_combatMode = sanitize(combatMode, "none");
		_partyMode = sanitize(partyMode, "none");
		_clanMode = sanitize(clanMode, "none");
		_matchmakingMode = sanitize(matchmakingMode, "none");
		_progressionMode = sanitize(progressionMode, "fixed");
		_stageBandId = sanitize(stageBandId, "");
		_fixedLevel = Math.max(0, fixedLevel);
		_loadoutProfile = sanitize(loadoutProfile, "");
		_consumableProfile = sanitize(consumableProfile, "");
		_routeId = sanitize(routeId, "");
	}

	private String sanitize(String value, String defaultValue)
	{
		return ((value == null) || value.isBlank()) ? defaultValue : value;
	}

	public String getTier()
	{
		return _tier;
	}

	public String getCarrierMode()
	{
		return _carrierMode;
	}

	public String getInventoryMode()
	{
		return _inventoryMode;
	}

	public String getPickupPolicy()
	{
		return _pickupPolicy;
	}

	public String getCombatMode()
	{
		return _combatMode;
	}

	public String getPartyMode()
	{
		return _partyMode;
	}

	public String getClanMode()
	{
		return _clanMode;
	}

	public String getMatchmakingMode()
	{
		return _matchmakingMode;
	}

	public String getProgressionMode()
	{
		return _progressionMode;
	}

	public String getStageBandId()
	{
		return _stageBandId;
	}

	public int getFixedLevel()
	{
		return _fixedLevel;
	}

	public String getLoadoutProfile()
	{
		return _loadoutProfile;
	}

	public String getConsumableProfile()
	{
		return _consumableProfile;
	}

	public String getRouteId()
	{
		return _routeId;
	}

	public boolean isAdventurerTier()
	{
		return "adventurer".equalsIgnoreCase(_tier);
	}

	public boolean isNpcBacked()
	{
		return "npc_backed".equalsIgnoreCase(_carrierMode);
	}

	public boolean usesSyntheticInventory()
	{
		return "synthetic".equalsIgnoreCase(_inventoryMode);
	}

	public boolean isPickupDisabled()
	{
		return (_pickupPolicy == null) || _pickupPolicy.isBlank() || "disabled".equalsIgnoreCase(_pickupPolicy);
	}

	public boolean usesStagedProgression()
	{
		return "staged".equalsIgnoreCase(_progressionMode);
	}

	public boolean supportsPartyCoordination()
	{
		return !"none".equalsIgnoreCase(_partyMode);
	}

	public boolean supportsClanMembership()
	{
		return !"none".equalsIgnoreCase(_clanMode);
	}

	public boolean supportsMatchmaking()
	{
		return !"none".equalsIgnoreCase(_matchmakingMode);
	}
}
