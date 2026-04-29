package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcPersonaTemplate
{
	private final String _id;
	private final String _baseSummary;
	private final String _responseStyle;
	private final String _playerStance;
	private final String _coreNeed;

	public FpcPersonaTemplate(String id, String baseSummary, String responseStyle, String playerStance, String coreNeed)
	{
		_id = id;
		_baseSummary = baseSummary;
		_responseStyle = responseStyle;
		_playerStance = playerStance;
		_coreNeed = coreNeed;
	}

	public String getId()
	{
		return _id;
	}

	public String getBaseSummary()
	{
		return _baseSummary;
	}

	public String getResponseStyle()
	{
		return _responseStyle;
	}

	public String getPlayerStance()
	{
		return _playerStance;
	}

	public String getCoreNeed()
	{
		return _coreNeed;
	}
}
