package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStageBand;

public class FakePlayerStageBandData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerStageBandData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, FpcStageBand> _bands = new LinkedHashMap<>();

	public FakePlayerStageBandData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_bands.clear();

		try
		{
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getFpcStageBandsPath());
			if ((raw == null) || raw.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No stage band file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcStageBandsPath()) + ". Continuing.");
				return;
			}

			for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
			{
				final FpcStageBand band = new FpcStageBand(
					requiredString(objectJson, "id"),
					requiredString(objectJson, "displayName"),
					requiredInt(objectJson, "minLevel"),
					requiredInt(objectJson, "maxLevel"),
					requiredInt(objectJson, "representativeLevel"),
					optionalString(objectJson, "loadoutProfile", ""),
					optionalString(objectJson, "consumableProfile", ""),
					optionalString(objectJson, "combatProfile", ""),
					optionalString(objectJson, "notes", ""));
				_bands.put(FakePlayerJsonDataSupport.normalizeKey(band.getId()), band);
			}

			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded FPC stage bands=" + _bands.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcStageBandsPath()));
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading stage bands from " + _config.getFpcStageBandsPath() + " -> " + e.getMessage());
			_bands.clear();
		}
	}

	public FpcStageBand getStageBand(String id)
	{
		return _bands.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public Collection<FpcStageBand> getAllStageBands()
	{
		return List.copyOf(_bands.values());
	}

	private String requiredString(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredString(json, key);
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	private int requiredInt(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredInt(json, key);
	}
}
