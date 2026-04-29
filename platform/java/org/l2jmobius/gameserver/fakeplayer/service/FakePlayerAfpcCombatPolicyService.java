package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Locale;
import java.util.Set;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcPersona;
import org.l2jmobius.gameserver.fakeplayer.model.enums.FpcSocialEpisodeType;
import org.l2jmobius.gameserver.model.actor.Player;

public class FakePlayerAfpcCombatPolicyService
{
	public enum Trigger
	{
		DIRECT_ATTACK,
		WITNESSED_PK,
		HOSTILE_PK_NEARBY,
		CLAN_MEMBER_UNDER_ATTACK,
		CLAN_MEMBER_PVP,
		CLAN_MEMBER_KILLED,
		PARTY_FRIEND_UNDER_ATTACK,
		PARTY_FRIEND_INITIATED_PVP,
		PARTY_FRIEND_KILLED
	}

	public static final class Decision
	{
		public static final Decision DENY = new Decision(false, false, 0, 0, 0L, 0, "deny");

		private final boolean _engage;
		private final boolean _allowPkEscalation;
		private final int _pressureScore;
		private final int _personalityAggression;
		private final long _memoryMs;
		private final int _leashRange;
		private final String _reason;

		public Decision(boolean engage, boolean allowPkEscalation, int pressureScore, int personalityAggression, long memoryMs, int leashRange, String reason)
		{
			_engage = engage;
			_allowPkEscalation = allowPkEscalation;
			_pressureScore = pressureScore;
			_personalityAggression = personalityAggression;
			_memoryMs = Math.max(memoryMs, 0L);
			_leashRange = Math.max(leashRange, 0);
			_reason = (reason == null) ? "" : reason;
		}

		public boolean isEngage()
		{
			return _engage;
		}

		public boolean isAllowPkEscalation()
		{
			return _allowPkEscalation;
		}

		public int getPressureScore()
		{
			return _pressureScore;
		}

		public int getPersonalityAggression()
		{
			return _personalityAggression;
		}

		public long getMemoryMs()
		{
			return _memoryMs;
		}

		public int getLeashRange()
		{
			return _leashRange;
		}

		public String getReason()
		{
			return _reason;
		}
	}

	private static final Set<String> POSITIVE_SOCIAL_LABELS = Set.of("trusted", "friendly", "warm", "open");

	private static final class PairCombatContext
	{
		private final FpcRelationshipSnapshot _relationship;
		private final FpcSocialSnapshot _social;
		private final int _recentAttackedMe;
		private final int _recentWitnessedPk;
		private final int _recentKilledMe;
		private final int _personalityAggression;

		private PairCombatContext(FpcRelationshipSnapshot relationship, FpcSocialSnapshot social, int recentAttackedMe, int recentWitnessedPk, int recentKilledMe, int personalityAggression)
		{
			_relationship = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
			_social = (social == null) ? FpcSocialSnapshot.EMPTY : social;
			_recentAttackedMe = Math.max(recentAttackedMe, 0);
			_recentWitnessedPk = Math.max(recentWitnessedPk, 0);
			_recentKilledMe = Math.max(recentKilledMe, 0);
			_personalityAggression = personalityAggression;
		}
	}

	private final FakePlayerConfig _config;
	private final FakePlayerPersonaResolver _personaResolver;
	private final FakePlayerChatService _chatService;
	private final FakePlayerSocialMemoryService _socialMemoryService;

	public FakePlayerAfpcCombatPolicyService(FakePlayerConfig config, FakePlayerPersonaResolver personaResolver, FakePlayerChatService chatService, FakePlayerSocialMemoryService socialMemoryService)
	{
		_config = config;
		_personaResolver = personaResolver;
		_chatService = chatService;
		_socialMemoryService = socialMemoryService;
	}

	public boolean isGoodFriend(FpcDefinition definition, Player player)
	{
		final PairCombatContext context = buildPairContext(definition, player);
		if (context == null)
		{
			return false;
		}
		if ((context._relationship.getPlayerKillCount() > 0) || context._social.hasHistoricalKillHistory() || context._social.getActionStance().isAvoidPlayer())
		{
			return false;
		}

		final boolean relationshipStrong = (context._relationship.getFamiliarity() >= 6) && (context._relationship.getTrust() >= 6) && (context._relationship.getRespect() >= 5) && (context._relationship.getTension() <= 2) && (context._relationship.getResentment() <= 1);
		final String socialLabel = normalize(context._social.getSocialLabel());
		final boolean socialStrong = (POSITIVE_SOCIAL_LABELS.contains(socialLabel) || (context._social.getTrustBias() >= 3)) && (context._social.getGuardBias() <= 2);
		return relationshipStrong || ((context._relationship.getTrust() >= 7) && (context._relationship.getRespect() >= 6) && socialStrong);
	}

	public Decision evaluate(FpcDefinition definition, Player targetPlayer, Trigger trigger)
	{
		if ((definition == null) || (targetPlayer == null) || (trigger == null))
		{
			return Decision.DENY;
		}

		return switch (trigger)
		{
			case DIRECT_ATTACK -> evaluateSelfDefense(definition, targetPlayer);
			case WITNESSED_PK -> evaluateWitnessedKill(definition, targetPlayer, false, false);
			case HOSTILE_PK_NEARBY -> evaluateHostilePkNearby(definition, targetPlayer);
			case CLAN_MEMBER_UNDER_ATTACK -> evaluateProtectedAllyAttack(definition, targetPlayer, true, false);
			case CLAN_MEMBER_PVP -> evaluateProtectedAllyOffense(definition, targetPlayer, true, false);
			case CLAN_MEMBER_KILLED -> evaluateWitnessedKill(definition, targetPlayer, true, false);
			case PARTY_FRIEND_UNDER_ATTACK -> evaluateProtectedAllyAttack(definition, targetPlayer, false, true);
			case PARTY_FRIEND_INITIATED_PVP -> evaluateProtectedAllyOffense(definition, targetPlayer, false, true);
			case PARTY_FRIEND_KILLED -> evaluateWitnessedKill(definition, targetPlayer, false, true);
		};
	}

	private Decision evaluateSelfDefense(FpcDefinition definition, Player aggressor)
	{
		final PairCombatContext context = buildPairContext(definition, aggressor);
		if (context == null)
		{
			return Decision.DENY;
		}

		int score = context._personalityAggression;
		score += (context._recentAttackedMe * 3);
		score += (context._recentKilledMe * 5);
		score += context._relationship.getTension();
		score += (context._relationship.getResentment() * 2);
		score += (context._social.getGuardBias() * 2);
		score += context._social.hasHistoricalKillHistory() ? 5 : 0;
		score -= Math.max(0, context._relationship.getTrust() - 2);
		score -= Math.max(0, context._social.getTrustBias() - 1);

		final boolean hardHostility = isHatedTarget(context) || (context._recentAttackedMe >= 2) || (context._recentKilledMe > 0);
		final boolean calmHold = (context._personalityAggression <= 0) && (context._recentAttackedMe <= 1) && !hardHostility;
		final boolean allowEngage = hardHostility || (!calmHold && (score >= 5));
		if (!allowEngage)
		{
			return new Decision(false, false, score, context._personalityAggression, 0L, 0, "attacked_me_hold");
		}

		final boolean allowPkEscalation = (context._recentKilledMe > 0)
			|| (isHatedTarget(context) && (context._recentAttackedMe >= 2))
			|| (hasStrongPersonalHostility(context) && (context._recentAttackedMe >= 1));
		return new Decision(true, allowPkEscalation, score, context._personalityAggression, severeMemoryWindow(_config.getPersonalityRetaliationMemoryMs(), hardHostility || allowPkEscalation), _config.getPersonalityConflictEngageRange(), "attacked_me");
	}
	
	private Decision evaluateHostilePkNearby(FpcDefinition definition, Player target)
	{
		final PairCombatContext context = buildPairContext(definition, target);
		if (context == null)
		{
			return Decision.DENY;
		}
		
		int score = context._personalityAggression;
		score += context._relationship.getTension();
		score += (context._relationship.getResentment() * 2);
		score += (context._social.getGuardBias() * 2);
		score += context._recentAttackedMe * 2;
		score += context._recentKilledMe * 4;
		score += (target.getReputation() < 0) ? 8 : 0;
		score += (target.getPvpFlag() > 0) ? 3 : 0;
		score += isHatedTarget(context) ? 5 : 0;
		
		final boolean chaoticTarget = target.getReputation() < 0;
		final boolean alreadyPersonalEnemy = hasStrongPersonalHostility(context) || isHatedTarget(context);
		if (!chaoticTarget || !alreadyPersonalEnemy)
		{
			return new Decision(false, false, score, context._personalityAggression, 0L, 0, "hostile_pk_nearby_hold");
		}
		
		return new Decision(true, true, score, context._personalityAggression, severeMemoryWindow(_config.getPersonalityPkWitnessMemoryMs(), true), _config.getPersonalityConflictEngageRange(), "hostile_pk_nearby");
	}

	private Decision evaluateWitnessedKill(FpcDefinition definition, Player killer, boolean clanProtected, boolean partyFriendProtected)
	{
		final PairCombatContext context = buildPairContext(definition, killer);
		if (context == null)
		{
			return Decision.DENY;
		}

		int score = context._personalityAggression;
		score += (context._recentWitnessedPk * 3);
		score += (context._recentKilledMe * 4);
		score += context._relationship.getTension();
		score += (context._relationship.getResentment() * 2);
		score += (context._social.getGuardBias() * 2);
		score += context._social.hasHistoricalKillHistory() ? 4 : 0;
		score += (killer.getReputation() < 0) ? 4 : 0;
		score += clanProtected ? 8 : 0;
		score += partyFriendProtected ? 6 : 0;
		score += isHatedTarget(context) ? 4 : 0;

		final boolean alliedPressure = clanProtected || partyFriendProtected;
		final boolean allowEngage = alliedPressure || (killer.getReputation() < 0) || (context._recentWitnessedPk >= 2) || (score >= 8);
		if (!allowEngage)
		{
			return new Decision(false, false, score, context._personalityAggression, 0L, 0, "witnessed_pk_hold");
		}

		final boolean allowPkEscalation = alliedPressure && ((killer.getReputation() < 0) || isHatedTarget(context)) && (score >= 10);
		final String reason = clanProtected ? "clan_member_killed" : partyFriendProtected ? "party_friend_killed" : "witnessed_pk";
		final long memoryMs = severeMemoryWindow(clanProtected ? _config.getPersonalityClanAssistMemoryMs() : _config.getPersonalityPkWitnessMemoryMs(), alliedPressure || allowPkEscalation);
		final int leashRange = alliedPressure ? _config.getPersonalityAllyAssistRange() : _config.getPersonalityConflictEngageRange();
		return new Decision(true, allowPkEscalation, score, context._personalityAggression, memoryMs, leashRange, reason);
	}

	private Decision evaluateProtectedAllyAttack(FpcDefinition definition, Player attacker, boolean clanProtected, boolean partyFriendProtected)
	{
		if (!clanProtected && !partyFriendProtected)
		{
			return Decision.DENY;
		}

		final PairCombatContext context = buildPairContext(definition, attacker);
		if (context == null)
		{
			return Decision.DENY;
		}

		int score = context._personalityAggression;
		score += context._relationship.getTension();
		score += (context._relationship.getResentment() * 2);
		score += (context._social.getGuardBias() * 2);
		score += clanProtected ? 8 : 0;
		score += partyFriendProtected ? 6 : 0;
		score += isHatedTarget(context) ? 4 : 0;
		score += (attacker.getPvpFlag() > 0) ? 2 : 0;
		score += (attacker.getReputation() < 0) ? 3 : 0;

		final boolean allowPkEscalation = clanProtected || (attacker.getReputation() < 0) || isHatedTarget(context);
		final String reason = clanProtected ? "clan_member_under_attack" : "party_friend_under_attack";
		final long memoryMs = severeMemoryWindow(clanProtected ? _config.getPersonalityClanAssistMemoryMs() : Math.max(_config.getPersonalityRetaliationMemoryMs(), _config.getPersonalityPkWitnessMemoryMs()), clanProtected || allowPkEscalation);
		return new Decision(true, allowPkEscalation, score, context._personalityAggression, memoryMs, _config.getPersonalityAllyAssistRange(), reason);
	}

	private Decision evaluateProtectedAllyOffense(FpcDefinition definition, Player target, boolean clanProtected, boolean partyFriendProtected)
	{
		if (!clanProtected && !partyFriendProtected)
		{
			return Decision.DENY;
		}

		final PairCombatContext context = buildPairContext(definition, target);
		if (context == null)
		{
			return Decision.DENY;
		}

		int score = context._personalityAggression;
		score += context._relationship.getTension();
		score += (context._relationship.getResentment() * 2);
		score += (context._social.getGuardBias() * 2);
		score += clanProtected ? 7 : 0;
		score += partyFriendProtected ? 5 : 0;
		score += isHatedTarget(context) ? 6 : 0;
		score += (target.getPvpFlag() > 0) ? 2 : 0;
		score += (target.getReputation() < 0) ? 4 : 0;

		final boolean allowEngage = clanProtected || (partyFriendProtected && isHatedTarget(context));
		if (!allowEngage)
		{
			return new Decision(false, false, score, context._personalityAggression, 0L, 0, "party_friend_initiated_pvp_hold");
		}

		final boolean allowPkEscalation = clanProtected || (isHatedTarget(context) && ((target.getReputation() < 0) || (target.getPvpFlag() > 0) || (context._recentWitnessedPk > 0) || (context._recentKilledMe > 0)));
		final String reason = clanProtected ? "clan_member_pvp" : "party_friend_initiated_pvp";
		final long memoryMs = severeMemoryWindow(clanProtected ? _config.getPersonalityClanAssistMemoryMs() : Math.max(_config.getPersonalityRetaliationMemoryMs(), _config.getPersonalityPkWitnessMemoryMs()), clanProtected || allowPkEscalation);
		return new Decision(true, allowPkEscalation, score, context._personalityAggression, memoryMs, _config.getPersonalityAllyAssistRange(), reason);
	}

	private PairCombatContext buildPairContext(FpcDefinition definition, Player targetPlayer)
	{
		if ((definition == null) || (targetPlayer == null))
		{
			return null;
		}

		final String speakerName = definition.getName();
		final String targetName = targetPlayer.getName();
		final FpcRelationshipSnapshot relationship = _chatService.getRelationshipSnapshot(speakerName, targetName);
		final FpcSocialSnapshot social = _socialMemoryService.getSnapshot(speakerName, targetName);
		final int recentAttackedMe = _socialMemoryService.countRecentEpisodes(speakerName, targetName, FpcSocialEpisodeType.ATTACKED_ME, Math.max(_config.getPersonalityRetaliationMemoryMs(), 60000L));
		final int recentWitnessedPk = _socialMemoryService.countRecentEpisodes(speakerName, targetName, FpcSocialEpisodeType.WITNESSED_PK, Math.max(_config.getPersonalityPkWitnessMemoryMs(), 60000L));
		final int recentKilledMe = _socialMemoryService.countRecentEpisodes(speakerName, targetName, FpcSocialEpisodeType.KILLED_ME, Math.max(_config.getPersonalityPkWitnessMemoryMs(), 60000L));
		return new PairCombatContext(relationship, social, recentAttackedMe, recentWitnessedPk, recentKilledMe, resolvePersonalityAggressionScore(definition));
	}

	private boolean isHatedTarget(PairCombatContext context)
	{
		if (context == null)
		{
			return false;
		}

		return (context._relationship.getPlayerKillCount() > 0)
			|| context._social.hasHistoricalKillHistory()
			|| (context._recentAttackedMe >= 2)
			|| (context._recentWitnessedPk >= 2)
			|| (context._relationship.getResentment() >= 7)
			|| ((context._relationship.getResentment() >= 5) && (context._relationship.getTension() >= 6))
			|| (context._social.getActionStance().isAvoidPlayer() && (context._social.getGuardBias() >= 3))
			|| ("hostile".equalsIgnoreCase(context._social.getSocialLabel()) && (context._social.getGuardBias() >= 4));
	}
	
	private boolean hasStrongPersonalHostility(PairCombatContext context)
	{
		if (context == null)
		{
			return false;
		}
		
		return (context._relationship.getPlayerKillCount() > 0)
			|| context._social.hasHistoricalKillHistory()
			|| ((context._relationship.getResentment() >= 7) && (context._relationship.getTension() >= 6))
			|| ("hostile".equalsIgnoreCase(context._social.getSocialLabel()) && (context._social.getGuardBias() >= 4))
			|| (context._social.getActionStance().isAvoidPlayer() && (context._social.getGuardBias() >= 3));
	}

	private int resolvePersonalityAggressionScore(FpcDefinition definition)
	{
		if (definition == null)
		{
			return 0;
		}

		final ResolvedFpcPersona persona = _personaResolver.resolve(definition.getId(), definition.getArchetype());
		final String combatText = normalize(String.join(" ",
			persona.getSelfKnowledgeSummary(),
			persona.getPublicMaskSummary(),
			persona.getSelfConcept(),
			persona.getLoyaltyAnchor(),
			persona.getResentmentAnchor(),
			persona.getPrivateContradiction(),
			persona.getDefaultInnerState(),
			persona.getLongTermGoal(),
			persona.getRepairStyle(),
			persona.getConflictStyle(),
			persona.getReflectionLens(),
			persona.getActiveObjective(),
			persona.getBehaviorFamilyId(),
			persona.getBehaviorSummary()));
		int aggressionScore = 0;
		aggressionScore += countMarkers(combatText, "punish hesitation", "predatory", "dominant", "command", "storm", "front line", "front line breaker", "war cantor", "pressure", "discipline", "cowards", "cowardice", "pack", "nerve", "fight beside", "sharp momentum", "living threat", "knife line", "surgical", "sharp instincts", "clean exits");
		aggressionScore += countMarkers(combatText, "resents panic", "resents cowards", "resents drift", "sort the useful from the noisy", "test resolve", "keep the field electric", "difference between command and drift", "keep the lane tight", "hold the risk alone", "worth trusting at speed", "end a problem before it spreads", "broken pace", "slights");
		aggressionScore += countMarkers(combatText, "blunt", "commanding", "contemptuous", "predatory", "measured authority", "clean correction", "shock the room awake", "ruthless", "quiet precise", "cut a room cleaner");
		aggressionScore -= countMarkers(combatText, "lower the temperature", "avoid escalation", "keep the party s rhythm healthy", "soften wasteful panic", "calm", "restore the rhythm", "retreat sound like wisdom", "support line", "cautious helper", "steady wayfarer");
		return aggressionScore;
	}

	private int countMarkers(String normalizedText, String... markers)
	{
		if ((normalizedText == null) || normalizedText.isBlank() || (markers == null))
		{
			return 0;
		}

		int score = 0;
		for (String marker : markers)
		{
			final String normalizedMarker = normalize(marker);
			if (!normalizedMarker.isBlank() && normalizedText.contains(normalizedMarker))
			{
				score++;
			}
		}
		return score;
	}

	private long severeMemoryWindow(long baseMemoryMs, boolean severe)
	{
		final long resolvedBase = Math.max(baseMemoryMs, 10000L);
		return severe ? (resolvedBase * 2) : resolvedBase;
	}

	private String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}
}
