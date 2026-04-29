package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerBankFoundationData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerKnowledgeData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcBehaviorFamily;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgeCard;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgePack;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgeSelection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;

public class FakePlayerKnowledgeService
{
	private final FakePlayerKnowledgeData _data;
	private final FakePlayerSelectionMatchSupport _matchSupport;
	private final FpcRegistry _registry;
	private final FakePlayerBankFoundationData _bankFoundationData;

	public FakePlayerKnowledgeService(FakePlayerKnowledgeData data, FakePlayerMessageClassifier classifier, FpcRegistry registry, FakePlayerBankFoundationData bankFoundationData)
	{
		_data = data;
		_matchSupport = new FakePlayerSelectionMatchSupport(classifier);
		_registry = registry;
		_bankFoundationData = bankFoundationData;
	}

	public FpcKnowledgeSelection select(String fakePlayerId, String audienceName, String channel, String knowledgeType, String incomingText)
	{
		final List<ScoredEntry> scoredEntries = new ArrayList<>();
		final List<ScoredPack> scoredPacks = new ArrayList<>();
		final String normalizedAudience = _matchSupport.normalize(audienceName);
		final String normalizedChannel = _matchSupport.normalize(channel);
		final String normalizedKnowledgeType = _matchSupport.normalize(knowledgeType);
		final String normalizedText = _matchSupport.normalizeText(incomingText);
		final String behaviorFamilyId = resolveBehaviorFamilyId(fakePlayerId);
		final int requestedLevel = extractRequestedLevel(normalizedText);

		addMatches(scoredEntries, _data.getEntriesForFpc(fakePlayerId), true, normalizedAudience, normalizedChannel, normalizedKnowledgeType, normalizedText, requestedLevel);
		addMatches(scoredEntries, _data.getDefaultEntries(), false, normalizedAudience, normalizedChannel, normalizedKnowledgeType, normalizedText, requestedLevel);
		addPackMatches(scoredPacks, behaviorFamilyId, normalizedKnowledgeType, normalizedText, normalizedChannel);
		if (scoredEntries.isEmpty() && scoredPacks.isEmpty())
		{
			return FpcKnowledgeSelection.EMPTY;
		}

		scoredEntries.sort(Comparator.comparingInt(ScoredEntry::score).reversed());
		scoredPacks.sort(Comparator.comparingInt(ScoredPack::score).reversed());

		final Set<String> factLines = new LinkedHashSet<>();
		final Set<String> candidateLines = new LinkedHashSet<>();
		final Set<String> heuristicLines = new LinkedHashSet<>();
		final StringBuilder summary = new StringBuilder();
		String topicId = "";
		String confidenceHint = "";
		String scope = "";
		int summaryCount = 0;

		for (ScoredEntry scoredEntry : scoredEntries)
		{
			final FpcKnowledgeCard entry = scoredEntry.entry();
			if (topicId.isBlank())
			{
				topicId = entry.getTopicId();
			}
			if (confidenceHint.isBlank())
			{
				confidenceHint = entry.getConfidenceHint();
			}
			for (String fact : entry.getFactLines())
			{
				factLines.add(fact);
				if (factLines.size() >= 5)
				{
					break;
				}
			}

			final String candidateLine = composeCandidateLine(entry, normalizedChannel);
			if ((candidateLine != null) && !candidateLine.isBlank())
			{
				candidateLines.add(candidateLine);
			}

			if ((summaryCount < 3) && !entry.getSummary().isBlank())
			{
				if (summaryCount > 0)
				{
					summary.append(" ");
				}
				summary.append("[").append(entry.getQuestionType()).append("] ").append(cleanSummary(entry.getSummary()));
				summaryCount++;
			}

			if ((candidateLines.size() >= 4) && (factLines.size() >= 4))
			{
				break;
			}
		}

		for (ScoredPack scoredPack : scoredPacks)
		{
			final FpcKnowledgePack pack = scoredPack.pack();
			if (topicId.isBlank())
			{
				topicId = "pack:" + pack.getId();
			}
			if (confidenceHint.isBlank())
			{
				confidenceHint = packConfidenceHint(pack);
			}
			for (String fact : splitPipe(pack.getFacts()))
			{
				factLines.add(fact);
				if (factLines.size() >= 5)
				{
					break;
				}
			}
			for (String heuristic : splitPipe(pack.getHeuristics()))
			{
				heuristicLines.add(heuristic);
				if (heuristicLines.size() >= 4)
				{
					break;
				}
			}
			if (scope.isBlank() && !pack.getScope().isBlank())
			{
				scope = pack.getScope().trim().replaceAll("\\s+", " ");
			}
			if ((summaryCount < 3) && !pack.getSummary().isBlank())
			{
				if (summaryCount > 0)
				{
					summary.append(" ");
				}
				summary.append("[").append(pack.getDomain()).append("] ").append(cleanSummary(pack.getSummary()));
				summaryCount++;
			}
			if ((candidateLines.size() >= 4) && (factLines.size() >= 4))
			{
				break;
			}
		}

		if (topicId.isBlank() && confidenceHint.isBlank() && summary.isEmpty() && factLines.isEmpty() && candidateLines.isEmpty() && heuristicLines.isEmpty() && scope.isBlank())
		{
			return FpcKnowledgeSelection.EMPTY;
		}
		return new FpcKnowledgeSelection(topicId, confidenceHint, summary.toString(), List.copyOf(factLines), List.copyOf(candidateLines), List.copyOf(heuristicLines), scope);
	}

	private void addMatches(List<ScoredEntry> scoredEntries, List<FpcKnowledgeCard> entries, boolean exactFpc, String normalizedAudience, String normalizedChannel, String normalizedKnowledgeType, String normalizedText, int requestedLevel)
	{
		for (FpcKnowledgeCard entry : entries)
		{
			final boolean farmingLevelBand = isFarmingLevelBandCard(entry);
			if (farmingLevelBand && (!"farming".equals(normalizedKnowledgeType) || (requestedLevel <= 0) || !matchesRequestedFarmingLevelBand(entry, requestedLevel)))
			{
				continue;
			}
			final boolean triggerMatch = _matchSupport.matchesPipeTriggers(entry.getTriggers(), normalizedText);
			final boolean hasTriggers = _matchSupport.hasPipeValues(entry.getTriggers());
			final boolean questionTypeMatch = matchesQuestionType(entry.getQuestionType(), normalizedKnowledgeType);
			final boolean levelBandDirectMatch = farmingLevelBand && "farming".equals(normalizedKnowledgeType) && (requestedLevel > 0);
			if (!_matchSupport.matchesChannel(entry.getChannel(), normalizedChannel) || !_matchSupport.matchesAudience(entry.getAudience(), normalizedAudience))
			{
				continue;
			}
			if (hasTriggers && !triggerMatch && !levelBandDirectMatch)
			{
				continue;
			}
			if (!hasTriggers && !questionTypeMatch)
			{
				continue;
			}

			int score = entry.getPriority();
			score += exactFpc ? 1000 : 100;
			score += _matchSupport.audienceScore(entry.getAudience(), normalizedAudience);
			score += _matchSupport.channelScore(entry.getChannel(), normalizedChannel);
			score += questionTypeMatch ? questionTypeScore(entry.getQuestionType(), normalizedKnowledgeType) : 0;
			score += triggerMatch ? 220 : (levelBandDirectMatch ? 80 : 0);
			score += levelBandScore(entry, normalizedKnowledgeType, requestedLevel);
			if ("cautious".equalsIgnoreCase(entry.getConfidenceHint()))
			{
				score -= 10;
			}
			scoredEntries.add(new ScoredEntry(entry, score));
		}
	}

	private void addPackMatches(List<ScoredPack> scoredPacks, String behaviorFamilyId, String normalizedKnowledgeType, String normalizedText, String normalizedChannel)
	{
		for (FpcKnowledgePack pack : _bankFoundationData.getKnowledgePacks())
		{
			final int domainScore = scorePack(pack, normalizedKnowledgeType, normalizedText);
			if (domainScore <= 0)
			{
				continue;
			}
			final int familyScore = scorePackFamily(pack.getLinkedFamilies(), behaviorFamilyId);
			if (familyScore < 0)
			{
				continue;
			}

			int score = domainScore + familyScore;
			score += "whisper".equals(normalizedChannel) ? 15 : 0;
			if ("canonical_official".equalsIgnoreCase(pack.getSourceTier()))
			{
				score += 60;
			}
			if ("official_system".equalsIgnoreCase(pack.getPackType()) || "official_progression".equalsIgnoreCase(pack.getPackType()) || "official_route".equalsIgnoreCase(pack.getPackType()))
			{
				score += 25;
			}
			scoredPacks.add(new ScoredPack(pack, score));
		}
	}

	private boolean matchesQuestionType(String entryQuestionType, String normalizedKnowledgeType)
	{
		final String questionType = _matchSupport.normalize(entryQuestionType);
		return questionType.isBlank() || "any".equals(questionType) || questionType.equals(normalizedKnowledgeType);
	}

	private int questionTypeScore(String entryQuestionType, String normalizedKnowledgeType)
	{
		final String questionType = _matchSupport.normalize(entryQuestionType);
		if (questionType.isBlank() || "any".equals(questionType))
		{
			return 0;
		}
		return questionType.equals(normalizedKnowledgeType) ? 140 : 0;
	}

	private int levelBandScore(FpcKnowledgeCard entry, String normalizedKnowledgeType, int requestedLevel)
	{
		if ((requestedLevel <= 0) || !"farming".equals(normalizedKnowledgeType))
		{
			return 0;
		}

		final String topicId = _matchSupport.normalize(entry.getTopicId());
		if ("farming_99_caution".equals(topicId))
		{
			return (requestedLevel == 99) ? 780 : ((requestedLevel >= 89) ? 120 : 0);
		}
		if (isFarmingLevelBandCard(entry))
		{
			return 760;
		}
		if (("local_farming_route".equals(topicId) || "generic_farming_route".equals(topicId)) && (requestedLevel > 52))
		{
			return -700;
		}
		return 0;
	}

	private boolean isFarmingLevelBandCard(FpcKnowledgeCard entry)
	{
		return _matchSupport.normalize(entry.getTopicId()).startsWith("farming_level_");
	}

	private boolean matchesRequestedFarmingLevelBand(FpcKnowledgeCard entry, int requestedLevel)
	{
		final int[] band = parseFarmingLevelBand(entry.getTopicId());
		return (band != null) && (requestedLevel >= band[0]) && (requestedLevel <= band[1]);
	}

	private int[] parseFarmingLevelBand(String topicId)
	{
		final String normalizedTopic = _matchSupport.normalize(topicId);
		if (!normalizedTopic.startsWith("farming_level_"))
		{
			return null;
		}

		final String[] parts = normalizedTopic.split("_");
		if (parts.length < 3)
		{
			return null;
		}

		final int minLevel = parseLevelToken(parts[2]);
		if (minLevel <= 0)
		{
			return null;
		}
		int maxLevel = minLevel;
		if ((parts.length > 3) && isNumeric(parts[3]))
		{
			maxLevel = parseLevelToken(parts[3]);
		}
		if ((maxLevel <= 0) || (maxLevel < minLevel))
		{
			return null;
		}
		return new int[]
		{
			minLevel,
			maxLevel
		};
	}

	private String composeCandidateLine(FpcKnowledgeCard entry, String normalizedChannel)
	{
		String answer = ("whisper".equals(normalizedChannel) && !entry.getWhisperAnswer().isBlank()) ? entry.getWhisperAnswer() : entry.getGeneralAnswer();
		if (answer.isBlank())
		{
			answer = entry.getSummary();
		}
		if (answer.isBlank())
		{
			return null;
		}

		if ("whisper".equals(normalizedChannel) && !entry.getShortAdvice().isBlank())
		{
			answer = answer + " " + entry.getShortAdvice();
		}
		return answer.trim().replaceAll("\\s+", " ");
	}

	private String resolveBehaviorFamilyId(String fakePlayerId)
	{
		final FpcDefinition definition = _registry.getDefinitionById(fakePlayerId);
		if (definition == null)
		{
			return "";
		}

		FpcBehaviorFamily best = null;
		int bestScore = Integer.MIN_VALUE;
		for (FpcBehaviorFamily family : _bankFoundationData.getBehaviorFamilies())
		{
			int score = scorePipeMatch(family.getLinkedPersonaTemplates(), definition.getPersonaTemplate()) * 3;
			score += scorePipeMatch(family.getLinkedArchetypes(), definition.getArchetype()) * 2;
			score += scoreTokenOverlap(family.getLinkedArchetypes(), definition.getPersonaTemplate());
			score += scoreTokenOverlap(family.getLinkedPersonaTemplates(), definition.getArchetype());
			if (score > bestScore)
			{
				best = family;
				bestScore = score;
			}
		}
		return ((best != null) && (bestScore > 0)) ? best.getId() : "";
	}

	private int scorePack(FpcKnowledgePack pack, String normalizedKnowledgeType, String normalizedText)
	{
		if ("travel_destination".equals(normalizedKnowledgeType))
		{
			return scoreTravelPack(pack, normalizedText);
		}
		if ("farming".equals(normalizedKnowledgeType))
		{
			return scoreFarmingPack(pack, normalizedText);
		}
		if ("class_choice".equals(normalizedKnowledgeType))
		{
			return scoreClassChoicePack(pack, normalizedText);
		}
		if ("progression_route".equals(normalizedKnowledgeType))
		{
			return scoreProgressionRoutePack(pack, normalizedText);
		}
		if ("progression_strength".equals(normalizedKnowledgeType))
		{
			return scoreProgressionStrengthPack(pack, normalizedText);
		}

		final String domain = pack.getDomain();
		final String normalizedDomain = _matchSupport.normalize(domain);
		if (normalizedDomain.isBlank())
		{
			return 0;
		}

		switch (normalizedDomain)
		{
			case "essence":
				if (Set.of("system_basics", "progression_route", "progression_strength", "party_farm", "server_rules").contains(normalizedKnowledgeType))
				{
					return 220;
				}
				if (containsAny(normalizedText, "essence", "auto hunting", "auto-hunting", "teleport", "orven", "transcendent", "sayha", "cardinal", "giran seals"))
				{
					return 180;
				}
				return 0;
			case "zones":
				if (Set.of("progression_route", "party_farm", "progression_strength").contains(normalizedKnowledgeType))
				{
					return 210;
				}
				if (containsAny(normalizedText, "agony", "camp", "gorgon", "cruma", "massacre", "tower", "wasteland", "southern wasteland", "orc fortress", "iron heart", "ironheart"))
				{
					return 190;
				}
				return 0;
			case "warg":
				if (Set.of("class_progression", "class_skills", "comparison", "itemization", "currency", "server_rules").contains(normalizedKnowledgeType) && containsAny(normalizedText, "warg", "wolf", "wp", "cardinal", "heroic", "seal", "spellbook", "jewel", "jewels", "antharas earring", "immortality"))
				{
					return 230;
				}
				if (containsAny(normalizedText, "warg", "wolf", "wp", "cardinal", "heroic", "spellbook", "giran seal", "aden essence", "jewel", "jewels", "antharas earring", "immortality", "dream dungeon", "monster invasion", "iron heart"))
				{
					return 200;
				}
				return 0;
			default:
				return 0;
		}
	}

	private int scoreTravelPack(FpcKnowledgePack pack, String normalizedText)
	{
		final String packId = _matchSupport.normalize(pack.getId());
		final String domain = _matchSupport.normalize(pack.getDomain());
		if ("travel_and_share_location".equals(packId))
		{
			return 260;
		}
		if ("essence_systems_core".equals(packId) && containsAny(normalizedText, "teleport", "gatekeeper", "travel", "how do i get", "how i get", "go to", "get to", "reach"))
		{
			return 180;
		}
		if ("zones".equals(domain) && containsAny(normalizedText, "cruma", "cruma tower", "abandoned camp", "ruins of agony", "ruins of despair", "wasteland", "southern wasteland", "orc fortress", "iron heart", "ironheart", "gorgon", "massacre"))
		{
			return 150;
		}
		return 0;
	}

	private int scoreFarmingPack(FpcKnowledgePack pack, String normalizedText)
	{
		final String domain = _matchSupport.normalize(pack.getDomain());
		if ("zones".equals(domain))
		{
			return containsAny(normalizedText, "farm", "grind", "hunt", "spot", "exp", "xp", "adena", "route", "agony", "camp", "gorgon", "cruma", "massacre", "wasteland") ? 230 : 180;
		}
		final String packId = _matchSupport.normalize(pack.getId());
		if ("orven_and_transcendent".equals(packId) && containsAny(normalizedText, "orven", "transcendent"))
		{
			return 165;
		}
		return 0;
	}

	private int scoreClassChoicePack(FpcKnowledgePack pack, String normalizedText)
	{
		final String domain = _matchSupport.normalize(pack.getDomain());
		return "warg".equals(domain) && containsAny(normalizedText, "warg", "wolf", "wp", "fist weapon", "sigil") ? 220 : 0;
	}

	private int scoreProgressionRoutePack(FpcKnowledgePack pack, String normalizedText)
	{
		final String packId = _matchSupport.normalize(pack.getId());
		if ("orven_and_transcendent".equals(packId))
		{
			return 250;
		}
		if ("essence_systems_core".equals(packId) || "travel_and_share_location".equals(packId))
		{
			return containsAny(normalizedText, "start", "first", "beginner", "route", "orven", "transcendent", "teleport", "teleports") ? 180 : 120;
		}
		final String domain = _matchSupport.normalize(pack.getDomain());
		if ("zones".equals(domain) && containsAny(normalizedText, "where next", "where should i go", "where do i go", "where can i go", "agony", "camp", "cruma", "wasteland", "southern wasteland", "orc fortress"))
		{
			return 145;
		}
		return 0;
	}

	private int scoreProgressionStrengthPack(FpcKnowledgePack pack, String normalizedText)
	{
		final String packId = _matchSupport.normalize(pack.getId());
		if ("orven_and_transcendent".equals(packId) && containsAny(normalizedText, "beginner", "start", "first", "what should i do first"))
		{
			return 150;
		}
		if ("essence_systems_core".equals(packId) && containsAny(normalizedText, "undergeared", "under geared", "dying", "weak", "sustain"))
		{
			return 120;
		}
		return 0;
	}

	private int scorePackFamily(String linkedFamilies, String behaviorFamilyId)
	{
		if (linkedFamilies == null || linkedFamilies.isBlank())
		{
			return 0;
		}
		if ((behaviorFamilyId == null) || behaviorFamilyId.isBlank())
		{
			return 15;
		}
		for (String family : splitPipe(linkedFamilies))
		{
			if (family.equals(_matchSupport.normalize(behaviorFamilyId)))
			{
				return 90;
			}
		}
		return -25;
	}

	private String packConfidenceHint(FpcKnowledgePack pack)
	{
		if ("canonical_official".equalsIgnoreCase(pack.getSourceTier()))
		{
			return "grounded";
		}
		if ("local_policy".equalsIgnoreCase(pack.getSourceTier()))
		{
			return "cautious";
		}
		return "steady";
	}

	private String cleanSummary(String summary)
	{
		final String cleaned = summary.trim().replaceAll("\\s+", " ");
		if (cleaned.length() <= 110)
		{
			return cleaned;
		}
		int cut = cleaned.lastIndexOf(' ', 110);
		if (cut <= 0)
		{
			cut = 110;
		}
		return cleaned.substring(0, cut).trim() + "...";
	}

	private int extractRequestedLevel(String normalizedText)
	{
		if ((normalizedText == null) || normalizedText.isBlank())
		{
			return -1;
		}

		final String[] tokens = normalizedText.replaceAll("[^a-z0-9']+", " ").trim().split("\\s+");
		for (int i = 0; i < tokens.length; i++)
		{
			final boolean tokenCarriesLevelMarker = tokens[i].matches("(?:(?:lv|lvl|level)\\d{1,3}|\\d{1,3}(?:lv|lvl|level))");
			final int level = parseLevelToken(tokens[i]);
			if (level <= 0)
			{
				continue;
			}

			final String previous = (i > 0) ? tokens[i - 1] : "";
			final String next = ((i + 1) < tokens.length) ? tokens[i + 1] : "";
			final String beforePrevious = (i > 1) ? tokens[i - 2] : "";
			if (tokenCarriesLevelMarker)
			{
				return level;
			}
			if (isLevelMarker(previous) || isLevelMarker(next))
			{
				return level;
			}
			if ((isFarmLevelContext(previous) || isFarmLevelContext(next)) || (isFarmLevelContext(beforePrevious) && "at".equals(previous)))
			{
				return level;
			}
		}
		return -1;
	}

	private boolean isLevelMarker(String token)
	{
		return "level".equals(token) || "levels".equals(token) || "lvl".equals(token) || "lv".equals(token);
	}

	private boolean isFarmLevelContext(String token)
	{
		return "farm".equals(token) || "farming".equals(token) || "hunt".equals(token) || "hunting".equals(token) || "grind".equals(token) || "grinding".equals(token) || "spot".equals(token) || "exp".equals(token) || "xp".equals(token) || "experience".equals(token);
	}

	private int parseLevelToken(String token)
	{
		if ((token == null) || token.isBlank())
		{
			return -1;
		}
		final String digitsOnly = token.replaceAll("\\D+", "");
		if (!isNumeric(digitsOnly))
		{
			return -1;
		}
		try
		{
			final int level = Integer.parseInt(digitsOnly);
			return ((level >= 1) && (level <= 99)) ? level : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private boolean isNumeric(String token)
	{
		return (token != null) && !token.isBlank() && token.chars().allMatch(Character::isDigit);
	}

	private boolean containsAny(String normalizedText, String... phrases)
	{
		for (String phrase : phrases)
		{
			if (_matchSupport.containsPhrase(normalizedText, phrase))
			{
				return true;
			}
		}
		return false;
	}

	private int scorePipeMatch(String pipeValues, String candidate)
	{
		final String normalizedCandidate = _matchSupport.normalize(candidate);
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

	private List<String> splitPipe(String value)
	{
		final String normalized = _matchSupport.normalize(value);
		if (normalized.isBlank())
		{
			return List.of();
		}
		return java.util.Arrays.stream(normalized.split("\\|")).map(String::trim).filter(part -> !part.isBlank()).toList();
	}

	private List<String> tokenize(String value)
	{
		final String normalized = _matchSupport.normalize(value);
		if (normalized.isBlank())
		{
			return List.of();
		}
		return java.util.Arrays.stream(normalized.split("[_\\-\\s]+")).map(String::trim).filter(part -> !part.isBlank()).toList();
	}

	private static record ScoredEntry(FpcKnowledgeCard entry, int score)
	{
	}

	private static record ScoredPack(FpcKnowledgePack pack, int score)
	{
	}
}
