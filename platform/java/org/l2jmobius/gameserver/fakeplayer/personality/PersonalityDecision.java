package org.l2jmobius.gameserver.fakeplayer.personality;

import java.util.Objects;

/**
 * Advisory-only personality result returned by a local adapter.
 */
public class PersonalityDecision
{
	public static final PersonalityDecision DEFAULT = new PersonalityDecision("calm", "idle", "", 0.0, false, "none", "none", 0.0, null);
	
	private final String _moodTag;
	private final String _intentPreference;
	private final String _zoneBias;
	private final double _aggressionRiskScore;
	private final boolean _speakNow;
	private final String _lineStyleTag;
	private final String _topicTag;
	private final double _confidence;
	private final String _directReplyLine;
	private final String _socialActionTag;
	private final String _socialTargetTag;
	private final double _socialSignalConfidence;
	private final int _socialSignalIntensity;
	
	public PersonalityDecision(String moodTag, String intentPreference, String zoneBias, double aggressionRiskScore, boolean speakNow, String lineStyleTag, String topicTag, double confidence, String directReplyLine)
	{
		this(moodTag, intentPreference, zoneBias, aggressionRiskScore, speakNow, lineStyleTag, topicTag, confidence, directReplyLine, "none", "none", 0.0, 0);
	}

	public PersonalityDecision(String moodTag, String intentPreference, String zoneBias, double aggressionRiskScore, boolean speakNow, String lineStyleTag, String topicTag, double confidence, String directReplyLine, String socialActionTag, String socialTargetTag, double socialSignalConfidence, int socialSignalIntensity)
	{
		_moodTag = Objects.requireNonNull(moodTag);
		_intentPreference = Objects.requireNonNull(intentPreference);
		_zoneBias = Objects.requireNonNull(zoneBias);
		_aggressionRiskScore = aggressionRiskScore;
		_speakNow = speakNow;
		_lineStyleTag = Objects.requireNonNull(lineStyleTag);
		_topicTag = Objects.requireNonNull(topicTag);
		_confidence = confidence;
		_directReplyLine = directReplyLine;
		_socialActionTag = Objects.requireNonNull(socialActionTag);
		_socialTargetTag = Objects.requireNonNull(socialTargetTag);
		_socialSignalConfidence = socialSignalConfidence;
		_socialSignalIntensity = socialSignalIntensity;
	}
	
	public String getMoodTag()
	{
		return _moodTag;
	}
	
	public String getIntentPreference()
	{
		return _intentPreference;
	}
	
	public String getZoneBias()
	{
		return _zoneBias;
	}
	
	public double getAggressionRiskScore()
	{
		return _aggressionRiskScore;
	}
	
	public boolean isSpeakNow()
	{
		return _speakNow;
	}
	
	public String getLineStyleTag()
	{
		return _lineStyleTag;
	}
	
	public String getTopicTag()
	{
		return _topicTag;
	}
	
	public double getConfidence()
	{
		return _confidence;
	}
	
	public String getDirectReplyLine()
	{
		return _directReplyLine;
	}

	public String getSocialActionTag()
	{
		return _socialActionTag;
	}

	public String getSocialTargetTag()
	{
		return _socialTargetTag;
	}

	public double getSocialSignalConfidence()
	{
		return _socialSignalConfidence;
	}

	public int getSocialSignalIntensity()
	{
		return _socialSignalIntensity;
	}
}
