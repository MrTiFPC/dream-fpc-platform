package org.l2jmobius.gameserver.fakeplayer.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialActionStance;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialEpisode;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.enums.FpcSocialEpisodeType;

public class FakePlayerSocialMemoryService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerSocialMemoryService.class.getName());
	private static final int MAX_EPISODES_PER_PAIR = 12;
	private static final long FRESH_WINDOW_MS = 15L * 60L * 1000L;
	private static final long RECENT_WINDOW_MS = 2L * 60L * 60L * 1000L;
	private static final long SAME_DAY_WINDOW_MS = 12L * 60L * 60L * 1000L;
	private static final long OLDER_WINDOW_MS = 48L * 60L * 60L * 1000L;
	private static final String TABLE_NAME = "fakeplayer_social_episodes";
	private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + "id BIGINT NOT NULL AUTO_INCREMENT, " + "speaker_key VARCHAR(64) NOT NULL, " + "player_key VARCHAR(64) NOT NULL, " + "event_type VARCHAR(32) NOT NULL, " + "summary TEXT NULL, " + "created_at BIGINT NOT NULL, " + "PRIMARY KEY (id), " + "INDEX idx_fakeplayer_social_pair (speaker_key, player_key, created_at)" + ") DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
	private static final String INSERT_EPISODE = "INSERT INTO " + TABLE_NAME + " (speaker_key, player_key, event_type, summary, created_at) VALUES (?,?,?,?,?)";
	private static final String LOAD_RECENT_EPISODES = "SELECT event_type, summary, created_at FROM " + TABLE_NAME + " WHERE speaker_key=? AND player_key=? ORDER BY created_at DESC LIMIT ?";
	private static final String HAS_EPISODE_TYPE = "SELECT 1 FROM " + TABLE_NAME + " WHERE speaker_key=? AND player_key=? AND event_type=? LIMIT 1";
	private static final String DELETE_PAIR_EPISODES = "DELETE FROM " + TABLE_NAME + " WHERE speaker_key=? AND player_key=?";

	private final ConcurrentHashMap<String, Deque<FpcSocialEpisode>> _episodesByPair = new ConcurrentHashMap<>();
	private final java.util.Set<String> _loadedPairs = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<String, Boolean> _historicalKillHistoryByPair = new ConcurrentHashMap<>();

	public void initializePersistence()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			final ResultSet result = con.getMetaData().getTables(null, null, TABLE_NAME, null);
			if (!result.next())
			{
				try (Statement statement = con.createStatement())
				{
					statement.executeUpdate(CREATE_TABLE);
					LOGGER.info("Missing '" + TABLE_NAME + "' table was successfully created.");
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Error creating '" + TABLE_NAME + "' table.", e);
		}
	}

	public void recordEpisode(String speakerName, String targetPlayerName, FpcSocialEpisodeType type)
	{
		recordEpisode(speakerName, targetPlayerName, type, "");
	}

	public void recordEpisode(String speakerName, String targetPlayerName, FpcSocialEpisodeType type, String detail)
	{
		if ((type == null) || (speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final String pairKey = buildPairKey(speakerName, targetPlayerName);
		final String speakerKey = normalizeName(speakerName);
		final String playerKey = normalizeName(targetPlayerName);
		final Deque<FpcSocialEpisode> episodes = getOrLoadEpisodes(pairKey, speakerKey, playerKey);
		final FpcSocialEpisode episode = new FpcSocialEpisode(System.currentTimeMillis(), type, buildEpisodeSummary(type, detail));
		synchronized (episodes)
		{
			while (episodes.size() >= MAX_EPISODES_PER_PAIR)
			{
				episodes.removeFirst();
			}
			episodes.addLast(episode);
		}
		storeEpisode(speakerKey, playerKey, episode);
		if (type == FpcSocialEpisodeType.KILLED_ME)
		{
			_historicalKillHistoryByPair.put(pairKey, Boolean.TRUE);
		}
	}

	public boolean recordEpisodeIfNotRecent(String speakerName, String targetPlayerName, FpcSocialEpisodeType type, String detail, long minimumSpacingMs)
	{
		if ((type == null) || (speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return false;
		}

		if (minimumSpacingMs <= 0L)
		{
			recordEpisode(speakerName, targetPlayerName, type, detail);
			return true;
		}

		final String pairKey = buildPairKey(speakerName, targetPlayerName);
		final String speakerKey = normalizeName(speakerName);
		final String playerKey = normalizeName(targetPlayerName);
		final Deque<FpcSocialEpisode> episodes = getOrLoadEpisodes(pairKey, speakerKey, playerKey);
		final long now = System.currentTimeMillis();
		final FpcSocialEpisode episode = new FpcSocialEpisode(now, type, buildEpisodeSummary(type, detail));
		synchronized (episodes)
		{
			for (java.util.Iterator<FpcSocialEpisode> iterator = episodes.descendingIterator(); iterator.hasNext();)
			{
				final FpcSocialEpisode existing = iterator.next();
				if (existing == null)
				{
					continue;
				}
				if ((now - existing.getTimestampMs()) > minimumSpacingMs)
				{
					break;
				}
				if (existing.getType() == type)
				{
					return false;
				}
			}

			while (episodes.size() >= MAX_EPISODES_PER_PAIR)
			{
				episodes.removeFirst();
			}
			episodes.addLast(episode);
		}
		storeEpisode(speakerKey, playerKey, episode);
		if (type == FpcSocialEpisodeType.KILLED_ME)
		{
			_historicalKillHistoryByPair.put(pairKey, Boolean.TRUE);
		}
		return true;
	}

	public int countRecentEpisodes(String speakerName, String targetPlayerName, FpcSocialEpisodeType type, long withinMs)
	{
		if ((type == null) || (withinMs <= 0L) || (speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return 0;
		}

		final String pairKey = buildPairKey(speakerName, targetPlayerName);
		final String speakerKey = normalizeName(speakerName);
		final String playerKey = normalizeName(targetPlayerName);
		final Deque<FpcSocialEpisode> episodes = getOrLoadEpisodes(pairKey, speakerKey, playerKey);
		if ((episodes == null) || episodes.isEmpty())
		{
			return 0;
		}

		final long now = System.currentTimeMillis();
		int count = 0;
		synchronized (episodes)
		{
			for (java.util.Iterator<FpcSocialEpisode> iterator = episodes.descendingIterator(); iterator.hasNext();)
			{
				final FpcSocialEpisode episode = iterator.next();
				if (episode == null)
				{
					continue;
				}
				if ((now - episode.getTimestampMs()) > withinMs)
				{
					break;
				}
				if (episode.getType() == type)
				{
					count++;
				}
			}
		}
		return count;
	}

	public FpcSocialSnapshot getSnapshot(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return FpcSocialSnapshot.EMPTY;
		}

		final String pairKey = buildPairKey(speakerName, targetPlayerName);
		final String speakerKey = normalizeName(speakerName);
		final String playerKey = normalizeName(targetPlayerName);
		final Deque<FpcSocialEpisode> episodes = getOrLoadEpisodes(pairKey, speakerKey, playerKey);
		if ((episodes == null) || episodes.isEmpty())
		{
			return FpcSocialSnapshot.EMPTY;
		}

		synchronized (episodes)
		{
			if (episodes.isEmpty())
			{
				return FpcSocialSnapshot.EMPTY;
			}
			final boolean hasHistoricalKillHistory = hasHistoricalKillHistory(pairKey, speakerKey, playerKey);
			return buildSnapshot(targetPlayerName.trim(), episodes, hasHistoricalKillHistory);
		}
	}

	public String describeSocialMemory(String speakerName, String targetPlayerName)
	{
		final FpcSocialSnapshot snapshot = getSnapshot(speakerName, targetPlayerName);
		return snapshot.hasMeaningfulHistory() ? snapshot.getSummary() : "";
	}

	public void clearPairHistory(String speakerName, String targetPlayerName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return;
		}

		final String pairKey = buildPairKey(speakerName, targetPlayerName);
		final String speakerKey = normalizeName(speakerName);
		final String playerKey = normalizeName(targetPlayerName);
		_episodesByPair.remove(pairKey);
		_loadedPairs.remove(pairKey);
		_historicalKillHistoryByPair.remove(pairKey);

		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(DELETE_PAIR_EPISODES))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not clear social episodes for speakerKey=" + speakerKey + " playerKey=" + playerKey, e);
		}
	}

	private Deque<FpcSocialEpisode> getOrLoadEpisodes(String pairKey, String speakerKey, String playerKey)
	{
		final Deque<FpcSocialEpisode> episodes = _episodesByPair.computeIfAbsent(pairKey, unused -> new ArrayDeque<>());
		if (_loadedPairs.add(pairKey))
		{
			loadRecentEpisodes(episodes, speakerKey, playerKey);
		}
		return episodes;
	}

	private void loadRecentEpisodes(Deque<FpcSocialEpisode> episodes, String speakerKey, String playerKey)
	{
		final java.util.List<FpcSocialEpisode> loadedEpisodes = new java.util.ArrayList<>(MAX_EPISODES_PER_PAIR);
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(LOAD_RECENT_EPISODES))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.setInt(3, MAX_EPISODES_PER_PAIR);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final FpcSocialEpisodeType type = parseEpisodeType(result.getString("event_type"));
					if (type == null)
					{
						continue;
					}
					loadedEpisodes.add(new FpcSocialEpisode(result.getLong("created_at"), type, result.getString("summary")));
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not load social episodes for speakerKey=" + speakerKey + " playerKey=" + playerKey, e);
		}

		if (loadedEpisodes.isEmpty())
		{
			return;
		}

		synchronized (episodes)
		{
			for (int i = loadedEpisodes.size() - 1; i >= 0; i--)
			{
				episodes.addLast(loadedEpisodes.get(i));
			}
		}
	}

	private void storeEpisode(String speakerKey, String playerKey, FpcSocialEpisode episode)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(INSERT_EPISODE))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.setString(3, episode.getType().name());
			statement.setString(4, episode.getSummary());
			statement.setLong(5, episode.getTimestampMs());
			statement.execute();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not persist social episode for speakerKey=" + speakerKey + " playerKey=" + playerKey + " type=" + episode.getType(), e);
		}
	}

	private FpcSocialSnapshot buildSnapshot(String targetPlayerName, Deque<FpcSocialEpisode> episodes, boolean hasHistoricalKillHistory)
	{
		int pleasantChat = 0;
		int inviteRequests = 0;
		int acceptedInvites = 0;
		int declinedInvites = 0;
		int buffedMe = 0;
		int helpedMe = 0;
		int revivedMe = 0;
		int spammedRequests = 0;
		int insultedMe = 0;
		int threatenedMe = 0;
		int distrustedMe = 0;
		int attackedBeliefs = 0;
		int praisedMe = 0;
		int reassuredMe = 0;
		int abandonedMe = 0;
		int attackedMe = 0;
		int witnessedPk = 0;
		int killEvents = 0;
		double pleasantChatWeight = 0;
		double inviteRequestWeight = 0;
		double acceptedInviteWeight = 0;
		double declinedInviteWeight = 0;
		double buffedWeight = 0;
		double helpedWeight = 0;
		double revivedWeight = 0;
		double spammedWeight = 0;
		double insultedWeight = 0;
		double threatenedWeight = 0;
		double distrustedWeight = 0;
		double beliefAttackWeight = 0;
		double apologyWeight = 0;
		double thankedWeight = 0;
		double respectWeight = 0;
		double praiseWeight = 0;
		double affectionWeight = 0;
		double reassuranceWeight = 0;
		double expressedHateWeight = 0;
		double repairWeight = 0;
		double abandonmentWeight = 0;
		double attackedMeWeight = 0;
		double witnessedPkWeight = 0;
		double killWeight = 0;
		String latestSummary = "";
		final long now = System.currentTimeMillis();
		for (FpcSocialEpisode episode : episodes)
		{
			if ((episode == null) || (episode.getType() == null))
			{
				continue;
			}
			final double freshnessWeight = getFreshnessWeight(now, episode.getTimestampMs());

			if ((episode.getSummary() != null) && !episode.getSummary().isBlank())
			{
				latestSummary = episode.getSummary();
			}

			switch (episode.getType())
			{
				case PLEASANT_CHAT:
					pleasantChat++;
					pleasantChatWeight += (0.90 * freshnessWeight);
					break;
				case PARTY_INVITE_REQUESTED:
					inviteRequests++;
					inviteRequestWeight += (0.75 * freshnessWeight);
					break;
				case PARTY_INVITE_ACCEPTED:
					acceptedInvites++;
					acceptedInviteWeight += (1.15 * freshnessWeight);
					break;
				case PARTY_INVITE_DECLINED:
					declinedInvites++;
					declinedInviteWeight += (1.00 * freshnessWeight);
					break;
				case BUFFED_ME:
					buffedMe++;
					buffedWeight += (0.65 * freshnessWeight);
					break;
				case HELPED_ME:
					helpedMe++;
					helpedWeight += (1.00 * freshnessWeight);
					break;
				case REVIVED_ME:
					revivedMe++;
					revivedWeight += (1.35 * freshnessWeight);
					break;
				case SPAMMED_REQUESTS:
					spammedRequests++;
					spammedWeight += (1.10 * freshnessWeight);
					break;
				case INSULTED_ME:
					insultedMe++;
					insultedWeight += (1.05 * freshnessWeight);
					break;
				case THREATENED_ME:
					threatenedMe++;
					threatenedWeight += (1.35 * freshnessWeight);
					break;
				case DISTRUSTED_ME:
					distrustedMe++;
					distrustedWeight += (0.95 * freshnessWeight);
					break;
				case ATTACKED_BELIEFS:
					attackedBeliefs++;
					beliefAttackWeight += (1.20 * freshnessWeight);
					break;
				case APOLOGIZED:
					apologyWeight += (0.95 * freshnessWeight);
					break;
				case THANKED_ME:
					thankedWeight += (0.65 * freshnessWeight);
					break;
				case EXPRESSED_RESPECT:
					respectWeight += (0.95 * freshnessWeight);
					break;
				case PRAISED_ME:
					praisedMe++;
					praiseWeight += (0.80 * freshnessWeight);
					break;
				case EXPRESSED_AFFECTION:
					affectionWeight += (1.10 * freshnessWeight);
					break;
				case REASSURED_ME:
					reassuredMe++;
					reassuranceWeight += (1.05 * freshnessWeight);
					break;
				case EXPRESSED_HATE:
					expressedHateWeight += (1.05 * freshnessWeight);
					break;
				case SOUGHT_REPAIR:
					repairWeight += (1.00 * freshnessWeight);
					break;
				case ABANDONED_ME:
					abandonedMe++;
					abandonmentWeight += (1.10 * freshnessWeight);
					break;
				case ATTACKED_ME:
					attackedMe++;
					attackedMeWeight += (1.30 * freshnessWeight);
					break;
				case WITNESSED_PK:
					witnessedPk++;
					witnessedPkWeight += (1.05 * freshnessWeight);
					break;
				case KILLED_ME:
					killEvents++;
					killWeight += (1.25 * freshnessWeight);
					break;
			}
		}

		final int supportEvents = buffedMe + helpedMe + revivedMe;
		final boolean severeKillHistory = hasHistoricalKillHistory || (killEvents > 0);
		final boolean mixedPartyHistory = (acceptedInvites > 0) && (declinedInvites > 0);
		final boolean repeatedDeclines = (declinedInvites >= 2) && (acceptedInvites == 0);
		final double pleasantChatScore = saturate(pleasantChatWeight);
		final double requestScore = saturate(inviteRequestWeight);
		final double acceptedScore = saturate(acceptedInviteWeight);
		final double declinedScore = saturate(declinedInviteWeight);
		final double buffedScore = saturate(buffedWeight);
		final double helpedScore = saturate(helpedWeight);
		final double revivedScore = saturate(revivedWeight);
		final double spammedScore = saturate(spammedWeight);
		final double insultedScore = saturate(insultedWeight);
		final double threatenedScore = saturate(threatenedWeight);
		final double distrustedScore = saturate(distrustedWeight);
		final double beliefAttackScore = saturate(beliefAttackWeight);
		final double apologyScore = saturate(apologyWeight);
		final double thankedScore = saturate(thankedWeight);
		final double respectScore = saturate(respectWeight);
		final double praiseScore = saturate(praiseWeight);
		final double affectionScore = saturate(affectionWeight);
		final double reassuranceScore = saturate(reassuranceWeight);
		final double expressedHateScore = saturate(expressedHateWeight);
		final double repairScore = saturate(repairWeight);
		final double abandonmentScore = saturate(abandonmentWeight);
		final double attackedMeScore = saturate(attackedMeWeight);
		final double witnessedPkScore = saturate(witnessedPkWeight);
		final double killScore = saturate(killWeight);
		final double supportScore = (buffedScore * 0.6) + helpedScore + (revivedScore * 1.2);
		final double rawFrictionScore = declinedScore + (spammedScore * 1.25) + (insultedScore * 1.10) + (threatenedScore * 1.45) + (distrustedScore * 1.00) + (beliefAttackScore * 1.30) + (expressedHateScore * 1.20) + (abandonmentScore * 0.95) + (attackedMeScore * 1.60) + (witnessedPkScore * 0.85) + (mixedPartyHistory ? 0.35 : 0.0) + (repeatedDeclines ? 0.35 : 0.0);
		final double emotionalBreachScore = (threatenedScore * 1.20) + (beliefAttackScore * 1.05) + (distrustedScore * 0.80) + (expressedHateScore * 0.95) + (abandonmentScore * 1.10) + (attackedMeScore * 1.25) + (witnessedPkScore * 0.80) + (killScore * 1.35);
		final double repairContextBoost = Math.min(0.45, (rawFrictionScore * 0.15) + (emotionalBreachScore * 0.08));
		final double repairResistance = severeKillHistory ? 0.75 : Math.min(0.72, (threatenedScore * 0.24) + (abandonmentScore * 0.18) + (beliefAttackScore * 0.16) + (expressedHateScore * 0.12) + (attackedMeScore * 0.22) + (witnessedPkScore * 0.12));
		final double effectiveApologyScore = apologyScore * Math.max(0.18, Math.min(1.00, 0.60 + repairContextBoost - repairResistance));
		final double effectiveRepairScore = repairScore * Math.max(0.20, Math.min(1.05, 0.66 + repairContextBoost - repairResistance));
		final double effectiveReassuranceScore = reassuranceScore * Math.max(0.25, Math.min(1.00, 0.72 + (rawFrictionScore * 0.10) - (repairResistance * 0.75)));
		final double effectivePraiseScore = praiseScore * Math.max(0.30, Math.min(1.00, 0.82 - (repairResistance * 0.55)));
		final double effectiveRespectScore = respectScore * Math.max(0.35, Math.min(1.00, 0.86 - (repairResistance * 0.45)));
		final double effectiveAffectionScore = affectionScore * Math.max(0.25, Math.min(1.00, 0.86 - (repairResistance * 0.55)));
		final double warmthScore = (pleasantChatScore * 0.95) + (thankedScore * 0.45) + (effectiveRespectScore * 0.85) + (effectivePraiseScore * 0.70) + effectiveAffectionScore + (effectiveReassuranceScore * 0.95) + (effectiveRepairScore * 0.60) + (effectiveApologyScore * 0.45);
		final double familiarityScore = requestScore + acceptedScore + declinedScore + warmthScore;
		final double cooperationScore = (acceptedScore * 1.4) + supportScore + (pleasantChatScore * 0.35) + (effectiveRespectScore * 0.70) + (effectivePraiseScore * 0.55) + (effectiveReassuranceScore * 0.85) + (effectiveRepairScore * 0.45) + (effectiveApologyScore * 0.35) + (thankedScore * 0.20);
		final double frictionScore = rawFrictionScore - Math.min(0.95, effectiveApologyScore * 0.60) - Math.min(0.90, effectiveRepairScore * 0.55) - Math.min(0.65, effectiveReassuranceScore * 0.30);
		final boolean repeatedAcceptedTravel = (acceptedInvites >= 2) || (acceptedScore >= 1.8);
		final boolean freshDeclinePressure = declinedInviteWeight >= 0.90;
		final boolean freshSpamPressure = spammedWeight >= 0.95;
		final boolean freshInsultPressure = insultedWeight >= 0.90;
		final boolean freshThreatPressure = threatenedWeight >= 0.95;
		final boolean freshDistrustPressure = distrustedWeight >= 0.90;
		final boolean freshBeliefPressure = beliefAttackWeight >= 0.95;
		final boolean freshHatePressure = expressedHateWeight >= 0.90;
		final boolean freshAbandonmentPressure = abandonmentWeight >= 0.90;
		final boolean freshAttackPressure = attackedMeWeight >= 0.95;
		final boolean freshWitnessedPkPressure = witnessedPkWeight >= 0.90;
		final boolean mixedHistoryGuardedPressure = mixedPartyHistory && (freshDeclinePressure || freshSpamPressure || (frictionScore >= 1.10));
		final boolean abandonmentPattern = (abandonedMe >= 2) || ((abandonedMe >= 1) && ((distrustedMe >= 1) || (insultedMe >= 1) || (attackedBeliefs >= 1) || (expressedHateScore >= 0.95) || (abandonmentScore >= 1.35)));
		final boolean repeatedSocialAbuse = (insultedMe >= 2) || (threatenedMe >= 1) || (distrustedMe >= 2) || (attackedBeliefs >= 2) || (attackedMe >= 1) || abandonmentPattern || (insultedScore >= 1.45) || (threatenedScore >= 1.10) || (distrustedScore >= 1.35) || (beliefAttackScore >= 1.55) || (expressedHateScore >= 1.35) || (abandonmentScore >= 1.45) || (attackedMeScore >= 1.00);
		final int trustBias = clampBias(Math.min(4.0, cooperationScore + Math.min(0.75, requestScore * 0.25)), 4);
		final int guardBias = clampBias(Math.min(5.0, (severeKillHistory ? 4.0 : 0.0) + frictionScore + (mixedHistoryGuardedPressure ? 0.75 : 0.0) + (freshInsultPressure ? 0.35 : 0.0) + (freshThreatPressure ? 0.55 : 0.0) + (freshDistrustPressure ? 0.30 : 0.0) + (freshBeliefPressure ? 0.45 : 0.0) + (freshHatePressure ? 0.40 : 0.0) + (freshAbandonmentPressure ? 0.30 : 0.0) + (freshAttackPressure ? 0.80 : 0.0) + (freshWitnessedPkPressure ? 0.45 : 0.0) + Math.min(0.75, killScore * 0.4)), 5);
		final String socialLabel;
		if (severeKillHistory)
		{
			socialLabel = "hostile";
		}
		else if ((beliefAttackScore >= 2.1) || (threatenedScore >= 1.10) || (attackedMeScore >= 1.00) || (repeatedSocialAbuse && (cooperationScore < 1.35)))
		{
			socialLabel = "hostile";
		}
		else if ((frictionScore >= 1.6) || mixedHistoryGuardedPressure || (witnessedPkScore >= 1.10) || (mixedPartyHistory && (cooperationScore < 3.2)) || (repeatedDeclines && (cooperationScore < 2.4)) || (spammedScore >= 1.35))
		{
			socialLabel = "guarded";
		}
		else if ((cooperationScore >= 3.25) || ((acceptedScore >= 1.25) && (supportScore >= 1.1)) || ((revivedScore >= 0.9) && (supportScore >= 1.4)) || (repeatedAcceptedTravel && (supportScore >= 0.75)))
		{
			socialLabel = "trusted";
		}
		else if ((cooperationScore >= 1.05) || (supportScore >= 0.85) || (acceptedScore >= 0.95) || (respectScore >= 0.95) || (praiseScore >= 0.85) || (reassuranceScore >= 0.90) || (affectionScore >= 0.90))
		{
			socialLabel = "friendly";
		}
		else if (familiarityScore >= 1.10)
		{
			socialLabel = "familiar";
		}
		else
		{
			socialLabel = "neutral";
		}

		final boolean refuseParty = severeKillHistory || repeatedSocialAbuse;
		final boolean refuseLead = severeKillHistory || repeatedSocialAbuse || (frictionScore >= 2.75) || ((mixedPartyHistory || repeatedDeclines) && (cooperationScore < 2.0)) || (spammedScore >= 1.25);
		final boolean partyOk = !severeKillHistory && !repeatedSocialAbuse && (cooperationScore >= 2.2) && (frictionScore < 1.25) && !mixedPartyHistory && (spammedScore < 0.75);
		final boolean partyGuarded = !severeKillHistory && !partyOk && (mixedHistoryGuardedPressure || freshInsultPressure || freshBeliefPressure || (familiarityScore >= 1.10) || (supportScore >= 0.85) || (frictionScore >= 0.75));
		final boolean rumorShareOk = !severeKillHistory && !repeatedSocialAbuse && ((cooperationScore >= 1.25) || ((requestScore >= 1.0) && (declinedScore < 0.75))) && (spammedScore < 1.10);
		final boolean avoidPlayer = severeKillHistory || (beliefAttackScore >= 1.8) || (threatenedScore >= 1.10) || (attackedMeScore >= 0.95) || (witnessedPkScore >= 1.00);
		final FpcSocialActionStance actionStance = new FpcSocialActionStance(partyOk, partyGuarded, refuseParty, refuseLead, rumorShareOk, avoidPlayer);
		final String summary = buildSummary(targetPlayerName, socialLabel, actionStance, pleasantChat, inviteRequests, acceptedInvites, declinedInvites, buffedMe, helpedMe, revivedMe, spammedRequests, insultedMe, threatenedMe, distrustedMe, attackedBeliefs, apologyScore > 0.0, thankedScore > 0.0, respectScore > 0.0, praiseScore > 0.0, affectionScore > 0.0, reassuranceScore > 0.0, expressedHateScore > 0.0, repairScore > 0.0, abandonmentScore > 0.0, attackedMe, witnessedPk, severeKillHistory ? Math.max(1, killEvents) : killEvents, latestSummary);
		return new FpcSocialSnapshot(socialLabel, summary, trustBias, guardBias, actionStance, severeKillHistory);
	}

	private double getFreshnessWeight(long now, long timestampMs)
	{
		final long ageMs = Math.max(0L, now - timestampMs);
		if (ageMs <= FRESH_WINDOW_MS)
		{
			return 1.00;
		}
		if (ageMs <= RECENT_WINDOW_MS)
		{
			return 0.90;
		}
		if (ageMs <= SAME_DAY_WINDOW_MS)
		{
			return 0.75;
		}
		if (ageMs <= OLDER_WINDOW_MS)
		{
			return 0.60;
		}
		return 0.45;
	}

	private double saturate(double value)
	{
		return (value <= 0.0) ? 0.0 : Math.sqrt(value);
	}

	private int clampBias(double value, int max)
	{
		return Math.max(0, Math.min(max, (int) Math.round(value)));
	}

	private boolean hasHistoricalKillHistory(String pairKey, String speakerKey, String playerKey)
	{
		return _historicalKillHistoryByPair.computeIfAbsent(pairKey, unused -> hasEpisodeType(speakerKey, playerKey, FpcSocialEpisodeType.KILLED_ME));
	}

	private boolean hasEpisodeType(String speakerKey, String playerKey, FpcSocialEpisodeType type)
	{
		if ((type == null) || (speakerKey == null) || speakerKey.isBlank() || (playerKey == null) || playerKey.isBlank())
		{
			return false;
		}

		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement(HAS_EPISODE_TYPE))
		{
			statement.setString(1, speakerKey);
			statement.setString(2, playerKey);
			statement.setString(3, type.name());
			try (ResultSet result = statement.executeQuery())
			{
				return result.next();
			}
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not query historical social episode type for speakerKey=" + speakerKey + " playerKey=" + playerKey + " type=" + type, e);
			return false;
		}
	}

	private String buildSummary(String targetPlayerName, String socialLabel, FpcSocialActionStance actionStance, int pleasantChat, int inviteRequests, int acceptedInvites, int declinedInvites, int buffedMe, int helpedMe, int revivedMe, int spammedRequests, int insultedMe, int threatenedMe, int distrustedMe, int attackedBeliefs, boolean apologized, boolean thanked, boolean respected, boolean praised, boolean affection, boolean reassured, boolean expressedHate, boolean soughtRepair, boolean abandoned, int attackedMe, int witnessedPk, int killEvents, String latestSummary)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Social memory with ").append(targetPlayerName).append(": ");
		final List<String> beliefs = buildBeliefs(pleasantChat, inviteRequests, acceptedInvites, declinedInvites, buffedMe, helpedMe, revivedMe, spammedRequests, insultedMe, threatenedMe, distrustedMe, attackedBeliefs, apologized, thanked, respected, praised, affection, reassured, expressedHate, soughtRepair, abandoned, attackedMe, witnessedPk, killEvents);
		if (!beliefs.isEmpty())
		{
			sb.append("Beliefs: ").append(String.join("; ", beliefs)).append(". ");
		}
		sb.append("Current social label: ").append(socialLabel).append(". ");
		if (actionStance.hasSignals())
		{
			sb.append("Action stance: ").append(actionStance.describe()).append(". ");
		}
		if ((latestSummary != null) && !latestSummary.isBlank())
		{
			sb.append("Latest episode: ").append(latestSummary).append(".");
		}
		return sb.toString().trim();
	}

	private List<String> buildBeliefs(int pleasantChat, int inviteRequests, int acceptedInvites, int declinedInvites, int buffedMe, int helpedMe, int revivedMe, int spammedRequests, int insultedMe, int threatenedMe, int distrustedMe, int attackedBeliefs, boolean apologized, boolean thanked, boolean respected, boolean praised, boolean affection, boolean reassured, boolean expressedHate, boolean soughtRepair, boolean abandoned, int attackedMe, int witnessedPk, int killEvents)
	{
		final java.util.ArrayList<String> beliefs = new java.util.ArrayList<>(4);
		if (killEvents > 0)
		{
			beliefs.add("views this player as personally dangerous");
		}
		if (attackedMe > 0)
		{
			beliefs.add("remembers this player striking them directly");
		}
		if (witnessedPk > 0)
		{
			beliefs.add("remembers this player spilling blood nearby");
		}
		if (attackedBeliefs > 0)
		{
			beliefs.add("bristles when this player pushes against something they hold sacred");
		}
		if (insultedMe > 0)
		{
			beliefs.add("remembers disrespect and a sharper tongue than they welcome");
		}
		if (threatenedMe > 0)
		{
			beliefs.add("treats the player's threats as something to take seriously");
		}
		if (distrustedMe > 0)
		{
			beliefs.add("remembers open suspicion and doubts about their honesty");
		}
		if (expressedHate)
		{
			beliefs.add("remembers open distrust or rejection in the player's tone");
		}
		if (abandoned)
		{
			beliefs.add("remembers language of leaving, absence, or being left alone");
		}
		if (revivedMe > 0)
		{
			beliefs.add("remembers being pulled back to their feet when it mattered");
		}
		else if (helpedMe > 0)
		{
			beliefs.add("has stepped in with real support before");
		}
		if (buffedMe > 0)
		{
			beliefs.add("has reinforced them with support magic");
		}
		if (pleasantChat >= 2)
		{
			beliefs.add("remembers easy conversation and steadier company with this player");
		}
		else if (pleasantChat == 1)
		{
			beliefs.add("remembers a calm conversation with this player");
		}
		if (apologized)
		{
			beliefs.add("has heard this player try to own a mistake instead of pushing harder");
		}
		if (thanked)
		{
			beliefs.add("has heard gratitude directed at them personally");
		}
		if (respected)
		{
			beliefs.add("has heard this player speak with direct respect");
		}
		if (praised)
		{
			beliefs.add("has heard this player offer direct praise instead of guarded flattery");
		}
		if (affection)
		{
			beliefs.add("has felt direct warmth from this player, not just convenience");
		}
		if (reassured)
		{
			beliefs.add("has heard the player promise steadiness or safety instead of drifting away");
		}
		if (soughtRepair)
		{
			beliefs.add("has heard this player ask to mend the bond instead of winning the moment");
		}
		if (acceptedInvites >= 2)
		{
			beliefs.add("expects steady company after repeated successful travel");
		}
		else if (acceptedInvites == 1)
		{
			beliefs.add("has traveled successfully with this player before");
		}
		if ((acceptedInvites > 0) && (declinedInvites > 0))
		{
			beliefs.add("remembers uneven party discipline");
		}
		else if ((declinedInvites >= 2) && (acceptedInvites == 0))
		{
			beliefs.add("keeps this player at arm's length for party requests");
		}
		else if (spammedRequests > 0)
		{
			beliefs.add("keeps feeling pressure after refusal");
		}
		else if ((inviteRequests >= 2) && (acceptedInvites == 0))
		{
			beliefs.add("recognizes repeated attempts to party up");
		}
		else if ((inviteRequests + acceptedInvites + declinedInvites) > 0)
		{
			beliefs.add("recognizes this player from recent contact");
		}
		return beliefs;
	}

	private String buildEpisodeSummary(FpcSocialEpisodeType type, String detail)
	{
		switch (type)
		{
			case PLEASANT_CHAT:
				return (detail == null) || detail.isBlank() ? "conversation felt easy and welcome" : detail;
			case PARTY_INVITE_REQUESTED:
				return "player asked to travel together";
			case PARTY_INVITE_ACCEPTED:
				return "party travel was accepted";
			case PARTY_INVITE_DECLINED:
				return "party travel was declined";
			case BUFFED_ME:
				return (detail == null) || detail.isBlank() ? "player reinforced the FPC with support magic" : detail;
			case HELPED_ME:
				return (detail == null) || detail.isBlank() ? "player restored the FPC during danger" : detail;
			case REVIVED_ME:
				return (detail == null) || detail.isBlank() ? "player brought the FPC back from death" : detail;
			case SPAMMED_REQUESTS:
				return (detail == null) || detail.isBlank() ? "player kept pressing after refusal" : detail;
			case INSULTED_ME:
				return (detail == null) || detail.isBlank() ? "player spoke with open disrespect" : detail;
			case THREATENED_ME:
				return (detail == null) || detail.isBlank() ? "player threatened the FPC directly" : detail;
			case DISTRUSTED_ME:
				return (detail == null) || detail.isBlank() ? "player spoke with open suspicion or distrust" : detail;
			case ATTACKED_BELIEFS:
				return (detail == null) || detail.isBlank() ? "player attacked something the FPC holds sacred" : detail;
			case APOLOGIZED:
				return (detail == null) || detail.isBlank() ? "player apologized after tension" : detail;
			case THANKED_ME:
				return (detail == null) || detail.isBlank() ? "player expressed gratitude directly" : detail;
			case EXPRESSED_RESPECT:
				return (detail == null) || detail.isBlank() ? "player spoke with direct respect" : detail;
			case PRAISED_ME:
				return (detail == null) || detail.isBlank() ? "player praised the FPC directly" : detail;
			case EXPRESSED_AFFECTION:
				return (detail == null) || detail.isBlank() ? "player spoke with direct warmth or affection" : detail;
			case REASSURED_ME:
				return (detail == null) || detail.isBlank() ? "player offered reassurance or promised steadiness" : detail;
			case EXPRESSED_HATE:
				return (detail == null) || detail.isBlank() ? "player spoke with open distrust or rejection" : detail;
			case SOUGHT_REPAIR:
				return (detail == null) || detail.isBlank() ? "player asked to repair the bond or start over" : detail;
			case ABANDONED_ME:
				return (detail == null) || detail.isBlank() ? "player spoke about leaving or abandoning the bond" : detail;
			case ATTACKED_ME:
				return (detail == null) || detail.isBlank() ? "player attacked the FPC directly" : detail;
			case WITNESSED_PK:
				return (detail == null) || detail.isBlank() ? "player murdered someone nearby" : detail;
			case KILLED_ME:
				return (detail == null) || detail.isBlank() ? "player killed the FPC" : detail;
			default:
				return "";
		}
	}

	private String buildPairKey(String speakerName, String targetPlayerName)
	{
		return normalizeName(speakerName) + "|" + normalizeName(targetPlayerName);
	}

	private FpcSocialEpisodeType parseEpisodeType(String value)
	{
		if ((value == null) || value.isBlank())
		{
			return null;
		}

		try
		{
			return FpcSocialEpisodeType.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
		}
		catch (IllegalArgumentException e)
		{
			LOGGER.warning("Skipping unknown social episode type: " + value);
			return null;
		}
	}

	private String normalizeName(String value)
	{
		return (value == null) ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}
}
