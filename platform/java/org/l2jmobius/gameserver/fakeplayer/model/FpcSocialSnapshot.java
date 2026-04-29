package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public final class FpcSocialSnapshot
{
	public static final FpcSocialSnapshot EMPTY = new FpcSocialSnapshot("neutral", "", 0, 0, FpcSocialActionStance.EMPTY, false);

	private final String _socialLabel;
	private final String _summary;
	private final int _trustBias;
	private final int _guardBias;
	private final FpcSocialActionStance _actionStance;
	private final boolean _historicalKillHistory;

	public FpcSocialSnapshot(String socialLabel, String summary, int trustBias, int guardBias, FpcSocialActionStance actionStance, boolean historicalKillHistory)
	{
		_socialLabel = Objects.requireNonNullElse(socialLabel, "neutral");
		_summary = Objects.requireNonNullElse(summary, "");
		_trustBias = trustBias;
		_guardBias = guardBias;
		_actionStance = (actionStance == null) ? FpcSocialActionStance.EMPTY : actionStance;
		_historicalKillHistory = historicalKillHistory;
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

	public boolean hasHistoricalKillHistory()
	{
		return _historicalKillHistory;
	}

	public boolean hasMeaningfulHistory()
	{
		return !_summary.isBlank() || (_trustBias != 0) || (_guardBias != 0) || _actionStance.hasSignals() || _historicalKillHistory || !"neutral".equalsIgnoreCase(_socialLabel);
	}
}
