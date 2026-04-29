package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerProfileData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;

public class FakePlayerManager
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerManager.class.getName());

	private final FakePlayerConfig _config;
	private final FakePlayerProfileData _profileData;
	private final FakePlayerAdventurerService _adventurerService;
	private final FakePlayerSpawnService _spawnService;
	private final FakePlayerDecisionEngine _decisionEngine;
	private final FakePlayerTaskService _taskService;

	public FakePlayerManager(FakePlayerConfig config, FakePlayerProfileData profileData, FakePlayerAdventurerService adventurerService, FakePlayerSpawnService spawnService, FakePlayerDecisionEngine decisionEngine, FakePlayerTaskService taskService)
	{
		_config = config;
		_profileData = profileData;
		_adventurerService = adventurerService;
		_spawnService = spawnService;
		_decisionEngine = decisionEngine;
		_taskService = taskService;
	}

	public void spawnInitialSquad()
	{
		_spawnService.spawnInitialSquad();
		autoSpawnConfiguredFpcsOnBoot();
	}
	
	private void autoSpawnConfiguredFpcsOnBoot()
	{
		for (FpcDefinition definition : _profileData.getDefinitions())
		{
			if (!definition.isAutoSpawnOnBoot())
			{
				continue;
			}
			
			final boolean success = _spawnService.spawnFpc(definition, _profileData.getRegistry());
			LOGGER.info(() -> getClass().getSimpleName() + ": autoSpawnOnBoot id=" + definition.getId() + " success=" + success);
		}
	}

	public void start()
	{
		LOGGER.info(() -> getClass().getSimpleName() + ": Starting runtime manager for squad size " + _config.getSquadSize());
		_taskService.start();
	}
	
	public void reloadFpcDefinitions()
	{
		_profileData.load();
		_adventurerService.validateDefinitions(_profileData.getDefinitions());
		reconcileActiveSpawns();
		LOGGER.info(() -> getClass().getSimpleName() + ": Reloaded generic FPC definitions. Count=" + _profileData.getDefinitions().size());
	}
	
	public Collection<FpcDefinition> getLoadedFpcDefinitions()
	{
		return _profileData.getDefinitions();
	}
	
	public boolean spawnFpc(String id)
	{
		final FpcDefinition definition = _profileData.getDefinitionById(id);
		if (definition == null)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Unknown FPC id " + id);
			return false;
		}
		return _spawnService.spawnFpc(definition, _profileData.getRegistry());
	}
	
	public boolean despawnFpc(String id)
	{
		return _spawnService.despawnFpc(id, _profileData.getRegistry());
	}

	public FakePlayerProfileData getProfileData()
	{
		return _profileData;
	}

	public FakePlayerDecisionEngine getDecisionEngine()
	{
		return _decisionEngine;
	}

	private void reconcileActiveSpawns()
	{
		final FpcRegistry registry = _profileData.getRegistry();
		final Set<String> desiredIds = new LinkedHashSet<>();
		for (FpcDefinition definition : _profileData.getDefinitions())
		{
			if ((definition != null) && definition.isEnabled())
			{
				desiredIds.add(definition.getId().toLowerCase());
			}
		}

		for (String activeId : registry.getActiveSpawnIds())
		{
			if (desiredIds.contains(activeId.toLowerCase()))
			{
				continue;
			}

			final boolean despawned = _spawnService.despawnFpc(activeId, registry);
			LOGGER.info(() -> getClass().getSimpleName() + ": Reconciled inactive FPC after reload id=" + activeId + " despawned=" + despawned);
		}

		for (FpcDefinition definition : _profileData.getDefinitions())
		{
			if ((definition == null) || !definition.isEnabled() || !definition.isAutoSpawnOnBoot())
			{
				continue;
			}
			if (registry.getActiveSpawn(definition.getId()) != null)
			{
				continue;
			}

			final boolean spawned = _spawnService.spawnFpc(definition, registry);
			LOGGER.info(() -> getClass().getSimpleName() + ": Reconciled missing active FPC after reload id=" + definition.getId() + " spawned=" + spawned);
		}
	}
}
