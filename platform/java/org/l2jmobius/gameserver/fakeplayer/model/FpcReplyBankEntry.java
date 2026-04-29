package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcReplyBankEntry
{
	private final String _fpcId;
	private final String _bankType;
	private final String _category;
	private final String _channel;
	private final String _audience;
	private final String _triggers;
	private final int _priority;
	private final String _line;

	public FpcReplyBankEntry(String fpcId, String bankType, String category, String channel, String audience, String triggers, int priority, String line)
	{
		_fpcId = fpcId;
		_bankType = bankType;
		_category = category;
		_channel = channel;
		_audience = audience;
		_triggers = triggers;
		_priority = priority;
		_line = line;
	}

	public String getFpcId()
	{
		return _fpcId;
	}

	public String getBankType()
	{
		return _bankType;
	}

	public String getCategory()
	{
		return _category;
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

	public String getLine()
	{
		return _line;
	}
}
