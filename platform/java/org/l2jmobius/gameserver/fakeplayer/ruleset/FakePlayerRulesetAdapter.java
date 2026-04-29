package org.l2jmobius.gameserver.fakeplayer.ruleset;

import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;

public interface FakePlayerRulesetAdapter
{
	String getId();

	String getDefaultSocialZoneId();

	long getAdminInterventionHoldMs();

	boolean shouldPreferTeleportReturnAfterAdminIntervention(long distanceSquared);

	String chooseResumeWorkLine(FpcDefinition definition);

	default boolean supportsSharedLocationPins()
	{
		return false;
	}
}
