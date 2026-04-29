package org.l2jmobius.gameserver.fakeplayer.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.FakePlayerData;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerReplySettingsData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplySettings;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerAdvisoryPlan;
import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardAssessment;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRouteProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerChatTransport;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.network.enums.ChatType;

/**
 * Draft chat service placeholder.
 */
public class FakePlayerChatService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerChatService.class.getName());
	
	private static final Map<String, String> STYLE_TOPIC_LINES = Map.ofEntries(
		Map.entry("casual_short|smalltalk", "quiet day out here."),
		Map.entry("casual_short|grind", "lets keep the grind going."),
		Map.entry("casual_short|zone", "this area feels decent."),
		Map.entry("friendly_short|smalltalk", "you all doing ok?"),
		Map.entry("friendly_short|grind", "we can clear a few more."),
		Map.entry("boast_short|brag", "easy route, easy wins."),
		Map.entry("boast_short|zone", "i know a better spot."),
		Map.entry("dry_short|smalltalk", "still moving."),
		Map.entry("dry_short|zone", "changing route."),
		Map.entry("warning_short|danger", "stay sharp here."),
		Map.entry("warning_short|zone", "watch this area.")
	);
	
	private final FakePlayerConfig _config;
	private final FakePlayerReplySettingsData _replySettingsData;
	private final FpcRegistry _registry;
	private final FakePlayerMessageClassifier _messageClassifier;
	private final FakePlayerWorldFacade _worldFacade;
	private final FakePlayerChatTransport _chatTransport;
	private final ConcurrentHashMap<String, Long> _lastGeneralChatTimeBySpeaker = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastPublicChatTimeBySpeaker = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastPartyChatTimeBySpeaker = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastClanChatTimeBySpeaker = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastWhisperChatTimeByTarget = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Deque<ConversationTurn>> _recentConversationByPair = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Deque<SalientMemory>> _salientMemoryByPair = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, RelationshipState> _relationshipStateByPair = new ConcurrentHashMap<>();
	private final Set<String> _loadedRecentConversationPairs = ConcurrentHashMap.newKeySet();
	private final Set<String> _loadedSalientMemoryPairs = ConcurrentHashMap.newKeySet();
	private final Set<String> _loadedRelationshipPairs = ConcurrentHashMap.newKeySet();

	private static final Set<String> NON_SALIENT_CATEGORIES = Set.of("greeting", "smalltalk", "status", "weather", "name");
	private static final long SALIENT_MEMORY_TTL_MS = 21600000L;
	private static final int MAX_SALIENT_MEMORIES = 6;
	private static final long RELATIONSHIP_DECAY_GRACE_MS = 25L * 60L * 1000L;
	private static final long FAMILIARITY_DECAY_STEP_MS = 3L * 60L * 60L * 1000L;
	private static final long TRUST_RESPECT_DECAY_STEP_MS = 90L * 60L * 1000L;
	private static final long TENSION_DECAY_STEP_MS = 35L * 60L * 1000L;
	private static final long RESENTMENT_DECAY_STEP_MS = 3L * 60L * 60L * 1000L;
	private static final long GOAL_NEED_FADE_MS = 45L * 60L * 1000L;
	private static final long TOPIC_FADE_MS = 2L * 60L * 60L * 1000L;
	private static final long ENTITY_TOPIC_FADE_MS = 4L * 60L * 60L * 1000L;
	private static final int MAX_RECENT_CONVERSATION_PERSIST = 8;
	private static final String RELATIONSHIP_TABLE_NAME = "fakeplayer_relationship_state";
	private static final String RELATIONSHIP_CREATE_TABLE = "CREATE TABLE " + RELATIONSHIP_TABLE_NAME + " (" + "speaker_key VARCHAR(64) NOT NULL, " + "player_key VARCHAR(64) NOT NULL, " + "familiarity INT NOT NULL, " + "trust_level INT NOT NULL, " + "respect_level INT NOT NULL, " + "tension INT NOT NULL, " + "resentment INT NOT NULL, " + "player_kill_count INT NOT NULL, " + "current_goal TEXT NULL, " + "active_need TEXT NULL, " + "last_topic VARCHAR(128) NULL, " + "updated_at BIGINT NOT NULL, " + "PRIMARY KEY (speaker_key, player_key)" + ") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String RELATIONSHIP_UPSERT = "REPLACE INTO " + RELATIONSHIP_TABLE_NAME + " (speaker_key, player_key, familiarity, trust_level, respect_level, tension, resentment, player_kill_count, current_goal, active_need, last_topic, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String RELATIONSHIP_LOAD = "SELECT familiarity, trust_level, respect_level, tension, resentment, player_kill_count, current_goal, active_need, last_topic, updated_at FROM " + RELATIONSHIP_TABLE_NAME + " WHERE speaker_key=? AND player_key=?";
	private static final String RELATIONSHIP_DELETE = "DELETE FROM " + RELATIONSHIP_TABLE_NAME + " WHERE speaker_key=? AND player_key=?";
	private static final String CONVERSATION_TABLE_NAME = "fakeplayer_recent_conversation";
	private static final String CONVERSATION_CREATE_TABLE = "CREATE TABLE " + CONVERSATION_TABLE_NAME + " (" + "id BIGINT NOT NULL AUTO_INCREMENT, " + "speaker_key VARCHAR(64) NOT NULL, " + "player_key VARCHAR(64) NOT NULL, " + "channel_name VARCHAR(16) NOT NULL, " + "player_name VARCHAR(64) NOT NULL, " + "player_line TEXT NOT NULL, " + "fakeplayer_line TEXT NOT NULL, " + "message_category VARCHAR(64) NOT NULL, " + "knowledge_type VARCHAR(64) NOT NULL, " + "topic VARCHAR(128) NOT NULL, " + "created_at BIGINT NOT NULL, " + "PRIMARY KEY (id), " + "INDEX idx_fakeplayer_recent_conversation_pair (speaker_key, player_key, created_at)" + ") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String CONVERSATION_INSERT = "INSERT INTO " + CONVERSATION_TABLE_NAME + " (speaker_key, player_key, channel_name, player_name, player_line, fakeplayer_line, message_category, knowledge_type, topic, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
	private static final String CONVERSATION_LOAD = "SELECT channel_name, player_name, player_line, fakeplayer_line, message_category, knowledge_type, topic, created_at FROM " + CONVERSATION_TABLE_NAME + " WHERE speaker_key=? AND player_key=? ORDER BY created_at DESC LIMIT ?";
	private static final String CONVERSATION_DELETE = "DELETE FROM " + CONVERSATION_TABLE_NAME + " WHERE speaker_key=? AND player_key=?";
	private static final String SALIENT_TABLE_NAME = "fakeplayer_salient_memory";
	private static final String SALIENT_CREATE_TABLE = "CREATE TABLE " + SALIENT_TABLE_NAME + " (" + "id BIGINT NOT NULL AUTO_INCREMENT, " + "speaker_key VARCHAR(64) NOT NULL, " + "player_key VARCHAR(64) NOT NULL, " + "topic VARCHAR(128) NOT NULL, " + "summary TEXT NOT NULL, " + "created_at BIGINT NOT NULL, " + "PRIMARY KEY (id), " + "INDEX idx_fakeplayer_salient_memory_pair (speaker_key, player_key, created_at)" + ") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String SALIENT_INSERT = "INSERT INTO " + SALIENT_TABLE_NAME + " (speaker_key, player_key, topic, summary, created_at) VALUES (?,?,?,?,?)";
	private static final String SALIENT_LOAD = "SELECT topic, summary, created_at FROM " + SALIENT_TABLE_NAME + " WHERE speaker_key=? AND player_key=? ORDER BY created_at DESC LIMIT ?";
	private static final String SALIENT_DELETE = "DELETE FROM " + SALIENT_TABLE_NAME + " WHERE speaker_key=? AND player_key=?";

	private static final class ConversationTurn
	{
		private final long _timestampMs;
		private final String _channel;
		private final String _playerName;
		private final String _playerLine;
		private final String _fakePlayerLine;
		private final String _messageCategory;
		private final String _knowledgeType;
		private final String _topic;

		private ConversationTurn(long timestampMs, String channel, String playerName, String playerLine, String fakePlayerLine, String messageCategory, String knowledgeType, String topic)
		{
			_timestampMs = timestampMs;
			_channel = channel;
			_playerName = playerName;
			_playerLine = playerLine;
			_fakePlayerLine = fakePlayerLine;
			_messageCategory = messageCategory;
			_knowledgeType = knowledgeType;
			_topic = topic;
		}
	}

	private static final class SalientMemory
	{
		private final long _timestampMs;
		private final String _topic;
		private final String _summary;

		private SalientMemory(long timestampMs, String topic, String summary)
		{
			_timestampMs = timestampMs;
			_topic = topic;
			_summary = summary;
		}
	}

	private static final class RelationshipState
	{
		private int _familiarity;
		private int _trust;
		private int _respect;
		private int _tension;
		private int _resentment;
		private int _playerKillCount;
		private String _currentGoal = "";
		private String _activeNeed = "";
		private String _lastImportantTopic = "";
		private long _lastUpdatedMs;
	}

	private static final class ResolvedSpeakerRuntime
	{
		private final FpcDefinition _definition;
		private final Npc _npc;
		private final String _displayName;

		private ResolvedSpeakerRuntime(FpcDefinition definition, Npc npc, String displayName)
		{
			_definition = definition;
			_npc = npc;
			_displayName = displayName;
		}
	}

	private static final class PairPersistenceKeys
	{
		private final String _pairKey;
		private final String _speakerKey;
		private final String _playerKey;

		private PairPersistenceKeys(String pairKey, String speakerKey, String playerKey)
		{
			_pairKey = pairKey;
			_speakerKey = speakerKey;
			_playerKey = playerKey;
		}
	}

	@FunctionalInterface
	private interface PairValueLoader<T>
	{
		void load(T value, PairPersistenceKeys keys);
	}

	@FunctionalInterface
	private interface PairResultMapper<T>
	{
		T map(ResultSet result) throws SQLException;
	}

	@FunctionalInterface
	private interface PairBatchBinder<T>
	{
		void bind(PreparedStatement statement, PairPersistenceKeys keys, T value) throws SQLException;
	}
	
	public FakePlayerChatService(FakePlayerConfig config, FakePlayerReplySettingsData replySettingsData, FpcRegistry registry, FakePlayerMessageClassifier messageClassifier, FakePlayerWorldFacade worldFacade, FakePlayerChatTransport chatTransport)
	{
		_config = config;
		_replySettingsData = replySettingsData;
		_registry = registry;
		_messageClassifier = messageClassifier;
		_worldFacade = worldFacade;
		_chatTransport = chatTransport;
	}

	public void initializePersistence()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			ensureTable(con, RELATIONSHIP_TABLE_NAME, RELATIONSHIP_CREATE_TABLE);
			ensureTable(con, CONVERSATION_TABLE_NAME, CONVERSATION_CREATE_TABLE);
			ensureTable(con, SALIENT_TABLE_NAME, SALIENT_CREATE_TABLE);
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Error creating fake-player chat persistence tables.", e);
		}
	}

	private void ensureTable(Connection con, String tableName, String createSql) throws SQLException
	{
		try (ResultSet result = con.getMetaData().getTables(null, null, tableName, null))
		{
			if (!result.next())
			{
				try (Statement statement = con.createStatement())
				{
					statement.executeUpdate(createSql);
					LOGGER.info("Missing '" + tableName + "' table was successfully created.");
				}
			}
		}
	}
	
	private String normalizeSpeakerKey(String speakerName)
	{
		return ((speakerName == null) || speakerName.isBlank()) ? "fake-player" : speakerName.toLowerCase();
	}
	
	private String normalizeTargetKey(String targetPlayerName)
	{
		return ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "unknown-target" : targetPlayerName.toLowerCase();
	}
	
	private String buildWhisperPairKey(String speakerName, String targetPlayerName)
	{
		return normalizeSpeakerKey(speakerName) + "->" + normalizeTargetKey(targetPlayerName);
	}

	private PairPersistenceKeys resolvePairPersistenceKeys(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return null;
		}

		final String speakerKey = normalizeSpeakerKey(speakerName);
		final String playerKey = normalizeTargetKey(targetPlayerName);
		return new PairPersistenceKeys(speakerKey + "->" + playerKey, speakerKey, playerKey);
	}

	private <T> T getOrLoadPairValue(String speakerName, String targetPlayerName, ConcurrentHashMap<String, T> storage, Set<String> loadedPairs, Supplier<T> factory, PairValueLoader<T> loader)
	{
		final PairPersistenceKeys keys = resolvePairPersistenceKeys(speakerName, targetPlayerName);
		if ((keys == null) || (storage == null) || (loadedPairs == null) || (factory == null) || (loader == null))
		{
			return null;
		}

		final T value = storage.computeIfAbsent(keys._pairKey, unused -> factory.get());
		if (loadedPairs.add(keys._pairKey))
		{
			loader.load(value, keys);
		}
		return value;
	}

	private <T> void removeCachedPairValue(ConcurrentHashMap<String, T> storage, Set<String> loadedPairs, PairPersistenceKeys keys, T value)
	{
		if ((storage == null) || (loadedPairs == null) || (keys == null))
		{
			return;
		}

		if (value == null)
		{
			storage.remove(keys._pairKey);
		}
		else
		{
			storage.remove(keys._pairKey, value);
		}
		loadedPairs.remove(keys._pairKey);
	}

	private <T> List<T> snapshotDeque(Deque<T> values)
	{
		if (values == null)
		{
			return List.of();
		}

		synchronized (values)
		{
			return values.isEmpty() ? List.of() : List.copyOf(values);
		}
	}

	private RelationshipState getOrLoadRelationshipState(String speakerName, String targetPlayerName)
	{
		return getOrLoadPairValue(speakerName, targetPlayerName, _relationshipStateByPair, _loadedRelationshipPairs, RelationshipState::new, (relationshipState, keys) -> loadRelationshipState(relationshipState, keys._speakerKey, keys._playerKey));
	}

	private Deque<ConversationTurn> getOrLoadRecentConversationTurns(String speakerName, String targetPlayerName)
	{
		return getOrLoadPairValue(speakerName, targetPlayerName, _recentConversationByPair, _loadedRecentConversationPairs, ArrayDeque::new, (turns, keys) -> loadRecentConversationTurns(turns, keys._speakerKey, keys._playerKey));
	}

	private Deque<SalientMemory> getOrLoadSalientMemories(String speakerName, String targetPlayerName)
	{
		return getOrLoadPairValue(speakerName, targetPlayerName, _salientMemoryByPair, _loadedSalientMemoryPairs, ArrayDeque::new, (memories, keys) -> loadSalientMemories(memories, keys._speakerKey, keys._playerKey));
	}

	private void loadRelationshipState(RelationshipState relationshipState, String speakerKey, String playerKey)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(RELATIONSHIP_LOAD))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return;
				}

				relationshipState._familiarity = result.getInt("familiarity");
				relationshipState._trust = result.getInt("trust_level");
				relationshipState._respect = result.getInt("respect_level");
				relationshipState._tension = result.getInt("tension");
				relationshipState._resentment = result.getInt("resentment");
				relationshipState._playerKillCount = result.getInt("player_kill_count");
				relationshipState._currentGoal = normalizePersistedText(result.getString("current_goal"));
				relationshipState._activeNeed = normalizePersistedText(result.getString("active_need"));
				relationshipState._lastImportantTopic = normalizePersistedText(result.getString("last_topic"));
				relationshipState._lastUpdatedMs = result.getLong("updated_at");
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not load relationship state for speakerKey=" + speakerKey + " playerKey=" + playerKey, e);
		}
	}

	private void loadRecentConversationTurns(Deque<ConversationTurn> turns, String speakerKey, String playerKey)
	{
		loadPairDeque(turns, CONVERSATION_LOAD, speakerKey, playerKey, Math.max(_replySettingsData.getSettings().getRecentConversationTurns(), MAX_RECENT_CONVERSATION_PERSIST), "recent conversation", result -> new ConversationTurn(result.getLong("created_at"), normalizeChannelLabel(result.getString("channel_name")), normalizePersistedText(result.getString("player_name")), normalizePersistedText(result.getString("player_line")), normalizePersistedText(result.getString("fakeplayer_line")), normalizePersistedText(result.getString("message_category")), normalizePersistedText(result.getString("knowledge_type")), normalizePersistedText(result.getString("topic"))));
	}

	private void loadSalientMemories(Deque<SalientMemory> memories, String speakerKey, String playerKey)
	{
		loadPairDeque(memories, SALIENT_LOAD, speakerKey, playerKey, MAX_SALIENT_MEMORIES, "selective memory", result -> new SalientMemory(result.getLong("created_at"), normalizePersistedText(result.getString("topic")), normalizePersistedText(result.getString("summary"))));
	}

	private void persistRelationshipState(String speakerName, String targetPlayerName, RelationshipState relationshipState)
	{
		final PairPersistenceKeys keys = resolvePairPersistenceKeys(speakerName, targetPlayerName);
		if (keys == null)
		{
			return;
		}
		if ((relationshipState == null) || !hasMeaningfulRelationshipState(relationshipState))
		{
			removeCachedPairValue(_relationshipStateByPair, _loadedRelationshipPairs, keys, relationshipState);
			deletePairRows(RELATIONSHIP_DELETE, keys._speakerKey, keys._playerKey, "relationship state");
			return;
		}

		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(RELATIONSHIP_UPSERT))
		{
			statement.setString(1, keys._speakerKey);
			statement.setString(2, keys._playerKey);
			statement.setInt(3, relationshipState._familiarity);
			statement.setInt(4, relationshipState._trust);
			statement.setInt(5, relationshipState._respect);
			statement.setInt(6, relationshipState._tension);
			statement.setInt(7, relationshipState._resentment);
			statement.setInt(8, relationshipState._playerKillCount);
			statement.setString(9, relationshipState._currentGoal);
			statement.setString(10, relationshipState._activeNeed);
			statement.setString(11, relationshipState._lastImportantTopic);
			statement.setLong(12, relationshipState._lastUpdatedMs);
			statement.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not persist relationship state for speakerKey=" + keys._speakerKey + " playerKey=" + keys._playerKey, e);
		}
	}

	private void persistRecentConversationTurns(String speakerName, String targetPlayerName, Deque<ConversationTurn> turns)
	{
		final PairPersistenceKeys keys = resolvePairPersistenceKeys(speakerName, targetPlayerName);
		if (keys == null)
		{
			return;
		}
		persistPairDeque(keys, turns, _recentConversationByPair, _loadedRecentConversationPairs, CONVERSATION_DELETE, "recent conversation", CONVERSATION_INSERT, this::snapshotDeque,
			(statement, pairKeys, turn) ->
			{
				statement.setString(1, pairKeys._speakerKey);
				statement.setString(2, pairKeys._playerKey);
				statement.setString(3, turn._channel);
				statement.setString(4, turn._playerName);
				statement.setString(5, turn._playerLine);
				statement.setString(6, turn._fakePlayerLine);
				statement.setString(7, turn._messageCategory);
				statement.setString(8, turn._knowledgeType);
				statement.setString(9, turn._topic);
				statement.setLong(10, turn._timestampMs);
			});
	}

	private void persistSalientMemories(String speakerName, String targetPlayerName, Deque<SalientMemory> memories)
	{
		final PairPersistenceKeys keys = resolvePairPersistenceKeys(speakerName, targetPlayerName);
		if (keys == null)
		{
			return;
		}
		persistPairDeque(keys, memories, _salientMemoryByPair, _loadedSalientMemoryPairs, SALIENT_DELETE, "selective memory", SALIENT_INSERT, this::snapshotDeque,
			(statement, pairKeys, memory) ->
			{
				statement.setString(1, pairKeys._speakerKey);
				statement.setString(2, pairKeys._playerKey);
				statement.setString(3, memory._topic);
				statement.setString(4, memory._summary);
				statement.setLong(5, memory._timestampMs);
			});
	}

	private <T> void loadPairDeque(Deque<T> values, String loadSql, String speakerKey, String playerKey, int limit, String label, PairResultMapper<T> mapper)
	{
		final List<T> loadedValues = new ArrayList<>(Math.max(limit, 1));
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(loadSql))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.setInt(3, Math.max(limit, 1));
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					loadedValues.add(mapper.map(result));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not load " + label + " for speakerKey=" + speakerKey + " playerKey=" + playerKey, e);
		}

		if (loadedValues.isEmpty())
		{
			return;
		}

		synchronized (values)
		{
			for (int i = loadedValues.size() - 1; i >= 0; i--)
			{
				values.addLast(loadedValues.get(i));
			}
		}
	}

	private <T> void persistPairDeque(PairPersistenceKeys keys, Deque<T> values, ConcurrentHashMap<String, Deque<T>> storage, Set<String> loadedPairs, String deleteSql, String deleteLabel, String insertSql, Function<Deque<T>, List<T>> snapshotter, PairBatchBinder<T> binder)
	{
		if ((keys == null) || (storage == null) || (loadedPairs == null) || (deleteSql == null) || (deleteLabel == null) || (insertSql == null) || (snapshotter == null) || (binder == null))
		{
			return;
		}

		final List<T> snapshot = snapshotter.apply(values);
		if (snapshot.isEmpty())
		{
			removeCachedPairValue(storage, loadedPairs, keys, values);
			deletePairRows(deleteSql, keys._speakerKey, keys._playerKey, deleteLabel);
			return;
		}

		try (Connection con = DatabaseFactory.getConnection())
		{
			deletePairRows(con, deleteSql, keys._speakerKey, keys._playerKey);
			try (PreparedStatement insert = con.prepareStatement(insertSql))
			{
				for (T value : snapshot)
				{
					binder.bind(insert, keys, value);
					insert.addBatch();
				}
				insert.executeBatch();
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not persist " + deleteLabel + " for speakerKey=" + keys._speakerKey + " playerKey=" + keys._playerKey, e);
		}
	}

	private void deletePairRows(String sql, String speakerKey, String playerKey, String label)
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			deletePairRows(con, sql, speakerKey, playerKey);
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not clear " + label + " for speakerKey=" + speakerKey + " playerKey=" + playerKey, e);
		}
	}

	private void deletePairRows(Connection con, String sql, String speakerKey, String playerKey) throws SQLException
	{
		try (PreparedStatement statement = con.prepareStatement(sql))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.executeUpdate();
		}
	}

	private String normalizePersistedText(String value)
	{
		return (value == null) ? "" : value;
	}
	
	private boolean isCooldownReady(ConcurrentHashMap<String, Long> storage, String key)
	{
		return isCooldownReady(storage, key, _config.getGlobalChatCooldownMs());
	}

	private boolean isCooldownReady(ConcurrentHashMap<String, Long> storage, String key, long cooldownMs)
	{
		final long cooldown = Math.max(cooldownMs, 0L);
		if (cooldown == 0L)
		{
			return true;
		}
		
		final long now = System.currentTimeMillis();
		final Long last = storage.get(key);
		return (last == null) || ((now - last.longValue()) >= cooldown);
	}
	
	private void markCooldownUsed(ConcurrentHashMap<String, Long> storage, String key)
	{
		storage.put(key, System.currentTimeMillis());
	}
	
	/**
	 * Legacy compatibility method used by the runtime snapshot path.
	 */
	public boolean isChatCooldownReady(String speakerName)
	{
		return isGeneralChatCooldownReady(speakerName);
	}
	
	public boolean isGeneralChatCooldownReady(String speakerName)
	{
		return isCooldownReady(_lastGeneralChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}
	
	public boolean isPublicChatCooldownReady(String speakerName)
	{
		return isCooldownReady(_lastPublicChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}

	public boolean isClanChatCooldownReady(String speakerName)
	{
		return isCooldownReady(_lastClanChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}

	public boolean isPartyChatCooldownReady(String speakerName)
	{
		return isCooldownReady(_lastPartyChatTimeBySpeaker, normalizeSpeakerKey(speakerName), _config.getPartyReplyCooldownMs());
	}
	
	public boolean isWhisperChatCooldownReady(String speakerName, String targetPlayerName)
	{
		return isCooldownReady(_lastWhisperChatTimeByTarget, buildWhisperPairKey(speakerName, targetPlayerName), _config.getWhisperReplyCooldownMs());
	}
	
	private void markGeneralChatUsed(String speakerName)
	{
		markCooldownUsed(_lastGeneralChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}
	
	private void markPublicChatUsed(String speakerName)
	{
		markCooldownUsed(_lastPublicChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}

	private void markClanChatUsed(String speakerName)
	{
		markCooldownUsed(_lastClanChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}

	private void markPartyChatUsed(String speakerName)
	{
		markCooldownUsed(_lastPartyChatTimeBySpeaker, normalizeSpeakerKey(speakerName));
	}
	
	private void markWhisperChatUsed(String speakerName, String targetPlayerName)
	{
		markCooldownUsed(_lastWhisperChatTimeByTarget, buildWhisperPairKey(speakerName, targetPlayerName));
	}

	public void rememberConversationTurn(String speakerName, String targetPlayerName, String channel, String incomingPlayerLine, String replyLine)
	{
		final String cleanedIncoming = trimConversationText(incomingPlayerLine, 96);
		final FpcRouteProfile routeProfile = classifyConversationRouteProfile(speakerName, targetPlayerName, cleanedIncoming);
		final String messageCategory = routeProfile.getMessageCategory();
		final String knowledgeType = routeProfile.getKnowledgeType();
		final String topic = deriveTopic(speakerName, targetPlayerName, messageCategory, knowledgeType, cleanedIncoming);
		rememberConversationTurnInternal(speakerName, targetPlayerName, channel, incomingPlayerLine, replyLine, messageCategory, knowledgeType, topic);
	}

	public void rememberGuardedExchange(String speakerName, String targetPlayerName, String channel, String incomingPlayerLine, String replyLine, FpcConversationGuardAssessment assessment)
	{
		if (assessment == null)
		{
			rememberConversationTurn(speakerName, targetPlayerName, channel, incomingPlayerLine, replyLine);
			return;
		}
		rememberConversationTurnInternal(speakerName, targetPlayerName, channel, incomingPlayerLine, replyLine, assessment.getCategory(), assessment.getHistoryKnowledgeType(), assessment.getHistoryTopic());
	}

	private void rememberConversationTurnInternal(String speakerName, String targetPlayerName, String channel, String incomingPlayerLine, String replyLine, String messageCategory, String knowledgeType, String topic)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final String cleanedIncoming = trimConversationText(incomingPlayerLine, 96);
		final String cleanedReply = trimConversationText(replyLine, 96);
		if (cleanedIncoming.isBlank() || cleanedReply.isBlank())
		{
			return;
		}

		final long now = System.currentTimeMillis();
		final FpcReplySettings settings = _replySettingsData.getSettings();
		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		final String effectiveCategory = ((messageCategory == null) || messageCategory.isBlank()) ? "unknown" : messageCategory;
		final String effectiveKnowledgeType = ((knowledgeType == null) || knowledgeType.isBlank()) ? "unknown" : knowledgeType;
		final String effectiveTopic = ((topic == null) || topic.isBlank()) ? effectiveCategory : topic;
		final Deque<ConversationTurn> turns = getOrLoadRecentConversationTurns(speakerName, targetPlayerName);
		if (turns == null)
		{
			return;
		}
		synchronized (turns)
		{
			pruneExpiredConversation(turns, now);
			while (turns.size() >= Math.max(settings.getRecentConversationTurns(), 1))
			{
				turns.removeFirst();
			}
			turns.addLast(new ConversationTurn(now, normalizeChannelLabel(channel), targetPlayerName.trim(), cleanedIncoming, cleanedReply, effectiveCategory, effectiveKnowledgeType, effectiveTopic));
		}
		persistRecentConversationTurns(speakerName, targetPlayerName, turns);
	}

	public void rememberSuccessfulExchange(String speakerName, String targetPlayerName, String channel, String incomingPlayerLine, String replyLine)
	{
		rememberConversationTurn(speakerName, targetPlayerName, channel, incomingPlayerLine, replyLine);
		promoteSelectiveMemory(speakerName, targetPlayerName, incomingPlayerLine, replyLine);
	}

	public FpcRelationshipSnapshot getRelationshipSnapshot(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return FpcRelationshipSnapshot.EMPTY;
		}

		final String pairKey = buildWhisperPairKey(speakerName, targetPlayerName);
		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState == null)
		{
			return FpcRelationshipSnapshot.EMPTY;
		}
		final boolean decayed = applyRelationshipDecay(relationshipState, System.currentTimeMillis());
		if (!hasMeaningfulRelationshipState(relationshipState))
		{
			_relationshipStateByPair.remove(pairKey, relationshipState);
			_loadedRelationshipPairs.remove(pairKey);
			deletePairRows(RELATIONSHIP_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "relationship state");
			return FpcRelationshipSnapshot.EMPTY;
		}
		if (decayed)
		{
			persistRelationshipState(speakerName, targetPlayerName, relationshipState);
		}

		return new FpcRelationshipSnapshot(relationshipState._familiarity, relationshipState._trust, relationshipState._respect, relationshipState._tension, relationshipState._resentment, relationshipState._playerKillCount, relationshipState._currentGoal, relationshipState._activeNeed, relationshipState._lastImportantTopic);
	}

	public FpcRelationshipSnapshot overrideRelationshipPreset(String speakerName, String targetPlayerName, String presetName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return FpcRelationshipSnapshot.EMPTY;
		}

		final String normalizedPreset = (presetName == null) ? "" : presetName.trim().toLowerCase(Locale.ENGLISH);
		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		if (normalizedPreset.isBlank() || "neutral".equals(normalizedPreset) || "reset".equals(normalizedPreset) || "clear".equals(normalizedPreset))
		{
			_relationshipStateByPair.remove(key);
			_loadedRelationshipPairs.remove(key);
			deletePairRows(RELATIONSHIP_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "relationship state");
			return FpcRelationshipSnapshot.EMPTY;
		}

		final RelationshipState relationshipState = buildPresetState(normalizedPreset);
		relationshipState._lastUpdatedMs = System.currentTimeMillis();
		_relationshipStateByPair.put(key, relationshipState);
		_loadedRelationshipPairs.add(key);
		persistRelationshipState(speakerName, targetPlayerName, relationshipState);
		return new FpcRelationshipSnapshot(relationshipState._familiarity, relationshipState._trust, relationshipState._respect, relationshipState._tension, relationshipState._resentment, relationshipState._playerKillCount, relationshipState._currentGoal, relationshipState._activeNeed, relationshipState._lastImportantTopic);
	}

	public void clearPairMemory(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final String pairKey = buildWhisperPairKey(speakerName, targetPlayerName);
		_relationshipStateByPair.remove(pairKey);
		_recentConversationByPair.remove(pairKey);
		_salientMemoryByPair.remove(pairKey);
		_loadedRelationshipPairs.remove(pairKey);
		_loadedRecentConversationPairs.remove(pairKey);
		_loadedSalientMemoryPairs.remove(pairKey);
		final String speakerKey = normalizeSpeakerKey(speakerName);
		final String playerKey = normalizeTargetKey(targetPlayerName);
		deletePairRows(RELATIONSHIP_DELETE, speakerKey, playerKey, "relationship state");
		deletePairRows(CONVERSATION_DELETE, speakerKey, playerKey, "recent conversation");
		deletePairRows(SALIENT_DELETE, speakerKey, playerKey, "selective memory");
	}

	public void resetRelationshipWarmth(String speakerName, String targetPlayerName, String topic, int tensionFloor, int resentmentFloor)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState == null)
		{
			return;
		}
		applyRelationshipDecay(relationshipState, System.currentTimeMillis());
		relationshipState._lastUpdatedMs = System.currentTimeMillis();
		relationshipState._familiarity = 0;
		relationshipState._trust = 0;
		relationshipState._respect = 0;
		relationshipState._tension = clampRelationship(Math.max(relationshipState._tension, tensionFloor));
		relationshipState._resentment = clampRelationship(Math.max(relationshipState._resentment, resentmentFloor));
		relationshipState._currentGoal = "keep distance from clan promises that broke";
		relationshipState._activeNeed = "steady loyalty";
		relationshipState._lastImportantTopic = normalizeTopicToken(topic);
		persistRelationshipState(speakerName, targetPlayerName, relationshipState);
	}

	public void recordHostilePlayerAction(String speakerName, String targetPlayerName, String reason)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState == null)
		{
			return;
		}
		applyRelationshipDecay(relationshipState, System.currentTimeMillis());
		relationshipState._lastUpdatedMs = System.currentTimeMillis();
		final String normalizedReason = normalizeHostileTopic(reason);
		int tensionDelta = 4;
		int resentmentDelta = 5;
		int trustDelta = -4;
		int respectDelta = -2;
		String currentGoal = "";
		String activeNeed = "";
		if ("insulted_me".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 3;
			resentmentDelta = 3;
			trustDelta = -2;
			respectDelta = -3;
			currentGoal = "hold the line against disrespect";
			activeNeed = "basic respect";
		}
		else if ("attacked_beliefs".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 3;
			resentmentDelta = 4;
			trustDelta = -2;
			respectDelta = -2;
			currentGoal = "defend what matters without overexposing it";
			activeNeed = "protect the bond";
		}
		else if ("attacked_me".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 5;
			resentmentDelta = 4;
			trustDelta = -5;
			respectDelta = -3;
			currentGoal = "answer the strike without forgetting it";
			activeNeed = "immediate safety";
		}
		else if ("witnessed_pk".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 2;
			resentmentDelta = 3;
			trustDelta = -3;
			respectDelta = -2;
			currentGoal = "treat that player like a nearby killer";
			activeNeed = "distance from bloodshed";
		}
		else if ("killed_me".equalsIgnoreCase(normalizedReason))
		{
			currentGoal = "avoid that player's lead";
			activeNeed = "self-preservation";
		}
		else if ("expressed_hate".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 3;
			resentmentDelta = 4;
			trustDelta = -3;
			respectDelta = -2;
			currentGoal = "keep distance until words improve";
			activeNeed = "basic sincerity";
		}
		else if ("distrusted_me".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 2;
			resentmentDelta = 2;
			trustDelta = -4;
			respectDelta = -1;
			currentGoal = "decide whether honesty is possible";
			activeNeed = "clear intent";
		}
		else if ("threatened_me".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 5;
			resentmentDelta = 5;
			trustDelta = -5;
			respectDelta = -3;
			currentGoal = "treat the threat as real";
			activeNeed = "immediate safety";
		}
		else if ("abandoned_me".equalsIgnoreCase(normalizedReason))
		{
			tensionDelta = 2;
			resentmentDelta = 4;
			trustDelta = -4;
			respectDelta = -1;
			currentGoal = "brace for another absence";
			activeNeed = "constancy";
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity - 1);
		}

		relationshipState._tension = clampRelationship(relationshipState._tension + tensionDelta);
		relationshipState._resentment = clampRelationship(relationshipState._resentment + resentmentDelta);
		relationshipState._trust = clampRelationship(relationshipState._trust + trustDelta);
		relationshipState._respect = clampRelationship(relationshipState._respect + respectDelta);
		if ("killed_me".equalsIgnoreCase(normalizedReason))
		{
			relationshipState._playerKillCount++;
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 2);
		}
		if (!currentGoal.isBlank())
		{
			relationshipState._currentGoal = currentGoal;
		}
		if (!activeNeed.isBlank())
		{
			relationshipState._activeNeed = activeNeed;
		}
		relationshipState._lastImportantTopic = normalizedReason;
		persistRelationshipState(speakerName, targetPlayerName, relationshipState);
	}

	public void recordPositivePlayerAction(String speakerName, String targetPlayerName, String reason, String focusEntityName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState == null)
		{
			return;
		}
		applyRelationshipDecay(relationshipState, System.currentTimeMillis());
		relationshipState._lastUpdatedMs = System.currentTimeMillis();
		final String normalizedReason = normalizeHostileTopic(reason);
		if ("affirmed_belief".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 1, 1);
			final int trustGain = warmthGain(relationshipState, 2, 1);
			final int respectGain = warmthGain(relationshipState, 2, 1);
			final int tensionRelief = repairRelief(relationshipState, 0, 1);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = severeRepairResistance(relationshipState) ? "watch whether the loyalty survives pressure" : "see whether that respect is real";
			relationshipState._activeNeed = "guarded hope";
			relationshipState._lastImportantTopic = "affirmed_" + normalizeTopicToken(focusEntityName);
		}
		else if ("apologized".equalsIgnoreCase(normalizedReason))
		{
			final int trustGain = repairGain(relationshipState, 1, 1);
			final int respectGain = Math.max(1, repairGain(relationshipState, 1, 0));
			final int tensionRelief = repairRelief(relationshipState, 1, 2);
			final int resentmentRelief = repairRelief(relationshipState, 1, 2);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = severeRepairResistance(relationshipState) ? "watch for proof beyond words" : "see whether the apology holds";
			relationshipState._activeNeed = "consistent change";
			relationshipState._lastImportantTopic = "apology";
		}
		else if ("thanked_me".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int trustGain = warmthGain(relationshipState, 1, 0);
			final int respectGain = warmthGain(relationshipState, 1, 0);
			final int tensionRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._lastImportantTopic = "gratitude";
		}
		else if ("respected_me".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 1, 1);
			final int trustGain = warmthGain(relationshipState, 2, 1);
			final int respectGain = warmthGain(relationshipState, 3, 1);
			final int tensionRelief = repairRelief(relationshipState, 0, 1);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = severeRepairResistance(relationshipState) ? "see whether the respect becomes steady conduct" : "decide whether to lower the guard";
			relationshipState._activeNeed = "sincere respect";
			relationshipState._lastImportantTopic = "respect";
		}
		else if ("expressed_affection".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 2, 1);
			final int trustGain = warmthGain(relationshipState, 1, 1);
			final int respectGain = warmthGain(relationshipState, 1, 0);
			final int tensionRelief = repairRelief(relationshipState, 0, 1);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = severeRepairResistance(relationshipState) ? "shield the warmth until it proves steady" : "protect the fragile warmth";
			relationshipState._activeNeed = "emotional safety";
			relationshipState._lastImportantTopic = "affection";
		}
		else if ("sought_repair".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int trustGain = repairGain(relationshipState, 1, 1);
			final int respectGain = Math.max(1, repairGain(relationshipState, 1, 0));
			final int tensionRelief = repairRelief(relationshipState, 1, 2);
			final int resentmentRelief = repairRelief(relationshipState, 1, 2);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = "test the repair slowly";
			relationshipState._activeNeed = "steady proof";
			relationshipState._lastImportantTopic = "repair";
		}
		else if ("praised_me".equalsIgnoreCase(normalizedReason))
		{
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int trustGain = warmthGain(relationshipState, 1, 0);
			final int respectGain = warmthGain(relationshipState, 2, 1);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = "see whether the praise has weight";
			relationshipState._activeNeed = "earned sincerity";
			relationshipState._lastImportantTopic = "praise";
		}
		else if ("reassured_me".equalsIgnoreCase(normalizedReason))
		{
			final String currentTopic = normalizeTopicToken(relationshipState._lastImportantTopic);
			final boolean constancyWound = currentTopic.contains("abandon") || normalizeTopicToken(relationshipState._activeNeed).contains("constancy") || normalizeTopicToken(relationshipState._activeNeed).contains("presence");
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int trustGain = repairGain(relationshipState, constancyWound ? 2 : 1, 1);
			final int respectGain = Math.max(1, repairGain(relationshipState, 1, 0));
			final int tensionRelief = repairRelief(relationshipState, 1, 2);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = "see whether the promise holds";
			relationshipState._activeNeed = "steady presence";
			relationshipState._lastImportantTopic = "reassurance";
		}
		persistRelationshipState(speakerName, targetPlayerName, relationshipState);
	}

	public String buildConversationAwareText(String speakerName, String targetPlayerName, String incomingText, String followUpStrictness)
	{
		final String normalizedIncoming = trimConversationText(incomingText, 180);
		if (normalizedIncoming.isBlank())
		{
			return "";
		}

		final ConversationTurn latestTurn = getLatestConversationTurnInternal(speakerName, targetPlayerName);
		if ((latestTurn == null) || FpcConversationGuardAssessment.isSensitiveCategory(latestTurn._messageCategory))
		{
			return normalizedIncoming;
		}
		if (!shouldCarryConversation(normalizedIncoming, followUpStrictness, latestTurn))
		{
			return normalizedIncoming;
		}

		final String selectiveMemory = describeSelectiveMemory(speakerName, targetPlayerName);
		final String previousCategory = latestTurn._messageCategory.isBlank() ? "unknown" : latestTurn._messageCategory;
		final String previousKnowledgeType = latestTurn._knowledgeType.isBlank() ? "unknown" : latestTurn._knowledgeType;
		final String previousTopic = latestTurn._topic.isBlank() ? previousKnowledgeType : latestTurn._topic;
		final String memoryTail = selectiveMemory.isBlank() ? "" : " Longer memory: " + selectiveMemory + ".";
		return trimConversationText(normalizedIncoming + " Previous category: " + previousCategory + ". Previous knowledge topic: " + previousKnowledgeType + ". Previous focus: " + previousTopic + ". Previous player topic: " + latestTurn._playerLine + ". Previous reply: " + latestTurn._fakePlayerLine + "." + memoryTail, 420);
	}

	public String describeRecentConversation(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return "";
		}

		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		final Deque<ConversationTurn> turns = getOrLoadRecentConversationTurns(speakerName, targetPlayerName);
		if (turns == null)
		{
			return "";
		}

		final long now = System.currentTimeMillis();
		synchronized (turns)
		{
			final boolean changed = pruneExpiredConversation(turns, now);
			if (turns.isEmpty())
			{
				_recentConversationByPair.remove(key, turns);
				_loadedRecentConversationPairs.remove(key);
				deletePairRows(CONVERSATION_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "recent conversation");
				return "";
			}
			if (changed)
			{
				persistRecentConversationTurns(speakerName, targetPlayerName, turns);
			}

			final String speakerLabel = speakerName.trim();
			final StringBuilder sb = new StringBuilder();
			sb.append("Recent exchange history with ").append(targetPlayerName.trim()).append(": ");
			boolean first = true;
			for (ConversationTurn turn : turns)
			{
				if (!first)
				{
					sb.append(' ');
				}
				first = false;
				sb.append('[').append(turn._channel).append("] ").append(turn._playerName).append(" said \"").append(turn._playerLine).append("\". ");
				sb.append(speakerLabel).append(" answered \"").append(turn._fakePlayerLine).append("\".");
			}
			return trimConversationText(sb.toString(), 420);
		}
	}

	public long getLatestConversationTimestampMs(String speakerName, String targetPlayerName)
	{
		final ConversationTurn latestTurn = getLatestConversationTurnInternal(speakerName, targetPlayerName);
		return (latestTurn == null) ? 0L : latestTurn._timestampMs;
	}

	public String describeSelectiveMemory(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return "";
		}

		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		final long now = System.currentTimeMillis();
		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState != null)
		{
			final boolean decayed = applyRelationshipDecay(relationshipState, now);
			if (!hasMeaningfulRelationshipState(relationshipState))
			{
				_relationshipStateByPair.remove(key, relationshipState);
				_loadedRelationshipPairs.remove(key);
				deletePairRows(RELATIONSHIP_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "relationship state");
			}
			else if (decayed)
			{
				persistRelationshipState(speakerName, targetPlayerName, relationshipState);
			}
		}
		final Deque<SalientMemory> memories = getOrLoadSalientMemories(speakerName, targetPlayerName);
		String recentMemory = "";
		if (memories != null)
		{
			synchronized (memories)
			{
				final boolean changed = pruneExpiredSalientMemory(memories, now);
				if (memories.isEmpty())
				{
					_salientMemoryByPair.remove(key, memories);
					_loadedSalientMemoryPairs.remove(key);
					deletePairRows(SALIENT_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "selective memory");
				}
				else
				{
					if (changed)
					{
						persistSalientMemories(speakerName, targetPlayerName, memories);
					}
					final SalientMemory latest = memories.peekLast();
					if (latest != null)
					{
						recentMemory = latest._summary;
					}
				}
			}
		}

		if ((relationshipState == null) && recentMemory.isBlank())
		{
			return "";
		}

		final StringBuilder sb = new StringBuilder();
		if (relationshipState != null)
		{
			sb.append("Longer memory with ").append(targetPlayerName.trim()).append(": ");
			sb.append("familiarity=").append(describeLevel(relationshipState._familiarity)).append(", ");
			sb.append("trust=").append(describeLevel(relationshipState._trust)).append(", ");
			sb.append("respect=").append(describeLevel(relationshipState._respect)).append(", ");
			sb.append("tension=").append(describeTension(relationshipState._tension)).append(".");
			if (relationshipState._resentment > 0)
			{
				sb.append(" Resentment=").append(describeTension(relationshipState._resentment)).append(".");
			}
			if (relationshipState._playerKillCount > 0)
			{
				sb.append(" This addressee has killed the FPC ").append(relationshipState._playerKillCount).append(" time(s).");
			}
			if (!relationshipState._currentGoal.isBlank())
			{
				sb.append(" Current goal: ").append(relationshipState._currentGoal).append(".");
			}
			if (!relationshipState._activeNeed.isBlank())
			{
				sb.append(" Active need: ").append(relationshipState._activeNeed).append(".");
			}
		}
		if (!recentMemory.isBlank())
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append("Recent notable memory: ").append(recentMemory);
		}
		return trimConversationText(sb.toString(), 360);
	}
	
	public List<String> getAvailableLineStyleTags()
	{
		return List.of("none", "casual_short", "boast_short", "dry_short", "warning_short", "friendly_short");
	}
	
	public List<String> getAvailableTopicTags()
	{
		return List.of("none", "grind", "loot", "zone", "danger", "brag", "smalltalk");
	}
	
	public String selectCannedLine(String lineStyleTag, String topicTag)
	{
		if ((lineStyleTag == null) || (topicTag == null) || "none".equals(lineStyleTag) || "none".equals(topicTag))
		{
			return null;
		}
		
		final String exact = STYLE_TOPIC_LINES.get(lineStyleTag + "|" + topicTag);
		if (exact != null)
		{
			return exact;
		}
		
		for (Map.Entry<String, String> entry : STYLE_TOPIC_LINES.entrySet())
		{
			if (entry.getKey().startsWith(lineStyleTag + "|"))
			{
				return entry.getValue();
			}
		}
		
		return null;
	}
	
	private ResolvedSpeakerRuntime resolveSpeakerRuntime(String speakerName)
	{
		final FpcDefinition definition = resolveSpeakerDefinition(speakerName);
		if (definition == null)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Refusing chat egress for non-registry speaker=" + speakerName);
			return null;
		}

		final Npc activeNpc = resolveRegistrySpeakerNpc(definition);
		if (activeNpc == null)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Dropping chat egress because active registry NPC could not be resolved for speaker=" + speakerName + " id=" + definition.getId());
			return null;
		}
		return new ResolvedSpeakerRuntime(definition, activeNpc, definition.getName());
	}

	private FpcDefinition resolveSpeakerDefinition(String speakerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (_registry == null))
		{
			return null;
		}

		FpcDefinition definition = _registry.getDefinitionById(speakerName);
		if (definition == null)
		{
			definition = _registry.getDefinitionByName(speakerName);
		}
		if (definition == null)
		{
			final String properName = FakePlayerData.getInstance().getProperName(speakerName);
			if ((properName != null) && !properName.isBlank())
			{
				definition = _registry.getDefinitionByName(properName);
				if (definition == null)
				{
					definition = _registry.getDefinitionById(properName);
				}
			}
		}
		return definition;
	}

	private Npc resolveRegistrySpeakerNpc(FpcDefinition definition)
	{
		if ((definition == null) || (_registry == null))
		{
			return null;
		}
		return _registry.resolveActiveNpc(definition.getId());
	}

	private String normalizeSpeakerLookupName(String speakerName)
	{
		if ((speakerName == null) || speakerName.isBlank())
		{
			return "fake-player";
		}

		final FpcDefinition definition = resolveSpeakerDefinition(speakerName);
		if (definition != null)
		{
			return definition.getName();
		}

		final String properName = FakePlayerData.getInstance().getProperName(speakerName);
		return ((properName != null) && !properName.isBlank()) ? properName : speakerName;
	}

	private boolean hasSpeakNowLine(FakePlayerAdvisoryPlan advisoryPlan)
	{
		return (advisoryPlan != null) && advisoryPlan.isSpeakNow() && (advisoryPlan.getSelectedLine() != null) && !advisoryPlan.getSelectedLine().isBlank();
	}

	private String resolveSpeakerNameForLogs(String speakerLookupName)
	{
		final FpcDefinition speakerDefinition = resolveSpeakerDefinition(speakerLookupName);
		return (speakerDefinition != null) ? speakerDefinition.getName() : normalizeSpeakerLookupName(speakerLookupName);
	}

	private ResolvedSpeakerRuntime prepareSpeakerRuntimeForEmit(String speakerLookupName, BooleanSupplier cooldownReady, Supplier<String> cooldownSkipMessage, Supplier<String> unresolvedMessage)
	{
		if ((cooldownReady != null) && !cooldownReady.getAsBoolean())
		{
			if (cooldownSkipMessage != null)
			{
				LOGGER.info(cooldownSkipMessage);
			}
			return null;
		}

		final ResolvedSpeakerRuntime speakerRuntime = resolveSpeakerRuntime(speakerLookupName);
		if (speakerRuntime == null)
		{
			if (unresolvedMessage != null)
			{
				LOGGER.warning(unresolvedMessage.get());
			}
			return null;
		}
		return speakerRuntime;
	}

	private boolean emitResolvedLine(String speakerLookupName, BooleanSupplier cooldownReady, Supplier<String> cooldownSkipMessage, Supplier<String> unresolvedMessage, Consumer<ResolvedSpeakerRuntime> emitAction, Consumer<ResolvedSpeakerRuntime> cooldownMarker, Function<ResolvedSpeakerRuntime, String> successMessage, Supplier<String> failureMessage)
	{
		final ResolvedSpeakerRuntime speakerRuntime = prepareSpeakerRuntimeForEmit(speakerLookupName, cooldownReady, cooldownSkipMessage, unresolvedMessage);
		if (speakerRuntime == null)
		{
			return false;
		}

		try
		{
			emitAction.accept(speakerRuntime);
			if (cooldownMarker != null)
			{
				cooldownMarker.accept(speakerRuntime);
			}
			if (successMessage != null)
			{
				LOGGER.info(() -> successMessage.apply(speakerRuntime));
			}
			return true;
		}
		catch (Exception e)
		{
			final String prefix = (failureMessage != null) ? failureMessage.get() : (getClass().getSimpleName() + ": Failed fake-player chat emit for speaker=" + speakerRuntime._displayName);
			LOGGER.warning(prefix + " -> " + e.getMessage());
			return false;
		}
	}
	
	public boolean tryEmitVisibleLine(FakePlayerAdvisoryPlan advisoryPlan)
	{
		if (!hasSpeakNowLine(advisoryPlan))
		{
			return false;
		}

		final String selectedLine = advisoryPlan.getSelectedLine();
		final String speakerName = resolveSpeakerNameForLogs(advisoryPlan.getSpeakerName());
		return emitResolvedLine(advisoryPlan.getSpeakerName(), () -> isGeneralChatCooldownReady(speakerName), () -> getClass().getSimpleName() + ": Skipping visible line due to per-speaker general chat cooldown. speaker=" + speakerName, () -> getClass().getSimpleName() + ": Visible fake-player line dropped because active registry speaker runtime could not be resolved. speaker=" + speakerName + " text=" + selectedLine, speakerRuntime -> _chatTransport.broadcastNearbyReply(speakerRuntime._npc, speakerRuntime._displayName, ChatType.GENERAL, selectedLine), speakerRuntime -> markGeneralChatUsed(speakerRuntime._displayName), speakerRuntime -> getClass().getSimpleName() + ": Emitted nearby fake-player line through active NPC: speaker=" + speakerRuntime._displayName + " id=" + speakerRuntime._definition.getId() + " objId=" + speakerRuntime._npc.getObjectId() + " text=" + selectedLine, () -> getClass().getSimpleName() + ": Failed visible fake-player path for speaker=" + speakerName);
	}
	
	public boolean tryEmitPublicLine(FakePlayerAdvisoryPlan advisoryPlan, boolean worldChannel)
	{
		if (!hasSpeakNowLine(advisoryPlan))
		{
			return false;
		}
		
		final String selectedLine = advisoryPlan.getSelectedLine();
		final String speakerName = resolveSpeakerNameForLogs(advisoryPlan.getSpeakerName());
		final ChatType chatType = worldChannel ? ChatType.WORLD : ChatType.SHOUT;
		return emitResolvedLine(advisoryPlan.getSpeakerName(), () -> isPublicChatCooldownReady(speakerName), () -> getClass().getSimpleName() + ": Skipping public fake-player line due to per-speaker public chat cooldown. speaker=" + speakerName, () -> getClass().getSimpleName() + ": Public fake-player line dropped because active registry speaker runtime could not be resolved. speaker=" + speakerName + " channel=" + chatType + " text=" + selectedLine, speakerRuntime -> _chatTransport.broadcastPublicReply(speakerRuntime._npc, speakerRuntime._displayName, chatType, selectedLine), speakerRuntime -> markPublicChatUsed(speakerRuntime._displayName), speakerRuntime -> getClass().getSimpleName() + ": Emitted public fake-player line through active NPC: channel=" + chatType + " speaker=" + speakerRuntime._displayName + " id=" + speakerRuntime._definition.getId() + " objId=" + speakerRuntime._npc.getObjectId() + " text=" + selectedLine, () -> getClass().getSimpleName() + ": Failed public fake-player path for speaker=" + speakerName + " channel=" + chatType);
	}
	
	public boolean trySendPrivateLine(Player targetPlayer, FakePlayerAdvisoryPlan advisoryPlan)
	{
		if ((targetPlayer == null) || !hasSpeakNowLine(advisoryPlan))
		{
			return false;
		}

		return trySendDirectPrivateLine(targetPlayer, advisoryPlan.getSpeakerName(), advisoryPlan.getSelectedLine(), shouldShareLocation(advisoryPlan));
	}

	public boolean trySendDirectPrivateLine(Player targetPlayer, String speakerLookupName, String selectedLine)
	{
		return trySendDirectPrivateLine(targetPlayer, speakerLookupName, selectedLine, false);
	}

	public boolean trySendDirectPrivateLine(Player targetPlayer, String speakerLookupName, String selectedLine, boolean shareLocation)
	{
		if ((targetPlayer == null) || (speakerLookupName == null) || speakerLookupName.isBlank() || (selectedLine == null) || selectedLine.isBlank())
		{
			return false;
		}

		final String speakerName = resolveSpeakerNameForLogs(speakerLookupName);
		final String targetPlayerName = targetPlayer.getName();
		return emitResolvedLine(speakerLookupName, () -> isWhisperChatCooldownReady(speakerName, targetPlayerName), () -> getClass().getSimpleName() + ": Skipping private fake-player whisper due to per-target whisper cooldown. speaker=" + speakerName + " target=" + targetPlayerName, () -> getClass().getSimpleName() + ": Private fake-player whisper dropped because active registry speaker runtime could not be resolved. speaker=" + speakerName + " target=" + targetPlayerName + " text=" + selectedLine, speakerRuntime -> _chatTransport.sendPrivateReply(targetPlayer, speakerRuntime._npc, speakerRuntime._displayName, selectedLine, shareLocation), speakerRuntime -> markWhisperChatUsed(speakerRuntime._displayName, targetPlayerName), speakerRuntime -> getClass().getSimpleName() + ": Emitted AI whisper reply through active NPC: speaker=" + speakerRuntime._displayName + " id=" + speakerRuntime._definition.getId() + " objId=" + speakerRuntime._npc.getObjectId() + " target=" + targetPlayerName + " shareLocation=" + shareLocation + " text=" + selectedLine, () -> getClass().getSimpleName() + ": Failed private fake-player whisper path for speaker=" + speakerName);
	}

	public boolean tryEmitClanLine(Clan clan, FakePlayerAdvisoryPlan advisoryPlan)
	{
		if ((clan == null) || !hasSpeakNowLine(advisoryPlan))
		{
			return false;
		}

		final String selectedLine = advisoryPlan.getSelectedLine();
		final String speakerName = resolveSpeakerNameForLogs(advisoryPlan.getSpeakerName());
		return emitResolvedLine(advisoryPlan.getSpeakerName(), () -> isClanChatCooldownReady(speakerName), () -> getClass().getSimpleName() + ": Skipping clan fake-player line due to per-speaker clan chat cooldown. speaker=" + speakerName + " clan=" + clan.getName(), () -> getClass().getSimpleName() + ": Clan fake-player line dropped because active registry speaker runtime could not be resolved. speaker=" + speakerName + " clan=" + clan.getName() + " text=" + selectedLine, speakerRuntime ->
		{
			final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
			final int speakerClanId = (hybridClanService == null) ? 0 : hybridClanService.getHybridClanId(speakerRuntime._definition.getId());
			if (speakerClanId != clan.getId())
			{
				throw new IllegalStateException("speaker clan overlay does not match target clan. speaker=" + speakerRuntime._displayName + " expectedClan=" + clan.getId() + " actualClan=" + speakerClanId);
			}
			_chatTransport.broadcastClanReply(clan, speakerRuntime._npc, speakerRuntime._displayName, selectedLine);
		}, speakerRuntime -> markClanChatUsed(speakerRuntime._displayName), speakerRuntime -> getClass().getSimpleName() + ": Emitted clan fake-player line through active NPC: clan=" + clan.getName() + " speaker=" + speakerRuntime._displayName + " id=" + speakerRuntime._definition.getId() + " objId=" + speakerRuntime._npc.getObjectId() + " text=" + selectedLine, () -> getClass().getSimpleName() + ": Failed clan fake-player path for speaker=" + speakerName + " clan=" + clan.getName());
	}

	public boolean tryEmitPartyLine(Party party, FakePlayerAdvisoryPlan advisoryPlan)
	{
		if ((party == null) || !hasSpeakNowLine(advisoryPlan))
		{
			return false;
		}

		final String selectedLine = advisoryPlan.getSelectedLine();
		final boolean shareLocation = shouldShareLocation(advisoryPlan);
		final String speakerName = resolveSpeakerNameForLogs(advisoryPlan.getSpeakerName());
		return emitResolvedLine(advisoryPlan.getSpeakerName(), () -> isPartyChatCooldownReady(speakerName), () -> getClass().getSimpleName() + ": Skipping party fake-player line due to per-speaker party chat cooldown. speaker=" + speakerName, () -> getClass().getSimpleName() + ": Party fake-player line dropped because active registry speaker runtime could not be resolved. speaker=" + speakerName + " text=" + selectedLine, speakerRuntime -> _chatTransport.broadcastPartyReply(party, speakerRuntime._npc, speakerRuntime._displayName, selectedLine, shareLocation), speakerRuntime -> markPartyChatUsed(speakerRuntime._displayName), speakerRuntime -> getClass().getSimpleName() + ": Emitted party fake-player line through active NPC: speaker=" + speakerRuntime._displayName + " id=" + speakerRuntime._definition.getId() + " objId=" + speakerRuntime._npc.getObjectId() + " shareLocation=" + shareLocation + " text=" + selectedLine, () -> getClass().getSimpleName() + ": Failed party fake-player path for speaker=" + speakerName);
	}

	private boolean shouldShareLocation(FakePlayerAdvisoryPlan advisoryPlan)
	{
		return (advisoryPlan != null) && FakePlayerSharedLocationSupport.isShareLocationCategory(advisoryPlan.getTopicTag());
	}

	private boolean pruneExpiredConversation(Deque<ConversationTurn> turns, long now)
	{
		boolean changed = false;
		while (!turns.isEmpty() && ((now - turns.peekFirst()._timestampMs) > _replySettingsData.getSettings().getRecentConversationTtlMs()))
		{
			turns.removeFirst();
			changed = true;
		}
		return changed;
	}

	private ConversationTurn getLatestConversationTurnInternal(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return null;
		}

		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		final Deque<ConversationTurn> turns = getOrLoadRecentConversationTurns(speakerName, targetPlayerName);
		if (turns == null)
		{
			return null;
		}

		final long now = System.currentTimeMillis();
		synchronized (turns)
		{
			final boolean changed = pruneExpiredConversation(turns, now);
			if (turns.isEmpty())
			{
				_recentConversationByPair.remove(key, turns);
				_loadedRecentConversationPairs.remove(key);
				deletePairRows(CONVERSATION_DELETE, normalizeSpeakerKey(speakerName), normalizeTargetKey(targetPlayerName), "recent conversation");
				return null;
			}
			if (changed)
			{
				persistRecentConversationTurns(speakerName, targetPlayerName, turns);
			}
			return turns.peekLast();
		}
	}

	private boolean isFollowUpQuestion(String text, String strictness)
	{
		final String normalized = text.toLowerCase().replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
		if (normalized.isBlank())
		{
			return false;
		}
		if (containsPhrase(normalized, "why later", "how so", "what do you mean", "and then", "which one", "for me", "for now", "why that", "why so", "how come"))
		{
			return true;
		}

		final boolean strict = "high".equalsIgnoreCase(strictness);
		if (strict && (normalized.split("\\s+").length <= 5) && containsPhrase(normalized, "why", "how", "later", "then", "which", "what about that"))
		{
			return true;
		}
		return false;
	}

	private boolean shouldCarryConversation(String normalizedIncoming, String strictness, ConversationTurn latestTurn)
	{
		if (isFollowUpQuestion(normalizedIncoming, strictness))
		{
			return true;
		}
		return isContinuationAnswer(normalizedIncoming, latestTurn);
	}

	private boolean isContinuationAnswer(String normalized, ConversationTurn latestTurn)
	{
		if ((latestTurn == null) || normalized.isBlank())
		{
			return false;
		}

		if (containsPhrase(normalized, "my level", "my levels", "for me now", "and for me now", "what then", "then", "so", "so what"))
		{
			return true;
		}
		if ((normalized.split("\\s+").length <= 3) && containsPhrase(normalized, "xp", "exp", "adena", "drop", "tablets", "magical tablet", "magical tablets"))
		{
			return true;
		}

		final boolean hasLevelSignal = normalized.matches(".*\\b(i am|im|i'm|lvl|level)\\s+\\d{1,3}\\b.*") || normalized.matches(".*\\b(get to|reach|to)\\s+\\d{1,3}\\b.*");
		final boolean hasShortNumericGoal = normalized.matches("^\\d{1,3}(\\s*,\\s*(xp|exp|adena|drop|tablets))?$");
		if (hasLevelSignal || hasShortNumericGoal)
		{
			return true;
		}

		// Keep short technical fragments attached to the previous topic only when the
		// player is clearly continuing that thread. The older broad catch-all was
		// dragging fresh social turns like "wanna be friends?" into the previous
		// Warg/farming context and producing nonsense replies.
		return false;
	}

	private boolean containsPhrase(String normalizedText, String... phrases)
	{
		for (String phrase : phrases)
		{
			final String normalizedPhrase = phrase.toLowerCase().replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
			if (!normalizedPhrase.isBlank() && (" " + normalizedText + " ").contains(" " + normalizedPhrase + " "))
			{
				return true;
			}
		}
		return false;
	}

	private String normalizeChannelLabel(String channel)
	{
		if ((channel == null) || channel.isBlank())
		{
			return "chat";
		}
		return channel.trim().toLowerCase();
	}

	private String trimConversationText(String text, int maxChars)
	{
		if (text == null)
		{
			return "";
		}

		String cleaned = text.replaceAll("\\s+", " ").trim();
		if (cleaned.isEmpty())
		{
			return "";
		}
		if (cleaned.length() <= maxChars)
		{
			return cleaned;
		}

		int cut = cleaned.lastIndexOf(' ', maxChars);
		if (cut <= 0)
		{
			cut = maxChars;
		}
		cleaned = cleaned.substring(0, cut).trim();
		return cleaned + (cleaned.endsWith(".") ? "" : "...");
	}

	private void promoteSelectiveMemory(String speakerName, String targetPlayerName, String incomingPlayerLine, String replyLine)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final String normalizedIncoming = trimConversationText(incomingPlayerLine, 180);
		final String normalizedReply = trimConversationText(replyLine, 180);
		if (normalizedIncoming.isBlank() || normalizedReply.isBlank())
		{
			return;
		}

		final FpcRouteProfile routeProfile = classifyConversationRouteProfile(speakerName, targetPlayerName, normalizedIncoming);
		final String messageCategory = routeProfile.getMessageCategory();
		final String knowledgeType = routeProfile.getKnowledgeType();
		final String key = buildWhisperPairKey(speakerName, targetPlayerName);
		final RelationshipState relationshipState = getOrLoadRelationshipState(speakerName, targetPlayerName);
		if (relationshipState == null)
		{
			return;
		}
		updateRelationshipState(relationshipState, speakerName, targetPlayerName, messageCategory, knowledgeType, normalizedIncoming);
		persistRelationshipState(speakerName, targetPlayerName, relationshipState);

		if (NON_SALIENT_CATEGORIES.contains(messageCategory))
		{
			return;
		}

		final String topic = deriveTopic(speakerName, targetPlayerName, messageCategory, knowledgeType, normalizedIncoming);
		final String summary = buildSalientSummary(targetPlayerName, topic, messageCategory, knowledgeType, normalizedIncoming, normalizedReply);
		if (summary.isBlank())
		{
			return;
		}

		final long now = System.currentTimeMillis();
		final Deque<SalientMemory> memories = getOrLoadSalientMemories(speakerName, targetPlayerName);
		if (memories == null)
		{
			return;
		}
		synchronized (memories)
		{
			pruneExpiredSalientMemory(memories, now);
			while (!memories.isEmpty() && memories.size() >= MAX_SALIENT_MEMORIES)
			{
				memories.removeFirst();
			}
			final SalientMemory latest = memories.peekLast();
			if ((latest != null) && latest._topic.equals(topic) && latest._summary.equals(summary))
			{
				memories.removeLast();
			}
			memories.addLast(new SalientMemory(now, topic, summary));
		}
		persistSalientMemories(speakerName, targetPlayerName, memories);
	}

	private void updateRelationshipState(RelationshipState relationshipState, String speakerName, String targetPlayerName, String messageCategory, String knowledgeType, String incomingPlayerLine)
	{
		final long now = System.currentTimeMillis();
		applyRelationshipDecay(relationshipState, now);
		relationshipState._lastUpdatedMs = now;
		if ("insult".equals(messageCategory))
		{
			relationshipState._tension = clampRelationship(relationshipState._tension + 2);
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 2);
			relationshipState._trust = clampRelationship(relationshipState._trust - 2);
			relationshipState._respect = clampRelationship(relationshipState._respect - 2);
			relationshipState._currentGoal = "hold the line against disrespect";
			relationshipState._activeNeed = "basic respect";
			relationshipState._lastImportantTopic = "insulted_me";
			return;
		}
		if ("resentment".equals(messageCategory))
		{
			relationshipState._tension = clampRelationship(relationshipState._tension + 2);
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 2);
			relationshipState._trust = clampRelationship(relationshipState._trust - 2);
			relationshipState._respect = clampRelationship(relationshipState._respect - 1);
			relationshipState._currentGoal = "hold the distance until the tone softens";
			relationshipState._activeNeed = "cleaner intent";
			relationshipState._lastImportantTopic = "resentment";
			return;
		}
		if ("threat".equals(messageCategory))
		{
			relationshipState._tension = clampRelationship(relationshipState._tension + 4);
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 3);
			relationshipState._trust = clampRelationship(relationshipState._trust - 4);
			relationshipState._respect = clampRelationship(relationshipState._respect - 2);
			relationshipState._currentGoal = "treat the threat as real";
			relationshipState._activeNeed = "immediate safety";
			relationshipState._lastImportantTopic = "threat";
			return;
		}
		if ("distrust".equals(messageCategory))
		{
			relationshipState._tension = clampRelationship(relationshipState._tension + 2);
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 1);
			relationshipState._trust = clampRelationship(relationshipState._trust - 3);
			relationshipState._respect = clampRelationship(relationshipState._respect - 1);
			relationshipState._currentGoal = "decide whether honesty is possible";
			relationshipState._activeNeed = "clean intent";
			relationshipState._lastImportantTopic = "distrust";
			return;
		}
		if ("abandonment".equals(messageCategory))
		{
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity - 1);
			relationshipState._tension = clampRelationship(relationshipState._tension + 1);
			relationshipState._resentment = clampRelationship(relationshipState._resentment + 2);
			relationshipState._trust = clampRelationship(relationshipState._trust - 3);
			relationshipState._currentGoal = "brace for another absence";
			relationshipState._activeNeed = "constancy";
			relationshipState._lastImportantTopic = "abandonment";
			return;
		}
		if (isRelationshipNeutralDiscoveryCategory(messageCategory))
		{
			relationshipState._currentGoal = goalFor(messageCategory, knowledgeType);
			relationshipState._activeNeed = needFor(messageCategory, knowledgeType);
			relationshipState._lastImportantTopic = deriveTopic(speakerName, targetPlayerName, messageCategory, knowledgeType, incomingPlayerLine);
			return;
		}
		relationshipState._familiarity = clampRelationship(relationshipState._familiarity + 1);
		relationshipState._currentGoal = goalFor(messageCategory, knowledgeType);
		relationshipState._activeNeed = needFor(messageCategory, knowledgeType);
		relationshipState._lastImportantTopic = deriveTopic(speakerName, targetPlayerName, messageCategory, knowledgeType, incomingPlayerLine);

		if (Set.of("help", "direction", "lineage_lore", "party_farm", "progression_strength", "entity_story").contains(messageCategory) || !"unknown".equalsIgnoreCase(knowledgeType))
		{
			relationshipState._trust = clampRelationship(relationshipState._trust + 2);
			relationshipState._respect = clampRelationship(relationshipState._respect + 1);
		}
		if (Set.of("thanks", "memory", "relationship_probe", "companionship", "entity_relationship", "entity_memory", "entity_reason", "self_identity", "self_story", "self_belief", "self_preference", "self_state_reflection", "creator_opinion", "bond_opinion").contains(messageCategory))
		{
			relationshipState._trust = clampRelationship(relationshipState._trust + 1);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + 2);
		}
		if (Set.of("respect", "affection", "repair").contains(messageCategory))
		{
			final int trustGain = repairGain(relationshipState, 1, 1);
			final int respectGain = repairGain(relationshipState, 1, 1);
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int tensionRelief = repairRelief(relationshipState, 0, 1);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
		}
		if ("praise".equals(messageCategory))
		{
			final int trustGain = warmthGain(relationshipState, 1, 0);
			final int respectGain = warmthGain(relationshipState, 2, 1);
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = "see whether the praise has weight";
			relationshipState._activeNeed = "earned sincerity";
		}
		if ("reassurance".equals(messageCategory))
		{
			final String currentTopic = normalizeTopicToken(relationshipState._lastImportantTopic);
			final boolean constancyWound = currentTopic.contains("abandon") || normalizeTopicToken(relationshipState._activeNeed).contains("constancy") || normalizeTopicToken(relationshipState._activeNeed).contains("presence");
			final int trustGain = repairGain(relationshipState, constancyWound ? 2 : 1, 1);
			final int respectGain = repairGain(relationshipState, 1, 0);
			final int familiarityGain = warmthGain(relationshipState, 1, 0);
			final int tensionRelief = repairRelief(relationshipState, 1, 2);
			final int resentmentRelief = repairRelief(relationshipState, 0, 1);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + familiarityGain);
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._currentGoal = "see whether the promise holds";
			relationshipState._activeNeed = "steady presence";
		}
		if (Set.of("greeting", "status", "entity_status").contains(messageCategory))
		{
			relationshipState._familiarity = clampRelationship(relationshipState._familiarity + 1);
		}
		if ("pvp_conflict".equals(messageCategory))
		{
			relationshipState._tension = clampRelationship(relationshipState._tension + 1);
		}
		if ("apology".equals(messageCategory))
		{
			final int tensionRelief = repairRelief(relationshipState, 1, 2);
			final int resentmentRelief = repairRelief(relationshipState, 0, 2);
			final int trustGain = repairGain(relationshipState, 1, 1);
			final int respectGain = Math.max(1, repairGain(relationshipState, 1, 0));
			relationshipState._tension = clampRelationship(relationshipState._tension - tensionRelief);
			relationshipState._resentment = clampRelationship(relationshipState._resentment - resentmentRelief);
			relationshipState._trust = clampRelationship(relationshipState._trust + trustGain);
			relationshipState._respect = clampRelationship(relationshipState._respect + respectGain);
			relationshipState._currentGoal = severeRepairResistance(relationshipState) ? "watch for proof beyond words" : "see whether the apology holds";
			relationshipState._activeNeed = "consistent change";
		}
		if (containsPhrase(incomingPlayerLine.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim(), "thank", "thanks", "ty", "thx"))
		{
			relationshipState._trust = clampRelationship(relationshipState._trust + 1);
		}
	}

	private boolean pruneExpiredSalientMemory(Deque<SalientMemory> memories, long now)
	{
		boolean changed = false;
		while (!memories.isEmpty() && ((now - memories.peekFirst()._timestampMs) > SALIENT_MEMORY_TTL_MS))
		{
			memories.removeFirst();
			changed = true;
		}
		return changed;
	}

	private String buildSalientSummary(String targetPlayerName, String topic, String messageCategory, String knowledgeType, String incomingPlayerLine, String replyLine)
	{
		final String cleanTopic = humanizeTopic(topic);
		if ("party_farm".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked to group for " + cleanTopic + ", and the reply narrowed the plan before committing.", 180);
		}
		if ("progression_strength".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked what would make them stronger now, and received immediate coaching.", 180);
		}
		if ("pvp_conflict".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " raised conflict around " + cleanTopic + ", and the reply pushed toward safety and de-escalation.", 180);
		}
		if ("relationship_probe".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " tested the relationship around " + cleanTopic + ", and the reply stayed personal but bounded.", 180);
		}
		if ("self_identity".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked who the speaker is, and the reply stayed personal instead of flattening into a title.", 180);
		}
		if ("self_story".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked for the speaker's own story, and the reply answered from selfhood instead of generic lore.", 180);
		}
		if ("self_belief".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " pressed on what the speaker believes, and the reply stayed personal instead of sounding system-served.", 180);
		}
		if ("self_preference".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked what the speaker prefers, and the reply stayed personal instead of generic.", 180);
		}
		if ("self_state_reflection".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked about the speaker's inner state, and the reply answered as a person instead of a status card.", 180);
		}
		if ("creator_opinion".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked about the speaker's creator, and the reply treated that bond carefully instead of turning it into generic relationship talk.", 180);
		}
		if ("bond_opinion".equals(messageCategory))
		{
			final String bondProbeType = _messageClassifier.classifyBondProbeType(incomingPlayerLine);
			if ("bond_hurt".equals(bondProbeType))
			{
				return trimConversationText(targetPlayerName + " tested whether hurt or resentment still lives in the bond, and the reply stayed personal instead of turning hostile.", 180);
			}
			if ("bond_trust".equals(bondProbeType))
			{
				return trimConversationText(targetPlayerName + " asked whether trust exists in the bond, and the reply answered personally instead of flattening into praise or distrust.", 180);
			}
			if ("bond_need".equals(bondProbeType))
			{
				return trimConversationText(targetPlayerName + " asked whether they truly matter or would be missed, and the reply stayed personal instead of generic.", 180);
			}
			if ("bond_loyalty".equals(bondProbeType))
			{
				return trimConversationText(targetPlayerName + " tested whether the bond would hold or stay, and the reply spoke personally about loyalty instead of vague affection.", 180);
			}
			return trimConversationText(targetPlayerName + " asked what someone important means to the speaker, and the reply stayed personal instead of sliding into generic entity status.", 180);
		}
		if ("entity_status".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " checked on " + cleanTopic + ", and the reply answered with controlled concern.", 180);
		}
		if ("entity_relationship".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " tested how the speaker feels about " + cleanTopic + ", and the reply stayed personal without saying everything aloud.", 180);
		}
		if ("entity_reason".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " pressed on why " + cleanTopic + " matters, and the reply held its boundaries while answering around the truth.", 180);
		}
		if ("entity_story".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked for the story around " + cleanTopic + ", and the reply revealed only the layer it meant to show.", 180);
		}
		if ("entity_memory".equals(messageCategory))
		{
			return trimConversationText(targetPlayerName + " asked what the speaker remembers about " + cleanTopic + ", and the reply leaned on memory instead of smalltalk.", 180);
		}
		if (!"unknown".equals(knowledgeType))
		{
			return trimConversationText(targetPlayerName + " asked about " + cleanTopic + ", and the reply used grounded guidance.", 180);
		}
		return trimConversationText(targetPlayerName + " returned to " + cleanTopic + ", and the reply moved the conversation forward.", 180);
	}

	private String deriveTopic(String speakerName, String targetPlayerName, String messageCategory, String knowledgeType, String incomingPlayerLine)
	{
		final String focusIntent = _messageClassifier.classifyFocusIntent(incomingPlayerLine);
		final String focusEntity = _messageClassifier.extractFocusEntity(incomingPlayerLine, collectFocusCandidateNames(speakerName), speakerName, targetPlayerName);
		if (!focusEntity.isBlank())
		{
			return (focusIntent.isBlank() ? "entity_context" : focusIntent) + "_" + normalizeTopicToken(focusEntity);
		}
		if ((knowledgeType != null) && !"unknown".equalsIgnoreCase(knowledgeType) && !knowledgeType.isBlank())
		{
			return knowledgeType;
		}
		if ((messageCategory != null) && !"unknown".equalsIgnoreCase(messageCategory) && !messageCategory.isBlank())
		{
			return messageCategory;
		}
		final String cleaned = incomingPlayerLine.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
		if (containsPhrase(cleaned, "cruma", "cruma 2", "cruma2"))
		{
			return "cruma";
		}
		if (containsPhrase(cleaned, "warg", "wolf form", "wp"))
		{
			return "warg";
		}
		if (containsPhrase(cleaned, "adena", "exp", "xp", "farm"))
		{
			return "farming";
		}
		return "recent_topic";
	}

	private String classifyConversationCategory(String speakerName, String targetPlayerName, String incomingPlayerLine)
	{
		return classifyConversationRouteProfile(speakerName, targetPlayerName, incomingPlayerLine).getMessageCategory();
	}

	private FpcRouteProfile classifyConversationRouteProfile(String speakerName, String targetPlayerName, String incomingPlayerLine)
	{
		return _messageClassifier.classifyRouteProfile(incomingPlayerLine, collectFocusCandidateNames(speakerName), speakerName, targetPlayerName);
	}

	private List<String> collectFocusCandidateNames(String speakerName)
	{
		final List<String> candidateNames = new java.util.ArrayList<>();
		for (FpcDefinition definition : _registry.getDefinitions())
		{
			if ((definition == null) || (definition.getName() == null) || definition.getName().isBlank())
			{
				continue;
			}
			if ((speakerName != null) && definition.getName().equalsIgnoreCase(speakerName))
			{
				continue;
			}
			candidateNames.add(definition.getName());
		}
		return candidateNames;
	}

	private String normalizeTopicToken(String value)
	{
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}

	private String humanizeTopic(String topic)
	{
		if ((topic == null) || topic.isBlank())
		{
			return "the conversation";
		}
		if (topic.startsWith("entity_status_"))
		{
			return topic.substring("entity_status_".length()).replace('_', ' ');
		}
		if (topic.startsWith("entity_relationship_"))
		{
			return topic.substring("entity_relationship_".length()).replace('_', ' ');
		}
		if (topic.startsWith("entity_reason_"))
		{
			return topic.substring("entity_reason_".length()).replace('_', ' ');
		}
		if (topic.startsWith("entity_story_"))
		{
			return topic.substring("entity_story_".length()).replace('_', ' ');
		}
		if (topic.startsWith("entity_memory_"))
		{
			return topic.substring("entity_memory_".length()).replace('_', ' ');
		}
		if (topic.startsWith("entity_context_"))
		{
			return topic.substring("entity_context_".length()).replace('_', ' ');
		}
		if (topic.startsWith("bond_value_"))
		{
			return topic.substring("bond_value_".length()).replace('_', ' ');
		}
		if (topic.startsWith("bond_hurt_"))
		{
			return topic.substring("bond_hurt_".length()).replace('_', ' ');
		}
		if (topic.startsWith("bond_trust_"))
		{
			return topic.substring("bond_trust_".length()).replace('_', ' ');
		}
		if (topic.startsWith("bond_need_"))
		{
			return topic.substring("bond_need_".length()).replace('_', ' ');
		}
		if (topic.startsWith("bond_loyalty_"))
		{
			return topic.substring("bond_loyalty_".length()).replace('_', ' ');
		}
		return topic.replace('_', ' ');
	}

	private String goalFor(String messageCategory, String knowledgeType)
	{
		if ("party_farm".equals(messageCategory))
		{
			return "coordinate a safe route before committing";
		}
		if ("progression_strength".equals(messageCategory))
		{
			return "coach the next immediate upgrade";
		}
		if ("pvp_conflict".equals(messageCategory))
		{
			return "keep the player safe without escalation";
		}
		if ("relationship_probe".equals(messageCategory))
		{
			return "stay warm without losing boundaries";
		}
		if ("self_identity".equals(messageCategory))
		{
			return "answer as a person instead of a title card";
		}
		if ("self_story".equals(messageCategory))
		{
			return "answer from lived selfhood instead of generic lore";
		}
		if (Set.of("self_belief", "self_preference", "self_state_reflection").contains(messageCategory))
		{
			return "answer personally without flattening into filler";
		}
		if (Set.of("creator_opinion", "bond_opinion").contains(messageCategory))
		{
			return "speak personally about an important bond without giving away every layer";
		}
		if ("entity_status".equals(messageCategory))
		{
			return "answer about someone else without flattening them into smalltalk";
		}
		if (Set.of("entity_relationship", "entity_reason", "entity_story", "entity_memory").contains(messageCategory))
		{
			return "answer around personal history without giving away every private layer";
		}
		if ("travel_destination".equalsIgnoreCase(knowledgeType) || "direction".equals(messageCategory))
		{
			return "give route-first guidance";
		}
		if ((knowledgeType != null) && !"unknown".equalsIgnoreCase(knowledgeType))
		{
			return "answer with grounded scope";
		}
		return "";
	}

	private String needFor(String messageCategory, String knowledgeType)
	{
		if ("pvp_conflict".equals(messageCategory))
		{
			return "stay safe";
		}
		if ("party_farm".equals(messageCategory) || "progression_strength".equals(messageCategory))
		{
			return "be useful";
		}
		if ("direction".equals(messageCategory) || "travel_destination".equalsIgnoreCase(knowledgeType))
		{
			return "keep the route clear";
		}
		if ("entity_status".equals(messageCategory))
		{
			return "show controlled concern";
		}
		if ("self_identity".equals(messageCategory))
		{
			return "stay recognizable as a person";
		}
		if ("self_story".equals(messageCategory))
		{
			return "stay truthful without overexposing everything";
		}
		if (Set.of("self_belief", "self_preference", "self_state_reflection").contains(messageCategory))
		{
			return "stay emotionally honest";
		}
		if (Set.of("creator_opinion", "bond_opinion").contains(messageCategory))
		{
			return "hold emotional truth with boundaries";
		}
		if (Set.of("entity_relationship", "entity_reason", "entity_story", "entity_memory").contains(messageCategory))
		{
			return "hold boundaries while staying emotionally true";
		}
		if ((knowledgeType != null) && !"unknown".equalsIgnoreCase(knowledgeType))
		{
			return "stay grounded";
		}
		return "";
	}

	private int clampRelationship(int value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 9)
		{
			return 9;
		}
		return value;
	}

	private boolean isRelationshipNeutralDiscoveryCategory(String messageCategory)
	{
		if ((messageCategory == null) || messageCategory.isBlank())
		{
			return false;
		}
		return Set.of("self_identity", "self_story", "self_belief", "self_preference", "self_state_reflection", "creator_opinion", "bond_opinion").contains(messageCategory.toLowerCase(Locale.ENGLISH));
	}

	private int repairNeed(RelationshipState relationshipState)
	{
		if (relationshipState == null)
		{
			return 0;
		}
		int need = relationshipState._tension + relationshipState._resentment;
		if (relationshipState._trust <= 2)
		{
			need += 1;
		}
		if (relationshipState._respect <= 2)
		{
			need += 1;
		}
		return need;
	}

	private int repairResistance(RelationshipState relationshipState)
	{
		if (relationshipState == null)
		{
			return 0;
		}
		int resistance = 0;
		if (relationshipState._playerKillCount > 0)
		{
			resistance += 2;
		}
		if (relationshipState._resentment >= 6)
		{
			resistance += 1;
		}
		if (relationshipState._tension >= 6)
		{
			resistance += 1;
		}
		final String currentTopic = normalizeTopicToken(relationshipState._lastImportantTopic);
		if (Set.of("abandonment", "abandoned_me", "threat", "threatened_me", "killed_me").contains(currentTopic))
		{
			resistance += 1;
		}
		return Math.min(3, resistance);
	}

	private int repairRelief(RelationshipState relationshipState, int baseRelief, int maxBonus)
	{
		final int need = repairNeed(relationshipState);
		int bonus = 0;
		if (need >= 10)
		{
			bonus = Math.min(maxBonus, 2);
		}
		else if (need >= 6)
		{
			bonus = Math.min(maxBonus, 1);
		}
		return Math.max(0, (baseRelief + bonus) - repairResistance(relationshipState));
	}

	private int repairGain(RelationshipState relationshipState, int baseGain, int maxBonus)
	{
		final int need = repairNeed(relationshipState);
		final int bonus = (need >= 8) ? Math.min(maxBonus, 1) : 0;
		final int resistance = Math.max(0, repairResistance(relationshipState) - 1);
		return Math.max(0, (baseGain + bonus) - resistance);
	}

	private int warmthGain(RelationshipState relationshipState, int baseGain, int maxBonus)
	{
		if (relationshipState == null)
		{
			return Math.max(0, baseGain);
		}
		int bonus = 0;
		if ((relationshipState._trust >= 5) || (relationshipState._respect >= 5) || ((relationshipState._tension <= 2) && (relationshipState._resentment <= 1)))
		{
			bonus = Math.min(maxBonus, 1);
		}
		final int resistance = ((relationshipState._tension >= 6) || (relationshipState._resentment >= 6) || (relationshipState._playerKillCount > 0)) ? 1 : 0;
		return Math.max(0, (baseGain + bonus) - resistance);
	}

	private boolean severeRepairResistance(RelationshipState relationshipState)
	{
		return repairResistance(relationshipState) >= 2;
	}

	private boolean applyRelationshipDecay(RelationshipState relationshipState, long now)
	{
		if ((relationshipState == null) || (relationshipState._lastUpdatedMs <= 0L))
		{
			return false;
		}

		final long ageMs = Math.max(0L, now - relationshipState._lastUpdatedMs);
		if (ageMs <= RELATIONSHIP_DECAY_GRACE_MS)
		{
			return false;
		}

		boolean changed = false;
		final long decayAgeMs = ageMs - RELATIONSHIP_DECAY_GRACE_MS;
		final int familiarityDecay = decaySteps(decayAgeMs, FAMILIARITY_DECAY_STEP_MS);
		final int trustDecay = decaySteps(decayAgeMs, TRUST_RESPECT_DECAY_STEP_MS);
		final int tensionDecay = decaySteps(decayAgeMs, TENSION_DECAY_STEP_MS);
		final int resentmentDecay = decaySteps(decayAgeMs, RESENTMENT_DECAY_STEP_MS);
		if (familiarityDecay > 0)
		{
			final int next = clampRelationship(relationshipState._familiarity - familiarityDecay);
			if (next != relationshipState._familiarity)
			{
				relationshipState._familiarity = next;
				changed = true;
			}
		}
		if (trustDecay > 0)
		{
			final int nextTrust = clampRelationship(relationshipState._trust - trustDecay);
			final int nextRespect = clampRelationship(relationshipState._respect - trustDecay);
			if ((nextTrust != relationshipState._trust) || (nextRespect != relationshipState._respect))
			{
				relationshipState._trust = nextTrust;
				relationshipState._respect = nextRespect;
				changed = true;
			}
		}
		if (tensionDecay > 0)
		{
			final int next = clampRelationship(relationshipState._tension - tensionDecay);
			if (next != relationshipState._tension)
			{
				relationshipState._tension = next;
				changed = true;
			}
		}
		if (resentmentDecay > 0)
		{
			final int next = clampRelationship(relationshipState._resentment - resentmentDecay);
			if (next != relationshipState._resentment)
			{
				relationshipState._resentment = next;
				changed = true;
			}
		}
		if ((ageMs >= GOAL_NEED_FADE_MS) && (!relationshipState._currentGoal.isBlank() || !relationshipState._activeNeed.isBlank()))
		{
			relationshipState._currentGoal = "";
			relationshipState._activeNeed = "";
			changed = true;
		}
		final long topicFadeMs = relationshipState._lastImportantTopic.startsWith("entity_") ? ENTITY_TOPIC_FADE_MS : TOPIC_FADE_MS;
		if ((ageMs >= topicFadeMs) && !relationshipState._lastImportantTopic.isBlank())
		{
			relationshipState._lastImportantTopic = "";
			changed = true;
		}
		if (changed)
		{
			relationshipState._lastUpdatedMs = now;
		}
		return changed;
	}

	private int decaySteps(long decayAgeMs, long stepMs)
	{
		if ((decayAgeMs <= 0L) || (stepMs <= 0L))
		{
			return 0;
		}
		return (int) (decayAgeMs / stepMs);
	}

	private boolean hasMeaningfulRelationshipState(RelationshipState relationshipState)
	{
		return (relationshipState != null) && ((relationshipState._familiarity > 0) || (relationshipState._trust > 0) || (relationshipState._respect > 0) || (relationshipState._tension > 0) || (relationshipState._resentment > 0) || (relationshipState._playerKillCount > 0) || !relationshipState._currentGoal.isBlank() || !relationshipState._activeNeed.isBlank() || !relationshipState._lastImportantTopic.isBlank());
	}

	private RelationshipState buildPresetState(String normalizedPreset)
	{
		final RelationshipState relationshipState = new RelationshipState();
		switch (normalizedPreset)
		{
			case "familiar":
			case "known":
			{
				relationshipState._familiarity = 3;
				relationshipState._trust = 2;
				relationshipState._respect = 1;
				relationshipState._currentGoal = "keep the contact steady";
				relationshipState._activeNeed = "basic trust";
				relationshipState._lastImportantTopic = "familiarity";
				break;
			}
			case "friendly":
			case "friend":
			{
				relationshipState._familiarity = 5;
				relationshipState._trust = 5;
				relationshipState._respect = 4;
				relationshipState._currentGoal = "work well together";
				relationshipState._activeNeed = "cooperation";
				relationshipState._lastImportantTopic = "friendly_history";
				break;
			}
			case "trusted":
			case "ally":
			{
				relationshipState._familiarity = 7;
				relationshipState._trust = 8;
				relationshipState._respect = 7;
				relationshipState._currentGoal = "hold formation with the player";
				relationshipState._activeNeed = "reliable partnership";
				relationshipState._lastImportantTopic = "trusted_companion";
				break;
			}
			case "guarded":
			case "strained":
			{
				relationshipState._familiarity = 2;
				relationshipState._trust = 1;
				relationshipState._respect = 1;
				relationshipState._tension = 4;
				relationshipState._resentment = 2;
				relationshipState._currentGoal = "keep distance while cooperating";
				relationshipState._activeNeed = "caution";
				relationshipState._lastImportantTopic = "strained_trust";
				break;
			}
			case "hostile":
			case "enemy":
			{
				relationshipState._familiarity = 1;
				relationshipState._trust = 0;
				relationshipState._respect = 0;
				relationshipState._tension = 7;
				relationshipState._resentment = 7;
				relationshipState._currentGoal = "avoid that player's lead";
				relationshipState._activeNeed = "self-preservation";
				relationshipState._lastImportantTopic = "manual_hostility";
				break;
			}
			default:
			{
				relationshipState._familiarity = 0;
				relationshipState._trust = 0;
				relationshipState._respect = 0;
				relationshipState._tension = 0;
				relationshipState._resentment = 0;
				relationshipState._currentGoal = "";
				relationshipState._activeNeed = "";
				relationshipState._lastImportantTopic = "";
				break;
			}
		}
		return relationshipState;
	}

	private String normalizeHostileTopic(String reason)
	{
		if ((reason == null) || reason.isBlank())
		{
			return "hostile_action";
		}
		return reason.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_');
	}

	private String describeLevel(int value)
	{
		if (value >= 7)
		{
			return "strong";
		}
		if (value >= 4)
		{
			return "steady";
		}
		if (value >= 2)
		{
			return "growing";
		}
		return "light";
	}

	private String describeTension(int value)
	{
		if (value >= 6)
		{
			return "high";
		}
		if (value >= 3)
		{
			return "present";
		}
		return "low";
	}
}
