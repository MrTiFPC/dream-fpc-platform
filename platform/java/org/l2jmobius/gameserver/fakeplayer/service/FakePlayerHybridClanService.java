package org.l2jmobius.gameserver.fakeplayer.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.managers.AntiFeedManager;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.enums.player.Sex;
import org.l2jmobius.gameserver.model.actor.holders.npc.FakePlayerHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanWar;
import org.l2jmobius.gameserver.model.clan.enums.ClanWarState;
import org.l2jmobius.gameserver.network.serverpackets.ExPledgeCount;
import org.l2jmobius.gameserver.network.serverpackets.PledgeInfo;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAdd;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListDelete;

public class FakePlayerHybridClanService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerHybridClanService.class.getName());
	public static final String HYBRID_CLAN_MODE = "hybrid_overlay";
	private static final long PERSIST_RETRY_COOLDOWN_MS = 60_000L;
	private static final String TABLE_NAME = "fakeplayer_hybrid_clan_contracts";
	private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + "fpc_id VARCHAR(64) NOT NULL, " + "fpc_name VARCHAR(64) NOT NULL, " + "clan_id INT NOT NULL, " + "clan_name VARCHAR(64) NOT NULL, " + "leader_object_id INT NOT NULL, " + "leader_name VARCHAR(64) NOT NULL, " + "pledge_type INT NOT NULL, " + "power_grade INT NOT NULL, " + "accepted_at BIGINT NOT NULL, " + "PRIMARY KEY (fpc_id), " + "INDEX idx_fakeplayer_hybrid_clan_clan (clan_id)" + ") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String UPSERT_CONTRACT = "REPLACE INTO " + TABLE_NAME + " (fpc_id, fpc_name, clan_id, clan_name, leader_object_id, leader_name, pledge_type, power_grade, accepted_at) VALUES (?,?,?,?,?,?,?,?,?)";
	private static final String DELETE_CONTRACT = "DELETE FROM " + TABLE_NAME + " WHERE fpc_id=?";
	private static final String LOAD_CONTRACTS = "SELECT fpc_id, fpc_name, clan_id, clan_name, leader_object_id, leader_name, pledge_type, power_grade, accepted_at FROM " + TABLE_NAME;
	private static volatile FakePlayerHybridClanService INSTANCE;

	private static final class HybridClanContract
	{
		private final String _fpcId;
		private final String _fpcName;
		private final int _clanId;
		private final String _clanName;
		private final int _leaderObjectId;
		private final String _leaderName;
		private final int _pledgeType;
		private final int _powerGrade;
		private final long _acceptedAtMs;
		private volatile boolean _persisted;
		private volatile long _lastPersistAttemptMs;

		private HybridClanContract(String fpcId, String fpcName, int clanId, String clanName, int leaderObjectId, String leaderName, int pledgeType, int powerGrade, long acceptedAtMs)
		{
			this(fpcId, fpcName, clanId, clanName, leaderObjectId, leaderName, pledgeType, powerGrade, acceptedAtMs, false);
		}

		private HybridClanContract(String fpcId, String fpcName, int clanId, String clanName, int leaderObjectId, String leaderName, int pledgeType, int powerGrade, long acceptedAtMs, boolean persisted)
		{
			_fpcId = (fpcId == null) ? "" : fpcId;
			_fpcName = (fpcName == null) ? "" : fpcName;
			_clanId = clanId;
			_clanName = (clanName == null) ? "" : clanName;
			_leaderObjectId = leaderObjectId;
			_leaderName = (leaderName == null) ? "" : leaderName;
			_pledgeType = pledgeType;
			_powerGrade = powerGrade;
			_acceptedAtMs = acceptedAtMs;
			_persisted = persisted;
			_lastPersistAttemptMs = persisted ? System.currentTimeMillis() : 0L;
		}
	}

	public enum HybridClanOfferResult
	{
		ACCEPTED,
		ALREADY_ACTIVE_WITH_CLAN,
		BLOCKED_OTHER_CLAN,
		BLOCKED_UNAVAILABLE
	}

	public enum HybridClanForceResult
	{
		FORCED_ACCEPTED,
		REASSIGNED_EXISTING_CONTRACT,
		ALREADY_ACTIVE_WITH_CLAN,
		BLOCKED_UNAVAILABLE,
		BLOCKED_FULL
	}

	public static final class HybridClanInviteState
	{
		public static final HybridClanInviteState EMPTY = new HybridClanInviteState(false, false, 0, "", 0, 0L);

		private final boolean _activeWithClan;
		private final boolean _activeWithOtherClan;
		private final int _currentClanId;
		private final String _currentClanName;
		private final int _pledgeType;
		private final long _acceptedAtMs;

		private HybridClanInviteState(boolean activeWithClan, boolean activeWithOtherClan, int currentClanId, String currentClanName, int pledgeType, long acceptedAtMs)
		{
			_activeWithClan = activeWithClan;
			_activeWithOtherClan = activeWithOtherClan;
			_currentClanId = currentClanId;
			_currentClanName = (currentClanName == null) ? "" : currentClanName;
			_pledgeType = pledgeType;
			_acceptedAtMs = acceptedAtMs;
		}

		public boolean isActiveWithClan()
		{
			return _activeWithClan;
		}

		public boolean isActiveWithOtherClan()
		{
			return _activeWithOtherClan;
		}

		public int getCurrentClanId()
		{
			return _currentClanId;
		}

		public String getCurrentClanName()
		{
			return _currentClanName;
		}

		public int getPledgeType()
		{
			return _pledgeType;
		}

		public long getAcceptedAtMs()
		{
			return _acceptedAtMs;
		}
	}

	public static final class HybridClanInviteDecision
	{
		private final boolean _accepted;
		private final String _reason;
		private final String _stance;
		private final int _score;

		private HybridClanInviteDecision(boolean accepted, String reason, String stance, int score)
		{
			_accepted = accepted;
			_reason = (reason == null) ? "" : reason;
			_stance = (stance == null) ? "" : stance;
			_score = score;
		}

		public boolean isAccepted()
		{
			return _accepted;
		}

		public String getReason()
		{
			return _reason;
		}

		public String getStance()
		{
			return _stance;
		}

		public int getScore()
		{
			return _score;
		}
	}

	public static final class HybridClanMemberSnapshot
	{
		private final String _fpcId;
		private final String _name;
		private final String _title;
		private final int _level;
		private final int _classId;
		private final int _raceOrdinal;
		private final boolean _female;
		private final int _objectId;
		private final int _onlineStatus;
		private final int _pledgeType;
		private final int _powerGrade;
		private final String _pledgeName;
		private final String _apprenticeOrSponsorName;
		private final boolean _hasSponsor;

		private HybridClanMemberSnapshot(String fpcId, String name, String title, int level, int classId, int raceOrdinal, boolean female, int objectId, int onlineStatus, int pledgeType, int powerGrade, String pledgeName, String apprenticeOrSponsorName, boolean hasSponsor)
		{
			_fpcId = (fpcId == null) ? "" : fpcId;
			_name = (name == null) ? "" : name;
			_title = (title == null) ? "" : title;
			_level = Math.max(1, level);
			_classId = Math.max(0, classId);
			_raceOrdinal = Math.max(0, raceOrdinal);
			_female = female;
			_objectId = objectId;
			_onlineStatus = Math.max(0, onlineStatus);
			_pledgeType = pledgeType;
			_powerGrade = Math.max(0, powerGrade);
			_pledgeName = (pledgeName == null) ? "" : pledgeName;
			_apprenticeOrSponsorName = (apprenticeOrSponsorName == null) ? "" : apprenticeOrSponsorName;
			_hasSponsor = hasSponsor;
		}

		public String getFpcId()
		{
			return _fpcId;
		}

		public String getName()
		{
			return _name;
		}

		public String getTitle()
		{
			return _title;
		}

		public int getLevel()
		{
			return _level;
		}

		public int getClassId()
		{
			return _classId;
		}

		public int getRaceOrdinal()
		{
			return _raceOrdinal;
		}

		public boolean isFemale()
		{
			return _female;
		}

		public int getObjectId()
		{
			return _objectId;
		}

		public int getOnlineStatus()
		{
			return _onlineStatus;
		}

		public int getPledgeType()
		{
			return _pledgeType;
		}

		public int getPowerGrade()
		{
			return _powerGrade;
		}

		public String getPledgeName()
		{
			return _pledgeName;
		}

		public String getApprenticeOrSponsorName()
		{
			return _apprenticeOrSponsorName;
		}

		public boolean hasSponsor()
		{
			return _hasSponsor;
		}
	}

	private final FpcRegistry _registry;
	private final Map<String, HybridClanContract> _contracts = new ConcurrentHashMap<>();
	private final Map<String, ConcurrentLinkedDeque<Long>> _recentWarDeaths = new ConcurrentHashMap<>();
	private final Map<String, Map<String, ConcurrentLinkedDeque<Long>>> _recentWarDeathsByPlayer = new ConcurrentHashMap<>();

	public FakePlayerHybridClanService(FpcRegistry registry)
	{
		INSTANCE = this;
		_registry = registry;
	}

	public static FakePlayerHybridClanService getInstance()
	{
		return INSTANCE;
	}

	public void initializePersistence()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (ResultSet result = con.getMetaData().getTables(null, null, TABLE_NAME, null))
			{
				if (!result.next())
				{
					try (Statement statement = con.createStatement())
					{
						statement.executeUpdate(CREATE_TABLE);
						LOGGER.info("Missing '" + TABLE_NAME + "' table was successfully created.");
					}
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Error creating '" + TABLE_NAME + "' table.", e);
		}
	}

	public void restorePersistedContracts()
	{
		final Map<String, HybridClanContract> restoredContracts = new HashMap<>();
		int staleContracts = 0;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(LOAD_CONTRACTS);
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				final String fpcId = result.getString("fpc_id");
				final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionById(fpcId);
				final Clan clan = ClanTable.getInstance().getClan(result.getInt("clan_id"));
				if ((definition == null) || !supportsHybridClanMembership(definition) || (clan == null) || (result.getInt("pledge_type") == Clan.SUBUNIT_ACADEMY))
				{
					deletePersistedContract(fpcId);
					staleContracts++;
					continue;
				}

				final HybridClanContract contract = new HybridClanContract(definition.getId(), definition.getName(), clan.getId(), clan.getName(), result.getInt("leader_object_id"), result.getString("leader_name"), result.getInt("pledge_type"), result.getInt("power_grade"), result.getLong("accepted_at"), true);
				restoredContracts.put(normalize(contract._fpcId), contract);
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not restore persisted hybrid clan contracts.", e);
			return;
		}

		_contracts.clear();
		_contracts.putAll(restoredContracts);
		LOGGER.info(getClass().getSimpleName() + ": Restored " + restoredContracts.size() + " persisted hybrid clan contracts" + (staleContracts > 0 ? " and dropped " + staleContracts + " stale rows." : "."));
	}

	public boolean supportsHybridClanMembership(FpcDefinition definition)
	{
		return (definition != null) && definition.isTalkable() && HYBRID_CLAN_MODE.equalsIgnoreCase(definition.getAdventurerProfile().getClanMode());
	}

	public HybridClanInviteState inspectInviteState(FpcDefinition definition, Clan clan)
	{
		if (definition == null)
		{
			return HybridClanInviteState.EMPTY;
		}

		final HybridClanContract contract = getActiveContract(definition.getId());
		if (contract == null)
		{
			return HybridClanInviteState.EMPTY;
		}

		final boolean activeWithClan = (clan != null) && (contract._clanId == clan.getId());
		final boolean activeWithOtherClan = (clan == null) || (contract._clanId != clan.getId());
		return new HybridClanInviteState(activeWithClan, activeWithOtherClan, contract._clanId, contract._clanName, contract._pledgeType, contract._acceptedAtMs);
	}

	public HybridClanInviteDecision evaluateClanInvite(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialMemory)
	{
		if ((definition == null) || (sender == null))
		{
			return new HybridClanInviteDecision(false, "invalid", "hostile", -99);
		}

		final FpcRelationshipSnapshot snapshot = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
		final FpcSocialSnapshot socialSnapshot = (socialMemory == null) ? FpcSocialSnapshot.EMPTY : socialMemory;
		if ((snapshot.getPlayerKillCount() > 0) || socialSnapshot.hasHistoricalKillHistory())
		{
			return new HybridClanInviteDecision(false, "personal_kill_history", "hostile", -20);
		}
		if (socialSnapshot.getActionStance().isAvoidPlayer())
		{
			return new HybridClanInviteDecision(false, "avoid_player", "hostile", -16);
		}

		final String socialLabel = normalize(socialSnapshot.getSocialLabel());
		if ("hostile".equals(socialLabel))
		{
			return new HybridClanInviteDecision(false, "hostile_memory", "hostile", -14);
		}
		if ("guarded".equals(socialLabel))
		{
			return new HybridClanInviteDecision(false, "guarded_memory", "guarded", -8);
		}

		int score = snapshot.getFamiliarity();
		score += snapshot.getTrust();
		score += snapshot.getRespect();
		score -= (snapshot.getTension() * 2);
		score -= (snapshot.getResentment() * 3);
		score += socialSnapshot.getTrustBias();
		score -= socialSnapshot.getGuardBias();

		String reason = "bond_ready";
		boolean liveRiskPressure = false;
		if (sender.getReputation() < 0)
		{
			score -= 2;
			reason = "chaotic_reputation";
			liveRiskPressure = true;
		}
		if (sender.getPkKills() >= 25)
		{
			score -= 3;
			reason = "violent_history";
			liveRiskPressure = true;
		}
		else if (sender.getPkKills() >= 10)
		{
			score -= 2;
			if ("bond_ready".equals(reason))
			{
				reason = "blooded_history";
			}
			liveRiskPressure = true;
		}
		else if (sender.getPkKills() > 0)
		{
			score -= 1;
			if ("bond_ready".equals(reason))
			{
				reason = "blooded_history";
			}
			liveRiskPressure = true;
		}
		if (sender.getPvpFlag() > 0)
		{
			score -= 3;
			if ("bond_ready".equals(reason))
			{
				reason = "combat_active";
			}
			liveRiskPressure = true;
		}

		final boolean maxFriendliness = (snapshot.getFamiliarity() >= 8) && (snapshot.getTrust() >= 8) && (snapshot.getRespect() >= 7) && (snapshot.getTension() <= 1) && (snapshot.getResentment() == 0);
		if (!maxFriendliness)
		{
			return new HybridClanInviteDecision(false, reason, "guarded", score);
		}
		if ((sender.getReputation() < 0) || (sender.getPvpFlag() > 0))
		{
			return new HybridClanInviteDecision(false, reason, "guarded", score);
		}
		if (score < 20)
		{
			return new HybridClanInviteDecision(false, "guarded_threshold", "guarded", score);
		}

		final String stance = liveRiskPressure ? "guarded" : (("trusted".equals(socialLabel) || "friendly".equals(socialLabel) || (socialSnapshot.getTrustBias() > 0)) ? "willing" : "guarded");
		return new HybridClanInviteDecision(true, reason, stance, score);
	}

	public String chooseAcceptanceLine(FpcDefinition definition, HybridClanInviteDecision decision)
	{
		final String name = normalize(definition != null ? definition.getName() : "");
		final String stance = normalize((decision == null) ? "willing" : decision.getStance());
		if ("pippa".equals(name))
		{
			return "guarded".equals(stance) ? "I will wear the crest, but keep your people steady." : "All right. I will carry your crest and hold the line with your clan.";
		}
		if ("caelan".equals(name))
		{
			return "guarded".equals(stance) ? "I will join, but I expect discipline from your house." : "Understood. I will stand under your clan name.";
		}
		return "guarded".equals(stance) ? "I will take your crest, but I am watching the way your clan carries itself." : "I will carry your clan crest for a while.";
	}

	public String chooseDeclineLine(FpcDefinition definition, HybridClanInviteDecision decision)
	{
		final String name = normalize(definition != null ? definition.getName() : "");
		final String reason = normalize((decision == null) ? "bond_not_ready" : decision.getReason());
		if ("personal_kill_history".equals(reason))
		{
			return "No. I remember dying by your hand.";
		}
		if ("hostile_memory".equals(reason) || "avoid_player".equals(reason))
		{
			return "No. We are not standing on that kind of ground.";
		}
		if ("guarded_memory".equals(reason) || "guarded_threshold".equals(reason))
		{
			return "No. Trust is not steady enough for a clan oath yet.";
		}
		if ("blooded_history".equals(reason) || "violent_history".equals(reason) || "combat_active".equals(reason) || "chaotic_reputation".equals(reason))
		{
			return "No. I will not take a clan oath while your road still smells like fresh blood.";
		}
		if ("pippa".equals(name))
		{
			return "Not yet. A clan crest asks for more trust than we have built.";
		}
		if ("caelan".equals(name))
		{
			return "Not yet. That kind of bond has to settle before I wear your crest.";
		}
		return "Not yet. A clan crest is a heavier promise than party road.";
	}

	public HybridClanOfferResult offerHybridContract(FpcDefinition definition, Player inviter, int pledgeType)
	{
		if ((definition == null) || (inviter == null) || !supportsHybridClanMembership(definition))
		{
			return HybridClanOfferResult.BLOCKED_UNAVAILABLE;
		}

		final Clan clan = inviter.getClan();
		if ((clan == null) || (pledgeType == Clan.SUBUNIT_ACADEMY))
		{
			return HybridClanOfferResult.BLOCKED_UNAVAILABLE;
		}

		final Npc npc = (_registry == null) ? null : _registry.resolveActiveNpc(definition.getId());
		if ((npc == null) || npc.isDead())
		{
			return HybridClanOfferResult.BLOCKED_UNAVAILABLE;
		}

		final String definitionKey = normalize(definition.getId());
		final HybridClanContract existing = getActiveContract(definitionKey);
		if (existing != null)
		{
			if (existing._clanId == clan.getId())
			{
				return HybridClanOfferResult.ALREADY_ACTIVE_WITH_CLAN;
			}

			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " declined hybrid clan contract from " + inviter.getName() + " because it is already contracted to clan=" + existing._clanName + "(" + existing._clanId + ")");
			return HybridClanOfferResult.BLOCKED_OTHER_CLAN;
		}

		final HybridClanContract contract = new HybridClanContract(definition.getId(), definition.getName(), clan.getId(), clan.getName(), inviter.getObjectId(), inviter.getName(), pledgeType, 5, System.currentTimeMillis());
		_contracts.put(definitionKey, contract);
		storeContract(contract);
		broadcastHybridMemberAdded(contract);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " accepted hybrid clan contract clan=" + clan.getName() + "(" + clan.getId() + ") leader=" + inviter.getName() + " pledgeType=" + pledgeType + " powerGrade=" + contract._powerGrade);
		return HybridClanOfferResult.ACCEPTED;
	}

	public HybridClanForceResult forceHybridContract(FpcDefinition definition, Player inviter, int pledgeType)
	{
		if ((definition == null) || (inviter == null) || !supportsHybridClanMembership(definition))
		{
			return HybridClanForceResult.BLOCKED_UNAVAILABLE;
		}

		final Clan clan = inviter.getClan();
		if ((clan == null) || (pledgeType == Clan.SUBUNIT_ACADEMY))
		{
			return HybridClanForceResult.BLOCKED_UNAVAILABLE;
		}

		final Npc npc = (_registry == null) ? null : _registry.resolveActiveNpc(definition.getId());
		if ((npc == null) || npc.isDead())
		{
			return HybridClanForceResult.BLOCKED_UNAVAILABLE;
		}

		final String definitionKey = normalize(definition.getId());
		final HybridClanContract existing = getActiveContract(definitionKey);
		if ((existing != null) && (existing._clanId == clan.getId()) && (existing._pledgeType == pledgeType))
		{
			return HybridClanForceResult.ALREADY_ACTIVE_WITH_CLAN;
		}

		final int activeMembers = clan.getSubPledgeMembersCount(pledgeType) + getHybridMemberCount(clan, pledgeType);
		if (activeMembers >= clan.getMaxNrOfMembers(pledgeType))
		{
			return HybridClanForceResult.BLOCKED_FULL;
		}

		final boolean reassigned = existing != null;
		if (reassigned)
		{
			clearHybridContract(definitionKey, "admin_force_join");
		}

		final HybridClanContract contract = new HybridClanContract(definition.getId(), definition.getName(), clan.getId(), clan.getName(), inviter.getObjectId(), inviter.getName(), pledgeType, 5, System.currentTimeMillis());
		_contracts.put(definitionKey, contract);
		storeContract(contract);
		broadcastHybridMemberAdded(contract);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " force-joined hybrid clan contract clan=" + clan.getName() + "(" + clan.getId() + ") leader=" + inviter.getName() + " pledgeType=" + pledgeType + " powerGrade=" + contract._powerGrade + " reassigned=" + reassigned);
		return reassigned ? HybridClanForceResult.REASSIGNED_EXISTING_CONTRACT : HybridClanForceResult.FORCED_ACCEPTED;
	}

	public int getHybridClanId(String fpcId)
	{
		final HybridClanContract contract = getActiveContract(fpcId);
		return (contract == null) ? 0 : contract._clanId;
	}

	public int getHybridClanId(Npc npc)
	{
		final HybridClanContract contract = getActiveContractForNpc(npc);
		return (contract == null) ? 0 : contract._clanId;
	}

	public Clan getHybridClan(Npc npc)
	{
		final int clanId = getHybridClanId(npc);
		return clanId > 0 ? ClanTable.getInstance().getClan(clanId) : null;
	}

	public Clan getDisplayClan(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return null;
		}

		final Clan hybridClan = getHybridClan(npc);
		if (hybridClan != null)
		{
			return hybridClan;
		}

		final FakePlayerHolder holder = (npc.getTemplate() == null) ? null : npc.getTemplate().getFakePlayerInfo();
		if ((holder == null) || (holder.getClanId() <= 0))
		{
			return null;
		}
		return ClanTable.getInstance().getClan(holder.getClanId());
	}

	public int getDisplayPledgeType(Npc npc)
	{
		final HybridClanContract contract = getActiveContractForNpc(npc);
		return (contract == null) ? 0 : contract._pledgeType;
	}

	public int getDisplayPowerGrade(Npc npc)
	{
		final HybridClanContract contract = getActiveContractForNpc(npc);
		if (contract != null)
		{
			return contract._powerGrade;
		}

		final FakePlayerHolder holder = ((npc == null) || (npc.getTemplate() == null)) ? null : npc.getTemplate().getFakePlayerInfo();
		return (holder == null) ? 0 : holder.getPledgeStatus();
	}

	public void syncDisplayClanId(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		final Clan clan = getDisplayClan(npc);
		npc.setClanId((clan == null) ? 0 : clan.getId());
	}

	public void prepareDisplayClanInfo(Player viewer, Npc npc)
	{
		if ((viewer == null) || (npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		final Clan clan = getDisplayClan(npc);
		npc.setClanId((clan == null) ? 0 : clan.getId());
		if (clan != null)
		{
			viewer.sendPacket(new PledgeInfo(clan));
		}
	}

	public Clan getEffectiveClan(Creature creature)
	{
		if (creature == null)
		{
			return null;
		}

		if (creature.isFakePlayer() && creature.isNpc())
		{
			return getHybridClan(creature.asNpc());
		}

		final Player player = creature.asPlayer();
		return (player != null) ? player.getClan() : creature.getClan();
	}

	public boolean isHybridClanWarAttack(Creature attacker, Creature target)
	{
		return resolveHybridClanWar(attacker, target) != null;
	}

	public int getRecentHybridWarDeathCount(String fpcId, long windowMs)
	{
		final String normalizedFpcId = normalize(fpcId);
		if (normalizedFpcId.isBlank())
		{
			return 0;
		}

		final ConcurrentLinkedDeque<Long> recentDeaths = _recentWarDeaths.get(normalizedFpcId);
		if (recentDeaths == null)
		{
			return 0;
		}

		pruneRecentWarDeaths(recentDeaths, windowMs);
		if (recentDeaths.isEmpty())
		{
			_recentWarDeaths.remove(normalizedFpcId, recentDeaths);
			return 0;
		}
		return recentDeaths.size();
	}

	public boolean isHybridWarRetreatActive(String fpcId, long windowMs, int deathThreshold)
	{
		return getRecentHybridWarDeathCount(fpcId, windowMs) >= Math.max(1, deathThreshold);
	}

	public int getRecentHybridWarDeathsFromPlayer(String fpcId, String playerName, long windowMs)
	{
		final String normalizedFpcId = normalize(fpcId);
		final String normalizedPlayerName = normalize(playerName);
		if (normalizedFpcId.isBlank() || normalizedPlayerName.isBlank())
		{
			return 0;
		}

		final Map<String, ConcurrentLinkedDeque<Long>> deathsByPlayer = _recentWarDeathsByPlayer.get(normalizedFpcId);
		if (deathsByPlayer == null)
		{
			return 0;
		}

		final ConcurrentLinkedDeque<Long> recentDeaths = deathsByPlayer.get(normalizedPlayerName);
		if (recentDeaths == null)
		{
			return 0;
		}

		pruneRecentWarDeaths(recentDeaths, windowMs);
		if (recentDeaths.isEmpty())
		{
			deathsByPlayer.remove(normalizedPlayerName, recentDeaths);
			if (deathsByPlayer.isEmpty())
			{
				_recentWarDeathsByPlayer.remove(normalizedFpcId, deathsByPlayer);
			}
			return 0;
		}
		return recentDeaths.size();
	}

	public boolean isHybridClanWarTargetSuppressed(Npc npc, Player player, long windowMs, int deathThreshold)
	{
		if ((npc == null) || (player == null) || !npc.isFakePlayer())
		{
			return false;
		}

		final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionByName(npc.getName());
		if (definition == null)
		{
			return false;
		}

		return getRecentHybridWarDeathsFromPlayer(definition.getId(), player.getName(), windowMs) >= Math.max(1, deathThreshold);
	}

	public boolean registerHybridWarKill(Creature killer, Creature victim)
	{
		if ((killer == null) || (victim == null) || (!killer.isFakePlayer() && !victim.isFakePlayer()))
		{
			return false;
		}

		final ClanWar war = resolveHybridClanWar(killer, victim);
		if (war == null)
		{
			return false;
		}

		if (victim.isPlayer() && !AntiFeedManager.getInstance().check(killer, victim))
		{
			return false;
		}

		final Clan killerClan = getEffectiveClan(killer);
		final Clan victimClan = getEffectiveClan(victim);
		if ((killerClan == null) || (victimClan == null))
		{
			return false;
		}

		war.onHybridKill(killerClan, victimClan, Math.max(1, victim.getLevel()), victim.getReputation());
		recordHybridWarDeath(victim, killer);
		return true;
	}

	public int getHybridMemberCount(Clan clan, int pledgeType)
	{
		if (clan == null)
		{
			return 0;
		}

		int count = 0;
		for (HybridClanContract contract : _contracts.values())
		{
			final HybridClanContract activeContract = getActiveContract(contract._fpcId);
			if ((activeContract != null) && (activeContract._clanId == clan.getId()) && (activeContract._pledgeType == pledgeType))
			{
				count++;
			}
		}
		return count;
	}

	public int getHybridOnlineMemberCount(Clan clan)
	{
		if (clan == null)
		{
			return 0;
		}

		int count = 0;
		for (HybridClanMemberSnapshot member : getHybridClanMembers(clan))
		{
			if (member.getOnlineStatus() > 0)
			{
				count++;
			}
		}
		return count;
	}

	public List<HybridClanMemberSnapshot> getHybridClanMembers(Clan clan)
	{
		final List<HybridClanMemberSnapshot> members = new ArrayList<>();
		if (clan == null)
		{
			return members;
		}

		for (HybridClanContract contract : _contracts.values())
		{
			final HybridClanContract activeContract = getActiveContract(contract._fpcId);
			if ((activeContract == null) || (activeContract._clanId != clan.getId()))
			{
				continue;
			}

			final HybridClanMemberSnapshot snapshot = buildMemberSnapshot(activeContract);
			if (snapshot != null)
			{
				members.add(snapshot);
			}
		}
		return members;
	}

	public List<HybridClanMemberSnapshot> getHybridClanMembers(Clan clan, int pledgeType)
	{
		final List<HybridClanMemberSnapshot> members = new ArrayList<>();
		for (HybridClanMemberSnapshot member : getHybridClanMembers(clan))
		{
			if (member.getPledgeType() == pledgeType)
			{
				members.add(member);
			}
		}
		return members;
	}

	public HybridClanMemberSnapshot getHybridClanMember(Clan clan, String memberName)
	{
		if ((clan == null) || (memberName == null) || memberName.isBlank())
		{
			return null;
		}

		for (HybridClanMemberSnapshot member : getHybridClanMembers(clan))
		{
			if (memberName.equalsIgnoreCase(member.getName()))
			{
				return member;
			}
		}
		return null;
	}

	public void clearHybridContract(String fpcId, String reason)
	{
		final String normalizedFpcId = normalize(fpcId);
		if (normalizedFpcId.isBlank())
		{
			return;
		}

		final HybridClanContract removed = _contracts.remove(normalizedFpcId);
		if (removed != null)
		{
			_recentWarDeaths.remove(normalizedFpcId);
			_recentWarDeathsByPlayer.remove(normalizedFpcId);
			deletePersistedContract(normalizedFpcId);
			broadcastHybridMemberRemoved(removed);
			LOGGER.info(() -> getClass().getSimpleName() + ": Cleared hybrid clan contract fpc=" + removed._fpcId + " clan=" + removed._clanName + "(" + removed._clanId + ") leader=" + removed._leaderName + " reason=" + normalizeReason(reason));
		}
	}

	private HybridClanContract getActiveContract(String fpcId)
	{
		final String normalizedFpcId = normalize(fpcId);
		if (normalizedFpcId.isBlank())
		{
			return null;
		}

		final HybridClanContract contract = _contracts.get(normalizedFpcId);
		if (contract == null)
		{
			return null;
		}
		final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionById(contract._fpcId);
		if ((definition == null) || !supportsHybridClanMembership(definition) || (contract._pledgeType == Clan.SUBUNIT_ACADEMY))
		{
			_contracts.remove(normalizedFpcId, contract);
			_recentWarDeaths.remove(normalizedFpcId);
			_recentWarDeathsByPlayer.remove(normalizedFpcId);
			deletePersistedContract(normalizedFpcId);
			LOGGER.info(() -> getClass().getSimpleName() + ": Removed stale hybrid clan contract fpc=" + contract._fpcId + " reason=" + ((definition == null) ? "missing_definition" : "unsupported_mode"));
			return null;
		}
		if (ClanTable.getInstance().getClan(contract._clanId) == null)
		{
			_contracts.remove(normalizedFpcId, contract);
			_recentWarDeaths.remove(normalizedFpcId);
			_recentWarDeathsByPlayer.remove(normalizedFpcId);
			deletePersistedContract(normalizedFpcId);
			LOGGER.info(() -> getClass().getSimpleName() + ": Removed stale hybrid clan contract fpc=" + contract._fpcId + " missingClanId=" + contract._clanId);
			return null;
		}
		ensureContractPersisted(contract);
		return contract;
	}

	private HybridClanContract getActiveContractForNpc(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return null;
		}

		final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionByName(npc.getName());
		return (definition == null) ? null : getActiveContract(definition.getId());
	}

	private String normalize(String value)
	{
		return (value == null) ? "" : value.trim().toLowerCase();
	}

	private String normalizeReason(String value)
	{
		return ((value == null) || value.isBlank()) ? "unspecified" : value.trim().toLowerCase();
	}

	private void recordHybridWarDeath(Creature victim, Creature killer)
	{
		if ((victim == null) || !victim.isFakePlayer() || !victim.isNpc())
		{
			return;
		}

		final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionByName(victim.getName());
		if (definition == null)
		{
			return;
		}

		final String definitionKey = normalize(definition.getId());
		final long now = System.currentTimeMillis();
		_recentWarDeaths.computeIfAbsent(definitionKey, key -> new ConcurrentLinkedDeque<>()).addLast(Long.valueOf(now));
		final Player killerPlayer = (killer == null) ? null : killer.asPlayer();
		if (killerPlayer != null)
		{
			_recentWarDeathsByPlayer.computeIfAbsent(definitionKey, key -> new ConcurrentHashMap<>()).computeIfAbsent(normalize(killerPlayer.getName()), key -> new ConcurrentLinkedDeque<>()).addLast(Long.valueOf(now));
		}
	}

	private void pruneRecentWarDeaths(ConcurrentLinkedDeque<Long> recentDeaths, long windowMs)
	{
		if (recentDeaths == null)
		{
			return;
		}

		final long cutoff = System.currentTimeMillis() - Math.max(windowMs, 1L);
		while (true)
		{
			final Long oldest = recentDeaths.peekFirst();
			if ((oldest == null) || (oldest.longValue() >= cutoff))
			{
				return;
			}
			recentDeaths.pollFirst();
		}
	}

	private HybridClanMemberSnapshot buildMemberSnapshot(HybridClanContract contract)
	{
		if (contract == null)
		{
			return null;
		}

		final FpcDefinition definition = (_registry == null) ? null : _registry.getDefinitionById(contract._fpcId);
		final Npc npc = (_registry == null) ? null : _registry.resolveActiveNpc(contract._fpcId);
		final ResolvedFpcCombatPower combatPower = (_registry == null) ? null : _registry.getActiveCombatPower(contract._fpcId);
		final NpcTemplate template = resolveTemplate(definition, npc);
		final FakePlayerHolder holder = (template == null) ? null : template.getFakePlayerInfo();
		final PlayerClass playerClass = (combatPower != null) ? combatPower.getResolvedPlayerClass() : ((holder == null) ? null : holder.getPlayerClass());
		final int level = (combatPower != null) ? combatPower.getEffectiveLevel() : resolveFallbackLevel(definition, npc);
		final int classId = (playerClass == null) ? 0 : playerClass.getId();
		final int raceOrdinal = (playerClass == null) ? ((npc == null) ? 0 : npc.getRace().ordinal()) : playerClass.getRace().ordinal();
		final boolean female = (npc != null) ? (npc.getTemplate().getSex() == Sex.FEMALE) : ((template != null) && (template.getSex() == Sex.FEMALE));
		final int objectId = (npc == null) ? 0 : npc.getObjectId();
		final int onlineStatus = (npc == null) ? 0 : 1;
		final Clan clan = ClanTable.getInstance().getClan(contract._clanId);
		final String pledgeName = resolvePledgeName(clan, contract._pledgeType, contract._clanName);
		final String title = ((definition == null) || definition.getTitle().isBlank()) ? ((npc == null) ? "" : npc.getTitle()) : definition.getTitle();
		return new HybridClanMemberSnapshot(contract._fpcId, contract._fpcName, title, level, classId, raceOrdinal, female, objectId, onlineStatus, contract._pledgeType, contract._powerGrade, pledgeName, "", false);
	}

	private NpcTemplate resolveTemplate(FpcDefinition definition, Npc npc)
	{
		if (npc != null)
		{
			return npc.getTemplate();
		}
		if ((definition == null) || (definition.getCarrierNpcId() <= 0))
		{
			return null;
		}
		return NpcData.getInstance().getTemplate(definition.getCarrierNpcId());
	}

	private int resolveFallbackLevel(FpcDefinition definition, Npc npc)
	{
		if (npc != null)
		{
			return Math.max(1, npc.getLevel());
		}
		if ((definition != null) && (definition.getAdventurerProfile().getFixedLevel() > 0))
		{
			return definition.getAdventurerProfile().getFixedLevel();
		}
		return 1;
	}

	private String resolvePledgeName(Clan clan, int pledgeType, String fallbackClanName)
	{
		if (clan == null)
		{
			return (fallbackClanName == null) ? "" : fallbackClanName;
		}
		if ((pledgeType != 0) && (clan.getSubPledge(pledgeType) != null))
		{
			return clan.getSubPledge(pledgeType).getName();
		}
		return clan.getName();
	}

	private void ensureContractPersisted(HybridClanContract contract)
	{
		if ((contract == null) || contract._persisted)
		{
			return;
		}

		final long now = System.currentTimeMillis();
		if ((now - contract._lastPersistAttemptMs) < PERSIST_RETRY_COOLDOWN_MS)
		{
			return;
		}

		synchronized (contract)
		{
			if (contract._persisted)
			{
				return;
			}

			final long retryAt = System.currentTimeMillis();
			if ((retryAt - contract._lastPersistAttemptMs) < PERSIST_RETRY_COOLDOWN_MS)
			{
				return;
			}

			storeContract(contract);
		}
	}

	private boolean storeContract(HybridClanContract contract)
	{
		if (contract == null)
		{
			return false;
		}

		contract._lastPersistAttemptMs = System.currentTimeMillis();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(UPSERT_CONTRACT))
		{
			statement.setString(1, contract._fpcId);
			statement.setString(2, contract._fpcName);
			statement.setInt(3, contract._clanId);
			statement.setString(4, contract._clanName);
			statement.setInt(5, contract._leaderObjectId);
			statement.setString(6, contract._leaderName);
			statement.setInt(7, contract._pledgeType);
			statement.setInt(8, contract._powerGrade);
			statement.setLong(9, contract._acceptedAtMs);
			statement.execute();
			contract._persisted = true;
			return true;
		}
		catch (SQLException e)
		{
			contract._persisted = false;
			LOGGER.log(Level.WARNING, "Could not persist hybrid clan contract for fpc=" + contract._fpcId + " clanId=" + contract._clanId, e);
			return false;
		}
	}

	private void deletePersistedContract(String fpcId)
	{
		final String normalizedFpcId = normalize(fpcId);
		if (normalizedFpcId.isBlank())
		{
			return;
		}

		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(DELETE_CONTRACT))
		{
			statement.setString(1, normalizedFpcId);
			statement.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not delete persisted hybrid clan contract for fpc=" + normalizedFpcId, e);
		}
	}

	private ClanWar resolveHybridClanWar(Creature attacker, Creature target)
	{
		if ((attacker == null) || (target == null))
		{
			return null;
		}

		final Clan attackerClan = getEffectiveClan(attacker);
		final Clan targetClan = getEffectiveClan(target);
		if ((attackerClan == null) || (targetClan == null) || (attackerClan == targetClan))
		{
			return null;
		}

		final Player attackerPlayer = attacker.asPlayer();
		if ((attackerPlayer != null) && (attackerPlayer.isAcademyMember() || (attackerPlayer.getWantsPeace() != 0)))
		{
			return null;
		}

		final Player targetPlayer = target.asPlayer();
		if ((targetPlayer != null) && (targetPlayer.isAcademyMember() || (targetPlayer.getWantsPeace() != 0)))
		{
			return null;
		}

		final ClanWar war = attackerClan.getWarWith(targetClan.getId());
		if (war == null)
		{
			return null;
		}

		final ClanWarState state = war.getState();
		if (state == ClanWarState.MUTUAL)
		{
			return war;
		}
		if (((state == ClanWarState.BLOOD_DECLARATION) || (state == ClanWarState.DECLARATION)) && (war.getAttackerClanId() == targetClan.getId()))
		{
			return war;
		}
		return null;
	}

	private void broadcastHybridMemberAdded(HybridClanContract contract)
	{
		if (contract == null)
		{
			return;
		}

		final Clan clan = ClanTable.getInstance().getClan(contract._clanId);
		if (clan == null)
		{
			return;
		}

		final HybridClanMemberSnapshot snapshot = buildMemberSnapshot(contract);
		if (snapshot != null)
		{
			clan.broadcastToOnlineMembers(new PledgeShowMemberListAdd(snapshot));
		}
		clan.broadcastToOnlineMembers(new ExPledgeCount(clan));
		final Npc npc = (_registry == null) ? null : _registry.resolveActiveNpc(contract._fpcId);
		if (npc != null)
		{
			syncDisplayClanId(npc);
		}
	}

	private void broadcastHybridMemberRemoved(HybridClanContract contract)
	{
		if (contract == null)
		{
			return;
		}

		final Clan clan = ClanTable.getInstance().getClan(contract._clanId);
		if (clan != null)
		{
			clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(contract._fpcName));
			clan.broadcastToOnlineMembers(new ExPledgeCount(clan));
		}

		final Npc npc = (_registry == null) ? null : _registry.resolveActiveNpc(contract._fpcId);
		if (npc != null)
		{
			syncDisplayClanId(npc);
			npc.broadcastInfo();
		}
	}
}
