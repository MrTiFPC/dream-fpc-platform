package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Collection;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerStageBandData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStageBand;

public class FakePlayerAdventurerService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerAdventurerService.class.getName());

	private final FakePlayerStageBandData _stageBandData;

	public FakePlayerAdventurerService(FakePlayerStageBandData stageBandData)
	{
		_stageBandData = stageBandData;
	}

	public void validateDefinitions(Collection<FpcDefinition> definitions)
	{
		int adventurerCount = 0;
		int stagedCount = 0;
		int warningCount = 0;

		for (FpcDefinition definition : definitions)
		{
			final FpcAdventurerProfile profile = definition.getAdventurerProfile();
			if (!profile.isAdventurerTier())
			{
				continue;
			}

			adventurerCount++;

			if (!profile.isNpcBacked())
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " requests unsupported carrierMode=" + profile.getCarrierMode() + ". NPC-backed remains the only supported adventurer vessel for this phase.");
			}
			if (!profile.usesSyntheticInventory())
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " requests unsupported inventoryMode=" + profile.getInventoryMode() + ". Adventurer FPCs should use synthetic inventory for now.");
			}
			if (!profile.isPickupDisabled())
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " requests pickupPolicy=" + profile.getPickupPolicy() + ". World pickup is intentionally disabled in the NPC-backed adventurer phase.");
			}
			if (profile.usesStagedProgression())
			{
				stagedCount++;
				final FpcStageBand stageBand = resolveStageBand(profile.getStageBandId());
				if (stageBand == null)
				{
					warningCount++;
					LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " uses staged progression but references unknown stageBandId=" + profile.getStageBandId());
				}
			}
			else if (profile.getFixedLevel() <= 0)
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " uses fixed progression without a positive fixedLevel.");
			}
			if (profile.supportsClanMembership() && !FakePlayerHybridClanService.HYBRID_CLAN_MODE.equalsIgnoreCase(profile.getClanMode()))
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " requests unsupported clanMode=" + profile.getClanMode() + ". Supported adventurer clan mode is " + FakePlayerHybridClanService.HYBRID_CLAN_MODE + ".");
			}
			if (profile.supportsMatchmaking() && !profile.supportsPartyCoordination())
			{
				warningCount++;
				LOGGER.warning(getClass().getSimpleName() + ": FPC " + definition.getId() + " requests matchmakingMode=" + profile.getMatchmakingMode() + " without party support.");
			}
		}

		LOGGER.info(getClass().getSimpleName() + ": Validated adventurer profiles across definitions=" + definitions.size() + " adventurerTier=" + adventurerCount + " staged=" + stagedCount + " warnings=" + warningCount);
	}

	public FpcStageBand resolveStageBand(String stageBandId)
	{
		return _stageBandData.getStageBand(stageBandId);
	}

	public String describeDefinition(FpcDefinition definition)
	{
		final FpcAdventurerProfile profile = definition.getAdventurerProfile();
		final StringBuilder summary = new StringBuilder();
		summary.append("tier=").append(profile.getTier());
		summary.append(" carrier=").append(profile.getCarrierMode());
		summary.append(" inventory=").append(profile.getInventoryMode());
		summary.append(" pickup=").append(profile.getPickupPolicy());
		summary.append(" party=").append(profile.getPartyMode());
		if (!"none".equalsIgnoreCase(profile.getClanMode()))
		{
			summary.append(" clan=").append(profile.getClanMode());
		}
		if (!"none".equalsIgnoreCase(profile.getMatchmakingMode()))
		{
			summary.append(" match=").append(profile.getMatchmakingMode());
		}
		if (profile.usesStagedProgression())
		{
			final FpcStageBand stageBand = resolveStageBand(profile.getStageBandId());
			summary.append(" progression=staged:").append(profile.getStageBandId().isBlank() ? "missing" : profile.getStageBandId());
			if (stageBand != null)
			{
				summary.append("(").append(stageBand.getRepresentativeLevel()).append(")");
			}
		}
		else
		{
			summary.append(" progression=").append(profile.getFixedLevel() > 0 ? ("fixed:" + profile.getFixedLevel()) : "static");
		}
		if (!profile.getLoadoutProfile().isBlank())
		{
			summary.append(" loadout=").append(profile.getLoadoutProfile());
		}
		if (!profile.getConsumableProfile().isBlank())
		{
			summary.append(" consumables=").append(profile.getConsumableProfile());
		}
		return summary.toString();
	}
}
