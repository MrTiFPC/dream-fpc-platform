package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcChannelProfile
{
	private final boolean _whisperEnabled;
	private final boolean _generalEnabled;
	private final boolean _shoutEnabled;
	private final boolean _worldEnabled;
	
	public FpcChannelProfile(boolean whisperEnabled, boolean generalEnabled, boolean shoutEnabled, boolean worldEnabled)
	{
		_whisperEnabled = whisperEnabled;
		_generalEnabled = generalEnabled;
		_shoutEnabled = shoutEnabled;
		_worldEnabled = worldEnabled;
	}
	
	public boolean isWhisperEnabled()
	{
		return _whisperEnabled;
	}
	
	public boolean isGeneralEnabled()
	{
		return _generalEnabled;
	}
	
	public boolean isShoutEnabled()
	{
		return _shoutEnabled;
	}
	
	public boolean isWorldEnabled()
	{
		return _worldEnabled;
	}
}
