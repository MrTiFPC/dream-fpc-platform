package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerNavigationFacade;

public class MobiusTierANavigationFacade implements FakePlayerNavigationFacade
{
	@Override
	public boolean canSeeTarget(WorldObject actor, WorldObject target)
	{
		return GeoEngine.getInstance().canSeeTarget(actor, target);
	}
}
