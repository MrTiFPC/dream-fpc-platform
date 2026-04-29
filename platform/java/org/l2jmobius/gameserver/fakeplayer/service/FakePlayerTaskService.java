package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.function.BooleanSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.fakeplayer.FakePlayerModule;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerProfileData;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerAdvisoryPlan;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerRuntimeSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute;
import org.l2jmobius.gameserver.fakeplayer.model.FpcCompanionProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDebugCategory;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSyntheticScenarioDefinition;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerSyntheticScenarioService.ScenarioProgress;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetAdapter;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerNavigationFacade;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.WorldRegion;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.instancezone.Instance;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.enums.ChatType;

public class FakePlayerTaskService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerTaskService.class.getName());
	private static final int PLAYER_SCAN_RANGE = 900;
	private static final double LOW_BAND_THRESHOLD = 0.30;
	private static final double MID_BAND_THRESHOLD = 0.70;
	private static final String ROKHAN_FPC_ID = "rokhan";
	private static final int ROKHAN_PARTY_SUPPORT_SKILL_ID = 45448; // Pa'agrio's Cure
	private static final int ROKHAN_PARTY_SUPPORT_RANGE = 1000;
	private static final double ROKHAN_PARTY_HP_TRIGGER = 0.92;
	private static final double ROKHAN_PARTY_CP_TRIGGER = 0.90;
	private static final long COMPANION_ASSIST_TARGET_WINDOW_MS = 8000L;
	private static final int COMPANION_DIRECTIVE_CLOSE_FOLLOW_RANGE = 150;
	private static final int COMPANION_DIRECTIVE_SYNC_RANGE = 700;
	private static final long COMPANION_INSTANCE_SYNC_WINDOW_MS = 12000L;
	private static final long COMPANION_DIRECTIVE_FOLLOW_WINDOW_MS = 10000L;
	private static final long COMPANION_DIRECTIVE_TELEPORT_COOLDOWN_MS = 3000L;
	private static final long POST_PARTY_FARM_DURATION_MS = 60000L;
	private static final long POST_PARTY_FARM_ARM_WINDOW_MS = 15000L;
	private static final int POST_PARTY_FARM_SEARCH_RADIUS = 900;
	private static final int POST_PARTY_FARM_LEASH_RADIUS = 1400;
	private static final int POST_PARTY_FARM_ROAM_RADIUS = 320;

	private final FakePlayerConfig _config;
	private final FakePlayerChatService _chatService;
	private final FakePlayerProfileData _profileData;
	private final FakePlayerDecisionEngine _decisionEngine;
	private final FakePlayerRouteService _routeService;
	private final FakePlayerCombatPowerService _combatPowerService;
	private final FakePlayerTargetingService _targetingService;
	private final FakePlayerSpawnService _spawnService;
	private final FakePlayerAdventurerCombatService _adventurerCombatService;
	private final FakePlayerAfpcCombatPolicyService _afpcCombatPolicyService;
	private final FakePlayerSyntheticScenarioService _syntheticScenarioService;
	private final FakePlayerHybridPartyService _hybridPartyService;
	private final FakePlayerDebugService _debugService;
	private final FakePlayerRulesetAdapter _ruleset;
	private final FakePlayerWorldFacade _worldFacade;
	private final FakePlayerNavigationFacade _navigationFacade;
	private final ConcurrentHashMap<String, Long> _zoneEntryTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastMoveTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, String> _lastKnownZones = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Location> _lastKnownPositions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastProgressTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> _peaceZoneSafetyInvul = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, PlannerCacheEntry> _plannerPlanCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastPlannerRefreshAttemptTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _plannerFirstEligibleTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> _plannerRefreshInFlight = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _respawnReadyTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _adminHoldUntilTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> _adminRecallReturnByTeleport = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _directiveFollowUntilTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastDirectiveFollowTeleportTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, PersonalityConflictState> _personalityConflictStates = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Location> _personalityConflictReturnAnchors = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Location> _hybridClanWarChaseAnchors = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, HybridClanWarRouteAvoidance> _hybridClanWarRouteAvoidances = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, PostPartyFarmState> _postPartyFarmStates = new ConcurrentHashMap<>();
	private final AtomicBoolean _plannerWorkerBusy = new AtomicBoolean();

	private volatile ScheduledFuture<?> _task;

	private static final class HybridClanWarRouteAvoidance
	{
		private final String _routeId;
		private final long _untilMs;

		private HybridClanWarRouteAvoidance(String routeId, long untilMs)
		{
			_routeId = (routeId == null) ? "" : routeId.trim().toLowerCase();
			_untilMs = untilMs;
		}
	}

	private static final class PersonalityConflictState
	{
		private final int _targetObjectId;
		private final String _targetName;
		private final Location _anchor;
		private final String _reason;
		private final boolean _allowPkEscalation;
		private final int _leashRange;
		private final long _untilMs;

		private PersonalityConflictState(int targetObjectId, String targetName, Location anchor, String reason, boolean allowPkEscalation, int leashRange, long untilMs)
		{
			_targetObjectId = targetObjectId;
			_targetName = (targetName == null) ? "" : targetName;
			_anchor = (anchor == null) ? null : new Location(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getHeading());
			_reason = ((reason == null) || reason.isBlank()) ? "personality_conflict" : reason.trim().toLowerCase();
			_allowPkEscalation = allowPkEscalation;
			_leashRange = Math.max(leashRange, 0);
			_untilMs = untilMs;
		}
	}

	private static final class PostPartyFarmState
	{
		private final Location _anchor;
		private final String _reason;
		private final long _untilMs;

		private PostPartyFarmState(Location anchor, String reason, long untilMs)
		{
			_anchor = (anchor == null) ? null : new Location(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getHeading());
			_reason = ((reason == null) || reason.isBlank()) ? "party_exit" : reason.trim().toLowerCase();
			_untilMs = untilMs;
		}
	}

	public FakePlayerTaskService(FakePlayerConfig config, FakePlayerChatService chatService, FakePlayerProfileData profileData, FakePlayerDecisionEngine decisionEngine, FakePlayerRouteService routeService, FakePlayerCombatPowerService combatPowerService, FakePlayerTargetingService targetingService, FakePlayerSpawnService spawnService, FakePlayerAdventurerCombatService adventurerCombatService, FakePlayerAfpcCombatPolicyService afpcCombatPolicyService, FakePlayerSyntheticScenarioService syntheticScenarioService, FakePlayerHybridPartyService hybridPartyService, FakePlayerDebugService debugService, FakePlayerRulesetAdapter ruleset, FakePlayerWorldFacade worldFacade, FakePlayerNavigationFacade navigationFacade)
	{
		_config = config;
		_chatService = chatService;
		_profileData = profileData;
		_decisionEngine = decisionEngine;
		_routeService = routeService;
		_combatPowerService = combatPowerService;
		_targetingService = targetingService;
		_spawnService = spawnService;
		_adventurerCombatService = adventurerCombatService;
		_afpcCombatPolicyService = afpcCombatPolicyService;
		_syntheticScenarioService = syntheticScenarioService;
		_hybridPartyService = hybridPartyService;
		_debugService = debugService;
		_ruleset = ruleset;
		_worldFacade = worldFacade;
		_navigationFacade = navigationFacade;
	}

	public synchronized void start()
	{
		if ((_task != null) && !_task.isDone())
		{
			return;
		}

		LOGGER.info(() -> getClass().getSimpleName() + ": Starting personality-driven task loop at interval " + _config.getDecisionIntervalMs() + " ms with planner refresh " + _config.getPlannerRefreshMs() + " ms");
		_task = ThreadPool.scheduleAtFixedRate(this::runTick, _config.getDecisionIntervalMs(), _config.getDecisionIntervalMs());
	}

	public boolean canUseSharedLocationTeleport(String fakePlayerName, Player leader)
	{
		if (!_ruleset.supportsSharedLocationPins() || (leader == null))
		{
			return false;
		}

		final FpcDefinition definition = resolveDefinition(fakePlayerName);
		if (definition == null)
		{
			return false;
		}

		final Npc npc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
		return isSharedLocationTeleportAllowed(npc, leader);
	}

	public boolean teleportToSharedLocation(String fakePlayerName, Player leader)
	{
		if (!_ruleset.supportsSharedLocationPins() || (leader == null))
		{
			return false;
		}

		final FpcDefinition definition = resolveDefinition(fakePlayerName);
		if (definition == null)
		{
			return false;
		}

		final Npc npc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
		if (!isSharedLocationTeleportAllowed(npc, leader))
		{
			return false;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final Location sharedLocation = new Location(leader.getX(), leader.getY(), leader.getZ(), leader.getHeading());
		npc.stopMove(null);
		npc.abortCast();
		if (npc.isAttackable())
		{
			final Attackable attackable = npc.asAttackable();
			attackable.abortAttack();
			attackable.setTarget(null);
			attackable.clearAggroList();
		}
		npc.getAI().setIntention(Intention.ACTIVE);
		npc.teleToLocation(sharedLocation.getX(), sharedLocation.getY(), sharedLocation.getZ(), sharedLocation.getHeading());
		recordZonePresence(definitionKey, _routeService.resolveCurrentZone(npc, definition.getSpawnProfile().getZone()));
		ensureProgressMarker(definitionKey, npc);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " consumed shared location from leader=" + leader.getName() + " x=" + sharedLocation.getX() + " y=" + sharedLocation.getY() + " z=" + sharedLocation.getZ());
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=shared_location_teleport leader=" + leader.getName() + " x=" + sharedLocation.getX() + " y=" + sharedLocation.getY() + " z=" + sharedLocation.getZ());
		return true;
	}

	public boolean promptImmediateFollow(String fakePlayerName, Player leader)
	{
		if ((leader == null) || (_hybridPartyService == null))
		{
			return false;
		}

		final FpcDefinition definition = resolveDefinition(fakePlayerName);
		if (definition == null)
		{
			return false;
		}

		final Npc activeNpc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
		if ((activeNpc == null) || !activeNpc.isAttackable() || activeNpc.isDead() || activeNpc.isDecayed())
		{
			return false;
		}

		final Attackable npc = activeNpc.asAttackable();
		final String definitionKey = definition.getId().toLowerCase();
		final long followDistanceSq = (long) COMPANION_DIRECTIVE_CLOSE_FOLLOW_RANGE * COMPANION_DIRECTIVE_CLOSE_FOLLOW_RANGE;
		armDirectiveFollowWindow(definition.getId());
		disengage(npc);
		return applyForcedDirectiveFollow(definition, npc, leader, definitionKey);
	}

	private FpcDefinition resolveDefinition(String fakePlayerName)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank())
		{
			return null;
		}

		FpcDefinition definition = _profileData.getRegistry().getDefinitionByName(fakePlayerName);
		if (definition == null)
		{
			definition = _profileData.getRegistry().getDefinitionById(fakePlayerName);
		}
		return definition;
	}

	private boolean isSharedLocationTeleportAllowed(Npc npc, Player leader)
	{
		if ((npc == null) || (leader == null) || npc.isDead() || npc.isDecayed())
		{
			return false;
		}
		if ((npc.getInstanceId() > 0) || (leader.getInstanceId() > 0))
		{
			return false;
		}
		if (npc.isInsideZone(ZoneId.SIEGE) || leader.isInsideZone(ZoneId.SIEGE))
		{
			return false;
		}
		return true;
	}

	private void runTick()
	{
		for (FpcDefinition definition : _profileData.getDefinitions())
		{
			try
			{
				processDefinition(definition);
			}
			catch (Exception e)
			{
				LOGGER.warning(getClass().getSimpleName() + ": Failed task tick for FPC " + definition.getId() + " -> " + e.getMessage());
			}
		}
	}

	private void processDefinition(FpcDefinition definition)
	{
		if ((definition == null) || !definition.isEnabled())
		{
			return;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final Object spawn = _profileData.getRegistry().getActiveSpawn(definition.getId());
		if (spawn == null)
		{
			handleInactiveSpawn(definition, definitionKey, false);
			return;
		}

		final Npc npc = _profileData.getRegistry().resolveLastSpawnNpc(definition.getId());
		if ((npc == null) || npc.isDead())
		{
			handleInactiveSpawn(definition, definitionKey, true);
			return;
		}

		_respawnReadyTimes.remove(definitionKey);
		processActiveDefinition(definition, definitionKey, npc);
	}

	private void processActiveDefinition(FpcDefinition definition, String definitionKey, Npc npc)
	{
		if ((definition == null) || (npc == null))
		{
			return;
		}
		if (isAdminHoldActive(definition, definitionKey, npc))
		{
			return;
		}

		final String currentZone = _routeService.resolveCurrentZone(npc, definition.getSpawnProfile().getZone());
		recordZonePresence(definitionKey, currentZone);
		final double boredomScore = calculateBoredom(definitionKey);
		final String state = npc.isMoving() ? "moving" : "idle";
		final FakePlayerRuntimeSnapshot snapshot = _decisionEngine.createSnapshot(definition.getName(), definition.getArchetype(), currentZone, state, resolveBand(npc.getCurrentHp(), npc.getMaxHp()), resolveBand(npc.getCurrentMp(), npc.getMaxMp()), countNearbyPlayers(npc), "none", boredomScore);
		final String recentEvent = (boredomScore >= 1.0) ? "zone_boredom" : (npc.isMoving() ? "moving" : "idle_timeout");
		requestPlannerRefreshIfDue(definition, snapshot, recentEvent);
		final FakePlayerAdvisoryPlan plan = resolveActivePlannerPlan(definitionKey, snapshot, recentEvent);

		if (!applyAdventurerAutomation(definition, npc, currentZone, plan))
		{
			applyMovement(definition, npc, currentZone, plan);
		}
		tryEmitPendingSpeech(definitionKey);
	}

	private void handleInactiveSpawn(FpcDefinition definition, String definitionKey, boolean hadActiveSpawn)
	{
		final Long readyAt = _respawnReadyTimes.get(definitionKey);
		final long now = System.currentTimeMillis();
		if (readyAt == null)
		{
			if (!hadActiveSpawn)
			{
				return;
			}

			final long scheduledRespawn = now + Math.max(_config.getRespawnDelayMs(), 5000L);
			_respawnReadyTimes.put(definitionKey, scheduledRespawn);
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC death detected for " + definition.getId() + ", respawn scheduled in " + (scheduledRespawn - now) + " ms");
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=death_detected respawnInMs=" + (scheduledRespawn - now));
			return;
		}

		if (now < readyAt.longValue())
		{
			return;
		}

		if (hadActiveSpawn)
		{
			_spawnService.despawnFpc(definition.getId(), _profileData.getRegistry());
		}
		clearRuntimeState(definitionKey);
		final boolean success = _spawnService.spawnFpc(definition, _profileData.getRegistry());
		if (success)
		{
			_targetingService.clearTeleportUsage(definition.getName());
			_respawnReadyTimes.remove(definitionKey);
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC respawned id=" + definition.getId() + " after death recovery with travel cooldown reset");
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=respawned travelCooldownReset=true");
		}
		else
		{
			final long retryAt = now + Math.max(_config.getRespawnDelayMs(), 5000L);
			_respawnReadyTimes.put(definitionKey, retryAt);
			LOGGER.warning(getClass().getSimpleName() + ": AFPC respawn retry scheduled for " + definition.getId() + " after failed recovery spawn.");
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=respawn_retry_scheduled");
		}
	}

	private boolean applyAdventurerAutomation(FpcDefinition definition, Npc npc, String currentZone, FakePlayerAdvisoryPlan plan)
	{
		if ((definition == null) || (npc == null) || !definition.getAdventurerProfile().isAdventurerTier() || !"synthetic".equalsIgnoreCase(definition.getAdventurerProfile().getCombatMode()) || !(npc instanceof Attackable))
		{
			return false;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final int effectiveLevel = _combatPowerService.resolveEffectiveLevel(definition, npc.getTemplate().getLevel());
		final FpcAdventurerRoute route = resolveActiveAdventurerRoute(definition, effectiveLevel, definitionKey);
		if (route == null)
		{
			return false;
		}

		final Attackable combatNpc = (Attackable) npc;
		final ResolvedFpcCombatPower combatPower = resolveCombatPower(definition, combatNpc);
		suppressSpellOnlyBasicAttack(definition, combatNpc, combatPower, "automation_tick");
		applyCompanionPeaceZoneSafety(definition, combatNpc, definitionKey);
		final Location arrivalLocation = _routeService.resolveArrivalLocation(route, definition);
		final Location farmAnchor = _routeService.resolveFarmAnchor(route, definition);
		final Player contractedLeader = (_hybridPartyService != null) ? _hybridPartyService.getActivePlayerLeader(definition.getId()) : null;
		if (applyHybridClanWarRetreat(definition, combatNpc, definitionKey, route, effectiveLevel))
		{
			return true;
		}
		if (applyPersonalityConflictEngagement(definition, combatNpc, definitionKey))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}
		if (applyProactivePersonalityThreatEngagement(definition, combatNpc, definitionKey))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}
		if (applySyntheticScenarioAutomation(definition, combatNpc, currentZone, combatPower, definitionKey))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}
		if (applyPlayerCompanionAutomation(definition, combatNpc, route, contractedLeader, combatPower, definitionKey))
		{
			return true;
		}
		if (applyPostPartyFarmBuffer(definition, combatNpc, currentZone, combatPower, definitionKey))
		{
			return true;
		}
		if (shouldUseCompanionHomeAnchor(definition))
		{
			return applyCompanionHomeAnchorBehavior(definition, combatNpc, currentZone, definitionKey);
		}
		if (tryResumeAfterAdminRecall(definition, combatNpc, currentZone, route, definitionKey, arrivalLocation, farmAnchor))
		{
			return true;
		}
		if (_adventurerCombatService.maintainOffscreenPreparationState(definition, combatNpc, combatPower))
		{
			ensureProgressMarker(definitionKey, combatNpc);
		}
		if (shouldRecover(combatNpc))
		{
			final boolean emergencyRecoverTeleport = shouldEmergencyRecoverTeleport(combatNpc);
			disengage(combatNpc);
			if (emergencyRecoverTeleport && tryEmergencyRecoverTeleport(definition, combatNpc, route, definitionKey, arrivalLocation, currentZone))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				LOGGER.fine(() -> getClass().getSimpleName() + ": Recovery emergency teleport applied for AFPC " + definition.getId() + " route=" + route.getId() + " hp=" + combatNpc.getCurrentHp() + "/" + combatNpc.getMaxHp());
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=recover_emergency_teleport route=" + route.getId() + " hp=" + (int) combatNpc.getCurrentHp() + "/" + (int) combatNpc.getMaxHp());
				return true;
			}
			ensureProgressMarker(definitionKey, combatNpc);
			moveToward(definition, combatNpc, currentZone, route.getArrivalZoneId(), arrivalLocation, "recover");
			LOGGER.fine(() -> getClass().getSimpleName() + ": Recovery advisory applied for AFPC " + definition.getId() + " route=" + route.getId() + " hp=" + combatNpc.getCurrentHp() + "/" + combatNpc.getMaxHp());
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=recover route=" + route.getId() + " hp=" + (int) combatNpc.getCurrentHp() + "/" + (int) combatNpc.getMaxHp());
			return true;
		}
		if (applyHybridClanWarEngagement(definition, combatNpc))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}

		if (maintainCombat(combatNpc, farmAnchor, route, definition, combatPower))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}

		if (isAntiStuckTriggered(definitionKey, combatNpc))
		{
			disengage(combatNpc);
			if (_routeService.isWithinZoneRadius(combatNpc, route.getFarmZoneId(), route.getLeashRadius()))
			{
				final Location unstuckTarget = _routeService.pickRoamTarget(route.getFarmZoneId(), definition, combatNpc);
				if (distanceSquared(combatNpc, unstuckTarget) >= 10000L)
				{
					combatNpc.setRunning();
					combatNpc.moveToLocation(unstuckTarget.getX(), unstuckTarget.getY(), unstuckTarget.getZ(), 0);
					_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
					ensureProgressMarker(definitionKey, combatNpc);
					LOGGER.fine(() -> getClass().getSimpleName() + ": Anti-stuck locally repositioned AFPC " + definition.getId() + " route=" + route.getId() + " farmZone=" + route.getFarmZoneId() + " target=" + unstuckTarget.getX() + "," + unstuckTarget.getY() + "," + unstuckTarget.getZ());
					_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=anti_stuck_local route=" + route.getId() + " target=" + unstuckTarget.getX() + "," + unstuckTarget.getY() + "," + unstuckTarget.getZ());
					return true;
				}
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			if (_targetingService.isTeleportCooldownReady(definition.getName()))
			{
				combatNpc.teleToLocation(farmAnchor.getX(), farmAnchor.getY(), farmAnchor.getZ(), farmAnchor.getHeading());
				_targetingService.markTeleportUsed(definition.getName());
				recordZonePresence(definitionKey, route.getFarmZoneId());
				ensureProgressMarker(definitionKey, combatNpc);
				LOGGER.fine(() -> getClass().getSimpleName() + ": Anti-stuck teleported AFPC " + definition.getId() + " to route=" + route.getId() + " farmZone=" + route.getFarmZoneId());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=anti_stuck_teleport route=" + route.getId() + " farmZone=" + route.getFarmZoneId());
				return true;
			}
			moveToward(definition, combatNpc, currentZone, route.getFarmZoneId(), farmAnchor, "recover");
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}

		final boolean inArrivalZone = isWithinRouteZone(combatNpc, currentZone, route.getArrivalZoneId(), route.getLeashRadius());
		final boolean inFarmZone = isWithinRouteZone(combatNpc, currentZone, route.getFarmZoneId(), route.getLeashRadius());
		if (!inArrivalZone && !inFarmZone)
		{
			if (_targetingService.isTeleportCooldownReady(definition.getName()))
			{
				combatNpc.teleToLocation(arrivalLocation.getX(), arrivalLocation.getY(), arrivalLocation.getZ(), arrivalLocation.getHeading());
				_targetingService.markTeleportUsed(definition.getName());
				recordZonePresence(definitionKey, route.getArrivalZoneId());
				ensureProgressMarker(definitionKey, combatNpc);
				LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " used teleportId=" + route.getTeleportId() + " route=" + route.getId() + " arrivalZone=" + route.getArrivalZoneId());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=route_teleport teleportId=" + route.getTeleportId() + " route=" + route.getId() + " arrivalZone=" + route.getArrivalZoneId());
				return true;
			}
			moveToward(definition, combatNpc, currentZone, route.getArrivalZoneId(), arrivalLocation, "travel");
			return true;
		}
		if (shouldHoldRecoveryAtArrival(combatNpc, route, currentZone))
		{
			applyRecoveryHold(definitionKey, combatNpc);
			return true;
		}

		if (!inFarmZone)
		{
			moveToward(definition, combatNpc, currentZone, route.getFarmZoneId(), farmAnchor, "travel");
			return true;
		}

		if (_adventurerCombatService.tryUsePreparationSkill(definition, combatNpc, combatPower))
		{
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}

		final Monster assistTarget = _adventurerCombatService.selectAssistTarget(definition, combatNpc, route, farmAnchor, _profileData.getRegistry());
		if (assistTarget != null)
		{
			if (shouldPreserveCastIntent(definition, combatNpc, assistTarget, "assist"))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			if (_adventurerCombatService.tryUsePreparationSkill(definition, combatNpc, combatPower))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			if (_adventurerCombatService.tryUseOffensiveSkill(definition, combatNpc, assistTarget, combatPower, "assist"))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			beginAttack(definition, combatNpc, assistTarget, route, "assist", combatPower);
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}

		final Monster target = _targetingService.selectFarmTarget(combatNpc, route, farmAnchor);
		if (target != null)
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=farm_target_acquired route=" + route.getId() + " target=" + target.getName() + "(" + target.getId() + ")");
			if (shouldPreserveCastIntent(definition, combatNpc, target, "solo"))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			if (_adventurerCombatService.tryUsePreparationSkill(definition, combatNpc, combatPower))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			if (_adventurerCombatService.tryUseOffensiveSkill(definition, combatNpc, target, combatPower, "solo"))
			{
				ensureProgressMarker(definitionKey, combatNpc);
				return true;
			}
			beginAttack(definition, combatNpc, target, route, "solo", combatPower);
			ensureProgressMarker(definitionKey, combatNpc);
			return true;
		}
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=no_farm_target route=" + route.getId() + " zone=" + route.getFarmZoneId());

		if (!combatNpc.isMoving() && isMoveCadenceReady(definition.getId()))
		{
			final Location roamTarget = _routeService.pickRoamTarget(route.getFarmZoneId(), definition, combatNpc);
			if (distanceSquared(combatNpc, roamTarget) >= 10000L)
			{
				combatNpc.setRunning();
				combatNpc.moveToLocation(roamTarget.getX(), roamTarget.getY(), roamTarget.getZ(), 0);
				_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
				ensureProgressMarker(definitionKey, combatNpc);
				LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " patrolling farm zone route=" + route.getId() + " target=" + roamTarget.getX() + "," + roamTarget.getY() + "," + roamTarget.getZ());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=patrol route=" + route.getId() + " target=" + roamTarget.getX() + "," + roamTarget.getY() + "," + roamTarget.getZ());
				return true;
			}
		}

		return true;
	}

	private boolean applySyntheticScenarioAutomation(FpcDefinition definition, Attackable npc, String currentZone, ResolvedFpcCombatPower combatPower, String definitionKey)
	{
		if ((_syntheticScenarioService == null) || (definition == null) || (npc == null) || (combatPower == null))
		{
			return false;
		}

		final FpcSyntheticScenarioDefinition scenario = _syntheticScenarioService.resolveScenario(definition);
		if (scenario == null)
		{
			return false;
		}

		final ScenarioProgress progress = _syntheticScenarioService.getOrStartProgress(definition, scenario);
		if ((progress == null) || progress.isCompleted())
		{
			return false;
		}

		if (progress.consumeStartAnnouncement())
		{
			broadcastSyntheticScenarioLine(npc, scenario.getStartLine());
		}
		if (progress.isObjectiveComplete() || (progress.getTargetCount() >= scenario.getTargetCount()))
		{
			return applySyntheticScenarioReturn(definition, npc, currentZone, scenario, progress, definitionKey);
		}

		final Location objectiveAnchor = _routeService.resolveAnchor(scenario.getObjectiveZoneId(), definition);
		if (!isInSyntheticScenarioZone(npc, currentZone, scenario.getObjectiveZoneId(), scenario.getLeashRadius()))
		{
			disengage(npc);
			final String stagingZoneId = scenario.getStagingZoneId();
			if (!stagingZoneId.isBlank())
			{
				final Location stagingAnchor = _routeService.resolveAnchor(stagingZoneId, definition);
				if (!isInSyntheticScenarioZone(npc, currentZone, stagingZoneId, scenario.getReturnRadius()))
				{
					if (progress.consumeStagingTeleport())
					{
						npc.teleToLocation(stagingAnchor.getX(), stagingAnchor.getY(), stagingAnchor.getZ(), stagingAnchor.getHeading());
						recordZonePresence(definitionKey, stagingZoneId);
						LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " used synthetic scenario staging teleport scenario=" + scenario.getId() + " stagingZone=" + stagingZoneId + " objectiveZone=" + scenario.getObjectiveZoneId());
						_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=synthetic_scenario_staging_teleport scenario=" + scenario.getId() + " stagingZone=" + stagingZoneId + " objectiveZone=" + scenario.getObjectiveZoneId());
						return true;
					}

					moveToward(definition, npc, currentZone, stagingZoneId, stagingAnchor, "synthetic_scenario_staging");
					return true;
				}

				moveToward(definition, npc, currentZone, scenario.getObjectiveZoneId(), objectiveAnchor, "synthetic_scenario_objective_walk");
				return true;
			}
			if (_targetingService.isTeleportCooldownReady(definition.getName()))
			{
				npc.teleToLocation(objectiveAnchor.getX(), objectiveAnchor.getY(), objectiveAnchor.getZ(), objectiveAnchor.getHeading());
				_targetingService.markTeleportUsed(definition.getName());
				recordZonePresence(definitionKey, scenario.getObjectiveZoneId());
				LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " started synthetic scenario=" + scenario.getId() + " sourceQuestId=" + scenario.getSourceQuestId() + " objectiveZone=" + scenario.getObjectiveZoneId());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=synthetic_scenario_teleport scenario=" + scenario.getId() + " objectiveZone=" + scenario.getObjectiveZoneId());
				return true;
			}

			moveToward(definition, npc, currentZone, scenario.getObjectiveZoneId(), objectiveAnchor, "synthetic_scenario_travel");
			return true;
		}

		if (maintainSyntheticScenarioCombat(definition, npc, scenario, objectiveAnchor, combatPower))
		{
			return true;
		}
		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			return true;
		}

		final Monster target = selectSyntheticScenarioTarget(npc, scenario, objectiveAnchor);
		if (target != null)
		{
			if (shouldResolveSyntheticScenarioOffscreen(npc) && _syntheticScenarioService.recordObjectiveProgress(definition, scenario, target.getId(), "offscreen"))
			{
				npc.setTarget(null);
				LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " advanced synthetic scenario=" + scenario.getId() + " offscreen target=" + target.getName() + "(" + target.getId() + ") count=" + progress.getTargetCount() + "/" + scenario.getTargetCount());
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=synthetic_scenario_offscreen_progress scenario=" + scenario.getId() + " target=" + target.getName() + "(" + target.getId() + ")");
				return true;
			}
			if (shouldPreserveCastIntent(definition, npc, target, "synthetic_scenario"))
			{
				return true;
			}
			if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, "synthetic_scenario"))
			{
				return true;
			}
			beginSyntheticScenarioAttack(definition, npc, target, scenario, combatPower, "engage");
			return true;
		}

		if (!npc.isMoving() && isMoveCadenceReady(definition.getId()))
		{
			final Location roamTarget = _routeService.pickRoamTarget(scenario.getObjectiveZoneId(), definition, npc);
			if (distanceSquared(npc, roamTarget) >= 10000L)
			{
				npc.setRunning();
				npc.moveToLocation(roamTarget.getX(), roamTarget.getY(), roamTarget.getZ(), 0);
				_lastMoveTimes.put(definitionKey, System.currentTimeMillis());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=synthetic_scenario_patrol scenario=" + scenario.getId() + " target=" + roamTarget.getX() + "," + roamTarget.getY() + "," + roamTarget.getZ());
				return true;
			}
		}

		return true;
	}

	private boolean applySyntheticScenarioReturn(FpcDefinition definition, Attackable npc, String currentZone, FpcSyntheticScenarioDefinition scenario, ScenarioProgress progress, String definitionKey)
	{
		final String returnZoneId = scenario.getReturnZoneId();
		final Location returnAnchor = _routeService.resolveAnchor(returnZoneId, definition);
		if (isInSyntheticScenarioZone(npc, currentZone, returnZoneId, scenario.getReturnRadius()))
		{
			disengage(npc);
			if (_syntheticScenarioService.markCompleted(definition, scenario) && progress.consumeCompleteAnnouncement())
			{
				broadcastSyntheticScenarioLine(npc, scenario.getCompleteLine());
			}
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=synthetic_scenario_returned scenario=" + scenario.getId() + " count=" + progress.getTargetCount() + "/" + scenario.getTargetCount());
			return true;
		}

		disengage(npc);
		if (progress.consumeReturnTeleport() && !returnZoneId.equalsIgnoreCase(currentZone))
		{
			npc.teleToLocation(returnAnchor.getX(), returnAnchor.getY(), returnAnchor.getZ(), returnAnchor.getHeading());
			recordZonePresence(definitionKey, returnZoneId);
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " used synthetic scenario return teleport scenario=" + scenario.getId() + " returnZone=" + returnZoneId);
			_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=synthetic_scenario_return_teleport scenario=" + scenario.getId() + " returnZone=" + returnZoneId);
			return true;
		}

		moveToward(definition, npc, currentZone, returnZoneId, returnAnchor, "synthetic_scenario_return");
		return true;
	}

	private boolean maintainSyntheticScenarioCombat(FpcDefinition definition, Attackable npc, FpcSyntheticScenarioDefinition scenario, Location objectiveAnchor, ResolvedFpcCombatPower combatPower)
	{
		if ((npc == null) || (scenario == null) || (objectiveAnchor == null) || (npc.getTarget() == null) || !npc.getTarget().isMonster())
		{
			return false;
		}
		final Monster currentTarget = npc.getTarget().asMonster();
		if (isValidSyntheticScenarioTarget(npc, currentTarget, scenario, objectiveAnchor))
		{
			return tryMaintainMonsterCombatWindow(definition, npc, currentTarget, combatPower, "synthetic_scenario_maintain", null, "synthetic_scenario_maintain",
				() -> holdSyntheticScenarioSpellCombat(definition, npc, currentTarget, scenario, "maintain_spell_only"),
				() ->
				{
					beginSyntheticScenarioAttack(definition, npc, currentTarget, scenario, combatPower, "maintain");
					return true;
				});
		}

		if (npc.isInCombat() && npc.isCastingNow())
		{
			return true;
		}
		if (npc.isInCombat() && handleInCombatMonsterSpellOnlyWindow(definition, npc, combatPower, null, null, "synthetic_scenario_in_combat"))
		{
			return true;
		}
		if (npc.isInCombat() && npc.isAttackingNow())
		{
			return true;
		}

		disengage(npc);
		return false;
	}

	private Monster selectSyntheticScenarioTarget(Attackable npc, FpcSyntheticScenarioDefinition scenario, Location objectiveAnchor)
	{
		final Monster[] selectedTarget = new Monster[1];
		final long[] bestDistance = new long[]
		{
			Long.MAX_VALUE
		};

		_worldFacade.forEachVisibleObjectInRange(npc, Monster.class, scenario.getSearchRadius(), monster ->
		{
			if (!isValidSyntheticScenarioTarget(npc, monster, scenario, objectiveAnchor))
			{
				return;
			}

			final long distance = distanceSquared(npc.getX(), npc.getY(), monster.getX(), monster.getY());
			if (distance < bestDistance[0])
			{
				bestDistance[0] = distance;
				selectedTarget[0] = monster;
			}
		});
		return selectedTarget[0];
	}

	private boolean isValidSyntheticScenarioTarget(Attackable npc, Monster monster, FpcSyntheticScenarioDefinition scenario, Location objectiveAnchor)
	{
		if ((npc == null) || (monster == null) || (scenario == null) || (objectiveAnchor == null) || monster.isDead() || monster.isRaid() || monster.isFakePlayer() || !scenario.matchesTargetNpcId(monster.getId()))
		{
			return false;
		}
		if (monster.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || (monster.isInCombat() && (monster.getTarget() != npc)))
		{
			return false;
		}
		if (distanceSquared(monster.getX(), monster.getY(), objectiveAnchor.getX(), objectiveAnchor.getY()) > ((long) scenario.getLeashRadius() * scenario.getLeashRadius()))
		{
			return false;
		}
		return _navigationFacade.canSeeTarget(npc, monster);
	}

	private boolean isInSyntheticScenarioZone(Npc npc, String currentZone, String zoneId, int radius)
	{
		return (zoneId != null) && _routeService.isWithinZoneRadius(npc, zoneId, Math.max(radius, 100));
	}

	private boolean shouldResolveSyntheticScenarioOffscreen(Npc npc)
	{
		if ((npc == null) || npc.isInCombat() || npc.isCastingNow() || npc.isAttackingNow())
		{
			return false;
		}

		final WorldRegion region = npc.getWorldRegion();
		return (region == null) || !region.areNeighborsActive();
	}

	private void beginSyntheticScenarioAttack(FpcDefinition definition, Attackable npc, Monster target, FpcSyntheticScenarioDefinition scenario, ResolvedFpcCombatPower combatPower, String source)
	{
		beginMonsterAttackWindow(definition, npc, target, combatPower,
			() -> holdSyntheticScenarioSpellCombat(definition, npc, target, scenario, source),
			() ->
			{
				npc.getAI().setIntention(Intention.ATTACK, target);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=synthetic_scenario_engage scenario=" + scenario.getId() + " source=" + source + " target=" + target.getName() + "(" + target.getId() + ")");
			});
	}

	private void holdSyntheticScenarioSpellCombat(FpcDefinition definition, Attackable npc, Monster target, FpcSyntheticScenarioDefinition scenario, String source)
	{
		holdSpellCombatWindow(definition, npc, target, source,
			() ->
			{
				if (npc.isMoving())
				{
					npc.stopMove(null);
				}
				if (npc.getAI().getIntention() == Intention.ATTACK)
				{
					npc.getAI().setIntention(Intention.ACTIVE);
				}
			},
			() -> _debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=synthetic_scenario_hold_spell scenario=" + scenario.getId() + " source=" + source + " target=" + target.getName() + "(" + target.getId() + ")"));
	}

	private void broadcastSyntheticScenarioLine(Npc npc, String line)
	{
		if ((npc != null) && (line != null) && !line.isBlank())
		{
			npc.broadcastSay(ChatType.GENERAL, line);
		}
	}

	private boolean applyPlayerCompanionAutomation(FpcDefinition definition, Attackable npc, FpcAdventurerRoute route, Player leader, ResolvedFpcCombatPower combatPower, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (route == null) || (leader == null))
		{
			return false;
		}

		final long followDistanceSq = (long) Math.max(_config.getPartyFollowRange(), 150) * Math.max(_config.getPartyFollowRange(), 150);
		final long teleportDistanceSq = (long) Math.max(_config.getPartyTeleportRange(), _config.getPartyFollowRange()) * Math.max(_config.getPartyTeleportRange(), _config.getPartyFollowRange());
		if (leader.isDead())
		{
			if (shouldRetainStickyCompanionContract(definition))
			{
				return holdForDeadLeader(definition, npc, leader, definitionKey, followDistanceSq, teleportDistanceSq);
			}
			_hybridPartyService.clearHybridContract(definition.getId(), "leader_dead");
			return false;
		}
		if (leader.getInstanceId() != npc.getInstanceId())
		{
			if (!tryFollowLeaderAcrossInstance(definition, npc, leader, definitionKey))
			{
				_hybridPartyService.clearHybridContract(definition.getId(), "instance_mismatch");
				return false;
			}
		}
		if ((_hybridPartyService != null) && (leader.isMoving() || leader.isInCombat()))
		{
			_hybridPartyService.markLeaderActivity(definition.getId(), leader, leader.isInCombat() ? "combat" : "move");
		}

		final Location leaderLocation = leader.getLocation();
		final Location leaderAnchor = new Location(leaderLocation.getX(), leaderLocation.getY(), leaderLocation.getZ(), leaderLocation.getHeading());
		if (applyForcedDirectiveFollow(definition, npc, leader, definitionKey))
		{
			return true;
		}
		if (shouldRecover(npc))
		{
			disengage(npc);
			if (distanceSquared(npc, leaderAnchor) > followDistanceSq)
			{
				regroupToLeader(definition, npc, leader, leaderAnchor, definitionKey, teleportDistanceSq, false);
			}
			return true;
		}

		if (tryRokhanPartySupport(definition, npc, leader, combatPower, definitionKey))
		{
			return true;
		}

		final Monster leaderTarget = resolveLeaderAssistTarget(definition, npc, leader, leaderAnchor, route);
		if (leaderTarget != null)
		{
			if (shouldPreserveCastIntent(definition, npc, leaderTarget, "player_assist"))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, leaderTarget, combatPower, "player_assist"))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			beginAttack(definition, npc, leaderTarget, route, "player_assist", combatPower);
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		if (maintainCombat(npc, leaderAnchor, route, definition, combatPower))
		{
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		if (distanceSquared(npc, leaderAnchor) > followDistanceSq)
		{
			regroupToLeader(definition, npc, leader, leaderAnchor, definitionKey, teleportDistanceSq, false);
			return true;
		}

		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		if ((npc.getAI().getIntention() != Intention.FOLLOW) || (npc.getTarget() != leader))
		{
			npc.setRunning();
			npc.setTarget(leader);
			npc.getAI().setIntention(Intention.FOLLOW, leader);
			ensureProgressMarker(definitionKey, npc);
			LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " following contracted leader=" + leader.getName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=follow_leader leader=" + leader.getName());
		}
		final FakePlayerModule module = FakePlayerModule.getInstance();
		if (module != null)
		{
			module.maybeTriggerCompanionFollowup(definition, npc, leader);
		}
		return true;
	}

	private boolean shouldRetainStickyCompanionContract(FpcDefinition definition)
	{
		final FpcCompanionProfile companionProfile = (definition != null) ? definition.getCompanionProfile() : null;
		return (companionProfile != null) && companionProfile.isStickySession();
	}

	private boolean holdForDeadLeader(FpcDefinition definition, Attackable npc, Player leader, String definitionKey, long followDistanceSq, long teleportDistanceSq)
	{
		if ((definition == null) || (npc == null) || (leader == null))
		{
			return false;
		}

		final Location leaderAnchor = new Location(leader.getX(), leader.getY(), leader.getZ(), leader.getHeading());
		disengage(npc);
		if (distanceSquared(npc, leaderAnchor) > followDistanceSq)
		{
			regroupToLeaderCorpse(definition, npc, leader, leaderAnchor, definitionKey, teleportDistanceSq);
			return true;
		}
		if (npc.isMoving())
		{
			npc.stopMove(null);
		}
		if (npc.getAI().getIntention() != Intention.ACTIVE)
		{
			npc.getAI().setIntention(Intention.ACTIVE);
		}
		npc.setTarget(leader);
		ensureProgressMarker(definitionKey, npc);
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=leader_dead_hold leader=" + leader.getName() + " x=" + leaderAnchor.getX() + " y=" + leaderAnchor.getY() + " z=" + leaderAnchor.getZ());
		return true;
	}

	private boolean shouldUseCompanionHomeAnchor(FpcDefinition definition)
	{
		if (definition == null)
		{
			return false;
		}

		final FpcCompanionProfile companionProfile = definition.getCompanionProfile();
		return (companionProfile != null) && companionProfile.isHomeAnchorIdleBehavior();
	}

	private void applyCompanionPeaceZoneSafety(FpcDefinition definition, Attackable npc, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (definitionKey == null))
		{
			return;
		}

		if (definition.getCompanionProfile() == null)
		{
			clearCompanionPeaceZoneSafety(definitionKey, npc);
			return;
		}

		final boolean safeZone = npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.NO_PVP);
		if (safeZone)
		{
			if (_peaceZoneSafetyInvul.putIfAbsent(definitionKey, Boolean.TRUE) == null)
			{
				npc.setInvul(true);
				_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=companion_peace_invul enabled=true");
			}
			return;
		}

		clearCompanionPeaceZoneSafety(definitionKey, npc);
	}

	private void clearCompanionPeaceZoneSafety(String definitionKey, Attackable npc)
	{
		if ((definitionKey == null) || (npc == null))
		{
			return;
		}

		if (_peaceZoneSafetyInvul.remove(definitionKey) != null)
		{
			npc.setInvul(false);
		}
	}

	private boolean applyPostPartyFarmBuffer(FpcDefinition definition, Attackable npc, String currentZone, ResolvedFpcCombatPower combatPower, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (combatPower == null) || !shouldUseCompanionHomeAnchor(definition) || (_hybridPartyService == null))
		{
			return false;
		}

		final PostPartyFarmState state = getOrCreatePostPartyFarmState(definition, npc, definitionKey);
		if (state == null)
		{
			return false;
		}

		if (maintainPostPartyFarmCombat(definition, npc, state, combatPower))
		{
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		final Monster target = selectPostPartyFarmTarget(npc, state);
		if (target != null)
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_target target=" + target.getName() + "(" + target.getId() + ") reason=" + state._reason);
			if (shouldPreserveCastIntent(definition, npc, target, "post_party_farm"))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, "post_party_farm"))
			{
				ensureProgressMarker(definitionKey, npc);
				return true;
			}
			beginPostPartyFarmAttack(definition, npc, target, state, combatPower);
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		if (!npc.isMoving() && isMoveCadenceReady(definition.getId()))
		{
			final Location roamTarget = pickPostPartyRoamTarget(currentZone, definition, npc, state);
			if ((roamTarget != null) && (distanceSquared(npc, roamTarget) >= 10000L))
			{
				npc.setRunning();
				npc.moveToLocation(roamTarget.getX(), roamTarget.getY(), roamTarget.getZ(), 0);
				_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
				ensureProgressMarker(definitionKey, npc);
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=post_party_farm_roam target=" + roamTarget.getX() + "," + roamTarget.getY() + "," + roamTarget.getZ() + " reason=" + state._reason);
				return true;
			}
		}

		ensureProgressMarker(definitionKey, npc);
		return true;
	}

	private boolean applyCompanionHomeAnchorBehavior(FpcDefinition definition, Attackable npc, String currentZone, String definitionKey)
	{
		if ((definition == null) || (npc == null))
		{
			return false;
		}

		final String homeZoneId = definition.getSpawnProfile().getZone();
		final Location homeAnchor = _routeService.resolveAnchor(homeZoneId, definition);
		final long homeDistanceSq = distanceSquared(npc, homeAnchor);
		if ((npc.getInstanceId() == 0) && (homeDistanceSq <= 22500L))
		{
			disengage(npc);
			if (npc.isMoving())
			{
				npc.stopMove(null);
			}
			if (npc.getAI().getIntention() != Intention.ACTIVE)
			{
				npc.getAI().setIntention(Intention.ACTIVE);
			}
			ensureProgressMarker(definitionKey, npc);
			return true;
		}

		disengage(npc);
		if ((npc.getInstanceId() != 0) || (homeDistanceSq > 160000L) || _targetingService.isTeleportCooldownReady(definition.getName()))
		{
			npc.teleToLocation(homeAnchor, null);
			_targetingService.markTeleportUsed(definition.getName());
			recordZonePresence(definitionKey, homeZoneId);
			ensureProgressMarker(definitionKey, npc);
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=companion_return_home mode=teleport zone=" + homeZoneId);
			return true;
		}

		moveToward(definition, npc, currentZone, homeZoneId, homeAnchor, "companion_home_anchor");
		ensureProgressMarker(definitionKey, npc);
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=companion_return_home mode=walk zone=" + homeZoneId);
		return true;
	}

	private PostPartyFarmState getOrCreatePostPartyFarmState(FpcDefinition definition, Attackable npc, String definitionKey)
	{
		final PostPartyFarmState activeState = getActivePostPartyFarmState(definition, definitionKey);
		if (activeState != null)
		{
			return activeState;
		}

		final String recentExitReason = _hybridPartyService.getRecentExitReason(definition.getId(), POST_PARTY_FARM_ARM_WINDOW_MS);
		if (!shouldArmPostPartyFarm(recentExitReason))
		{
			return null;
		}

		final PostPartyFarmState nextState = new PostPartyFarmState(new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading()), recentExitReason, System.currentTimeMillis() + POST_PARTY_FARM_DURATION_MS);
		final PostPartyFarmState existingState = _postPartyFarmStates.putIfAbsent(definitionKey, nextState);
		final PostPartyFarmState resolvedState = (existingState != null) ? existingState : nextState;
		if (existingState == null)
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=post_party_farm_start durationMs=" + POST_PARTY_FARM_DURATION_MS + " reason=" + recentExitReason + " anchor=" + npc.getX() + "," + npc.getY() + "," + npc.getZ());
		}
		return resolvedState;
	}

	private PostPartyFarmState getActivePostPartyFarmState(FpcDefinition definition, String definitionKey)
	{
		final PostPartyFarmState state = _postPartyFarmStates.get(definitionKey);
		if (state == null)
		{
			return null;
		}
		if (System.currentTimeMillis() <= state._untilMs)
		{
			return state;
		}

		_postPartyFarmStates.remove(definitionKey, state);
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=post_party_farm_complete reason=" + state._reason);
		return null;
	}

	private boolean shouldArmPostPartyFarm(String reason)
	{
		if ((reason == null) || reason.isBlank())
		{
			return false;
		}

		final String normalizedReason = reason.trim().toLowerCase();
		return normalizedReason.equals("ousted") || normalizedReason.equals("party_anchor_changed");
	}

	private boolean maintainPostPartyFarmCombat(FpcDefinition definition, Attackable npc, PostPartyFarmState state, ResolvedFpcCombatPower combatPower)
	{
		if ((definition == null) || (npc == null) || (state == null))
		{
			return false;
		}

		if ((npc.getTarget() != null) && npc.getTarget().isMonster())
		{
			final Monster currentTarget = npc.getTarget().asMonster();
			if (isValidPostPartyFarmTarget(npc, currentTarget, state))
			{
				return tryMaintainMonsterCombatWindow(definition, npc, currentTarget, combatPower, "post_party_farm_maintain", "post_party_farm_maintain", "post_party_farm_maintain",
					() -> holdPostPartyFarmSpellCombat(definition, npc, currentTarget, state, "maintain_spell_only"),
					() ->
					{
						if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower))
						{
							holdPostPartyFarmSpellCombat(definition, npc, currentTarget, state, "maintain");
							return true;
						}
						if (npc.getAI().getIntention() != Intention.ATTACK)
						{
							npc.getAI().setIntention(Intention.ATTACK, currentTarget);
							_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_reassert target=" + currentTarget.getName() + "(" + currentTarget.getId() + ") reason=" + state._reason);
						}
						return true;
					});
			}
		}

		if (!npc.isInCombat())
		{
			return false;
		}
		if (npc.isCastingNow())
		{
			return true;
		}
		if (handleInCombatMonsterSpellOnlyWindow(definition, npc, combatPower, ((npc.getTarget() != null) && npc.getTarget().isMonster()) ? npc.getTarget().asMonster() : null, () -> holdPostPartyFarmSpellCombat(definition, npc, npc.getTarget().asMonster(), state, "in_combat_spell_only"), "post_party_farm_in_combat"))
		{
			return true;
		}
		if (npc.isAttackingNow())
		{
			return true;
		}

		final Creature mostHated = npc.getMostHated();
		if ((mostHated != null) && mostHated.isMonster())
		{
			final Monster hatedTarget = mostHated.asMonster();
			if (isValidPostPartyFarmTarget(npc, hatedTarget, state))
			{
				npc.setTarget(hatedTarget);
				return tryMaintainMonsterCombatWindow(definition, npc, hatedTarget, combatPower, "post_party_farm_most_hated", "post_party_farm_most_hated", "post_party_farm_most_hated",
					() -> holdPostPartyFarmSpellCombat(definition, npc, hatedTarget, state, "most_hated_spell_only"),
					() ->
					{
						if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower))
						{
							holdPostPartyFarmSpellCombat(definition, npc, hatedTarget, state, "most_hated");
							return true;
						}
						npc.getAI().setIntention(Intention.ATTACK, hatedTarget);
						_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_reassert_most_hated target=" + hatedTarget.getName() + "(" + hatedTarget.getId() + ") reason=" + state._reason);
						return true;
					});
			}
		}

		disengage(npc);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_clear_stale_combat reason=" + state._reason);
		return false;
	}

	private Monster selectPostPartyFarmTarget(Attackable npc, PostPartyFarmState state)
	{
		if ((npc == null) || (state == null))
		{
			return null;
		}

		final Monster[] selectedTarget = new Monster[1];
		final long[] bestDistance = new long[]
		{
			Long.MAX_VALUE
		};
		_worldFacade.forEachVisibleObjectInRange(npc, Monster.class, POST_PARTY_FARM_SEARCH_RADIUS, monster ->
		{
			if (!isValidPostPartyFarmTarget(npc, monster, state))
			{
				return;
			}

			final long distance = distanceSquared(npc.getX(), npc.getY(), monster.getX(), monster.getY());
			if (distance < bestDistance[0])
			{
				bestDistance[0] = distance;
				selectedTarget[0] = monster;
			}
		});
		return selectedTarget[0];
	}

	private boolean isValidPostPartyFarmTarget(Attackable npc, Monster monster, PostPartyFarmState state)
	{
		if ((npc == null) || (monster == null) || (state == null) || (state._anchor == null) || monster.isDead() || monster.isRaid() || monster.isQuestMonster() || monster.isFakePlayer())
		{
			return false;
		}
		if (monster.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || (monster.isInCombat() && (monster.getTarget() != npc)))
		{
			return false;
		}
		if (distanceSquared(monster.getX(), monster.getY(), state._anchor.getX(), state._anchor.getY()) > ((long) POST_PARTY_FARM_LEASH_RADIUS * POST_PARTY_FARM_LEASH_RADIUS))
		{
			return false;
		}
		return _navigationFacade.canSeeTarget(npc, monster);
	}

	private void beginPostPartyFarmAttack(FpcDefinition definition, Attackable npc, Monster target, PostPartyFarmState state, ResolvedFpcCombatPower combatPower)
	{
		beginMonsterAttackWindow(definition, npc, target, combatPower,
			() -> holdPostPartyFarmSpellCombat(definition, npc, target, state, "engage"),
			() ->
			{
				npc.getAI().setIntention(Intention.ATTACK, target);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_engage target=" + target.getName() + "(" + target.getId() + ") reason=" + state._reason);
			});
	}

	private void holdPostPartyFarmSpellCombat(FpcDefinition definition, Attackable npc, Monster target, PostPartyFarmState state, String source)
	{
		holdSpellCombatWindow(definition, npc, target, source, null, () -> _debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=post_party_farm_hold_spell target=" + target.getName() + "(" + target.getId() + ") reason=" + ((state == null) ? "" : state._reason) + " source=" + source));
	}

	private Location pickPostPartyRoamTarget(String currentZone, FpcDefinition definition, Attackable npc, PostPartyFarmState state)
	{
		if ((definition == null) || (npc == null) || (state == null) || (state._anchor == null))
		{
			return null;
		}

		final ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 6; attempt++)
		{
			final double angle = random.nextDouble(0, Math.PI * 2);
			final double distance = Math.sqrt(random.nextDouble()) * POST_PARTY_FARM_ROAM_RADIUS;
			final int x = state._anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
			final int y = state._anchor.getY() + (int) Math.round(Math.sin(angle) * distance);
			final Location desiredTarget = new Location(x, y, state._anchor.getZ(), state._anchor.getHeading());
			final Location resolvedTarget = _routeService.resolveReachableTarget(currentZone, definition, npc, desiredTarget);
			if ((resolvedTarget != null) && (distanceSquared(npc, resolvedTarget) >= 10000L) && (distanceSquared(resolvedTarget.getX(), resolvedTarget.getY(), state._anchor.getX(), state._anchor.getY()) <= ((long) POST_PARTY_FARM_LEASH_RADIUS * POST_PARTY_FARM_LEASH_RADIUS)))
			{
				return resolvedTarget;
			}
		}
		return _routeService.resolveReachableTarget(currentZone, definition, npc, state._anchor);
	}

	private boolean tryFollowLeaderAcrossInstance(FpcDefinition definition, Attackable npc, Player leader, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (leader == null) || (_hybridPartyService == null))
		{
			return false;
		}
		if (!_hybridPartyService.allowInstanceFollow(definition.getId()) || !isCompanionInstanceFollowAllowed(leader))
		{
			return false;
		}

		final Instance leaderInstance = leader.getInstanceWorld();
		final Location leaderAnchor = new Location(leader.getX(), leader.getY(), leader.getZ(), leader.getHeading());
		npc.teleToLocation(leaderAnchor, leaderInstance);
		_targetingService.markTeleportUsed(definition.getName());
		_hybridPartyService.markLeaderActivity(definition.getId(), leader, "instance_follow");
		ensureProgressMarker(definitionKey, npc);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " followed contracted leader into instance=" + leader.getInstanceId() + " leader=" + leader.getName());
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=instance_follow leader=" + leader.getName() + " instanceId=" + leader.getInstanceId());
		return true;
	}

	private boolean isCompanionInstanceFollowAllowed(Player leader)
	{
		if ((leader == null) || !leader.isOnline() || leader.isInOfflineMode() || leader.isDead())
		{
			return false;
		}
		if (leader.isInOlympiadMode() || leader.isInDuel() || leader.isJailed() || leader.isInBoat() || leader.isInVehicle() || leader.isFlyingMounted())
		{
			return false;
		}

		final Instance leaderInstance = leader.getInstanceWorld();
		return (leaderInstance == null) || !leaderInstance.isPvP();
	}

	private boolean tryRokhanPartySupport(FpcDefinition definition, Attackable npc, Player leader, ResolvedFpcCombatPower combatPower, String definitionKey)
	{
		if (!isRokhan(definition) || (npc == null) || (leader == null) || (combatPower == null) || !isRokhanPartySupportNeeded(npc, leader))
		{
			return false;
		}

		if (_adventurerCombatService.tryUseSupportSkill(definition, npc, combatPower, ROKHAN_PARTY_SUPPORT_SKILL_ID))
		{
			ensureProgressMarker(definitionKey, npc);
			return true;
		}
		return false;
	}

	private boolean isRokhanPartySupportNeeded(Attackable npc, Player leader)
	{
		if ((npc == null) || (leader == null) || (leader.getInstanceId() != npc.getInstanceId()))
		{
			return false;
		}

		final Party party = leader.getParty();
		if (party != null)
		{
			for (Player member : party.getMembers())
			{
				if (shouldTriggerRokhanPartySupport(npc, member))
				{
					return true;
				}
			}
		}
		else if (shouldTriggerRokhanPartySupport(npc, leader))
		{
			return true;
		}

		if (_hybridPartyService != null)
		{
			for (Npc member : _hybridPartyService.getHybridPartyMembers(leader))
			{
				if (shouldTriggerRokhanPartySupport(npc, member))
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean shouldTriggerRokhanPartySupport(Attackable npc, Creature member)
	{
		if ((npc == null) || (member == null) || (member == npc) || member.isDead() || (member.getInstanceId() != npc.getInstanceId()))
		{
			return false;
		}
		if (distanceSquared(npc.getX(), npc.getY(), member.getX(), member.getY()) > ((long) ROKHAN_PARTY_SUPPORT_RANGE * ROKHAN_PARTY_SUPPORT_RANGE))
		{
			return false;
		}

		final double hpRatio = ratio(member.getCurrentHp(), member.getMaxHp());
		final double cpRatio = ratio(member.getCurrentCp(), member.getMaxCp());
		return (hpRatio < ROKHAN_PARTY_HP_TRIGGER) || ((member.getMaxCp() > 0) && (cpRatio < ROKHAN_PARTY_CP_TRIGGER));
	}

	private boolean isRokhan(FpcDefinition definition)
	{
		return (definition != null) && ROKHAN_FPC_ID.equalsIgnoreCase(definition.getId());
	}

	private double ratio(double current, double max)
	{
		return (max <= 0) ? 1.0 : (current / max);
	}

	private void applyMovement(FpcDefinition definition, Npc npc, String currentZone, FakePlayerAdvisoryPlan plan)
	{
		if (!shouldAttemptMovement(plan))
		{
			return;
		}

		final String actorKey = definition.getName();
		final String preferredZone = normalizePreferredZone(plan, currentZone);
		if (!preferredZone.equalsIgnoreCase(currentZone) && _targetingService.isTeleportCooldownReady(actorKey))
		{
			final Location anchor = _routeService.resolveAnchor(preferredZone, definition);
			if (distanceSquared(npc, anchor) > 250000L)
			{
				npc.teleToLocation(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getHeading());
				_targetingService.markTeleportUsed(actorKey);
				recordZonePresence(definition.getId().toLowerCase(), preferredZone);
				LOGGER.fine(() -> getClass().getSimpleName() + ": Teleported FPC " + definition.getId() + " to preferredZone=" + preferredZone + " intent=" + plan.getIntentPreference() + " mood=" + plan.getMoodTag() + " confidence=" + plan.getConfidence());
				_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=planner_teleport preferredZone=" + preferredZone + " intent=" + plan.getIntentPreference() + " confidence=" + plan.getConfidence());
				return;
			}
		}

		if (npc.isMoving() || !isMoveCadenceReady(definition.getId()))
		{
			return;
		}

		final Location target = _routeService.pickRoamTarget(preferredZone, definition, npc);
		if (distanceSquared(npc, target) < 10000L)
		{
			return;
		}

		if ("farm".equalsIgnoreCase(plan.getIntentPreference()) || "regroup".equalsIgnoreCase(plan.getIntentPreference()))
		{
			npc.setRunning();
		}
		else
		{
			npc.setWalking();
		}

		npc.moveToLocation(target.getX(), target.getY(), target.getZ(), 0);
		_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
		LOGGER.fine(() -> getClass().getSimpleName() + ": Movement advisory applied for FPC " + definition.getId() + " currentZone=" + currentZone + " preferredZone=" + preferredZone + " intent=" + plan.getIntentPreference() + " target=" + target.getX() + "," + target.getY() + "," + target.getZ());
		_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=planner_move currentZone=" + currentZone + " preferredZone=" + preferredZone + " intent=" + plan.getIntentPreference() + " target=" + target.getX() + "," + target.getY() + "," + target.getZ());
	}

	private boolean shouldAttemptMovement(FakePlayerAdvisoryPlan plan)
	{
		final String intent = plan.getIntentPreference();
		return "move".equalsIgnoreCase(intent) || "farm".equalsIgnoreCase(intent) || "regroup".equalsIgnoreCase(intent);
	}

	private boolean maintainCombat(Attackable npc, Location farmAnchor, FpcAdventurerRoute route, FpcDefinition definition, ResolvedFpcCombatPower combatPower)
	{
		if (npc == null)
		{
			return false;
		}
		if ((npc.getTarget() != null) && npc.getTarget().isMonster())
		{
			final Monster currentTarget = npc.getTarget().asMonster();
			if ((currentTarget != null) && !currentTarget.isDead() && !currentTarget.isQuestMonster() && (distanceSquared(currentTarget.getX(), currentTarget.getY(), farmAnchor.getX(), farmAnchor.getY()) <= ((long) route.getLeashRadius() * route.getLeashRadius())))
			{
				return tryMaintainMonsterCombatWindow(definition, npc, currentTarget, combatPower, "maintain", null, "maintain",
					() -> holdSpellCombat(definition, npc, currentTarget, route, "maintain_spell_only"),
					() ->
					{
						if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower))
						{
							holdSpellCombat(definition, npc, currentTarget, route, "maintain");
							return true;
						}
						if (npc.getAI().getIntention() != Intention.ATTACK)
						{
							npc.getAI().setIntention(Intention.ATTACK, currentTarget);
							_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=reassert target=" + currentTarget.getName() + "(" + currentTarget.getId() + ") route=" + route.getId());
						}
						return true;
					});
			}
		}

		if (npc.isInCombat())
		{
			if (npc.isCastingNow())
			{
				return true;
			}
			if (handleInCombatMonsterSpellOnlyWindow(definition, npc, combatPower, ((npc.getTarget() != null) && npc.getTarget().isMonster()) ? npc.getTarget().asMonster() : null, () -> holdSpellCombat(definition, npc, npc.getTarget().asMonster(), route, "in_combat_spell_only"), "in_combat"))
			{
				return true;
			}
			if (npc.isAttackingNow())
			{
				return true;
			}

			final Creature mostHated = npc.getMostHated();
			if ((mostHated != null) && mostHated.isMonster())
			{
				final Monster hatedTarget = mostHated.asMonster();
				if ((hatedTarget != null) && !hatedTarget.isDead() && !hatedTarget.isQuestMonster() && (distanceSquared(hatedTarget.getX(), hatedTarget.getY(), farmAnchor.getX(), farmAnchor.getY()) <= ((long) route.getLeashRadius() * route.getLeashRadius())))
				{
					npc.setTarget(hatedTarget);
					return tryMaintainMonsterCombatWindow(definition, npc, hatedTarget, combatPower, "most_hated", null, "most_hated",
						() -> holdSpellCombat(definition, npc, hatedTarget, route, "most_hated_spell_only"),
						() ->
						{
							if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower))
							{
								holdSpellCombat(definition, npc, hatedTarget, route, "most_hated");
								return true;
							}
							npc.getAI().setIntention(Intention.ATTACK, hatedTarget);
							_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=reassert_most_hated target=" + hatedTarget.getName() + "(" + hatedTarget.getId() + ") route=" + route.getId());
							return true;
						});
				}
			}

			disengage(npc);
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=clear_stale_combat route=" + route.getId());
		}
		return false;
	}

	private boolean applyHybridClanWarRetreat(FpcDefinition definition, Attackable npc, String definitionKey, FpcAdventurerRoute route, int effectiveLevel)
	{
		if ((definition == null) || (npc == null))
		{
			return false;
		}

		final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
		if ((hybridClanService == null) || !hybridClanService.isHybridWarRetreatActive(definition.getId(), _config.getHybridClanWarDeathWindowMs(), _config.getHybridClanWarDeathRetreatThreshold()))
		{
			return false;
		}

		final HybridClanWarRouteAvoidance routeAvoidance = getHybridClanWarRouteAvoidance(definitionKey);
		if ((routeAvoidance != null) && (route != null) && !normalizeRouteId(route.getId()).equals(routeAvoidance._routeId))
		{
			return false;
		}

		disengage(npc);
		_hybridClanWarChaseAnchors.remove(definitionKey);
		ensureProgressMarker(definitionKey, npc);
		final int recentDeaths = hybridClanService.getRecentHybridWarDeathCount(definition.getId(), _config.getHybridClanWarDeathWindowMs());
		if ((routeAvoidance == null) && (route != null))
		{
			final long avoidUntil = markHybridClanWarRouteAvoidance(definitionKey, route.getId(), _config.getHybridClanWarDeathWindowMs());
			final FpcAdventurerRoute alternateRoute = _routeService.resolveAdventurerRouteExcluding(definition, effectiveLevel, route.getId());
			if (alternateRoute != null)
			{
				final Location fallbackArrival = _routeService.resolveArrivalLocation(alternateRoute, definition);
				npc.teleToLocation(fallbackArrival.getX(), fallbackArrival.getY(), fallbackArrival.getZ(), fallbackArrival.getHeading());
				recordZonePresence(definitionKey, alternateRoute.getArrivalZoneId());
				_lastKnownPositions.put(definitionKey, new Location(fallbackArrival.getX(), fallbackArrival.getY(), fallbackArrival.getZ(), fallbackArrival.getHeading()));
				_lastProgressTimes.put(definitionKey, System.currentTimeMillis());
				LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " rotated off hybrid clan war route=" + route.getId() + " to route=" + alternateRoute.getId() + " after deaths=" + recentDeaths + " avoidUntilMs=" + avoidUntil);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_route_rotate from=" + route.getId() + " to=" + alternateRoute.getId() + " deaths=" + recentDeaths + " avoidUntilMs=" + avoidUntil);
				return true;
			}
		}
		if (!npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && !npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.NO_PVP))
		{
			npc.teleToLocation(TeleportWhereType.TOWN);
			LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " retreated to town after hybrid clan war pressure deaths=" + recentDeaths + " windowMs=" + _config.getHybridClanWarDeathWindowMs());
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_retreat deaths=" + recentDeaths + " windowMs=" + _config.getHybridClanWarDeathWindowMs());
		}
		return true;
	}

	private boolean applyHybridClanWarEngagement(FpcDefinition definition, Attackable npc)
	{
		if ((definition == null) || (npc == null))
		{
			return false;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final Location chaseAnchor = _hybridClanWarChaseAnchors.get(definitionKey);
		if (maintainHybridClanWarCombat(definition, npc, definitionKey, chaseAnchor))
		{
			return true;
		}

		final Location effectiveAnchor = (chaseAnchor != null) ? chaseAnchor : new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
		final long chaseRangeSq = (long) Math.max(_config.getHybridClanWarChaseRange(), _config.getHybridClanWarEngageRange()) * Math.max(_config.getHybridClanWarChaseRange(), _config.getHybridClanWarEngageRange());
		if (distanceSquared(npc, effectiveAnchor) > chaseRangeSq)
		{
			disengage(npc);
			_hybridClanWarChaseAnchors.put(definitionKey, effectiveAnchor);
			return retreatToHybridClanWarAnchor(definition, npc, definitionKey, effectiveAnchor, "leash_return");
		}

		final Creature target = _targetingService.selectHybridClanWarTarget(npc, effectiveAnchor);
		if (target == null)
		{
			return retreatToHybridClanWarAnchor(definition, npc, definitionKey, effectiveAnchor, "anchor_hold");
		}
		if (npc.isCastingNow() || (npc.getAI().getIntention() == Intention.CAST))
		{
			return true;
		}

		final ResolvedFpcCombatPower combatPower = resolveCombatPower(definition, npc);
		_hybridClanWarChaseAnchors.put(definitionKey, effectiveAnchor);
		npc.setRunning();
		npc.setTarget(target);
		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseChaseSkill(definition, npc, target, combatPower, "hybrid_clan_war_engage"))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, "hybrid_clan_war_engage"))
		{
			return true;
		}
		if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower, target))
		{
			holdHybridClanWarSpellCombat(definition, npc, target, "engage");
			return true;
		}
		if (tryDirectCloseRangeAfpcAttack(definition, npc, target, "hybrid_clan_war_engage"))
		{
			return true;
		}
		npc.getAI().setIntention(Intention.ATTACK, target);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_engage target=" + target.getName() + "(" + target.getObjectId() + ") engageRange=" + _config.getHybridClanWarEngageRange() + " chaseRange=" + _config.getHybridClanWarChaseRange());
		return true;
	}

	private boolean applyPersonalityConflictEngagement(FpcDefinition definition, Attackable npc, String definitionKey)
	{
		if ((definition == null) || (npc == null))
		{
			return false;
		}

		final PersonalityConflictState state = getActivePersonalityConflictState(definitionKey);
		if (state == null)
		{
			return retreatToPersonalityConflictAnchor(definition, npc, definitionKey, _personalityConflictReturnAnchors.get(definitionKey), "post_conflict");
		}

		if (maintainPersonalityConflictCombat(definition, npc, definitionKey, state))
		{
			return true;
		}

		final Creature target = resolvePersonalityConflictTarget(state);
		if (target == null)
		{
			markPersonalityConflictReturn(definitionKey, state);
			clearPersonalityConflictState(definitionKey);
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_clear reason=" + state._reason + " target=" + state._targetName + "(" + state._targetObjectId + ") source=missing_target");
			return retreatToPersonalityConflictAnchor(definition, npc, definitionKey, state._anchor, "missing_target");
		}

		final int engageRange = Math.max((state._leashRange > 0) ? state._leashRange : _config.getPersonalityConflictEngageRange(), 200);
		if (!_targetingService.isValidPersonalityConflictTarget(npc, target, state._anchor, engageRange, state._allowPkEscalation))
		{
			if (shouldClearPersonalityConflictState(npc, target, state, engageRange))
			{
				markPersonalityConflictReturn(definitionKey, state);
				clearPersonalityConflictState(definitionKey);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_clear reason=" + state._reason + " target=" + target.getName() + "(" + target.getObjectId() + ") source=engage");
				return retreatToPersonalityConflictAnchor(definition, npc, definitionKey, state._anchor, "engage_clear");
			}
			return false;
		}
		if (npc.isCastingNow() || (npc.getAI().getIntention() == Intention.CAST))
		{
			return true;
		}

		final ResolvedFpcCombatPower combatPower = resolveCombatPower(definition, npc);
		npc.setRunning();
		npc.setTarget(target);
		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseChaseSkill(definition, npc, target, combatPower, "personality_conflict_engage"))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, "personality_conflict_engage"))
		{
			return true;
		}
		if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower, target))
		{
			holdPersonalityConflictSpellCombat(definition, npc, target, state._reason, "engage");
			return true;
		}
		if (tryDirectCloseRangeAfpcAttack(definition, npc, target, "personality_conflict_engage"))
		{
			return true;
		}
		npc.getAI().setIntention(Intention.ATTACK, target);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_engage target=" + target.getName() + "(" + target.getObjectId() + ") reason=" + state._reason + " leash=" + engageRange);
		return true;
	}

	private boolean maintainHybridClanWarCombat(FpcDefinition definition, Attackable npc, String definitionKey, Location chaseAnchor)
	{
		if ((definition == null) || (npc == null) || (npc.getTarget() == null) || !npc.getTarget().isCreature())
		{
			return false;
		}

		final Creature currentTarget = npc.getTarget().asCreature();
		final Location effectiveAnchor = (chaseAnchor != null) ? chaseAnchor : new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
		final int chaseRange = Math.max(_config.getHybridClanWarChaseRange(), _config.getHybridClanWarEngageRange());
		if ((currentTarget != null) && _targetingService.isHybridClanWarTargetSuppressed(npc, currentTarget))
		{
			disengage(npc);
			return retreatToHybridClanWarAnchor(definition, npc, definitionKey, effectiveAnchor, "feed_guard");
		}
		if ((currentTarget == null) || !_targetingService.isValidHybridClanWarTarget(npc, currentTarget, effectiveAnchor, chaseRange))
		{
			if (!npc.isInCombat() || (!npc.isCastingNow() && !npc.isAttackingNow()))
			{
				disengage(npc);
				return retreatToHybridClanWarAnchor(definition, npc, definitionKey, effectiveAnchor, "target_lost");
			}
			return true;
		}
		final long chaseRangeSq = (long) chaseRange * chaseRange;
		if ((distanceSquared(npc, effectiveAnchor) > chaseRangeSq) || (distanceSquared(currentTarget.getX(), currentTarget.getY(), effectiveAnchor.getX(), effectiveAnchor.getY()) > chaseRangeSq))
		{
			disengage(npc);
			return retreatToHybridClanWarAnchor(definition, npc, definitionKey, effectiveAnchor, "chase_limit");
		}

		final ResolvedFpcCombatPower combatPower = resolveCombatPower(definition, npc);
		return tryMaintainCreatureCombatWindow(definition, npc, currentTarget, combatPower, "hybrid_clan_war_maintain", "hybrid_clan_war_maintain",
			() -> holdHybridClanWarSpellCombat(definition, npc, currentTarget, "maintain_spell_only"),
			() -> tryDirectCloseRangeAfpcAttack(definition, npc, currentTarget, "hybrid_clan_war_maintain"),
			() ->
			{
				if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower, currentTarget))
				{
					holdHybridClanWarSpellCombat(definition, npc, currentTarget, "maintain");
					return true;
				}
				if (tryDirectCloseRangeAfpcAttack(definition, npc, currentTarget, "hybrid_clan_war_reassert"))
				{
					return true;
				}
				npc.getAI().setIntention(Intention.ATTACK, currentTarget);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_reassert target=" + currentTarget.getName() + "(" + currentTarget.getObjectId() + ")");
				return true;
			});
	}

	private boolean maintainPersonalityConflictCombat(FpcDefinition definition, Attackable npc, String definitionKey, PersonalityConflictState state)
	{
		if ((definition == null) || (npc == null) || (state == null) || (npc.getTarget() == null) || !npc.getTarget().isCreature())
		{
			return false;
		}
		if (npc.getTarget().getObjectId() != state._targetObjectId)
		{
			return false;
		}

		final Creature currentTarget = npc.getTarget().asCreature();
		final int chaseRange = Math.max((state._leashRange > 0) ? state._leashRange : _config.getPersonalityConflictChaseRange(), _config.getPersonalityConflictEngageRange());
		if (!_targetingService.isValidPersonalityConflictTarget(npc, currentTarget, state._anchor, chaseRange, state._allowPkEscalation))
		{
			if (npc.isInCombat() && (npc.isCastingNow() || npc.isAttackingNow()))
			{
				return true;
			}

			disengage(npc);
			if (shouldClearPersonalityConflictState(npc, currentTarget, state, chaseRange))
			{
				markPersonalityConflictReturn(definitionKey, state);
				clearPersonalityConflictState(definitionKey);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_clear reason=" + state._reason + " target=" + state._targetName + "(" + state._targetObjectId + ") source=maintain");
				return retreatToPersonalityConflictAnchor(definition, npc, definitionKey, state._anchor, "maintain_clear");
			}
			return false;
		}

		final ResolvedFpcCombatPower combatPower = resolveCombatPower(definition, npc);
		return tryMaintainCreatureCombatWindow(definition, npc, currentTarget, combatPower, "personality_conflict_maintain", "personality_conflict_maintain",
			() -> holdPersonalityConflictSpellCombat(definition, npc, currentTarget, state._reason, "maintain_spell_only"),
			() -> tryDirectCloseRangeAfpcAttack(definition, npc, currentTarget, "personality_conflict_maintain"),
			() ->
			{
				if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower, currentTarget))
				{
					holdPersonalityConflictSpellCombat(definition, npc, currentTarget, state._reason, "maintain");
					return true;
				}
				if (tryDirectCloseRangeAfpcAttack(definition, npc, currentTarget, "personality_conflict_reassert"))
				{
					return true;
				}
				npc.getAI().setIntention(Intention.ATTACK, currentTarget);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_reassert target=" + currentTarget.getName() + "(" + currentTarget.getObjectId() + ") reason=" + state._reason);
				return true;
			});
	}
	
	private boolean applyProactivePersonalityThreatEngagement(FpcDefinition definition, Attackable npc, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (definitionKey == null) || npc.isDead() || npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.NO_PVP))
		{
			return false;
		}
		if ((_personalityConflictStates.containsKey(definitionKey) || _personalityConflictReturnAnchors.containsKey(definitionKey)) || npc.isCastingNow() || npc.isAttackingNow())
		{
			return false;
		}
		
		final int engageRange = Math.max(_config.getPersonalityConflictEngageRange(), 600);
		final Location anchor = new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
		final Player[] selectedTarget = new Player[1];
		final FakePlayerAfpcCombatPolicyService.Decision[] selectedDecision = new FakePlayerAfpcCombatPolicyService.Decision[1];
		final int[] bestScore = new int[]
		{
			Integer.MIN_VALUE
		};
		final long[] bestDistance = new long[]
		{
			Long.MAX_VALUE
		};
		
		_worldFacade.forEachVisibleObjectInRange(npc, Player.class, engageRange, player ->
		{
			if ((player == null) || player.isDead() || player.isAlikeDead() || player.isInvisible() || !player.isTargetable() || (player.getInstanceId() != npc.getInstanceId()) || player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.NO_PVP) || (player.getReputation() >= 0))
			{
				return;
			}
			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, player, FakePlayerAfpcCombatPolicyService.Trigger.HOSTILE_PK_NEARBY);
			if ((decision == null) || !decision.isEngage() || !_targetingService.isValidPersonalityConflictTarget(npc, player, anchor, engageRange, decision.isAllowPkEscalation()))
			{
				return;
			}
			
			final long distance = distanceSquared(npc.getX(), npc.getY(), player.getX(), player.getY());
			if ((decision.getPressureScore() > bestScore[0]) || ((decision.getPressureScore() == bestScore[0]) && (distance < bestDistance[0])))
			{
				selectedTarget[0] = player;
				selectedDecision[0] = decision;
				bestScore[0] = decision.getPressureScore();
				bestDistance[0] = distance;
			}
		});
		
		if ((selectedTarget[0] == null) || (selectedDecision[0] == null))
		{
			return false;
		}
		
		markPersonalityConflictTarget(definition, npc, selectedTarget[0], selectedDecision[0].getReason(), selectedDecision[0].isAllowPkEscalation(), selectedDecision[0].getLeashRange(), selectedDecision[0].getMemoryMs());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hostile_pk_nearby_acquire target=" + selectedTarget[0].getName() + "(" + selectedTarget[0].getObjectId() + ") score=" + selectedDecision[0].getPressureScore() + " reputation=" + selectedTarget[0].getReputation());
		return applyPersonalityConflictEngagement(definition, npc, definitionKey);
	}

	private boolean retreatToHybridClanWarAnchor(FpcDefinition definition, Attackable npc, String definitionKey, Location chaseAnchor, String reason)
	{
		if ((definition == null) || (npc == null) || (chaseAnchor == null))
		{
			return false;
		}

		if (distanceSquared(npc, chaseAnchor) <= 62500L)
		{
			_hybridClanWarChaseAnchors.remove(definitionKey);
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_anchor_clear reason=" + reason);
			return false;
		}
		if (npc.isMoving() || !isMoveCadenceReady(definition.getId()))
		{
			return true;
		}

		npc.setRunning();
		npc.moveToLocation(chaseAnchor.getX(), chaseAnchor.getY(), chaseAnchor.getZ(), 0);
		_lastMoveTimes.put(definitionKey, System.currentTimeMillis());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hybrid_clan_war_anchor_return reason=" + reason + " target=" + chaseAnchor.getX() + "," + chaseAnchor.getY() + "," + chaseAnchor.getZ());
		return true;
	}
	
	private void markPersonalityConflictReturn(String definitionKey, PersonalityConflictState state)
	{
		if ((definitionKey == null) || definitionKey.isBlank() || (state == null) || (state._anchor == null))
		{
			return;
		}
		
		_personalityConflictReturnAnchors.put(definitionKey, new Location(state._anchor.getX(), state._anchor.getY(), state._anchor.getZ(), state._anchor.getHeading()));
	}
	
	private boolean retreatToPersonalityConflictAnchor(FpcDefinition definition, Attackable npc, String definitionKey, Location returnAnchor, String reason)
	{
		if ((definition == null) || (npc == null) || (definitionKey == null) || definitionKey.isBlank() || (returnAnchor == null))
		{
			return false;
		}
		
		if (distanceSquared(npc, returnAnchor) <= 62500L)
		{
			_personalityConflictReturnAnchors.remove(definitionKey);
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_return_clear reason=" + reason);
			return false;
		}
		if (npc.isMoving() || !isMoveCadenceReady(definition.getId()))
		{
			return true;
		}
		
		npc.setRunning();
		npc.moveToLocation(returnAnchor.getX(), returnAnchor.getY(), returnAnchor.getZ(), 0);
		_lastMoveTimes.put(definitionKey, System.currentTimeMillis());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_anchor_return reason=" + reason + " target=" + returnAnchor.getX() + "," + returnAnchor.getY() + "," + returnAnchor.getZ());
		return true;
	}

	private boolean shouldPreserveCastIntent(FpcDefinition definition, Attackable npc, Monster target, String source)
	{
		if ((npc == null) || (target == null) || target.isDead())
		{
			return false;
		}

		if (npc.isCastingNow())
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=preserve_cast source=" + source + " target=" + target.getName() + "(" + target.getId() + ")");
			return true;
		}

		if ((npc.getAI().getIntention() == Intention.CAST) && (npc.getTarget() == target))
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=preserve_cast_intent source=" + source + " target=" + target.getName() + "(" + target.getId() + ")");
			return true;
		}

		return false;
	}

	private boolean isSpellOnlyAttackWindow(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower)
	{
		return (definition != null) && (npc != null) && _adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower) && (npc.isAttackingNow() || (npc.getAI().getIntention() == Intention.ATTACK));
	}

	private boolean isSpellOnlyAttackWindow(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower, Creature target)
	{
		return (definition != null) && (npc != null) && (target != null) && _adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower, target) && (npc.isAttackingNow() || (npc.getAI().getIntention() == Intention.ATTACK));
	}

	private boolean handleInCombatMonsterSpellOnlyWindow(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower, Monster target, Runnable holdAction, String suppressSource)
	{
		if (!isSpellOnlyAttackWindow(definition, npc, combatPower))
		{
			return false;
		}
		if ((target != null) && (holdAction != null))
		{
			holdAction.run();
			return true;
		}
		suppressSpellOnlyBasicAttack(definition, npc, combatPower, suppressSource);
		return true;
	}

	private boolean tryMaintainMonsterCombatWindow(FpcDefinition definition, Attackable npc, Monster target, ResolvedFpcCombatPower combatPower, String preserveCastSource, String chaseSkillSource, String offensiveSkillSource, Runnable spellOnlyAttackHoldAction, BooleanSupplier reassertAction)
	{
		if ((definition == null) || (npc == null) || (target == null) || (reassertAction == null))
		{
			return false;
		}

		npc.setRunning();
		if (shouldPreserveCastIntent(definition, npc, target, preserveCastSource))
		{
			return true;
		}
		if (isSpellOnlyAttackWindow(definition, npc, combatPower))
		{
			if (spellOnlyAttackHoldAction != null)
			{
				spellOnlyAttackHoldAction.run();
				return true;
			}
			return false;
		}
		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			return true;
		}
		if ((chaseSkillSource != null) && _adventurerCombatService.tryUseChaseSkill(definition, npc, target, combatPower, chaseSkillSource))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, offensiveSkillSource))
		{
			return true;
		}
		return reassertAction.getAsBoolean();
	}

	private boolean tryMaintainCreatureCombatWindow(FpcDefinition definition, Attackable npc, Creature target, ResolvedFpcCombatPower combatPower, String chaseSkillSource, String offensiveSkillSource, Runnable spellOnlyAttackHoldAction, BooleanSupplier maintainAttackAction, BooleanSupplier reassertAction)
	{
		if ((definition == null) || (npc == null) || (target == null) || (reassertAction == null))
		{
			return false;
		}

		npc.setRunning();
		if (npc.isCastingNow() || (npc.getAI().getIntention() == Intention.CAST))
		{
			return true;
		}
		if (isSpellOnlyAttackWindow(definition, npc, combatPower, target))
		{
			if (spellOnlyAttackHoldAction != null)
			{
				spellOnlyAttackHoldAction.run();
				return true;
			}
			return false;
		}
		if ((chaseSkillSource != null) && _adventurerCombatService.tryUseChaseSkill(definition, npc, target, combatPower, chaseSkillSource))
		{
			return true;
		}
		if (npc.isAttackingNow())
		{
			return true;
		}
		if ((npc.getAI().getIntention() == Intention.ATTACK) && (maintainAttackAction != null) && maintainAttackAction.getAsBoolean())
		{
			return true;
		}
		if (npc.getAI().getIntention() == Intention.ATTACK)
		{
			return true;
		}
		if (_adventurerCombatService.tryUsePreparationSkill(definition, npc, combatPower))
		{
			return true;
		}
		if (_adventurerCombatService.tryUseOffensiveSkill(definition, npc, target, combatPower, offensiveSkillSource))
		{
			return true;
		}
		return reassertAction.getAsBoolean();
	}

	private void beginMonsterAttackWindow(FpcDefinition definition, Attackable npc, Monster target, ResolvedFpcCombatPower combatPower, Runnable holdAction, Runnable attackAction)
	{
		if ((definition == null) || (npc == null) || (target == null))
		{
			return;
		}

		npc.setRunning();
		npc.setTarget(target);
		if (_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower))
		{
			if (holdAction != null)
			{
				holdAction.run();
			}
			return;
		}
		if (attackAction != null)
		{
			attackAction.run();
		}
	}

	private void holdSpellCombatWindow(FpcDefinition definition, Attackable npc, Creature target, String source, Runnable stateAdjuster, Runnable traceAction)
	{
		if ((definition == null) || (npc == null) || (target == null))
		{
			return;
		}

		suppressSpellOnlyBasicAttack(definition, npc, null, source);
		if (stateAdjuster != null)
		{
			stateAdjuster.run();
		}
		npc.setTarget(target);
		if (traceAction != null)
		{
			traceAction.run();
		}
	}

	private void beginAttack(FpcDefinition definition, Attackable npc, Monster target, FpcAdventurerRoute route, String source, ResolvedFpcCombatPower combatPower)
	{
		beginMonsterAttackWindow(definition, npc, target, combatPower,
			() -> holdSpellCombat(definition, npc, target, route, source),
			() ->
			{
				npc.getAI().setIntention(Intention.ATTACK, target);
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=engage target=" + target.getName() + "(" + target.getId() + ") route=" + route.getId() + " source=" + source);
			});
	}

	private void holdSpellCombat(FpcDefinition definition, Attackable npc, Monster target, FpcAdventurerRoute route, String source)
	{
		if (route == null)
		{
			return;
		}

		holdSpellCombatWindow(definition, npc, target, source, null, () -> _debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hold_spell_window target=" + target.getName() + "(" + target.getId() + ") route=" + route.getId() + " source=" + source));
	}

	private void holdHybridClanWarSpellCombat(FpcDefinition definition, Attackable npc, Creature target, String source)
	{
		holdSpellCombatWindow(definition, npc, target, source, null, () -> _debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hold_hybrid_clan_war_spell_window target=" + target.getName() + "(" + target.getObjectId() + ") source=" + source));
	}

	private boolean tryDirectCloseRangeAfpcAttack(FpcDefinition definition, Attackable npc, Creature target, String source)
	{
		if ((definition == null) || (npc == null) || (target == null) || !target.isFakePlayer() || !target.isNpc() || npc.isDead() || target.isDead() || npc.isCastingNow() || npc.isAttackingNow() || npc.isMoving())
		{
			return false;
		}
		if (!_navigationFacade.canSeeTarget(npc, target) || !target.isAutoAttackable(npc))
		{
			return false;
		}

		final int range = Math.max(40, npc.getPhysicalAttackRange() + npc.getTemplate().getCollisionRadius() + target.getTemplate().getCollisionRadius() + 40);
		if (distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY()) > ((long) range * range))
		{
			return false;
		}

		npc.setTarget(target);
		npc.doAutoAttack(target);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=direct_afpc_attack source=" + (((source == null) || source.isBlank()) ? "direct" : source) + " target=" + target.getName() + "(" + target.getObjectId() + ") range=" + range);
		return true;
	}

	private void holdPersonalityConflictSpellCombat(FpcDefinition definition, Attackable npc, Creature target, String reason, String source)
	{
		holdSpellCombatWindow(definition, npc, target, source, null, () -> _debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=hold_personality_conflict_spell_window target=" + target.getName() + "(" + target.getObjectId() + ") reason=" + reason + " source=" + source));
	}

	private void suppressSpellOnlyBasicAttack(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower, String source)
	{
		if ((definition == null) || (npc == null) || ((combatPower != null) && !_adventurerCombatService.shouldAvoidBasicAttack(definition, combatPower)))
		{
			return;
		}

		boolean suppressed = false;
		if (npc.isAttackingNow())
		{
			npc.abortAttack();
			suppressed = true;
		}
		if (npc.isMoving() && (npc.getAI().getIntention() == Intention.ATTACK))
		{
			npc.stopMove(null);
			suppressed = true;
		}
		if (npc.getAI().getIntention() == Intention.ATTACK)
		{
			npc.getAI().setIntention(Intention.ACTIVE);
			suppressed = true;
		}
		if (suppressed)
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=suppress_spell_basic_attack source=" + source);
		}
	}

	private void disengage(Attackable npc)
	{
		npc.abortAttack();
		npc.setTarget(null);
		npc.clearAggroList();
		npc.getAI().setIntention(Intention.ACTIVE);
	}

	private boolean tryEmergencyRecoverTeleport(FpcDefinition definition, Attackable npc, FpcAdventurerRoute route, String definitionKey, Location arrivalLocation, String currentZone)
	{
		if ((definition == null) || (npc == null) || (route == null) || (arrivalLocation == null))
		{
			return false;
		}
		if (isWithinRouteZone(npc, currentZone, route.getArrivalZoneId(), route.getLeashRadius()) && (distanceSquared(npc, arrivalLocation) < 10000L))
		{
			return false;
		}

		npc.stopMove(null);
		npc.teleToLocation(arrivalLocation.getX(), arrivalLocation.getY(), arrivalLocation.getZ(), arrivalLocation.getHeading());
		recordZonePresence(definitionKey, route.getArrivalZoneId());
		return true;
	}

	private boolean shouldRecover(Attackable npc)
	{
		if ((npc == null) || (npc.getMaxHp() <= 0))
		{
			return false;
		}

		return (npc.getCurrentHp() / npc.getMaxHp()) <= 0.35;
	}

	private boolean shouldEmergencyRecoverTeleport(Attackable npc)
	{
		if ((npc == null) || (npc.getMaxHp() <= 0))
		{
			return false;
		}

		final double hpRatio = npc.getCurrentHp() / npc.getMaxHp();
		return hpRatio <= 0.35;
	}

	private boolean shouldHoldRecoveryAtArrival(Attackable npc, FpcAdventurerRoute route, String currentZone)
	{
		if ((npc == null) || (route == null) || (currentZone == null))
		{
			return false;
		}
		if (!isWithinRouteZone(npc, currentZone, route.getArrivalZoneId(), route.getLeashRadius()))
		{
			return false;
		}
		return (npc.getMaxHp() > 0) && ((npc.getCurrentHp() / npc.getMaxHp()) < MID_BAND_THRESHOLD);
	}

	private boolean isWithinRouteZone(Npc npc, String currentZone, String routeZoneId, int radius)
	{
		if ((npc == null) || (routeZoneId == null) || routeZoneId.isBlank())
		{
			return false;
		}
		return routeZoneId.equalsIgnoreCase(currentZone) || _routeService.isWithinZoneRadius(npc, routeZoneId, radius);
	}

	private void applyRecoveryHold(String definitionKey, Attackable npc)
	{
		disengage(npc);
		npc.stopMove(null);
		_lastKnownPositions.put(definitionKey, new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading()));
		_lastProgressTimes.put(definitionKey, System.currentTimeMillis());
	}

	private Monster resolveLeaderAssistTarget(FpcDefinition definition, Attackable npc, Player leader, Location leaderAnchor, FpcAdventurerRoute route)
	{
		if ((definition == null) || (npc == null) || (leader == null) || (route == null))
		{
			return null;
		}

		final Monster directTarget = (leader.getTarget() != null) && leader.getTarget().isMonster() ? leader.getTarget().asMonster() : null;
		if (isValidLeaderAssistTarget(npc, leaderAnchor, route, directTarget))
		{
			return directTarget;
		}

		final Monster recentTarget = (_hybridPartyService == null) ? null : _hybridPartyService.resolveRecentAssistTarget(definition.getId(), leader, COMPANION_ASSIST_TARGET_WINDOW_MS);
		if (isValidLeaderAssistTarget(npc, leaderAnchor, route, recentTarget))
		{
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=assist_recent_target target=" + recentTarget.getName() + "(" + recentTarget.getId() + ")");
			return recentTarget;
		}
		return null;
	}

	private boolean isValidLeaderAssistTarget(Attackable npc, Location leaderAnchor, FpcAdventurerRoute route, Monster leaderTarget)
	{
		if ((npc == null) || (leaderAnchor == null) || (route == null) || (leaderTarget == null))
		{
			return false;
		}
		if (distanceSquared(leaderTarget.getX(), leaderTarget.getY(), leaderAnchor.getX(), leaderAnchor.getY()) > ((long) route.getSearchRadius() * route.getSearchRadius()))
		{
			return false;
		}
		if (!_navigationFacade.canSeeTarget(npc, leaderTarget))
		{
			return false;
		}
		return !leaderTarget.isDead() && !leaderTarget.isRaid() && !leaderTarget.isQuestMonster() && !leaderTarget.isFakePlayer() && !leaderTarget.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE);
	}

	private boolean isDirectiveFollowTeleportReady(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return false;
		}

		final Long lastUse = _lastDirectiveFollowTeleportTimes.get(fpcId.toLowerCase());
		return (lastUse == null) || ((System.currentTimeMillis() - lastUse.longValue()) >= COMPANION_DIRECTIVE_TELEPORT_COOLDOWN_MS);
	}

	private void markDirectiveFollowTeleportUsed(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return;
		}
		_lastDirectiveFollowTeleportTimes.put(fpcId.toLowerCase(), System.currentTimeMillis());
	}

	private void armDirectiveFollowWindow(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return;
		}
		_directiveFollowUntilTimes.put(fpcId.toLowerCase(), System.currentTimeMillis() + COMPANION_DIRECTIVE_FOLLOW_WINDOW_MS);
	}

	private boolean isDirectiveFollowWindowActive(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return false;
		}

		final String normalizedId = fpcId.toLowerCase();
		final Long activeUntil = _directiveFollowUntilTimes.get(normalizedId);
		if (activeUntil == null)
		{
			return false;
		}
		if (System.currentTimeMillis() <= activeUntil.longValue())
		{
			return true;
		}
		_directiveFollowUntilTimes.remove(normalizedId, activeUntil);
		return false;
	}

	private void clearDirectiveFollowWindow(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return;
		}
		_directiveFollowUntilTimes.remove(fpcId.toLowerCase());
	}

	private boolean applyForcedDirectiveFollow(FpcDefinition definition, Attackable npc, Player leader, String definitionKey)
	{
		if ((definition == null) || (npc == null) || (leader == null))
		{
			return false;
		}
		if (!isDirectiveFollowWindowActive(definition.getId()))
		{
			return false;
		}
		if (leader.isDead())
		{
			clearDirectiveFollowWindow(definition.getId());
			return false;
		}

		disengage(npc);
		npc.setRunning();
		npc.setTarget(leader);

		if (leader.getInstanceId() != npc.getInstanceId())
		{
			return tryFollowLeaderAcrossInstance(definition, npc, leader, definitionKey);
		}

		final Location leaderAnchor = new Location(leader.getX(), leader.getY(), leader.getZ(), leader.getHeading());
		final long distanceToLeader = distanceSquared(npc, leaderAnchor);
		final long syncDistanceSq = (long) COMPANION_DIRECTIVE_SYNC_RANGE * COMPANION_DIRECTIVE_SYNC_RANGE;
		if ((distanceToLeader > syncDistanceSq) && isDirectiveFollowTeleportReady(definition.getId()))
		{
			npc.teleToLocation(leaderAnchor, leader.getInstanceWorld());
			markDirectiveFollowTeleportUsed(definition.getId());
			ensureProgressMarker(definitionKey, npc);
			npc.getAI().setIntention(Intention.FOLLOW, leader);
			if (_hybridPartyService != null)
			{
				_hybridPartyService.markLeaderActivity(definition.getId(), leader, "directive_follow_sync");
			}
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=directive_follow_sync leader=" + leader.getName() + " x=" + leaderAnchor.getX() + " y=" + leaderAnchor.getY() + " z=" + leaderAnchor.getZ());
			return true;
		}

		npc.getAI().setIntention(Intention.FOLLOW, leader);
		_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
		ensureProgressMarker(definitionKey, npc);
		if (_hybridPartyService != null)
		{
			_hybridPartyService.markLeaderActivity(definition.getId(), leader, "directive_follow");
		}
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=directive_follow leader=" + leader.getName() + " distance=" + distanceToLeader);
		return true;
	}

	private boolean canUseCompanionRegroupTeleport(FpcDefinition definition, boolean allowDirectiveTeleportBypass)
	{
		if ((definition == null) || (_targetingService == null))
		{
			return false;
		}
		if (_targetingService.isTeleportCooldownReady(definition.getName()))
		{
			return true;
		}
		return allowDirectiveTeleportBypass && isDirectiveFollowTeleportReady(definition.getId());
	}

	private void markCompanionRegroupTeleportUsed(FpcDefinition definition, boolean allowDirectiveTeleportBypass)
	{
		if ((definition == null) || (_targetingService == null))
		{
			return;
		}

		_targetingService.markTeleportUsed(definition.getName());
		if (allowDirectiveTeleportBypass)
		{
			markDirectiveFollowTeleportUsed(definition.getId());
		}
	}

	private void regroupToLeader(FpcDefinition definition, Attackable npc, Player leader, Location leaderAnchor, String definitionKey, long teleportDistanceSq, boolean allowDirectiveTeleportBypass)
	{
		final long distance = distanceSquared(npc, leaderAnchor);
		if ((distance > teleportDistanceSq) && canUseCompanionRegroupTeleport(definition, allowDirectiveTeleportBypass))
		{
			final boolean recentInstanceTeleport = (leader.getInstanceId() > 0) && (_hybridPartyService != null) && _hybridPartyService.hasRecentTeleportActivity(definition.getId(), leader, COMPANION_INSTANCE_SYNC_WINDOW_MS);
			if (recentInstanceTeleport)
			{
				npc.teleToLocation(leaderAnchor, leader.getInstanceWorld());
				markCompanionRegroupTeleportUsed(definition, allowDirectiveTeleportBypass);
				ensureProgressMarker(definitionKey, npc);
				npc.setRunning();
				npc.setTarget(leader);
				npc.getAI().setIntention(Intention.FOLLOW, leader);
				LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " regrouped by instance sync near contracted leader=" + leader.getName());
				_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=regroup_instance_sync leader=" + leader.getName() + " x=" + leaderAnchor.getX() + " y=" + leaderAnchor.getY() + " z=" + leaderAnchor.getZ());
				return;
			}

			if (leader.getInstanceId() <= 0)
			{
				final Location publicTeleport = _routeService.resolveNearestPublicTeleport(leaderAnchor);
				if (publicTeleport != null)
				{
					npc.teleToLocation(publicTeleport.getX(), publicTeleport.getY(), publicTeleport.getZ(), publicTeleport.getHeading());
					markCompanionRegroupTeleportUsed(definition, allowDirectiveTeleportBypass);
					ensureProgressMarker(definitionKey, npc);
					npc.setRunning();
					npc.setTarget(leader);
					npc.getAI().setIntention(Intention.FOLLOW, leader);
					LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " regrouped by public teleport near contracted leader=" + leader.getName());
					_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=regroup_public_teleport leader=" + leader.getName() + " landing=" + publicTeleport.getX() + "," + publicTeleport.getY() + "," + publicTeleport.getZ());
					return;
				}
			}
			else
			{
				_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=regroup_instance_walk leader=" + leader.getName() + " recentTeleport=false");
			}
		}

		if (!npc.isMoving() && isMoveCadenceReady(definition.getId()))
		{
			npc.setRunning();
			npc.setTarget(leader);
			npc.getAI().setIntention(Intention.FOLLOW, leader);
			_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
			ensureProgressMarker(definitionKey, npc);
			LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " regrouping on foot to contracted leader=" + leader.getName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=regroup_walk leader=" + leader.getName());
		}
	}

	private void regroupToLeaderCorpse(FpcDefinition definition, Attackable npc, Player leader, Location leaderAnchor, String definitionKey, long teleportDistanceSq)
	{
		final long distance = distanceSquared(npc, leaderAnchor);
		if ((distance > teleportDistanceSq) && _targetingService.isTeleportCooldownReady(definition.getName()))
		{
			if (leader.getInstanceId() <= 0)
			{
				final Location publicTeleport = _routeService.resolveNearestPublicTeleport(leaderAnchor);
				if (publicTeleport != null)
				{
					npc.teleToLocation(publicTeleport.getX(), publicTeleport.getY(), publicTeleport.getZ(), publicTeleport.getHeading());
					_targetingService.markTeleportUsed(definition.getName());
					ensureProgressMarker(definitionKey, npc);
					_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=leader_dead_regroup_public_teleport leader=" + leader.getName() + " landing=" + publicTeleport.getX() + "," + publicTeleport.getY() + "," + publicTeleport.getZ());
					return;
				}
			}
			else if ((_hybridPartyService != null) && _hybridPartyService.hasRecentTeleportActivity(definition.getId(), leader, COMPANION_INSTANCE_SYNC_WINDOW_MS))
			{
				npc.teleToLocation(leaderAnchor, leader.getInstanceWorld());
				_targetingService.markTeleportUsed(definition.getName());
				ensureProgressMarker(definitionKey, npc);
				_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=leader_dead_regroup_instance_sync leader=" + leader.getName() + " x=" + leaderAnchor.getX() + " y=" + leaderAnchor.getY() + " z=" + leaderAnchor.getZ());
				return;
			}
		}

		if (!npc.isMoving() && isMoveCadenceReady(definition.getId()))
		{
			npc.setRunning();
			npc.moveToLocation(leaderAnchor.getX(), leaderAnchor.getY(), leaderAnchor.getZ(), 0);
			_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
			ensureProgressMarker(definitionKey, npc);
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=leader_dead_regroup_walk leader=" + leader.getName());
		}
	}

	private void moveToward(FpcDefinition definition, Npc npc, String currentZone, String preferredZone, Location target, String intent)
	{
		if (npc.isMoving())
		{
			return;
		}
		if (!isMoveCadenceReady(definition.getId()))
		{
			return;
		}
		final Location resolvedTarget = _routeService.resolveReachableTarget(preferredZone, definition, npc, target);
		if (distanceSquared(npc, resolvedTarget) < 10000L)
		{
			return;
		}

		npc.setRunning();
		npc.moveToLocation(resolvedTarget.getX(), resolvedTarget.getY(), resolvedTarget.getZ(), 0);
		_lastMoveTimes.put(definition.getId().toLowerCase(), System.currentTimeMillis());
		LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC movement applied for " + definition.getId() + " currentZone=" + currentZone + " preferredZone=" + preferredZone + " intent=" + intent + " target=" + resolvedTarget.getX() + "," + resolvedTarget.getY() + "," + resolvedTarget.getZ());
		_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=move currentZone=" + currentZone + " preferredZone=" + preferredZone + " intent=" + intent + " target=" + resolvedTarget.getX() + "," + resolvedTarget.getY() + "," + resolvedTarget.getZ());
	}

	private void ensureProgressMarker(String definitionKey, Npc npc)
	{
		final Location previous = _lastKnownPositions.get(definitionKey);
		final Location current = new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
		if ((previous == null) || (distanceSquared(previous.getX(), previous.getY(), current.getX(), current.getY()) > 14400L) || npc.isInCombat() || npc.isMoving() || hasCombatHoldState(npc))
		{
			_lastKnownPositions.put(definitionKey, current);
			_lastProgressTimes.put(definitionKey, System.currentTimeMillis());
		}
	}

	private boolean isAntiStuckTriggered(String definitionKey, Npc npc)
	{
		ensureProgressMarker(definitionKey, npc);
		final Long lastProgress = _lastProgressTimes.get(definitionKey);
		if (lastProgress == null)
		{
			return false;
		}
		if (hasCombatHoldState(npc))
		{
			return false;
		}
		return !npc.isMoving() && !npc.isInCombat() && ((System.currentTimeMillis() - lastProgress.longValue()) >= Math.max(_config.getAntiStuckTimeoutMs(), 10000L));
	}

	private boolean hasCombatHoldState(Npc npc)
	{
		if (!(npc instanceof Attackable))
		{
			return npc.isCastingNow() || npc.isAttackingNow();
		}

		final Attackable attackable = (Attackable) npc;
		if (attackable.isCastingNow() || attackable.isAttackingNow())
		{
			return true;
		}
		if ((attackable.getTarget() != null) && attackable.getTarget().isMonster())
		{
			final Monster target = attackable.getTarget().asMonster();
			if ((target != null) && !target.isDead())
			{
				return true;
			}
		}
		return (attackable.getMostHated() != null) || !attackable.getAggroList().isEmpty();
	}

	private String normalizeRouteId(String routeId)
	{
		return (routeId == null) ? "" : routeId.trim().toLowerCase();
	}

	private ResolvedFpcCombatPower resolveCombatPower(FpcDefinition definition, Npc npc)
	{
		final ResolvedFpcCombatPower cached = _profileData.getRegistry().getActiveCombatPower(definition.getId());
		return (cached != null) ? cached : _combatPowerService.resolveCombatPower(definition, npc);
	}

	private FpcAdventurerRoute resolveActiveAdventurerRoute(FpcDefinition definition, int level, String definitionKey)
	{
		final HybridClanWarRouteAvoidance routeAvoidance = getHybridClanWarRouteAvoidance(definitionKey);
		if (routeAvoidance == null)
		{
			return _routeService.resolveAdventurerRoute(definition, level);
		}

		final FpcAdventurerRoute alternateRoute = _routeService.resolveAdventurerRouteExcluding(definition, level, routeAvoidance._routeId);
		return (alternateRoute != null) ? alternateRoute : _routeService.resolveAdventurerRoute(definition, level);
	}

	private HybridClanWarRouteAvoidance getHybridClanWarRouteAvoidance(String definitionKey)
	{
		if ((definitionKey == null) || definitionKey.isBlank())
		{
			return null;
		}

		final HybridClanWarRouteAvoidance avoidance = _hybridClanWarRouteAvoidances.get(definitionKey);
		if (avoidance == null)
		{
			return null;
		}
		if (avoidance._untilMs > System.currentTimeMillis())
		{
			return avoidance;
		}

		_hybridClanWarRouteAvoidances.remove(definitionKey, avoidance);
		return null;
	}

	private long markHybridClanWarRouteAvoidance(String definitionKey, String routeId, long durationMs)
	{
		if ((definitionKey == null) || definitionKey.isBlank())
		{
			return 0L;
		}

		final long untilMs = System.currentTimeMillis() + Math.max(durationMs, 1000L);
		_hybridClanWarRouteAvoidances.put(definitionKey, new HybridClanWarRouteAvoidance(routeId, untilMs));
		return untilMs;
	}

	public void markPersonalityConflictTarget(FpcDefinition definition, Npc npc, Creature target, String reason, boolean allowPkEscalation, int leashRange, long durationMs)
	{
		if ((definition == null) || (npc == null) || (target == null))
		{
			return;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final long untilMs = System.currentTimeMillis() + Math.max(durationMs, 10000L);
		final Location anchor = new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
		final PersonalityConflictState nextState = new PersonalityConflictState(target.getObjectId(), target.getName(), anchor, reason, allowPkEscalation, leashRange, untilMs);
		final PersonalityConflictState previous = _personalityConflictStates.put(definitionKey, nextState);
		if ((previous == null) || (previous._targetObjectId != nextState._targetObjectId) || !previous._reason.equals(nextState._reason) || (previous._allowPkEscalation != nextState._allowPkEscalation) || (previous._leashRange != nextState._leashRange))
		{
			if (npc instanceof Attackable)
			{
				npc.asAttackable().addDamageHate(target, 0, 1);
			}
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=personality_conflict_mark target=" + target.getName() + "(" + target.getObjectId() + ") reason=" + nextState._reason + " pk=" + nextState._allowPkEscalation + " leash=" + nextState._leashRange + " untilMs=" + untilMs);
		}
	}

	private PersonalityConflictState getActivePersonalityConflictState(String definitionKey)
	{
		final PersonalityConflictState state = _personalityConflictStates.get(definitionKey);
		if (state == null)
		{
			return null;
		}
		if (System.currentTimeMillis() > state._untilMs)
		{
			_personalityConflictStates.remove(definitionKey, state);
			return null;
		}
		return state;
	}

	private void clearPersonalityConflictState(String definitionKey)
	{
		_personalityConflictStates.remove(definitionKey);
	}

	private boolean shouldClearPersonalityConflictState(Attackable npc, Creature target, PersonalityConflictState state, int maxOriginRange)
	{
		if ((npc == null) || (target == null) || (state == null))
		{
			return true;
		}
		if (target.isDead() || target.isAlikeDead())
		{
			return true;
		}
		if (target.isPlayer() && (target.asPlayer().isInvisible() || !target.asPlayer().isTargetable()))
		{
			return true;
		}
		if (target.isNpc() && !target.asNpc().isTargetable())
		{
			return true;
		}
		if ((target.getInstanceId() != npc.getInstanceId()) || target.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || target.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.NO_PVP))
		{
			return true;
		}
		if ((state._anchor != null) && (distanceSquared(target.getX(), target.getY(), state._anchor.getX(), state._anchor.getY()) > ((long) Math.max(maxOriginRange, 200) * Math.max(maxOriginRange, 200))))
		{
			return true;
		}
		if (state._allowPkEscalation)
		{
			return false;
		}
		return !target.isAutoAttackable(npc);
	}

	private Creature resolvePersonalityConflictTarget(PersonalityConflictState state)
	{
		if (state == null)
		{
			return null;
		}

		final WorldObject targetObject = _worldFacade.findObject(state._targetObjectId);
		return ((targetObject != null) && targetObject.isCreature()) ? targetObject.asCreature() : null;
	}

	private void clearRuntimeState(String definitionKey)
	{
		_zoneEntryTimes.remove(definitionKey);
		_lastMoveTimes.remove(definitionKey);
		_lastKnownZones.remove(definitionKey);
		_lastKnownPositions.remove(definitionKey);
		_lastProgressTimes.remove(definitionKey);
		_plannerPlanCache.remove(definitionKey);
		_lastPlannerRefreshAttemptTimes.remove(definitionKey);
		_plannerFirstEligibleTimes.remove(definitionKey);
		_plannerRefreshInFlight.remove(definitionKey);
		_adminHoldUntilTimes.remove(definitionKey);
		_adminRecallReturnByTeleport.remove(definitionKey);
		_personalityConflictStates.remove(definitionKey);
		_personalityConflictReturnAnchors.remove(definitionKey);
		_hybridClanWarChaseAnchors.remove(definitionKey);
		_hybridClanWarRouteAvoidances.remove(definitionKey);
		_postPartyFarmStates.remove(definitionKey);
		if (_syntheticScenarioService != null)
		{
			_syntheticScenarioService.clearRuntimeState(definitionKey);
		}
	}

	public void holdForAdminControl(FpcDefinition definition, Npc npc, long holdMs, boolean returnByTeleport)
	{
		if ((definition == null) || (npc == null))
		{
			return;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final long now = System.currentTimeMillis();
		final long until = now + Math.max(holdMs, 1000L);
		_adminHoldUntilTimes.put(definitionKey, until);
		if (returnByTeleport)
		{
			_adminRecallReturnByTeleport.put(definitionKey, Boolean.TRUE);
		}
		else
		{
			_adminRecallReturnByTeleport.remove(definitionKey);
		}
		if (npc instanceof Attackable)
		{
			disengage((Attackable) npc);
		}
		else
		{
			npc.abortAttack();
			npc.setTarget(null);
			npc.stopMove(null);
			if (npc.hasAI())
			{
				npc.getAI().setIntention(Intention.IDLE);
			}
		}
		_targetingService.clearTeleportUsage(definition.getName());
		_lastKnownPositions.put(definitionKey, new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading()));
		_lastProgressTimes.put(definitionKey, now);
		_lastMoveTimes.put(definitionKey, now);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC admin hold applied id=" + definition.getId() + " holdMs=" + (until - now) + " returnByTeleport=" + returnByTeleport);
		_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=admin_hold_applied holdMs=" + (until - now) + " returnByTeleport=" + returnByTeleport);
	}

	public boolean releaseAdminHold(FpcDefinition definition, boolean returnByTeleport)
	{
		if (definition == null)
		{
			return false;
		}

		final String definitionKey = definition.getId().toLowerCase();
		final Long existingHold = _adminHoldUntilTimes.remove(definitionKey);
		if (existingHold == null)
		{
			return false;
		}

		if (returnByTeleport)
		{
			_adminRecallReturnByTeleport.put(definitionKey, Boolean.TRUE);
		}
		else
		{
			_adminRecallReturnByTeleport.remove(definitionKey);
		}
		_targetingService.clearTeleportUsage(definition.getName());
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC admin hold manually released id=" + definition.getId() + " returnByTeleport=" + returnByTeleport);
		_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=admin_hold_released_manual returnByTeleport=" + returnByTeleport);
		return true;
	}

	public long getAdminHoldRemainingMs(String fpcId)
	{
		final Long holdUntil = _adminHoldUntilTimes.get((fpcId == null) ? "" : fpcId.toLowerCase());
		if (holdUntil == null)
		{
			return 0L;
		}
		return Math.max(holdUntil.longValue() - System.currentTimeMillis(), 0L);
	}

	private boolean isAdminHoldActive(FpcDefinition definition, String definitionKey, Npc npc)
	{
		final Long holdUntil = _adminHoldUntilTimes.get(definitionKey);
		if (holdUntil == null)
		{
			return false;
		}

		final long now = System.currentTimeMillis();
		if (now >= holdUntil.longValue())
		{
			_adminHoldUntilTimes.remove(definitionKey, holdUntil);
			_targetingService.clearTeleportUsage(definition.getName());
			npc.broadcastSay(ChatType.GENERAL, _ruleset.chooseResumeWorkLine(definition));
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC admin hold expired id=" + definition.getId());
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=admin_hold_expired");
			return false;
		}

		if (npc instanceof Attackable)
		{
			disengage((Attackable) npc);
		}
		else
		{
			npc.abortAttack();
			npc.setTarget(null);
			npc.stopMove(null);
			if (npc.hasAI())
			{
				npc.getAI().setIntention(Intention.IDLE);
			}
		}
		_lastKnownPositions.put(definitionKey, new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading()));
		_lastProgressTimes.put(definitionKey, now);
		return true;
	}

	private boolean tryResumeAfterAdminRecall(FpcDefinition definition, Attackable npc, String currentZone, FpcAdventurerRoute route, String definitionKey, Location arrivalLocation, Location farmAnchor)
	{
		if ((_adminRecallReturnByTeleport.remove(definitionKey) == null) || (npc == null) || (route == null))
		{
			return false;
		}

		final boolean inFarmZone = isWithinRouteZone(npc, currentZone, route.getFarmZoneId(), route.getLeashRadius());
		final boolean inArrivalZone = isWithinRouteZone(npc, currentZone, route.getArrivalZoneId(), route.getLeashRadius());
		final boolean inRouteZone = inArrivalZone || inFarmZone;
		final Location resumeTarget = inFarmZone ? farmAnchor : arrivalLocation;
		final long distanceToResume = distanceSquared(npc, resumeTarget);
		if (_ruleset.shouldPreferTeleportReturnAfterAdminIntervention(distanceToResume) && _targetingService.isTeleportCooldownReady(definition.getName()))
		{
			npc.teleToLocation(resumeTarget.getX(), resumeTarget.getY(), resumeTarget.getZ(), resumeTarget.getHeading());
			_targetingService.markTeleportUsed(definition.getName());
			recordZonePresence(definitionKey, inFarmZone ? route.getFarmZoneId() : route.getArrivalZoneId());
			ensureProgressMarker(definitionKey, npc);
			LOGGER.fine(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " resumed route by teleport after admin hold targetZone=" + (inFarmZone ? route.getFarmZoneId() : route.getArrivalZoneId()));
			_debugService.trace(definition.getId(), FpcDebugCategory.MOVEMENT, "event=resume_teleport targetZone=" + (inFarmZone ? route.getFarmZoneId() : route.getArrivalZoneId()));
			return true;
		}

		if (!inRouteZone)
		{
			moveToward(definition, npc, currentZone, route.getArrivalZoneId(), arrivalLocation, "travel");
			return true;
		}
		return false;
	}

	private String normalizePreferredZone(FakePlayerAdvisoryPlan plan, String currentZone)
	{
		final String preferredZone = _routeService.choosePreferredZone(plan.getPreferredZone());
		return ((preferredZone == null) || preferredZone.isBlank()) ? currentZone : preferredZone;
	}

	private void recordZonePresence(String definitionKey, String zoneId)
	{
		final String previousZone = _lastKnownZones.put(definitionKey, zoneId);
		if ((previousZone == null) || !previousZone.equalsIgnoreCase(zoneId))
		{
			_zoneEntryTimes.put(definitionKey, System.currentTimeMillis());
		}
	}

	private double calculateBoredom(String definitionKey)
	{
		final long zoneBoredomMs = Math.max(_config.getZoneBoredomMs(), 1L);
		final long zoneEntryTime = _zoneEntryTimes.getOrDefault(definitionKey, System.currentTimeMillis());
		return Math.min((System.currentTimeMillis() - zoneEntryTime) / (double) zoneBoredomMs, 1.0);
	}

	private boolean isMoveCadenceReady(String definitionId)
	{
		final long now = System.currentTimeMillis();
		final Long lastMoveTime = _lastMoveTimes.get(definitionId.toLowerCase());
		return (lastMoveTime == null) || ((now - lastMoveTime.longValue()) >= Math.max(_config.getDecisionIntervalMs(), 4000L));
	}

	private int countNearbyPlayers(Npc npc)
	{
		final AtomicInteger count = new AtomicInteger();
		_worldFacade.forEachVisibleObjectInRange(npc, Player.class, PLAYER_SCAN_RANGE, player ->
		{
			if ((player != null) && !player.isInvisible())
			{
				count.incrementAndGet();
			}
		});
		return count.get();
	}

	private String resolveBand(double currentValue, double maxValue)
	{
		if (maxValue <= 0)
		{
			return "high";
		}

		final double ratio = currentValue / maxValue;
		if (ratio <= LOW_BAND_THRESHOLD)
		{
			return "low";
		}
		if (ratio <= MID_BAND_THRESHOLD)
		{
			return "mid";
		}
		return "high";
	}

	private long distanceSquared(Npc npc, Location target)
	{
		final long dx = npc.getX() - target.getX();
		final long dy = npc.getY() - target.getY();
		return (dx * dx) + (dy * dy);
	}

	private long distanceSquared(int x1, int y1, int x2, int y2)
	{
		final long dx = x1 - x2;
		final long dy = y1 - y2;
		return (dx * dx) + (dy * dy);
	}

	private void requestPlannerRefreshIfDue(FpcDefinition definition, FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		final String definitionKey = definition.getId().toLowerCase();
		if (!isPlannerRefreshDue(definitionKey))
		{
			return;
		}
		if (_plannerRefreshInFlight.putIfAbsent(definitionKey, Boolean.TRUE) != null)
		{
			return;
		}
		if (!_plannerWorkerBusy.compareAndSet(false, true))
		{
			_plannerRefreshInFlight.remove(definitionKey);
			return;
		}

		_lastPlannerRefreshAttemptTimes.put(definitionKey, System.currentTimeMillis());
		ThreadPool.execute(() -> refreshPlanner(definition, snapshot, recentEvent, definitionKey));
	}

	private boolean isPlannerRefreshDue(String definitionKey)
	{
		final long now = System.currentTimeMillis();
		final Long lastAttempt = _lastPlannerRefreshAttemptTimes.get(definitionKey);
		if (lastAttempt != null)
		{
			return (now - lastAttempt.longValue()) >= Math.max(_config.getPlannerRefreshMs(), _config.getDecisionIntervalMs());
		}

		final long firstEligibleTime = _plannerFirstEligibleTimes.computeIfAbsent(definitionKey, this::computeFirstPlannerEligibleTime);
		return now >= firstEligibleTime;
	}

	private long computeFirstPlannerEligibleTime(String definitionKey)
	{
		final long refreshWindow = Math.max(_config.getPlannerRefreshMs(), _config.getDecisionIntervalMs());
		final long stagger = Math.floorMod(definitionKey.hashCode(), (int) Math.min(Integer.MAX_VALUE, refreshWindow));
		return System.currentTimeMillis() + stagger;
	}

	private void refreshPlanner(FpcDefinition definition, FakePlayerRuntimeSnapshot snapshot, String recentEvent, String definitionKey)
	{
		try
		{
			final FakePlayerAdvisoryPlan plan = _decisionEngine.createPlannerAdvisoryPlan(snapshot, recentEvent);
			if (plan != null)
			{
				_plannerPlanCache.put(definitionKey, new PlannerCacheEntry(plan, System.currentTimeMillis()));
				LOGGER.fine(() -> getClass().getSimpleName() + ": Refreshed planner cache for FPC " + definition.getId() + " intent=" + plan.getIntentPreference() + " preferredZone=" + plan.getPreferredZone() + " confidence=" + plan.getConfidence());
				_debugService.trace(definition.getId(), FpcDebugCategory.PLANNER, "event=refresh intent=" + plan.getIntentPreference() + " preferredZone=" + plan.getPreferredZone() + " confidence=" + plan.getConfidence());
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Planner refresh failed for FPC " + definition.getId() + " -> " + e.getMessage());
		}
		finally
		{
			_plannerRefreshInFlight.remove(definitionKey);
			_plannerWorkerBusy.set(false);
		}
	}

	private FakePlayerAdvisoryPlan resolveActivePlannerPlan(String definitionKey, FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		final PlannerCacheEntry cachedEntry = getActivePlannerEntry(definitionKey);
		if (cachedEntry != null)
		{
			return cachedEntry.getPlan();
		}
		return _decisionEngine.createFallbackPlannerPlan(snapshot, recentEvent);
	}

	private PlannerCacheEntry getActivePlannerEntry(String definitionKey)
	{
		final PlannerCacheEntry cachedEntry = _plannerPlanCache.get(definitionKey);
		if (cachedEntry == null)
		{
			return null;
		}
		if ((System.currentTimeMillis() - cachedEntry.getCreatedTime()) > Math.max(_config.getPlannerPlanReuseMs(), _config.getPlannerRefreshMs()))
		{
			_plannerPlanCache.remove(definitionKey, cachedEntry);
			return null;
		}
		return cachedEntry;
	}

	private void tryEmitPendingSpeech(String definitionKey)
	{
		final PlannerCacheEntry cachedEntry = getActivePlannerEntry(definitionKey);
		if ((cachedEntry == null) || !cachedEntry.hasPendingSpeech())
		{
			return;
		}
		if (_chatService.tryEmitVisibleLine(cachedEntry.getPlan()))
		{
			cachedEntry.markSpeechEmitted();
		}
	}

	private static class PlannerCacheEntry
	{
		private final FakePlayerAdvisoryPlan _plan;
		private final long _createdTime;
		private final AtomicBoolean _pendingSpeech;

		public PlannerCacheEntry(FakePlayerAdvisoryPlan plan, long createdTime)
		{
			_plan = plan;
			_createdTime = createdTime;
			_pendingSpeech = new AtomicBoolean(plan.isSpeakNow() && (plan.getSelectedLine() != null) && !plan.getSelectedLine().isBlank());
		}

		public FakePlayerAdvisoryPlan getPlan()
		{
			return _plan;
		}

		public long getCreatedTime()
		{
			return _createdTime;
		}

		public boolean hasPendingSpeech()
		{
			return _pendingSpeech.get();
		}

		public void markSpeechEmitted()
		{
			_pendingSpeech.set(false);
		}
	}
}
