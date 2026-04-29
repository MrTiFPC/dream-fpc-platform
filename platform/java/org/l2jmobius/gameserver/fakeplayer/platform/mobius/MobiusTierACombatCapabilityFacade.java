package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerCombatCapabilityFacade;

public class MobiusTierACombatCapabilityFacade implements FakePlayerCombatCapabilityFacade
{
	@Override
	public boolean supportsSyntheticAdventurerCombat()
	{
		return true;
	}

	@Override
	public boolean supportsDirectSelfBuffCast()
	{
		return true;
	}
}
