package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerBankFoundationData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerPersonaData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcBehaviorFamily;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaRelationship;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaTemplate;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStateProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStoryArc;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcPersona;

public class FakePlayerPersonaResolver
{
	private static final String DEFAULT_TEMPLATE = "default";
	private static final String DEFAULT_RESPONSE_STYLE = "steady, concise, and natural";
	private static final String DEFAULT_PLAYER_STANCE = "neutral toward adventurers";
	private static final String DEFAULT_CORE_NEED = "maintain presence without breaking immersion";

	private final FpcRegistry _registry;
	private final FakePlayerPersonaData _personaData;
	private final FakePlayerBankFoundationData _bankFoundationData;

	public FakePlayerPersonaResolver(FpcRegistry registry, FakePlayerPersonaData personaData, FakePlayerBankFoundationData bankFoundationData)
	{
		_registry = registry;
		_personaData = personaData;
		_bankFoundationData = bankFoundationData;
	}

	public ResolvedFpcPersona resolve(String fakePlayerIdOrName, String fallbackArchetype)
	{
		return resolve(fakePlayerIdOrName, fallbackArchetype, null);
	}

	public ResolvedFpcPersona resolve(String fakePlayerIdOrName, String fallbackArchetype, String addressedTargetName)
	{
		final FpcDefinition definition = resolveDefinition(fakePlayerIdOrName);
		final String canonicalId = (definition != null) ? definition.getId() : normalizeKey(fakePlayerIdOrName);
		final String archetype = ((definition != null) && !isBlank(definition.getArchetype())) ? definition.getArchetype() : fallbackArchetype;
		final String templateId = ((definition != null) && !isBlank(definition.getPersonaTemplate())) ? definition.getPersonaTemplate() : DEFAULT_TEMPLATE;
		final String speciesTag = ((definition != null) && !isBlank(definition.getSpeciesTag())) ? definition.getSpeciesTag() : "unknown";
		final String familyTag = ((definition != null) && !isBlank(definition.getFamilyTag())) ? definition.getFamilyTag() : "independent";

		final FpcPersonaTemplate template = _personaData.getTemplateById(templateId);
		final FpcPersonaProfile profile = _personaData.getProfileByFpcId(canonicalId);
		final List<FpcPersonaRelationship> relationships = _personaData.getRelationshipsBySourceId(canonicalId);
		final FpcStoryArc storyArc = _personaData.getStoryArcByFpcId(canonicalId);
		final FpcBehaviorFamily behaviorFamily = resolveBehaviorFamily(templateId, archetype);
		final FpcStateProfile stateProfile = resolveStateProfile(canonicalId, behaviorFamily);
		final String defaultInnerState = summarizeDefaultInnerState(profile, stateProfile, behaviorFamily);
		final String longTermGoal = summarizeLongTermGoal(profile, storyArc);
		final String socialTestStyle = summarizeSocialTestStyle(profile, behaviorFamily);
		final String trustCriteria = summarizeTrustCriteria(profile, behaviorFamily);
		final String repairStyle = summarizeRepairStyle(profile, behaviorFamily);
		final String revealBoundary = summarizeRevealBoundary(profile, storyArc);
		final String reflectionLens = summarizeReflectionLens(profile, stateProfile);
		final String activeObjective = summarizeActiveObjective(storyArc);
		final String openLoops = summarizeOpenLoops(storyArc);
		final String revealPressure = summarizeRevealPressure(storyArc);
		final String relationshipPressure = summarizeRelationshipPressure(storyArc);
		final String nextBeatHint = summarizeNextBeatHint(storyArc);
		final String stateModes = summarizeStateModes(stateProfile);
		final String reflectionPolicy = summarizeReflectionPolicy(stateProfile);
		final String memoryRetrievalPolicy = summarizeMemoryRetrievalPolicy(stateProfile);
		final String goalPersistencePolicy = summarizeGoalPersistencePolicy(stateProfile);
		final String emotionTransitionRules = summarizeEmotionTransitionRules(stateProfile);
		final String callbackStyle = summarizeCallbackStyle(behaviorFamily);
		final String conflictStyle = summarizeConflictStyle(behaviorFamily);
		final String repairCadence = summarizeRepairCadence(behaviorFamily);

		return new ResolvedFpcPersona(fakePlayerIdOrName, isBlank(archetype) ? "generic" : archetype, templateId, speciesTag, familyTag, chooseTemplateValue(template != null ? template.getResponseStyle() : null, DEFAULT_RESPONSE_STYLE), chooseTemplateValue(template != null ? template.getPlayerStance() : null, DEFAULT_PLAYER_STANCE), chooseTemplateValue(template != null ? template.getCoreNeed() : null, DEFAULT_CORE_NEED), summarizePersona(template, profile), summarizeSelfKnowledge(profile), summarizePublicMask(profile), summarizeHiddenTruth(profile), profile != null ? profile.getSelfConcept() : "", profile != null ? profile.getCoreWound() : "", profile != null ? profile.getCoreDesire() : "", profile != null ? profile.getLoyaltyAnchor() : "", profile != null ? profile.getResentmentAnchor() : "", profile != null ? profile.getPrivateTaboo() : "", profile != null ? profile.getSpeechAnchor() : "", profile != null ? profile.getPrivateContradiction() : "", profile != null ? profile.getGuidancePosture() : "", profile != null ? profile.getGuidancePriority() : "", profile != null ? profile.getUncertaintyStyle() : "", profile != null ? profile.getRecommendationStyle() : "", profile != null ? profile.getRiskStyle() : "", profile != null ? profile.getTeachingStyle() : "", summarizeRelationships(relationships), summarizeStory(storyArc), summarizeMatchedRelationship(relationships, addressedTargetName, profile, behaviorFamily, stateProfile), (behaviorFamily != null) ? behaviorFamily.getId() : "", summarizeBehaviorFamily(behaviorFamily), summarizeBehaviorRules(behaviorFamily), (stateProfile != null) ? stateProfile.getId() : "", summarizeStateProfile(stateProfile), summarizeStateRules(stateProfile), defaultInnerState, longTermGoal, socialTestStyle, trustCriteria, repairStyle, revealBoundary, reflectionLens, activeObjective, openLoops, revealPressure, relationshipPressure, nextBeatHint, stateModes, reflectionPolicy, memoryRetrievalPolicy, goalPersistencePolicy, emotionTransitionRules, callbackStyle, conflictStyle, repairCadence);
	}

	public String summarizeFocusEntity(String fakePlayerIdOrName, String focusEntityName)
	{
		if (isBlank(fakePlayerIdOrName) || isBlank(focusEntityName))
		{
			return "";
		}

		final FpcDefinition definition = resolveDefinition(fakePlayerIdOrName);
		final String canonicalId = (definition != null) ? definition.getId() : normalizeKey(fakePlayerIdOrName);
		final List<FpcPersonaRelationship> relationships = _personaData.getRelationshipsBySourceId(canonicalId);
		if ((relationships == null) || relationships.isEmpty())
		{
			return "";
		}

		final String normalizedFocus = normalizeKey(focusEntityName);
		FpcPersonaRelationship exactMatch = null;
		FpcPersonaRelationship adventurerFallback = null;
		for (FpcPersonaRelationship relationship : relationships)
		{
			if (relationship == null)
			{
				continue;
			}

			final String target = normalizeKey(relationship.getTargetId());
			if (target.equals(normalizedFocus))
			{
				exactMatch = relationship;
				break;
			}
			if (isAdventurerGroup(normalizedFocus) && "adventurers".equals(target))
			{
				adventurerFallback = relationship;
			}
		}

		final FpcPersonaRelationship selected = (exactMatch != null) ? exactMatch : adventurerFallback;
		if ((selected == null) || isBlank(selected.getSummary()))
		{
			return "";
		}
		return cleanSentence(selected.getSummary());
	}

	public int getPositiveBondIntensity(String fakePlayerIdOrName, String focusEntityName)
	{
		final FpcPersonaRelationship relationship = findRelationship(fakePlayerIdOrName, focusEntityName, true);
		return (relationship == null) ? 0 : Math.max(0, relationship.getIntensity());
	}

	public String summarizePositiveBond(String fakePlayerIdOrName, String focusEntityName)
	{
		final FpcPersonaRelationship relationship = findRelationship(fakePlayerIdOrName, focusEntityName, true);
		if ((relationship == null) || isBlank(relationship.getSummary()))
		{
			return "";
		}
		return cleanSentence(relationship.getSummary());
	}

	private FpcDefinition resolveDefinition(String fakePlayerIdOrName)
	{
		if ((fakePlayerIdOrName == null) || fakePlayerIdOrName.isBlank())
		{
			return null;
		}

		FpcDefinition definition = _registry.getDefinitionById(fakePlayerIdOrName);
		if (definition == null)
		{
			definition = _registry.getDefinitionByName(fakePlayerIdOrName);
		}
		return definition;
	}

	private FpcPersonaRelationship findRelationship(String fakePlayerIdOrName, String focusEntityName, boolean positiveOnly)
	{
		if (isBlank(fakePlayerIdOrName) || isBlank(focusEntityName))
		{
			return null;
		}

		final FpcDefinition definition = resolveDefinition(fakePlayerIdOrName);
		final String canonicalId = (definition != null) ? definition.getId() : normalizeKey(fakePlayerIdOrName);
		final List<FpcPersonaRelationship> relationships = _personaData.getRelationshipsBySourceId(canonicalId);
		if ((relationships == null) || relationships.isEmpty())
		{
			return null;
		}

		final String normalizedFocus = normalizeKey(focusEntityName);
		FpcPersonaRelationship adventurerFallback = null;
		for (FpcPersonaRelationship relationship : relationships)
		{
			if (relationship == null)
			{
				continue;
			}
			if (positiveOnly && !isPositiveBond(relationship))
			{
				continue;
			}

			final String target = normalizeKey(relationship.getTargetId());
			if (target.equals(normalizedFocus))
			{
				return relationship;
			}
			if (isAdventurerGroup(normalizedFocus) && "adventurers".equals(target))
			{
				adventurerFallback = relationship;
			}
		}
		return adventurerFallback;
	}

	private String summarizePersona(FpcPersonaTemplate template, FpcPersonaProfile profile)
	{
		return joinSentences(template != null ? template.getBaseSummary() : null, profile != null ? profile.getOriginSummary() : null, profile != null ? profile.getCreatorSummary() : null);
	}

	private String summarizeSelfKnowledge(FpcPersonaProfile profile)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getSelfKnowledgeSummary(), prefix("Self concept: ", profile.getSelfConcept()), prefix("Core wound: ", profile.getCoreWound()), prefix("Core desire: ", profile.getCoreDesire()), prefix("Loyalty anchor: ", profile.getLoyaltyAnchor()), prefix("Resentment anchor: ", profile.getResentmentAnchor()), prefix("Private taboo: ", profile.getPrivateTaboo()), prefix("Speech anchor: ", profile.getSpeechAnchor()), prefix("Private contradiction: ", profile.getPrivateContradiction()), prefix("Conversation goal: ", profile.getConversationGoal()), prefix("Hidden fear: ", profile.getHiddenFear()));
	}

	private String summarizePublicMask(FpcPersonaProfile profile)
	{
		return (profile == null) ? "" : joinSentences(profile.getPublicMaskSummary());
	}

	private String summarizeHiddenTruth(FpcPersonaProfile profile)
	{
		return (profile == null) ? "" : joinSentences(profile.getHiddenTruthSummary());
	}

	private String summarizeRelationships(List<FpcPersonaRelationship> relationships)
	{
		if ((relationships == null) || relationships.isEmpty())
		{
			return "";
		}

		final StringJoiner joiner = new StringJoiner(" ");
		int count = 0;
		for (FpcPersonaRelationship relationship : relationships)
		{
			if ((relationship == null) || isBlank(relationship.getSummary()))
			{
				continue;
			}
			joiner.add(cleanSentence(relationship.getSummary()));
			count++;
			if (count >= 3)
			{
				break;
			}
		}
		return joiner.toString();
	}

	private String summarizeStory(FpcStoryArc storyArc)
	{
		if (storyArc == null)
		{
			return "";
		}
		return joinSentences(storyArc.getChapterSummary(), prefix("Current focus: ", storyArc.getCurrentFocus()), prefix("Current conflict: ", storyArc.getCurrentConflict()), prefix("Active objective: ", storyArc.getActiveObjective()), prefix("Next beat: ", storyArc.getNextBeatHint()), prefix("Reveal pressure: ", storyArc.getRevealPressure()));
	}

	private String summarizeMatchedRelationship(List<FpcPersonaRelationship> relationships, String addressedTargetName, FpcPersonaProfile profile, FpcBehaviorFamily behaviorFamily, FpcStateProfile stateProfile)
	{
		if (isBlank(addressedTargetName))
		{
			return "";
		}

		FpcPersonaRelationship exactMatch = null;
		FpcPersonaRelationship adventurerFallback = null;
		final String normalizedTarget = normalizeKey(addressedTargetName);
		for (FpcPersonaRelationship relationship : (relationships != null) ? relationships : List.<FpcPersonaRelationship>of())
		{
			if (relationship == null)
			{
				continue;
			}

			final String relationshipTarget = normalizeKey(relationship.getTargetId());
			if (relationshipTarget.equals(normalizedTarget))
			{
				exactMatch = relationship;
				break;
			}
			if ("adventurers".equals(relationshipTarget))
			{
				adventurerFallback = relationship;
			}
		}

		if (exactMatch != null)
		{
			return "Current addressee " + addressedTargetName.trim() + " matches this relationship cue: " + cleanSentence(exactMatch.getSummary());
		}
		if (adventurerFallback != null)
		{
			return "Current addressee " + addressedTargetName.trim() + " fits this adventurer-facing cue: " + cleanSentence(adventurerFallback.getSummary());
		}
		final String fallbackCue = summarizeFallbackRelationshipCue(profile, behaviorFamily, stateProfile);
		if (!isBlank(fallbackCue))
		{
			return "Current addressee " + addressedTargetName.trim() + " is being evaluated through this live fallback cue: " + fallbackCue;
		}
		return "";
	}

	private String summarizeFallbackRelationshipCue(FpcPersonaProfile profile, FpcBehaviorFamily behaviorFamily, FpcStateProfile stateProfile)
	{
		final String socialTest = firstSentence(profile != null ? profile.getSocialTestStyle() : null);
		final String trust = firstSentence(profile != null ? profile.getTrustCriteria() : null);
		if (!isBlank(socialTest) && !isBlank(trust))
		{
			return cleanSentence(socialTest) + " Trust starts with " + lowerLeadingCharacter(trimTerminalPunctuation(trust)) + ".";
		}
		if (!isBlank(trust))
		{
			return "Trust starts with " + lowerLeadingCharacter(trimTerminalPunctuation(trust)) + ".";
		}
		if (!isBlank(socialTest))
		{
			return cleanSentence(socialTest);
		}
		if ((behaviorFamily != null) && !isBlank(behaviorFamily.getSummary()))
		{
			return cleanSentence(firstSentence(behaviorFamily.getSummary()));
		}
		if ((stateProfile != null) && !isBlank(stateProfile.getSummary()))
		{
			return cleanSentence(firstSentence(stateProfile.getSummary()));
		}
		return "";
	}

	private FpcBehaviorFamily resolveBehaviorFamily(String personaTemplate, String archetype)
	{
		FpcBehaviorFamily best = null;
		int bestScore = Integer.MIN_VALUE;
		for (FpcBehaviorFamily family : _bankFoundationData.getBehaviorFamilies())
		{
			int score = 0;
			score += scorePipeMatch(family.getLinkedPersonaTemplates(), personaTemplate) * 3;
			score += scorePipeMatch(family.getLinkedArchetypes(), archetype) * 2;
			score += scoreTokenOverlap(family.getLinkedArchetypes(), personaTemplate);
			score += scoreTokenOverlap(family.getLinkedPersonaTemplates(), archetype);
			if (score > bestScore)
			{
				best = family;
				bestScore = score;
			}
		}
		return (bestScore > 0) ? best : null;
	}

	private FpcStateProfile resolveStateProfile(String fakePlayerId, FpcBehaviorFamily behaviorFamily)
	{
		FpcStateProfile best = null;
		int bestScore = Integer.MIN_VALUE;
		final String familyId = (behaviorFamily != null) ? behaviorFamily.getId() : "";
		for (FpcStateProfile profile : _bankFoundationData.getStateProfiles())
		{
			int score = scorePipeMatch(profile.getLinkedFpcIds(), fakePlayerId) * 5;
			score += scorePipeMatch(profile.getLinkedFamilies(), familyId) * 3;
			if (containsPipeValue(profile.getLinkedFpcIds(), "default"))
			{
				score += 5;
			}
			if (score > bestScore)
			{
				best = profile;
				bestScore = score;
			}
		}
		return (bestScore > 0) ? best : null;
	}

	private String summarizeBehaviorFamily(FpcBehaviorFamily family)
	{
		if (family == null)
		{
			return "";
		}
		return joinSentences(family.getSummary(), prefix("Motives: ", humanizePipe(family.getMotivationWeights())), prefix("Temperament: ", humanizePipe(family.getTemperamentProfile())), prefix("Callback style: ", family.getCallbackStyle()));
	}

	private String summarizeBehaviorRules(FpcBehaviorFamily family)
	{
		if (family == null)
		{
			return "";
		}
		return joinSentences(prefix("Etiquette: ", humanizePipe(family.getSocialEtiquette())), prefix("Tactics: ", humanizePipe(family.getTacticalPatterns())), prefix("Speech: ", humanizePipe(family.getSpeechProfile())), prefix("Memory style: ", humanizePipe(family.getMemoryStyle())), prefix("Conflict style: ", family.getConflictStyle()), prefix("Repair cadence: ", family.getRepairCadence()));
	}

	private String summarizeStateProfile(FpcStateProfile profile)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getSummary(), prefix("Needs: ", humanizePipe(profile.getActiveNeeds())), prefix("Personality sliders: ", humanizePipe(profile.getPersonalitySliders())), prefix("State modes: ", profile.getStateModes()), prefix("Reflection policy: ", profile.getReflectionPolicy()));
	}

	private String summarizeStateRules(FpcStateProfile profile)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(prefix("Value rules: ", humanizePipe(profile.getValueRules())), prefix("Relationship axes: ", humanizePipe(profile.getRelationshipAxes())), prefix("Memory windows: ", humanizePipe(profile.getMemoryWindows())), prefix("Emotional axes: ", humanizePipe(profile.getEmotionalAxes())), prefix("Memory retrieval policy: ", profile.getMemoryRetrievalPolicy()), prefix("Goal persistence policy: ", profile.getGoalPersistencePolicy()), prefix("Emotion transitions: ", profile.getEmotionTransitionRules()));
	}

	private String summarizeDefaultInnerState(FpcPersonaProfile profile, FpcStateProfile stateProfile, FpcBehaviorFamily behaviorFamily)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getDefaultInnerState(), profile.getSelfKnowledgeSummary(), profile.getConversationGoal(), prefix("State modes: ", stateProfile != null ? stateProfile.getStateModes() : null), prefix("Callback style: ", behaviorFamily != null ? behaviorFamily.getCallbackStyle() : null));
	}

	private String summarizeLongTermGoal(FpcPersonaProfile profile, FpcStoryArc storyArc)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getLongTermGoal(), profile.getConversationGoal(), storyArc != null ? storyArc.getActiveObjective() : null);
	}

	private String summarizeSocialTestStyle(FpcPersonaProfile profile, FpcBehaviorFamily behaviorFamily)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getSocialTestStyle(), prefix("Social etiquette: ", behaviorFamily != null ? behaviorFamily.getSocialEtiquette() : null));
	}

	private String summarizeTrustCriteria(FpcPersonaProfile profile, FpcBehaviorFamily behaviorFamily)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getTrustCriteria(), profile.getLoyaltyAnchor(), prefix("Memory style: ", behaviorFamily != null ? behaviorFamily.getMemoryStyle() : null));
	}

	private String summarizeRepairStyle(FpcPersonaProfile profile, FpcBehaviorFamily behaviorFamily)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getRepairStyle(), prefix("Repair cadence: ", behaviorFamily != null ? behaviorFamily.getRepairCadence() : null));
	}

	private String summarizeRevealBoundary(FpcPersonaProfile profile, FpcStoryArc storyArc)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getRevealBoundary(), prefix("Reveal pressure: ", storyArc != null ? storyArc.getRevealPressure() : null), prefix("Relationship pressure: ", storyArc != null ? storyArc.getRelationshipPressure() : null));
	}

	private String summarizeReflectionLens(FpcPersonaProfile profile, FpcStateProfile stateProfile)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(profile.getReflectionLens(), prefix("Reflection policy: ", stateProfile != null ? stateProfile.getReflectionPolicy() : null));
	}

	private String summarizeActiveObjective(FpcStoryArc storyArc)
	{
		if (storyArc == null)
		{
			return "";
		}
		return joinSentences(storyArc.getActiveObjective(), storyArc.getCurrentFocus());
	}

	private String summarizeOpenLoops(FpcStoryArc storyArc)
	{
		return (storyArc == null) ? "" : joinSentences(prefix("Open loops: ", storyArc.getOpenLoops()));
	}

	private String summarizeRevealPressure(FpcStoryArc storyArc)
	{
		return (storyArc == null) ? "" : joinSentences(prefix("Reveal pressure: ", storyArc.getRevealPressure()));
	}

	private String summarizeRelationshipPressure(FpcStoryArc storyArc)
	{
		return (storyArc == null) ? "" : joinSentences(prefix("Relationship pressure: ", storyArc.getRelationshipPressure()));
	}

	private String summarizeNextBeatHint(FpcStoryArc storyArc)
	{
		return (storyArc == null) ? "" : joinSentences(prefix("Next beat: ", storyArc.getNextBeatHint()));
	}

	private String summarizeStateModes(FpcStateProfile profile)
	{
		if (profile == null)
		{
			return "";
		}
		return joinSentences(prefix("State modes: ", profile.getStateModes()), prefix("Personality sliders: ", humanizePipe(profile.getPersonalitySliders())));
	}

	private String summarizeReflectionPolicy(FpcStateProfile profile)
	{
		return (profile == null) ? "" : joinSentences(prefix("Reflection policy: ", profile.getReflectionPolicy()));
	}

	private String summarizeMemoryRetrievalPolicy(FpcStateProfile profile)
	{
		return (profile == null) ? "" : joinSentences(prefix("Memory retrieval policy: ", profile.getMemoryRetrievalPolicy()));
	}

	private String summarizeGoalPersistencePolicy(FpcStateProfile profile)
	{
		return (profile == null) ? "" : joinSentences(prefix("Goal persistence policy: ", profile.getGoalPersistencePolicy()));
	}

	private String summarizeEmotionTransitionRules(FpcStateProfile profile)
	{
		return (profile == null) ? "" : joinSentences(prefix("Emotion transitions: ", profile.getEmotionTransitionRules()));
	}

	private String summarizeCallbackStyle(FpcBehaviorFamily family)
	{
		return (family == null) ? "" : joinSentences(prefix("Callback style: ", family.getCallbackStyle()));
	}

	private String summarizeConflictStyle(FpcBehaviorFamily family)
	{
		return (family == null) ? "" : joinSentences(prefix("Conflict style: ", family.getConflictStyle()));
	}

	private String summarizeRepairCadence(FpcBehaviorFamily family)
	{
		return (family == null) ? "" : joinSentences(prefix("Repair cadence: ", family.getRepairCadence()));
	}

	private String chooseTemplateValue(String value, String fallback)
	{
		return isBlank(value) ? fallback : value;
	}

	private String joinSentences(String... values)
	{
		final StringJoiner joiner = new StringJoiner(" ");
		for (String value : values)
		{
			if (!isBlank(value))
			{
				joiner.add(cleanSentence(value));
			}
		}
		return joiner.toString();
	}

	private String prefix(String prefix, String value)
	{
		return isBlank(value) ? "" : prefix + cleanSentence(value);
	}

	private String cleanSentence(String value)
	{
		final String cleaned = value.trim().replaceAll("\\s+", " ");
		if (cleaned.isEmpty())
		{
			return cleaned;
		}
		final char last = cleaned.charAt(cleaned.length() - 1);
		return ((last == '.') || (last == '!') || (last == '?')) ? cleaned : (cleaned + ".");
	}

	private String firstSentence(String value)
	{
		if (isBlank(value))
		{
			return "";
		}
		final String cleaned = value.trim().replaceAll("\\s+", " ");
		final String[] parts = cleaned.split("(?<=[.!?])\\s+", 2);
		return trimTerminalPunctuation((parts.length > 0) ? parts[0] : cleaned).trim();
	}

	private String trimTerminalPunctuation(String value)
	{
		if (isBlank(value))
		{
			return "";
		}
		String result = value.trim();
		while (!result.isEmpty())
		{
			final char last = result.charAt(result.length() - 1);
			if ((last != '.') && (last != '!') && (last != '?'))
			{
				break;
			}
			result = result.substring(0, result.length() - 1).trim();
		}
		return result;
	}

	private String lowerLeadingCharacter(String value)
	{
		if (isBlank(value))
		{
			return "";
		}
		return Character.toLowerCase(value.charAt(0)) + value.substring(1);
	}

	private boolean isBlank(String value)
	{
		return (value == null) || value.isBlank();
	}

	private String normalizeKey(String value)
	{
		return isBlank(value) ? "" : value.toLowerCase(Locale.ROOT);
	}

	private int scorePipeMatch(String pipeValues, String candidate)
	{
		final String normalizedCandidate = normalizeKey(candidate);
		if (normalizedCandidate.isBlank())
		{
			return 0;
		}
		for (String part : splitPipe(pipeValues))
		{
			if (part.equals(normalizedCandidate))
			{
				return 100;
			}
		}
		return 0;
	}

	private int scoreTokenOverlap(String pipeValues, String candidate)
	{
		final List<String> candidateTokens = tokenize(candidate);
		if (candidateTokens.isEmpty())
		{
			return 0;
		}

		int score = 0;
		for (String part : splitPipe(pipeValues))
		{
			for (String token : tokenize(part))
			{
				if (candidateTokens.contains(token))
				{
					score += 15;
				}
			}
		}
		return score;
	}

	private boolean containsPipeValue(String pipeValues, String expected)
	{
		final String normalizedExpected = normalizeKey(expected);
		for (String part : splitPipe(pipeValues))
		{
			if (part.equals(normalizedExpected))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isPositiveBond(FpcPersonaRelationship relationship)
	{
		if (relationship == null)
		{
			return false;
		}

		final String stance = normalizeKey(relationship.getStance());
		final String bondType = normalizeKey(relationship.getBondType());
		if (stance.contains("devoted") || stance.contains("reverent") || stance.contains("protective") || stance.contains("hopeful") || stance.contains("warm") || stance.contains("trust") || stance.contains("grateful") || stance.contains("loving") || stance.contains("caring") || stance.contains("friendly"))
		{
			return true;
		}
		return bondType.contains("creator") || bondType.contains("anchor") || bondType.contains("companion");
	}

	private boolean isAdventurerGroup(String normalizedFocus)
	{
		return "adventurers".equals(normalizedFocus) || "adventurer".equals(normalizedFocus) || "players".equals(normalizedFocus) || "player".equals(normalizedFocus) || "people".equals(normalizedFocus) || "strangers".equals(normalizedFocus);
	}

	private List<String> splitPipe(String value)
	{
		if (isBlank(value))
		{
			return List.of();
		}
		return java.util.Arrays.stream(value.split("\\|")).map(this::normalizeKey).filter(part -> !part.isBlank()).toList();
	}

	private List<String> tokenize(String value)
	{
		if (isBlank(value))
		{
			return List.of();
		}
		return java.util.Arrays.stream(normalizeKey(value).split("[_\\-\\s]+")).filter(part -> !part.isBlank()).toList();
	}

	private String humanizePipe(String value)
	{
		if (isBlank(value))
		{
			return "";
		}
		final String cleaned = value.replace('|', ';').replace('_', ' ').replace("=", ": ");
		return cleaned.replaceAll("\\s+", " ").trim();
	}
}
