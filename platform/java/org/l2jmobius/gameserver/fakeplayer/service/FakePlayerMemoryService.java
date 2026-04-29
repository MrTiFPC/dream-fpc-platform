package org.l2jmobius.gameserver.fakeplayer.service;

import org.l2jmobius.gameserver.fakeplayer.model.FpcMemoryBundle;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRetrievedMemorySection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;

public class FakePlayerMemoryService
{
	private final FakePlayerChatService _chatService;
	private final FakePlayerSocialMemoryService _socialMemoryService;

	public FakePlayerMemoryService(FakePlayerChatService chatService, FakePlayerSocialMemoryService socialMemoryService)
	{
		_chatService = chatService;
		_socialMemoryService = socialMemoryService;
	}

	public FpcMemoryBundle buildReplyMemory(String speakerName, String targetPlayerName)
	{
		return buildReplyMemory(speakerName, targetPlayerName, "", "", "", "", "");
	}

	public FpcMemoryBundle buildReplyMemory(String speakerName, String targetPlayerName, String currentUtterance, String messageCategory, String focusIntent)
	{
		return buildReplyMemory(speakerName, targetPlayerName, currentUtterance, messageCategory, focusIntent, "", "");
	}

	public FpcMemoryBundle buildReplyMemory(String speakerName, String targetPlayerName, String currentUtterance, String messageCategory, String focusIntent, String memoryRetrievalPolicy)
	{
		return buildReplyMemory(speakerName, targetPlayerName, currentUtterance, messageCategory, focusIntent, memoryRetrievalPolicy, "");
	}

	public FpcMemoryBundle buildReplyMemory(String speakerName, String targetPlayerName, String currentUtterance, String messageCategory, String focusIntent, String memoryRetrievalPolicy, String matchedRelationshipSummary)
	{
		if ((speakerName == null) || speakerName.isBlank() || (targetPlayerName == null) || targetPlayerName.isBlank())
		{
			return FpcMemoryBundle.EMPTY;
		}

		final String recentConversationSummary = _chatService.describeRecentConversation(speakerName, targetPlayerName);
		final String salientMemorySummary = _chatService.describeSelectiveMemory(speakerName, targetPlayerName);
		final FpcRelationshipSnapshot relationshipSnapshot = _chatService.getRelationshipSnapshot(speakerName, targetPlayerName);
		final FpcSocialSnapshot socialSnapshot = _socialMemoryService.getSnapshot(speakerName, targetPlayerName);
		final String prioritySummary = composeInteractionPriority(currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot);
		final String reflectionSummary = composeReflectionSummary(targetPlayerName, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot);
		final String trustSummary = composeTrustSummary(targetPlayerName, relationshipSnapshot, socialSnapshot);
		final String repairSummary = composeRepairSummary(targetPlayerName, relationshipSnapshot, socialSnapshot);
		final String pressureSummary = composePressureSummary(targetPlayerName, relationshipSnapshot, socialSnapshot);
		final RetrievedMemoryComposition retrievedMemory = composeRetrievedMemorySummary(targetPlayerName, currentUtterance, messageCategory, focusIntent, memoryRetrievalPolicy, matchedRelationshipSummary, recentConversationSummary, salientMemorySummary, relationshipSnapshot, socialSnapshot, prioritySummary, reflectionSummary);
		return new FpcMemoryBundle(relationshipSnapshot, socialSnapshot, recentConversationSummary, salientMemorySummary, retrievedMemory.summary(), prioritySummary, reflectionSummary, trustSummary, repairSummary, pressureSummary, retrievedMemory.selectionSummary(), retrievedMemory.retrievalSections());
	}

	private RetrievedMemoryComposition composeRetrievedMemorySummary(String targetPlayerName, String currentUtterance, String messageCategory, String focusIntent, String memoryRetrievalPolicy, String matchedRelationshipSummary, String recentConversationSummary, String salientMemorySummary, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot, String prioritySummary, String reflectionSummary)
	{
		final String normalizedTarget = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final String prefix = "Memory focus with " + normalizedTarget + ": ";
		final int maxContentLength = Math.max(0, 460 - prefix.length());
		final StringBuilder sb = new StringBuilder();
		appendSectionWithinBudget(sb, "Priority", prioritySummary, maxContentLength);
		appendSectionWithinBudget(sb, "Reflection", reflectionSummary, maxContentLength);
		final java.util.List<FpcRetrievedMemorySection> rankedSections = new java.util.ArrayList<>(4);
		if ((matchedRelationshipSummary != null) && !matchedRelationshipSummary.isBlank())
		{
			int matchedScore = scoreSectionPriority("matched", memoryRetrievalPolicy, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot);
			if (recentConversationSummary.isBlank())
			{
				matchedScore += 15;
			}
			if (salientMemorySummary.isBlank())
			{
				matchedScore += 15;
			}
			if (((relationshipSnapshot == null) || !relationshipSnapshot.hasMeaningfulHistory()) && ((socialSnapshot == null) || !socialSnapshot.hasMeaningfulHistory()))
			{
				matchedScore += 10;
			}
			addRankedSection(rankedSections, "Relationship cue", matchedRelationshipSummary, 180, matchedScore);
		}
		if ((relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory())
		{
			addRankedSection(rankedSections, "Relationship memory", buildRelationshipMemorySummary(relationshipSnapshot), 150, scoreSectionPriority("relationship", memoryRetrievalPolicy, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot));
		}
		if ((socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory())
		{
			addRankedSection(rankedSections, "Social memory", socialSnapshot.getSummary(), 190, scoreSectionPriority("social", memoryRetrievalPolicy, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot));
		}
		addRankedSection(rankedSections, "Recent memory", recentConversationSummary, 140, scoreSectionPriority("recent", memoryRetrievalPolicy, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot));
		addRankedSection(rankedSections, "Selective memory", salientMemorySummary, 140, scoreSectionPriority("selective", memoryRetrievalPolicy, currentUtterance, messageCategory, focusIntent, relationshipSnapshot, socialSnapshot));
		rankedSections.sort(java.util.Comparator.comparingInt(FpcRetrievedMemorySection::getScore).reversed());
		for (FpcRetrievedMemorySection section : rankedSections)
		{
			appendSectionWithinBudget(sb, section.getLabel(), section.getValue(), maxContentLength);
		}
		if (sb.length() == 0)
		{
			return new RetrievedMemoryComposition("", "", java.util.List.of());
		}
		return new RetrievedMemoryComposition(prefix + sb, composeSectionOrderSummary(rankedSections), java.util.List.copyOf(rankedSections));
	}

	private String buildRelationshipMemorySummary(FpcRelationshipSnapshot relationshipSnapshot)
	{
		if ((relationshipSnapshot == null) || !relationshipSnapshot.hasMeaningfulHistory())
		{
			return "";
		}
		final StringBuilder relationship = new StringBuilder();
		relationship.append("familiarity=").append(relationshipSnapshot.getFamiliarity());
		relationship.append(", trust=").append(relationshipSnapshot.getTrust());
		relationship.append(", respect=").append(relationshipSnapshot.getRespect());
		relationship.append(", tension=").append(relationshipSnapshot.getTension());
		if (relationshipSnapshot.getResentment() > 0)
		{
			relationship.append(", resentment=").append(relationshipSnapshot.getResentment());
		}
		if (relationshipSnapshot.getPlayerKillCount() > 0)
		{
			relationship.append(", playerKillCount=").append(relationshipSnapshot.getPlayerKillCount());
		}
		if (!relationshipSnapshot.getCurrentGoal().isBlank())
		{
			relationship.append(", goal=").append(relationshipSnapshot.getCurrentGoal());
		}
		if (!relationshipSnapshot.getActiveNeed().isBlank())
		{
			relationship.append(", need=").append(relationshipSnapshot.getActiveNeed());
		}
		if (!relationshipSnapshot.getLastImportantTopic().isBlank())
		{
			relationship.append(", topic=").append(relationshipSnapshot.getLastImportantTopic());
		}
		return relationship.toString();
	}

	private String composeSectionOrderSummary(java.util.List<FpcRetrievedMemorySection> rankedSections)
	{
		if ((rankedSections == null) || rankedSections.isEmpty())
		{
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (FpcRetrievedMemorySection section : rankedSections)
		{
			if ((section == null) || (section.getLabel() == null) || section.getLabel().isBlank())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(" > ");
			}
			sb.append(section.getLabel());
		}
		return trimText(sb.toString(), 180);
	}

	private String composeReflectionSummary(String targetPlayerName, String currentUtterance, String messageCategory, String focusIntent, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		final boolean hasRelationshipHistory = (relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory();
		final boolean hasSocialHistory = (socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory();
		if (!hasRelationshipHistory && !hasSocialHistory)
		{
			return "";
		}

		final String normalizedTarget = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final String socialLabel = hasSocialHistory ? socialSnapshot.getSocialLabel().trim().toLowerCase(java.util.Locale.ROOT) : "";
		final StringBuilder reflection = new StringBuilder();
		if (isIdentityTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("This turn should anchor on origin, selfhood, and why ").append(normalizedTarget).append(" matters to that story");
		}
		else if (isRepairTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("Repair with ").append(normalizedTarget).append(" still needs proof of return before easy softness");
		}
		else if (isTrustTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("Trust with ").append(normalizedTarget).append(" still has to read as earned through consistency, not charm");
		}
		else if (isWoundTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("This turn should stay close to the wound or pressure underneath the calm surface with ").append(normalizedTarget);
		}
		else if (isNeedTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("Need around ").append(normalizedTarget).append(" is about chosen presence, not emotional certainty");
		}
		else if (isProtectionTurn(messageCategory, focusIntent, currentUtterance))
		{
			reflection.append("This turn should name what still feels worth protecting with ").append(normalizedTarget).append(" instead of drifting into broad autobiography");
		}
		else switch (socialLabel)
		{
			case "hostile":
				reflection.append("The bond with ").append(normalizedTarget).append(" still reads as unsafe; easy warmth would feel false");
				break;
			case "guarded":
				reflection.append("The bond with ").append(normalizedTarget).append(" is still being tested; steadiness matters more than charm");
				break;
			case "trusted":
			case "friendly":
			case "familiar":
			case "warm":
				reflection.append("The bond with ").append(normalizedTarget).append(" is opening; consistency matters more than big promises");
				break;
			default:
				if (hasRelationshipHistory && ((relationshipSnapshot.getResentment() > 0) || (relationshipSnapshot.getTension() > 0)))
				{
					reflection.append("The bond with ").append(normalizedTarget).append(" still carries pressure from earlier friction");
				}
				else
				{
					reflection.append("The next honest turn with ").append(normalizedTarget).append(" matters more than a polished one");
				}
				break;
		}
		appendReflectionFacet(reflection, "goal", hasRelationshipHistory ? relationshipSnapshot.getCurrentGoal() : "");
		appendReflectionFacet(reflection, "need", hasRelationshipHistory ? relationshipSnapshot.getActiveNeed() : "");
		appendReflectionFacet(reflection, "pressure", hasRelationshipHistory ? relationshipSnapshot.getLastImportantTopic() : "");
		return trimText(reflection.toString(), 180);
	}

	private String composeInteractionPriority(String currentUtterance, String messageCategory, String focusIntent, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		final boolean hasRelationshipHistory = (relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory();
		final boolean hasSocialHistory = (socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory();
		final String normalizedCategory = normalizeSignal(messageCategory);
		final String normalizedFocusIntent = normalizeSignal(focusIntent);
		final String normalizedUtterance = normalizeSignal(currentUtterance);
		if (!hasRelationshipHistory && !hasSocialHistory && normalizedCategory.isBlank() && normalizedFocusIntent.isBlank() && normalizedUtterance.isBlank())
		{
			return "";
		}

		final int trust = hasRelationshipHistory ? relationshipSnapshot.getTrust() : 0;
		final int tension = hasRelationshipHistory ? relationshipSnapshot.getTension() : 0;
		final int resentment = hasRelationshipHistory ? relationshipSnapshot.getResentment() : 0;
		final int playerKillCount = hasRelationshipHistory ? relationshipSnapshot.getPlayerKillCount() : 0;
		final String currentGoal = hasRelationshipHistory ? relationshipSnapshot.getCurrentGoal() : "";
		final String activeNeed = hasRelationshipHistory ? relationshipSnapshot.getActiveNeed() : "";
		final String lastTopic = hasRelationshipHistory ? relationshipSnapshot.getLastImportantTopic() : "";
		final String socialLabel = hasSocialHistory ? normalizeSignal(socialSnapshot.getSocialLabel()) : "";
		final int trustBias = hasSocialHistory ? socialSnapshot.getTrustBias() : 0;
		final int guardBias = hasSocialHistory ? socialSnapshot.getGuardBias() : 0;
		final boolean avoidPlayer = hasSocialHistory && socialSnapshot.getActionStance().isAvoidPlayer();
		final boolean refuseParty = hasSocialHistory && socialSnapshot.getActionStance().isRefuseParty();
		final boolean historicalKillHistory = hasSocialHistory && socialSnapshot.hasHistoricalKillHistory();
		final boolean hostile = "hostile".equals(socialLabel) || avoidPlayer || historicalKillHistory || (playerKillCount > 0) || (resentment >= 7) || (tension >= 8);
		final boolean guarded = "guarded".equals(socialLabel) || (guardBias >= 3) || (resentment > 0) || (tension >= 3) || refuseParty;
		final boolean warm = "trusted".equals(socialLabel) || "friendly".equals(socialLabel) || ((trust >= 6) && (trustBias >= 2));
		if (isRepairTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!lastTopic.isBlank())
			{
				return trimText("Test repair slowly around topic=" + lastTopic + "; require follow-through before softening", 150);
			}
			if (!activeNeed.isBlank())
			{
				return trimText("Test repair through activeNeed=" + activeNeed + "; promises alone are not enough", 150);
			}
			return hostile || guarded ? "Test repair slowly and require steadiness before softening" : "Let repair move through consistency, not just apology wording";
		}
		if (isTrustTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!activeNeed.isBlank())
			{
				return trimText("Evaluate trust through activeNeed=" + activeNeed + " rather than easy warmth", 150);
			}
			return guarded ? "Evaluate trust through consistency, honesty, and follow-through before warmth" : "Let earned trust show, but keep it grounded in consistency";
		}
		if (isIdentityTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!currentGoal.isBlank())
			{
				return trimText("Answer from origin and self-concept while staying anchored to currentGoal=" + currentGoal, 150);
			}
			return "Answer from origin and self-concept before relationship defense takes over";
		}
		if (isWoundTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!lastTopic.isBlank())
			{
				return trimText("Answer from the wound under pressure=" + lastTopic + " and avoid polished distance", 150);
			}
			return "Answer from the wound underneath the mask, not from polished smalltalk";
		}
		if (isNeedTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!activeNeed.isBlank())
			{
				return trimText("Answer from activeNeed=" + activeNeed + " and avoid sounding emotionally settled", 150);
			}
			if (!currentGoal.isBlank())
			{
				return trimText("Answer from currentGoal=" + currentGoal + " and keep the opening measured", 150);
			}
		}
		if (isProtectionTurn(normalizedCategory, normalizedFocusIntent, normalizedUtterance))
		{
			if (!currentGoal.isBlank())
			{
				return trimText("Answer from currentGoal=" + currentGoal + "; protect the bond without pretending the pressure is gone", 150);
			}
			if (!lastTopic.isBlank())
			{
				return trimText("Let the reply stay anchored to pressure=" + lastTopic + " rather than sounding generic", 150);
			}
			if (!activeNeed.isBlank())
			{
				return trimText("Name what still feels worth protecting through activeNeed=" + activeNeed + " and keep it concrete", 150);
			}
			return guarded ? "Name what still feels worth protecting and keep the bond concrete, not abstract" : "Name what still feels worth protecting and keep it grounded in the present";
		}
		if (hostile)
		{
			return "Keep guard up and avoid easy warmth";
		}
		if (guarded)
		{
			return !lastTopic.isBlank() ? trimText("Stay open but measured; let pressure=" + lastTopic + " guide the turn", 150) : "Stay open but measured and test steadiness before warmth";
		}
		if (warm)
		{
			return !currentGoal.isBlank() ? trimText("Reward steady cooperation while staying anchored to currentGoal=" + currentGoal, 150) : "Reward steady cooperation without overpromising";
		}
		if (!lastTopic.isBlank())
		{
			return trimText("Let the reply stay anchored to pressure=" + lastTopic + " and keep it honest", 150);
		}
		return "Keep the reply honest and relationship-aware";
	}

	private String composeTrustSummary(String targetPlayerName, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		final boolean hasRelationshipHistory = (relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory();
		final boolean hasSocialHistory = (socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory();
		if (!hasRelationshipHistory && !hasSocialHistory)
		{
			return "";
		}

		final String normalizedTarget = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final String socialLabel = hasSocialHistory ? normalizeSignal(socialSnapshot.getSocialLabel()) : "";
		final int trust = hasRelationshipHistory ? relationshipSnapshot.getTrust() : 0;
		final int respect = hasRelationshipHistory ? relationshipSnapshot.getRespect() : 0;
		final int tension = hasRelationshipHistory ? relationshipSnapshot.getTension() : 0;
		final int resentment = hasRelationshipHistory ? relationshipSnapshot.getResentment() : 0;
		final int trustBias = hasSocialHistory ? socialSnapshot.getTrustBias() : 0;
		final int guardBias = hasSocialHistory ? socialSnapshot.getGuardBias() : 0;
		if ("hostile".equals(socialLabel) || ((relationshipSnapshot != null) && ((relationshipSnapshot.getPlayerKillCount() > 0) || (resentment >= 7) || (tension >= 8))))
		{
			return trimText("Trust with " + normalizedTarget + " is badly damaged; safety outranks openness right now", 150);
		}
		if ("guarded".equals(socialLabel) || (guardBias >= 3) || (resentment > 0) || (tension >= 3))
		{
			final StringBuilder sb = new StringBuilder("Trust with ").append(normalizedTarget).append(" is conditional and still weighs steadiness over charm");
			appendReflectionFacet(sb, "need", hasRelationshipHistory ? relationshipSnapshot.getActiveNeed() : "");
			appendReflectionFacet(sb, "topic", hasRelationshipHistory ? relationshipSnapshot.getLastImportantTopic() : "");
			return trimText(sb.toString(), 150);
		}
		if ("trusted".equals(socialLabel) || "friendly".equals(socialLabel) || "familiar".equals(socialLabel) || ((trust >= 6) && ((trustBias >= 2) || (respect >= 5))))
		{
			return trimText("Trust with " + normalizedTarget + " is opening through repeated steady contact, not big promises", 150);
		}
		if (hasRelationshipHistory && ((trust > 0) || (respect > 0)))
		{
			return trimText("Trust with " + normalizedTarget + " is forming through small proof, useful follow-through, and cleaner intent", 150);
		}
		return trimText("Trust with " + normalizedTarget + " is still undecided and will be judged by consistency", 150);
	}

	private String composeRepairSummary(String targetPlayerName, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		final boolean hasRelationshipHistory = (relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory();
		final boolean hasSocialHistory = (socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory();
		if (!hasRelationshipHistory && !hasSocialHistory)
		{
			return "";
		}

		final String normalizedTarget = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final int tension = hasRelationshipHistory ? relationshipSnapshot.getTension() : 0;
		final int resentment = hasRelationshipHistory ? relationshipSnapshot.getResentment() : 0;
		final String topic = hasRelationshipHistory ? relationshipSnapshot.getLastImportantTopic() : "";
		final String need = hasRelationshipHistory ? relationshipSnapshot.getActiveNeed() : "";
		final String socialLabel = hasSocialHistory ? normalizeSignal(socialSnapshot.getSocialLabel()) : "";
		final boolean openRepairPressure = containsAny(topic, "apology", "repair", "abandon", "threat", "distrust", "resent");
		if ("hostile".equals(socialLabel) || (resentment >= 7) || (tension >= 8))
		{
			return trimText("Repair with " + normalizedTarget + " will not move on words alone; distance and caution are still in charge", 150);
		}
		if ("guarded".equals(socialLabel) || openRepairPressure || (resentment > 0) || (tension >= 3))
		{
			final StringBuilder sb = new StringBuilder("Repair with ").append(normalizedTarget).append(" still needs proof of return before softness");
			appendReflectionFacet(sb, "need", need);
			appendReflectionFacet(sb, "topic", topic);
			return trimText(sb.toString(), 150);
		}
		if ("trusted".equals(socialLabel) || "friendly".equals(socialLabel) || "familiar".equals(socialLabel))
		{
			return trimText("Repair with " + normalizedTarget + " can reopen quickly after a clean correction, but it still expects consistency", 150);
		}
		return trimText("Repair with " + normalizedTarget + " is possible, but the next correction has to stay clean and steady", 150);
	}

	private String composePressureSummary(String targetPlayerName, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		final boolean hasRelationshipHistory = (relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory();
		final boolean hasSocialHistory = (socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory();
		if (!hasRelationshipHistory && !hasSocialHistory)
		{
			return "";
		}

		final String normalizedTarget = ((targetPlayerName == null) || targetPlayerName.isBlank()) ? "this player" : targetPlayerName.trim();
		final String goal = hasRelationshipHistory ? relationshipSnapshot.getCurrentGoal() : "";
		final String need = hasRelationshipHistory ? relationshipSnapshot.getActiveNeed() : "";
		final String topic = hasRelationshipHistory ? relationshipSnapshot.getLastImportantTopic() : "";
		final String socialLabel = hasSocialHistory ? normalizeSignal(socialSnapshot.getSocialLabel()) : "";
		final StringBuilder sb = new StringBuilder("Current pressure with ").append(normalizedTarget).append(" centers on");
		boolean appended = false;
		if (!goal.isBlank())
		{
			sb.append(" goal=").append(goal);
			appended = true;
		}
		if (!need.isBlank())
		{
			sb.append(appended ? ", " : " ").append("need=").append(need);
			appended = true;
		}
		if (!topic.isBlank())
		{
			sb.append(appended ? ", " : " ").append("topic=").append(topic);
			appended = true;
		}
		if (!socialLabel.isBlank() && !"neutral".equals(socialLabel))
		{
			sb.append(appended ? ", " : " ").append("stance=").append(socialLabel);
			appended = true;
		}
		if (!appended)
		{
			return "";
		}
		return trimText(sb.toString(), 160);
	}

	private static boolean isRepairTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "apology", "repair", "reassurance", "abandonment", "resentment", "bond_hurt") || signalMatches(focusIntent, "bond_hurt")
			|| containsAny(currentUtterance, "sorry", "apolog", "forgive", "repair", "start over", "stay this time", "dont vanish", "don't vanish");
	}

	private static boolean isIdentityTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "self_story", "self_identity")
			|| containsAny(currentUtterance, "your story", "tell me your story", "who are you", "who are you really", "what are you really", "where do you come from", "what made you");
	}

	private static boolean isTrustTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "distrust", "respect", "praise", "bond_trust", "self_belief") || signalMatches(focusIntent, "bond_trust")
			|| containsAny(currentUtterance, "trust", "earn your trust", "make you trust", "help you trust");
	}

	private static boolean isNeedTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "affection", "companionship", "bond_need", "bond_loyalty", "bond_value", "bond_opinion") || signalMatches(focusIntent, "bond_need", "bond_loyalty", "bond_value")
			|| containsAny(currentUtterance, "need me", "care about me", "matter to you", "important to you", "miss me", "choose me", "stay with me");
	}

	private static boolean isWoundTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "self_state_reflection", "self_belief") || signalMatches(focusIntent, "bond_hurt")
			|| containsAny(currentUtterance, "fear", "afraid", "hiding", "distance", "forgive", "hurt", "loneliness", "abandon");
	}

	private static boolean isProtectionTurn(String messageCategory, String focusIntent, String currentUtterance)
	{
		return signalMatches(messageCategory, "self_state_reflection", "self_preference", "bond_loyalty", "bond_need") || signalMatches(focusIntent, "bond_loyalty", "bond_need")
			|| containsAny(currentUtterance, "protect", "save", "keep safe", "keep open", "remain", "stay", "choose", "stand with", "on my side");
	}

	private int scoreSectionPriority(String sectionType, String memoryRetrievalPolicy, String currentUtterance, String messageCategory, String focusIntent, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		int score = switch (normalizeSignal(sectionType))
		{
			case "matched" -> 85;
			case "relationship" -> 90;
			case "social" -> 80;
			case "recent" -> 70;
			case "selective" -> 60;
			default -> 0;
		};

		final String normalizedPolicy = normalizeSignal(memoryRetrievalPolicy);
		if (normalizedPolicy.contains("recent"))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "recent" -> 30;
				case "selective" -> 10;
				default -> 0;
			};
		}
		if (containsAny(normalizedPolicy, "relationship-critical", "breaches", "returns", "loyalty", "followed through", "follow through", "promises"))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 30;
				case "relationship" -> 35;
				case "social" -> 20;
				case "recent" -> 10;
				default -> 0;
			};
		}
		if (containsAny(normalizedPolicy, "relevant", "useful tips", "useful", "tips"))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "recent" -> 20;
				case "selective" -> 15;
				default -> 0;
			};
		}
		if (containsAny(normalizedPolicy, "routes", "route details"))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "recent" -> 25;
				case "selective" -> 15;
				default -> 0;
			};
		}
		if (containsAny(normalizedPolicy, "threats", "discipline", "clean finishes"))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "social" -> 25;
				case "relationship" -> 15;
				case "recent" -> 10;
				default -> 0;
			};
		}
		if (isRepairTurn(messageCategory, focusIntent, currentUtterance))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 30;
				case "relationship" -> 35;
				case "social" -> 25;
				case "recent" -> 10;
				default -> 0;
			};
		}
		if (isTrustTurn(messageCategory, focusIntent, currentUtterance))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 30;
				case "relationship" -> 35;
				case "social" -> 25;
				case "recent" -> 10;
				default -> 0;
			};
		}
		if (isIdentityTurn(messageCategory, focusIntent, currentUtterance))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 25;
				case "selective" -> 50;
				case "recent" -> 20;
				case "relationship" -> 5;
				case "social" -> -5;
				default -> 0;
			};
		}
		if (isWoundTurn(messageCategory, focusIntent, currentUtterance))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 15;
				case "relationship" -> 20;
				case "social" -> 20;
				case "selective" -> 15;
				default -> 0;
			};
		}
		if (isNeedTurn(messageCategory, focusIntent, currentUtterance) || isProtectionTurn(messageCategory, focusIntent, currentUtterance))
		{
			score += switch (normalizeSignal(sectionType))
			{
				case "matched" -> 25;
				case "relationship" -> 30;
				case "social" -> 15;
				case "recent" -> 15;
				case "selective" -> 10;
				default -> 0;
			};
		}
		if ((relationshipSnapshot != null) && relationshipSnapshot.hasMeaningfulHistory() && ((relationshipSnapshot.getResentment() > 0) || (relationshipSnapshot.getTension() > 0)))
		{
			score += "relationship".equals(normalizeSignal(sectionType)) ? 15 : 0;
		}
		if ((socialSnapshot != null) && socialSnapshot.hasMeaningfulHistory() && !"neutral".equalsIgnoreCase(socialSnapshot.getSocialLabel()))
		{
			score += "social".equals(normalizeSignal(sectionType)) ? 10 : 0;
		}
		return score;
	}

	private static void addRankedSection(java.util.List<FpcRetrievedMemorySection> sections, String label, String value, int sectionMaxLength, int score)
	{
		if ((sections == null) || (label == null) || label.isBlank() || (value == null) || value.isBlank())
		{
			return;
		}
		sections.add(new FpcRetrievedMemorySection(label.trim(), trimText(value, sectionMaxLength), score));
	}

	private static void appendSectionWithinBudget(StringBuilder sb, String label, String value, int maxContentLength)
	{
		if ((sb == null) || (label == null) || label.isBlank() || (value == null) || value.isBlank() || (maxContentLength <= 0))
		{
			return;
		}
		final int remaining = maxContentLength - sb.length();
		final String normalizedLabel = label.trim();
		final String prefix = (sb.length() > 0 ? " " : "") + normalizedLabel + ": ";
		if (remaining <= (prefix.length() + 8))
		{
			return;
		}
		final String truncatedValue = trimText(value, remaining - prefix.length() - 1);
		if (truncatedValue.isBlank())
		{
			return;
		}
		if (sb.length() > 0)
		{
			sb.append(' ');
		}
		sb.append(normalizedLabel).append(": ").append(truncatedValue);
		if (!truncatedValue.endsWith("."))
		{
			sb.append('.');
		}
	}

	private static boolean signalMatches(String value, String... candidates)
	{
		final String normalized = normalizeSignal(value);
		if (normalized.isBlank() || (candidates == null) || (candidates.length == 0))
		{
			return false;
		}
		for (String candidate : candidates)
		{
			if (normalized.equals(normalizeSignal(candidate)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsAny(String value, String... fragments)
	{
		final String normalized = normalizeSignal(value);
		if (normalized.isBlank() || (fragments == null) || (fragments.length == 0))
		{
			return false;
		}
		for (String fragment : fragments)
		{
			final String normalizedFragment = normalizeSignal(fragment);
			if (!normalizedFragment.isBlank() && normalized.contains(normalizedFragment))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalizeSignal(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private record RetrievedMemoryComposition(String summary, String selectionSummary, java.util.List<FpcRetrievedMemorySection> retrievalSections)
	{
	}

	private static void appendSection(StringBuilder sb, String label, String value)
	{
		if ((sb == null) || (label == null) || label.isBlank() || (value == null) || value.isBlank())
		{
			return;
		}
		if (sb.length() > 0)
		{
			sb.append(' ');
		}
		sb.append(label.trim()).append(": ").append(value.trim()).append('.');
	}

	private static void appendReflectionFacet(StringBuilder sb, String label, String value)
	{
		if ((sb == null) || (label == null) || label.isBlank() || (value == null) || value.isBlank())
		{
			return;
		}
		sb.append("; ").append(label.trim()).append('=').append(value.trim());
	}

	private static String trimText(String value, int maxLength)
	{
		if (value == null)
		{
			return "";
		}
		final String trimmed = value.trim();
		if (trimmed.length() <= Math.max(0, maxLength))
		{
			return trimmed;
		}
		return trimmed.substring(0, Math.max(0, maxLength)).trim();
	}
}
