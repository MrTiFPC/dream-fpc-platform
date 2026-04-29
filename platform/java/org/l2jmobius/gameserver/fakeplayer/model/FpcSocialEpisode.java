package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

import org.l2jmobius.gameserver.fakeplayer.model.enums.FpcSocialEpisodeType;

public final class FpcSocialEpisode
{
	private final long _timestampMs;
	private final FpcSocialEpisodeType _type;
	private final String _summary;

	public FpcSocialEpisode(long timestampMs, FpcSocialEpisodeType type, String summary)
	{
		_timestampMs = timestampMs;
		_type = Objects.requireNonNull(type);
		_summary = Objects.requireNonNullElse(summary, "");
	}

	public long getTimestampMs()
	{
		return _timestampMs;
	}

	public FpcSocialEpisodeType getType()
	{
		return _type;
	}

	public String getSummary()
	{
		return _summary;
	}
}
