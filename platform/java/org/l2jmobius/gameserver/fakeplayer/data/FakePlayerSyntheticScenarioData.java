package org.l2jmobius.gameserver.fakeplayer.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSyntheticScenarioDefinition;

public class FakePlayerSyntheticScenarioData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerSyntheticScenarioData.class.getName());

	private final FakePlayerConfig _config;
	private volatile List<FpcSyntheticScenarioDefinition> _scenarios = List.of();

	public FakePlayerSyntheticScenarioData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		final String raw;
		try
		{
			raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getSyntheticScenariosPath());
		}
		catch (Exception e)
		{
			_scenarios = List.of();
			LOGGER.warning(getClass().getSimpleName() + ": Failed to resolve AFPC synthetic scenario catalog from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getSyntheticScenariosPath()) + " -> " + e.getMessage());
			return;
		}
		if ((raw == null) || raw.isBlank())
		{
			_scenarios = List.of();
			LOGGER.info(() -> getClass().getSimpleName() + ": No synthetic scenario file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getSyntheticScenariosPath()) + ". Continuing without AFPC scenario quests.");
			return;
		}

		try
		{
			final List<FpcSyntheticScenarioDefinition> parsed = parseScenarios(raw);
			parsed.sort(Comparator.comparing(FpcSyntheticScenarioDefinition::getId));
			_scenarios = List.copyOf(parsed);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded AFPC synthetic scenarios=" + _scenarios.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getSyntheticScenariosPath()));
		}
		catch (Exception e)
		{
			_scenarios = List.of();
			LOGGER.warning(getClass().getSimpleName() + ": Failed to load AFPC synthetic scenarios from " + _config.getSyntheticScenariosPath() + " -> " + e.getMessage());
		}
	}

	private List<FpcSyntheticScenarioDefinition> parseScenarios(String json) throws IOException
	{
		final List<FpcSyntheticScenarioDefinition> result = new ArrayList<>();
		for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(json))
		{
			final FpcSyntheticScenarioDefinition scenario = parseScenario(objectJson);
			if ((scenario != null) && scenario.isEnabled())
			{
				result.add(scenario);
			}
		}
		return result;
	}

	private FpcSyntheticScenarioDefinition parseScenario(String objectJson)
	{
		return new FpcSyntheticScenarioDefinition(
			requiredString(objectJson, "id"),
			optionalString(objectJson, "displayName", ""),
			optionalBoolean(objectJson, "enabled", true),
			optionalInt(objectJson, "sourceQuestId", 0),
			optionalString(objectJson, "sourceQuestName", ""),
			readStringArray(objectJson, "assignedFpcIds"),
			optionalString(objectJson, "stagingZoneId", ""),
			requiredString(objectJson, "objectiveZoneId"),
			optionalString(objectJson, "returnZoneId", ""),
			readIntArray(objectJson, "targetNpcIds"),
			optionalInt(objectJson, "targetCount", 1),
			optionalInt(objectJson, "searchRadius", 900),
			optionalInt(objectJson, "leashRadius", 1400),
			optionalInt(objectJson, "returnRadius", 350),
			optionalBoolean(objectJson, "repeatable", false),
			optionalString(objectJson, "startLine", ""),
			optionalString(objectJson, "progressLine", ""),
			optionalString(objectJson, "completeLine", ""),
			optionalString(objectJson, "notes", ""));
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

	private int optionalInt(String json, String key, int defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalInt(json, key, defaultValue);
	}

	private Set<String> readStringArray(String json, String key)
	{
		final Set<String> result = new LinkedHashSet<>();
		for (String value : FakePlayerJsonDataSupport.parseStringArray(FakePlayerJsonDataSupport.optionalArray(json, key), true, true))
		{
			result.add(value);
		}
		return result;
	}

	private Set<Integer> readIntArray(String json, String key)
	{
		return new LinkedHashSet<>(FakePlayerJsonDataSupport.parseIntegerArray(FakePlayerJsonDataSupport.optionalArray(json, key)));
	}

	public Collection<FpcSyntheticScenarioDefinition> getScenarios()
	{
		return _scenarios;
	}
}
