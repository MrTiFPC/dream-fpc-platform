package org.l2jmobius.gameserver.fakeplayer.platform;

/**
 * Host-runtime adapter for the fakeplayer platform.
 * Rulesets answer chronicle behavior; platform adapters answer engine/runtime ownership.
 */
public interface FakePlayerPlatformAdapter
{
	String getId();

	FakePlayerChatTransport getChatTransport();

	FakePlayerWorldFacade getWorldFacade();

	FakePlayerSpawnLifecycle getSpawnLifecycle();

	FakePlayerNavigationFacade getNavigationFacade();

	FakePlayerCombatCapabilityFacade getCombatCapabilityFacade();

	FakePlayerHybridPartyAdapter getHybridPartyAdapter();
}
