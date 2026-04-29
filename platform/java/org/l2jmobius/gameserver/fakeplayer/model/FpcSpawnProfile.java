package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcSpawnProfile
{
	private final String _mode;
	private final String _zone;
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _heading;
	
	public FpcSpawnProfile(String mode, String zone, int x, int y, int z, int heading)
	{
		_mode = mode;
		_zone = zone;
		_x = x;
		_y = y;
		_z = z;
		_heading = heading;
	}
	
	public String getMode()
	{
		return _mode;
	}
	
	public String getZone()
	{
		return _zone;
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
}
