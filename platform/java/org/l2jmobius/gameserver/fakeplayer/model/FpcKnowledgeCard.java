package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FpcKnowledgeCard
{
	private final String _fpcId;
	private final String _topicId;
	private final String _domain;
	private final String _questionType;
	private final String _channel;
	private final String _audience;
	private final String _triggers;
	private final int _priority;
	private final String _summary;
	private final String _facts;
	private final String _generalAnswer;
	private final String _whisperAnswer;
	private final String _shortAdvice;
	private final String _confidenceHint;

	public FpcKnowledgeCard(String fpcId, String topicId, String domain, String questionType, String channel, String audience, String triggers, int priority, String summary, String facts, String generalAnswer, String whisperAnswer, String shortAdvice, String confidenceHint)
	{
		_fpcId = Objects.requireNonNull(fpcId);
		_topicId = Objects.requireNonNull(topicId);
		_domain = Objects.requireNonNull(domain);
		_questionType = Objects.requireNonNull(questionType);
		_channel = Objects.requireNonNull(channel);
		_audience = Objects.requireNonNull(audience);
		_triggers = Objects.requireNonNull(triggers);
		_priority = priority;
		_summary = Objects.requireNonNull(summary);
		_facts = Objects.requireNonNull(facts);
		_generalAnswer = Objects.requireNonNull(generalAnswer);
		_whisperAnswer = Objects.requireNonNull(whisperAnswer);
		_shortAdvice = Objects.requireNonNull(shortAdvice);
		_confidenceHint = Objects.requireNonNull(confidenceHint);
	}

	public String getFpcId()
	{
		return _fpcId;
	}

	public String getTopicId()
	{
		return _topicId;
	}

	public String getDomain()
	{
		return _domain;
	}

	public String getQuestionType()
	{
		return _questionType;
	}

	public String getChannel()
	{
		return _channel;
	}

	public String getAudience()
	{
		return _audience;
	}

	public String getTriggers()
	{
		return _triggers;
	}

	public int getPriority()
	{
		return _priority;
	}

	public String getSummary()
	{
		return _summary;
	}

	public String getFacts()
	{
		return _facts;
	}

	public List<String> getFactLines()
	{
		final List<String> facts = new ArrayList<>();
		for (String rawPart : _facts.split("\\|"))
		{
			final String fact = rawPart.trim();
			if (!fact.isBlank())
			{
				facts.add(fact);
			}
		}
		return facts;
	}

	public String getGeneralAnswer()
	{
		return _generalAnswer;
	}

	public String getWhisperAnswer()
	{
		return _whisperAnswer;
	}

	public String getShortAdvice()
	{
		return _shortAdvice;
	}

	public String getConfidenceHint()
	{
		return _confidenceHint;
	}
}
