package org.l2jmobius.gameserver.fakeplayer.service;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.platform.mobius.MobiusTierAEffectAccess;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;

/**
 * Generic buff-state evaluator for combat-capable FPCs.
 * AFPCs use it first, but the service is intentionally reusable for later combat-capable FPC tiers.
 */
public class FakePlayerBuffStateService
{
	private final int _refreshWindowSeconds;

	public FakePlayerBuffStateService(FakePlayerConfig config)
	{
		_refreshWindowSeconds = Math.max((config != null) ? config.getBuffRefreshWindowSeconds() : 5, 1);
	}

	public boolean shouldRefreshSelfBuff(Creature caster, Skill skill)
	{
		if (!isEligibleSelfBuff(caster, skill))
		{
			return false;
		}
		if (caster.isSkillDisabled(skill) || caster.hasSkillReuse(skill.getReuseHashCode()) || !skill.checkCondition(caster, caster, false))
		{
			return false;
		}
		return isBuffMissingOrExpiring(caster, skill);
	}

	public boolean shouldMaintainSelfBuff(Creature caster, Skill skill)
	{
		return isEligibleSelfBuff(caster, skill) && isBuffMissingOrExpiring(caster, skill);
	}

	private boolean isEligibleSelfBuff(Creature caster, Skill skill)
	{
		if ((caster == null) || (skill == null) || caster.isDead())
		{
			return false;
		}
		if (!skill.isContinuous() && !skill.isToggle())
		{
			return false;
		}
		if (!skill.hasEffects(EffectScope.SELF) && !skill.hasEffects(EffectScope.GENERAL))
		{
			return false;
		}
		return true;
	}

	private boolean isBuffMissingOrExpiring(Creature caster, Skill skill)
	{
		final BuffInfo exactBuff = MobiusTierAEffectAccess.getBuffInfoBySkillId(caster, skill.getId());
		if (exactBuff != null)
		{
			if (skill.isToggle())
			{
				return !exactBuff.isInUse();
			}
			return (exactBuff.getTime() <= _refreshWindowSeconds) || (exactBuff.getSkill().getLevel() < skill.getLevel());
		}

		final AbnormalType abnormalType = skill.getAbnormalType();
		if ((abnormalType == null) || abnormalType.isNone())
		{
			return false;
		}

		final BuffInfo abnormalBuff = MobiusTierAEffectAccess.getFirstBuffInfoByAbnormalType(caster, abnormalType);
		if (abnormalBuff == null)
		{
			return true;
		}

		return (abnormalBuff.getSkill().getAbnormalLevel() < skill.getAbnormalLevel()) || abnormalBuff.isAbnormalType(AbnormalType.NONE);
	}
}
