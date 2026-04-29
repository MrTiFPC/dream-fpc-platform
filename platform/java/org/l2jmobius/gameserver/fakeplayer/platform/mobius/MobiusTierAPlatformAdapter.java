package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerChatTransport;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerCombatCapabilityFacade;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerHybridPartyAdapter;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerNavigationFacade;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapter;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerSpawnLifecycle;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;

public class MobiusTierAPlatformAdapter implements FakePlayerPlatformAdapter
{
	public static final String ID = "mobius_tier_a";

	private final FakePlayerChatTransport _chatTransport = new MobiusTierAChatTransport();
	private final FakePlayerWorldFacade _worldFacade = new MobiusTierAWorldFacade();
	private final FakePlayerSpawnLifecycle _spawnLifecycle = new MobiusTierASpawnLifecycle();
	private final FakePlayerNavigationFacade _navigationFacade = new MobiusTierANavigationFacade();
	private final FakePlayerCombatCapabilityFacade _combatCapabilityFacade = new MobiusTierACombatCapabilityFacade();
	private final FakePlayerHybridPartyAdapter _hybridPartyAdapter = new MobiusTierAHybridPartyAdapter();

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public FakePlayerChatTransport getChatTransport()
	{
		return _chatTransport;
	}

	@Override
	public FakePlayerWorldFacade getWorldFacade()
	{
		return _worldFacade;
	}

	@Override
	public FakePlayerSpawnLifecycle getSpawnLifecycle()
	{
		return _spawnLifecycle;
	}

	@Override
	public FakePlayerNavigationFacade getNavigationFacade()
	{
		return _navigationFacade;
	}

	@Override
	public FakePlayerCombatCapabilityFacade getCombatCapabilityFacade()
	{
		return _combatCapabilityFacade;
	}

	@Override
	public FakePlayerHybridPartyAdapter getHybridPartyAdapter()
	{
		return _hybridPartyAdapter;
	}
}
