package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Locale;

import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialActionStance;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.model.actor.Player;

public class FakePlayerSocialService
{
	public static final class ReplySocialCue
	{
		private final String _socialLabel;
		private final String _summary;
		private final int _trustBias;
		private final int _guardBias;
		private final FpcSocialActionStance _actionStance;
		private final boolean _liveRiskPressure;
		private final String _liveRiskSummary;

		private ReplySocialCue(String socialLabel, String summary, int trustBias, int guardBias, FpcSocialActionStance actionStance, boolean liveRiskPressure, String liveRiskSummary)
		{
			_socialLabel = (socialLabel == null) ? "neutral" : socialLabel;
			_summary = (summary == null) ? "" : summary;
			_trustBias = trustBias;
			_guardBias = guardBias;
			_actionStance = (actionStance == null) ? FpcSocialActionStance.EMPTY : actionStance;
			_liveRiskPressure = liveRiskPressure;
			_liveRiskSummary = (liveRiskSummary == null) ? "" : liveRiskSummary;
		}

		public String getSocialLabel()
		{
			return _socialLabel;
		}

		public String getSummary()
		{
			return _summary;
		}

		public int getTrustBias()
		{
			return _trustBias;
		}

		public int getGuardBias()
		{
			return _guardBias;
		}

		public FpcSocialActionStance getActionStance()
		{
			return _actionStance;
		}

		public boolean hasLiveRiskPressure()
		{
			return _liveRiskPressure;
		}

		public String getLiveRiskSummary()
		{
			return _liveRiskSummary;
		}
	}

	public static final class PartyInviteDecision
	{
		private final boolean _accepted;
		private final String _reason;
		private final String _stance;
		private final int _score;

		private PartyInviteDecision(boolean accepted, String reason, String stance, int score)
		{
			_accepted = accepted;
			_reason = reason;
			_stance = stance;
			_score = score;
		}

		public boolean isAccepted()
		{
			return _accepted;
		}

		public String getReason()
		{
			return _reason;
		}

		public String getStance()
		{
			return _stance;
		}

		public int getScore()
		{
			return _score;
		}
	}

	public PartyInviteDecision evaluatePartyInvite(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship)
	{
		return evaluatePartyInvite(definition, sender, relationship, FpcSocialSnapshot.EMPTY, false);
	}

	public ReplySocialCue evaluateReplyCue(Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialMemory)
	{
		final FpcRelationshipSnapshot snapshot = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
		final FpcSocialSnapshot socialSnapshot = (socialMemory == null) ? FpcSocialSnapshot.EMPTY : socialMemory;
		final String liveRiskSummary = buildLiveRiskSummary(sender);
		final boolean liveRiskPressure = !liveRiskSummary.isBlank();
		final boolean positiveHistory = hasPositiveReplyHistory(snapshot, socialSnapshot);
		final boolean personalKillHistory = hasPersonalKillHistory(snapshot, socialSnapshot);
		final boolean activeCombatPressure = (sender != null) && (sender.getPvpFlag() > 0);
		final boolean severeLiveRisk = hasSevereLiveRisk(sender);
		final boolean pairSpecificGuardSignals = hasPairSpecificGuardSignals(snapshot, socialSnapshot);

		String effectiveLabel = socialSnapshot.getSocialLabel();
		int effectiveTrustBias = socialSnapshot.getTrustBias();
		int effectiveGuardBias = socialSnapshot.getGuardBias();
		FpcSocialActionStance effectiveActionStance = socialSnapshot.getActionStance();
		if (personalKillHistory)
		{
			effectiveLabel = "hostile";
			effectiveGuardBias = Math.max(effectiveGuardBias, 4);
			effectiveTrustBias = Math.min(effectiveTrustBias, 0);
			effectiveActionStance = new FpcSocialActionStance(false, false, true, true, false, true);
		}
		else if (liveRiskPressure)
		{
			final int liveRiskPenalty = calculateLiveRiskGuardPenalty(sender);
			if (activeCombatPressure && (!positiveHistory || pairSpecificGuardSignals))
			{
				effectiveLabel = "guarded".equalsIgnoreCase(effectiveLabel) || "hostile".equalsIgnoreCase(effectiveLabel) ? effectiveLabel : "guarded";
				effectiveTrustBias = Math.max(0, effectiveTrustBias - 1);
				effectiveGuardBias += Math.max(2, liveRiskPenalty);
				effectiveActionStance = buildLiveRiskActionStance(sender, socialSnapshot.getActionStance());
			}
			else if (activeCombatPressure)
			{
				if (normalize(effectiveLabel).isBlank() || "neutral".equalsIgnoreCase(effectiveLabel) || "guarded".equalsIgnoreCase(effectiveLabel))
				{
					effectiveLabel = choosePositiveReplyLabel(snapshot, socialSnapshot);
				}
				effectiveTrustBias = Math.max(1, effectiveTrustBias);
				effectiveGuardBias += 1;
				effectiveActionStance = buildAlertCombatActionStance(sender, socialSnapshot.getActionStance());
			}
			else if (!positiveHistory)
			{
				effectiveLabel = "guarded".equalsIgnoreCase(effectiveLabel) || "hostile".equalsIgnoreCase(effectiveLabel) ? effectiveLabel : "guarded";
				effectiveTrustBias = Math.max(0, effectiveTrustBias - 1);
				effectiveGuardBias += Math.max(1, liveRiskPenalty);
				effectiveActionStance = severeLiveRisk ? buildLiveRiskActionStance(sender, socialSnapshot.getActionStance()) : buildSoftenedLiveRiskActionStance(sender, socialSnapshot.getActionStance());
			}
			else
			{
				if (normalize(effectiveLabel).isBlank() || "neutral".equalsIgnoreCase(effectiveLabel) || "guarded".equalsIgnoreCase(effectiveLabel))
				{
					effectiveLabel = choosePositiveReplyLabel(snapshot, socialSnapshot);
				}
				effectiveTrustBias = Math.max(1, effectiveTrustBias);
				effectiveGuardBias += severeLiveRisk ? 1 : 0;
				effectiveActionStance = buildSoftenedLiveRiskActionStance(sender, socialSnapshot.getActionStance());
			}
		}

		final String summary = appendLiveRiskGuidance(socialSnapshot.getSummary(), liveRiskSummary, effectiveLabel);
		return new ReplySocialCue(effectiveLabel, summary, effectiveTrustBias, effectiveGuardBias, effectiveActionStance, liveRiskPressure, liveRiskSummary);
	}

	public PartyInviteDecision evaluatePartyInvite(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialMemory)
	{
		return evaluatePartyInvite(definition, sender, relationship, socialMemory, false);
	}

	public PartyInviteDecision evaluatePartyInvite(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialMemory, boolean rapidReinvitePressure)
	{
		if ((definition == null) || (sender == null))
		{
			return new PartyInviteDecision(false, "invalid", "hostile", -99);
		}

		final FpcRelationshipSnapshot snapshot = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
		final FpcSocialSnapshot socialSnapshot = (socialMemory == null) ? FpcSocialSnapshot.EMPTY : socialMemory;
		if ((snapshot.getPlayerKillCount() > 0) || socialSnapshot.hasHistoricalKillHistory())
		{
			return new PartyInviteDecision(false, "personal_kill_history", "hostile", -20);
		}
		if (socialSnapshot.getActionStance().isRefuseParty())
		{
			return new PartyInviteDecision(false, "social_refusal", "hostile", -12);
		}

		int score = 4;
		score += snapshot.getFamiliarity();
		score += snapshot.getTrust();
		score += snapshot.getRespect();
		score -= (snapshot.getTension() * 2);
		score -= (snapshot.getResentment() * 2);
		score += socialSnapshot.getTrustBias();
		score -= socialSnapshot.getGuardBias();
		
		String reason = "steady";
		boolean liveRiskPressure = false;
		if (sender.getReputation() < 0)
		{
			score -= 1;
			reason = "chaotic_reputation";
			liveRiskPressure = true;
		}
		if (sender.getPkKills() >= 25)
		{
			score -= 3;
			reason = "violent_history";
			liveRiskPressure = true;
		}
		else if (sender.getPkKills() >= 10)
		{
			score -= 2;
			if ("steady".equals(reason))
			{
				reason = "blooded_history";
			}
			liveRiskPressure = true;
		}
		else if (sender.getPkKills() > 0)
		{
			score -= 1;
			if ("steady".equals(reason))
			{
				reason = "blooded_history";
			}
			liveRiskPressure = true;
		}
		if (sender.getPvpFlag() > 0)
		{
			score -= 2;
			if ("steady".equals(reason))
			{
				reason = "combat_active";
			}
			liveRiskPressure = true;
		}
		if (snapshot.getTension() >= 6)
		{
			score -= 4;
			reason = "high_tension";
		}
		if (snapshot.getResentment() >= 6)
		{
			return new PartyInviteDecision(false, "personal_resentment", "hostile", score);
		}
		if (socialSnapshot.getActionStance().isRefuseLead())
		{
			score -= 2;
			if ("steady".equals(reason))
			{
				reason = "refuse_lead";
			}
		}
		if (rapidReinvitePressure)
		{
			score = Math.min(score - 2, 5);
			return new PartyInviteDecision(false, "recent_reinvite", "guarded", score);
		}

		if (score < 2)
		{
			return new PartyInviteDecision(false, reason, "hostile", score);
		}
		if (liveRiskPressure)
		{
			return new PartyInviteDecision(true, reason, "guarded", score);
		}
		if (socialSnapshot.getActionStance().isPartyGuarded() || "guarded".equalsIgnoreCase(socialSnapshot.getSocialLabel()) || "familiar".equalsIgnoreCase(socialSnapshot.getSocialLabel()))
		{
			return new PartyInviteDecision(true, reason, "guarded", score);
		}
		if (score < 8)
		{
			return new PartyInviteDecision(true, reason, "guarded", score);
		}
		return new PartyInviteDecision(true, reason, "willing", score);
	}

	public String choosePartyAcceptanceLine(FpcDefinition definition, Player sender, PartyInviteDecision decision)
	{
		final String name = normalize(definition != null ? definition.getName() : "");
		final String stance = (decision == null) ? "willing" : normalize(decision.getStance());
		if ("pippa".equals(name))
		{
			return "guarded".equals(stance) ? "I will come, but keep it clean and clear." : "I will keep up. Point me at the trouble.";
		}
		if ("caelan".equals(name))
		{
			return "guarded".equals(stance) ? "Understood. I will follow, but I expect discipline." : "Understood. I will keep formation with you.";
		}
		return "guarded".equals(stance) ? "I will travel with you, but I am watching how you handle yourself." : "I will travel with you for a while.";
	}

	public String choosePartyDeclineLine(FpcDefinition definition, Player sender, PartyInviteDecision decision)
	{
		final String name = normalize(definition != null ? definition.getName() : "");
		final String reason = (decision == null) ? "steady" : normalize(decision.getReason());
		if ("personal_kill_history".equals(reason))
		{
			if ("pippa".equals(name))
			{
				return "No. You killed me once. I will not walk under your lead.";
			}
			if ("caelan".equals(name))
			{
				return "No. You already proved what your company costs me.";
			}
			return "No. I remember dying by your hand.";
		}
		if ("chaotic_reputation".equals(reason) || "violent_history".equals(reason))
		{
			if ("pippa".equals(name))
			{
				return "I will not follow that kind of bloodstained road.";
			}
			if ("caelan".equals(name))
			{
				return "No. Your trail is too violent for my company.";
			}
			return "No. I do not travel with someone carrying that kind of blood behind them.";
		}
		if ("personal_resentment".equals(reason))
		{
			return "Not with you. That wound between us is still too fresh.";
		}
		if ("social_refusal".equals(reason))
		{
			return "Not with you. I still remember how that last road ended.";
		}
		if ("recent_reinvite".equals(reason))
		{
			if ("pippa".equals(name))
			{
				return "You just broke step. Give me a moment before you ask again.";
			}
			if ("caelan".equals(name))
			{
				return "Not again so soon. Keep your formation straight first.";
			}
			return "Not again so soon. Let the road settle before you ask.";
		}
		if ("refuse_lead".equals(reason))
		{
			return "Not yet. I do not trust your lead enough for that.";
		}
		if ("high_tension".equals(reason))
		{
			return "Not now. There is too much strain between us for clean work.";
		}
		if ("blooded_history".equals(reason) || "combat_active".equals(reason))
		{
			return "Not yet. Show me steadier judgment first.";
		}
		return "Not now. Earn a little more trust with me first.";
	}

	private String normalize(String value)
	{
		return (value == null) ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}

	private int calculateLiveRiskGuardPenalty(Player sender)
	{
		if (sender == null)
		{
			return 0;
		}

		int penalty = 0;
		if (sender.getReputation() < 0)
		{
			penalty += 1;
		}
		if (sender.getPkKills() >= 25)
		{
			penalty += 2;
		}
		else if (sender.getPkKills() > 0)
		{
			penalty += 1;
		}
		if (sender.getPvpFlag() > 0)
		{
			penalty += 2;
		}
		return penalty;
	}

	private FpcSocialActionStance buildLiveRiskActionStance(Player sender, FpcSocialActionStance baseActionStance)
	{
		final FpcSocialActionStance base = (baseActionStance == null) ? FpcSocialActionStance.EMPTY : baseActionStance;
		final boolean chaoticReputation = (sender != null) && (sender.getReputation() < 0);
		final boolean bloodedHistory = (sender != null) && (sender.getPkKills() > 0);
		final boolean activeCombat = (sender != null) && (sender.getPvpFlag() > 0);
		return new FpcSocialActionStance(false, base.isPartyGuarded() || chaoticReputation || bloodedHistory || activeCombat, base.isRefuseParty(), base.isRefuseLead() || chaoticReputation || activeCombat, base.isRumorShareOk() && !chaoticReputation, base.isAvoidPlayer());
	}

	private FpcSocialActionStance buildSoftenedLiveRiskActionStance(Player sender, FpcSocialActionStance baseActionStance)
	{
		final FpcSocialActionStance base = (baseActionStance == null) ? FpcSocialActionStance.EMPTY : baseActionStance;
		final boolean chaoticReputation = (sender != null) && (sender.getReputation() < 0);
		return new FpcSocialActionStance(base.isPartyOk(), base.isPartyGuarded(), base.isRefuseParty(), base.isRefuseLead(), base.isRumorShareOk() && !chaoticReputation, base.isAvoidPlayer());
	}

	private FpcSocialActionStance buildAlertCombatActionStance(Player sender, FpcSocialActionStance baseActionStance)
	{
		final FpcSocialActionStance base = (baseActionStance == null) ? FpcSocialActionStance.EMPTY : baseActionStance;
		final boolean severeBloodedHistory = (sender != null) && (sender.getPkKills() >= 10);
		final boolean chaoticReputation = (sender != null) && (sender.getReputation() < 0);
		return new FpcSocialActionStance(base.isPartyOk() && !severeBloodedHistory, base.isPartyGuarded() || severeBloodedHistory, base.isRefuseParty(), base.isRefuseLead() || severeBloodedHistory, base.isRumorShareOk() && !chaoticReputation, base.isAvoidPlayer());
	}

	private boolean hasPositiveReplyHistory(FpcRelationshipSnapshot snapshot, FpcSocialSnapshot socialSnapshot)
	{
		final FpcRelationshipSnapshot relationship = (snapshot == null) ? FpcRelationshipSnapshot.EMPTY : snapshot;
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		final int warmthScore = relationship.getFamiliarity() + relationship.getTrust() + relationship.getRespect();
		if (warmthScore >= 4)
		{
			return true;
		}
		if (social.getTrustBias() >= 2)
		{
			return true;
		}
		return isPositiveSocialLabel(social.getSocialLabel());
	}

	private boolean hasPairSpecificGuardSignals(FpcRelationshipSnapshot snapshot, FpcSocialSnapshot socialSnapshot)
	{
		final FpcRelationshipSnapshot relationship = (snapshot == null) ? FpcRelationshipSnapshot.EMPTY : snapshot;
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		if ((relationship.getTension() >= 4) || (relationship.getResentment() >= 3))
		{
			return true;
		}
		if ("guarded".equalsIgnoreCase(social.getSocialLabel()) || "hostile".equalsIgnoreCase(social.getSocialLabel()))
		{
			return true;
		}
		return social.getActionStance().isAvoidPlayer() || social.getActionStance().isRefuseLead() || social.getActionStance().isRefuseParty() || social.getActionStance().isPartyGuarded();
	}

	private boolean isPositiveSocialLabel(String socialLabel)
	{
		final String label = normalize(socialLabel);
		return "familiar".equals(label) || "friendly".equals(label) || "trusted".equals(label) || "warm".equals(label) || "open".equals(label);
	}

	private String appendLiveRiskGuidance(String baseSummary, String liveRiskSummary, String effectiveLabel)
	{
		final String base = (baseSummary == null) ? "" : baseSummary.trim();
		final String risk = (liveRiskSummary == null) ? "" : liveRiskSummary.trim();
		if (risk.isBlank())
		{
			return base;
		}

		final StringBuilder sb = new StringBuilder(base);
		if (sb.length() > 0)
		{
			sb.append(' ');
		}
		sb.append("Current live risk: ").append(risk).append(". ");
		if ("hostile".equalsIgnoreCase(effectiveLabel))
		{
			sb.append("Effective reply stance should stay hard and distant.");
		}
		else if ("guarded".equalsIgnoreCase(effectiveLabel))
		{
			sb.append("Effective reply stance should stay careful and guarded.");
		}
		else if (isPositiveSocialLabel(effectiveLabel))
		{
			sb.append("Effective reply stance should acknowledge the risk without erasing existing trust.");
		}
		else
		{
			sb.append("Effective reply stance should acknowledge the risk without sounding naive.");
		}
		return sb.toString().trim();
	}

	private boolean hasPersonalKillHistory(FpcRelationshipSnapshot snapshot, FpcSocialSnapshot socialSnapshot)
	{
		return ((snapshot != null) && (snapshot.getPlayerKillCount() > 0)) || ((socialSnapshot != null) && socialSnapshot.hasHistoricalKillHistory());
	}

	private boolean hasSevereLiveRisk(Player sender)
	{
		return (sender != null) && ((sender.getPvpFlag() > 0) || (sender.getPkKills() >= 10) || (sender.getReputation() <= -5000));
	}

	private String choosePositiveReplyLabel(FpcRelationshipSnapshot snapshot, FpcSocialSnapshot socialSnapshot)
	{
		final FpcRelationshipSnapshot relationship = (snapshot == null) ? FpcRelationshipSnapshot.EMPTY : snapshot;
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		final int warmthScore = relationship.getFamiliarity() + relationship.getTrust() + relationship.getRespect();
		if ((warmthScore >= 8) || (social.getTrustBias() >= 3) || "trusted".equalsIgnoreCase(social.getSocialLabel()) || "friendly".equalsIgnoreCase(social.getSocialLabel()))
		{
			return "friendly";
		}
		return "familiar";
	}

	private String buildLiveRiskSummary(Player sender)
	{
		if (sender == null)
		{
			return "";
		}

		final java.util.List<String> reasons = new java.util.ArrayList<>(3);
		if (sender.getReputation() < 0)
		{
			reasons.add("chaotic reputation");
		}
		if (sender.getPkKills() >= 10)
		{
			reasons.add("blooded player-kill history");
		}
		else if (sender.getPkKills() > 0)
		{
			reasons.add("some player-kill history");
		}
		if (sender.getPvpFlag() > 0)
		{
			reasons.add("active PvP pressure");
		}
		return reasons.isEmpty() ? "" : String.join(", ", reasons);
	}
}
