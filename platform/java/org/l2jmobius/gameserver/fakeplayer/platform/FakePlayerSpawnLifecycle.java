package org.l2jmobius.gameserver.fakeplayer.platform;

import org.l2jmobius.gameserver.model.actor.Npc;

/**
 * Spawn/despawn ownership seam for fakeplayer carriers.
 */
public interface FakePlayerSpawnLifecycle
{
	Object createSpawn(int npcId, int x, int y, int z, int heading) throws Exception;

	void stopRespawn(Object spawn);

	Npc spawnNow(Object spawn);

	Npc getLastSpawn(Object spawn);

	void broadcastInfo(Npc npc);

	void deleteNpc(Npc npc);
}
