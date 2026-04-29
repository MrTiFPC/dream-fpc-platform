package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public final class FpcRetrievedMemorySection
{
	private final String _label;
	private final String _value;
	private final int _score;

	public FpcRetrievedMemorySection(String label, String value, int score)
	{
		_label = Objects.requireNonNullElse(label, "");
		_value = Objects.requireNonNullElse(value, "");
		_score = score;
	}

	public String getLabel()
	{
		return _label;
	}

	public String getValue()
	{
		return _value;
	}

	public int getScore()
	{
		return _score;
	}
}
