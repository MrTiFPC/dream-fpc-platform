package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.WorldObject;

public class FpcRegistry
{
	private volatile Map<String, FpcDefinition> _definitionsById = Collections.emptyMap();
	private volatile Map<String, FpcDefinition> _definitionsByName = Collections.emptyMap();
	private final Map<String, Integer> _activeNpcObjectIds = new ConcurrentHashMap<>();
	private final Map<String, Object> _activeSpawns = new ConcurrentHashMap<>();
	private final Map<String, ResolvedFpcCombatPower> _activeCombatPowers = new ConcurrentHashMap<>();
	private final FakePlayerWorldFacade _worldFacade;

	public FpcRegistry(FakePlayerWorldFacade worldFacade)
	{
		_worldFacade = worldFacade;
	}
	
	public void replaceDefinitions(Collection<FpcDefinition> definitions)
	{
		final Map<String, FpcDefinition> nextDefinitionsById = new LinkedHashMap<>();
		final Map<String, FpcDefinition> nextDefinitionsByName = new LinkedHashMap<>();
		
		for (FpcDefinition definition : definitions)
		{
			final FpcDefinition previousById = nextDefinitionsById.putIfAbsent(definition.getId().toLowerCase(), definition);
			if (previousById != null)
			{
				throw new IllegalArgumentException("Duplicate FPC id: " + definition.getId());
			}
			
			final FpcDefinition previousByName = nextDefinitionsByName.putIfAbsent(definition.getName().toLowerCase(), definition);
			if (previousByName != null)
			{
				throw new IllegalArgumentException("Duplicate FPC name: " + definition.getName());
			}
		}
		
		_definitionsById = Collections.unmodifiableMap(nextDefinitionsById);
		_definitionsByName = Collections.unmodifiableMap(nextDefinitionsByName);
	}
	
	public Collection<FpcDefinition> getDefinitions()
	{
		return Collections.unmodifiableList(new ArrayList<>(_definitionsById.values()));
	}
	
	public FpcDefinition getDefinitionById(String id)
	{
		return (id == null) ? null : _definitionsById.get(id.toLowerCase());
	}
	
	public FpcDefinition getDefinitionByName(String name)
	{
		return (name == null) ? null : _definitionsByName.get(name.toLowerCase());
	}
	
	public void registerActiveNpc(String id, int objectId)
	{
		if ((id != null) && !id.isBlank())
		{
			_activeNpcObjectIds.put(id.toLowerCase(), objectId);
		}
	}
	
	public void unregisterActiveNpc(String id)
	{
		if ((id != null) && !id.isBlank())
		{
			_activeNpcObjectIds.remove(id.toLowerCase());
		}
	}
	
	public Integer getActiveNpcObjectId(String id)
	{
		return (id == null) ? null : _activeNpcObjectIds.get(id.toLowerCase());
	}
	
	public void registerActiveSpawn(String id, Object spawn)
	{
		if ((id != null) && !id.isBlank() && (spawn != null))
		{
			_activeSpawns.put(id.toLowerCase(), spawn);
		}
	}
	
	public void unregisterActiveSpawn(String id)
	{
		if ((id != null) && !id.isBlank())
		{
			_activeSpawns.remove(id.toLowerCase());
		}
	}
	
	public Object getActiveSpawn(String id)
	{
		return (id == null) ? null : _activeSpawns.get(id.toLowerCase());
	}

	public Collection<String> getActiveSpawnIds()
	{
		return Collections.unmodifiableList(new ArrayList<>(_activeSpawns.keySet()));
	}

	public Npc resolveActiveNpc(String id)
	{
		if ((id == null) || id.isBlank())
		{
			return null;
		}

		final String normalizedId = id.toLowerCase();
		final Integer objectId = _activeNpcObjectIds.get(normalizedId);
		if (objectId != null)
		{
			final WorldObject worldObject = _worldFacade.findObject(objectId.intValue());
			if ((worldObject != null) && worldObject.isNpc())
			{
				final Npc npc = worldObject.asNpc();
				if (isUsableActiveNpc(npc))
				{
					return npc;
				}
			}
			_activeNpcObjectIds.remove(normalizedId, objectId);
		}

		final Object activeSpawn = _activeSpawns.get(normalizedId);
		if (activeSpawn != null)
		{
			final Npc npc = extractLastSpawn(activeSpawn);
			if (isUsableActiveNpc(npc))
			{
				_activeNpcObjectIds.put(normalizedId, npc.getObjectId());
				return npc;
			}
		}
		return null;
	}

	public Npc resolveLastSpawnNpc(String id)
	{
		if ((id == null) || id.isBlank())
		{
			return null;
		}
		return extractLastSpawn(_activeSpawns.get(id.toLowerCase()));
	}

	private boolean isUsableActiveNpc(Npc npc)
	{
		return (npc != null) && npc.isSpawned() && !npc.isDead() && !npc.isDecayed();
	}

	private Npc extractLastSpawn(Object activeSpawn)
	{
		if (activeSpawn == null)
		{
			return null;
		}
		try
		{
			final Object npc = activeSpawn.getClass().getMethod("getLastSpawn").invoke(activeSpawn);
			return (npc instanceof Npc) ? (Npc) npc : null;
		}
		catch (ReflectiveOperationException e)
		{
			return null;
		}
	}

	public void registerActiveCombatPower(String id, ResolvedFpcCombatPower combatPower)
	{
		if ((id != null) && !id.isBlank() && (combatPower != null))
		{
			_activeCombatPowers.put(id.toLowerCase(), combatPower);
		}
	}

	public void unregisterActiveCombatPower(String id)
	{
		if ((id != null) && !id.isBlank())
		{
			_activeCombatPowers.remove(id.toLowerCase());
		}
	}

	public ResolvedFpcCombatPower getActiveCombatPower(String id)
	{
		return (id == null) ? null : _activeCombatPowers.get(id.toLowerCase());
	}
}
