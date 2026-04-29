package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcPersonaRelationship
{
	private final String _sourceId;
	private final String _targetId;
	private final String _bondType;
	private final String _stance;
	private final int _intensity;
	private final String _summary;

	public FpcPersonaRelationship(String sourceId, String targetId, String bondType, String stance, int intensity, String summary)
	{
		_sourceId = sourceId;
		_targetId = targetId;
		_bondType = bondType;
		_stance = stance;
		_intensity = intensity;
		_summary = summary;
	}

	public String getSourceId()
	{
		return _sourceId;
	}

	public String getTargetId()
	{
		return _targetId;
	}

	public String getBondType()
	{
		return _bondType;
	}

	public String getStance()
	{
		return _stance;
	}

	public int getIntensity()
	{
		return _intensity;
	}

	public String getSummary()
	{
		return _summary;
	}
}
