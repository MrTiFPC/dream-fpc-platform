package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcCompanionProfile
{
	private final String _relationGate;
	private final String _sessionMode;
	private final String _idleBehavior;
	private final long _idleReturnHomeMs;
	private final boolean _allowInstanceFollow;
	private final String _followupMode;
	private final String _clanJoinMode;

	public FpcCompanionProfile(String relationGate, String sessionMode, String idleBehavior, long idleReturnHomeMs, boolean allowInstanceFollow, String followupMode, String clanJoinMode)
	{
		_relationGate = sanitize(relationGate, "none");
		_sessionMode = sanitize(sessionMode, "default");
		_idleBehavior = sanitize(idleBehavior, "none");
		_idleReturnHomeMs = Math.max(0L, idleReturnHomeMs);
		_allowInstanceFollow = allowInstanceFollow;
		_followupMode = sanitize(followupMode, "none");
		_clanJoinMode = sanitize(clanJoinMode, "default");
	}

	private String sanitize(String value, String defaultValue)
	{
		return ((value == null) || value.isBlank()) ? defaultValue : value;
	}

	public String getRelationGate()
	{
		return _relationGate;
	}

	public String getSessionMode()
	{
		return _sessionMode;
	}

	public String getIdleBehavior()
	{
		return _idleBehavior;
	}

	public long getIdleReturnHomeMs()
	{
		return _idleReturnHomeMs;
	}

	public boolean isAllowInstanceFollow()
	{
		return _allowInstanceFollow;
	}

	public String getFollowupMode()
	{
		return _followupMode;
	}

	public String getClanJoinMode()
	{
		return _clanJoinMode;
	}

	public boolean requiresGoodFriendRelation()
	{
		return "good_friend".equalsIgnoreCase(_relationGate);
	}

	public boolean isStickySession()
	{
		return "sticky".equalsIgnoreCase(_sessionMode);
	}

	public boolean isHomeAnchorIdleBehavior()
	{
		return "home_anchor".equalsIgnoreCase(_idleBehavior);
	}

	public boolean usesSoftWhisperFollowup()
	{
		return "soft_whisper".equalsIgnoreCase(_followupMode);
	}

	public boolean isManualExclusiveClanJoinMode()
	{
		return "manual_exclusive".equalsIgnoreCase(_clanJoinMode);
	}

	public boolean isConfigured()
	{
		return !"none".equalsIgnoreCase(_relationGate)
			|| !"default".equalsIgnoreCase(_sessionMode)
			|| !"none".equalsIgnoreCase(_idleBehavior)
			|| (_idleReturnHomeMs > 0L)
			|| _allowInstanceFollow
			|| !"none".equalsIgnoreCase(_followupMode)
			|| !"default".equalsIgnoreCase(_clanJoinMode);
	}
}
