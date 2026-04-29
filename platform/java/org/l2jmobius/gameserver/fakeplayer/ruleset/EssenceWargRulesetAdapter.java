package org.l2jmobius.gameserver.fakeplayer.ruleset;

import java.util.Locale;

import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;

public class EssenceWargRulesetAdapter implements FakePlayerRulesetAdapter
{
	public static final String ID = "essence_warg";
	private static final String DEFAULT_SOCIAL_ZONE_ID = "giran";
	private static final long DEFAULT_ADMIN_HOLD_MS = 30000L;
	private static final long TELEPORT_RETURN_DISTANCE_SQ = 250000L;

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public String getDefaultSocialZoneId()
	{
		return DEFAULT_SOCIAL_ZONE_ID;
	}

	@Override
	public long getAdminInterventionHoldMs()
	{
		return DEFAULT_ADMIN_HOLD_MS;
	}

	@Override
	public boolean shouldPreferTeleportReturnAfterAdminIntervention(long distanceSquared)
	{
		return distanceSquared >= TELEPORT_RETURN_DISTANCE_SQ;
	}

	@Override
	public String chooseResumeWorkLine(FpcDefinition definition)
	{
		if (definition == null)
		{
			return "Back to work.";
		}

		switch (definition.getId().toLowerCase(Locale.ENGLISH))
		{
			case "pippa":
				return "Back to work, then.";
			case "caelan":
				return "Back on route.";
			case "marc":
				return "I should return to my watch.";
			default:
				return "Back to work.";
		}
	}

	@Override
	public boolean supportsSharedLocationPins()
	{
		return true;
	}
}
