package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.mobius.MobiusTierAEffectAccess;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.status.NpcStatus;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.network.enums.PartySmallWindowUpdateType;
import org.l2jmobius.gameserver.network.serverpackets.JoinParty;
import org.l2jmobius.gameserver.network.serverpackets.PartyMemberPosition;
import org.l2jmobius.gameserver.network.serverpackets.PartySpelled;
import org.l2jmobius.gameserver.network.serverpackets.PartySmallWindowAll;
import org.l2jmobius.gameserver.network.serverpackets.PartySmallWindowDeleteAll;
import org.l2jmobius.gameserver.network.serverpackets.PartySmallWindowUpdate;

public class FakePlayerHybridPartyService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerHybridPartyService.class.getName());
	private static final long RECENT_EXIT_MEMORY_MS = 5L * 60L * 1000L;
	private static volatile FakePlayerHybridPartyService INSTANCE;

	private final FakePlayerConfig _config;
	private final FpcRegistry _registry;
	private final FakePlayerWorldFacade _worldFacade;
	private final Map<String, HybridPartyContract> _contracts = new ConcurrentHashMap<>();
	private final Map<String, RecentContractExit> _recentExits = new ConcurrentHashMap<>();
	private final Map<String, Party.HybridPartyMember> _memberSnapshots = new ConcurrentHashMap<>();

	public enum HybridContractOfferResult
	{
		ACCEPTED,
		ALREADY_ACTIVE_WITH_LEADER,
		BLOCKED_OTHER_LEADER,
		BLOCKED_UNAVAILABLE
	}

	public static final class HybridPartyInviteState
	{
		public static final HybridPartyInviteState EMPTY = new HybridPartyInviteState(false, false, "", 0L, 0L);

		private final boolean _activeWithLeader;
		private final boolean _activeWithOtherLeader;
		private final String _recentExitReason;
		private final long _recentExitAgeMs;
		private final long _recentLifetimeMs;

		private HybridPartyInviteState(boolean activeWithLeader, boolean activeWithOtherLeader, String recentExitReason, long recentExitAgeMs, long recentLifetimeMs)
		{
			_activeWithLeader = activeWithLeader;
			_activeWithOtherLeader = activeWithOtherLeader;
			_recentExitReason = (recentExitReason == null) ? "" : recentExitReason;
			_recentExitAgeMs = recentExitAgeMs;
			_recentLifetimeMs = recentLifetimeMs;
		}

		public boolean isActiveWithLeader()
		{
			return _activeWithLeader;
		}

		public boolean isActiveWithOtherLeader()
		{
			return _activeWithOtherLeader;
		}

		public boolean hasRecentExit()
		{
			return !_recentExitReason.isBlank();
		}

		public String getRecentExitReason()
		{
			return _recentExitReason;
		}

		public long getRecentExitAgeMs()
		{
			return _recentExitAgeMs;
		}

		public long getRecentLifetimeMs()
		{
			return _recentLifetimeMs;
		}
	}

	private static final class HybridPartyContract
	{
		private final String _fpcId;
		private final int _leaderObjectId;
		private final String _leaderName;
		private final int _anchorPartyLeaderObjectId;
		private final PartyDistributionType _distributionType;
		private final long _acceptedAtMs;
		private final long _expiresAtMs;
		private final boolean _stickySession;
		private final long _idleReturnHomeMs;
		private final boolean _allowInstanceFollow;
		private volatile long _lastLeaderActivityMs;
		private volatile long _lastTeleportActivityMs;
		private volatile int _lastAssistTargetObjectId;
		private volatile long _lastAssistTargetAtMs;
		private volatile String _lastLeaderActivitySource;
		private volatile long _lastFollowupAtMs;

		private HybridPartyContract(String fpcId, int leaderObjectId, String leaderName, int anchorPartyLeaderObjectId, PartyDistributionType distributionType, long acceptedAtMs, long expiresAtMs, boolean stickySession, long idleReturnHomeMs, boolean allowInstanceFollow)
		{
			_fpcId = fpcId;
			_leaderObjectId = leaderObjectId;
			_leaderName = (leaderName == null) ? "" : leaderName;
			_anchorPartyLeaderObjectId = anchorPartyLeaderObjectId;
			_distributionType = distributionType;
			_acceptedAtMs = acceptedAtMs;
			_expiresAtMs = expiresAtMs;
			_stickySession = stickySession;
			_idleReturnHomeMs = Math.max(idleReturnHomeMs, 0L);
			_allowInstanceFollow = allowInstanceFollow;
			_lastLeaderActivityMs = acceptedAtMs;
			_lastTeleportActivityMs = 0L;
			_lastAssistTargetObjectId = 0;
			_lastAssistTargetAtMs = 0L;
			_lastLeaderActivitySource = "";
			_lastFollowupAtMs = 0L;
		}

		private boolean isExpired(long now)
		{
			if (_stickySession && (_idleReturnHomeMs > 0L))
			{
				return (now - _lastLeaderActivityMs) >= _idleReturnHomeMs;
			}
			return _expiresAtMs <= now;
		}

		private void touchActivity(long now, String source)
		{
			_lastLeaderActivityMs = now;
			_lastLeaderActivitySource = (source == null) ? "" : source;
			if (isTeleportSignal(source))
			{
				_lastTeleportActivityMs = now;
			}
		}

		private void markFollowup(long now)
		{
			_lastFollowupAtMs = now;
		}

		private void rememberAssistTarget(int objectId, long now)
		{
			_lastAssistTargetObjectId = Math.max(objectId, 0);
			_lastAssistTargetAtMs = now;
		}
	}

	private static boolean isTeleportSignal(String source)
	{
		if ((source == null) || source.isBlank())
		{
			return false;
		}

		final String normalized = source.trim().toLowerCase(Locale.ENGLISH);
		return normalized.equals("teleport") || normalized.equals("instance_follow") || normalized.contains("teleport");
	}

	private static final class RecentContractExit
	{
		private final String _fpcId;
		private final int _leaderObjectId;
		private final String _leaderName;
		private final long _acceptedAtMs;
		private final long _clearedAtMs;
		private final String _reason;

		private RecentContractExit(String fpcId, int leaderObjectId, String leaderName, long acceptedAtMs, long clearedAtMs, String reason)
		{
			_fpcId = ((fpcId == null) || fpcId.isBlank()) ? "" : fpcId.toLowerCase(Locale.ENGLISH);
			_leaderObjectId = leaderObjectId;
			_leaderName = (leaderName == null) ? "" : leaderName;
			_acceptedAtMs = acceptedAtMs;
			_clearedAtMs = clearedAtMs;
			_reason = ((reason == null) || reason.isBlank()) ? "" : reason;
		}
	}

	public FakePlayerHybridPartyService(FakePlayerConfig config, FpcRegistry registry, FakePlayerWorldFacade worldFacade)
	{
		INSTANCE = this;
		_config = config;
		_registry = registry;
		_worldFacade = worldFacade;
	}

	public static FakePlayerHybridPartyService getInstance()
	{
		return INSTANCE;
	}

	public Party ensureAnchorParty(Player leader, PartyDistributionType distributionType)
	{
		if (leader == null)
		{
			return null;
		}

		Party party = leader.getParty();
		if (party != null)
		{
			return party;
		}

		final PartyDistributionType resolvedDistributionType = (distributionType != null) ? distributionType : PartyDistributionType.FINDERS_KEEPERS;
		party = new Party(leader, resolvedDistributionType);
		leader.setParty(party);
		leader.sendPacket(new JoinParty(1, leader));
		leader.sendPacket(PartySmallWindowDeleteAll.STATIC_PACKET);
		leader.sendPacket(new PartySmallWindowAll(leader, party));
		leader.sendPacket(new PartyMemberPosition(party));
		LOGGER.info(() -> getClass().getSimpleName() + ": Created anchor player party for hybrid AFPC support leader=" + leader.getName() + " distribution=" + resolvedDistributionType.name().toLowerCase(Locale.ENGLISH));
		return party;
	}

	public boolean canAcceptAdditionalHybridMember(Player leader)
	{
		if (leader == null)
		{
			return false;
		}

		final Party party = leader.getParty();
		if (party == null)
		{
			return 1 <= PlayerConfig.ALT_PARTY_MAX_MEMBERS;
		}
		return (party.getMemberCount() + getHybridMemberCount(party)) < PlayerConfig.ALT_PARTY_MAX_MEMBERS;
	}

	public HybridContractOfferResult offerHybridContract(FpcDefinition definition, Player leader, PartyDistributionType distributionType)
	{
		if ((definition == null) || (leader == null) || !definition.getAdventurerProfile().isAdventurerTier() || !definition.getAdventurerProfile().supportsPartyCoordination())
		{
			return HybridContractOfferResult.BLOCKED_UNAVAILABLE;
		}

		final Party party = ensureAnchorParty(leader, distributionType);
		if (party == null)
		{
			return HybridContractOfferResult.BLOCKED_UNAVAILABLE;
		}
		if (!canAcceptAdditionalHybridMember(leader))
		{
			return HybridContractOfferResult.BLOCKED_UNAVAILABLE;
		}

		final long now = System.currentTimeMillis();
		final String definitionKey = normalize(definition.getId());
		final HybridPartyContract existing = _contracts.get(definitionKey);
		if ((existing != null) && !existing.isExpired(now))
		{
			if (existing._leaderObjectId != leader.getObjectId())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " declined hybrid contract from " + leader.getName() + " because it is already contracted to " + existing._leaderName);
				return HybridContractOfferResult.BLOCKED_OTHER_LEADER;
			}
			return HybridContractOfferResult.ALREADY_ACTIVE_WITH_LEADER;
		}
		if ((existing != null) && existing.isExpired(now))
		{
			clearHybridContract(definition.getId(), existing._stickySession ? "inactive" : "expired");
		}

		final Player anchorLeader = party.getLeader();
		final long duration = Math.max(_config.getPartyContractDurationMs(), 300000L);
		final boolean stickySession = (definition.getCompanionProfile() != null) && definition.getCompanionProfile().isStickySession();
		final long idleReturnHomeMs = stickySession ? Math.max(definition.getCompanionProfile().getIdleReturnHomeMs(), 300000L) : 0L;
		final boolean allowInstanceFollow = (definition.getCompanionProfile() != null) && definition.getCompanionProfile().isAllowInstanceFollow();
		final HybridPartyContract contract = new HybridPartyContract(definition.getId(), leader.getObjectId(), leader.getName(), (anchorLeader != null) ? anchorLeader.getObjectId() : leader.getObjectId(), distributionType, now, now + duration, stickySession, idleReturnHomeMs, allowInstanceFollow);
		_contracts.put(definitionKey, contract);
		restoreAcceptedMemberSyntheticCp(definition);
		_recentExits.remove(buildRecentExitKey(definition.getId(), leader.getObjectId()));
		refreshPartyWindows(party);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " accepted hybrid party contract leader=" + leader.getName() + " anchorLeader=" + ((anchorLeader != null) ? anchorLeader.getName() : leader.getName()) + " durationMs=" + duration + " sticky=" + stickySession + " idleReturnHomeMs=" + idleReturnHomeMs + " allowInstanceFollow=" + allowInstanceFollow + " distribution=" + describeDistribution(contract._distributionType));
		return HybridContractOfferResult.ACCEPTED;
	}

	public HybridPartyInviteState inspectInviteState(FpcDefinition definition, Player leader)
	{
		if ((definition == null) || (leader == null))
		{
			return HybridPartyInviteState.EMPTY;
		}

		final String definitionKey = normalize(definition.getId());
		final HybridPartyContract active = _contracts.get(definitionKey);
		final long now = System.currentTimeMillis();
		final boolean activeWithLeader = (active != null) && !active.isExpired(now) && (active._leaderObjectId == leader.getObjectId());
		final boolean activeWithOtherLeader = (active != null) && !active.isExpired(now) && (active._leaderObjectId != leader.getObjectId());
		final RecentContractExit recentExit = getRecentExit(definition.getId(), leader.getObjectId());
		if (recentExit == null)
		{
			return new HybridPartyInviteState(activeWithLeader, activeWithOtherLeader, "", 0L, 0L);
		}

		final long ageMs = Math.max(0L, now - recentExit._clearedAtMs);
		final long lifetimeMs = Math.max(0L, recentExit._clearedAtMs - recentExit._acceptedAtMs);
		return new HybridPartyInviteState(activeWithLeader, activeWithOtherLeader, recentExit._reason, ageMs, lifetimeMs);
	}

	private Party restoreAnchorParty(Player leader, HybridPartyContract contract, String source)
	{
		if ((leader == null) || (contract == null))
		{
			return null;
		}

		Party party = leader.getParty();
		if (party != null)
		{
			return party;
		}
		if (contract._anchorPartyLeaderObjectId != leader.getObjectId())
		{
			return null;
		}

		party = ensureAnchorParty(leader, contract._distributionType);
		if (party != null)
		{
			refreshPartyWindows(party);
			final Party restoredParty = party;
			LOGGER.info(() -> getClass().getSimpleName() + ": Restored hybrid anchor party leader=" + leader.getName() + " fpc=" + contract._fpcId + " source=" + safe(source) + " distribution=" + describeDistribution(contract._distributionType) + " members=" + restoredParty.getMemberCount());
		}
		return party;
	}

	private Party resolvePartyForPlayer(Player player, String source)
	{
		if (player == null)
		{
			return null;
		}

		Party party = player.getParty();
		if (party != null)
		{
			return party;
		}

		final long now = System.currentTimeMillis();
		for (HybridPartyContract contract : _contracts.values())
		{
			if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
			{
				continue;
			}
			if (contract.isExpired(now))
			{
				clearHybridContract(contract._fpcId, contract._stickySession ? "inactive" : "expired");
				continue;
			}

			party = restoreAnchorParty(player, contract, source);
			if (party != null)
			{
				return party;
			}
		}
		return null;
	}

	public Party resolveActiveParty(Player player, String source)
	{
		return resolvePartyForPlayer(player, source);
	}

	public Player getActivePlayerLeader(String fpcId)
	{
		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		if (contract == null)
		{
			return null;
		}
		if (contract.isExpired(System.currentTimeMillis()))
		{
			clearHybridContract(fpcId, contract._stickySession ? "inactive" : "expired");
			return null;
		}

		final Player player = _worldFacade.findPlayer(contract._leaderObjectId);
		if ((player == null) || !player.isOnline() || player.isInOfflineMode())
		{
			clearHybridContract(fpcId, "leader_missing");
			return null;
		}

		final Party party = resolvePartyForPlayer(player, "get_active_leader");
		if ((party == null) || (party.getLeaderObjectId() != contract._anchorPartyLeaderObjectId))
		{
			clearHybridContract(fpcId, "party_anchor_changed");
			return null;
		}
		return player;
	}

	public String describePlayerLeader(String fpcId)
	{
		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		return (contract == null) ? "" : contract._leaderName;
	}

	public boolean hasActivePlayerContract(String fpcId)
	{
		return getActivePlayerLeader(fpcId) != null;
	}

	public int getHybridMemberCount(Party party)
	{
		if (party == null)
		{
			return 0;
		}
		return getHybridPartyMembers(party).size();
	}

	public List<Party.HybridPartyMember> getHybridPartyMembers(Party party)
	{
		if (party == null)
		{
			return List.of();
		}

		final long now = System.currentTimeMillis();
		final List<Party.HybridPartyMember> members = new ArrayList<>();
		final List<String> expired = new ArrayList<>();
		for (HybridPartyContract contract : _contracts.values())
		{
			if (contract == null)
			{
				continue;
			}
			if (contract.isExpired(now))
			{
				expired.add(contract._fpcId);
				continue;
			}
			if (contract._anchorPartyLeaderObjectId != party.getLeaderObjectId())
			{
				continue;
			}

			final Party.HybridPartyMember member = buildOverlayMember(contract);
			if (member != null)
			{
				members.add(member);
			}
		}

		for (String fpcId : expired)
		{
			clearHybridContract(fpcId, "expired");
		}

		members.sort(Comparator.comparing(Party.HybridPartyMember::getName, String.CASE_INSENSITIVE_ORDER));
		return members;
	}

	public boolean markLeaderActivity(String fpcId, Player player, String source)
	{
		if ((fpcId == null) || fpcId.isBlank() || (player == null))
		{
			return false;
		}

		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
		{
			return false;
		}

		final long now = System.currentTimeMillis();
		if (contract.isExpired(now))
		{
			clearHybridContract(fpcId, contract._stickySession ? "inactive" : "expired");
			return false;
		}

		contract.touchActivity(now, source);
		return true;
	}

	public int markLeaderActivity(Player player, String source)
	{
		if (player == null)
		{
			return 0;
		}

		final long now = System.currentTimeMillis();
		int updated = 0;
		for (HybridPartyContract contract : _contracts.values())
		{
			if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
			{
				continue;
			}
			if (contract.isExpired(now))
			{
				clearHybridContract(contract._fpcId, contract._stickySession ? "inactive" : "expired");
				continue;
			}
			contract.touchActivity(now, source);
			updated++;
		}
		return updated;
	}

	public long getLastLeaderActivityAtMs(String fpcId)
	{
		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		return (contract == null) ? 0L : contract._lastLeaderActivityMs;
	}

	public boolean hasRecentTeleportActivity(String fpcId, Player player, long windowMs)
	{
		if ((fpcId == null) || fpcId.isBlank() || (player == null))
		{
			return false;
		}

		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
		{
			return false;
		}

		final long now = System.currentTimeMillis();
		if (contract.isExpired(now))
		{
			clearHybridContract(fpcId, contract._stickySession ? "inactive" : "expired");
			return false;
		}

		return (windowMs > 0L) && ((now - contract._lastTeleportActivityMs) <= windowMs);
	}

	public int rememberLeaderAssistTarget(Player player, Creature target)
	{
		if ((player == null) || !isValidAssistTarget(target))
		{
			return 0;
		}

		final long now = System.currentTimeMillis();
		int updated = 0;
		for (HybridPartyContract contract : _contracts.values())
		{
			if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
			{
				continue;
			}
			if (contract.isExpired(now))
			{
				clearHybridContract(contract._fpcId, contract._stickySession ? "inactive" : "expired");
				continue;
			}

			contract.rememberAssistTarget(target.getObjectId(), now);
			updated++;
		}
		return updated;
	}

	public Monster resolveRecentAssistTarget(String fpcId, Player player, long windowMs)
	{
		if ((fpcId == null) || fpcId.isBlank() || (player == null) || (windowMs <= 0L))
		{
			return null;
		}

		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		if ((contract == null) || (contract._leaderObjectId != player.getObjectId()))
		{
			return null;
		}

		final long now = System.currentTimeMillis();
		if (contract.isExpired(now))
		{
			clearHybridContract(fpcId, contract._stickySession ? "inactive" : "expired");
			return null;
		}
		if ((contract._lastAssistTargetObjectId <= 0) || ((now - contract._lastAssistTargetAtMs) > windowMs))
		{
			return null;
		}

		final WorldObject target = _worldFacade.findObject(contract._lastAssistTargetObjectId);
		if ((target == null) || !target.isMonster() || !isValidAssistTarget(target.asCreature()) || (target.asMonster().getInstanceId() != player.getInstanceId()))
		{
			return null;
		}
		return target.asMonster();
	}

	private static boolean isValidAssistTarget(Creature target)
	{
		if ((target == null) || !target.isMonster())
		{
			return false;
		}

		final Monster monster = target.asMonster();
		return (monster != null) && !monster.isDead() && !monster.isRaid() && !monster.isQuestMonster() && !monster.isFakePlayer() && !monster.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE);
	}

	public boolean allowInstanceFollow(String fpcId)
	{
		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		return (contract != null) && contract._allowInstanceFollow;
	}

	public long getLastFollowupAtMs(String fpcId)
	{
		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		return (contract == null) ? 0L : contract._lastFollowupAtMs;
	}

	public boolean markFollowupSent(String fpcId, Player player)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return false;
		}

		final HybridPartyContract contract = _contracts.get(normalize(fpcId));
		if (contract == null)
		{
			return false;
		}
		if ((player != null) && (contract._leaderObjectId != player.getObjectId()))
		{
			return false;
		}
		contract.markFollowup(System.currentTimeMillis());
		return true;
	}

	public List<Npc> getHybridPartyMembers(Player player)
	{
		if (player == null)
		{
			return List.of();
		}

		final Party party = resolvePartyForPlayer(player, "get_hybrid_members");
		if (party == null)
		{
			return List.of();
		}

		final List<Npc> npcs = new ArrayList<>();
		for (Party.HybridPartyMember member : getHybridPartyMembers(party))
		{
			final WorldObject object = _worldFacade.findObject(member.getObjectId());
			if ((object != null) && object.isNpc())
			{
				npcs.add(object.asNpc());
			}
		}
		return npcs;
	}

	public List<Player> getHybridPartyPlayers(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return List.of();
		}

		final FpcDefinition definition = findDefinitionByNpc(npc);
		if (definition == null)
		{
			return List.of();
		}

		final HybridPartyContract contract = _contracts.get(normalize(definition.getId()));
		if (contract == null)
		{
			return List.of();
		}

		final Player leader = getActivePlayerLeader(definition.getId());
		if (leader == null)
		{
			return List.of();
		}

		final Party party = leader.getParty();
		if (party == null)
		{
			return List.of();
		}
		return List.copyOf(party.getMembers());
	}

	public boolean isHybridPartyMember(Player player, WorldObject target)
	{
		if ((player == null) || (target == null) || !target.isNpc())
		{
			return false;
		}

		final Npc npc = target.asNpc();
		if (!npc.isFakePlayer())
		{
			return false;
		}

		final Party party = resolvePartyForPlayer(player, "is_hybrid_member");
		if (party == null)
		{
			return false;
		}

		final FpcDefinition definition = findDefinitionByNpc(npc);
		if (definition == null)
		{
			return false;
		}

		final HybridPartyContract contract = _contracts.get(normalize(definition.getId()));
		return (contract != null) && (contract._anchorPartyLeaderObjectId == party.getLeaderObjectId()) && (getActivePlayerLeader(definition.getId()) != null);
	}

	public boolean isHybridPartyMember(Creature creature, WorldObject target)
	{
		if ((creature == null) || (target == null))
		{
			return false;
		}

		if (creature.isPlayer())
		{
			return isHybridPartyMember(creature.asPlayer(), target);
		}

		if (!creature.isNpc() || !creature.asNpc().isFakePlayer())
		{
			return false;
		}

		final Npc casterNpc = creature.asNpc();
		final FpcDefinition definition = findDefinitionByNpc(casterNpc);
		if (definition == null)
		{
			return false;
		}

		final Player leader = getActivePlayerLeader(definition.getId());
		if (leader == null)
		{
			return false;
		}

		if (target.isPlayer())
		{
			final Party party = leader.getParty();
			return (party != null) && party.getMembers().contains(target.asPlayer());
		}

		if (target.isNpc())
		{
			final Npc targetNpc = target.asNpc();
			return targetNpc.isFakePlayer() && isHybridPartyMember(leader, targetNpc);
		}
		return false;
	}

	public void clearHybridContract(String fpcId, String reason)
	{
		final String normalizedId = normalize(fpcId);
		final HybridPartyContract removed = _contracts.remove(normalizedId);
		if (removed == null)
		{
			return;
		}
		_memberSnapshots.remove(normalizedId);
		rememberRecentExit(removed, reason);

		final Player leader = _worldFacade.findPlayer(removed._leaderObjectId);
		final Party party = (leader != null) ? leader.getParty() : null;
		if (party != null)
		{
			refreshPartyWindows(party);
			if ((party.getMemberCount() == 1) && (getHybridMemberCount(party) == 0))
			{
				party.disbandParty();
			}
		}

		LOGGER.info(() -> getClass().getSimpleName() + ": Cleared AFPC hybrid contract fpc=" + fpcId + " leader=" + removed._leaderName + " reason=" + safe(reason) + " ageMs=" + (System.currentTimeMillis() - removed._acceptedAtMs));
	}

	public int clearHybridContractsForLeader(Player leader, String reason)
	{
		if (leader == null)
		{
			return 0;
		}

		final List<String> fpcIds = new ArrayList<>();
		for (HybridPartyContract contract : _contracts.values())
		{
			if ((contract != null) && (contract._leaderObjectId == leader.getObjectId()))
			{
				fpcIds.add(contract._fpcId);
			}
		}

		for (String fpcId : fpcIds)
		{
			clearHybridContract(fpcId, reason);
		}
		return fpcIds.size();
	}

	public boolean removeHybridContractByName(Player leader, String fpcIdOrName, String reason)
	{
		if ((leader == null) || (fpcIdOrName == null) || fpcIdOrName.isBlank())
		{
			return false;
		}

		final FpcDefinition definition = findDefinition(fpcIdOrName);
		if (definition == null)
		{
			return false;
		}

		final HybridPartyContract contract = _contracts.get(normalize(definition.getId()));
		if (contract == null)
		{
			return false;
		}

		final Party leaderParty = leader.getParty();
		if ((leaderParty == null) || (contract._anchorPartyLeaderObjectId != leaderParty.getLeaderObjectId()) || !leaderParty.isLeader(leader))
		{
			return false;
		}

		clearHybridContract(definition.getId(), reason);
		return true;
	}

	public String getRecentExitReason(String fpcId, long windowMs)
	{
		final RecentContractExit exit = getMostRecentExit(fpcId);
		if (exit == null)
		{
			return "";
		}

		final long ageMs = System.currentTimeMillis() - exit._clearedAtMs;
		if ((windowMs > 0L) && (ageMs > windowMs))
		{
			return "";
		}
		return exit._reason;
	}

	public void refreshPartyWindows(Party party)
	{
		if (party == null)
		{
			return;
		}

		for (Player member : party.getMembers())
		{
			if (member == null)
			{
				continue;
			}
			member.sendPacket(PartySmallWindowDeleteAll.STATIC_PACKET);
			member.sendPacket(new PartySmallWindowAll(member, party));
			member.sendPacket(new PartyMemberPosition(party));
		}
		broadcastHybridPartySpelledForParty(party);
	}

	public void broadcastPartySpelled(Npc npc, PartySpelled packet)
	{
		if ((npc == null) || (packet == null))
		{
			return;
		}

		for (Player player : getHybridPartyPlayers(npc))
		{
			if ((player != null) && player.isOnline() && !player.isInOfflineMode())
			{
				player.sendPacket(packet);
			}
		}
	}

	public void broadcastHybridPartyVitalsUpdate(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		recordHybridMemberSnapshot(npc, false);

		final FpcDefinition definition = findDefinitionByNpc(npc);
		if (definition == null)
		{
			return;
		}

		final HybridPartyContract contract = _contracts.get(normalize(definition.getId()));
		if (contract == null)
		{
			return;
		}

		final Player leader = getActivePlayerLeader(definition.getId());
		if (leader == null)
		{
			return;
		}

		final Party party = leader.getParty();
		if (party == null)
		{
			return;
		}

		final Party.HybridPartyMember member = buildOverlayMember(contract);
		if (member == null)
		{
			return;
		}

		final PartySmallWindowUpdate packet = new PartySmallWindowUpdate(member, false);
		packet.addComponentType(PartySmallWindowUpdateType.CURRENT_CP);
		packet.addComponentType(PartySmallWindowUpdateType.MAX_CP);
		packet.addComponentType(PartySmallWindowUpdateType.CURRENT_HP);
		packet.addComponentType(PartySmallWindowUpdateType.MAX_HP);
		packet.addComponentType(PartySmallWindowUpdateType.CURRENT_MP);
		packet.addComponentType(PartySmallWindowUpdateType.MAX_MP);

		for (Player memberPlayer : party.getMembers())
		{
			if ((memberPlayer != null) && memberPlayer.isOnline() && !memberPlayer.isInOfflineMode())
			{
				memberPlayer.sendPacket(packet);
			}
		}
	}

	public void refreshPartyWindowsForNpc(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		recordHybridMemberSnapshot(npc, false);

		final FpcDefinition definition = findDefinitionByNpc(npc);
		if (definition == null)
		{
			return;
		}

		refreshPartyWindowsForFpc(definition.getId());
	}

	public void refreshPartyWindowsForFpc(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return;
		}

		final Player leader = getActivePlayerLeader(fpcId);
		if (leader == null)
		{
			return;
		}

		final Party party = leader.getParty();
		if (party != null)
		{
			refreshPartyWindows(party);
		}
	}

	public void recordDeadHybridMember(Npc npc)
	{
		recordHybridMemberSnapshot(npc, true);
	}

	public void recordLiveHybridMember(Npc npc)
	{
		recordHybridMemberSnapshot(npc, false);
	}

	private void broadcastHybridPartySpelledForParty(Party party)
	{
		if (party == null)
		{
			return;
		}

		for (Party.HybridPartyMember member : getHybridPartyMembers(party))
		{
			if (member == null)
			{
				continue;
			}

			final WorldObject worldObject = _worldFacade.findObject(member.getObjectId());
			if ((worldObject == null) || !worldObject.isNpc())
			{
				continue;
			}

			final Npc npc = worldObject.asNpc();
			broadcastPartySpelled(npc, buildPartySpelledPacket(npc));
		}
	}

	private PartySpelled buildPartySpelledPacket(Creature creature)
	{
		final PartySpelled packet = new PartySpelled(creature);
		if (creature == null)
		{
			return packet;
		}

		MobiusTierAEffectAccess.getEffects(creature).stream().filter(info -> (info != null) && info.isInUse() && info.isDisplayedForEffected() && !info.getSkill().isToggle()).forEach(packet::addSkill);
		return packet;
	}

	private Party.HybridPartyMember buildOverlayMember(HybridPartyContract contract)
	{
		final FpcDefinition definition = _registry.getDefinitionById(contract._fpcId);
		if (definition == null)
		{
			return null;
		}

		final Integer objectId = _registry.getActiveNpcObjectId(definition.getId());
		final Npc npc = resolveLiveNpc(objectId);
		if (npc != null)
		{
			final Party.HybridPartyMember member = buildOverlayMember(definition, npc, false);
			if (member != null)
			{
				_memberSnapshots.put(normalize(definition.getId()), member);
				return member;
			}
		}
		return _memberSnapshots.get(normalize(definition.getId()));
	}

	private void recordHybridMemberSnapshot(Npc npc, boolean forceDead)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		final FpcDefinition definition = findDefinitionByNpc(npc);
		if (definition == null)
		{
			return;
		}

		if (!_contracts.containsKey(normalize(definition.getId())))
		{
			return;
		}

		final Party.HybridPartyMember member = buildOverlayMember(definition, npc, forceDead);
		if (member != null)
		{
			_memberSnapshots.put(normalize(definition.getId()), member);
		}
	}

	private Npc resolveLiveNpc(Integer objectId)
	{
		if (objectId == null)
		{
			return null;
		}

		final WorldObject worldObject = _worldFacade.findObject(objectId.intValue());
		if ((worldObject == null) || !worldObject.isNpc())
		{
			return null;
		}
		return worldObject.asNpc();
	}

	private void restoreAcceptedMemberSyntheticCp(FpcDefinition definition)
	{
		if (definition == null)
		{
			return;
		}

		final Npc npc = resolveLiveNpc(_registry.getActiveNpcObjectId(definition.getId()));
		if ((npc == null) || npc.isDead())
		{
			return;
		}

		final NpcStatus status = npc.getStatus();
		final int maxCp = npc.getMaxCp();
		if ((status == null) || (maxCp <= 0) || (status.getCurrentCp() > 0))
		{
			return;
		}

		status.setCurrentCp(maxCp, false);
	}

	private Party.HybridPartyMember buildOverlayMember(FpcDefinition definition, Npc npc, boolean forceDead)
	{
		if ((definition == null) || (npc == null))
		{
			return null;
		}

		final ResolvedFpcCombatPower combatPower = _registry.getActiveCombatPower(definition.getId());
		final PlayerClass playerClass = (combatPower != null) ? combatPower.getResolvedPlayerClass() : null;
		final int level = (combatPower != null) ? combatPower.getEffectiveLevel() : Math.max(1, npc.getLevel());
		final int playerClassId = (playerClass != null) ? playerClass.getId() : 0;
		final int raceOrdinal = (playerClass != null) ? playerClass.getRace().ordinal() : 0;
		final int currentCp = forceDead ? 0 : (int) Math.round(npc.getCurrentCp());
		final int maxCp = npc.getMaxCp();
		final int currentHp = forceDead ? 0 : (int) npc.getCurrentHp();
		final int maxHp = (int) npc.getMaxHp();
		final int currentMp = (int) npc.getCurrentMp();
		final int maxMp = npc.getMaxMp();
		return new Party.HybridPartyMember(npc.getObjectId(), definition.getName(), currentCp, maxCp, currentHp, maxHp, currentMp, maxMp, 0, level, playerClassId, raceOrdinal, 0, npc.getX(), npc.getY(), npc.getZ());
	}

	private RecentContractExit getRecentExit(String fpcId, int leaderObjectId)
	{
		final String key = buildRecentExitKey(fpcId, leaderObjectId);
		final RecentContractExit exit = _recentExits.get(key);
		if (exit == null)
		{
			return null;
		}
		if ((System.currentTimeMillis() - exit._clearedAtMs) > RECENT_EXIT_MEMORY_MS)
		{
			_recentExits.remove(key, exit);
			return null;
		}
		return exit;
	}

	private RecentContractExit getMostRecentExit(String fpcId)
	{
		if ((fpcId == null) || fpcId.isBlank())
		{
			return null;
		}

		final String normalizedId = normalize(fpcId);
		final long now = System.currentTimeMillis();
		RecentContractExit latest = null;
		for (Map.Entry<String, RecentContractExit> entry : _recentExits.entrySet())
		{
			final RecentContractExit exit = entry.getValue();
			if (exit == null)
			{
				continue;
			}
			if ((now - exit._clearedAtMs) > RECENT_EXIT_MEMORY_MS)
			{
				_recentExits.remove(entry.getKey(), exit);
				continue;
			}
			if (!normalizedId.equals(exit._fpcId))
			{
				continue;
			}
			if ((latest == null) || (exit._clearedAtMs > latest._clearedAtMs))
			{
				latest = exit;
			}
		}
		return latest;
	}

	private void rememberRecentExit(HybridPartyContract contract, String reason)
	{
		if (contract == null)
		{
			return;
		}
		final RecentContractExit exit = new RecentContractExit(contract._fpcId, contract._leaderObjectId, contract._leaderName, contract._acceptedAtMs, System.currentTimeMillis(), reason);
		_recentExits.put(buildRecentExitKey(contract._fpcId, contract._leaderObjectId), exit);
	}

	private String buildRecentExitKey(String fpcId, int leaderObjectId)
	{
		return normalize(fpcId) + "#" + leaderObjectId;
	}

	private FpcDefinition findDefinitionByNpc(Npc npc)
	{
		if (npc == null)
		{
			return null;
		}
		return findDefinition(npc.getName());
	}

	private FpcDefinition findDefinition(String idOrName)
	{
		if ((idOrName == null) || idOrName.isBlank())
		{
			return null;
		}

		FpcDefinition definition = _registry.getDefinitionById(idOrName);
		if (definition == null)
		{
			definition = _registry.getDefinitionByName(idOrName);
		}
		return definition;
	}

	private String normalize(String value)
	{
		return ((value == null) || value.isBlank()) ? "" : value.toLowerCase(Locale.ENGLISH);
	}

	private String safe(String value)
	{
		return (value == null) ? "" : value;
	}

	private String describeDistribution(PartyDistributionType distributionType)
	{
		return (distributionType == null) ? "unknown" : distributionType.name().toLowerCase(Locale.ENGLISH);
	}
}
