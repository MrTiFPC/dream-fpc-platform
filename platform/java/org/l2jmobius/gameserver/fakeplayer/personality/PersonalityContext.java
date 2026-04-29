package org.l2jmobius.gameserver.fakeplayer.personality;

import java.util.List;
import java.util.Objects;

/**
 * Narrow advisory input sent to the local personality adapter.
 */
public class PersonalityContext
{
	private final String _fakePlayerId;
	private final String _archetype;
	private final String _personaTemplate;
	private final String _speciesTag;
	private final String _familyTag;
	private final String _responseStyle;
	private final String _playerStance;
	private final String _coreNeed;
	private final String _personaSummary;
	private final String _selfKnowledgeSummary;
	private final String _publicMaskSummary;
	private final String _hiddenTruthSummary;
	private final String _selfConcept;
	private final String _coreWound;
	private final String _coreDesire;
	private final String _loyaltyAnchor;
	private final String _resentmentAnchor;
	private final String _privateTaboo;
	private final String _speechAnchor;
	private final String _privateContradiction;
	private final String _guidancePosture;
	private final String _guidancePriority;
	private final String _uncertaintyStyle;
	private final String _recommendationStyle;
	private final String _riskStyle;
	private final String _teachingStyle;
	private final String _focusEntityName;
	private final String _focusEntityType;
	private final String _focusIntent;
	private final String _focusSummary;
	private final String _relationshipSummary;
	private final String _storySummary;
	private final String _matchedRelationshipSummary;
	private final String _recentConversationSummary;
	private final String _behaviorFamilyId;
	private final String _behaviorSummary;
	private final String _behaviorRuleSummary;
	private final String _stateProfileId;
	private final String _stateSummary;
	private final String _stateRuleSummary;
	private final String _salientMemorySummary;
	private final String _retrievedMemorySummary;
	private final String _memoryPrioritySummary;
	private final String _memoryReflectionSummary;
	private final String _memoryTrustSummary;
	private final String _memoryRepairSummary;
	private final String _memoryPressureSummary;
	private final String _memorySelectionSummary;
	private final String _socialLabel;
	private final String _socialSummary;
	private final int _socialTrustBias;
	private final int _socialGuardBias;
	private final String _socialActionStance;
	private final String _sceneSummary;
	private final String _currentRelationshipGoal;
	private final String _currentRelationshipNeed;
	private final String _lastRelationshipTopic;
	private final String _currentZone;
	private final String _state;
	private final String _hpBand;
	private final String _mpBand;
	private final int _nearbyPlayerCount;
	private final String _decisionScope;
	private final String _recentEvent;
	private final boolean _chatCooldownReady;
	private final boolean _teleportCooldownReady;
	private final List<String> _allowedIntents;
	private final List<String> _allowedZones;
	private final List<String> _availableLineStyleTags;
	private final List<String> _availableTopicTags;
	private final String _messageCategory;
	private final String _routeLane;
	private final String _routeSpeechAct;
	private final String _routeKnowledgeNeed;
	private final String _routeSocialStake;
	private final boolean _routeActionRequested;
	private final String _routePrimaryTopic;
	private final List<String> _routeSecondaryTopics;
	private final double _routeConfidence;
	private final String _replyBankSummary;
	private final List<String> _replyBankLines;
	private final String _knowledgeType;
	private final String _knowledgeSummary;
	private final List<String> _knowledgeFacts;
	private final List<String> _knowledgeReplyLines;
	private final List<String> _knowledgeHeuristicLines;
	private final String _knowledgeScope;
	private final String _knowledgeConfidenceHint;
	private final String _lastLineTag;
	private final double _boredomScore;
	private final String _incomingPlayerName;
	private final String _incomingPlayerMessage;
	private final String _defaultInnerState;
	private final String _longTermGoal;
	private final String _socialTestStyle;
	private final String _trustCriteria;
	private final String _repairStyle;
	private final String _revealBoundary;
	private final String _reflectionLens;
	private final String _activeObjective;
	private final String _openLoops;
	private final String _revealPressure;
	private final String _relationshipPressure;
	private final String _nextBeatHint;
	private final String _stateModes;
	private final String _reflectionPolicy;
	private final String _memoryRetrievalPolicy;
	private final String _goalPersistencePolicy;
	private final String _emotionTransitionRules;
	private final String _callbackStyle;
	private final String _conflictStyle;
	private final String _repairCadence;
	
	public PersonalityContext(String fakePlayerId, String archetype, String personaTemplate, String speciesTag, String familyTag, String responseStyle, String playerStance, String coreNeed, String personaSummary, String selfKnowledgeSummary, String publicMaskSummary, String hiddenTruthSummary, String selfConcept, String coreWound, String coreDesire, String loyaltyAnchor, String resentmentAnchor, String privateTaboo, String speechAnchor, String privateContradiction, String guidancePosture, String guidancePriority, String uncertaintyStyle, String recommendationStyle, String riskStyle, String teachingStyle, String focusEntityName, String focusEntityType, String focusIntent, String focusSummary, String relationshipSummary, String storySummary, String matchedRelationshipSummary, String recentConversationSummary, String behaviorFamilyId, String behaviorSummary, String behaviorRuleSummary, String stateProfileId, String stateSummary, String stateRuleSummary, String salientMemorySummary, String retrievedMemorySummary, String memoryPrioritySummary, String memoryReflectionSummary, String memoryTrustSummary, String memoryRepairSummary, String memoryPressureSummary, String memorySelectionSummary, String socialLabel, String socialSummary, int socialTrustBias, int socialGuardBias, String socialActionStance, String sceneSummary, String currentRelationshipGoal, String currentRelationshipNeed, String lastRelationshipTopic, String currentZone, String state, String hpBand, String mpBand, int nearbyPlayerCount, String decisionScope, String recentEvent, boolean chatCooldownReady, boolean teleportCooldownReady, List<String> allowedIntents, List<String> allowedZones, List<String> availableLineStyleTags, List<String> availableTopicTags, String messageCategory, String routeLane, String routeSpeechAct, String routeKnowledgeNeed, String routeSocialStake, boolean routeActionRequested, String routePrimaryTopic, List<String> routeSecondaryTopics, double routeConfidence, String replyBankSummary, List<String> replyBankLines, String knowledgeType, String knowledgeSummary, List<String> knowledgeFacts, List<String> knowledgeReplyLines, List<String> knowledgeHeuristicLines, String knowledgeScope, String knowledgeConfidenceHint, String lastLineTag, double boredomScore, String incomingPlayerName, String incomingPlayerMessage, String defaultInnerState, String longTermGoal, String socialTestStyle, String trustCriteria, String repairStyle, String revealBoundary, String reflectionLens, String activeObjective, String openLoops, String revealPressure, String relationshipPressure, String nextBeatHint, String stateModes, String reflectionPolicy, String memoryRetrievalPolicy, String goalPersistencePolicy, String emotionTransitionRules, String callbackStyle, String conflictStyle, String repairCadence)
	{
		_fakePlayerId = Objects.requireNonNull(fakePlayerId);
		_archetype = Objects.requireNonNull(archetype);
		_personaTemplate = Objects.requireNonNull(personaTemplate);
		_speciesTag = Objects.requireNonNull(speciesTag);
		_familyTag = Objects.requireNonNull(familyTag);
		_responseStyle = Objects.requireNonNull(responseStyle);
		_playerStance = Objects.requireNonNull(playerStance);
		_coreNeed = Objects.requireNonNull(coreNeed);
		_personaSummary = Objects.requireNonNull(personaSummary);
		_selfKnowledgeSummary = Objects.requireNonNull(selfKnowledgeSummary);
		_publicMaskSummary = Objects.requireNonNull(publicMaskSummary);
		_hiddenTruthSummary = Objects.requireNonNull(hiddenTruthSummary);
		_selfConcept = Objects.requireNonNull(selfConcept);
		_coreWound = Objects.requireNonNull(coreWound);
		_coreDesire = Objects.requireNonNull(coreDesire);
		_loyaltyAnchor = Objects.requireNonNull(loyaltyAnchor);
		_resentmentAnchor = Objects.requireNonNull(resentmentAnchor);
		_privateTaboo = Objects.requireNonNull(privateTaboo);
		_speechAnchor = Objects.requireNonNull(speechAnchor);
		_privateContradiction = Objects.requireNonNull(privateContradiction);
		_guidancePosture = Objects.requireNonNull(guidancePosture);
		_guidancePriority = Objects.requireNonNull(guidancePriority);
		_uncertaintyStyle = Objects.requireNonNull(uncertaintyStyle);
		_recommendationStyle = Objects.requireNonNull(recommendationStyle);
		_riskStyle = Objects.requireNonNull(riskStyle);
		_teachingStyle = Objects.requireNonNull(teachingStyle);
		_focusEntityName = Objects.requireNonNull(focusEntityName);
		_focusEntityType = Objects.requireNonNull(focusEntityType);
		_focusIntent = Objects.requireNonNull(focusIntent);
		_focusSummary = Objects.requireNonNull(focusSummary);
		_relationshipSummary = Objects.requireNonNull(relationshipSummary);
		_storySummary = Objects.requireNonNull(storySummary);
		_matchedRelationshipSummary = Objects.requireNonNull(matchedRelationshipSummary);
		_recentConversationSummary = Objects.requireNonNull(recentConversationSummary);
		_behaviorFamilyId = Objects.requireNonNull(behaviorFamilyId);
		_behaviorSummary = Objects.requireNonNull(behaviorSummary);
		_behaviorRuleSummary = Objects.requireNonNull(behaviorRuleSummary);
		_stateProfileId = Objects.requireNonNull(stateProfileId);
		_stateSummary = Objects.requireNonNull(stateSummary);
		_stateRuleSummary = Objects.requireNonNull(stateRuleSummary);
		_salientMemorySummary = Objects.requireNonNull(salientMemorySummary);
		_retrievedMemorySummary = Objects.requireNonNull(retrievedMemorySummary);
		_memoryPrioritySummary = Objects.requireNonNull(memoryPrioritySummary);
		_memoryReflectionSummary = Objects.requireNonNull(memoryReflectionSummary);
		_memoryTrustSummary = Objects.requireNonNull(memoryTrustSummary);
		_memoryRepairSummary = Objects.requireNonNull(memoryRepairSummary);
		_memoryPressureSummary = Objects.requireNonNull(memoryPressureSummary);
		_memorySelectionSummary = Objects.requireNonNull(memorySelectionSummary);
		_socialLabel = Objects.requireNonNull(socialLabel);
		_socialSummary = Objects.requireNonNull(socialSummary);
		_socialTrustBias = socialTrustBias;
		_socialGuardBias = socialGuardBias;
		_socialActionStance = Objects.requireNonNull(socialActionStance);
		_sceneSummary = Objects.requireNonNull(sceneSummary);
		_currentRelationshipGoal = Objects.requireNonNull(currentRelationshipGoal);
		_currentRelationshipNeed = Objects.requireNonNull(currentRelationshipNeed);
		_lastRelationshipTopic = Objects.requireNonNull(lastRelationshipTopic);
		_currentZone = Objects.requireNonNull(currentZone);
		_state = Objects.requireNonNull(state);
		_hpBand = Objects.requireNonNull(hpBand);
		_mpBand = Objects.requireNonNull(mpBand);
		_nearbyPlayerCount = nearbyPlayerCount;
		_decisionScope = Objects.requireNonNull(decisionScope);
		_recentEvent = Objects.requireNonNull(recentEvent);
		_chatCooldownReady = chatCooldownReady;
		_teleportCooldownReady = teleportCooldownReady;
		_allowedIntents = List.copyOf(allowedIntents);
		_allowedZones = List.copyOf(allowedZones);
		_availableLineStyleTags = List.copyOf(availableLineStyleTags);
		_availableTopicTags = List.copyOf(availableTopicTags);
		_messageCategory = Objects.requireNonNull(messageCategory);
		_routeLane = Objects.requireNonNull(routeLane);
		_routeSpeechAct = Objects.requireNonNull(routeSpeechAct);
		_routeKnowledgeNeed = Objects.requireNonNull(routeKnowledgeNeed);
		_routeSocialStake = Objects.requireNonNull(routeSocialStake);
		_routeActionRequested = routeActionRequested;
		_routePrimaryTopic = Objects.requireNonNull(routePrimaryTopic);
		_routeSecondaryTopics = List.copyOf(routeSecondaryTopics);
		_routeConfidence = routeConfidence;
		_replyBankSummary = Objects.requireNonNull(replyBankSummary);
		_replyBankLines = List.copyOf(replyBankLines);
		_knowledgeType = Objects.requireNonNull(knowledgeType);
		_knowledgeSummary = Objects.requireNonNull(knowledgeSummary);
		_knowledgeFacts = List.copyOf(knowledgeFacts);
		_knowledgeReplyLines = List.copyOf(knowledgeReplyLines);
		_knowledgeHeuristicLines = List.copyOf(knowledgeHeuristicLines);
		_knowledgeScope = Objects.requireNonNull(knowledgeScope);
		_knowledgeConfidenceHint = Objects.requireNonNull(knowledgeConfidenceHint);
		_lastLineTag = Objects.requireNonNull(lastLineTag);
		_boredomScore = boredomScore;
		_incomingPlayerName = incomingPlayerName;
		_incomingPlayerMessage = incomingPlayerMessage;
		_defaultInnerState = Objects.requireNonNull(defaultInnerState);
		_longTermGoal = Objects.requireNonNull(longTermGoal);
		_socialTestStyle = Objects.requireNonNull(socialTestStyle);
		_trustCriteria = Objects.requireNonNull(trustCriteria);
		_repairStyle = Objects.requireNonNull(repairStyle);
		_revealBoundary = Objects.requireNonNull(revealBoundary);
		_reflectionLens = Objects.requireNonNull(reflectionLens);
		_activeObjective = Objects.requireNonNull(activeObjective);
		_openLoops = Objects.requireNonNull(openLoops);
		_revealPressure = Objects.requireNonNull(revealPressure);
		_relationshipPressure = Objects.requireNonNull(relationshipPressure);
		_nextBeatHint = Objects.requireNonNull(nextBeatHint);
		_stateModes = Objects.requireNonNull(stateModes);
		_reflectionPolicy = Objects.requireNonNull(reflectionPolicy);
		_memoryRetrievalPolicy = Objects.requireNonNull(memoryRetrievalPolicy);
		_goalPersistencePolicy = Objects.requireNonNull(goalPersistencePolicy);
		_emotionTransitionRules = Objects.requireNonNull(emotionTransitionRules);
		_callbackStyle = Objects.requireNonNull(callbackStyle);
		_conflictStyle = Objects.requireNonNull(conflictStyle);
		_repairCadence = Objects.requireNonNull(repairCadence);
	}
	
	public String getFakePlayerId()
	{
		return _fakePlayerId;
	}
	
	public String getArchetype()
	{
		return _archetype;
	}

	public String getPersonaTemplate()
	{
		return _personaTemplate;
	}

	public String getSpeciesTag()
	{
		return _speciesTag;
	}

	public String getFamilyTag()
	{
		return _familyTag;
	}

	public String getResponseStyle()
	{
		return _responseStyle;
	}

	public String getPlayerStance()
	{
		return _playerStance;
	}

	public String getCoreNeed()
	{
		return _coreNeed;
	}

	public String getPersonaSummary()
	{
		return _personaSummary;
	}

	public String getSelfKnowledgeSummary()
	{
		return _selfKnowledgeSummary;
	}

	public String getPublicMaskSummary()
	{
		return _publicMaskSummary;
	}

	public String getHiddenTruthSummary()
	{
		return _hiddenTruthSummary;
	}

	public String getSelfConcept()
	{
		return _selfConcept;
	}

	public String getCoreWound()
	{
		return _coreWound;
	}

	public String getCoreDesire()
	{
		return _coreDesire;
	}

	public String getLoyaltyAnchor()
	{
		return _loyaltyAnchor;
	}

	public String getResentmentAnchor()
	{
		return _resentmentAnchor;
	}

	public String getPrivateTaboo()
	{
		return _privateTaboo;
	}

	public String getSpeechAnchor()
	{
		return _speechAnchor;
	}

	public String getPrivateContradiction()
	{
		return _privateContradiction;
	}

	public String getGuidancePosture()
	{
		return _guidancePosture;
	}

	public String getGuidancePriority()
	{
		return _guidancePriority;
	}

	public String getUncertaintyStyle()
	{
		return _uncertaintyStyle;
	}

	public String getRecommendationStyle()
	{
		return _recommendationStyle;
	}

	public String getRiskStyle()
	{
		return _riskStyle;
	}

	public String getTeachingStyle()
	{
		return _teachingStyle;
	}

	public String getFocusEntityName()
	{
		return _focusEntityName;
	}

	public String getFocusEntityType()
	{
		return _focusEntityType;
	}

	public String getFocusIntent()
	{
		return _focusIntent;
	}

	public String getFocusSummary()
	{
		return _focusSummary;
	}

	public String getRelationshipSummary()
	{
		return _relationshipSummary;
	}

	public String getStorySummary()
	{
		return _storySummary;
	}

	public String getMatchedRelationshipSummary()
	{
		return _matchedRelationshipSummary;
	}

	public String getRecentConversationSummary()
	{
		return _recentConversationSummary;
	}

	public String getBehaviorFamilyId()
	{
		return _behaviorFamilyId;
	}

	public String getBehaviorSummary()
	{
		return _behaviorSummary;
	}

	public String getBehaviorRuleSummary()
	{
		return _behaviorRuleSummary;
	}

	public String getStateProfileId()
	{
		return _stateProfileId;
	}

	public String getStateSummary()
	{
		return _stateSummary;
	}

	public String getStateRuleSummary()
	{
		return _stateRuleSummary;
	}

	public String getSalientMemorySummary()
	{
		return _salientMemorySummary;
	}

	public String getRetrievedMemorySummary()
	{
		return _retrievedMemorySummary;
	}

	public String getMemoryPrioritySummary()
	{
		return _memoryPrioritySummary;
	}

	public String getMemoryReflectionSummary()
	{
		return _memoryReflectionSummary;
	}

	public String getMemoryTrustSummary()
	{
		return _memoryTrustSummary;
	}

	public String getMemoryRepairSummary()
	{
		return _memoryRepairSummary;
	}

	public String getMemoryPressureSummary()
	{
		return _memoryPressureSummary;
	}

	public String getMemorySelectionSummary()
	{
		return _memorySelectionSummary;
	}

	public String getSocialLabel()
	{
		return _socialLabel;
	}

	public String getSocialSummary()
	{
		return _socialSummary;
	}

	public int getSocialTrustBias()
	{
		return _socialTrustBias;
	}

	public int getSocialGuardBias()
	{
		return _socialGuardBias;
	}

	public String getSocialActionStance()
	{
		return _socialActionStance;
	}

	public String getSceneSummary()
	{
		return _sceneSummary;
	}

	public String getCurrentRelationshipGoal()
	{
		return _currentRelationshipGoal;
	}

	public String getCurrentRelationshipNeed()
	{
		return _currentRelationshipNeed;
	}

	public String getLastRelationshipTopic()
	{
		return _lastRelationshipTopic;
	}
	
	public String getCurrentZone()
	{
		return _currentZone;
	}
	
	public String getState()
	{
		return _state;
	}
	
	public String getHpBand()
	{
		return _hpBand;
	}
	
	public String getMpBand()
	{
		return _mpBand;
	}
	
	public int getNearbyPlayerCount()
	{
		return _nearbyPlayerCount;
	}
	
	public String getDecisionScope()
	{
		return _decisionScope;
	}
	
	public String getRecentEvent()
	{
		return _recentEvent;
	}
	
	public boolean isChatCooldownReady()
	{
		return _chatCooldownReady;
	}
	
	public boolean isTeleportCooldownReady()
	{
		return _teleportCooldownReady;
	}
	
	public List<String> getAllowedIntents()
	{
		return _allowedIntents;
	}
	
	public List<String> getAllowedZones()
	{
		return _allowedZones;
	}
	
	public List<String> getAvailableLineStyleTags()
	{
		return _availableLineStyleTags;
	}
	
	public List<String> getAvailableTopicTags()
	{
		return _availableTopicTags;
	}

	public String getMessageCategory()
	{
		return _messageCategory;
	}

	public String getRouteLane()
	{
		return _routeLane;
	}

	public String getRouteSpeechAct()
	{
		return _routeSpeechAct;
	}

	public String getRouteKnowledgeNeed()
	{
		return _routeKnowledgeNeed;
	}

	public String getRouteSocialStake()
	{
		return _routeSocialStake;
	}

	public boolean isRouteActionRequested()
	{
		return _routeActionRequested;
	}

	public String getRoutePrimaryTopic()
	{
		return _routePrimaryTopic;
	}

	public List<String> getRouteSecondaryTopics()
	{
		return _routeSecondaryTopics;
	}

	public double getRouteConfidence()
	{
		return _routeConfidence;
	}

	public String getReplyBankSummary()
	{
		return _replyBankSummary;
	}

	public List<String> getReplyBankLines()
	{
		return _replyBankLines;
	}

	public String getKnowledgeType()
	{
		return _knowledgeType;
	}

	public String getKnowledgeSummary()
	{
		return _knowledgeSummary;
	}

	public List<String> getKnowledgeFacts()
	{
		return _knowledgeFacts;
	}

	public List<String> getKnowledgeReplyLines()
	{
		return _knowledgeReplyLines;
	}

	public List<String> getKnowledgeHeuristicLines()
	{
		return _knowledgeHeuristicLines;
	}

	public String getKnowledgeScope()
	{
		return _knowledgeScope;
	}

	public String getKnowledgeConfidenceHint()
	{
		return _knowledgeConfidenceHint;
	}
	
	public String getLastLineTag()
	{
		return _lastLineTag;
	}
	
	public double getBoredomScore()
	{
		return _boredomScore;
	}
	
	public String getIncomingPlayerName()
	{
		return _incomingPlayerName;
	}
	
	public String getIncomingPlayerMessage()
	{
		return _incomingPlayerMessage;
	}

	public String getDefaultInnerState()
	{
		return _defaultInnerState;
	}

	public String getLongTermGoal()
	{
		return _longTermGoal;
	}

	public String getSocialTestStyle()
	{
		return _socialTestStyle;
	}

	public String getTrustCriteria()
	{
		return _trustCriteria;
	}

	public String getRepairStyle()
	{
		return _repairStyle;
	}

	public String getRevealBoundary()
	{
		return _revealBoundary;
	}

	public String getReflectionLens()
	{
		return _reflectionLens;
	}

	public String getActiveObjective()
	{
		return _activeObjective;
	}

	public String getOpenLoops()
	{
		return _openLoops;
	}

	public String getRevealPressure()
	{
		return _revealPressure;
	}

	public String getRelationshipPressure()
	{
		return _relationshipPressure;
	}

	public String getNextBeatHint()
	{
		return _nextBeatHint;
	}

	public String getStateModes()
	{
		return _stateModes;
	}

	public String getReflectionPolicy()
	{
		return _reflectionPolicy;
	}

	public String getMemoryRetrievalPolicy()
	{
		return _memoryRetrievalPolicy;
	}

	public String getGoalPersistencePolicy()
	{
		return _goalPersistencePolicy;
	}

	public String getEmotionTransitionRules()
	{
		return _emotionTransitionRules;
	}

	public String getCallbackStyle()
	{
		return _callbackStyle;
	}

	public String getConflictStyle()
	{
		return _conflictStyle;
	}

	public String getRepairCadence()
	{
		return _repairCadence;
	}
}
