package org.l2jmobius.gameserver.fakeplayer.model;

import org.l2jmobius.gameserver.model.Location;

public class FakePlayerZoneDefinition
{
	private final String _id;
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _heading;
	private final int _roamRadius;
	private final boolean _enabled;

	public FakePlayerZoneDefinition(String id, int x, int y, int z, int heading, int roamRadius, boolean enabled)
	{
		_id = id;
		_x = x;
		_y = y;
		_z = z;
		_heading = heading;
		_roamRadius = roamRadius;
		_enabled = enabled;
	}

	public String getId()
	{
		return _id;
	}

	public int getX()
	{
		return _x;
	}

	public int getY()
	{
		return _y;
	}

	public int getZ()
	{
		return _z;
	}

	public int getHeading()
	{
		return _heading;
	}

	public int getRoamRadius()
	{
		return _roamRadius;
	}

	public boolean isEnabled()
	{
		return _enabled;
	}

	public Location toLocation()
	{
		return new Location(_x, _y, _z, _heading);
	}
}
