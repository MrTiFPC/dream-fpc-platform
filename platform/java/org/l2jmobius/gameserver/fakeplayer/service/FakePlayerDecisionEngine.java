package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.FakePlayerModule;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerReplySettingsData;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerAdvisoryPlan;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcMemoryBundle;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerRuntimeSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgeSelection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplyBankSelection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRouteProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcPersona;
import org.l2jmobius.gameserver.fakeplayer.personality.LocalPersonalityAdapter;
import org.l2jmobius.gameserver.fakeplayer.personality.PersonalityContext;
import org.l2jmobius.gameserver.fakeplayer.personality.PersonalityDecision;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Draft decision engine that converts runtime state into advisory personality context.
 */
public class FakePlayerDecisionEngine
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerDecisionEngine.class.getName());
	private static final java.util.Set<String> BANK_FIRST_REPLY_CATEGORIES = java.util.Set.of("greeting", "status", "thanks", "apology", "repair", "affection", "respect", "reassurance", "companionship", "smalltalk", "memory", "name", "party_farm", "pvp_conflict");
	private static final java.util.Set<String> KNOWLEDGE_OWNED_REPLY_TYPES = java.util.Set.of("travel_destination", "farming", "progression_route", "progression_strength", "class_choice", "itemization", "system_basics", "class_identity", "class_progression", "class_skills", "currency");
	private static final java.util.Set<String> KNOWLEDGE_FIRST_REPLY_TYPES = java.util.Set.of("travel_destination", "farming", "progression_route", "progression_strength", "class_choice", "itemization", "system_basics", "class_identity", "class_progression", "class_skills", "currency");

	private final FakePlayerConfig _config;
	private final FakePlayerRouteService _routeService;
	private final FakePlayerChatService _chatService;
	private final FakePlayerReplySettingsData _replySettingsData;
	private final FakePlayerTargetingService _targetingService;
	private final FakePlayerPersonaResolver _personaResolver;
	private final FakePlayerMessageClassifier _messageClassifier;
	private final FakePlayerReplyBankService _replyBankService;
	private final FakePlayerKnowledgeService _knowledgeService;
	private final FakePlayerSocialMemoryService _socialMemoryService;
	private final FakePlayerMemoryService _memoryService;
	private final FakePlayerSocialService _socialService;
	private final LocalPersonalityAdapter _personalityAdapter;
	
	public FakePlayerDecisionEngine(FakePlayerConfig config, FakePlayerRouteService routeService, FakePlayerChatService chatService, FakePlayerReplySettingsData replySettingsData, FakePlayerTargetingService targetingService, FakePlayerPersonaResolver personaResolver, FakePlayerMessageClassifier messageClassifier, FakePlayerReplyBankService replyBankService, FakePlayerKnowledgeService knowledgeService, FakePlayerSocialMemoryService socialMemoryService, FakePlayerSocialService socialService, LocalPersonalityAdapter personalityAdapter)
	{
		_config = config;
		_routeService = routeService;
		_chatService = chatService;
		_replySettingsData = replySettingsData;
		_targetingService = targetingService;
		_personaResolver = personaResolver;
		_messageClassifier = messageClassifier;
		_replyBankService = replyBankService;
		_knowledgeService = knowledgeService;
		_socialMemoryService = socialMemoryService;
		_memoryService = new FakePlayerMemoryService(chatService, socialMemoryService);
		_socialService = socialService;
		_personalityAdapter = personalityAdapter;
	}
	
	public PersonalityDecision decide(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		return decide(buildPlannerContext(snapshot, recentEvent));
	}
	
	public PersonalityDecision decide(PersonalityContext context)
	{
		return normalize(_personalityAdapter.decide(context), context);
	}
	
	public FakePlayerAdvisoryPlan createAdvisoryPlan(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		return createPlannerAdvisoryPlan(snapshot, recentEvent);
	}
	
	public FakePlayerAdvisoryPlan createPlannerAdvisoryPlan(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		final PersonalityDecision decision = decide(snapshot, recentEvent);
		if ((decision.getConfidence() <= 0.0) && "idle".equalsIgnoreCase(decision.getIntentPreference()))
		{
			return createFallbackPlannerPlan(snapshot, recentEvent);
		}
		return toAdvisoryPlan(snapshot.getFakePlayerId(), decision);
	}
	
	public FakePlayerAdvisoryPlan createFallbackPlannerPlan(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		final String fallbackIntent = choosePlannerFallbackIntent(snapshot, recentEvent);
		final String fallbackZone = resolveFallbackZone(snapshot.getCurrentZone(), snapshot.getAllowedZones());
		final String moodTag = (snapshot.getBoredomScore() >= 0.80) ? "bored" : "calm";
		return new FakePlayerAdvisoryPlan(snapshot.getFakePlayerId(), moodTag, fallbackIntent, fallbackZone, false, "none", "none", "planner_fallback", null, 0.15);
	}
	
	public FakePlayerAdvisoryPlan createWhisperAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "whisper_reply", 1, "whisper_reply", "player_whisper", "whisper", "whisper", 0.15, _chatService.isWhisperChatCooldownReady(fakePlayerId, incomingPlayerName), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}
	
	public FakePlayerAdvisoryPlan createGeneralAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "general_reply", 3, "general_reply", "player_general", "general", "general", 0.20, _chatService.isGeneralChatCooldownReady(fakePlayerId), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}
	
	public FakePlayerAdvisoryPlan createShoutAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "shout_reply", 12, "shout_reply", "player_shout", "shout", "shout", 0.25, _chatService.isPublicChatCooldownReady(fakePlayerId), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}
	
	public FakePlayerAdvisoryPlan createWorldAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "world_reply", 24, "world_reply", "player_world", "world", "world", 0.30, _chatService.isPublicChatCooldownReady(fakePlayerId), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}

	public FakePlayerAdvisoryPlan createClanAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "clan_reply", 6, "clan_reply", "player_clan", "clan", "clan", 0.20, _chatService.isClanChatCooldownReady(fakePlayerId), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}

	public FakePlayerAdvisoryPlan createPartyAdvisoryPlan(String fakePlayerId, String currentZone, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "party_reply", 2, "party_reply", "player_party", "party", "party", 0.18, _chatService.isPartyChatCooldownReady(fakePlayerId), incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}

	public FakePlayerAdvisoryPlan createProbeAdvisoryPlan(String fakePlayerId, String currentZone, String channelName, String incomingPlayerName, String incomingPlayerMessage)
	{
		final String normalizedChannel = ((channelName == null) || channelName.isBlank()) ? "whisper" : channelName.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (normalizedChannel)
		{
			case "clan" -> createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "clan_reply", 6, "clan_reply", "player_clan_probe", "clan", "clan", 0.20, _chatService.isClanChatCooldownReady(fakePlayerId), null, incomingPlayerName, incomingPlayerMessage));
			case "general" -> createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "general_reply", 3, "general_reply", "player_general_probe", "general", "general", 0.20, _chatService.isGeneralChatCooldownReady(fakePlayerId), null, incomingPlayerName, incomingPlayerMessage));
			case "shout" -> createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "shout_reply", 12, "shout_reply", "player_shout_probe", "shout", "shout", 0.25, _chatService.isPublicChatCooldownReady(fakePlayerId), null, incomingPlayerName, incomingPlayerMessage));
			case "world" -> createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "world_reply", 24, "world_reply", "player_world_probe", "world", "world", 0.30, _chatService.isPublicChatCooldownReady(fakePlayerId), null, incomingPlayerName, incomingPlayerMessage));
			default -> createReplyAdvisoryPlan(buildReplyContext(fakePlayerId, currentZone, "whisper_reply", 1, "whisper_reply", "player_whisper_probe", "whisper", "whisper", 0.15, _chatService.isWhisperChatCooldownReady(fakePlayerId, incomingPlayerName), null, incomingPlayerName, incomingPlayerMessage));
		};
	}

	public PersonalityDecision judgeSocialSignal(String fakePlayerId, String currentZone, String channelName, String recentEvent, Player incomingPlayer, String incomingPlayerMessage)
	{
		final String incomingPlayerName = (incomingPlayer == null) ? "" : incomingPlayer.getName();
		return decide(buildReplyContext(fakePlayerId, currentZone, "social_signal", 1, "social_signal", recentEvent, channelName, "social_signal", 0.0, true, incomingPlayer, incomingPlayerName, incomingPlayerMessage));
	}
	
	public FakePlayerRuntimeSnapshot createSnapshot(String fakePlayerId, String archetype, String currentZone, String state, String hpBand, String mpBand, int nearbyPlayerCount, String lastLineTag, double boredomScore)
	{
		return new FakePlayerRuntimeSnapshot(fakePlayerId, archetype, currentZone, state, hpBand, mpBand, nearbyPlayerCount, _chatService.isChatCooldownReady(fakePlayerId), _targetingService.isTeleportCooldownReady(fakePlayerId), lastLineTag, boredomScore, _targetingService.getAllowedIntents(), _routeService.getAllowedZones(), _chatService.getAvailableLineStyleTags(), _chatService.getAvailableTopicTags());
	}

	private PersonalityContext buildPlannerContext(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		final ResolvedFpcPersona persona = _personaResolver.resolve(snapshot.getFakePlayerId(), snapshot.getArchetype());
		return new PersonalityContext(snapshot.getFakePlayerId(), persona.getArchetype(), persona.getPersonaTemplate(), persona.getSpeciesTag(), persona.getFamilyTag(), persona.getResponseStyle(), persona.getPlayerStance(), persona.getCoreNeed(), persona.getPersonaSummary(), persona.getSelfKnowledgeSummary(), persona.getPublicMaskSummary(), persona.getHiddenTruthSummary(), persona.getSelfConcept(), persona.getCoreWound(), persona.getCoreDesire(), persona.getLoyaltyAnchor(), persona.getResentmentAnchor(), persona.getPrivateTaboo(), persona.getSpeechAnchor(), persona.getPrivateContradiction(), persona.getGuidancePosture(), persona.getGuidancePriority(), persona.getUncertaintyStyle(), persona.getRecommendationStyle(), persona.getRiskStyle(), persona.getTeachingStyle(), "", "", "", "", persona.getRelationshipSummary(), persona.getStorySummary(), "", "", persona.getBehaviorFamilyId(), persona.getBehaviorSummary(), persona.getBehaviorRuleSummary(), persona.getStateProfileId(), persona.getStateSummary(), persona.getStateRuleSummary(), "", "", "", "", "", "", "", "", "neutral", "", 0, 0, "", "", "", "", "", snapshot.getCurrentZone(), snapshot.getState(), snapshot.getHpBand(), snapshot.getMpBand(), snapshot.getNearbyPlayerCount(), "planner", recentEvent, snapshot.isChatCooldownReady(), snapshot.isTeleportCooldownReady(), snapshot.getAllowedIntents(), snapshot.getAllowedZones(), snapshot.getAvailableLineStyleTags(), snapshot.getAvailableTopicTags(), "", "unknown", "none", "none", "low", false, "none", java.util.List.of(), 0.0, "", java.util.List.of(), "", "", java.util.List.of(), java.util.List.of(), java.util.List.of(), "", "", snapshot.getLastLineTag(), snapshot.getBoredomScore(), null, null, persona.getDefaultInnerState(), persona.getLongTermGoal(), persona.getSocialTestStyle(), persona.getTrustCriteria(), persona.getRepairStyle(), persona.getRevealBoundary(), persona.getReflectionLens(), persona.getActiveObjective(), persona.getOpenLoops(), persona.getRevealPressure(), persona.getRelationshipPressure(), persona.getNextBeatHint(), persona.getStateModes(), persona.getReflectionPolicy(), persona.getMemoryRetrievalPolicy(), persona.getGoalPersistencePolicy(), persona.getEmotionTransitionRules(), persona.getCallbackStyle(), persona.getConflictStyle(), persona.getRepairCadence());
	}

	private PersonalityContext buildReplyContext(String fakePlayerId, String currentZone, String state, int nearbyPlayerCount, String decisionScope, String recentEvent, String channelName, String lastLineTag, double boredomScore, boolean chatCooldownReady, Player incomingPlayer, String incomingPlayerName, String incomingPlayerMessage)
	{
		final boolean privateChannel = isPrivateReplyChannel(channelName);
		final ResolvedFpcPersona persona = privateChannel ? _personaResolver.resolve(fakePlayerId, "fighter", incomingPlayerName) : _personaResolver.resolve(fakePlayerId, "fighter");
		final String recoveredIncomingText = normalizeReplyInputText(incomingPlayerMessage);
		final String effectiveIncomingText = privateChannel ? _chatService.buildConversationAwareText(fakePlayerId, incomingPlayerName, recoveredIncomingText, _replySettingsData.getSettings().getFollowUpStrictness()) : recoveredIncomingText;
		final String focusEntityName = detectFocusEntity(fakePlayerId, incomingPlayerName, effectiveIncomingText);
		final String focusEntityType = determineFocusEntityType(fakePlayerId, incomingPlayerName, focusEntityName);
		final String focusIntent = _messageClassifier.classifyFocusIntent(effectiveIncomingText);
		final String focusSummary = (focusEntityName == null) || focusEntityName.isBlank() ? "" : _personaResolver.summarizeFocusEntity(fakePlayerId, focusEntityName);
		final FpcRouteProfile initialRouteProfile = _messageClassifier.classifyRouteProfile(effectiveIncomingText, collectFocusCandidateNames(), fakePlayerId, incomingPlayerName);
		final String promotedMessageCategory = promoteSocialSignalCategory(fakePlayerId, incomingPlayerName, incomingPlayerMessage, effectiveIncomingText, focusEntityName, initialRouteProfile.getMessageCategory());
		final FpcRouteProfile routeProfile = _messageClassifier.overrideRouteProfile(effectiveIncomingText, promotedMessageCategory, initialRouteProfile.getKnowledgeType());
		final String messageCategory = routeProfile.getMessageCategory();
		final FpcMemoryBundle memoryBundle = privateChannel ? _memoryService.buildReplyMemory(fakePlayerId, incomingPlayerName, effectiveIncomingText, messageCategory, focusIntent, persona.getMemoryRetrievalPolicy(), persona.getMatchedRelationshipSummary()) : FpcMemoryBundle.EMPTY;
		final String recentConversationSummary = memoryBundle.getRecentConversationSummary();
		final FpcRelationshipSnapshot relationshipSnapshot = memoryBundle.getRelationshipSnapshot();
		final FpcSocialSnapshot socialSnapshot = memoryBundle.getSocialSnapshot();
		final FakePlayerSocialService.ReplySocialCue replySocialCue = _socialService.evaluateReplyCue(incomingPlayer, relationshipSnapshot, socialSnapshot);
		final String dynamicRelationshipSummary = privateChannel ? buildReplyRelationshipSummary(incomingPlayerName, relationshipSnapshot, replySocialCue.getSummary()) : "";
		final String matchedRelationshipSummary = privateChannel ? mergeMemorySummaries(persona.getMatchedRelationshipSummary(), dynamicRelationshipSummary) : "";
		final String salientMemorySummary = memoryBundle.getSalientMemorySummary();
		final String retrievedMemorySummary = memoryBundle.getRetrievedMemorySummary();
		final String memoryPrioritySummary = memoryBundle.getMemoryPrioritySummary();
		final String memoryReflectionSummary = memoryBundle.getMemoryReflectionSummary();
		final String memoryTrustSummary = memoryBundle.getMemoryTrustSummary();
		final String memoryRepairSummary = memoryBundle.getMemoryRepairSummary();
		final String memoryPressureSummary = memoryBundle.getMemoryPressureSummary();
		final String memorySelectionSummary = memoryBundle.getMemorySelectionSummary();
		final String sceneSummary = buildReplySceneSummary(currentZone, state, nearbyPlayerCount, recentEvent, channelName, incomingPlayer, replySocialCue, relationshipSnapshot);
		final String knowledgeType = routeProfile.getKnowledgeType();
		final FpcReplyBankSelection replyBankSelection = _replyBankService.select(fakePlayerId, incomingPlayerName, channelName, messageCategory, routeProfile.getRouteLane(), effectiveIncomingText);
		final FpcKnowledgeSelection knowledgeSelection = _knowledgeService.select(fakePlayerId, incomingPlayerName, channelName, knowledgeType, effectiveIncomingText);
		if (privateChannel && ((incomingPlayerName != null) && !incomingPlayerName.isBlank()) && (memoryBundle.hasMeaningfulMemory() || !replySocialCue.getSummary().isBlank()))
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": ReplyMemoryTrace speaker=" + fakePlayerId + " target=" + incomingPlayerName.trim() + " relationship={familiarity=" + relationshipSnapshot.getFamiliarity() + ", trust=" + relationshipSnapshot.getTrust() + ", respect=" + relationshipSnapshot.getRespect() + ", tension=" + relationshipSnapshot.getTension() + ", resentment=" + relationshipSnapshot.getResentment() + ", playerKillCount=" + relationshipSnapshot.getPlayerKillCount() + ", currentGoal=" + cleanRelationshipFacet(relationshipSnapshot.getCurrentGoal()) + ", activeNeed=" + cleanRelationshipFacet(relationshipSnapshot.getActiveNeed()) + ", lastTopic=" + cleanRelationshipFacet(relationshipSnapshot.getLastImportantTopic()) + "} mergedCue=\"" + trimForLog(matchedRelationshipSummary, 280) + "\" memoryCue=\"" + trimForLog(retrievedMemorySummary, 280) + "\"");
		}
		return new PersonalityContext(fakePlayerId, persona.getArchetype(), persona.getPersonaTemplate(), persona.getSpeciesTag(), persona.getFamilyTag(), persona.getResponseStyle(), persona.getPlayerStance(), persona.getCoreNeed(), persona.getPersonaSummary(), persona.getSelfKnowledgeSummary(), persona.getPublicMaskSummary(), persona.getHiddenTruthSummary(), persona.getSelfConcept(), persona.getCoreWound(), persona.getCoreDesire(), persona.getLoyaltyAnchor(), persona.getResentmentAnchor(), persona.getPrivateTaboo(), persona.getSpeechAnchor(), persona.getPrivateContradiction(), persona.getGuidancePosture(), persona.getGuidancePriority(), persona.getUncertaintyStyle(), persona.getRecommendationStyle(), persona.getRiskStyle(), persona.getTeachingStyle(), focusEntityName, focusEntityType, focusIntent, focusSummary, persona.getRelationshipSummary(), persona.getStorySummary(), matchedRelationshipSummary, recentConversationSummary, persona.getBehaviorFamilyId(), persona.getBehaviorSummary(), persona.getBehaviorRuleSummary(), persona.getStateProfileId(), persona.getStateSummary(), persona.getStateRuleSummary(), salientMemorySummary, retrievedMemorySummary, memoryPrioritySummary, memoryReflectionSummary, memoryTrustSummary, memoryRepairSummary, memoryPressureSummary, memorySelectionSummary, replySocialCue.getSocialLabel(), replySocialCue.getSummary(), replySocialCue.getTrustBias(), replySocialCue.getGuardBias(), replySocialCue.getActionStance().describe(), sceneSummary, cleanRelationshipFacet(relationshipSnapshot.getCurrentGoal()), cleanRelationshipFacet(relationshipSnapshot.getActiveNeed()), cleanRelationshipFacet(relationshipSnapshot.getLastImportantTopic()), currentZone, state, "high", "high", nearbyPlayerCount, decisionScope, recentEvent, chatCooldownReady, _targetingService.isTeleportCooldownReady(fakePlayerId), _targetingService.getAllowedIntents(), _routeService.getAllowedZones(), _chatService.getAvailableLineStyleTags(), _chatService.getAvailableTopicTags(), messageCategory, routeProfile.getRouteLane(), routeProfile.getSpeechAct(), routeProfile.getKnowledgeNeed(), routeProfile.getSocialStake(), routeProfile.isActionRequested(), routeProfile.getPrimaryTopic(), routeProfile.getSecondaryTopics(), routeProfile.getConfidence(), replyBankSelection.getSummary(), replyBankSelection.getCandidateLines(), knowledgeType, knowledgeSelection.getSummary(), knowledgeSelection.getFactLines(), knowledgeSelection.getCandidateLines(), knowledgeSelection.getHeuristicLines(), knowledgeSelection.getScope(), knowledgeSelection.getConfidenceHint(), lastLineTag, boredomScore, incomingPlayerName, recoveredIncomingText, persona.getDefaultInnerState(), persona.getLongTermGoal(), persona.getSocialTestStyle(), persona.getTrustCriteria(), persona.getRepairStyle(), persona.getRevealBoundary(), persona.getReflectionLens(), persona.getActiveObjective(), persona.getOpenLoops(), persona.getRevealPressure(), persona.getRelationshipPressure(), persona.getNextBeatHint(), persona.getStateModes(), persona.getReflectionPolicy(), persona.getMemoryRetrievalPolicy(), persona.getGoalPersistencePolicy(), persona.getEmotionTransitionRules(), persona.getCallbackStyle(), persona.getConflictStyle(), persona.getRepairCadence());
	}

	private String detectFocusEntity(String fakePlayerId, String incomingPlayerName, String effectiveIncomingText)
	{
		return _messageClassifier.extractFocusEntity(effectiveIncomingText, collectFocusCandidateNames(), fakePlayerId, incomingPlayerName);
	}

	private String promoteSocialSignalCategory(String fakePlayerId, String incomingPlayerName, String incomingPlayerMessage, String effectiveIncomingText, String focusEntityName, String baseCategory)
	{
		if (_messageClassifier.isThreatSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "threat";
		}
		if (_messageClassifier.isDirectFlame(incomingPlayerMessage, fakePlayerId))
		{
			return "insult";
		}
		if (_messageClassifier.isSelfhoodCategory(baseCategory))
		{
			return baseCategory;
		}
		if (_messageClassifier.isDistrustSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "distrust";
		}
		if (_messageClassifier.isResentmentSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "resentment";
		}
		if (_messageClassifier.isAbandonmentSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "abandonment";
		}
		if (_messageClassifier.isApologySignal(incomingPlayerMessage))
		{
			return "apology";
		}
		if (_messageClassifier.isGratitudeSignal(incomingPlayerMessage))
		{
			return "thanks";
		}
		if (_messageClassifier.isRepairSignal(incomingPlayerMessage))
		{
			return "repair";
		}
		if (_messageClassifier.isAffectionSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "affection";
		}
		if (_messageClassifier.isRespectSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "respect";
		}
		if (_messageClassifier.isPraiseSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "praise";
		}
		if (_messageClassifier.isReassuranceSignal(incomingPlayerMessage, fakePlayerId))
		{
			return "reassurance";
		}
		if ((_personaResolver.getPositiveBondIntensity(fakePlayerId, focusEntityName) >= 70) && _messageClassifier.isNegativeSubjectStatement(effectiveIncomingText, focusEntityName))
		{
			return "belief_conflict";
		}
		return baseCategory;
	}

	private java.util.List<String> collectFocusCandidateNames()
	{
		final java.util.List<String> candidateNames = new java.util.ArrayList<>();
		final FakePlayerModule module = FakePlayerModule.getInstance();
		if (module != null)
		{
			for (FpcDefinition definition : module.getLoadedFpcDefinitions())
			{
				if ((definition != null) && (definition.getName() != null) && !definition.getName().isBlank())
				{
					candidateNames.add(definition.getName());
				}
			}
		}
		return candidateNames;
	}

	private String determineFocusEntityType(String fakePlayerId, String incomingPlayerName, String focusEntityName)
	{
		if ((focusEntityName == null) || focusEntityName.isBlank())
		{
			return "";
		}
		if ("adventurers".equalsIgnoreCase(focusEntityName))
		{
			return "group";
		}
		if ((incomingPlayerName != null) && focusEntityName.equalsIgnoreCase(incomingPlayerName))
		{
			return "player";
		}
		final FakePlayerModule module = FakePlayerModule.getInstance();
		if ((module != null) && (module.findFpcDefinition(focusEntityName) != null))
		{
			return "fpc";
		}
		if (focusEntityName.equalsIgnoreCase(fakePlayerId))
		{
			return "self";
		}
		return "named_other";
	}

	private boolean shouldPromoteFocusIntent(String messageCategory, String focusIntent, String focusEntityName)
	{
		if ((focusIntent == null) || focusIntent.isBlank() || (focusEntityName == null) || focusEntityName.isBlank())
		{
			return false;
		}
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(java.util.Locale.ROOT))
		{
			case "", "unknown", "greeting", "status", "smalltalk", "memory", "help", "quest_story", "relationship_probe" -> true;
			default -> false;
		};
	}

	private boolean isPrivateReplyChannel(String channelName)
	{
		return "whisper".equalsIgnoreCase(channelName) || "clan".equalsIgnoreCase(channelName);
	}

	private String normalizeReplyInputText(String incomingPlayerMessage)
	{
		return (incomingPlayerMessage == null) ? "" : _messageClassifier.recoverTypoSensitiveText(incomingPlayerMessage).trim();
	}

	private String mergeMemorySummaries(String selectiveMemorySummary, String socialMemorySummary)
	{
		final String selective = (selectiveMemorySummary == null) ? "" : selectiveMemorySummary.trim();
		final String social = (socialMemorySummary == null) ? "" : socialMemorySummary.trim();
		if (selective.isBlank())
		{
			return social;
		}
		if (social.isBlank())
		{
			return selective;
		}
		return selective + " " + social;
	}

	private String buildReplyRelationshipSummary(String targetPlayerName, FpcRelationshipSnapshot relationshipSnapshot, String socialMemorySummary)
	{
		return mergeMemorySummaries(summarizeChatRelationshipSnapshot(targetPlayerName, relationshipSnapshot), socialMemorySummary);
	}

	private String summarizeChatRelationshipSnapshot(String targetPlayerName, FpcRelationshipSnapshot snapshot)
	{
		if ((snapshot == null) || !snapshot.hasMeaningfulHistory())
		{
			return "";
		}

		final String playerLabel = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final int warmthScore = snapshot.getFamiliarity() + snapshot.getTrust() + snapshot.getRespect();
		final int guardScore = snapshot.getTension() + snapshot.getResentment() + (snapshot.getPlayerKillCount() * 2);
		final StringBuilder sb = new StringBuilder();
		sb.append("Current per-player chat relationship with ").append(playerLabel).append(": ");
		if ((snapshot.getPlayerKillCount() > 0) || (snapshot.getResentment() >= 6) || (guardScore >= Math.max(8, warmthScore + 4)))
		{
			sb.append("hostile and resentful; stays guarded and keeps emotional distance.");
		}
		else if ((snapshot.getTension() >= 4) || (snapshot.getResentment() >= 3) || (guardScore >= (warmthScore + 2)))
		{
			sb.append("guarded and strained; trust is limited and the tone should stay careful.");
		}
		else if ((snapshot.getTrust() >= 4) || (warmthScore >= 12))
		{
			sb.append("warm, trusting, and marked by gratitude; sounds relieved when ").append(playerLabel).append(" comes back.");
		}
		else if ((snapshot.getFamiliarity() >= 4) || (warmthScore >= 7))
		{
			sb.append("hopeful and companionship-driven; familiarity is growing and the tone can be friendly.");
		}
		else
		{
			sb.append("curious but still tentative; the tone should stay open rather than intimate.");
		}
		appendRelationshipFacet(sb, "Current goal", snapshot.getCurrentGoal());
		appendRelationshipFacet(sb, "Active need", snapshot.getActiveNeed());
		appendRelationshipFacet(sb, "Last important topic", snapshot.getLastImportantTopic());
		return sb.toString();
	}

	private void appendRelationshipFacet(StringBuilder sb, String label, String value)
	{
		final String cleaned = cleanRelationshipFacet(value);
		if (!cleaned.isBlank())
		{
			sb.append(' ').append(label).append(": ").append(cleaned).append('.');
		}
	}

	private static String cleanRelationshipFacet(String value)
	{
		if ((value == null) || value.isBlank())
		{
			return "";
		}
		return value.trim().replace('_', ' ').replaceAll("\\s+", " ");
	}

	private static String trimForLog(String value, int maxLength)
	{
		if ((value == null) || value.isBlank())
		{
			return "";
		}
		final String cleaned = value.trim().replaceAll("\\s+", " ");
		if (cleaned.length() <= maxLength)
		{
			return cleaned;
		}
		int cut = cleaned.lastIndexOf(' ', maxLength);
		if (cut <= 0)
		{
			cut = maxLength;
		}
		return cleaned.substring(0, cut).trim() + "...";
	}

	private String buildReplySceneSummary(String currentZone, String state, int nearbyPlayerCount, String recentEvent, String channelName, Player incomingPlayer, FakePlayerSocialService.ReplySocialCue replySocialCue, FpcRelationshipSnapshot relationshipSnapshot)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Current reply scene: channel=").append(channelName).append(", zone=").append(currentZone).append(", state=").append(state).append(", nearbyPlayers=").append(nearbyPlayerCount).append(", event=").append(recentEvent).append('.');
		if (incomingPlayer != null)
		{
			sb.append(' ').append("Addressed player ").append(incomingPlayer.getName()).append(" has reputation=").append(incomingPlayer.getReputation()).append(", pkKills=").append(incomingPlayer.getPkKills()).append(", pvpFlag=").append(incomingPlayer.getPvpFlag()).append('.');
		}
		if ((replySocialCue != null) && replySocialCue.hasLiveRiskPressure())
		{
			sb.append(' ').append("Live pressure: ").append(replySocialCue.getLiveRiskSummary()).append('.');
		}
		if ((relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory())
		{
			appendSceneFacet(sb, "Current relationship goal", relationshipSnapshot.getCurrentGoal());
			appendSceneFacet(sb, "Current relationship need", relationshipSnapshot.getActiveNeed());
			appendSceneFacet(sb, "Last relationship topic", relationshipSnapshot.getLastImportantTopic());
		}
		return trimForLog(sb.toString(), 360);
	}

	private void appendSceneFacet(StringBuilder sb, String label, String value)
	{
		final String cleaned = cleanRelationshipFacet(value);
		if (!cleaned.isBlank())
		{
			sb.append(' ').append(label).append(": ").append(cleaned).append('.');
		}
	}

	private FakePlayerAdvisoryPlan createReplyAdvisoryPlan(PersonalityContext context)
	{
		return toReplyAdvisoryPlan(context, decide(context));
	}

	private FakePlayerAdvisoryPlan toAdvisoryPlan(String speakerName, PersonalityDecision decision)
	{
		final String preferredZone = _routeService.choosePreferredZone(decision.getZoneBias());
		String selectedLine = decision.getDirectReplyLine();
		String replySource = "planner_direct";
		if ((selectedLine == null) || selectedLine.isBlank())
		{
			selectedLine = decision.isSpeakNow() ? _chatService.selectCannedLine(decision.getLineStyleTag(), decision.getTopicTag()) : null;
			replySource = (selectedLine == null) || selectedLine.isBlank() ? "planner_silent" : "planner_canned";
		}
		return new FakePlayerAdvisoryPlan(speakerName, decision.getMoodTag(), decision.getIntentPreference(), preferredZone, decision.isSpeakNow(), decision.getLineStyleTag(), decision.getTopicTag(), replySource, selectedLine, decision.getConfidence());
	}

	private FakePlayerAdvisoryPlan toReplyAdvisoryPlan(PersonalityContext context, PersonalityDecision decision)
	{
		final String preferredZone = _routeService.choosePreferredZone(decision.getZoneBias());
		final boolean selfhoodCategory = _messageClassifier.isSelfhoodCategory(context.getMessageCategory());
		final boolean bankFirstCategory = isBankFirstReplyCategory(context.getMessageCategory());
		final boolean blockReplyBankFallback = shouldBlockReplyBankFallback(context.getKnowledgeType());
		final String bankReplyLine = selfhoodCategory ? null : chooseFreshCandidateLine(context.getReplyBankLines(), context.getRecentConversationSummary());
		final String knowledgeReplyLine = selfhoodCategory ? null : chooseFreshCandidateLine(context.getKnowledgeReplyLines(), context.getRecentConversationSummary());
		String selectedLine = decision.getDirectReplyLine();
		String replySource = "planner_direct";
		if (!selfhoodCategory && isKnowledgeFirstReplyType(context.getKnowledgeType()) && (knowledgeReplyLine != null) && !knowledgeReplyLine.isBlank())
		{
			selectedLine = knowledgeReplyLine;
			replySource = "knowledge_bank";
		}
		else if (bankFirstCategory && (bankReplyLine != null) && !bankReplyLine.isBlank())
		{
			selectedLine = bankReplyLine;
			replySource = "reply_bank";
		}
		if (!selfhoodCategory && ((selectedLine == null) || selectedLine.isBlank()))
		{
			selectedLine = knowledgeReplyLine;
			replySource = (selectedLine == null) || selectedLine.isBlank() ? "planner_direct" : "knowledge_bank";
		}
		if (!selfhoodCategory && !blockReplyBankFallback && ((selectedLine == null) || selectedLine.isBlank()))
		{
			selectedLine = bankReplyLine;
			replySource = (selectedLine == null) || selectedLine.isBlank() ? replySource : "reply_bank";
		}
		if (!selfhoodCategory && ((selectedLine == null) || selectedLine.isBlank()))
		{
			selectedLine = decision.isSpeakNow() ? _chatService.selectCannedLine(decision.getLineStyleTag(), decision.getTopicTag()) : null;
			replySource = (selectedLine == null) || selectedLine.isBlank() ? "planner_silent" : "planner_canned";
		}
		return new FakePlayerAdvisoryPlan(context.getFakePlayerId(), decision.getMoodTag(), decision.getIntentPreference(), preferredZone, decision.isSpeakNow(), decision.getLineStyleTag(), decision.getTopicTag(), replySource, selectedLine, decision.getConfidence());
	}

	private String choosePlannerFallbackIntent(FakePlayerRuntimeSnapshot snapshot, String recentEvent)
	{
		if ("moving".equalsIgnoreCase(snapshot.getState()) && snapshot.getAllowedIntents().contains("move"))
		{
			return "move";
		}
		if (("zone_boredom".equalsIgnoreCase(recentEvent) || "idle_timeout".equalsIgnoreCase(recentEvent) || (snapshot.getBoredomScore() >= 0.20)) && snapshot.getAllowedIntents().contains("move"))
		{
			return "move";
		}
		return snapshot.getAllowedIntents().contains("idle") ? "idle" : PersonalityDecision.DEFAULT.getIntentPreference();
	}

	private String resolveFallbackZone(String currentZone, java.util.List<String> allowedZones)
	{
		if ((currentZone != null) && allowedZones.contains(currentZone))
		{
			return currentZone;
		}
		return allowedZones.isEmpty() ? "" : allowedZones.get(0);
	}

	private String chooseFreshCandidateLine(java.util.List<String> candidateLines, String recentConversationSummary)
	{
		if ((candidateLines == null) || candidateLines.isEmpty())
		{
			return null;
		}

		final String recent = (recentConversationSummary == null) ? "" : recentConversationSummary.toLowerCase(java.util.Locale.ROOT);
		for (String candidate : candidateLines)
		{
			if ((candidate != null) && !candidate.isBlank() && !recent.contains(candidate.toLowerCase(java.util.Locale.ROOT)))
			{
				return candidate;
			}
		}
		return candidateLines.get(0);
	}

	private boolean isBankFirstReplyCategory(String messageCategory)
	{
		if ((messageCategory == null) || messageCategory.isBlank())
		{
			return false;
		}
		return BANK_FIRST_REPLY_CATEGORIES.contains(messageCategory.toLowerCase(java.util.Locale.ROOT));
	}

	private boolean shouldBlockReplyBankFallback(String knowledgeType)
	{
		if ((knowledgeType == null) || knowledgeType.isBlank())
		{
			return false;
		}
		return KNOWLEDGE_OWNED_REPLY_TYPES.contains(knowledgeType.toLowerCase(java.util.Locale.ROOT));
	}

	private boolean isKnowledgeFirstReplyType(String knowledgeType)
	{
		if ((knowledgeType == null) || knowledgeType.isBlank())
		{
			return false;
		}
		return KNOWLEDGE_FIRST_REPLY_TYPES.contains(knowledgeType.toLowerCase(java.util.Locale.ROOT));
	}
	
	private PersonalityDecision normalize(PersonalityDecision decision, PersonalityContext context)
	{
		if (decision == null)
		{
			return PersonalityDecision.DEFAULT;
		}
		
		final String intent = context.getAllowedIntents().contains(decision.getIntentPreference()) ? decision.getIntentPreference() : PersonalityDecision.DEFAULT.getIntentPreference();
		final String zoneBias = context.getAllowedZones().contains(decision.getZoneBias()) ? decision.getZoneBias() : PersonalityDecision.DEFAULT.getZoneBias();
		final String lineStyleTag = context.getAvailableLineStyleTags().contains(decision.getLineStyleTag()) ? decision.getLineStyleTag() : PersonalityDecision.DEFAULT.getLineStyleTag();
		final String topicTag = context.getAvailableTopicTags().contains(decision.getTopicTag()) ? decision.getTopicTag() : PersonalityDecision.DEFAULT.getTopicTag();
		final double aggressionRiskScore = clamp(decision.getAggressionRiskScore());
		final double confidence = clamp(decision.getConfidence());
		
		return new PersonalityDecision(decision.getMoodTag(), intent, zoneBias, aggressionRiskScore, decision.isSpeakNow() && context.isChatCooldownReady(), lineStyleTag, topicTag, confidence, decision.getDirectReplyLine(), decision.getSocialActionTag(), decision.getSocialTargetTag(), clamp(decision.getSocialSignalConfidence()), clampIntensity(decision.getSocialSignalIntensity()));
	}
	
	private static double clamp(double value)
	{
		if (value < 0.0)
		{
			return 0.0;
		}
		if (value > 1.0)
		{
			return 1.0;
		}
		return value;
	}

	private static int clampIntensity(int value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 3)
		{
			return 3;
		}
		return value;
	}
	
	public FakePlayerConfig getConfig()
	{
		return _config;
	}
}
