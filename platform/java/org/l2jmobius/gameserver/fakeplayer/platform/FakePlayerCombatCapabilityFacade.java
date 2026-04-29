package org.l2jmobius.gameserver.fakeplayer.platform;

/**
 * Chronicle/runtime combat capability metadata seam.
 * The first portability wave keeps this light while stronger capability projection is extracted later.
 */
public interface FakePlayerCombatCapabilityFacade
{
	boolean supportsSyntheticAdventurerCombat();

	boolean supportsDirectSelfBuffCast();
}
