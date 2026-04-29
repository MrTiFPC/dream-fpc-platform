package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerNavigationFacade;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.actor.instance.Monster;

public class FakePlayerTargetingService
{
	private final FakePlayerConfig _config;
	private final FakePlayerWorldFacade _worldFacade;
	private final FakePlayerNavigationFacade _navigationFacade;
	private final ConcurrentHashMap<String, Long> _lastTeleportTimeByActor = new ConcurrentHashMap<>();

	public FakePlayerTargetingService(FakePlayerConfig config, FakePlayerWorldFacade worldFacade, FakePlayerNavigationFacade navigationFacade)
	{
		_config = config;
		_worldFacade = worldFacade;
		_navigationFacade = navigationFacade;
	}

	public List<String> getAllowedIntents()
	{
		return List.of("idle", "move", "travel", "farm", "recover", "speak", "regroup");
	}

	public boolean isTeleportCooldownReady(String actorKey)
	{
		final long cooldown = Math.max(_config.getTeleportCooldownMs(), 0L);
		if (cooldown == 0L)
		{
			return true;
		}

		final Long lastUse = _lastTeleportTimeByActor.get(normalizeActorKey(actorKey));
		return (lastUse == null) || ((System.currentTimeMillis() - lastUse.longValue()) >= cooldown);
	}

	public void markTeleportUsed(String actorKey)
	{
		_lastTeleportTimeByActor.put(normalizeActorKey(actorKey), System.currentTimeMillis());
	}

	public void clearTeleportUsage(String actorKey)
	{
		_lastTeleportTimeByActor.remove(normalizeActorKey(actorKey));
	}

	public Monster selectFarmTarget(Npc npc, FpcAdventurerRoute route, Location farmAnchor)
	{
		if ((npc == null) || (route == null) || (farmAnchor == null))
		{
			return null;
		}

		final AtomicReference<Monster> selected = new AtomicReference<>();
		final AtomicReference<Long> bestDistance = new AtomicReference<>(Long.MAX_VALUE);
		_worldFacade.forEachVisibleObjectInRange(npc, Monster.class, route.getSearchRadius(), monster ->
		{
			if (!isValidFarmTarget(npc, monster, route, farmAnchor))
			{
				return;
			}

			final long distance = distanceSquared(npc.getX(), npc.getY(), monster.getX(), monster.getY());
			if (distance < bestDistance.get().longValue())
			{
				bestDistance.set(Long.valueOf(distance));
				selected.set(monster);
			}
		});
		return selected.get();
	}

	public Creature selectHybridClanWarTarget(Npc npc)
	{
		return selectHybridClanWarTarget(npc, null);
	}

	public Creature selectHybridClanWarTarget(Npc npc, Location originAnchor)
	{
		if ((npc == null) || !npc.isFakePlayer() || npc.isDead() || npc.isInsideZone(ZoneId.PEACE) || npc.isInsideZone(ZoneId.NO_PVP))
		{
			return null;
		}

		final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
		if ((hybridClanService == null) || (hybridClanService.getHybridClanId(npc) <= 0))
		{
			return null;
		}

		final AtomicReference<Creature> selected = new AtomicReference<>();
		final AtomicReference<Long> bestDistance = new AtomicReference<>(Long.MAX_VALUE);
		_worldFacade.forEachVisibleObjectInRange(npc, Creature.class, Math.max(_config.getHybridClanWarEngageRange(), 200), target ->
		{
			if (!isValidHybridClanWarTarget(npc, target, originAnchor, _config.getHybridClanWarEngageRange(), hybridClanService))
			{
				return;
			}

			final long distance = distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY());
			if (distance < bestDistance.get().longValue())
			{
				bestDistance.set(Long.valueOf(distance));
				selected.set(target);
			}
		});
		return selected.get();
	}

	public boolean isValidHybridClanWarTarget(Npc npc, Creature target)
	{
		final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
		return (hybridClanService != null) && isValidHybridClanWarTarget(npc, target, null, _config.getHybridClanWarEngageRange(), hybridClanService);
	}

	public boolean isHybridClanWarTargetSuppressed(Npc npc, Creature target)
	{
		final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
		if (target == null)
		{
			return false;
		}
		if (target.isPlayer())
		{
			return (hybridClanService != null) && hybridClanService.isHybridClanWarTargetSuppressed(npc, target.asPlayer(), _config.getHybridClanWarRepeatKillerWindowMs(), _config.getHybridClanWarRepeatKillerThreshold());
		}
		return false;
	}

	public boolean isValidHybridClanWarTarget(Npc npc, Creature target, Location originAnchor, int maxOriginRange)
	{
		final FakePlayerHybridClanService hybridClanService = FakePlayerHybridClanService.getInstance();
		return (hybridClanService != null) && isValidHybridClanWarTarget(npc, target, originAnchor, maxOriginRange, hybridClanService);
	}

	public boolean isValidHybridClanWarTarget(Npc npc, Player player)
	{
		return isValidHybridClanWarTarget(npc, (Creature) player);
	}

	public boolean isHybridClanWarTargetSuppressed(Npc npc, Player player)
	{
		return isHybridClanWarTargetSuppressed(npc, (Creature) player);
	}

	public boolean isValidHybridClanWarTarget(Npc npc, Player player, Location originAnchor, int maxOriginRange)
	{
		return isValidHybridClanWarTarget(npc, (Creature) player, originAnchor, maxOriginRange);
	}

	private boolean isValidFarmTarget(Npc npc, Monster monster, FpcAdventurerRoute route, Location farmAnchor)
	{
		if ((monster == null) || monster.isDead() || monster.isRaid() || monster.isQuestMonster() || monster.isFakePlayer())
		{
			return false;
		}
		if (monster.isInsideZone(ZoneId.PEACE))
		{
			return false;
		}
		if (monster.isInCombat() && (monster.getTarget() != npc))
		{
			return false;
		}
		if (!_navigationFacade.canSeeTarget(npc, monster))
		{
			return false;
		}
		return distanceSquared(monster.getX(), monster.getY(), farmAnchor.getX(), farmAnchor.getY()) <= ((long) route.getLeashRadius() * route.getLeashRadius());
	}

	public boolean isValidFarmTargetForAssist(Npc npc, Monster monster, FpcAdventurerRoute route, Location farmAnchor)
	{
		return isValidFarmTarget(npc, monster, route, farmAnchor);
	}

	public boolean isValidPersonalityConflictTarget(Npc npc, Creature target, Location originAnchor, int maxOriginRange, boolean allowPkEscalation)
	{
		if ((npc == null) || (target == null) || !npc.isFakePlayer() || npc.isDead() || npc.isInsideZone(ZoneId.PEACE) || npc.isInsideZone(ZoneId.NO_PVP))
		{
			return false;
		}
		if (target.isDead() || target.isAlikeDead())
		{
			return false;
		}
		if (target.isPlayer())
		{
			final Player player = target.asPlayer();
			if (player.isInvisible() || !player.isTargetable())
			{
				return false;
			}
		}
		else if (target.isNpc() && !target.asNpc().isTargetable())
		{
			return false;
		}
		if ((target.getInstanceId() != npc.getInstanceId()) || target.isInsideZone(ZoneId.PEACE) || target.isInsideZone(ZoneId.NO_PVP))
		{
			return false;
		}
		if ((originAnchor != null) && (distanceSquared(target.getX(), target.getY(), originAnchor.getX(), originAnchor.getY()) > ((long) Math.max(maxOriginRange, 200) * Math.max(maxOriginRange, 200))))
		{
			return false;
		}
		if (!_navigationFacade.canSeeTarget(npc, target))
		{
			return false;
		}
		if (allowPkEscalation)
		{
			return true;
		}
		if (target.isFakePlayer() && target.isNpc())
		{
			return target.isAutoAttackable(npc);
		}
		return target.isAutoAttackable(npc);
	}

	private boolean isValidHybridClanWarTarget(Npc npc, Creature target, Location originAnchor, int maxOriginRange, FakePlayerHybridClanService hybridClanService)
	{
		if ((npc == null) || (target == null) || (hybridClanService == null) || (target == npc))
		{
			return false;
		}
		if (target.isDead() || target.isAlikeDead())
		{
			return false;
		}
		if ((target.getInstanceId() != npc.getInstanceId()) || target.isInsideZone(ZoneId.PEACE) || target.isInsideZone(ZoneId.NO_PVP))
		{
			return false;
		}
		if ((originAnchor != null) && (distanceSquared(target.getX(), target.getY(), originAnchor.getX(), originAnchor.getY()) > ((long) Math.max(maxOriginRange, 200) * Math.max(maxOriginRange, 200))))
		{
			return false;
		}
		if (target.isPlayer())
		{
			final Player player = target.asPlayer();
			if (player.isInvisible() || !player.isTargetable())
			{
				return false;
			}
			if (hybridClanService.isHybridClanWarTargetSuppressed(npc, player, _config.getHybridClanWarRepeatKillerWindowMs(), _config.getHybridClanWarRepeatKillerThreshold()))
			{
				return false;
			}
		}
		else if (!target.isNpc() || !target.asNpc().isFakePlayer() || !target.asNpc().isTargetable())
		{
			return false;
		}
		if (!_navigationFacade.canSeeTarget(npc, target))
		{
			return false;
		}
		if (!hybridClanService.isHybridClanWarAttack(npc, target))
		{
			return false;
		}
		return target.isAutoAttackable(npc);
	}

	private long distanceSquared(int x1, int y1, int x2, int y2)
	{
		final long dx = x1 - x2;
		final long dy = y1 - y2;
		return (dx * dx) + (dy * dy);
	}

	private String normalizeActorKey(String actorKey)
	{
		return ((actorKey == null) || actorKey.isBlank()) ? "fake-player" : actorKey.toLowerCase();
	}
}
