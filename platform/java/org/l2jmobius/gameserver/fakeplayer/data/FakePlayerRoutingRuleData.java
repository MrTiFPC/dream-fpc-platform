package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRoutingRule;

public class FakePlayerRoutingRuleData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerRoutingRuleData.class.getName());

	private final FakePlayerConfig _config;
	private final AtomicReference<List<FpcRoutingRule>> _rules = new AtomicReference<>(List.of());

	public FakePlayerRoutingRuleData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		try
		{
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getRoutingRulesPath());
			if ((raw == null) || raw.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No routing rules file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getRoutingRulesPath()) + ". Continuing.");
				_rules.set(List.of());
				return;
			}

			final List<FpcRoutingRule> loaded = new ArrayList<>();
			for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
			{
				final FpcRoutingRule rule = new FpcRoutingRule(
					requiredString(objectJson, "ruleId"),
					requiredString(objectJson, "targetType"),
					requiredString(objectJson, "result"),
					optionalString(objectJson, "scope", "current_utterance"),
					optionalBoolean(objectJson, "enabled", true),
					optionalInt(objectJson, "priority", 0),
					readStringArray(objectJson, "anyPhrases"),
					readStringArray(objectJson, "allPhrases"),
					readStringArray(objectJson, "excludePhrases"),
					optionalString(objectJson, "notes", ""));
				loaded.add(rule);
			}
			loaded.sort(Comparator.comparingInt(FpcRoutingRule::getPriority).reversed());
			_rules.set(List.copyOf(loaded));
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded routing rules=" + loaded.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getRoutingRulesPath()));
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading routing rules from " + _config.getRoutingRulesPath() + " -> " + e.getMessage());
			_rules.set(List.of());
		}
	}

	public List<FpcRoutingRule> getRules()
	{
		return _rules.get();
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

	private boolean optionalBoolean(String json, String key, boolean defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalBoolean(json, key, defaultValue);
	}

	private List<String> readStringArray(String json, String key)
	{
		return List.copyOf(FakePlayerJsonDataSupport.parseStringArray(FakePlayerJsonDataSupport.optionalArray(json, key), true, true));
	}
}
