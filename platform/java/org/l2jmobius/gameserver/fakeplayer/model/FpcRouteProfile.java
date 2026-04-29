package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.List;
import java.util.Objects;

public class FpcRouteProfile
{
	public static final FpcRouteProfile EMPTY = new FpcRouteProfile("unknown", "none", "none", "low", false, "unknown", "unknown", "none", List.of(), 0.0);

	private final String _routeLane;
	private final String _speechAct;
	private final String _knowledgeNeed;
	private final String _socialStake;
	private final boolean _actionRequested;
	private final String _messageCategory;
	private final String _knowledgeType;
	private final String _primaryTopic;
	private final List<String> _secondaryTopics;
	private final double _confidence;

	public FpcRouteProfile(String routeLane, String speechAct, String knowledgeNeed, String socialStake, boolean actionRequested, String messageCategory, String knowledgeType, String primaryTopic, List<String> secondaryTopics, double confidence)
	{
		_routeLane = Objects.requireNonNull(routeLane);
		_speechAct = Objects.requireNonNull(speechAct);
		_knowledgeNeed = Objects.requireNonNull(knowledgeNeed);
		_socialStake = Objects.requireNonNull(socialStake);
		_actionRequested = actionRequested;
		_messageCategory = Objects.requireNonNull(messageCategory);
		_knowledgeType = Objects.requireNonNull(knowledgeType);
		_primaryTopic = Objects.requireNonNull(primaryTopic);
		_secondaryTopics = List.copyOf(secondaryTopics);
		_confidence = confidence;
	}

	public String getRouteLane()
	{
		return _routeLane;
	}

	public String getSpeechAct()
	{
		return _speechAct;
	}

	public String getKnowledgeNeed()
	{
		return _knowledgeNeed;
	}

	public String getSocialStake()
	{
		return _socialStake;
	}

	public boolean isActionRequested()
	{
		return _actionRequested;
	}

	public String getMessageCategory()
	{
		return _messageCategory;
	}

	public String getKnowledgeType()
	{
		return _knowledgeType;
	}

	public String getPrimaryTopic()
	{
		return _primaryTopic;
	}

	public List<String> getSecondaryTopics()
	{
		return _secondaryTopics;
	}

	public double getConfidence()
	{
		return _confidence;
	}
}
