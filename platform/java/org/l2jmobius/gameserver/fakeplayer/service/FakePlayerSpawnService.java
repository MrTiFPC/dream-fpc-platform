package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerSpawnLifecycle;
import org.l2jmobius.gameserver.model.actor.holders.npc.FakePlayerHolder;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.actor.Npc;

/**
 * Draft spawn service placeholder.
 */
public class FakePlayerSpawnService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerSpawnService.class.getName());

	private final FakePlayerConfig _config;
	private final FakePlayerAppearanceService _appearanceService;
	private final FakePlayerCombatPowerService _combatPowerService;
	private final FakePlayerRouteService _routeService;
	private final FakePlayerSpawnLifecycle _spawnLifecycle;

	public FakePlayerSpawnService(FakePlayerConfig config, FakePlayerAppearanceService appearanceService, FakePlayerCombatPowerService combatPowerService, FakePlayerRouteService routeService, FakePlayerSpawnLifecycle spawnLifecycle)
	{
		_config = config;
		_appearanceService = appearanceService;
		_combatPowerService = combatPowerService;
		_routeService = routeService;
		_spawnLifecycle = spawnLifecycle;
	}

	public void spawnInitialSquad()
	{
		LOGGER.info(() -> getClass().getSimpleName() + ": Placeholder spawn for squad size " + _config.getSquadSize() + " across zones " + _routeService.getAllowedZones());
	}
	
	public boolean spawnFpc(FpcDefinition definition, FpcRegistry registry)
	{
		if ((definition == null) || !definition.isEnabled())
		{
			return false;
		}
		
		if (registry.getActiveSpawn(definition.getId()) != null)
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": FPC already active: " + definition.getId());
			return false;
		}
		
		final int npcId = _appearanceService.resolveCarrierNpcId(definition);
		if (npcId <= 0)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Could not resolve carrier npc id for FPC " + definition.getId() + " using carrierTemplate=" + definition.getCarrierTemplate() + " presentationMode=" + definition.getPresentationMode());
			return false;
		}
		
		try
		{
			final Object spawn = _spawnLifecycle.createSpawn(npcId, definition.getSpawnProfile().getX(), definition.getSpawnProfile().getY(), definition.getSpawnProfile().getZ(), definition.getSpawnProfile().getHeading());
			_spawnLifecycle.stopRespawn(spawn);
			
			final Npc npc = _spawnLifecycle.spawnNow(spawn);
			if (npc == null)
			{
				LOGGER.warning(getClass().getSimpleName() + ": Spawn returned null NPC for FPC " + definition.getId());
				return false;
			}
			
			npc.setName(definition.getName());
			final ResolvedFpcCombatPower combatPower = definition.getAdventurerProfile().isAdventurerTier() ? _combatPowerService.resolveCombatPower(definition, npc) : null;
			_appearanceService.applyPresentationProfile(npc, definition, combatPower);
			if (combatPower != null)
			{
				_combatPowerService.applyCombatPower(npc, combatPower);
				npc.getStat().recalculateStats(false);
				npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());
			}
			initializeSyntheticCp(npc, combatPower);
			_spawnLifecycle.broadcastInfo(npc);
			registry.registerActiveNpc(definition.getId(), npc.getObjectId());
			registry.registerActiveSpawn(definition.getId(), spawn);
			if (combatPower != null)
			{
				registry.registerActiveCombatPower(definition.getId(), combatPower);
			}
			final FakePlayerHybridPartyService hybridPartyService = FakePlayerHybridPartyService.getInstance();
			if (hybridPartyService != null)
			{
				hybridPartyService.refreshPartyWindowsForFpc(definition.getId());
			}
			LOGGER.info(() -> getClass().getSimpleName() + ": Spawned generic FPC id=" + definition.getId() + " name=" + definition.getName() + " npcId=" + npcId + " presentationMode=" + definition.getPresentationMode() + " speciesTag=" + definition.getSpeciesTag() + " familyTag=" + definition.getFamilyTag() + " tier=" + definition.getAdventurerProfile().getTier() + " partyMode=" + definition.getAdventurerProfile().getPartyMode() + " pickupPolicy=" + definition.getAdventurerProfile().getPickupPolicy() + " progressionMode=" + definition.getAdventurerProfile().getProgressionMode() + " stageBand=" + definition.getAdventurerProfile().getStageBandId() + ((combatPower != null) ? (" " + combatPower.describe()) : ""));
			return true;
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed to spawn FPC " + definition.getId() + " -> " + e.getMessage());
			return false;
		}
	}
	
	public boolean despawnFpc(String id, FpcRegistry registry)
	{
		final Object spawn = registry.getActiveSpawn(id);
		if (spawn == null)
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": No active spawn found for FPC " + id);
			return false;
		}
		
		try
		{
			_spawnLifecycle.stopRespawn(spawn);
			final Npc npc = _spawnLifecycle.getLastSpawn(spawn);
			if (npc != null)
			{
				_spawnLifecycle.deleteNpc(npc);
			}
			registry.unregisterActiveNpc(id);
			registry.unregisterActiveSpawn(id);
			registry.unregisterActiveCombatPower(id);
			LOGGER.info(() -> getClass().getSimpleName() + ": Despawned generic FPC " + id);
			return true;
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed to despawn FPC " + id + " -> " + e.getMessage());
			return false;
		}
	}

	public FakePlayerAppearanceService getAppearanceService()
	{
		return _appearanceService;
	}

	private void initializeSyntheticCp(Npc npc, ResolvedFpcCombatPower combatPower)
	{
		if ((npc == null) || !npc.isFakePlayer() || (npc.getTemplate() == null) || (npc.getTemplate().getFakePlayerInfo() == null))
		{
			return;
		}

		final int syntheticCpMax = resolveSyntheticCpMax(npc, combatPower);
		npc.getStatus().initializeSyntheticCp(syntheticCpMax);
	}

	private int resolveSyntheticCpMax(Npc npc, ResolvedFpcCombatPower combatPower)
	{
		PlayerTemplate playerTemplate = null;
		if ((combatPower != null) && (combatPower.getResolvedPlayerClass() != null))
		{
			playerTemplate = PlayerTemplateData.getInstance().getTemplate(combatPower.getResolvedPlayerClass());
		}

		if (playerTemplate == null)
		{
			final FakePlayerHolder holder = npc.getTemplate().getFakePlayerInfo();
			if (holder == null)
			{
				return 0;
			}
			playerTemplate = PlayerTemplateData.getInstance().getTemplate(holder.getPlayerClass());
		}
		if (playerTemplate == null)
		{
			return 0;
		}

		final int maxLevel = Math.max(1, ExperienceData.getInstance().getMaxLevel() - 1);
		final int seedLevel = (combatPower != null) ? combatPower.getEffectiveLevel() : npc.getLevel();
		final int level = Math.max(1, Math.min(seedLevel, maxLevel));
		return Math.max(0, Math.round(playerTemplate.getBaseCpMax(level)));
	}
}
