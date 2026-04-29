package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.List;
import java.util.Objects;

public class FpcKnowledgeSelection
{
	public static final FpcKnowledgeSelection EMPTY = new FpcKnowledgeSelection("", "", "", List.of(), List.of(), List.of(), "");

	private final String _topicId;
	private final String _confidenceHint;
	private final String _summary;
	private final List<String> _factLines;
	private final List<String> _candidateLines;
	private final List<String> _heuristicLines;
	private final String _scope;

	public FpcKnowledgeSelection(String topicId, String confidenceHint, String summary, List<String> factLines, List<String> candidateLines, List<String> heuristicLines, String scope)
	{
		_topicId = Objects.requireNonNull(topicId);
		_confidenceHint = Objects.requireNonNull(confidenceHint);
		_summary = Objects.requireNonNull(summary);
		_factLines = List.copyOf(factLines);
		_candidateLines = List.copyOf(candidateLines);
		_heuristicLines = List.copyOf(heuristicLines);
		_scope = Objects.requireNonNull(scope);
	}

	public String getTopicId()
	{
		return _topicId;
	}

	public String getConfidenceHint()
	{
		return _confidenceHint;
	}

	public String getSummary()
	{
		return _summary;
	}

	public List<String> getFactLines()
	{
		return _factLines;
	}

	public List<String> getCandidateLines()
	{
		return _candidateLines;
	}

	public List<String> getHeuristicLines()
	{
		return _heuristicLines;
	}

	public String getScope()
	{
		return _scope;
	}
}
