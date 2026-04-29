package org.l2jmobius.gameserver.fakeplayer.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerZoneDefinition;

public class FakePlayerZoneData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerZoneData.class.getName());

	private final FakePlayerConfig _config;
	private volatile Map<String, FakePlayerZoneDefinition> _zonesById = Map.of();
	private volatile List<String> _defaultZones = List.of();

	public FakePlayerZoneData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		final String raw;
		try
		{
			raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getZonesPath());
		}
		catch (Exception e)
		{
			installFallbackZones("Failed to resolve FPC zone catalog from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getZonesPath()) + " -> " + e.getMessage());
			return;
		}
		if ((raw == null) || raw.isBlank())
		{
			installFallbackZones("No FPC zone file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getZonesPath()));
			return;
		}

		try
		{
			final List<FakePlayerZoneDefinition> zones = parseZones(raw);
			if (zones.isEmpty())
			{
				installFallbackZones("No enabled FPC zones found in " + FakePlayerDataAssetResolver.describeSource(_config, _config.getZonesPath()));
				return;
			}

			replaceZones(zones);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded " + zones.size() + " FPC zone(s) from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getZonesPath()) + " -> " + _defaultZones);
		}
		catch (Exception e)
		{
			installFallbackZones("Failed to load FPC zones from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getZonesPath()) + " -> " + e.getMessage());
		}
	}

	private void installFallbackZones(String reason)
	{
		replaceZones(List.of(new FakePlayerZoneDefinition("giran", 83456, 148632, -3400, 0, 260, true)));
		LOGGER.info(() -> getClass().getSimpleName() + ": " + reason + ". Falling back to built-in zones " + _defaultZones);
	}

	private void replaceZones(List<FakePlayerZoneDefinition> zones)
	{
		final Map<String, FakePlayerZoneDefinition> byId = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();
		for (FakePlayerZoneDefinition zone : zones)
		{
			if ((zone == null) || !zone.isEnabled())
			{
				continue;
			}

			final String key = FakePlayerJsonDataSupport.normalizeKey(zone.getId());
			if (byId.putIfAbsent(key, zone) != null)
			{
				throw new IllegalArgumentException("Duplicate FPC zone id: " + zone.getId());
			}
			order.add(zone.getId());
		}
		_zonesById = Map.copyOf(byId);
		_defaultZones = List.copyOf(order);
	}

	private List<FakePlayerZoneDefinition> parseZones(String json) throws IOException
	{
		final List<FakePlayerZoneDefinition> result = new ArrayList<>();
		for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(json))
		{
			result.add(parseZone(objectJson));
		}
		return result;
	}

	private FakePlayerZoneDefinition parseZone(String objectJson)
	{
		return new FakePlayerZoneDefinition(
			requiredString(objectJson, "id"),
			requiredInt(objectJson, "x"),
			requiredInt(objectJson, "y"),
			requiredInt(objectJson, "z"),
			optionalInt(objectJson, "heading", 0),
			optionalInt(objectJson, "roamRadius", 220),
			optionalBoolean(objectJson, "enabled", true));
	}

	private String requiredString(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredString(json, key);
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	private boolean optionalBoolean(String json, String key, boolean defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalBoolean(json, key, defaultValue);
	}

	private int requiredInt(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredInt(json, key);
	}

	private int optionalInt(String json, String key, int defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalInt(json, key, defaultValue);
	}

	public List<String> getDefaultZones()
	{
		return _defaultZones;
	}

	public FakePlayerZoneDefinition getZone(String id)
	{
		return (id == null) ? null : _zonesById.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public Collection<FakePlayerZoneDefinition> getZones()
	{
		return _zonesById.values();
	}
}
