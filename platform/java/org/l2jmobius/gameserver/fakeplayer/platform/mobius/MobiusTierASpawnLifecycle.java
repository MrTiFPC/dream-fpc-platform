package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerSpawnLifecycle;
import org.l2jmobius.gameserver.model.actor.Npc;

public class MobiusTierASpawnLifecycle implements FakePlayerSpawnLifecycle
{
	private static final String[] SPAWN_CLASS_NAMES =
	{
		"org.l2jmobius.gameserver.model.Spawn",
		"org.l2jmobius.gameserver.model.spawns.Spawn"
	};

	@Override
	public Object createSpawn(int npcId, int x, int y, int z, int heading) throws Exception
	{
		final Class<?> spawnClass = resolveSpawnClass();
		final Constructor<?> constructor = spawnClass.getConstructor(int.class);
		final Object spawn = constructor.newInstance(npcId);
		invoke(spawn, "setHeading", new Class<?>[]
		{
			int.class
		}, heading);
		invoke(spawn, "setXYZ", new Class<?>[]
		{
			int.class,
			int.class,
			int.class
		}, x, y, z);
		return spawn;
	}

	@Override
	public void stopRespawn(Object spawn)
	{
		if (spawn != null)
		{
			invoke(spawn, "stopRespawn", new Class<?>[0]);
		}
	}

	@Override
	public Npc spawnNow(Object spawn)
	{
		if (spawn == null)
		{
			return null;
		}
		try
		{
			return (Npc) invoke(spawn, "doSpawn", new Class<?>[]
			{
				boolean.class
			}, false);
		}
		catch (IllegalStateException firstFailure)
		{
			return (Npc) invoke(spawn, "doSpawn", new Class<?>[0]);
		}
	}

	@Override
	public Npc getLastSpawn(Object spawn)
	{
		return (spawn != null) ? (Npc) invoke(spawn, "getLastSpawn", new Class<?>[0]) : null;
	}

	@Override
	public void broadcastInfo(Npc npc)
	{
		if (npc != null)
		{
			npc.broadcastInfo();
		}
	}

	@Override
	public void deleteNpc(Npc npc)
	{
		if (npc != null)
		{
			npc.deleteMe();
		}
	}

	private Class<?> resolveSpawnClass() throws ClassNotFoundException
	{
		ClassNotFoundException lastFailure = null;
		for (String className : SPAWN_CLASS_NAMES)
		{
			try
			{
				return Class.forName(className);
			}
			catch (ClassNotFoundException e)
			{
				lastFailure = e;
			}
		}
		throw lastFailure;
	}

	private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
	{
		try
		{
			final Method method = target.getClass().getMethod(methodName, parameterTypes);
			return method.invoke(target, args);
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException("Spawn lifecycle reflection failed for " + target.getClass().getName() + "." + methodName + "()", e);
		}
	}
}
