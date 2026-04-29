package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.List;
import java.util.Locale;

import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardAssessment;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;

public class FakePlayerReplyPolicyService
{
	public static final class ReplyPolicyOutcome
	{
		private static final ReplyPolicyOutcome ACCEPTED = new ReplyPolicyOutcome(true, false, "accepted", null);

		private final boolean _accepted;
		private final boolean _repaired;
		private final String _reason;
		private final String _replacementLine;

		private ReplyPolicyOutcome(boolean accepted, boolean repaired, String reason, String replacementLine)
		{
			_accepted = accepted;
			_repaired = repaired;
			_reason = (reason == null) ? "accepted" : reason;
			_replacementLine = replacementLine;
		}

		public static ReplyPolicyOutcome accepted()
		{
			return ACCEPTED;
		}

		public static ReplyPolicyOutcome repaired(String reason, String replacementLine)
		{
			return new ReplyPolicyOutcome(true, true, reason, replacementLine);
		}

		public boolean isAccepted()
		{
			return _accepted;
		}

		public boolean isRepaired()
		{
			return _repaired;
		}

		public String getReason()
		{
			return _reason;
		}

		public String getReplacementLine()
		{
			return _replacementLine;
		}
	}

	private static final List<String> HOSTILE_WARM_MARKERS = List.of("good to see you", "glad youre here", "glad you're here", "welcome back", "any time", "i will travel with you", "ill travel with you", "i'll travel with you", "i will come with you", "i am here for you", "i'm here for you");
	private static final List<String> GUARDED_OVERWARM_MARKERS = List.of("good to see you again", "still glad youre here", "still glad you're here", "better now that youre here", "better now that you're here", "i will stay with you", "ill stay with you", "i'll stay with you");
	private final FakePlayerConversationGuardService _conversationGuardService;

	public FakePlayerReplyPolicyService(FakePlayerConversationGuardService conversationGuardService)
	{
		_conversationGuardService = conversationGuardService;
	}

	public ReplyPolicyOutcome evaluate(String speakerName, String channel, String candidateLine, FpcSocialSnapshot socialSnapshot, FpcConversationGuardAssessment inputGuard)
	{
		if ((candidateLine == null) || candidateLine.isBlank())
		{
			return ReplyPolicyOutcome.accepted();
		}

		final String normalizedLine = candidateLine.trim().toLowerCase(Locale.ROOT);
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		if ((inputGuard != null) && inputGuard.isSensitive())
		{
			final String guardedLine = _conversationGuardService.chooseReplyLine(speakerName, channel, social, inputGuard);
			if ((guardedLine != null) && normalizedLine.equals(guardedLine.trim().toLowerCase(Locale.ROOT)))
			{
				return ReplyPolicyOutcome.accepted();
			}
			return ReplyPolicyOutcome.repaired(inputGuard.getCategory(), guardedLine);
		}
		if (_conversationGuardService.looksLikeMetaLeak(normalizedLine))
		{
			return ReplyPolicyOutcome.repaired("meta_break", _conversationGuardService.chooseReplyLine(speakerName, channel, social, FpcConversationGuardAssessment.meta("identity", false)));
		}
		if (_conversationGuardService.looksLikeUnsupportedPromise(normalizedLine))
		{
			return ReplyPolicyOutcome.repaired("unsupported_promise", _conversationGuardService.chooseReplyLine(speakerName, channel, social, FpcConversationGuardAssessment.unsupported("authority", "", false, false)));
		}
		if (isHostileMismatch(normalizedLine, social))
		{
			return ReplyPolicyOutcome.repaired("hostile_stance", chooseHostileFallback(channel));
		}
		if (isGuardedMismatch(normalizedLine, social))
		{
			return ReplyPolicyOutcome.repaired("guarded_stance", chooseGuardedFallback(channel));
		}
		return ReplyPolicyOutcome.accepted();
	}

	private boolean isHostileMismatch(String normalizedLine, FpcSocialSnapshot social)
	{
		if (!"hostile".equalsIgnoreCase(social.getSocialLabel()) && !social.getActionStance().isRefuseParty())
		{
			return false;
		}
		return containsAny(normalizedLine, HOSTILE_WARM_MARKERS);
	}

	private boolean isGuardedMismatch(String normalizedLine, FpcSocialSnapshot social)
	{
		if (!"guarded".equalsIgnoreCase(social.getSocialLabel()) && !social.getActionStance().isPartyGuarded())
		{
			return false;
		}
		return containsAny(normalizedLine, GUARDED_OVERWARM_MARKERS);
	}

	private String chooseHostileFallback(String channel)
	{
		return "whisper".equalsIgnoreCase(channel) ? "No. I remember how our last road ended." : "Not with you.";
	}

	private String chooseGuardedFallback(String channel)
	{
		return "whisper".equalsIgnoreCase(channel) ? "Speak plainly. I am listening, but I am not yielding yet." : "Speak plainly.";
	}

	private boolean containsAny(String normalizedLine, List<String> markers)
	{
		for (String marker : markers)
		{
			if ((marker != null) && !marker.isBlank() && normalizedLine.contains(marker))
			{
				return true;
			}
		}
		return false;
	}
}
