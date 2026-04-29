package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcReplySettings
{
	public static final FpcReplySettings DEFAULT = new FpcReplySettings(4, 1200000L, "high", 7, 20, 7, true, "strict", "mentor", "de_escalate", "warm_but_not_constant", "honest_partial", "level_goal_confidence", "system_first", "model_first_guided");

	private final int _recentConversationTurns;
	private final long _recentConversationTtlMs;
	private final String _followUpStrictness;
	private final int _generalMaxWords;
	private final int _whisperMaxWords;
	private final int _publicMaxWords;
	private final boolean _whisperAllowSecondSentence;
	private final String _knowledgeConfidencePolicy;
	private final String _progressionCoachingLevel;
	private final String _pvpConflictPolicy;
	private final String _relationshipWarmth;
	private final String _offscopeClassKnowledge;
	private final String _farmAdviceMode;
	private final String _travelStyle;
	private final String _socialReplyMode;

	public FpcReplySettings(int recentConversationTurns, long recentConversationTtlMs, String followUpStrictness, int generalMaxWords, int whisperMaxWords, int publicMaxWords, boolean whisperAllowSecondSentence, String knowledgeConfidencePolicy, String progressionCoachingLevel, String pvpConflictPolicy, String relationshipWarmth, String offscopeClassKnowledge, String farmAdviceMode, String travelStyle, String socialReplyMode)
	{
		_recentConversationTurns = recentConversationTurns;
		_recentConversationTtlMs = recentConversationTtlMs;
		_followUpStrictness = followUpStrictness;
		_generalMaxWords = generalMaxWords;
		_whisperMaxWords = whisperMaxWords;
		_publicMaxWords = publicMaxWords;
		_whisperAllowSecondSentence = whisperAllowSecondSentence;
		_knowledgeConfidencePolicy = knowledgeConfidencePolicy;
		_progressionCoachingLevel = progressionCoachingLevel;
		_pvpConflictPolicy = pvpConflictPolicy;
		_relationshipWarmth = relationshipWarmth;
		_offscopeClassKnowledge = offscopeClassKnowledge;
		_farmAdviceMode = farmAdviceMode;
		_travelStyle = travelStyle;
		_socialReplyMode = socialReplyMode;
	}

	public int getRecentConversationTurns()
	{
		return _recentConversationTurns;
	}

	public long getRecentConversationTtlMs()
	{
		return _recentConversationTtlMs;
	}

	public String getFollowUpStrictness()
	{
		return _followUpStrictness;
	}

	public int getGeneralMaxWords()
	{
		return _generalMaxWords;
	}

	public int getWhisperMaxWords()
	{
		return _whisperMaxWords;
	}

	public int getPublicMaxWords()
	{
		return _publicMaxWords;
	}

	public boolean isWhisperAllowSecondSentence()
	{
		return _whisperAllowSecondSentence;
	}

	public String getKnowledgeConfidencePolicy()
	{
		return _knowledgeConfidencePolicy;
	}

	public String getProgressionCoachingLevel()
	{
		return _progressionCoachingLevel;
	}

	public String getPvpConflictPolicy()
	{
		return _pvpConflictPolicy;
	}

	public String getRelationshipWarmth()
	{
		return _relationshipWarmth;
	}

	public String getOffscopeClassKnowledge()
	{
		return _offscopeClassKnowledge;
	}

	public String getFarmAdviceMode()
	{
		return _farmAdviceMode;
	}

	public String getTravelStyle()
	{
		return _travelStyle;
	}

	public String getSocialReplyMode()
	{
		return _socialReplyMode;
	}
}
