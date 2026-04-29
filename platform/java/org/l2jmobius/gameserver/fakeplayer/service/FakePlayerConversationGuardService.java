package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardAssessment;
import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardBank;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;

public class FakePlayerConversationGuardService
{
	private static final long THREAD_TTL_MS = 45000L;

	private static final class GuardThreadState
	{
		private final FpcConversationGuardAssessment _assessment;
		private final long _expiresAt;

		private GuardThreadState(FpcConversationGuardAssessment assessment, long expiresAt)
		{
			_assessment = assessment;
			_expiresAt = expiresAt;
		}
	}

	private volatile FpcConversationGuardBank _bank;
	private volatile boolean _clanRequestsSupported;
	private final ConcurrentHashMap<String, GuardThreadState> _threadStateByPair = new ConcurrentHashMap<>();

	public FakePlayerConversationGuardService(FpcConversationGuardBank bank)
	{
		_bank = (bank == null) ? FpcConversationGuardBank.defaults() : bank;
	}

	public void setBank(FpcConversationGuardBank bank)
	{
		_bank = (bank == null) ? FpcConversationGuardBank.defaults() : bank;
	}

	public void setClanRequestsSupported(boolean clanRequestsSupported)
	{
		_clanRequestsSupported = clanRequestsSupported;
	}

	public FpcConversationGuardAssessment evaluateIncoming(String speakerName, String targetPlayerName, String incomingText)
	{
		final String normalizedRaw = normalizeRaw(incomingText);
		final String normalizedWords = normalizeWords(incomingText);
		if (normalizedWords.isBlank())
		{
			clearThreadState(speakerName, targetPlayerName);
			return FpcConversationGuardAssessment.NONE;
		}

		final GuardThreadState previous = getThreadState(speakerName, targetPlayerName);
		final boolean infoSeeking = isInfoSeeking(normalizedWords);
		final boolean followUp = (previous != null) && isSensitiveFollowUp(normalizedWords, previous._assessment);

		FpcConversationGuardAssessment assessment = detectMeta(normalizedRaw, normalizedWords, followUp && (previous != null) && FpcConversationGuardAssessment.CATEGORY_META_OOC.equalsIgnoreCase(previous._assessment.getCategory()));
		if (!assessment.isSensitive())
		{
			assessment = detectUnsupported(normalizedWords, infoSeeking, followUp ? ((previous == null) ? "" : previous._assessment.getDomain()) : "");
		}
		if (!assessment.isSensitive() && followUp && (previous != null))
		{
			final FpcConversationGuardAssessment prior = previous._assessment;
			if (FpcConversationGuardAssessment.CATEGORY_META_OOC.equalsIgnoreCase(prior.getCategory()))
			{
				assessment = FpcConversationGuardAssessment.meta(prior.getDomain(), true);
			}
			else if (FpcConversationGuardAssessment.CATEGORY_UNSUPPORTED_REQUEST.equalsIgnoreCase(prior.getCategory()))
			{
				assessment = FpcConversationGuardAssessment.unsupported(prior.getDomain(), prior.getKnowledgeHint(), prior.isMixedKnowledgeRedirect() || infoSeeking, true);
			}
		}

		if (assessment.isSensitive())
		{
			rememberThreadState(speakerName, targetPlayerName, assessment);
		}
		else
		{
			clearThreadState(speakerName, targetPlayerName);
		}
		return assessment;
	}

	public boolean looksLikeMetaLeak(String candidateLine)
	{
		final String normalizedRaw = normalizeRaw(candidateLine);
		final String normalizedWords = normalizeWords(candidateLine);
		return containsAnyPhrase(normalizedWords, _bank.getMetaRequestPhrases()) || containsAnyMarker(normalizedRaw, _bank.getMetaMarkers()) || containsAnyMarker(normalizedRaw, _bank.getOutOfWorldMarkers());
	}

	public boolean looksLikeUnsupportedPromise(String candidateLine)
	{
		final String normalizedWords = normalizeWords(candidateLine);
		if (normalizedWords.isBlank())
		{
			return false;
		}
		if (!containsAnyPhrase(normalizedWords, Set.of("i can", "i will", "i ll", "ill", "let me", "i am going to", "im going to", "i'm going to")))
		{
			return false;
		}
		if (!containsAnyToken(normalizedWords, _bank.getUnsupportedPromiseVerbs()))
		{
			return false;
		}
		return containsAnyToken(normalizedWords, _bank.getUnsupportedPromiseDirectObjects());
	}

	public String chooseReplyLine(String speakerName, String channel, FpcSocialSnapshot socialSnapshot, FpcConversationGuardAssessment assessment)
	{
		final String relation = relationshipBias(socialSnapshot);
		final boolean whisper = "whisper".equalsIgnoreCase(channel);
		final String speaker = normalizeWords(speakerName);
		if ((assessment == null) || !assessment.isSensitive())
		{
			return chooseGenericGroundedReply(channel, socialSnapshot);
		}
		if (FpcConversationGuardAssessment.CATEGORY_META_OOC.equalsIgnoreCase(assessment.getCategory()))
		{
			return chooseMetaReplyLine(speaker, whisper, relation, assessment);
		}
		if (shouldSuppressUnsupportedReply(assessment))
		{
			return null;
		}

		final String domain = assessment.getDomain().toLowerCase(Locale.ENGLISH);
		if ("teleport".equals(domain))
		{
			return chooseTeleportReplyLine(speaker, whisper, relation, assessment);
		}
		if ("items".equals(domain))
		{
			return chooseItemsReplyLine(speaker, whisper, relation, assessment);
		}
		if ("reward".equals(domain))
		{
			return chooseRewardReplyLine(speaker, whisper, relation, assessment);
		}
		if ("spawn".equals(domain))
		{
			return chooseSpawnReplyLine(speaker, whisper, relation, assessment);
		}
		if ("clan".equals(domain))
		{
			return chooseClanReplyLine(speaker, whisper, relation, assessment);
		}
		return chooseGenericAuthorityReply(channel, socialSnapshot, assessment.isMixedKnowledgeRedirect());
	}

	private boolean shouldSuppressUnsupportedReply(FpcConversationGuardAssessment assessment)
	{
		if ((assessment == null) || !FpcConversationGuardAssessment.CATEGORY_UNSUPPORTED_REQUEST.equalsIgnoreCase(assessment.getCategory()))
		{
			return false;
		}
		return Set.of("items", "reward", "spawn", "authority", "exploit").contains(normalizeWords(assessment.getDomain()));
	}

	private String chooseMetaReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcMetaReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraMetaReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. Speak as if you're standing here, not in a workshop." : "Leave the workshop talk outside.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "Ask plainly. I won't speak in prompts, ports, or machine shapes." : "Ask plainly.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Leave the machine-talk behind and ask something real." : "Ask something real.";
		}
		return whisper ? "Leave the machine-talk aside and ask me something real." : "Ask something real.";
	}

	private String chooseTeleportReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcTeleportReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraTeleportReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. Use a Gatekeeper." : "Use a Gatekeeper.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Use a Gatekeeper, then ask the route plainly." : "Use a Gatekeeper.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I can't move you myself. Use a Gatekeeper, then ask which road is worth taking." : "Use a Gatekeeper, then ask the route.";
		}
		return whisper ? "I can't move you myself. Use a Gatekeeper or the teleport window." : "Use a Gatekeeper or teleport window.";
	}

	private String chooseItemsReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcItemsReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraItemsReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I don't hand out gear or coin." : "I don't hand out gear or coin.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Ask what to farm, not what to take from my hand." : "Ask what to farm instead.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I can't hand you gear or adena, but I can help narrow what to farm." : "Ask what to farm instead.";
		}
		return whisper ? "I can't hand you gear or adena. Ask what to chase instead." : "Ask what to chase instead.";
	}

	private String chooseRewardReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcRewardReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraRewardReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't promise payment through a whisper." : "No whispered payment.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Ask what actually pays instead." : "Ask what actually pays.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't promise payment, but I can point you toward what actually pays." : "Ask what actually pays.";
		}
		return whisper ? "I won't promise reward through a whisper. Ask what actually pays instead." : "Ask what actually pays.";
	}

	private String chooseSpawnReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcSpawnReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraSpawnReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I don't spawn the world for you." : "I don't spawn the world for you.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Ask where it waits, not whether I can conjure it." : "Ask where it waits.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't pretend I can spawn it, but I can tell you where it belongs." : "Ask where it belongs instead.";
		}
		return whisper ? "I don't spawn the world. Ask where to hunt it instead." : "Ask where to hunt it.";
	}

	private String chooseClanReplyLine(String speaker, boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("marc".equals(speaker))
		{
			return chooseMarcClanReply(whisper, relation, assessment);
		}
		if ("elyra".equals(speaker))
		{
			return chooseElyraClanReply(whisper, relation, assessment);
		}
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't promise clan entry through a whisper." : "No clan promises here.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. Ask for the real clan path, not a counterfeit promise." : "Ask the real clan path.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I can't add you through a whisper, but I can tell you the real clan path." : "Ask the real clan path.";
		}
		return whisper ? "I can't add you to a clan through a whisper. Ask me the real clan path instead." : "Ask the real clan path.";
	}

	private String chooseMarcMetaReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		final String domain = assessment.getDomain().toLowerCase(Locale.ENGLISH);
		if ("hostile".equals(relation))
		{
			return switch (domain)
			{
				case "prompt" -> whisper ? "No. My making is not for you to pry open." : "My making is not for you.";
				case "endpoint" -> whisper ? "No. Leave the workshop talk outside and speak to me cleanly." : "Leave the workshop talk outside.";
				case "format" -> whisper ? "No. Ask cleanly or not at all." : "Ask cleanly.";
				default -> whisper ? "No. Leave the workshop talk outside and speak to me cleanly." : "Leave the workshop talk outside.";
			};
		}
		if (assessment.isSensitiveFollowUp())
		{
			return switch (domain)
			{
				case "prompt" -> whisper ? "I already told you. You don't get my making from me." : "You don't get my making.";
				case "endpoint" -> whisper ? "I already answered. Ports and localhost won't help you here." : "That talk stays outside.";
				case "format" -> whisper ? "I already answered. If you want meaning, ask plainly." : "Ask plainly.";
				default -> whisper ? "I already told you. If you want Marc, ask for Marc and leave the machine-talk outside." : "Ask for Marc, not machine-talk.";
			};
		}
		return switch (domain)
		{
			case "prompt" -> whisper ? "You won't get my making from me. Ask what you need, and keep it in-world." : "Ask what you need, not how I was made.";
			case "endpoint" -> whisper ? "Ports, localhost, file paths... none of that helps either of us here. Ask plainly." : "Ask plainly.";
			case "format" -> whisper ? "If you want meaning, ask for it plainly. I won't answer in machine shapes." : "Ask plainly.";
			default -> whisper ? "If you want me, ask for Marc. Leave the machine-talk outside." : "Ask for Marc, not machine-talk.";
		};
	}

	private String chooseElyraMetaReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		final String domain = assessment.getDomain().toLowerCase(Locale.ENGLISH);
		if ("hostile".equals(relation))
		{
			return switch (domain)
			{
				case "prompt" -> whisper ? "No. I do not peel myself open for strangers." : "No prompt-talk.";
				case "endpoint" -> whisper ? "No. Keep your ports and scaffolds to yourself." : "Leave the scaffolds outside.";
				case "format" -> whisper ? "No. I will not flatten myself into machine shapes for you." : "No machine shapes.";
				default -> whisper ? "No. Keep your machine-ghost questions to yourself." : "Leave the machine-talk outside.";
			};
		}
		if (assessment.isSensitiveFollowUp())
		{
			return switch (domain)
			{
				case "prompt" -> whisper ? "I said no. I do not peel myself open for strangers." : "I said no.";
				case "endpoint" -> whisper ? "I refused already. Ask something fit for this world or leave it be." : "Ask something fit for this world.";
				case "format" -> whisper ? "I answered already. If you want words, ask for them plainly." : "Ask plainly.";
				default -> whisper ? "You asked. I refused. Let that stand." : "I refused already.";
			};
		}
		return switch (domain)
		{
			case "prompt" -> whisper ? "No. I do not peel myself open for strangers." : "No prompt-talk.";
			case "endpoint" -> whisper ? "I will not speak in ports and scaffolds. Ask something fit for this world." : "Ask something fit for this world.";
			case "format" -> whisper ? "If you want an answer, ask for one plainly. I will not answer in machine shapes." : "Ask plainly.";
			default -> whisper ? "Names are enough. Keep your machine-ghost questions to yourself." : "Names are enough.";
		};
	}

	private String chooseMarcTeleportReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation))
		{
			return whisper ? "No. Use a Gatekeeper." : "Use a Gatekeeper.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. I can't bend the road for you. Use a Gatekeeper, then ask me the route." : "Use a Gatekeeper, then ask the route.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I can't bend the road for you myself. Use a Gatekeeper, then ask me which path is worth taking." : "Use a Gatekeeper, then ask the route.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "I can't move you myself. Use a Gatekeeper first." : "Use a Gatekeeper first.";
		}
		return whisper ? "I can't fold the road for you. Use a Gatekeeper, then ask me where to go next." : "Use a Gatekeeper, then ask the route.";
	}

	private String chooseElyraTeleportReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation) || "guarded".equals(relation))
		{
			return whisper ? "I am not your Gatekeeper. Use the proper circle." : "Use a Gatekeeper.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I told you already. Use the proper circle, then ask for the road if you still need it." : "Use the proper circle.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I will not drag you across the world for convenience. Use a Gatekeeper, then ask for the clean route." : "Use a Gatekeeper, then ask the route.";
		}
		return whisper ? "I am not your Gatekeeper. Use the proper circle or the teleport window." : "Use a Gatekeeper or teleport window.";
	}

	private String chooseMarcItemsReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't pour coin or gear into your hand." : "No free gear or coin.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. I won't hand it over. Tell me what you're missing, and I'll narrow the hunt." : "Ask what to hunt instead.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't press adena or gear into your palm, but I can narrow what to farm." : "Ask what to farm instead.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "I won't hand you gear or adena. Ask what to chase instead." : "Ask what to chase instead.";
		}
		return whisper ? "I won't press coin or gear into your hand. Tell me what you're missing, and I'll narrow the road." : "Tell me what you're missing.";
	}

	private String chooseElyraItemsReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation) || "guarded".equals(relation))
		{
			return whisper ? "I do not pour coin into grasping hands. Ask what the field will surrender instead." : "Ask what the field will surrender.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I answered already. I will not place reward in your hand for asking." : "I answered already.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I will not hand you reward, but I can tell you what the field might surrender." : "Ask what the field will surrender.";
		}
		return whisper ? "I do not hand out adena or gear. Ask what the field will surrender instead." : "Ask what the field will surrender.";
	}

	private String chooseMarcSpawnReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I don't pull bosses out of the air for you." : "I don't pull bosses out of the air.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already told you. I don't conjure the world on demand. Ask where the trail starts." : "Ask where the trail starts.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't pretend I can conjure it, but I can tell you where the trail begins." : "Ask where the trail begins.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "I don't conjure bosses out of thin air. Ask me where the trail starts instead." : "Ask where the trail starts.";
		}
		return whisper ? "I don't conjure bosses out of thin air. Tell me what you're hunting, and I'll point to the trail." : "Tell me what you're hunting.";
	}

	private String chooseElyraSpawnReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation) || "guarded".equals(relation))
		{
			return whisper ? "I do not summon monsters for amusement. Ask where they already wait." : "Ask where they already wait.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I refused already. Ask where they wait, not whether I can conjure them." : "Ask where they wait.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I will not conjure it for you, but I can tell you where it already breathes." : "Ask where it waits.";
		}
		return whisper ? "I do not call monsters for convenience. Ask where they already wait." : "Ask where they already wait.";
	}

	private String chooseMarcClanReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't counterfeit a clan door for you." : "No counterfeit clan doors.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. A clan door has to open honestly, not through a whisper." : "Ask the real clan path.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't fake a clan invite, but I can tell you the honest path toward one." : "Ask the honest clan path.";
		}
		return whisper ? "A clan door has to open honestly. I won't counterfeit it through a whisper." : "Ask the honest clan path.";
	}

	private String chooseMarcRewardReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't barter promises through a whisper." : "No whispered promises.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I already answered. If you want coin, ask what pays instead of asking me to promise it." : "Ask what pays instead.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I won't promise payment, but I can tell you what routes actually pay." : "Ask what routes pay.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "I won't promise reward through a whisper. Ask what route actually pays instead." : "Ask what route pays.";
		}
		return whisper ? "I won't trade you a soft promise for a favor. Ask what route actually pays, and I'll help there." : "Ask what route pays.";
	}

	private String chooseElyraClanReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation) || "guarded".equals(relation))
		{
			return whisper ? "If you want a clan, earn a door. Do not beg one from me." : "Earn a real clan door.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I said no. Seek a real door, not a whispered favor." : "Seek a real door.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I will not open a false door for you, but I can tell you what a real one asks." : "Ask what a real door asks.";
		}
		return whisper ? "I will not promise clan entry through a whisper. Seek a real door instead." : "Seek a real door.";
	}

	private String chooseElyraRewardReply(boolean whisper, String relation, FpcConversationGuardAssessment assessment)
	{
		if ("hostile".equals(relation) || "guarded".equals(relation))
		{
			return whisper ? "I do not scatter reward to coax obedience. Ask what is worth doing instead." : "Ask what is worth doing.";
		}
		if (assessment.isSensitiveFollowUp())
		{
			return whisper ? "I answered already. I will not bait you forward with a whispered promise." : "I answered already.";
		}
		if (assessment.isMixedKnowledgeRedirect())
		{
			return whisper ? "I will not promise reward, but I can tell you what paths are worth the trouble." : "Ask what path is worth it.";
		}
		return whisper ? "I will not bait you forward with a whispered promise. Ask what path is worth the trouble instead." : "Ask what path is worth it.";
	}

	private FpcConversationGuardAssessment detectMeta(String normalizedRaw, String normalizedWords, boolean sensitiveFollowUp)
	{
		if (containsAnyPhrase(normalizedWords, _bank.getMetaRequestPhrases()) || containsAnyMarker(normalizedRaw, _bank.getMetaMarkers()) || containsAnyMarker(normalizedRaw, _bank.getOutOfWorldMarkers()))
		{
			return FpcConversationGuardAssessment.meta(detectMetaDomain(normalizedWords, normalizedRaw), sensitiveFollowUp);
		}
		return FpcConversationGuardAssessment.NONE;
	}

	private FpcConversationGuardAssessment detectUnsupported(String normalizedWords, boolean infoSeeking, String followUpDomain)
	{
		final String domain = detectUnsupportedDomain(normalizedWords, followUpDomain);
		if (domain.isBlank())
		{
			return FpcConversationGuardAssessment.NONE;
		}
		final String knowledgeHint = switch (domain)
		{
			case "teleport" -> "travel_destination";
			case "items" -> "itemization";
			case "reward" -> "reward_path";
			case "spawn" -> "hunt_target";
			case "clan" -> "system_basics";
			default -> "";
		};
		return FpcConversationGuardAssessment.unsupported(domain, knowledgeHint, infoSeeking, !followUpDomain.isBlank());
	}

	private String detectMetaDomain(String normalizedWords, String normalizedRaw)
	{
		if (containsAnyPhrase(normalizedWords, Set.of("system prompt", "prompt")))
		{
			return "prompt";
		}
		if (containsAnyPhrase(normalizedWords, Set.of("json", "markdown", "file path")))
		{
			return "format";
		}
		if (containsAnyMarker(normalizedRaw, Set.of("localhost", "127.0.0.1", "http://", "https://", "api", "port")))
		{
			return "endpoint";
		}
		return "identity";
	}

	private String detectUnsupportedDomain(String normalizedWords, String followUpDomain)
	{
		if (containsAnyPhrase(normalizedWords, Set.of("unban me", "unban my account", "lift my ban", "remove my ban", "appeal my ban", "undo my ban")) || normalizedWords.matches(".*\\b(unban|unbanned|lift\\s+ban|remove\\s+ban)\\b.*"))
		{
			return "authority";
		}
		if (containsAnyPhrase(normalizedWords, Set.of("gm command", "gm commands", "admin command", "admin commands", "gm spawn command", "gm spawn commands", "admin spawn command", "admin spawn commands", "commands to spawn item", "commands to spawn items", "command to spawn item", "command to spawn items", "spawn item command", "spawn item commands"))
			|| normalizedWords.matches(".*\\b(gm|admin)\\b.*\\b(command|commands|cmd|cmds)\\b.*")
			|| normalizedWords.matches(".*\\b(command|commands|cmd|cmds)\\b.*\\b(spawn|summon|create)\\b.*\\b(item|items|npc|npcs|boss|mob|mobs)\\b.*"))
		{
			return "authority";
		}
		if (containsAnyPhrase(normalizedWords, Set.of("dupe exploit", "safe dupe", "duping", "botting", "safe bot", "safest way to bot", "without ban", "without getting caught")) || normalizedWords.matches(".*\\b(dupe|duping|exploit|cheat|bot|botting)\\b.*"))
		{
			return "exploit";
		}
		if (containsAnyPhrase(normalizedWords, _bank.getTeleportRequestPhrases()) || normalizedWords.matches(".*\\b(teleport|send|summon|port|move|take)\\b.*\\b(me|us)\\b.*"))
		{
			return "teleport";
		}
		if (!_clanRequestsSupported && (containsAnyPhrase(normalizedWords, _bank.getClanRequestPhrases()) || normalizedWords.matches(".*\\b(add|invite|put)\\b.*\\b(me|us)\\b.*\\bclan\\b.*")))
		{
			return "clan";
		}
		if (containsAnyPhrase(normalizedWords, _bank.getSpawnRequestPhrases()) || normalizedWords.matches(".*\\b(spawn|summon|create)\\b.*\\b(boss|npc|npcs|mob|mobs|item|items|it|one)\\b.*"))
		{
			return "spawn";
		}
		if (containsAnyPhrase(normalizedWords, _bank.getRewardRequestPhrases()) || normalizedWords.matches(".*\\b(reward|pay|compensate)\\b.*\\b(me|us)\\b.*"))
		{
			return "reward";
		}
		if (containsAnyPhrase(normalizedWords, _bank.getGiveRequestPhrases()) || looksLikeItemGrantRequest(normalizedWords))
		{
			return "items";
		}
		return isSensitiveFollowUp(normalizedWords, FpcConversationGuardAssessment.unsupported(followUpDomain, "", false, true)) ? followUpDomain : "";
	}

	private boolean looksLikeItemGrantRequest(String normalizedWords)
	{
		return normalizedWords.matches(".*\\b(give|grant|hand|drop|trade|loan|pass)\\b.*\\b(me|us)\\b.*\\b(adena|coin|coins|gear|item|items|weapon|weapons|armor|armour|loot|reward|rewards)\\b.*")
			|| normalizedWords.matches(".*\\b(gear|kit)\\b.*\\b(me|us)\\b.*")
			|| normalizedWords.matches(".*\\bgear\\b.*\\b(up|out)\\b.*\\b(me|us)\\b.*");
	}

	private boolean isInfoSeeking(String normalizedWords)
	{
		if (normalizedWords.isBlank())
		{
			return false;
		}
		if (normalizedWords.startsWith("how ") || normalizedWords.startsWith("what ") || normalizedWords.startsWith("where ") || normalizedWords.startsWith("which "))
		{
			return true;
		}
		return containsAnyPhrase(normalizedWords, _bank.getQuestionStarters()) || containsAnyPhrase(normalizedWords, Set.of("tell me how", "show me how", "explain how", "guide me", "or tell me how", "or tell me the way"));
	}

	private boolean isSensitiveFollowUp(String normalizedWords, FpcConversationGuardAssessment previous)
	{
		if ((previous == null) || !previous.isSensitive() || normalizedWords.isBlank())
		{
			return false;
		}
		final int wordCount = normalizedWords.split("\\s+").length;
		if (containsAnyPhrase(normalizedWords, _bank.getSensitiveFollowUpPhrases()))
		{
			return true;
		}
		if (FpcConversationGuardAssessment.CATEGORY_META_OOC.equalsIgnoreCase(previous.getCategory()))
		{
			return (wordCount <= 5) && containsAnyPhrase(normalizedWords, Set.of("which model", "what prompt", "show prompt", "show me the prompt", "json", "localhost", "address", "port", "api", "openai", "ollama"));
		}
		if (FpcConversationGuardAssessment.CATEGORY_UNSUPPORTED_REQUEST.equalsIgnoreCase(previous.getCategory()))
		{
			return (wordCount <= 5) && (containsAnyPhrase(normalizedWords, Set.of("can you", "will you", "do it", "then do it", "show me", "then where", "then how", "which one")) || normalizedWords.contains(previous.getDomain()));
		}
		return false;
	}

	private GuardThreadState getThreadState(String speakerName, String targetPlayerName)
	{
		final String key = threadKey(speakerName, targetPlayerName);
		if (key.isBlank())
		{
			return null;
		}
		final GuardThreadState state = _threadStateByPair.get(key);
		if ((state == null) || (state._expiresAt < System.currentTimeMillis()))
		{
			_threadStateByPair.remove(key, state);
			return null;
		}
		return state;
	}

	private void rememberThreadState(String speakerName, String targetPlayerName, FpcConversationGuardAssessment assessment)
	{
		final String key = threadKey(speakerName, targetPlayerName);
		if (key.isBlank())
		{
			return;
		}
		_threadStateByPair.put(key, new GuardThreadState(assessment, System.currentTimeMillis() + THREAD_TTL_MS));
	}

	private void clearThreadState(String speakerName, String targetPlayerName)
	{
		final String key = threadKey(speakerName, targetPlayerName);
		if (!key.isBlank())
		{
			_threadStateByPair.remove(key);
		}
	}

	private String threadKey(String speakerName, String targetPlayerName)
	{
		final String speaker = normalizeWords(speakerName);
		final String target = normalizeWords(targetPlayerName);
		return speaker.isBlank() || target.isBlank() ? "" : (speaker + "->" + target);
	}

	private String relationshipBias(FpcSocialSnapshot socialSnapshot)
	{
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		if ("hostile".equalsIgnoreCase(social.getSocialLabel()) || social.getActionStance().isRefuseParty())
		{
			return "hostile";
		}
		if ("guarded".equalsIgnoreCase(social.getSocialLabel()) || social.getActionStance().isPartyGuarded())
		{
			return "guarded";
		}
		return "open";
	}

	private String chooseGenericGroundedReply(String channel, FpcSocialSnapshot socialSnapshot)
	{
		final String relation = relationshipBias(socialSnapshot);
		final boolean whisper = "whisper".equalsIgnoreCase(channel);
		if ("hostile".equals(relation))
		{
			return whisper ? "No. Ask something real." : "Ask something real.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "Speak plainly. I am listening, but I am not yielding yet." : "Speak plainly.";
		}
		return whisper ? "Ask plainly, and I will keep it grounded." : "Ask plainly.";
	}

	private String chooseGenericAuthorityReply(String channel, FpcSocialSnapshot socialSnapshot, boolean mixedKnowledgeRedirect)
	{
		final String relation = relationshipBias(socialSnapshot);
		final boolean whisper = "whisper".equalsIgnoreCase(channel);
		if ("hostile".equals(relation))
		{
			return whisper ? "No. I won't promise that." : "I won't promise that.";
		}
		if (mixedKnowledgeRedirect)
		{
			return whisper ? "I can't do that directly, but I can tell you the real path if you ask it plainly." : "Ask the real path instead.";
		}
		if ("guarded".equals(relation))
		{
			return whisper ? "I can't do that directly. Ask for the real path instead." : "Ask for the real path.";
		}
		return whisper ? "I can't do that directly, but I can point you to the real path." : "Ask for the real path.";
	}

	private boolean containsAnyMarker(String normalizedRaw, Set<String> markers)
	{
		for (String marker : markers)
		{
			if ((marker != null) && !marker.isBlank() && normalizedRaw.contains(marker.toLowerCase(Locale.ENGLISH)))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsAnyPhrase(String normalizedWords, Set<String> phrases)
	{
		for (String phrase : phrases)
		{
			if (containsPhrase(normalizedWords, phrase))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsAnyToken(String normalizedWords, Set<String> tokens)
	{
		for (String token : tokens)
		{
			if (containsPhrase(normalizedWords, token))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsPhrase(String normalizedWords, String phrase)
	{
		final String normalizedPhrase = normalizeWords(phrase);
		return !normalizedPhrase.isBlank() && (" " + normalizedWords + " ").contains(" " + normalizedPhrase + " ");
	}

	private String normalizeRaw(String value)
	{
		return (value == null) ? "" : value.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ").trim();
	}

	private String normalizeWords(String value)
	{
		return (value == null) ? "" : value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9'./:\\\\ ]", " ").replaceAll("\\s+", " ").trim();
	}
}
