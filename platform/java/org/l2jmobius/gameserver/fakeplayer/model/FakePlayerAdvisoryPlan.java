package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

/**
 * Minimal advisory application plan derived from personality output.
 */
public class FakePlayerAdvisoryPlan
{
	private final String _speakerName;
	private final String _moodTag;
	private final String _intentPreference;
	private final String _preferredZone;
	private final boolean _speakNow;
	private final String _lineStyleTag;
	private final String _topicTag;
	private final String _replySource;
	private final String _selectedLine;
	private final double _confidence;

	public FakePlayerAdvisoryPlan(String speakerName, String moodTag, String intentPreference, String preferredZone, boolean speakNow, String lineStyleTag, String topicTag, String selectedLine, double confidence)
	{
		this(speakerName, moodTag, intentPreference, preferredZone, speakNow, lineStyleTag, topicTag, "unknown", selectedLine, confidence);
	}

	public FakePlayerAdvisoryPlan(String speakerName, String moodTag, String intentPreference, String preferredZone, boolean speakNow, String lineStyleTag, String topicTag, String replySource, String selectedLine, double confidence)
	{
		_speakerName = Objects.requireNonNull(speakerName);
		_moodTag = Objects.requireNonNull(moodTag);
		_intentPreference = Objects.requireNonNull(intentPreference);
		_preferredZone = Objects.requireNonNull(preferredZone);
		_speakNow = speakNow;
		_lineStyleTag = Objects.requireNonNull(lineStyleTag);
		_topicTag = Objects.requireNonNull(topicTag);
		_replySource = Objects.requireNonNull(replySource);
		_selectedLine = selectedLine;
		_confidence = confidence;
	}

	public String getSpeakerName()
	{
		return _speakerName;
	}

	public String getMoodTag()
	{
		return _moodTag;
	}

	public String getIntentPreference()
	{
		return _intentPreference;
	}

	public String getPreferredZone()
	{
		return _preferredZone;
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

	public String getReplySource()
	{
		return _replySource;
	}

	public String getSelectedLine()
	{
		return _selectedLine;
	}

	public double getConfidence()
	{
		return _confidence;
	}
}
