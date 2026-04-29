package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.data.holders.TeleportListHolder;
import org.l2jmobius.gameserver.data.xml.TeleportListData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerAdventurerRouteData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerZoneData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerZoneDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;

public class FakePlayerRouteService
{
	private final FakePlayerZoneData _zoneData;
	private final FakePlayerAdventurerRouteData _adventurerRouteData;

	public FakePlayerRouteService(FakePlayerZoneData zoneData, FakePlayerAdventurerRouteData adventurerRouteData)
	{
		_zoneData = zoneData;
		_adventurerRouteData = adventurerRouteData;
	}

	public List<String> getAllowedZones()
	{
		return _zoneData.getDefaultZones();
	}

	public String choosePreferredZone(String requestedZone)
	{
		final List<String> allowedZones = getAllowedZones();
		if ((requestedZone != null) && allowedZones.contains(requestedZone))
		{
			return requestedZone;
		}
		return allowedZones.isEmpty() ? "" : allowedZones.get(0);
	}

	public Location resolveAnchor(String zoneId, FpcDefinition definition)
	{
		final FakePlayerZoneDefinition zone = _zoneData.getZone(zoneId);
		if (zone != null)
		{
			return zone.toLocation();
		}
		return new Location(definition.getSpawnProfile().getX(), definition.getSpawnProfile().getY(), definition.getSpawnProfile().getZ(), definition.getSpawnProfile().getHeading());
	}

	public FpcAdventurerRoute resolveAdventurerRoute(FpcDefinition definition, int level)
	{
		final List<FpcAdventurerRoute> matches = getCandidateAdventurerRoutes(definition, level);
		return matches.isEmpty() ? null : matches.get(0);
	}

	public FpcAdventurerRoute resolveAdventurerRouteExcluding(FpcDefinition definition, int level, String excludedRouteId)
	{
		final String normalizedExcludedRouteId = normalizeRouteId(excludedRouteId);
		for (FpcAdventurerRoute route : getCandidateAdventurerRoutes(definition, level))
		{
			if (!normalizeRouteId(route.getId()).equals(normalizedExcludedRouteId))
			{
				return route;
			}
		}
		return null;
	}

	public List<FpcAdventurerRoute> getCandidateAdventurerRoutes(FpcDefinition definition, int level)
	{
		if ((definition == null) || !definition.getAdventurerProfile().isAdventurerTier())
		{
			return List.of();
		}

		final String explicitRouteId = normalizeRouteId(definition.getAdventurerProfile().getRouteId());
		final List<FpcAdventurerRoute> matches = new ArrayList<>();
		for (FpcAdventurerRoute route : _adventurerRouteData.getRoutes())
		{
			if (!explicitRouteId.isBlank() && !normalizeRouteId(route.getId()).equals(explicitRouteId))
			{
				continue;
			}
			if (!route.matchesLevel(level))
			{
				continue;
			}

			matches.add(route);
		}
		matches.sort(Comparator.comparingInt(FpcAdventurerRoute::getPriority).reversed());
		return matches;
	}

	public Location resolveArrivalLocation(FpcAdventurerRoute route, FpcDefinition definition)
	{
		if (route == null)
		{
			return resolveAnchor(definition.getSpawnProfile().getZone(), definition);
		}

		if (route.getTeleportId() > 0)
		{
			final TeleportListHolder teleport = TeleportListData.getInstance().getTeleport(route.getTeleportId());
			if (teleport != null)
			{
				final Location location = teleport.getLocation();
				if (location != null)
				{
					return new Location(location.getX(), location.getY(), location.getZ(), 0);
				}
			}
		}

		return resolveAnchor(route.getArrivalZoneId(), definition);
	}

	public Location resolveNearestPublicTeleport(Location target)
	{
		if (target == null)
		{
			return null;
		}

		Location best = null;
		long bestDistance = Long.MAX_VALUE;
		for (TeleportListHolder teleport : TeleportListData.getInstance().getTeleports())
		{
			if ((teleport == null) || teleport.isSpecial())
			{
				continue;
			}

			for (Location candidate : teleport.getLocations())
			{
				if (candidate == null)
				{
					continue;
				}

				final long distance = distanceSquared(candidate.getX(), candidate.getY(), target.getX(), target.getY());
				if (distance < bestDistance)
				{
					bestDistance = distance;
					best = new Location(candidate.getX(), candidate.getY(), candidate.getZ(), 0);
				}
			}
		}
		return best;
	}

	public Location resolveFarmAnchor(FpcAdventurerRoute route, FpcDefinition definition)
	{
		if (route == null)
		{
			return resolveAnchor(definition.getSpawnProfile().getZone(), definition);
		}
		return resolveAnchor(route.getFarmZoneId(), definition);
	}

	public Location pickRoamTarget(String zoneId, FpcDefinition definition, Npc npc)
	{
		final FakePlayerZoneDefinition zone = _zoneData.getZone(zoneId);
		if (zone == null)
		{
			return (npc != null) ? resolveReachableTarget(zoneId, definition, npc, resolveAnchor(zoneId, definition)) : resolveAnchor(zoneId, definition);
		}

		final int radius = Math.max(zone.getRoamRadius(), 0);
		if (radius == 0)
		{
			return (npc != null) ? resolveReachableTarget(zoneId, definition, npc, zone.toLocation()) : zone.toLocation();
		}

		final ThreadLocalRandom random = ThreadLocalRandom.current();
		final int originX = (npc != null) ? npc.getX() : zone.getX();
		final int originY = (npc != null) ? npc.getY() : zone.getY();
		final int stepRadius = Math.max(80, Math.min(radius, 160));
		for (int attempt = 0; attempt < 8; attempt++)
		{
			final Location localTarget = clampToReachableTarget(zone, npc, randomPointInCircle(originX, originY, zone.getZ(), zone.getHeading(), stepRadius, random));
			if (isMeaningfulStep(npc, localTarget))
			{
				return localTarget;
			}
		}

		final Location fallbackTarget = clampToReachableTarget(zone, npc, randomPointInCircle(zone.getX(), zone.getY(), zone.getZ(), zone.getHeading(), radius, random));
		if (isMeaningfulStep(npc, fallbackTarget))
		{
			return fallbackTarget;
		}
		return (npc != null) ? currentLocation(npc) : zone.toLocation();
	}

	public Location resolveReachableTarget(String zoneId, FpcDefinition definition, Npc npc, Location desiredTarget)
	{
		final Location baseTarget = (desiredTarget != null) ? desiredTarget : resolveAnchor(zoneId, definition);
		if (npc == null)
		{
			return baseTarget;
		}

		final FakePlayerZoneDefinition zone = _zoneData.getZone(zoneId);
		final Location clampedTarget = clampToReachableTarget(zone, npc, baseTarget);
		if (isMeaningfulStep(npc, clampedTarget))
		{
			return clampedTarget;
		}

		if ((zone != null) && (Math.max(zone.getRoamRadius(), 0) > 0))
		{
			final ThreadLocalRandom random = ThreadLocalRandom.current();
			final int detourRadius = Math.max(90, Math.min(zone.getRoamRadius(), 220));
			for (int attempt = 0; attempt < 8; attempt++)
			{
				final Location detourTarget = clampToReachableTarget(zone, npc, randomPointInCircle(npc.getX(), npc.getY(), zone.getZ(), baseTarget.getHeading(), detourRadius, random));
				if (isMeaningfulStep(npc, detourTarget))
				{
					return detourTarget;
				}
			}
		}

		return currentLocation(npc);
	}

	public String resolveCurrentZone(Npc npc, String fallbackZone)
	{
		if (npc == null)
		{
			return choosePreferredZone(fallbackZone);
		}

		String closestZoneId = null;
		long closestDistance = Long.MAX_VALUE;
		for (FakePlayerZoneDefinition zone : _zoneData.getZones())
		{
			final long dx = npc.getX() - zone.getX();
			final long dy = npc.getY() - zone.getY();
			final long distance = (dx * dx) + (dy * dy);
			if (distance < closestDistance)
			{
				closestDistance = distance;
				closestZoneId = zone.getId();
			}
		}
		return (closestZoneId != null) ? closestZoneId : choosePreferredZone(fallbackZone);
	}

	public boolean isWithinZoneRadius(Npc npc, String zoneId, int radius)
	{
		if ((npc == null) || (zoneId == null) || zoneId.isBlank())
		{
			return false;
		}

		final FakePlayerZoneDefinition zone = _zoneData.getZone(zoneId);
		if (zone == null)
		{
			return false;
		}

		final int effectiveRadius = Math.max(radius, zone.getRoamRadius());
		return distanceSquared(npc.getX(), npc.getY(), zone.getX(), zone.getY()) <= ((long) effectiveRadius * effectiveRadius);
	}

	private Location clampToReachableTarget(FakePlayerZoneDefinition zone, Npc npc, Location desiredTarget)
	{
		if (desiredTarget == null)
		{
			return null;
		}
		if (npc == null)
		{
			return desiredTarget;
		}

		Location reachable = desiredTarget;
		if (!GeoEngine.getInstance().canMoveToTarget(npc.getX(), npc.getY(), npc.getZ(), desiredTarget.getX(), desiredTarget.getY(), desiredTarget.getZ(), npc.getInstanceWorld()))
		{
			reachable = GeoEngine.getInstance().getValidLocation(npc.getX(), npc.getY(), npc.getZ(), desiredTarget.getX(), desiredTarget.getY(), desiredTarget.getZ(), npc.getInstanceWorld());
		}
		if (reachable == null)
		{
			return null;
		}

		final Location normalized = new Location(reachable.getX(), reachable.getY(), reachable.getZ(), desiredTarget.getHeading());
		if ((zone != null) && !isWithinZoneRadius(normalized, zone, Math.max(zone.getRoamRadius(), 0)))
		{
			return null;
		}
		return normalized;
	}

	private boolean isWithinZoneRadius(Location location, FakePlayerZoneDefinition zone, int radius)
	{
		if ((location == null) || (zone == null) || (radius <= 0))
		{
			return true;
		}
		return distanceSquared(location.getX(), location.getY(), zone.getX(), zone.getY()) <= ((long) radius * radius);
	}

	private boolean isMeaningfulStep(Npc npc, Location target)
	{
		return (target != null) && ((npc == null) || (distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY()) >= 10000L));
	}

	private Location currentLocation(Npc npc)
	{
		return new Location(npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
	}

	private Location randomPointInCircle(int centerX, int centerY, int z, int heading, int radius, ThreadLocalRandom random)
	{
		final double angle = random.nextDouble(0, Math.PI * 2);
		final double distance = Math.sqrt(random.nextDouble()) * radius;
		final int x = centerX + (int) Math.round(Math.cos(angle) * distance);
		final int y = centerY + (int) Math.round(Math.sin(angle) * distance);
		return new Location(x, y, z, heading);
	}

	private long distanceSquared(int x1, int y1, int x2, int y2)
	{
		final long dx = x1 - x2;
		final long dy = y1 - y2;
		return (dx * dx) + (dy * dy);
	}

	private String normalizeRouteId(String routeId)
	{
		return (routeId == null) ? "" : routeId.trim().toLowerCase();
	}
}
