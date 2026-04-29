package org.l2jmobius.gameserver.fakeplayer.service;

import org.l2jmobius.gameserver.fakeplayer.FakePlayerVariableKey;
import org.l2jmobius.gameserver.data.xml.FakePlayerData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerGearData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcGearSet;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.mobius.MobiusTierAEffectAccess;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.skill.AbnormalVisualEffect;

/**
 * Draft appearance service placeholder.
 */
public class FakePlayerAppearanceService
{
	private static final int CHANGE_APPEARANCE_SKILL_ID = 87344;

	private final FakePlayerGearData _gearData;

	public FakePlayerAppearanceService(FakePlayerGearData gearData)
	{
		_gearData = gearData;
	}

	public int resolveCarrierNpcId(FpcDefinition definition)
	{
		if (definition.isFixedCarrierPolicy() && (definition.getCarrierNpcId() > 0))
		{
			return definition.getCarrierNpcId();
		}
		
		int npcId = 0;
		if (definition.isPresentationDrivenCarrierPolicy())
		{
			if (definition.getPresentationCarrierNpcId() > 0)
			{
				npcId = definition.getPresentationCarrierNpcId();
			}
			if ((npcId <= 0) && (definition.getPresentationCarrierTemplate() != null) && !definition.getPresentationCarrierTemplate().isBlank())
			{
				npcId = safeNpcIdLookup(definition.getPresentationCarrierTemplate());
			}
		}
		if (npcId <= 0)
		{
			if (definition.isPlayerLikePresentation())
			{
				npcId = safeNpcIdLookup(definition.getName());
				if (npcId <= 0)
				{
					npcId = safeNpcIdLookup(definition.getCarrierTemplate());
				}
			}
			else
			{
				npcId = safeNpcIdLookup(definition.getCarrierTemplate());
				if (npcId <= 0)
				{
					npcId = safeNpcIdLookup(definition.getName());
				}
			}
		}
		if ((npcId <= 0) && (definition.getCarrierNpcId() > 0))
		{
			npcId = definition.getCarrierNpcId();
		}
		return npcId;
	}
	
	public void applyPresentationProfile(Npc npc, FpcDefinition definition, ResolvedFpcCombatPower combatPower)
	{
		if ((npc == null) || (definition == null))
		{
			return;
		}
		
		if ((definition.getTitle() != null) && !definition.getTitle().isBlank())
		{
			npc.setTitle(definition.getTitle());
		}

		applyGearSetOverride(npc, combatPower);

		// Retail Assassin players show the special armor look when the toggle is off.
		// Fake players have no client to flip that toggle, so keep the abnormal visual
		// disabled and let the client render the assassin presentation by default.
		if ((npc.getSkillLevel(CHANGE_APPEARANCE_SKILL_ID) > 0) && MobiusTierAEffectAccess.hasAbnormalVisualEffect(npc, AbnormalVisualEffect.ASSASSIN_CHANGE_ARMOR))
		{
			MobiusTierAEffectAccess.stopAbnormalVisualEffect(npc, AbnormalVisualEffect.ASSASSIN_CHANGE_ARMOR);
		}
	}

	private void applyGearSetOverride(Npc npc, ResolvedFpcCombatPower combatPower)
	{
		npc.getVariables().remove(FakePlayerVariableKey.GEAR_SET_OVERRIDE);
		if ((combatPower == null) || (_gearData == null))
		{
			return;
		}

		final FpcGearSet gearSet = _gearData.resolveGearSet(combatPower);
		if (gearSet != null)
		{
			npc.getVariables().set(FakePlayerVariableKey.GEAR_SET_OVERRIDE, gearSet);
		}
	}
	
	private int safeNpcIdLookup(String key)
	{
		if ((key == null) || key.isBlank())
		{
			return 0;
		}
		
		try
		{
			return FakePlayerData.getInstance().getNpcIdByName(key);
		}
		catch (Exception e)
		{
			return 0;
		}
	}

	public FakePlayerGearData getGearData()
	{
		return _gearData;
	}
}
