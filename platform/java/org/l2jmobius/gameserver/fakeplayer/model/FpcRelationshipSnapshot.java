package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public final class FpcRelationshipSnapshot
{
	public static final FpcRelationshipSnapshot EMPTY = new FpcRelationshipSnapshot(0, 0, 0, 0, "", "", "");

	private final int _familiarity;
	private final int _trust;
	private final int _respect;
	private final int _tension;
	private final int _resentment;
	private final int _playerKillCount;
	private final String _currentGoal;
	private final String _activeNeed;
	private final String _lastImportantTopic;

	public FpcRelationshipSnapshot(int familiarity, int trust, int respect, int tension, String currentGoal, String activeNeed, String lastImportantTopic)
	{
		this(familiarity, trust, respect, tension, 0, 0, currentGoal, activeNeed, lastImportantTopic);
	}

	public FpcRelationshipSnapshot(int familiarity, int trust, int respect, int tension, int resentment, int playerKillCount, String currentGoal, String activeNeed, String lastImportantTopic)
	{
		_familiarity = familiarity;
		_trust = trust;
		_respect = respect;
		_tension = tension;
		_resentment = resentment;
		_playerKillCount = playerKillCount;
		_currentGoal = Objects.requireNonNullElse(currentGoal, "");
		_activeNeed = Objects.requireNonNullElse(activeNeed, "");
		_lastImportantTopic = Objects.requireNonNullElse(lastImportantTopic, "");
	}

	public int getFamiliarity()
	{
		return _familiarity;
	}

	public int getTrust()
	{
		return _trust;
	}

	public int getRespect()
	{
		return _respect;
	}

	public int getTension()
	{
		return _tension;
	}

	public int getResentment()
	{
		return _resentment;
	}

	public int getPlayerKillCount()
	{
		return _playerKillCount;
	}

	public String getCurrentGoal()
	{
		return _currentGoal;
	}

	public String getActiveNeed()
	{
		return _activeNeed;
	}

	public String getLastImportantTopic()
	{
		return _lastImportantTopic;
	}

	public boolean hasMeaningfulHistory()
	{
		return (_familiarity > 0) || (_trust > 0) || (_respect > 0) || (_tension > 0) || (_resentment > 0) || (_playerKillCount > 0) || !_lastImportantTopic.isBlank();
	}
}
