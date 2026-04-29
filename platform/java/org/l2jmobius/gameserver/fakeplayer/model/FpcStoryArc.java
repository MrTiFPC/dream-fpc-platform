package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcStoryArc
{
	private final String _fpcId;
	private final String _arcId;
	private final String _arcRole;
	private final String _currentFocus;
	private final String _currentConflict;
	private final String _chapterSummary;
	private final String _activeObjective;
	private final String _openLoops;
	private final String _revealPressure;
	private final String _relationshipPressure;
	private final String _nextBeatHint;

	public FpcStoryArc(String fpcId, String arcId, String arcRole, String currentFocus, String currentConflict, String chapterSummary, String activeObjective, String openLoops, String revealPressure, String relationshipPressure, String nextBeatHint)
	{
		_fpcId = fpcId;
		_arcId = arcId;
		_arcRole = arcRole;
		_currentFocus = currentFocus;
		_currentConflict = currentConflict;
		_chapterSummary = chapterSummary;
		_activeObjective = activeObjective;
		_openLoops = openLoops;
		_revealPressure = revealPressure;
		_relationshipPressure = relationshipPressure;
		_nextBeatHint = nextBeatHint;
	}

	public String getFpcId()
	{
		return _fpcId;
	}

	public String getArcId()
	{
		return _arcId;
	}

	public String getArcRole()
	{
		return _arcRole;
	}

	public String getCurrentFocus()
	{
		return _currentFocus;
	}

	public String getCurrentConflict()
	{
		return _currentConflict;
	}

	public String getChapterSummary()
	{
		return _chapterSummary;
	}

	public String getActiveObjective()
	{
		return _activeObjective;
	}

	public String getOpenLoops()
	{
		return _openLoops;
	}

	public String getRevealPressure()
	{
		return _revealPressure;
	}

	public String getRelationshipPressure()
	{
		return _relationshipPressure;
	}

	public String getNextBeatHint()
	{
		return _nextBeatHint;
	}
}
