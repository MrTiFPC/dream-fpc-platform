package org.l2jmobius.gameserver.fakeplayer.personality;

/**
 * Safe fallback adapter used when no local personality runtime is enabled.
 */
public class NoOpLocalPersonalityAdapter implements LocalPersonalityAdapter
{
	@Override
	public PersonalityDecision decide(PersonalityContext context)
	{
		return PersonalityDecision.DEFAULT;
	}

	@Override
	public boolean isHealthy()
	{
		return true;
	}
}
