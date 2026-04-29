package org.l2jmobius.gameserver.fakeplayer.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute;

public class FakePlayerAdventurerRouteData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerAdventurerRouteData.class.getName());

	private final FakePlayerConfig _config;
	private volatile List<FpcAdventurerRoute> _routes = List.of();

	public FakePlayerAdventurerRouteData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		final String raw;
		try
		{
			raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getAdventurerRoutesPath());
		}
		catch (Exception e)
		{
			_routes = List.of();
			LOGGER.warning(getClass().getSimpleName() + ": Failed to resolve AFPC route catalog from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getAdventurerRoutesPath()) + " -> " + e.getMessage());
			return;
		}
		if ((raw == null) || raw.isBlank())
		{
			_routes = List.of();
			LOGGER.info(() -> getClass().getSimpleName() + ": No AFPC route file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getAdventurerRoutesPath()) + ". Continuing without raw travel/combat routes.");
			return;
		}

		try
		{
			final List<FpcAdventurerRoute> parsed = parseRoutes(raw);
			parsed.sort(Comparator.comparingInt(FpcAdventurerRoute::getPriority).reversed());
			_routes = List.copyOf(parsed);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded AFPC adventurer routes=" + _routes.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getAdventurerRoutesPath()));
		}
		catch (Exception e)
		{
			_routes = List.of();
			LOGGER.warning(getClass().getSimpleName() + ": Failed to load AFPC routes from " + _config.getAdventurerRoutesPath() + " -> " + e.getMessage());
		}
	}

	private List<FpcAdventurerRoute> parseRoutes(String json) throws IOException
	{
		final List<FpcAdventurerRoute> result = new ArrayList<>();
		for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(json))
		{
			final FpcAdventurerRoute route = parseRoute(objectJson);
			if ((route != null) && route.isEnabled())
			{
				result.add(route);
			}
		}
		return result;
	}

	private FpcAdventurerRoute parseRoute(String objectJson)
	{
		return new FpcAdventurerRoute(
			requiredString(objectJson, "id"),
			optionalString(objectJson, "displayName", ""),
			optionalInt(objectJson, "priority", 0),
			optionalInt(objectJson, "minLevel", 1),
			optionalInt(objectJson, "maxLevel", 200),
			optionalInt(objectJson, "teleportId", 0),
			requiredString(objectJson, "arrivalZoneId"),
			requiredString(objectJson, "farmZoneId"),
			optionalInt(objectJson, "searchRadius", 900),
			optionalInt(objectJson, "leashRadius", 1400),
			optionalBoolean(objectJson, "enabled", true),
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

	public Collection<FpcAdventurerRoute> getRoutes()
	{
		return _routes;
	}
}
