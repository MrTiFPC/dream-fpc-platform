package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.AbnormalVisualEffect;
import org.l2jmobius.gameserver.model.skill.BuffInfo;

/**
 * Bridges effect-list access across Mobius variants where the effect-list type
 * moved packages but the method names stayed stable.
 */
public final class MobiusTierAEffectAccess
{
	private static final Logger LOGGER = Logger.getLogger(MobiusTierAEffectAccess.class.getName());
	private static final Map<Class<?>, EffectListBridge> BRIDGES = new ConcurrentHashMap<>();
	private static final Method CREATURE_GET_EFFECT_LIST = resolveCreatureGetEffectList();

	private MobiusTierAEffectAccess()
	{
	}

	public static BuffInfo getBuffInfoBySkillId(Creature creature, int skillId)
	{
		final Object effectList = getEffectList(creature);
		if (effectList == null)
		{
			return null;
		}

		try
		{
			return (BuffInfo) getBridge(effectList).getBuffInfoBySkillId.invoke(effectList, Integer.valueOf(skillId));
		}
		catch (ReflectiveOperationException | ClassCastException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed reading buff by skill id from effect list class " + effectList.getClass().getName() + ": " + e.getMessage());
			return null;
		}
	}

	public static BuffInfo getFirstBuffInfoByAbnormalType(Creature creature, AbnormalType abnormalType)
	{
		if (abnormalType == null)
		{
			return null;
		}

		final Object effectList = getEffectList(creature);
		if (effectList == null)
		{
			return null;
		}

		try
		{
			return (BuffInfo) getBridge(effectList).getFirstBuffInfoByAbnormalType.invoke(effectList, abnormalType);
		}
		catch (ReflectiveOperationException | ClassCastException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed reading abnormal buff from effect list class " + effectList.getClass().getName() + ": " + e.getMessage());
			return null;
		}
	}

	public static boolean hasAbnormalVisualEffect(Creature creature, AbnormalVisualEffect abnormalVisualEffect)
	{
		if (abnormalVisualEffect == null)
		{
			return false;
		}

		final Object effectList = getEffectList(creature);
		if (effectList == null)
		{
			return false;
		}

		try
		{
			return Boolean.TRUE.equals(getBridge(effectList).hasAbnormalVisualEffect.invoke(effectList, abnormalVisualEffect));
		}
		catch (ReflectiveOperationException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed reading abnormal visual effect from effect list class " + effectList.getClass().getName() + ": " + e.getMessage());
			return false;
		}
	}

	public static void stopAbnormalVisualEffect(Creature creature, AbnormalVisualEffect... abnormalVisualEffects)
	{
		if ((abnormalVisualEffects == null) || (abnormalVisualEffects.length == 0))
		{
			return;
		}

		final Object effectList = getEffectList(creature);
		if (effectList == null)
		{
			return;
		}

		try
		{
			getBridge(effectList).stopAbnormalVisualEffect.invoke(effectList, (Object) abnormalVisualEffects);
		}
		catch (ReflectiveOperationException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed stopping abnormal visual effect on effect list class " + effectList.getClass().getName() + ": " + e.getMessage());
		}
	}

	public static List<BuffInfo> getEffects(Creature creature)
	{
		final Object effectList = getEffectList(creature);
		if (effectList == null)
		{
			return List.of();
		}

		try
		{
			final Object rawEffects = getBridge(effectList).getEffects.invoke(effectList);
			if (!(rawEffects instanceof Collection<?> collection))
			{
				return List.of();
			}

			final List<BuffInfo> effects = new ArrayList<>(collection.size());
			for (Object value : collection)
			{
				if (value instanceof BuffInfo info)
				{
					effects.add(info);
				}
			}
			return effects;
		}
		catch (ReflectiveOperationException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed reading effects from effect list class " + effectList.getClass().getName() + ": " + e.getMessage());
			return List.of();
		}
	}

	private static Object getEffectList(Creature creature)
	{
		if (creature == null)
		{
			return null;
		}

		try
		{
			return CREATURE_GET_EFFECT_LIST.invoke(creature);
		}
		catch (ReflectiveOperationException e)
		{
			LOGGER.warning(() -> "MobiusTierAEffectAccess: Failed resolving effect list for creature class " + creature.getClass().getName() + ": " + e.getMessage());
			return null;
		}
	}

	private static EffectListBridge getBridge(Object effectList)
	{
		return BRIDGES.computeIfAbsent(effectList.getClass(), MobiusTierAEffectAccess::createBridge);
	}

	private static EffectListBridge createBridge(Class<?> effectListClass)
	{
		try
		{
			return new EffectListBridge(
				effectListClass.getMethod("getBuffInfoBySkillId", int.class),
				effectListClass.getMethod("getFirstBuffInfoByAbnormalType", AbnormalType.class),
				effectListClass.getMethod("hasAbnormalVisualEffect", AbnormalVisualEffect.class),
				effectListClass.getMethod("stopAbnormalVisualEffect", AbnormalVisualEffect[].class),
				effectListClass.getMethod("getEffects"));
		}
		catch (NoSuchMethodException e)
		{
			throw new IllegalStateException("Effect list compatibility bridge failed for class " + effectListClass.getName(), e);
		}
	}

	private static Method resolveCreatureGetEffectList()
	{
		try
		{
			return Creature.class.getMethod("getEffectList");
		}
		catch (NoSuchMethodException e)
		{
			throw new IllegalStateException("Creature.getEffectList compatibility seam is unavailable.", e);
		}
	}

	private static final class EffectListBridge
	{
		private final Method getBuffInfoBySkillId;
		private final Method getFirstBuffInfoByAbnormalType;
		private final Method hasAbnormalVisualEffect;
		private final Method stopAbnormalVisualEffect;
		private final Method getEffects;

		private EffectListBridge(Method getBuffInfoBySkillId, Method getFirstBuffInfoByAbnormalType, Method hasAbnormalVisualEffect, Method stopAbnormalVisualEffect, Method getEffects)
		{
			this.getBuffInfoBySkillId = getBuffInfoBySkillId;
			this.getFirstBuffInfoByAbnormalType = getFirstBuffInfoByAbnormalType;
			this.hasAbnormalVisualEffect = hasAbnormalVisualEffect;
			this.stopAbnormalVisualEffect = stopAbnormalVisualEffect;
			this.getEffects = getEffects;
		}
	}
}
