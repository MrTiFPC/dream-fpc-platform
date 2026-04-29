package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;

public class MobiusTierAWorldFacade implements FakePlayerWorldFacade
{
	@Override
	public Player findPlayer(String name)
	{
		return World.getInstance().getPlayer(name);
	}

	@Override
	public Player findPlayer(int objectId)
	{
		return World.getInstance().getPlayer(objectId);
	}

	@Override
	public WorldObject findObject(int objectId)
	{
		return World.getInstance().findObject(objectId);
	}

	@Override
	public Collection<Player> getPlayers()
	{
		return World.getInstance().getPlayers();
	}

	@Override
	public <T extends WorldObject> List<T> getVisibleObjectsInRange(WorldObject origin, Class<T> type, int range)
	{
		return World.getInstance().getVisibleObjectsInRange(origin, type, range);
	}

	@Override
	public <T extends WorldObject> void forEachVisibleObjectInRange(WorldObject origin, Class<T> type, int range, Consumer<T> consumer)
	{
		World.getInstance().forEachVisibleObjectInRange(origin, type, range, consumer);
	}
}
