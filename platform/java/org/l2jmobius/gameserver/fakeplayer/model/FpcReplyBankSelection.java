package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.List;

public class FpcReplyBankSelection
{
	public static final FpcReplyBankSelection EMPTY = new FpcReplyBankSelection("", List.of());

	private final String _summary;
	private final List<String> _candidateLines;

	public FpcReplyBankSelection(String summary, List<String> candidateLines)
	{
		_summary = summary;
		_candidateLines = List.copyOf(candidateLines);
	}

	public String getSummary()
	{
		return _summary;
	}

	public List<String> getCandidateLines()
	{
		return _candidateLines;
	}

	public boolean isEmpty()
	{
		return _candidateLines.isEmpty() && ((_summary == null) || _summary.isBlank());
	}
}
