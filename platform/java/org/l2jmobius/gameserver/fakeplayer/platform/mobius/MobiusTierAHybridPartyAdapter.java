package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerHybridPartyAdapter;

public class MobiusTierAHybridPartyAdapter implements FakePlayerHybridPartyAdapter
{
	@Override
	public boolean supportsHybridPartyOverlay()
	{
		return true;
	}
}
