package org.l2jmobius.gameserver.fakeplayer.platform;

import org.l2jmobius.gameserver.model.WorldObject;

/**
 * Visibility/reachability seam for navigation/combat decisions.
 */
public interface FakePlayerNavigationFacade
{
	boolean canSeeTarget(WorldObject actor, WorldObject target);
}
