package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bridges SkillLearn access across Mobius variants where the holder moved
 * packages but its accessor methods remained stable.
 */
public final class MobiusTierASkillLearnAccess
{
	private static final Logger LOGGER = Logger.getLogger(MobiusTierASkillLearnAccess.class.getName());
	private static final Map<Class<?>, SkillLearnBridge> BRIDGES = new ConcurrentHashMap<>();

	private MobiusTierASkillLearnAccess()
	{
	}

	public static int getAcquireLevel(Object skillLearn)
	{
		return invokeInt(skillLearn, bridge -> bridge.getAcquireLevel);
	}

	public static boolean isLearnedByFS(Object skillLearn)
	{
		return invokeBoolean(skillLearn, bridge -> bridge.isLearnedByFS);
	}

	public static boolean isResidentialSkill(Object skillLearn)
	{
		return invokeBoolean(skillLearn, bridge -> bridge.isResidentialSkill);
	}

	public static int getSkillId(Object skillLearn)
	{
		return invokeInt(skillLearn, bridge -> bridge.getSkillId);
	}

	public static int getSkillLevel(Object skillLearn)
	{
		return invokeInt(skillLearn, bridge -> bridge.getSkillLevel);
	}

	private static int invokeInt(Object skillLearn, MethodSelector selector)
	{
		if (skillLearn == null)
		{
			return 0;
		}

		try
		{
			return ((Number) selector.select(getBridge(skillLearn)).invoke(skillLearn)).intValue();
		}
		catch (ReflectiveOperationException | ClassCastException e)
		{
			LOGGER.warning(() -> "MobiusTierASkillLearnAccess: Failed reading int from skill learn class " + skillLearn.getClass().getName() + ": " + e.getMessage());
			return 0;
		}
	}

	private static boolean invokeBoolean(Object skillLearn, MethodSelector selector)
	{
		if (skillLearn == null)
		{
			return false;
		}

		try
		{
			return Boolean.TRUE.equals(selector.select(getBridge(skillLearn)).invoke(skillLearn));
		}
		catch (ReflectiveOperationException e)
		{
			LOGGER.warning(() -> "MobiusTierASkillLearnAccess: Failed reading boolean from skill learn class " + skillLearn.getClass().getName() + ": " + e.getMessage());
			return false;
		}
	}

	private static SkillLearnBridge getBridge(Object skillLearn)
	{
		return BRIDGES.computeIfAbsent(skillLearn.getClass(), MobiusTierASkillLearnAccess::createBridge);
	}

	private static SkillLearnBridge createBridge(Class<?> skillLearnClass)
	{
		try
		{
			return new SkillLearnBridge(
				skillLearnClass.getMethod("getGetLevel"),
				skillLearnClass.getMethod("isLearnedByFS"),
				skillLearnClass.getMethod("isResidencialSkill"),
				skillLearnClass.getMethod("getSkillId"),
				skillLearnClass.getMethod("getSkillLevel"));
		}
		catch (NoSuchMethodException e)
		{
			throw new IllegalStateException("SkillLearn compatibility bridge failed for class " + skillLearnClass.getName(), e);
		}
	}

	@FunctionalInterface
	private interface MethodSelector
	{
		Method select(SkillLearnBridge bridge);
	}

	private static final class SkillLearnBridge
	{
		private final Method getAcquireLevel;
		private final Method isLearnedByFS;
		private final Method isResidentialSkill;
		private final Method getSkillId;
		private final Method getSkillLevel;

		private SkillLearnBridge(Method getAcquireLevel, Method isLearnedByFS, Method isResidentialSkill, Method getSkillId, Method getSkillLevel)
		{
			this.getAcquireLevel = getAcquireLevel;
			this.isLearnedByFS = isLearnedByFS;
			this.isResidentialSkill = isResidentialSkill;
			this.getSkillId = getSkillId;
			this.getSkillLevel = getSkillLevel;
		}
	}
}
