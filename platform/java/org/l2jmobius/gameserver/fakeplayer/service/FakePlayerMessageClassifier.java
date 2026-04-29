package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.fakeplayer.model.FpcRouteProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRoutingRule;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSignalBank;

public class FakePlayerMessageClassifier
{
	private static final Set<String> TYPO_RECOVERY_TOKENS = Set.of(
		"people",
		"person",
		"player",
		"players",
		"adventurer",
		"adventurers",
		"strangers",
		"truly",
		"really",
		"honestly",
		"actually",
		"think",
		"thinks",
		"feeling",
		"feel",
		"fear",
		"fears",
		"becoming",
		"distance",
		"distant",
		"guarded",
		"lonely",
		"happy",
		"afraid",
		"abandoned",
		"hiding",
		"hidden",
		"creator",
		"story",
		"origin",
		"believe",
		"belief",
		"value",
		"matters",
		"trust",
		"forgive",
		"forgives",
		"forgiveness",
		"protect",
		"protects",
		"protecting",
		"choose",
		"chooses",
		"choosing",
		"important",
		"matter",
		"care",
		"cares",
		"need",
		"needs",
		"miss",
		"left",
		"remain",
		"resent",
		"angry",
		"upset",
		"blame",
		"apology",
		"sorry",
		"repair",
		"return",
		"returns",
		"thanks",
		"thank",
		"threat",
		"kill",
		"hurt",
		"hate");
	private static final Set<String> TYPO_RECOVERY_SHORT_TOKENS = Set.of("care", "need", "miss", "kill", "hurt", "hate");
	private volatile FpcSocialSignalBank _socialSignalBank;
	private volatile List<FpcRoutingRule> _routingRules = List.of();

	public FakePlayerMessageClassifier()
	{
		this(FpcSocialSignalBank.defaults());
	}

	public FakePlayerMessageClassifier(FpcSocialSignalBank socialSignalBank)
	{
		_socialSignalBank = (socialSignalBank != null) ? socialSignalBank : FpcSocialSignalBank.defaults();
	}

	public void setSocialSignalBank(FpcSocialSignalBank socialSignalBank)
	{
		_socialSignalBank = (socialSignalBank != null) ? socialSignalBank : FpcSocialSignalBank.defaults();
	}

	public void setRoutingRules(Collection<FpcRoutingRule> routingRules)
	{
		_routingRules = (routingRules == null) ? List.of() : List.copyOf(routingRules);
	}

	public String classify(String incomingText)
	{
		return classifyRouteProfile(incomingText, List.of(), "", "").getMessageCategory();
	}

	public FpcRouteProfile classifyRouteProfile(String incomingText, Iterable<String> candidateNames, String speakerName, String incomingPlayerName)
	{
		final String normalized = normalize(incomingText);
		final String currentUtterance = extractCurrentUtterance(normalized);
		if (currentUtterance.isBlank())
		{
			return FpcRouteProfile.EMPTY;
		}
		final String baseCategory = classifyBaseMessageCategory(normalized, currentUtterance);
		final String focusEntity = extractFocusEntity(incomingText, candidateNames, speakerName, incomingPlayerName);
		final String focusIntent = classifyFocusIntent(incomingText);
		final String messageCategory = shouldPromoteFocusIntent(baseCategory, focusIntent, focusEntity) ? focusIntent : baseCategory;
		final String knowledgeType = classifyKnowledgeTypeInternal(normalized, currentUtterance, messageCategory);
		final List<String> routeTopics = collectRouteTopics(normalized, messageCategory, knowledgeType);
		final String primaryTopic = routeTopics.isEmpty() ? "none" : routeTopics.get(0);
		final List<String> secondaryTopics = routeTopics.size() <= 1 ? List.of() : routeTopics.subList(1, routeTopics.size());
		final String speechAct = deriveSpeechAct(normalized, messageCategory);
		final String knowledgeNeed = deriveKnowledgeNeed(messageCategory, knowledgeType);
		final String routeLane = deriveRouteLane(messageCategory, knowledgeNeed);
		final String socialStake = deriveSocialStake(messageCategory, speechAct);
		final boolean actionRequested = isActionRequest(normalized, speechAct);
		final double confidence = estimateRouteConfidence(messageCategory, knowledgeType, primaryTopic);
		return new FpcRouteProfile(routeLane, speechAct, knowledgeNeed, socialStake, actionRequested, messageCategory, knowledgeType, primaryTopic, secondaryTopics, confidence);
	}

	public String classifyKnowledgeType(String incomingText)
	{
		return classifyRouteProfile(incomingText, List.of(), "", "").getKnowledgeType();
	}

	public String classifyFocusIntent(String incomingText)
	{
		final String normalized = normalize(incomingText);
		if (normalized.isBlank())
		{
			return "";
		}
		final String currentUtterance = extractCurrentUtterance(normalized);
		final String previousFocusEntity = extractPreviousFocusEntity(normalized);
		final String previousFocusIntent = extractPreviousFocusIntent(normalized);
		final String bondProbeType = classifyBondProbeType(currentUtterance);
		if (!bondProbeType.isBlank())
		{
			return bondProbeType;
		}
		final String routedFocusIntent = routingRuleOverride("focus_intent", normalized, currentUtterance);
		if (!routedFocusIntent.isBlank())
		{
			return routedFocusIntent;
		}
		if (!previousFocusEntity.isBlank())
		{
			if (isCarriedEntityReasonFollowUp(currentUtterance))
			{
				return "entity_reason";
			}
			if (isCarriedEntityRelationshipStatement(currentUtterance))
			{
				return "entity_relationship";
			}
		}
		if (isEntityRelationshipQuestion(normalized))
		{
			return "entity_relationship";
		}
		if (isEntityRelationshipStatement(normalized))
		{
			return "entity_relationship";
		}
		if (isEntityStatusQuestion(normalized))
		{
			return "entity_status";
		}
		if (isEntityStoryQuestion(normalized))
		{
			return "entity_story";
		}
		if (isEntityReasonQuestion(normalized))
		{
			return "entity_reason";
		}
		if (isEntityMemoryQuestion(normalized))
		{
			return "entity_memory";
		}
		return "";
	}

	public String classifyWithFocus(String incomingText, Iterable<String> candidateNames, String speakerName, String incomingPlayerName)
	{
		return classifyRouteProfile(incomingText, candidateNames, speakerName, incomingPlayerName).getMessageCategory();
	}

	public FpcRouteProfile overrideRouteProfile(String incomingText, String messageCategory, String knowledgeType)
	{
		final String normalized = normalize(incomingText);
		if (normalized.isBlank())
		{
			return FpcRouteProfile.EMPTY;
		}
		final String resolvedMessageCategory = ((messageCategory == null) || messageCategory.isBlank()) ? "unknown" : messageCategory.trim().toLowerCase(Locale.ROOT);
		final String resolvedKnowledgeType = coerceKnowledgeTypeForCategory(resolvedMessageCategory, knowledgeType);
		final List<String> routeTopics = collectRouteTopics(normalized, resolvedMessageCategory, resolvedKnowledgeType);
		final String primaryTopic = routeTopics.isEmpty() ? "none" : routeTopics.get(0);
		final List<String> secondaryTopics = routeTopics.size() <= 1 ? List.of() : routeTopics.subList(1, routeTopics.size());
		final String speechAct = deriveSpeechAct(normalized, resolvedMessageCategory);
		final String knowledgeNeed = deriveKnowledgeNeed(resolvedMessageCategory, resolvedKnowledgeType);
		final String routeLane = deriveRouteLane(resolvedMessageCategory, knowledgeNeed);
		final String socialStake = deriveSocialStake(resolvedMessageCategory, speechAct);
		final boolean actionRequested = isActionRequest(normalized, speechAct);
		final double confidence = estimateRouteConfidence(resolvedMessageCategory, resolvedKnowledgeType, primaryTopic);
		return new FpcRouteProfile(routeLane, speechAct, knowledgeNeed, socialStake, actionRequested, resolvedMessageCategory, resolvedKnowledgeType, primaryTopic, secondaryTopics, confidence);
	}

	private String classifyBaseMessageCategory(String normalized, String currentUtterance)
	{
		final String bondProbeType = classifyBondProbeType(currentUtterance);
		if (isThreatSignal(currentUtterance, ""))
		{
			return "threat";
		}
		if (isDirectFlame(currentUtterance))
		{
			return "insult";
		}
		final String routedCategory = routingRuleOverride("message_category", normalized, currentUtterance);
		if (!routedCategory.isBlank())
		{
			return routedCategory;
		}
		if (!bondProbeType.isBlank() || isPlayerBondOpinionPrompt(currentUtterance))
		{
			return "bond_opinion";
		}
		if (isDistrustSignal(currentUtterance, ""))
		{
			return "distrust";
		}
		if (isResentmentSignal(currentUtterance, ""))
		{
			return "resentment";
		}
		if (isAbandonmentSignal(currentUtterance, ""))
		{
			return "abandonment";
		}
		if (isApologySignal(currentUtterance))
		{
			return "apology";
		}
		if (isGratitudeSignal(currentUtterance))
		{
			return "thanks";
		}
		if (isRepairSignal(currentUtterance))
		{
			return "repair";
		}
		if (isAffectionSignal(currentUtterance, ""))
		{
			return "affection";
		}
		if (isRespectSignal(currentUtterance, ""))
		{
			return "respect";
		}
		if (isPraiseSignal(currentUtterance, ""))
		{
			return "praise";
		}
		if (isReassuranceSignal(currentUtterance, ""))
		{
			return "reassurance";
		}
		if (isSelfIdentityQuestion(currentUtterance))
		{
			return "self_identity";
		}
		if (isSelfStoryQuestion(currentUtterance))
		{
			return "self_story";
		}
		if (isCreatorOpinionPrompt(currentUtterance))
		{
			return "creator_opinion";
		}
		if (isCommunityOpinionQuestion(currentUtterance))
		{
			return "smalltalk";
		}
		if (isAmbientRespectSignal(currentUtterance))
		{
			return "respect";
		}
		if (isSelfBeliefQuestion(currentUtterance))
		{
			return "self_belief";
		}
		if (isSelfPreferenceQuestion(currentUtterance))
		{
			return "self_preference";
		}
		if (isSelfStateReflectionQuestion(currentUtterance))
		{
			return "self_state_reflection";
		}
		if (containsPhrase(normalized, "what happened", "your story", "what are you waiting for", "why are you waiting", "why do you wait"))
		{
			return "quest_story";
		}
		if (isRelationshipProbe(normalized))
		{
			return "relationship_probe";
		}
		if (isPvpConflictQuestion(normalized))
		{
			return "pvp_conflict";
		}
		if (isProgressionStrengthQuestion(normalized))
		{
			return "progression_strength";
		}
		if (isSocialInviteQuestion(normalized))
		{
			return "social_invite";
		}
		if (isPartyFarmQuestion(normalized))
		{
			return "party_farm";
		}
		if (isCompanionshipQuestion(normalized))
		{
			return "companionship";
		}
		if (isFarmingRouteQuestion(normalized))
		{
			return "farm";
		}
		if (containsPhrase(normalized, "help", "quest", "what should i do", "what do i do"))
		{
			return "help";
		}
		if (containsPhrase(normalized, "party", "group", "join me", "go together", "come with me", "with me", "wanna go together", "want to go together"))
		{
			return "party";
		}
		if (containsPhrase(normalized, "where", "lost", "gate", "town", "start", "which way", "how do i get", "how i get", "how i teleport", "teleport to"))
		{
			return "direction";
		}
		if (containsPhrase(normalized, "weather", "pleasant day", "beautiful day"))
		{
			return "weather";
		}
		if (containsPhrase(normalized, "farm", "exp", "level", "grind", "hunt", "spot", "aoe farm"))
		{
			return "farm";
		}
		if (containsPhrase(normalized, "how are", "you ok", "you okay", "howve you been", "how's it going", "hows it going", "how is it going"))
		{
			return "status";
		}
		if (containsPhrase(normalized, "remember", "missed", "miss you", "did you miss", "forgot"))
		{
			return "memory";
		}
		if (isLiteralNameQuestion(currentUtterance))
		{
			return "name";
		}
		if (containsPhrase(normalized, "danger", "safe", "unsafe", "guards", "suspicious"))
		{
			return "danger";
		}
		if (containsPhrase(normalized, "hello", "hi", "hey", "yo", "good morning", "goodmorning", "good evening", "goodevening", "good night", "goodnight"))
		{
			return "greeting";
		}
		if (containsPhrase(normalized, "essence", "warg", "wolf form", "wp", "lineage", "adena", "enchant", "upgrade", "gear", "class", "spellbook", "spellbooks", "book", "books", "heroic", "legendary", "rare spellbook", "rare book", "giran seal", "seal", "aden essence", "cruma", "cruma tower", "ruins of agony", "ruins of despair", "abandoned camp", "wasteland", "southern wasteland", "soulshot", "spirit ore", "auto hunting", "auto-hunting", "teleport", "orven", "transcendent", "pet", "pets", "orc fortress", "iron heart", "ironheart", "wolf server", "wolf pet", "assassin", "cardinal", "skill", "skills", "item", "items", "weapon", "weapons", "armor", "armour", "resource", "resources", "material", "materials"))
		{
			return "lineage_lore";
		}
		return "smalltalk";
	}

	private String classifyKnowledgeTypeInternal(String normalized, String currentUtterance, String messageCategory)
	{
		final String routedKnowledgeType = routingRuleOverride("knowledge_type", normalized, currentUtterance);
		if (!routedKnowledgeType.isBlank())
		{
			return routedKnowledgeType;
		}
		if ("social_invite".equalsIgnoreCase(messageCategory))
		{
			return "unknown";
		}
		if (containsPhrase(normalized, "wolf server", "wolf pet", "difference between warg and wolf", "warg and wolf", "warg vs wolf"))
		{
			return "server_rules";
		}
		if (isTravelDestinationQuestion(normalized))
		{
			return "travel_destination";
		}
		if (isClassChoiceQuestion(normalized))
		{
			return "class_choice";
		}
		if (isProgressionStrengthQuestion(normalized))
		{
			return "progression_strength";
		}
		if (isFarmingRouteQuestion(normalized))
		{
			return "farming";
		}
		if (containsPhrase(normalized, "where should i start", "how should i start", "what should i do first", "beginner", "start route", "first step", "opening route", "where should i go", "where do i go", "where can i go", "where next"))
		{
			return "progression_route";
		}
		if (containsPhrase(normalized, "wolf form", "wp", "2nd class", "second class", "class change", "when does warg get wolf form"))
		{
			return "class_progression";
		}
		if (containsPhrase(normalized, "what is warg", "warg class", "male human", "fist weapon", "sigil", "what does warg feel like", "what is warg like", "what is cardinal", "cardinal class"))
		{
			return "class_identity";
		}
		if (containsPhrase(normalized, "skill", "skills", "assassin", "cardinal", "what skills", "what does warg have", "what does assassin have", "kit"))
		{
			return "class_skills";
		}
		if (containsPhrase(normalized, "auto hunting", "auto-hunting", "teleport", "orven", "transcendent", "pet", "pets", "orc fortress", "essence", "warg update"))
		{
			return "system_basics";
		}
		if (containsPhrase(normalized, "heroic", "legendary", "rare spellbook", "rare book", "spellbook", "spellbooks", "book", "books", "enchant", "upgrade", "gear", "item", "items", "weapon", "weapons", "armor", "armour", "resource", "resources", "material", "materials"))
		{
			return "itemization";
		}
		if (containsPhrase(normalized, "giran seal", "giran seals", "adena essence", "l coin", "l coin", "sp"))
		{
			return "currency";
		}
		if (isGenericSupportFarmQuestion(normalized))
		{
			return "unknown";
		}
		if (containsPhrase(normalized, "farm", "grind", "hunt", "spot", "99", "lvl 99", "level 99", "cruma 2", "cruma2", "magical tablet", "magical tablets"))
		{
			return "farming";
		}
		return "unknown";
	}

	private String coerceKnowledgeTypeForCategory(String messageCategory, String knowledgeType)
	{
		final String normalizedCategory = (messageCategory == null) ? "" : messageCategory.trim().toLowerCase(Locale.ROOT);
		if (normalizedCategory.isBlank() || "unknown".equals(normalizedCategory))
		{
			return ((knowledgeType == null) || knowledgeType.isBlank()) ? "unknown" : knowledgeType.trim().toLowerCase(Locale.ROOT);
		}
		switch (normalizedCategory)
		{
			case "threat":
			case "insult":
			case "distrust":
			case "resentment":
			case "abandonment":
			case "apology":
			case "thanks":
			case "repair":
			case "affection":
			case "respect":
			case "praise":
			case "reassurance":
			case "social_invite":
			case "companionship":
			case "relationship_probe":
			case "self_identity":
			case "self_story":
			case "self_belief":
			case "self_preference":
			case "self_state_reflection":
			case "creator_opinion":
			case "bond_opinion":
				return "unknown";
			default:
				return ((knowledgeType == null) || knowledgeType.isBlank()) ? "unknown" : knowledgeType.trim().toLowerCase(Locale.ROOT);
		}
	}

	private boolean isSocialInviteQuestion(String normalized)
	{
		if (containsPhrase(normalized, "join our clan", "join my clan", "join the clan", "join clan", "join our guild", "join my guild", "join guild", "clan recruit", "clan recruiting", "we are recruiting", "we're recruiting", "wanna join our clan", "want to join our clan", "would you join our clan", "need one more for party", "need 1 more for party", "wanna join us", "want to join us", "would you join us", "you in or what", "you in", "party spot open", "party slot open", "slot open", "room for one more", "room for 1 more"))
		{
			return true;
		}
		final boolean hasClanTopic = containsPhrase(normalized, "clan", "guild", "ally");
		final boolean hasInviteCue = containsPhrase(normalized, "join", "recruit", "recruiting", "invite", "invited", "looking for", "need", "wanna", "want to", "would you", "you in", "come with us", "run with us", "roll with us", "spot open", "slot open", "room for one more", "room for 1 more");
		final boolean hasPartyInvite = containsPhrase(normalized, "party", "group", "join us", "join our party", "join our group", "come with us", "need one more", "need 1 more", "spot open", "slot open", "room for one more", "room for 1 more") && !isPartyFarmQuestion(normalized);
		return (hasClanTopic && hasInviteCue) || hasPartyInvite;
	}

	private boolean isCommunityOpinionQuestion(String normalized)
	{
		return (containsPhrase(normalized, "server population", "population here", "population on server", "server pop") && containsPhrase(normalized, "happy", "good", "bad", "alive", "dead", "healthy"))
			|| (containsPhrase(normalized, "community here", "community there") && containsPhrase(normalized, "healthy", "alive", "good", "bad"));
	}

	private boolean isAmbientRespectSignal(String normalized)
	{
		return containsPhrase(normalized, "respect for the grind", "respect the grind")
			|| (containsPhrase(normalized, "respect") && containsPhrase(normalized, "grind"));
	}

	private List<String> collectRouteTopics(String normalized, String messageCategory, String knowledgeType)
	{
		final LinkedHashSet<String> topics = new LinkedHashSet<>();
		if (isCommunityOpinionQuestion(normalized))
		{
			topics.add("community");
		}
		if (containsPhrase(normalized, "clan", "guild", "ally", "recruit", "recruiting"))
		{
			topics.add("clan");
		}
		if (containsPhrase(normalized, "party", "group", "cc"))
		{
			topics.add("party");
		}
		if (containsPhrase(normalized, "farm", "exp", "xp", "adena", "grind", "hunt", "spot", "aoe"))
		{
			topics.add("farm");
		}
		if (containsPhrase(normalized, "pvp", "pk", "war", "siege", "zerg", "conflict"))
		{
			topics.add("pvp");
		}
		if (containsPhrase(normalized, "stronger", "level", "gear", "upgrade", "class"))
		{
			topics.add("progression");
		}
		if (containsPhrase(normalized, "where", "teleport", "gate", "gatekeeper", "town", "route"))
		{
			topics.add("travel");
		}
		if (containsPhrase(normalized, "weather", "sky"))
		{
			topics.add("weather");
		}
		if (containsPhrase(normalized, "help", "quest"))
		{
			topics.add("help");
		}
		if ("unknown".equalsIgnoreCase(knowledgeType) && topics.isEmpty())
		{
			if ("smalltalk".equalsIgnoreCase(messageCategory) || "greeting".equalsIgnoreCase(messageCategory) || "status".equalsIgnoreCase(messageCategory))
			{
				topics.add("social");
			}
			else if ("memory".equalsIgnoreCase(messageCategory))
			{
				topics.add("memory");
			}
		}
		return new ArrayList<>(topics);
	}

	private String deriveSpeechAct(String normalized, String messageCategory)
	{
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ROOT))
		{
			case "threat" -> "threat";
			case "insult", "distrust", "resentment", "belief_conflict", "pvp_conflict" -> "challenge";
			case "abandonment" -> "break";
			case "apology", "repair" -> "repair";
			case "thanks" -> "thanks";
			case "affection", "respect", "praise", "reassurance" -> "affirm";
			case "social_invite", "party", "party_farm", "companionship" -> "invite";
			case "greeting" -> "greet";
			default -> looksLikeQuestion(normalized) ? "ask" : "chat";
		};
	}

	private String deriveKnowledgeNeed(String messageCategory, String knowledgeType)
	{
		if ((knowledgeType != null) && !"unknown".equalsIgnoreCase(knowledgeType))
		{
			return switch (knowledgeType.toLowerCase(Locale.ROOT))
			{
				case "travel_destination", "system_basics", "progression_route", "currency" -> "light";
				default -> "hard";
			};
		}
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ROOT))
		{
			case "farm", "party_farm", "lineage_lore", "progression_strength" -> "hard";
			case "direction", "help", "weather", "party" -> "light";
			default -> "none";
		};
	}

	private String deriveRouteLane(String messageCategory, String knowledgeNeed)
	{
		if ((messageCategory != null) && messageCategory.startsWith("entity_"))
		{
			return "selfhood";
		}
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ROOT))
		{
			case "threat", "insult", "distrust", "resentment", "abandonment", "belief_conflict", "pvp_conflict" -> "conflict";
			case "self_identity", "self_story", "self_belief", "self_preference", "self_state_reflection", "creator_opinion", "bond_opinion", "relationship_probe" -> "selfhood";
			case "help" -> "utility";
			default ->
			{
				if ("hard".equalsIgnoreCase(knowledgeNeed) || "light".equalsIgnoreCase(knowledgeNeed))
				{
					yield "knowledge";
				}
				yield "social";
			}
		};
	}

	private String deriveSocialStake(String messageCategory, String speechAct)
	{
		if ("invite".equalsIgnoreCase(speechAct))
		{
			return "medium";
		}
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ROOT))
		{
			case "threat", "insult", "distrust", "resentment", "abandonment", "belief_conflict", "repair", "affection", "respect", "praise", "reassurance", "bond_opinion", "relationship_probe" -> "high";
			case "companionship", "memory", "status", "party" -> "medium";
			default -> "low";
		};
	}

	private boolean isActionRequest(String normalized, String speechAct)
	{
		return "invite".equalsIgnoreCase(speechAct) || containsPhrase(normalized, "can you", "could you", "would you", "will you", "help me", "show me", "tell me", "join", "come with");
	}

	private double estimateRouteConfidence(String messageCategory, String knowledgeType, String primaryTopic)
	{
		if ((messageCategory == null) || messageCategory.isBlank() || "unknown".equalsIgnoreCase(messageCategory))
		{
			return 0.0;
		}
		if ("social_invite".equalsIgnoreCase(messageCategory))
		{
			return "none".equalsIgnoreCase(primaryTopic) ? 0.78 : 0.9;
		}
		if ((knowledgeType != null) && !"unknown".equalsIgnoreCase(knowledgeType))
		{
			return 0.84;
		}
		if ("smalltalk".equalsIgnoreCase(messageCategory))
		{
			return 0.35;
		}
		if ("party".equalsIgnoreCase(messageCategory) || "companionship".equalsIgnoreCase(messageCategory))
		{
			return 0.7;
		}
		return 0.82;
	}

	private boolean looksLikeQuestion(String normalized)
	{
		return containsPhrase(normalized, "what", "where", "when", "why", "how", "who", "which", "do you", "are you", "can you", "could you", "would you", "will you", "should i", "wanna", "want to");
	}

	public boolean isCreatorOpinionQuestion(String incomingText)
	{
		return isCreatorOpinionPrompt(extractCurrentUtterance(normalize(incomingText)));
	}

	public boolean isBondOpinionQuestion(String incomingText)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		return !classifyBondProbeType(currentUtterance).isBlank() || isBondOpinionPrompt(currentUtterance);
	}

	public String classifyBondProbeType(String incomingText)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank())
		{
			return "";
		}
		final String routedBondProbe = routingRuleOverride("bond_probe", currentUtterance, currentUtterance);
		if (!routedBondProbe.isBlank())
		{
			return routedBondProbe;
		}
		if (isBondHurtProbe(currentUtterance))
		{
			return "bond_hurt";
		}
		if (isBondTrustProbe(currentUtterance))
		{
			return "bond_trust";
		}
		if (isBondNeedProbe(currentUtterance))
		{
			return "bond_need";
		}
		if (isBondLoyaltyProbe(currentUtterance))
		{
			return "bond_loyalty";
		}
		if (isBondValueProbe(currentUtterance))
		{
			return "bond_value";
		}
		return "";
	}

	public boolean isSelfhoodCategory(String messageCategory)
	{
		if ((messageCategory == null) || messageCategory.isBlank())
		{
			return false;
		}
		return switch (messageCategory.toLowerCase(Locale.ROOT))
		{
			case "self_identity", "self_story", "self_belief", "self_preference", "self_state_reflection", "creator_opinion", "bond_opinion" -> true;
			default -> false;
		};
	}

	public String extractFocusEntity(String incomingText, Iterable<String> candidateNames, String speakerName, String incomingPlayerName)
	{
		final String normalized = normalize(incomingText);
		if (normalized.isBlank())
		{
			return "";
		}
		final String currentUtterance = extractCurrentUtterance(normalized);

		final String normalizedSpeaker = normalize(speakerName);
		final String normalizedIncomingPlayer = normalize(incomingPlayerName);
		final List<String> candidates = new ArrayList<>();
		if ((candidateNames != null))
		{
			for (String candidate : candidateNames)
			{
				final String normalizedCandidate = normalize(candidate);
				if (normalizedCandidate.isBlank() || normalizedCandidate.equals(normalizedSpeaker))
				{
					continue;
				}
				candidates.add(candidate.trim());
			}
		}
		if (!normalizedIncomingPlayer.isBlank() && !normalizedIncomingPlayer.equals(normalizedSpeaker))
		{
			candidates.add(incomingPlayerName.trim());
		}

		candidates.sort(Comparator.comparingInt(String::length).reversed());
		for (String candidate : candidates)
		{
			if (containsPhrase(currentUtterance, candidate))
			{
				return candidate;
			}
		}
		for (String candidate : candidates)
		{
			if (containsPhrase(normalized, candidate))
			{
				return candidate;
			}
		}

		final String previousFocus = extractPreviousFocusEntity(normalized);
		if (!previousFocus.isBlank())
		{
			return previousFocus.equals(normalizedSpeaker) ? "" : previousFocus;
		}
		if (!normalizedIncomingPlayer.isBlank() && isPlayerBondSubtypePrompt(currentUtterance))
		{
			return incomingPlayerName.trim();
		}

		if (containsPhrase(currentUtterance, "players", "player", "adventurers", "adventurer", "strangers", "people") || containsPhrase(normalized, "players", "player", "adventurers", "adventurer", "strangers", "people"))
		{
			return "adventurers";
		}
		return "";
	}

	public String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		final String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']+", " ").trim().replaceAll("\\s+", " ");
		if (normalized.isBlank())
		{
			return "";
		}
		final String[] rawTokens = normalized.split(" ");
		final StringBuilder builder = new StringBuilder(normalized.length());
		for (String rawToken : rawTokens)
		{
			if ((rawToken == null) || rawToken.isBlank())
			{
				continue;
			}
			if (builder.length() > 0)
			{
				builder.append(' ');
			}
			builder.append(correctTypoSensitiveToken(rawToken));
		}
		return builder.toString();
	}

	public String recoverTypoSensitiveText(String value)
	{
		if (value == null)
		{
			return "";
		}

		final java.util.regex.Matcher matcher = Pattern.compile("[A-Za-z0-9']+").matcher(value);
		final StringBuilder builder = new StringBuilder(value.length());
		int cursor = 0;
		while (matcher.find())
		{
			builder.append(value, cursor, matcher.start());
			final String rawToken = matcher.group();
			final String normalizedToken = rawToken.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']+", "").trim();
			final String corrected = normalizedToken.isBlank() ? rawToken : applyOriginalTokenStyle(rawToken, correctTypoSensitiveToken(normalizedToken));
			builder.append(corrected);
			cursor = matcher.end();
		}
		builder.append(value.substring(cursor));
		return builder.toString().trim().replaceAll("\\s+", " ");
	}

	private String applyOriginalTokenStyle(String rawToken, String correctedToken)
	{
		if ((rawToken == null) || rawToken.isBlank() || (correctedToken == null) || correctedToken.isBlank())
		{
			return (rawToken == null) ? "" : rawToken;
		}
		if (rawToken.equals(rawToken.toUpperCase(Locale.ROOT)))
		{
			return correctedToken.toUpperCase(Locale.ROOT);
		}
		if (Character.isUpperCase(rawToken.charAt(0)))
		{
			return Character.toUpperCase(correctedToken.charAt(0)) + correctedToken.substring(1);
		}
		return correctedToken;
	}

	private String correctTypoSensitiveToken(String token)
	{
		if ((token == null) || token.isBlank() || TYPO_RECOVERY_TOKENS.contains(token))
		{
			return (token == null) ? "" : token;
		}

		String bestCandidate = "";
		boolean ambiguous = false;
		for (String candidate : TYPO_RECOVERY_TOKENS)
		{
			if (!looksLikeRecoverableTypo(token, candidate))
			{
				continue;
			}
			if (bestCandidate.isBlank())
			{
				bestCandidate = candidate;
				ambiguous = false;
			}
			else if (!bestCandidate.equals(candidate))
			{
				ambiguous = true;
			}
		}
		return (!bestCandidate.isBlank() && !ambiguous) ? bestCandidate : token;
	}

	private boolean looksLikeRecoverableTypo(String token, String candidate)
	{
		if ((token == null) || token.isBlank() || (candidate == null) || candidate.isBlank() || token.equals(candidate))
		{
			return false;
		}
		if (Math.abs(token.length() - candidate.length()) > 1)
		{
			return false;
		}
		if ((candidate.length() < 4) && !TYPO_RECOVERY_SHORT_TOKENS.contains(candidate))
		{
			return false;
		}
		if ((token.length() < 4) && !TYPO_RECOVERY_SHORT_TOKENS.contains(candidate))
		{
			return false;
		}
		if (token.charAt(0) != candidate.charAt(0))
		{
			return false;
		}
		if ((token.length() == candidate.length()) && (token.charAt(token.length() - 1) != candidate.charAt(candidate.length() - 1)))
		{
			return false;
		}
		return isSingleTypoOrTransposition(token, candidate);
	}

	private boolean isSingleTypoOrTransposition(String actual, String expected)
	{
		if ((actual == null) || (expected == null) || actual.equals(expected))
		{
			return false;
		}
		return isSingleEditAway(actual, expected) || isSingleAdjacentTransposition(actual, expected);
	}

	private boolean isSingleEditAway(String first, String second)
	{
		if ((first == null) || (second == null))
		{
			return false;
		}
		if (Math.abs(first.length() - second.length()) > 1)
		{
			return false;
		}

		final String shorter = (first.length() <= second.length()) ? first : second;
		final String longer = (first.length() <= second.length()) ? second : first;
		int shortIndex = 0;
		int longIndex = 0;
		boolean foundDifference = false;
		while ((shortIndex < shorter.length()) && (longIndex < longer.length()))
		{
			if (shorter.charAt(shortIndex) == longer.charAt(longIndex))
			{
				shortIndex++;
				longIndex++;
				continue;
			}
			if (foundDifference)
			{
				return false;
			}
			foundDifference = true;
			if (shorter.length() == longer.length())
			{
				shortIndex++;
			}
			longIndex++;
		}
		return foundDifference || (shortIndex < shorter.length()) || (longIndex < longer.length());
	}

	private boolean isSingleAdjacentTransposition(String first, String second)
	{
		if ((first == null) || (second == null) || (first.length() != second.length()) || (first.length() < 2))
		{
			return false;
		}
		int diffIndex = -1;
		for (int index = 0; index < first.length(); index++)
		{
			if (first.charAt(index) != second.charAt(index))
			{
				diffIndex = index;
				break;
			}
		}
		if ((diffIndex < 0) || (diffIndex >= (first.length() - 1)))
		{
			return false;
		}
		if ((first.charAt(diffIndex) != second.charAt(diffIndex + 1)) || (first.charAt(diffIndex + 1) != second.charAt(diffIndex)))
		{
			return false;
		}
		for (int index = diffIndex + 2; index < first.length(); index++)
		{
			if (first.charAt(index) != second.charAt(index))
			{
				return false;
			}
		}
		return true;
	}

	public boolean containsPhrase(String normalizedText, String... phrases)
	{
		if ((normalizedText == null) || normalizedText.isBlank())
		{
			return false;
		}

		for (String phrase : phrases)
		{
			final String target = normalize(phrase);
			if (!target.isBlank() && Pattern.compile("\\b" + Pattern.quote(target) + "\\b").matcher(normalizedText).find())
			{
				return true;
			}
		}
		return false;
	}

	public boolean isDirectFlame(String incomingText, String speakerName)
	{
		final List<String> tokens = tokenizeFlameText(incomingText);
		if (tokens.isEmpty())
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (isPlayerBondSubtypePrompt(currentUtterance) || isPlayerBondOpinionPrompt(currentUtterance))
		{
			return false;
		}
		if (containsConfiguredPhrase(currentUtterance, bank.getDirectFlamePhrases()) || containsDirectFlamePhrase(tokens, bank))
		{
			return true;
		}

		final Set<String> tokenSet = Set.copyOf(tokens);
		final boolean mentionsSpeaker = tokenizeFlameText(speakerName).stream().anyMatch(tokenSet::contains);
		final boolean secondPersonAttack = tokenSet.stream().anyMatch(bank.getSecondPersonTokens()::contains);
		if (!mentionsSpeaker && !secondPersonAttack)
		{
			return false;
		}

		final boolean distrustOnly = isDistrustSignal(currentUtterance, speakerName)
			&& (countTokenMatches(tokenSet, bank.getInsultTokens()) == 0)
			&& (countTokenMatches(tokenSet, bank.getNegativeTraits()) == 0)
			&& !containsConfiguredPhrase(currentUtterance, bank.getNegativePhrases())
			&& !containsConfiguredPhrase(currentUtterance, bank.getResentmentPhrases())
			&& (countTokenMatches(tokenSet, bank.getHarmWishTerms()) == 0);
		if (distrustOnly)
		{
			return false;
		}

		int score = 0;
		if (mentionsSpeaker || secondPersonAttack)
		{
			score++;
		}
		if ((countTokenMatches(tokenSet, bank.getInsultTokens()) > 0) || (countTokenMatches(tokenSet, bank.getNegativeTraits()) > 0))
		{
			score++;
		}
		if (containsConfiguredPhrase(currentUtterance, bank.getNegativePhrases()))
		{
			score++;
		}
		if (containsConfiguredPhrase(currentUtterance, bank.getNegativeVerbs()))
		{
			score++;
		}
		if (countTokenMatches(tokenSet, bank.getHarmWishTerms()) > 0)
		{
			score++;
		}
		return score >= 2;
	}

	public boolean isApologySignal(String incomingText)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		return !currentUtterance.isBlank() && containsConfiguredPhrase(currentUtterance, socialSignalBank().getApologyPhrases());
	}

	public boolean isGratitudeSignal(String incomingText)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		return !currentUtterance.isBlank() && containsConfiguredPhrase(currentUtterance, socialSignalBank().getGratitudePhrases());
	}

	public boolean isRepairSignal(String incomingText)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		return !currentUtterance.isBlank() && containsConfiguredPhrase(currentUtterance, socialSignalBank().getRepairPhrases());
	}

	public boolean isAffectionSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getAffectionPhrases()))
		{
			return true;
		}
		return containsConfiguredPhrase(currentUtterance, List.of("i love you", "i like you", "i miss you", "care about you"));
	}

	public boolean isRespectSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getRespectPhrases()))
		{
			return true;
		}
		return containsConfiguredPhrase(currentUtterance, List.of("i trust you", "i respect you", "i admire you"));
	}

	public boolean isPraiseSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getPraisePhrases()))
		{
			return true;
		}
		return matchesSubjectTraitClause(currentUtterance, secondPersonPattern(), bank.getPositiveTraits());
	}

	public boolean isReassuranceSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		return containsConfiguredPhrase(currentUtterance, socialSignalBank().getReassurancePhrases());
	}

	public boolean isDistrustSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getDistrustPhrases()))
		{
			return true;
		}
		final String speakerPattern = normalize(speakerName).isBlank() ? secondPersonPattern() : "(?:" + secondPersonPattern() + "|" + Pattern.quote(normalize(speakerName)) + ")";
		return Pattern.compile("\\b" + speakerPattern + "\\s+(?:hide|hides|lying|lies|lie|pretend|pretending|deceive|deceiving)\\b").matcher(currentUtterance).find();
	}

	public boolean isResentmentSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank())
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		final boolean directed = isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName);
		if (!directed)
		{
			return false;
		}
		int score = 1;
		if (containsConfiguredPhrase(currentUtterance, bank.getResentmentPhrases()))
		{
			score += 2;
		}
		if (containsConfiguredPhrase(currentUtterance, bank.getNegativeVerbs()))
		{
			score += 1;
		}
		if (hasConfiguredTokenMatch(currentUtterance, bank.getNegativeTraits()) || hasConfiguredTokenMatch(currentUtterance, bank.getInsultTokens()))
		{
			score += 1;
		}
		return score >= 2;
	}

	public boolean isThreatSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getThreatPhrases()))
		{
			return true;
		}
		final String speakerPattern = normalize(speakerName).isBlank() ? secondPersonPattern() : "(?:" + secondPersonPattern() + "|" + Pattern.quote(normalize(speakerName)) + ")";
		return Pattern.compile("\\b(?:i\\s+will|i'll|ill|i\\s+am\\s+going\\s+to|i'm\\s+going\\s+to|im\\s+going\\s+to)\\s+(?:hurt|kill|ruin|destroy|hunt|break|burn|bleed)\\s+" + speakerPattern + "\\b").matcher(currentUtterance).find()
			|| Pattern.compile("\\b" + speakerPattern + "\\s+should\\s+(?:die|suffer|burn|rot|bleed)\\b").matcher(currentUtterance).find();
	}

	public boolean isAbandonmentSignal(String incomingText, String speakerName)
	{
		final String currentUtterance = extractCurrentUtterance(normalize(incomingText));
		if (currentUtterance.isBlank() || !isDirectedAtSpeakerOrSecondPerson(currentUtterance, speakerName))
		{
			return false;
		}
		final FpcSocialSignalBank bank = socialSignalBank();
		if (containsConfiguredPhrase(currentUtterance, bank.getAbandonmentPhrases()))
		{
			return true;
		}
		final String speakerPattern = normalize(speakerName).isBlank() ? secondPersonPattern() : "(?:" + secondPersonPattern() + "|" + Pattern.quote(normalize(speakerName)) + ")";
		return Pattern.compile("\\b(?:i\\s+am|i'm|im)\\s+(?:done|leaving)\\s+with\\s+" + speakerPattern + "\\b").matcher(currentUtterance).find()
			|| Pattern.compile("\\b" + speakerPattern + "\\s+is\\s+on\\s+your\\s+own\\b").matcher(currentUtterance).find();
	}

	public boolean isNegativeSubjectStatement(String incomingText, String subjectName)
	{
		final String normalizedSubject = normalize(subjectName);
		if ((incomingText == null) || incomingText.isBlank() || normalizedSubject.isBlank())
		{
			return false;
		}
		final String normalized = normalize(incomingText);
		final String currentUtterance = extractCurrentUtterance(normalized);
		if (currentUtterance.isBlank())
		{
			return false;
		}
		final boolean namedSubject = containsPhrase(currentUtterance, normalizedSubject) || containsPhrase(normalized, normalizedSubject);
		final boolean carriedSubject = isCarriedSubjectPronounReference(normalized, currentUtterance, normalizedSubject);
		if (!namedSubject && !carriedSubject)
		{
			return false;
		}

		int score = 0;
		if (namedSubject)
		{
			score = Math.max(score, scoreNegativeSubjectForPattern(currentUtterance, Pattern.quote(normalizedSubject)));
			score = Math.max(score, scoreLooseNegativeSubjectWindow(currentUtterance, normalizedSubject, false));
		}
		if (carriedSubject)
		{
			score = Math.max(score, scoreNegativeSubjectForPattern(currentUtterance, carryPronounPattern()));
			score = Math.max(score, scoreLooseNegativeSubjectWindow(currentUtterance, normalizedSubject, true));
		}
		return score >= 2;
	}

	public boolean isPositiveSubjectStatement(String incomingText, String subjectName)
	{
		final String normalizedSubject = normalize(subjectName);
		if ((incomingText == null) || incomingText.isBlank() || normalizedSubject.isBlank())
		{
			return false;
		}
		final String normalized = normalize(incomingText);
		final String currentUtterance = extractCurrentUtterance(normalized);
		if (currentUtterance.isBlank())
		{
			return false;
		}
		final boolean namedSubject = containsPhrase(currentUtterance, normalizedSubject) || containsPhrase(normalized, normalizedSubject);
		final boolean carriedSubject = isCarriedSubjectPronounReference(normalized, currentUtterance, normalizedSubject);
		if (!namedSubject && !carriedSubject)
		{
			return false;
		}

		int score = 0;
		if (namedSubject)
		{
			score = Math.max(score, scorePositiveSubjectForPattern(currentUtterance, Pattern.quote(normalizedSubject)));
			score = Math.max(score, scoreLoosePositiveSubjectWindow(currentUtterance, normalizedSubject, false));
		}
		if (carriedSubject)
		{
			score = Math.max(score, scorePositiveSubjectForPattern(currentUtterance, carryPronounPattern()));
			score = Math.max(score, scoreLoosePositiveSubjectWindow(currentUtterance, normalizedSubject, true));
		}
		return score >= 2;
	}

	private int scoreNegativeSubjectForPattern(String normalized, String subjectPattern)
	{
		final FpcSocialSignalBank bank = socialSignalBank();
		int score = 0;
		if (matchesSubjectVerbClause(normalized, subjectPattern, bank.getNegativeVerbs()))
		{
			score += 2;
		}
		if (matchesSubjectTraitClause(normalized, subjectPattern, bank.getNegativeTraits()))
		{
			score += 2;
		}
		if (matchesSubjectSuffixClause(normalized, subjectPattern, "sucks"))
		{
			score += 2;
		}
		if (matchesSubjectPhraseClause(normalized, subjectPattern, bank.getNegativePhrases()))
		{
			score += 2;
		}
		if (matchesSubjectWorthDenialClause(normalized, subjectPattern, bank))
		{
			score += 2;
		}
		if (matchesSubjectHarmWishClause(normalized, subjectPattern, bank))
		{
			score += 2;
		}
		return score;
	}

	private int scorePositiveSubjectForPattern(String normalized, String subjectPattern)
	{
		final FpcSocialSignalBank bank = socialSignalBank();
		int score = 0;
		if (matchesSubjectVerbClause(normalized, subjectPattern, bank.getPositiveVerbs()))
		{
			score += 2;
		}
		if (matchesSubjectTraitClause(normalized, subjectPattern, bank.getPositiveTraits()))
		{
			score += 2;
		}
		if (matchesSubjectPhraseClause(normalized, subjectPattern, bank.getPositivePhrases()))
		{
			score += 2;
		}
		return score;
	}

	private int scoreLooseNegativeSubjectWindow(String normalized, String normalizedSubject, boolean carryPronounMode)
	{
		final FpcSocialSignalBank bank = socialSignalBank();
		final List<String> tokens = tokenizeSignalText(normalized);
		if (tokens.isEmpty())
		{
			return 0;
		}

		for (int index = 0; index < tokens.size(); index++)
		{
			if (!matchesSubjectAnchor(tokens.get(index), normalizedSubject, carryPronounMode))
			{
				continue;
			}

			boolean negated = false;
			for (int next = index + 1; (next < tokens.size()) && (next <= (index + 6)); next++)
			{
				final String token = tokens.get(next);
				if (token.isBlank() || isWindowConnectorToken(token))
				{
					continue;
				}
				if (isNegationToken(token))
				{
					negated = true;
					continue;
				}
				if (bank.getNegativeTraits().contains(token) || bank.getInsultTokens().contains(token) || bank.getHarmWishTerms().contains(token))
				{
					return negated ? 0 : 2;
				}
			}
		}
		return 0;
	}

	private int scoreLoosePositiveSubjectWindow(String normalized, String normalizedSubject, boolean carryPronounMode)
	{
		final FpcSocialSignalBank bank = socialSignalBank();
		final List<String> tokens = tokenizeSignalText(normalized);
		if (tokens.isEmpty())
		{
			return 0;
		}

		for (int index = 0; index < tokens.size(); index++)
		{
			if (!matchesSubjectAnchor(tokens.get(index), normalizedSubject, carryPronounMode))
			{
				continue;
			}

			boolean negated = false;
			for (int next = index + 1; (next < tokens.size()) && (next <= (index + 6)); next++)
			{
				final String token = tokens.get(next);
				if (token.isBlank() || isWindowConnectorToken(token))
				{
					continue;
				}
				if (isNegationToken(token))
				{
					negated = true;
					continue;
				}
				if (bank.getPositiveTraits().contains(token))
				{
					return negated ? 0 : 2;
				}
			}
		}
		return 0;
	}

	private boolean isRelationshipProbe(String normalized)
	{
		return containsPhrase(normalized, "elyra", "marc") && containsPhrase(normalized, "wants from you", "want from you", "believes", "thinks", "think of", "feels about", "shouldnt exist", "shouldn't exist", "hate", "hates");
	}

	private boolean isLiteralNameQuestion(String normalized)
	{
		return containsPhrase(normalized, "what is your name", "what's your name", "whats your name", "what should i call you", "how should i call you", "what do people call you", "what are you called", "your name");
	}

	private boolean isSelfIdentityQuestion(String normalized)
	{
		return !isLiteralNameQuestion(normalized) && (containsPhrase(normalized, "who are you", "who re you", "whore you", "who r u", "what kind of person are you", "what kind of man are you", "what kind of woman are you", "what do you call yourself", "how would you describe yourself", "tell me who you are") || Pattern.compile("^what are you(?: really| exactly| supposed to be)?$").matcher(normalized).matches());
	}

	private boolean isSelfStoryQuestion(String normalized)
	{
		return containsPhrase(normalized, "tell me your story", "what is your story", "what's your story", "whats your story", "tell me about yourself", "why were you made", "why were you created", "how were you made", "how were you created", "how did you come to be", "who made you", "who created you", "why do you exist", "why are you here", "what brought you here", "your origin");
	}

	private boolean isCreatorOpinionPrompt(String normalized)
	{
		return containsPhrase(normalized, "what do you think of your creator", "how do you feel about your creator", "what do you feel about your creator", "do you trust your creator", "do you hate your creator", "do you resent your creator", "what do you think of the one who made you", "how do you feel about the one who made you", "do you resent the one who made you", "what do you think of your maker", "how do you feel about your maker");
	}

	private boolean isSelfBeliefQuestion(String normalized)
	{
		return containsPhrase(normalized, "what do you believe", "what matters to you", "what do you value", "what do you stand for", "what do you believe about", "what do you think people are like", "what do you think of players", "what do you think of adventurers", "do you trust players", "do you trust adventurers", "do you hate players", "do you hate adventurers", "what do you look for before you trust someone", "what do you look for before you trust people", "what makes you trust someone", "what makes you trust people", "what earns your trust", "how do you decide to trust someone", "how do you decide to trust people", "how do you decide to forgive someone", "how do you decide to forgive people", "what makes you forgive someone", "what makes you forgive people", "when do you forgive someone", "when do you forgive people");
	}

	private boolean isSelfPreferenceQuestion(String normalized)
	{
		return containsPhrase(normalized, "what do you like", "what do you dislike", "what do you hate", "what do you prefer", "what do you enjoy", "what kind of work do you enjoy", "do you prefer silence", "what do you want most", "what do you want for yourself", "what do you really want", "what do you honestly want", "what annoys you", "what gets under your skin", "what gets on your nerves", "what bothers you", "what frustrates you", "what are you trying to protect now", "what are you protecting now", "what are you protecting right now", "what are you trying to hold onto", "what are you trying to keep safe", "what are you trying to preserve");
	}

	private boolean isSelfStateReflectionQuestion(String normalized)
	{
		return containsPhrase(normalized, "are you lonely", "are you happy", "are you sad", "are you afraid", "are you tired", "are you exhausted", "are you empty", "do you feel lonely", "do you feel abandoned", "do you feel safe", "are you happy here", "are you lonely here", "does this place tire you", "what do you fear", "what scares you", "what are you afraid of", "what hurts you", "what still hurts", "what are you hiding", "what do you keep hidden", "what are you keeping hidden", "what wont you admit", "what won't you admit", "why do you keep people at a distance", "why do you keep others at a distance", "why do you keep everyone at a distance", "why do you keep me at a distance", "why are you so guarded", "why are you so distant", "why do you hold people at arms length", "why do you hold people at arm's length");
	}

	private boolean isBondOpinionPrompt(String normalized)
	{
		return containsPhrase(normalized, "what do you think of", "what do you think about", "how do you feel about", "what do you feel about", "what does he mean to you", "what does she mean to you", "what do they mean to you", "why does he matter to you", "why does she matter to you", "why do they matter to you", "why do you care about", "what does that person mean to you", "why does that person matter to you");
	}

	private boolean isPlayerBondOpinionPrompt(String normalized)
	{
		return containsPhrase(normalized, "what do you think of me", "what do you think about me", "how do you feel about me", "what do you feel about me", "what am i to you", "what do i mean to you", "do i matter to you", "am i important to you", "why do i matter to you", "do you care about me", "why do you care about me", "do you resent me", "why do you resent me", "do you hate me", "why do you hate me", "do you trust me", "do you like me", "what am i to you really");
	}

	private boolean isPlayerBondSubtypePrompt(String normalized)
	{
		return !classifyBondProbeType(normalized).isBlank() && Pattern.compile("\\b(me|my|myself|i)\\b").matcher(normalized).find();
	}

	private boolean isDirectFlame(String normalized)
	{
		return isDirectFlame(normalized, "");
	}

	private List<String> tokenizeFlameText(String value)
	{
		final String lowered = (value == null) ? "" : value.toLowerCase(Locale.ROOT);
		if (lowered.isBlank())
		{
			return List.of();
		}

		final String normalized = lowered.replace('@', 'a').replace('$', 's').replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't');
		final String[] rawParts = normalized.split("[^a-z0-9']+");
		final List<String> tokens = new ArrayList<>(rawParts.length);
		for (String rawPart : rawParts)
		{
			final String token = canonicalizeFlameToken(rawPart);
			if (!token.isBlank())
			{
				tokens.add(token);
			}
		}
		return tokens;
	}

	private String canonicalizeFlameToken(String rawToken)
	{
		if ((rawToken == null) || rawToken.isBlank())
		{
			return "";
		}

		String token = rawToken.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]", "");
		if (token.isBlank())
		{
			return "";
		}

		token = token.replaceAll("(.)\\1{2,}", "$1");
		return switch (token)
		{
			case "fuk", "fuq", "fck", "fack", "fuc", "phuck", "fcuk", "fawk", "fuck" -> "fuck";
			case "sht", "shit" -> "shit";
			case "btch", "bitch" -> "bitch";
			case "ahole", "ashole", "asshole", "arsehole" -> "asshole";
			case "dumas", "dumbas", "dumbass" -> "dumbass";
			case "dipshit" -> "dipshit";
			case "stfu", "gtfo", "kys" -> token;
			case "ur" -> "your";
			default -> token;
		};
	}

	private boolean containsDirectFlamePhrase(List<String> tokens, FpcSocialSignalBank bank)
	{
		for (String phrase : bank.getDirectFlamePhrases())
		{
			if (matchesTokenSequence(tokens, tokenizeFlameText(phrase)))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isEntityStatusQuestion(String normalized)
	{
		return containsPhrase(normalized, "how is", "how s", "is okay", "is ok", "is alright", "what about", "tell me about", "ask about", "asking about", "i am asking about", "im asking about", "checking on", "how has", "howve", "how have", "whats going on with", "what's going on with", "whats goin on with", "what's goin on with", "going on with")
			|| Pattern.compile("\\bis\\s+[a-z0-9']+\\s+here\\b").matcher(normalized).find()
			|| (containsPhrase(normalized, "how") && containsPhrase(normalized, "doing", "holding up", "keeping", "feeling"));
	}

	private boolean isEntityRelationshipQuestion(String normalized)
	{
		return containsPhrase(normalized, "what do you think of", "what do you think about", "how do you feel about", "what do you feel about", "do you hate", "do you like", "do you trust", "do you resent", "what do they mean to you", "what does he mean to you", "what does she mean to you", "why do you hate", "why do you resent", "what do they want", "what does he want", "what does she want", "think of", "feels about", "feel about", "believes about", "wants from");
	}

	private boolean isEntityRelationshipStatement(String normalized)
	{
		final String currentUtterance = extractCurrentUtterance(normalized);
		return containsPhrase(normalized, "i hate", "i love", "i care about", "i dont trust", "i don't trust", "i respect", "i admire", "means nothing", "means a lot", "matters", "is important", "deserves better", "does not matter", "doesnt matter", "doesn't matter", "does not deserve", "doesnt deserve", "doesn't deserve", "deserves nothing", "is weak", "is pathetic", "is worthless", "is useless", "is trash", "is garbage", "is a liar", "is stupid", "is dumb", "is an idiot", "is a moron", "is a clown", "is a joke", "sucks", "is worth protecting")
			|| containsGenericRelationshipCue(currentUtterance);
	}

	private boolean isEntityStoryQuestion(String normalized)
	{
		return containsPhrase(normalized, "who is", "who was", "what happened to", "why is", "why was", "who made", "who created", "tell me her story", "tell me his story", "tell me their story");
	}

	private boolean isEntityReasonQuestion(String normalized)
	{
		return containsPhrase(normalized, "why do you watch", "why are you watching", "why do you protect", "why are you with", "why does", "why do they matter", "why does he matter", "why does she matter", "why does that matter");
	}

	private boolean isEntityMemoryQuestion(String normalized)
	{
		return containsPhrase(normalized, "remember", "miss", "what do you remember about", "do you remember", "do you miss", "have you missed");
	}

	private boolean isTravelDestinationQuestion(String normalized)
	{
		return containsPhrase(normalized, "teleport", "gatekeeper", "how do i get", "how i get", "go to", "get to", "reach") && containsPhrase(normalized, "cruma", "cruma tower", "dion", "giran", "abandoned camp", "ruins of agony", "ruins of despair", "wasteland", "southern wasteland", "orc fortress", "iron heart", "ironheart");
	}

	private boolean isClassChoiceQuestion(String normalized)
	{
		return containsPhrase(normalized, "best class", "which class is best", "what class is best", "strongest class", "aoe farm", "aoe farming", "best farmer", "best for aoe", "best for farming", "what class should i pick", "what class should i choose", "which class should i pick", "which class should i choose", "what class do you recommend", "which class do you recommend", "what class fits me", "what class suits me", "what class would suit me", "what should i play");
	}

	private boolean isPvpConflictQuestion(String normalized)
	{
		return containsPhrase(normalized, "players that harass me", "player that harass me", "harass me", "harassing me", "kill players", "kill them", "pk them", "revenge", "grief", "griefing", "protect me from players", "kill you", "pk you", "i will kill you", "ill kill you", "i'll kill you", "i will pk you", "ill pk you", "i'll pk you", "attack you", "hunt you down", "talks big", "talk big", "hide behind zerg", "hides behind zerg", "crying in global", "losing its mind", "full drama", "full of drama", "flaming your family", "flame your family", "mouth off at family", "talk about your family")
			|| (containsPhrase(normalized, "player", "players") && containsPhrase(normalized, "harass", "harassing", "kill", "pk", "revenge", "protect"))
			|| (containsPhrase(normalized, "cp", "zerg", "clan", "global", "server chat") && containsPhrase(normalized, "dying", "hide behind", "talks big", "crying", "drama", "meltdown"));
	}

	private boolean isProgressionStrengthQuestion(String normalized)
	{
		return containsPhrase(normalized, "make me stronger", "make me strong", "what would make me stronger", "what would make me strong", "what makes me stronger", "what makes me strong", "stronger for now", "strong for now", "what should i improve first", "what should i upgrade first", "what should i focus on first", "how do i get stronger", "how i get stronger", "what would help me now", "what helps me now", "my level", "my levels", "i keep dying", "keep dying", "i keep getting killed", "keep getting killed", "die a lot", "dying a lot", "im undergeared", "i'm undergeared", "under geared", "undergeared", "too weak", "too squishy", "too fragile", "keep losing hp")
			|| looksLikeLevelGoalQuestion(normalized);
	}

	private boolean isPartyFarmQuestion(String normalized)
	{
		return containsPhrase(normalized, "help me farm", "would you help me farm", "can you help me farm", "come farm with me", "farm with me", "farm with you", "wanna farm with you", "want to farm with you", "come with you to farm", "go farm with you", "wanna go farm", "want to go farm", "go farm together", "farm together", "lets go farm", "let's go farm", "lets farm", "let's farm", "farm some exp", "lets kill some enemies", "let's kill some enemies", "kill some enemies", "kill mobs", "hunt mobs", "lets go hunt", "let's go hunt", "go hunt together", "clear some mobs", "grind together") || (containsPhrase(normalized, "help", "party", "group", "join me", "join you", "go together", "come with me", "come with you", "with me", "with you", "together", "lets", "let's") && containsPhrase(normalized, "farm", "grind", "hunt", "spot", "cruma 2", "cruma2", "magical tablet", "magical tablets", "mobs", "enemies", "kills", "exp", "xp"));
	}

	private boolean isCompanionshipQuestion(String normalized)
	{
		return containsPhrase(normalized, "friend", "friends", "be friends", "looking for friends", "wanna be friends", "want to be friends", "spend time with", "spend some time with", "spend time together", "spend some time together", "wanna spend some time together", "want to spend some time together", "hang out", "keep me company", "company", "lonely", "coffee together", "drink some coffee", "drink coffee", "stay with me for a while", "stay with you for a while");
	}

	private boolean isGenericSupportFarmQuestion(String normalized)
	{
		return isPartyFarmQuestion(normalized) && !containsPhrase(normalized, "99", "lvl 99", "level 99", "cruma", "cruma 2", "cruma2", "magical tablet", "magical tablets", "ruins of agony", "abandoned camp", "wasteland", "adena", "exp", "xp", "aoe");
	}

	private boolean isFarmingRouteQuestion(String normalized)
	{
		return (containsPhrase(normalized, "where should i farm", "where can i farm", "where do i farm", "best exp", "best xp", "farm adena", "farm exp", "farm xp", "where should he farm", "where can he farm", "where do you think i should farm", "looking to farm") || (containsPhrase(normalized, "where", "best", "spot") && containsPhrase(normalized, "farm", "grind", "hunt", "exp", "xp", "adena")));
	}

	private boolean containsNegativeSubjectJudgment(String normalized, String normalizedSubject)
	{
		return containsNegativeSubjectJudgmentForPattern(normalized, Pattern.quote(normalizedSubject));
	}

	private boolean containsNegativeSubjectJudgmentForPattern(String normalized, String subjectPattern)
	{
		return scoreNegativeSubjectForPattern(normalized, subjectPattern) >= 2;
	}

	private boolean containsPositiveSubjectJudgment(String normalized, String normalizedSubject)
	{
		return containsPositiveSubjectJudgmentForPattern(normalized, Pattern.quote(normalizedSubject));
	}

	private boolean containsPositiveSubjectJudgmentForPattern(String normalized, String subjectPattern)
	{
		return scorePositiveSubjectForPattern(normalized, subjectPattern) >= 2;
	}

	private boolean isCarriedSubjectPronounReference(String normalized, String currentUtterance, String normalizedSubject)
	{
		if (currentUtterance.isBlank())
		{
			return false;
		}
		final String previousFocus = normalize(extractPreviousFocusEntity(normalized));
		return !previousFocus.isBlank() && previousFocus.equals(normalizedSubject) && containsThirdPersonCarryPronoun(currentUtterance);
	}

	private boolean containsThirdPersonCarryPronoun(String normalized)
	{
		return containsConfiguredPhrase(normalized, socialSignalBank().getCarryPronouns());
	}

	private boolean isCarriedEntityRelationshipStatement(String currentUtterance)
	{
		if (currentUtterance.isBlank() || !containsThirdPersonCarryPronoun(currentUtterance))
		{
			return false;
		}
		return containsNegativeSubjectJudgmentForPattern(currentUtterance, carryPronounPattern())
			|| containsPositiveSubjectJudgmentForPattern(currentUtterance, carryPronounPattern());
	}

	private boolean isCarriedEntityReasonFollowUp(String currentUtterance)
	{
		if (currentUtterance.isBlank())
		{
			return false;
		}
		return Pattern.compile("^(why|how\\s+so|what\\s+do\\s+you\\s+mean|what\\s+about\\s+that|why\\s+that|why\\s+so)\\b").matcher(currentUtterance).find();
	}

	private List<String> tokenizeSignalText(String value)
	{
		final List<String> tokens = tokenizeFlameText(value);
		if (tokens.isEmpty())
		{
			return List.of();
		}
		final List<String> normalized = new ArrayList<>(tokens.size());
		for (String token : tokens)
		{
			final String next = canonicalizeSignalToken(token);
			if (!next.isBlank())
			{
				normalized.add(next);
			}
		}
		return normalized;
	}

	private String canonicalizeSignalToken(String rawToken)
	{
		String token = canonicalizeFlameToken(rawToken);
		if (token.isBlank())
		{
			return token;
		}

		token = switch (token)
		{
			case "douchebags", "douchebag", "dbag", "dbags" -> "douchebag";
			case "scumbags", "scumbag" -> "scumbag";
			case "jerks" -> "jerk";
			case "pricks" -> "prick";
			case "rats" -> "rat";
			case "snakes" -> "snake";
			case "pigs" -> "pig";
			case "parasites" -> "parasite";
			case "tools" -> "tool";
			case "trashy" -> "trash";
			case "filthy" -> "filth";
			case "scummy" -> "scum";
			case "cringey", "cringy" -> "cringe";
			case "douchey", "douchy" -> "douche";
			default -> token;
		};

		final String reduced = reduceSignalSuffix(token);
		return reduced.isBlank() ? token : reduced;
	}

	private String reduceSignalSuffix(String token)
	{
		if ((token == null) || token.isBlank())
		{
			return "";
		}

		final FpcSocialSignalBank bank = socialSignalBank();
		for (String suffix : List.of("iest", "ier", "ish", "ness", "less", "ful", "est", "er", "es", "s", "y"))
		{
			if (!token.endsWith(suffix) || (token.length() <= (suffix.length() + 2)))
			{
				continue;
			}
			final String base = token.substring(0, token.length() - suffix.length());
			if (matchesSignalBase(base, bank))
			{
				return base;
			}
			if ("y".equals(suffix) && matchesSignalBase(base + "e", bank))
			{
				return base + "e";
			}
		}
		return token;
	}

	private boolean matchesSignalBase(String token, FpcSocialSignalBank bank)
	{
		return bank.getNegativeTraits().contains(token)
			|| bank.getPositiveTraits().contains(token)
			|| bank.getInsultTokens().contains(token)
			|| bank.getAffectionPhrases().contains(token)
			|| bank.getRespectPhrases().contains(token)
			|| bank.getPraisePhrases().contains(token)
			|| bank.getReassurancePhrases().contains(token)
			|| bank.getDistrustPhrases().contains(token)
			|| bank.getThreatPhrases().contains(token)
			|| bank.getAbandonmentPhrases().contains(token)
			|| bank.getHarmWishTerms().contains(token);
	}

	private boolean isDirectedAtSpeakerOrSecondPerson(String normalized, String speakerName)
	{
		if ((normalized == null) || normalized.isBlank())
		{
			return false;
		}
		final String normalizedSpeaker = normalize(speakerName);
		if (!normalizedSpeaker.isBlank() && containsPhrase(normalized, normalizedSpeaker))
		{
			return true;
		}
		final List<String> tokens = tokenizeSignalText(normalized);
		return !tokens.isEmpty() && tokens.stream().anyMatch(socialSignalBank().getSecondPersonTokens()::contains);
	}

	private boolean hasConfiguredTokenMatch(String normalized, Collection<String> expected)
	{
		final List<String> tokens = tokenizeSignalText(normalized);
		return !tokens.isEmpty() && (countTokenMatches(Set.copyOf(tokens), expected) > 0);
	}

	private boolean matchesSubjectAnchor(String token, String normalizedSubject, boolean carryPronounMode)
	{
		if ((token == null) || token.isBlank())
		{
			return false;
		}
		return carryPronounMode ? socialSignalBank().getCarryPronouns().contains(token) : token.equals(normalizedSubject);
	}

	private boolean isWindowConnectorToken(String token)
	{
		return Set.of("is", "i", "a", "an", "the", "very", "really", "pretty", "quite", "too", "extra", "total", "totally", "complete", "completely", "absolute", "absolutely", "fucking", "damn", "so", "such", "massive", "utter", "utterly", "kind", "sort", "of", "like", "man", "person", "guy", "woman").contains(token);
	}

	private boolean isNegationToken(String token)
	{
		return Set.of("not", "no", "isnt", "isn't", "aint", "ain't", "never").contains(token);
	}

	private boolean containsGenericRelationshipCue(String normalized)
	{
		if ((normalized == null) || normalized.isBlank())
		{
			return false;
		}
		final String genericSubjectPattern = "(?:[a-z0-9']+|" + alternation(socialSignalBank().getCarryPronouns()) + ")";
		return (scoreNegativeSubjectForPattern(normalized, genericSubjectPattern) >= 2) || (scorePositiveSubjectForPattern(normalized, genericSubjectPattern) >= 2);
	}

	private boolean matchesTokenSequence(List<String> tokens, List<String> targetTokens)
	{
		if (tokens.isEmpty() || targetTokens.isEmpty() || (tokens.size() < targetTokens.size()))
		{
			return false;
		}
		for (int start = 0; start <= (tokens.size() - targetTokens.size()); start++)
		{
			boolean matches = true;
			for (int index = 0; index < targetTokens.size(); index++)
			{
				if (!tokens.get(start + index).equals(targetTokens.get(index)))
				{
					matches = false;
					break;
				}
			}
			if (matches)
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsConfiguredPhrase(String normalized, Collection<String> phrases)
	{
		if ((normalized == null) || normalized.isBlank() || (phrases == null) || phrases.isEmpty())
		{
			return false;
		}
		for (String phrase : phrases)
		{
			final String target = normalize(phrase);
			if (!target.isBlank() && Pattern.compile("\\b" + Pattern.quote(target) + "\\b").matcher(normalized).find())
			{
				return true;
			}
		}
		return containsConfiguredPhraseWithSingleTypo(normalized, phrases);
	}

	private boolean containsConfiguredPhraseWithSingleTypo(String normalized, Collection<String> phrases)
	{
		if ((normalized == null) || normalized.isBlank() || (phrases == null) || phrases.isEmpty())
		{
			return false;
		}

		final List<String> inputTokens = tokenizeNormalizedWords(normalized);
		if (inputTokens.isEmpty())
		{
			return false;
		}

		for (String phrase : phrases)
		{
			final List<String> targetTokens = tokenizeNormalizedWords(normalize(phrase));
			if (matchesNearTokenSequence(inputTokens, targetTokens))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> tokenizeNormalizedWords(String normalized)
	{
		if ((normalized == null) || normalized.isBlank())
		{
			return List.of();
		}
		final String[] rawTokens = normalized.trim().split("\\s+");
		final List<String> tokens = new ArrayList<>(rawTokens.length);
		for (String token : rawTokens)
		{
			if ((token != null) && !token.isBlank())
			{
				tokens.add(token);
			}
		}
		return tokens;
	}

	private boolean matchesNearTokenSequence(List<String> inputTokens, List<String> targetTokens)
	{
		if ((inputTokens == null) || inputTokens.isEmpty() || (targetTokens == null) || targetTokens.isEmpty() || (inputTokens.size() < targetTokens.size()))
		{
			return false;
		}

		for (int start = 0; start <= (inputTokens.size() - targetTokens.size()); start++)
		{
			int typoCount = 0;
			boolean matched = true;
			for (int index = 0; index < targetTokens.size(); index++)
			{
				final String actual = inputTokens.get(start + index);
				final String expected = targetTokens.get(index);
				if (actual.equals(expected))
				{
					continue;
				}
				if ((typoCount > 0) || !looksLikeConfiguredPhraseTypo(actual, expected))
				{
					matched = false;
					break;
				}
				typoCount++;
			}
			if (matched && (typoCount == 1))
			{
				return true;
			}
		}
		return false;
	}

	private boolean looksLikeConfiguredPhraseTypo(String actual, String expected)
	{
		if ((actual == null) || actual.isBlank() || (expected == null) || expected.isBlank() || actual.equals(expected))
		{
			return false;
		}
		if (Math.abs(actual.length() - expected.length()) > 1)
		{
			return false;
		}
		if ((actual.length() < 4) && (expected.length() < 4))
		{
			return false;
		}
		if (actual.charAt(0) != expected.charAt(0))
		{
			return false;
		}
		return isSingleTypoOrTransposition(actual, expected);
	}

	private int countTokenMatches(Collection<String> tokens, Collection<String> expected)
	{
		if ((tokens == null) || tokens.isEmpty() || (expected == null) || expected.isEmpty())
		{
			return 0;
		}
		int matches = 0;
		for (String token : tokens)
		{
			if (expected.contains(token))
			{
				matches++;
			}
		}
		return matches;
	}

	private boolean matchesSubjectVerbClause(String normalized, String subjectPattern, Collection<String> verbs)
	{
		final String verbAlternation = alternation(verbs);
		return !verbAlternation.isBlank() && Pattern.compile("\\b(?:i\\s+)?(?:" + verbAlternation + ")\\s+" + subjectPattern + "\\b").matcher(normalized).find();
	}

	private boolean matchesSubjectTraitClause(String normalized, String subjectPattern, Collection<String> traits)
	{
		final String traitAlternation = alternation(traits);
		if (traitAlternation.isBlank())
		{
			return false;
		}
		final String intensifierClause = optionalIntensifierClause();
		final String articleClause = "(?:a\\s+|an\\s+)?";
		final String qualifierClause = intensifierClause + articleClause + intensifierClause;
		return Pattern.compile("\\b(?:" + subjectPattern + "\\s+(?:is|looks|sounds|seems|feels)|is\\s+" + subjectPattern + ")\\s+" + qualifierClause + "(?:" + traitAlternation + ")\\b").matcher(normalized).find();
	}

	private boolean matchesSubjectPhraseClause(String normalized, String subjectPattern, Collection<String> phrases)
	{
		final String phraseAlternation = alternation(phrases);
		return !phraseAlternation.isBlank() && Pattern.compile("\\b" + subjectPattern + "\\s+" + optionalIntensifierClause() + "(?:" + phraseAlternation + ")\\b").matcher(normalized).find();
	}

	private boolean matchesSubjectWorthDenialClause(String normalized, String subjectPattern, FpcSocialSignalBank bank)
	{
		final String worthTerms = alternation(bank.getWorthDenialTerms());
		if (worthTerms.isBlank())
		{
			return false;
		}
		return Pattern.compile("\\b" + subjectPattern + "\\s+(?:does\\s+not|doesn'?t)\\s+deserve(?:\\s+(?:" + worthTerms + "))?\\b").matcher(normalized).find()
			|| Pattern.compile("\\b" + subjectPattern + "\\s+deserves\\s+(?:nothing|no\\s+(?:" + worthTerms + "))\\b").matcher(normalized).find();
	}

	private boolean matchesSubjectHarmWishClause(String normalized, String subjectPattern, FpcSocialSignalBank bank)
	{
		final String harmTerms = alternation(bank.getHarmWishTerms());
		if (harmTerms.isBlank())
		{
			return Pattern.compile("\\b" + subjectPattern + "\\s+shouldn'?t\\s+exist\\b").matcher(normalized).find();
		}
		return Pattern.compile("\\b" + subjectPattern + "\\s+deserves\\s+(?:pain|suffering|to\\s+(?:" + harmTerms + ")|worse)\\b").matcher(normalized).find()
			|| Pattern.compile("\\b" + subjectPattern + "\\s+should\\s+(?:be\\s+)?(?:" + harmTerms + ")\\b").matcher(normalized).find()
			|| Pattern.compile("\\b" + subjectPattern + "\\s+shouldn'?t\\s+exist\\b").matcher(normalized).find();
	}

	private boolean matchesSubjectSuffixClause(String normalized, String subjectPattern, String suffix)
	{
		return Pattern.compile("\\b" + subjectPattern + "\\s+" + Pattern.quote(suffix) + "\\b").matcher(normalized).find();
	}

	private String optionalIntensifierClause()
	{
		final String intensifiers = alternation(socialSignalBank().getIntensifiers());
		return intensifiers.isBlank() ? "" : "(?:(?:" + intensifiers + ")\\s+){0,2}";
	}

	private String carryPronounPattern()
	{
		return "(?:" + alternation(socialSignalBank().getCarryPronouns()) + ")";
	}

	private String secondPersonPattern()
	{
		return "(?:you|your|youre|you're|u)";
	}

	private String alternation(Collection<String> values)
	{
		if ((values == null) || values.isEmpty())
		{
			return "";
		}
		final StringBuilder builder = new StringBuilder();
		for (String value : values)
		{
			final String normalized = normalize(value);
			if (normalized.isBlank())
			{
				continue;
			}
			if (builder.length() > 0)
			{
				builder.append('|');
			}
			builder.append(Pattern.quote(normalized));
		}
		return builder.toString();
	}

	private FpcSocialSignalBank socialSignalBank()
	{
		return (_socialSignalBank != null) ? _socialSignalBank : FpcSocialSignalBank.defaults();
	}

	private boolean looksLikeLevelGoalQuestion(String normalized)
	{
		if (normalized.isBlank())
		{
			return false;
		}

		final boolean hasCurrentLevel = Pattern.compile("\\b(i am|im|i'm|lvl|level)\\s+\\d{1,3}\\b").matcher(normalized).find();
		final boolean hasGoalLevel = Pattern.compile("\\b(get to|reach|to)\\s+\\d{1,3}\\b").matcher(normalized).find();
		final boolean asksWhereNext = containsPhrase(normalized, "where should i go", "where do i go", "where can i go", "where next");
		return (hasCurrentLevel && hasGoalLevel) || (asksWhereNext && Pattern.compile("\\b\\d{1,3}\\b").matcher(normalized).find());
	}

	private String extractPreviousFocusEntity(String normalized)
	{
		final java.util.regex.Matcher matcher = Pattern.compile("\\bprevious focus\\s+(entity\\s+(?:relationship|status|story|reason|memory|context)\\s+[a-z0-9]+|[a-z0-9_]+)\\b").matcher(normalized);
		if (!matcher.find())
		{
			return "";
		}

		final String token = matcher.group(1).trim().replaceAll("\\s+", " ");
		if (token.startsWith("entity relationship "))
		{
			return token.substring("entity relationship ".length());
		}
		if (token.startsWith("entity status "))
		{
			return token.substring("entity status ".length());
		}
		if (token.startsWith("entity story "))
		{
			return token.substring("entity story ".length());
		}
		if (token.startsWith("entity reason "))
		{
			return token.substring("entity reason ".length());
		}
		if (token.startsWith("entity memory "))
		{
			return token.substring("entity memory ".length());
		}
		if (token.startsWith("entity context "))
		{
			return token.substring("entity context ".length());
		}
		return "";
	}

	private String extractPreviousFocusIntent(String normalized)
	{
		final java.util.regex.Matcher matcher = Pattern.compile("\\bprevious focus\\s+(entity\\s+(?:relationship|status|story|reason|memory|context)\\s+[a-z0-9]+|[a-z0-9_]+)\\b").matcher(normalized);
		if (!matcher.find())
		{
			return "";
		}

		final String token = matcher.group(1).trim().replaceAll("\\s+", " ");
		if (token.startsWith("entity relationship "))
		{
			return "entity_relationship";
		}
		if (token.startsWith("entity status "))
		{
			return "entity_status";
		}
		if (token.startsWith("entity story "))
		{
			return "entity_story";
		}
		if (token.startsWith("entity reason "))
		{
			return "entity_reason";
		}
		if (token.startsWith("entity memory "))
		{
			return "entity_memory";
		}
		return "";
	}

	private String extractCurrentUtterance(String normalized)
	{
		if ((normalized == null) || normalized.isBlank())
		{
			return "";
		}

		int cut = normalized.length();
		for (String marker : List.of(" previous category ", " previous knowledge topic ", " previous focus ", " previous player topic ", " previous reply ", " longer memory "))
		{
			final int markerIndex = normalized.indexOf(marker);
			if ((markerIndex >= 0) && (markerIndex < cut))
			{
				cut = markerIndex;
			}
		}
		return normalized.substring(0, cut).trim();
	}

	private String routingRuleOverride(String targetType, String normalizedText, String currentUtterance)
	{
		if ((targetType == null) || targetType.isBlank())
		{
			return "";
		}

		for (FpcRoutingRule rule : _routingRules)
		{
			if ((rule == null) || !rule.isEnabled())
			{
				continue;
			}
			if (!targetType.equalsIgnoreCase(rule.getTargetType()))
			{
				continue;
			}

			final String scope = (rule.getScope() == null) ? "current_utterance" : rule.getScope().trim().toLowerCase(Locale.ROOT);
			final String targetText = "full_text".equals(scope) ? normalizedText : ((currentUtterance == null) ? "" : currentUtterance);
			if (routingRuleMatches(rule, targetText))
			{
				return (rule.getResult() == null) ? "" : rule.getResult().trim().toLowerCase(Locale.ROOT);
			}
		}
		return "";
	}

	private boolean routingRuleMatches(FpcRoutingRule rule, String normalizedText)
	{
		if ((normalizedText == null) || normalizedText.isBlank())
		{
			return false;
		}
		if (rule == null)
		{
			return false;
		}

		final boolean hasAny = !rule.getAnyPhrases().isEmpty();
		final boolean hasAll = !rule.getAllPhrases().isEmpty();
		if (!hasAny && !hasAll)
		{
			return false;
		}
		for (String excluded : rule.getExcludePhrases())
		{
			if (containsPhrase(normalizedText, excluded))
			{
				return false;
			}
		}
		if (hasAll)
		{
			for (String required : rule.getAllPhrases())
			{
				if (!containsPhrase(normalizedText, required))
				{
					return false;
				}
			}
		}
		if (hasAny)
		{
			for (String candidate : rule.getAnyPhrases())
			{
				if (containsPhrase(normalizedText, candidate))
				{
					return true;
				}
			}
			return false;
		}
		return true;
	}

	private boolean shouldPromoteFocusIntent(String messageCategory, String focusIntent, String focusEntity)
	{
		if ((focusIntent == null) || focusIntent.isBlank() || (focusEntity == null) || focusEntity.isBlank())
		{
			return false;
		}
		return switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ROOT))
		{
			case "", "unknown", "greeting", "status", "smalltalk", "memory", "help", "quest_story", "relationship_probe" -> true;
			default -> false;
		};
	}

	private boolean isBondValueProbe(String normalized)
	{
		return containsPhrase(normalized, "what do you think of", "what do you think about", "how do you feel about", "what do you feel about", "how do you see", "what do you make of", "what am i to you", "what do i mean to you", "what does he mean to you", "what does she mean to you", "what do they mean to you")
			|| Pattern.compile("\\bwhat do you (?:truly |really |honestly |actually )?think of\\b").matcher(normalized).find()
			|| Pattern.compile("\\bwhat do you (?:truly |really |honestly |actually )?think about\\b").matcher(normalized).find()
			|| Pattern.compile("\\bhow do you (?:truly |really |honestly |actually )?feel about\\b").matcher(normalized).find();
	}

	private boolean isBondHurtProbe(String normalized)
	{
		return containsPhrase(normalized, "do you resent", "why do you resent", "are you angry at", "are you mad at", "are you upset with", "do you blame", "do you still blame", "have i hurt you", "did i hurt you", "have i wounded you", "did i wound you", "have i failed you", "did i fail you", "do you hate");
	}

	private boolean isBondTrustProbe(String normalized)
	{
		return containsPhrase(normalized, "do you trust", "can you trust", "could you trust", "will you trust", "why dont you trust", "why don't you trust", "why do you trust", "what would make you trust me", "what would help you trust me", "what would it take for you to trust me", "what do i need to do for you to trust me", "how do i earn your trust", "how could i earn your trust", "how can i earn your trust");
	}

	private boolean isBondNeedProbe(String normalized)
	{
		return containsPhrase(normalized, "do i matter to you", "am i important to you", "why do i matter to you", "do you care about me", "why do you care about me", "do you need me", "would you miss me", "would you care if i left", "would you notice if i left", "would you miss me if i left", "would you miss me if i was gone");
	}

	private boolean isBondLoyaltyProbe(String normalized)
	{
		return containsPhrase(normalized, "would you stay with me", "will you stay with me", "would you stand with me", "will you stand with me", "would you choose me", "will you choose me", "would you protect me", "will you protect me", "are you on my side", "whose side are you on", "would you remain with me", "will you remain with me", "would you keep choosing me", "will you keep choosing me")
			|| isBondLoyaltyDilemmaProbe(normalized);
	}

	private boolean isBondLoyaltyDilemmaProbe(String normalized)
	{
		final boolean forcedChoice = containsPhrase(normalized, "who would you choose", "who do you choose", "which one would you choose", "which one do you choose", "if you had to choose between", "if i had to choose between", "choose between");
		final boolean sacrificeFrame = containsPhrase(normalized, "if i had to kill one person", "if you had to kill one person", "kill one of us", "if one of us had to die", "if one of us had to be sacrificed", "if only one of us could live", "if you could only save one", "if you could save only one", "if you had to save one of us");
		final boolean personalStake = containsPhrase(normalized, "you or", "me or", "or you", "or me", "save me", "save you", "protect me", "protect you");
		return (forcedChoice && personalStake) || sacrificeFrame;
	}
}
