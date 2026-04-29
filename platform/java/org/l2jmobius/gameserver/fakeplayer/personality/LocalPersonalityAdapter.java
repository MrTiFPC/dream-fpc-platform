package org.l2jmobius.gameserver.fakeplayer.personality;

/**
 * Advisory-only bridge to a local personality source.
 *
 * Implementations may consult a local offline AI runtime, but they must never
 * return authoritative gameplay commands.
 */
public interface LocalPersonalityAdapter
{
	PersonalityDecision decide(PersonalityContext context);

	default boolean isHealthy()
	{
		return true;
	}
}
