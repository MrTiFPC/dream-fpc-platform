package org.l2jmobius.gameserver.fakeplayer.platform;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Query seam over the host runtime world registry/visibility layer.
 */
public interface FakePlayerWorldFacade
{
	Player findPlayer(String name);

	Player findPlayer(int objectId);

	WorldObject findObject(int objectId);

	Collection<Player> getPlayers();

	<T extends WorldObject> List<T> getVisibleObjectsInRange(WorldObject origin, Class<T> type, int range);

	<T extends WorldObject> void forEachVisibleObjectInRange(WorldObject origin, Class<T> type, int range, Consumer<T> consumer);
}
