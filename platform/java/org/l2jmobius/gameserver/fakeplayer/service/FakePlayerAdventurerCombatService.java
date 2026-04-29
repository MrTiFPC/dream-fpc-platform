package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.fakeplayer.FakePlayerVariableKey;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDebugCategory;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcGearSet;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerNavigationFacade;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.WorldRegion;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.SkillCaster;
import org.l2jmobius.gameserver.model.skill.SkillCastingType;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.zone.ZoneId;

public class FakePlayerAdventurerCombatService
{
	private static final int FLAME_BURST_SKILL_ID = 45453;
	private static final int BLOODY_STRIKE_SKILL_ID = 45288;
	private static final int TRANSCENDENT_BLOODY_STRIKE_SKILL_ID = 45289;
	private static final int STEAL_ESSENCE_SKILL_ID = 1245;
	private static final int TRANSCENDENT_STEAL_ESSENCE_SKILL_ID = 45225;
	private static final int HURRICANE_SKILL_ID = 1239;
	private static final int TRANSCENDENT_HURRICANE_SKILL_ID = 45220;
	private static final int THUNDER_EXPLOSION_SKILL_ID = 45380;
	private static final int TRANSCENDENT_THUNDER_EXPLOSION_SKILL_ID = 47438;
	private static final Set<TargetType> SELF_BUFF_TARGET_TYPES = Set.of(TargetType.SELF, TargetType.TARGET_OR_SELF);
	private static final Set<TargetType> NON_OFFENSIVE_TARGET_TYPES = Set.of(TargetType.SELF, TargetType.TARGET_OR_SELF, TargetType.MY_PARTY, TargetType.NONE, TargetType.GROUND);
	private static final Set<TargetType> DIRECT_HOSTILE_TARGET_TYPES = Set.of(TargetType.ENEMY, TargetType.ENEMY_ONLY, TargetType.TARGET);
	private static final List<String> MAGIC_HINTS = List.of("mage", "spell", "wizard", "sorcer", "singer", "elder", "bishop", "oracle", "warlock", "summoner", "necromancer", "mystic", "archmage", "soultaker", "soul taker", "mystic muse", "storm screamer", "spellhowler", "spellsinger", "cardinal", "hierophant", "eva saint", "eva's saint", "shillien saint", "overlord", "dominator", "warcryer", "doomcryer", "orc shaman");
	private static final List<String> SUMMONER_HINTS = List.of("summon", "warlock", "arcana", "elemental", "phantom", "spectral", "servitor", "cubic");
	private static final List<String> FRONTLINE_HINTS = List.of("fighter", "frontline", "melee", "skirmish", "knight", "warrior", "assassin", "hunter", "bounty", "scout", "rogue", "salvager");

	private final FakePlayerConfig _config;
	private final FakePlayerDebugService _debugService;
	private final FakePlayerBuffStateService _buffStateService;
	private final FakePlayerWorldFacade _worldFacade;
	private final FakePlayerNavigationFacade _navigationFacade;
	private final ConcurrentHashMap<String, Long> _lastOffensiveSkillUseTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> _lastPreparationSkillUseTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Integer> _lastOffensiveSkillIds = new ConcurrentHashMap<>();

	public FakePlayerAdventurerCombatService(FakePlayerConfig config, FakePlayerDebugService debugService, FakePlayerBuffStateService buffStateService, FakePlayerWorldFacade worldFacade, FakePlayerNavigationFacade navigationFacade)
	{
		_config = config;
		_debugService = debugService;
		_buffStateService = buffStateService;
		_worldFacade = worldFacade;
		_navigationFacade = navigationFacade;
	}

	public boolean tryUsePreparationSkill(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower)
	{
		if ((definition == null) || (npc == null) || (combatPower == null) || npc.isDead() || npc.isCastingNow() || npc.isAttackingNow() || !isPreparationSkillCadenceReady(definition.getId()))
		{
			return false;
		}

		final boolean silentRestore = shouldSilentlyRestorePreparationSkill(npc);
		for (Skill skill : resolveSelfBuffSkills(combatPower))
		{
			if (!(silentRestore ? shouldMaintainSelfBuffState(npc, skill) : canUseSelfBuff(npc, skill)))
			{
				continue;
			}

			if (silentRestore)
			{
				skill.applyEffects(npc, npc);
				markPreparationSkillUsed(definition.getId());
				_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=self_buff_restore skill=" + skill.getName() + "(" + skill.getId() + ")");
				return true;
			}

			// Cast upkeep directly from the AFPC seam so it does not depend on sleeping mob AI.
			if (npc.isMoving())
			{
				npc.stopMove(null);
			}
			if (SkillCaster.castSkill(npc, npc, skill, null, SkillCastingType.SIMULTANEOUS, false, false) == null)
			{
				continue;
			}
			markPreparationSkillUsed(definition.getId());
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=self_buff_direct skill=" + skill.getName() + "(" + skill.getId() + ")");
			return true;
		}
		return false;
	}

	public boolean maintainOffscreenPreparationState(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower)
	{
		if ((definition == null) || (npc == null) || (combatPower == null) || npc.isDead() || !shouldSilentlyRestorePreparationSkill(npc))
		{
			return false;
		}

		boolean restored = false;
		for (Skill skill : resolveSelfBuffSkills(combatPower))
		{
			if (!shouldMaintainSelfBuffState(npc, skill))
			{
				continue;
			}

			skill.applyEffects(npc, npc);
			restored = true;
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=self_buff_offscreen_restore skill=" + skill.getName() + "(" + skill.getId() + ")");
		}

		if (restored)
		{
			markPreparationSkillUsed(definition.getId());
		}
		return restored;
	}

	public boolean tryUseOffensiveSkill(FpcDefinition definition, Attackable npc, Monster target, ResolvedFpcCombatPower combatPower)
	{
		return tryUseOffensiveSkill(definition, npc, target, combatPower, "default");
	}

	public boolean tryUseOffensiveSkill(FpcDefinition definition, Attackable npc, Monster target, ResolvedFpcCombatPower combatPower, String context)
	{
		return tryUseOffensiveSkill(definition, npc, (Creature) target, combatPower, context);
	}

	public boolean tryUseOffensiveSkill(FpcDefinition definition, Attackable npc, Player target, ResolvedFpcCombatPower combatPower)
	{
		return tryUseOffensiveSkill(definition, npc, target, combatPower, "default");
	}

	public boolean tryUseOffensiveSkill(FpcDefinition definition, Attackable npc, Player target, ResolvedFpcCombatPower combatPower, String context)
	{
		return tryUseOffensiveSkill(definition, npc, (Creature) target, combatPower, context);
	}

	public boolean tryUseChaseSkill(FpcDefinition definition, Attackable npc, Creature target, ResolvedFpcCombatPower combatPower, String context)
	{
		if ((definition == null) || (npc == null) || (target == null) || (combatPower == null) || npc.isDead() || target.isDead() || npc.isCastingNow() || npc.isAttackingNow() || !isCombatSkillCadenceReady(definition.getId(), npc))
		{
			return false;
		}
		
		final CombatRole role = resolveCombatRole(definition, combatPower);
		if (role != CombatRole.FIGHTER)
		{
			return false;
		}
		
		final long distanceSq = distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY());
		if (distanceSq < 22500L)
		{
			return false;
		}
		
		for (Skill skill : resolveChaseSkills(npc, target, combatPower))
		{
			final int castRange = Math.max(skill.getCastRange(), 0);
			if ((castRange > 0) && (distanceSq > ((long) (castRange + 120) * (castRange + 120))))
			{
				continue;
			}
			
			final WorldObject castTarget = resolveOffensiveCastTarget(npc, target, skill);
			if (castTarget == null)
			{
				continue;
			}
			
			if (npc.isMoving())
			{
				npc.stopMove(null);
			}
			npc.setTarget(target);
			if (SkillCaster.castSkill(npc, castTarget, skill, null, SkillCastingType.NORMAL, true, false) == null)
			{
				continue;
			}
			markOffensiveSkillUsed(definition.getId(), skill.getId());
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=cast_chase role=fighter skill=" + skill.getName() + "(" + skill.getId() + ") target=" + target.getName() + "(" + target.getId() + ") context=" + safe(context));
			return true;
		}
		return false;
	}

	public boolean tryUseSupportSkill(FpcDefinition definition, Attackable npc, ResolvedFpcCombatPower combatPower, int skillId)
	{
		if ((definition == null) || (npc == null) || (combatPower == null) || npc.isDead() || npc.isCastingNow())
		{
			return false;
		}

		for (Skill skill : combatPower.getActiveSkills())
		{
			if ((skill == null) || (skill.getId() != skillId) || !isSupportSkill(skill) || npc.isSkillDisabled(skill) || !skill.checkCondition(npc, npc, false))
			{
				continue;
			}

			if (npc.isMoving())
			{
				npc.stopMove(null);
			}
			if (SkillCaster.castSkill(npc, npc, skill, null, SkillCastingType.SIMULTANEOUS, false, false) == null)
			{
				continue;
			}
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=cast_support skill=" + skill.getName() + "(" + skill.getId() + ")");
			return true;
		}
		return false;
	}

	public boolean tryUseOffensiveSkill(FpcDefinition definition, Attackable npc, Creature target, ResolvedFpcCombatPower combatPower, String context)
	{
		if ((definition == null) || (npc == null) || (target == null) || (combatPower == null) || npc.isDead() || target.isDead() || npc.isCastingNow() || !isCombatSkillCadenceReady(definition.getId(), npc))
		{
			return false;
		}

		final CombatRole role = resolveCombatRole(definition, combatPower);
		final List<Skill> offensiveSkills = resolveOffensiveSkills(definition, npc, target, combatPower, role, context);
		int disabledCount = 0;
		int lineOfSightCount = 0;
		int alreadyAffectedCount = 0;
		int conditionFailedCount = 0;
		int targetResolveCount = 0;
		String sampleFailures = "";
		for (Skill skill : offensiveSkills)
		{
			final OffensiveCastResolution resolution = resolveOffensiveCastResolution(npc, target, skill);
			final WorldObject castTarget = resolution._castTarget;
			if (castTarget == null)
			{
				switch (resolution._failureReason)
				{
					case "skill_disabled":
						disabledCount++;
						break;
					case "no_line_of_sight":
						lineOfSightCount++;
						break;
					case "already_affected":
						alreadyAffectedCount++;
						break;
					case "condition_failed":
						conditionFailedCount++;
						break;
					case "target_resolution_failed":
						targetResolveCount++;
						break;
				}
				if (sampleFailures.isBlank())
				{
					sampleFailures = skill.getName() + "(" + skill.getId() + ")=" + resolution._failureReason;
				}
				continue;
			}

			if (npc.isMoving())
			{
				npc.stopMove(null);
			}
			npc.setTarget(target);
			if (SkillCaster.castSkill(npc, castTarget, skill, null, SkillCastingType.NORMAL, true, false) == null)
			{
				continue;
			}
			markOffensiveSkillUsed(definition.getId(), skill.getId());
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=cast_offensive role=" + role.name().toLowerCase(Locale.ENGLISH) + " skill=" + skill.getName() + "(" + skill.getId() + ") target=" + target.getName() + "(" + target.getId() + ")");
			return true;
		}
		final long distanceSq = distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY());
		final int desiredRange = resolvePreferredOffensiveRange(offensiveSkills, role);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=no_offensive_cast_target role=" + role.name().toLowerCase(Locale.ENGLISH) + " skillCount=" + offensiveSkills.size() + " target=" + target.getName() + "(" + target.getId() + ") context=" + safe(context) + " currentDistance=" + (int) Math.sqrt(distanceSq) + " desiredRange=" + desiredRange + " disabled=" + disabledCount + " los=" + lineOfSightCount + " affected=" + alreadyAffectedCount + " condition=" + conditionFailedCount + " targetResolve=" + targetResolveCount + (sampleFailures.isBlank() ? "" : " sample=" + sampleFailures));
		return tryAdvanceIntoOffensiveRange(definition, npc, target, offensiveSkills, role, context);
	}

	private boolean tryAdvanceIntoOffensiveRange(FpcDefinition definition, Attackable npc, Creature target, List<Skill> offensiveSkills, CombatRole role, String context)
	{
		if ((definition == null) || (npc == null) || (target == null) || target.isDead() || (role == CombatRole.FIGHTER))
		{
			return false;
		}

		final int desiredRange = resolvePreferredOffensiveRange(offensiveSkills, role);
		final long distanceSq = distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY());
		if ((desiredRange <= 0) || (distanceSq <= ((long) desiredRange * desiredRange)))
		{
			return false;
		}

		if (npc.isMoving() && (npc.getTarget() == target))
		{
			return true;
		}

		npc.setRunning();
		npc.setTarget(target);
		npc.moveToLocation(target.getX(), target.getY(), target.getZ(), Math.max(60, Math.min(desiredRange, 220)));
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "action=advance_offensive_range role=" + role.name().toLowerCase(Locale.ENGLISH) + " desiredRange=" + desiredRange + " currentDistance=" + (int) Math.sqrt(distanceSq) + " target=" + target.getName() + "(" + target.getId() + ") context=" + safe(context));
		return true;
	}

	private int resolvePreferredOffensiveRange(List<Skill> offensiveSkills, CombatRole role)
	{
		int preferredRange = 0;
		if (offensiveSkills != null)
		{
			for (Skill skill : offensiveSkills)
			{
				if (skill == null)
				{
					continue;
				}

				preferredRange = Math.max(preferredRange, Math.max(skill.getCastRange(), 0));
			}
		}

		if (preferredRange <= 0)
		{
			return (role == CombatRole.SUMMONER) ? 360 : 500;
		}
		if (role == CombatRole.SUMMONER)
		{
			return Math.max(360, preferredRange - 60);
		}
		return Math.max(420, preferredRange - 80);
	}

	public boolean shouldAvoidBasicAttack(FpcDefinition definition, ResolvedFpcCombatPower combatPower)
	{
		if (combatPower == null)
		{
			return false;
		}
		final CombatRole role = resolveCombatRole(definition, combatPower);
		if ((role == CombatRole.MAGE) || (role == CombatRole.SUMMONER))
		{
			return true;
		}
		// Essence combat cadence is skill-driven for most active fighter kits too.
		// If the FPC actually has offensive actives, prefer holding for skills over falling back to plain hits.
		return combatPower.getActiveSkills().stream().anyMatch(this::isOffensiveSkill);
	}

	public boolean shouldAvoidBasicAttack(FpcDefinition definition, ResolvedFpcCombatPower combatPower, Creature target)
	{
		if (!shouldAvoidBasicAttack(definition, combatPower))
		{
			return false;
		}
		// Fakeplayer-vs-fakeplayer PvP still has a rougher skill-target resolution surface than monster farm.
		// Let fighter-style AFPCs commit to physical pressure there instead of freezing in endless spell hold.
		if ((target != null) && target.isFakePlayer() && target.isNpc() && (resolveCombatRole(definition, combatPower) == CombatRole.FIGHTER))
		{
			return false;
		}
		return true;
	}

	public Monster selectAssistTarget(FpcDefinition definition, Attackable npc, FpcAdventurerRoute route, Location farmAnchor, FpcRegistry registry)
	{
		if ((definition == null) || (npc == null) || (route == null) || (farmAnchor == null) || (registry == null) || !definition.getAdventurerProfile().supportsPartyCoordination())
		{
			return null;
		}

		final int coordinationRange = Math.max(_config.getCoordinationRange(), 200);
		Monster selectedTarget = null;
		int selectedWeight = Integer.MIN_VALUE;
		long selectedDistance = Long.MAX_VALUE;
		for (FpcDefinition candidate : registry.getDefinitions())
		{
			if ((candidate == null) || definition.getId().equalsIgnoreCase(candidate.getId()) || !candidate.getAdventurerProfile().supportsPartyCoordination() || !isSameCoordinationGroup(definition, candidate))
			{
				continue;
			}

			final Npc alliedNpc = registry.resolveActiveNpc(candidate.getId());
			if (!(alliedNpc instanceof Attackable) || alliedNpc.isDead())
			{
				continue;
			}

			if (distanceSquared(npc.getX(), npc.getY(), alliedNpc.getX(), alliedNpc.getY()) > ((long) coordinationRange * coordinationRange))
			{
				continue;
			}

			final Monster candidateTarget = resolveAssistableTarget(npc, alliedNpc.asAttackable(), route, farmAnchor);
			if (candidateTarget == null)
			{
				continue;
			}

			final ResolvedFpcCombatPower alliedPower = registry.getActiveCombatPower(candidate.getId());
			final int weight = (alliedPower != null) ? alliedPower.getCombatWeight() : 0;
			final long distance = distanceSquared(npc.getX(), npc.getY(), candidateTarget.getX(), candidateTarget.getY());
			if ((weight > selectedWeight) || ((weight == selectedWeight) && (distance < selectedDistance)))
			{
				selectedTarget = candidateTarget;
				selectedWeight = weight;
				selectedDistance = distance;
			}
		}
		return selectedTarget;
	}

	private Monster resolveAssistableTarget(Attackable requester, Attackable alliedNpc, FpcAdventurerRoute route, Location farmAnchor)
	{
		if ((requester == null) || (alliedNpc == null))
		{
			return null;
		}

		if ((alliedNpc.getTarget() == null) || !alliedNpc.getTarget().isMonster())
		{
			return null;
		}

		final Monster target = alliedNpc.getTarget().asMonster();
		if ((target == null) || target.isDead() || target.isRaid() || target.isQuestMonster() || target.isFakePlayer() || target.isInsideZone(ZoneId.PEACE))
		{
			return null;
		}
		if (target.isInCombat() && (target.getTarget() != alliedNpc) && (target.getTarget() != requester))
		{
			return null;
		}
		if (!_navigationFacade.canSeeTarget(requester, target))
		{
			return null;
		}
		return distanceSquared(target.getX(), target.getY(), farmAnchor.getX(), farmAnchor.getY()) <= ((long) route.getLeashRadius() * route.getLeashRadius()) ? target : null;
	}

	private List<Skill> resolveSelfBuffSkills(ResolvedFpcCombatPower combatPower)
	{
		return combatPower.getActiveSkills().stream().filter(this::isSelfBuffSkill).sorted(Comparator.comparingInt(this::selfBuffPriority).reversed().thenComparingInt(Skill::getId)).toList();
	}

	private List<Skill> resolveOffensiveSkills(FpcDefinition definition, Attackable npc, Creature target, ResolvedFpcCombatPower combatPower, CombatRole role, String context)
	{
		return combatPower.getActiveSkills().stream().filter(this::isOffensiveSkill).sorted(Comparator.comparingInt((Skill skill) -> offensivePriority(definition, npc, target, combatPower, skill, role, context)).reversed().thenComparingInt(Skill::getId)).toList();
	}
	
	private List<Skill> resolveChaseSkills(Attackable npc, Creature target, ResolvedFpcCombatPower combatPower)
	{
		return combatPower.getActiveSkills().stream().filter(this::isChaseSkill).sorted(Comparator.comparingInt((Skill skill) -> chaseSkillPriority(npc, target, skill)).reversed().thenComparingInt(Skill::getId)).toList();
	}

	private boolean isSelfBuffSkill(Skill skill)
	{
		if ((skill == null) || skill.isPassive() || !skill.isActive() || skill.isDebuff() || skill.isTransformation() || skill.isTriggeredSkill() || skill.isSuicideAttack() || skill.isHeroSkill() || skill.isGMSkill() || skill.isChanneling() || skill.isHealingPotionSkill())
		{
			return false;
		}
		if (!SELF_BUFF_TARGET_TYPES.contains(skill.getTargetType()))
		{
			return false;
		}
		if (skill.hasEffectType(EffectType.DISPEL, EffectType.DISPEL_BY_SLOT, EffectType.HEAL, EffectType.CPHEAL, EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT))
		{
			return false;
		}
		if ("bandage".equalsIgnoreCase(skill.getName()))
		{
			return false;
		}
		if (!skill.isContinuous() && !skill.isToggle())
		{
			return false;
		}
		return skill.hasEffects(EffectScope.SELF) || skill.hasEffects(EffectScope.GENERAL);
	}

	private boolean isOffensiveSkill(Skill skill)
	{
		if ((skill == null) || skill.isPassive() || !skill.isActive() || skill.isTransformation() || skill.isTriggeredSkill() || skill.isSuicideAttack() || skill.isHeroSkill() || skill.isGMSkill() || skill.isChanneling() || skill.isHealingPotionSkill())
		{
			return false;
		}
		if (NON_OFFENSIVE_TARGET_TYPES.contains(skill.getTargetType()))
		{
			return false;
		}
		if (skill.hasEffectType(EffectType.HEAL, EffectType.CPHEAL, EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT, EffectType.REBALANCE_HP, EffectType.RESURRECTION, EffectType.RESURRECTION_SPECIAL, EffectType.REFUEL_AIRSHIP, EffectType.SUMMON, EffectType.SUMMON_PET, EffectType.SUMMON_NPC))
		{
			return false;
		}
		return skill.isDebuff() || skill.hasNegativeEffect() || (skill.getEffectPoint() < 0) || skill.hasEffectType(EffectType.HP_DRAIN, EffectType.MAGICAL_ATTACK, EffectType.PHYSICAL_ATTACK, EffectType.PHYSICAL_ATTACK_HP_LINK, EffectType.DEATH_LINK, EffectType.DMG_OVER_TIME, EffectType.DMG_OVER_TIME_PERCENT, EffectType.MAGICAL_DMG_OVER_TIME, EffectType.LETHAL_ATTACK, EffectType.DISPEL, EffectType.DISPEL_BY_SLOT, EffectType.ROOT, EffectType.SLEEP, EffectType.MUTE, EffectType.BLOCK_ACTIONS, EffectType.BLOCK_CONTROL, EffectType.HATE, EffectType.STEAL_ABNORMAL);
	}
	
	private boolean isChaseSkill(Skill skill)
	{
		if (!isOffensiveSkill(skill) || !skill.isFlyType())
		{
			return false;
		}
		
		final String skillName = safe(skill.getName()).toLowerCase(Locale.ENGLISH);
		return skillName.contains("rush") || skillName.contains("charge") || skillName.contains("dash") || skillName.contains("assault");
	}

	private boolean isSupportSkill(Skill skill)
	{
		if ((skill == null) || skill.isPassive() || !skill.isActive() || skill.isTransformation() || skill.isTriggeredSkill() || skill.isSuicideAttack() || skill.isHeroSkill() || skill.isGMSkill() || skill.isChanneling() || skill.isHealingPotionSkill())
		{
			return false;
		}
		return skill.hasEffectType(EffectType.HEAL, EffectType.CPHEAL, EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT);
	}

	private boolean canUseSelfBuff(Attackable npc, Skill skill)
	{
		return (_buffStateService != null) && _buffStateService.shouldRefreshSelfBuff(npc, skill);
	}

	private boolean shouldMaintainSelfBuffState(Attackable npc, Skill skill)
	{
		return (_buffStateService != null) && _buffStateService.shouldMaintainSelfBuff(npc, skill);
	}

	private boolean shouldSilentlyRestorePreparationSkill(Attackable npc)
	{
		if (npc == null)
		{
			return false;
		}

		final WorldRegion region = npc.getWorldRegion();
		return (region == null) || !region.areNeighborsActive();
	}

	private OffensiveCastResolution resolveOffensiveCastResolution(Attackable npc, Creature target, Skill skill)
	{
		if ((skill == null) || (target == null) || target.isDead() || target.isInsideZone(ZoneId.PEACE) || target.isInsideZone(ZoneId.NO_PVP) || npc.isSkillDisabled(skill))
		{
			return new OffensiveCastResolution(null, "skill_disabled");
		}
		if (!_navigationFacade.canSeeTarget(npc, target))
		{
			return new OffensiveCastResolution(null, "no_line_of_sight");
		}
		if (skill.isDebuff() && target.isAffectedBySkill(skill.getId()))
		{
			return new OffensiveCastResolution(null, "already_affected");
		}
		if (!skill.checkCondition(npc, target, false))
		{
			return new OffensiveCastResolution(null, "condition_failed");
		}
		WorldObject castTarget = skill.getTarget(npc, target, false, false, false);
		if ((castTarget == null) && DIRECT_HOSTILE_TARGET_TYPES.contains(skill.getTargetType()))
		{
			castTarget = skill.getTarget(npc, target, true, false, false);
		}
		if (castTarget == null)
		{
			castTarget = resolveDirectHostileFallbackTarget(npc, target, skill);
		}
		return new OffensiveCastResolution(castTarget, (castTarget != null) ? "" : "target_resolution_failed");
	}

	private WorldObject resolveDirectHostileFallbackTarget(Attackable npc, Creature target, Skill skill)
	{
		if ((npc == null) || (target == null) || (skill == null) || target.isDead() || !DIRECT_HOSTILE_TARGET_TYPES.contains(skill.getTargetType()))
		{
			return null;
		}

		// Some hostile direct-target mage skills refuse NPC-backed casters during target-handler resolution even though
		// line of sight, range, and skill conditions are already satisfied. Reuse the validated hostile target instead.
		if (target.isMonster() || target.isAutoAttackable(npc))
		{
			return target;
		}
		return null;
	}

	private WorldObject resolveOffensiveCastTarget(Attackable npc, Creature target, Skill skill)
	{
		return resolveOffensiveCastResolution(npc, target, skill)._castTarget;
	}

	private CombatRole resolveCombatRole(FpcDefinition definition, ResolvedFpcCombatPower combatPower)
	{
		final PlayerClass playerClass = combatPower.getResolvedPlayerClass();
		if (playerClass != null)
		{
			if (playerClass.isSummoner())
			{
				return CombatRole.SUMMONER;
			}
			if (playerClass.isMage())
			{
				return CombatRole.MAGE;
			}
			return CombatRole.FIGHTER;
		}

		final String roleText = (combatPower.getResolvedPlayerClassName() + " " + combatPower.getResolvedLoadoutProfile() + " " + combatPower.getResolvedCombatProfile() + " " + safe((definition != null) ? definition.getArchetype() : "")).toLowerCase(Locale.ENGLISH);
		if (containsAny(roleText, SUMMONER_HINTS))
		{
			return CombatRole.SUMMONER;
		}
		if (containsAny(roleText, MAGIC_HINTS))
		{
			return CombatRole.MAGE;
		}
		if (containsAny(roleText, FRONTLINE_HINTS))
		{
			return CombatRole.FIGHTER;
		}

		int magicSkills = 0;
		int physicalSkills = 0;
		for (Skill skill : combatPower.getActiveSkills())
		{
			if (isSelfBuffSkill(skill))
			{
				continue;
			}
			if (skill.isMagic())
			{
				magicSkills++;
			}
			else
			{
				physicalSkills++;
			}
		}
		return (magicSkills >= physicalSkills) ? CombatRole.MAGE : CombatRole.FIGHTER;
	}

	private int selfBuffPriority(Skill skill)
	{
		int score = 0;
		if (skill.getTargetType() == TargetType.SELF)
		{
			score += 30;
		}
		if (skill.isToggle())
		{
			score += 18;
		}
		if (skill.isContinuous())
		{
			score += 12;
		}
		if (skill.hasEffects(EffectScope.SELF))
		{
			score += 8;
		}
		if (skill.hasEffects(EffectScope.GENERAL))
		{
			score += 5;
		}
		return score;
	}

	private int offensivePriority(FpcDefinition definition, Attackable npc, Creature target, ResolvedFpcCombatPower combatPower, Skill skill, CombatRole role, String context)
	{
		int score = 0;
		final int nearbyTargetCount = countNearbyTargets(npc, target, skill);
		switch (role)
		{
			case MAGE:
			{
				score += skill.isMagic() ? 34 : -14;
				score += Math.max(skill.getCastRange(), 0) / 18;
				break;
			}
			case SUMMONER:
			{
				score += skill.isMagic() ? 30 : -8;
				score += Math.max(skill.getCastRange(), 0) / 20;
				break;
			}
			case FIGHTER:
			{
				score += skill.isMagic() ? 6 : 30;
				score += (skill.getCastRange() <= 150) ? 10 : 2;
				break;
			}
		}
		if (skill.isAOE())
		{
			score += (nearbyTargetCount >= 2) ? (12 + Math.min((nearbyTargetCount - 1) * 10, 28)) : -18;
		}
		else
		{
			score += (nearbyTargetCount <= 1) ? 16 : 4;
		}
		if (skill.isDebuff())
		{
			score += 10;
		}
		score += skill.hasNegativeEffect() ? 6 : 0;
		if (!skill.isContinuous())
		{
			score += 6;
		}
		score += Math.min(Math.abs(Math.min(skill.getEffectPoint(), 0)) / 12, 40);
		score += Math.min(Math.max(skill.getMagicLevel(), 0) * 3, 18);
		score += Math.max(0, 18 - (skill.getReuseDelay() / 1000));
		score += Math.max(0, 10 - (skill.getHitTime() / 400));
		score -= Math.min(Math.max(skill.getMpConsume(), 0) / 18, 10);
		score += globalCooldownRuleBias(skill);
		score += offensiveProfileBias(definition, combatPower, role, skill);
		score += standardCombatPolicyBias(combatPower, target, skill, context);
		score -= repeatedSkillPenalty(definition, skill);
		return score;
	}
	
	private int chaseSkillPriority(Attackable npc, Creature target, Skill skill)
	{
		if ((npc == null) || (target == null) || (skill == null))
		{
			return 0;
		}
		
		int score = 120;
		final String skillName = safe(skill.getName()).toLowerCase(Locale.ENGLISH);
		if (skillName.contains("rush"))
		{
			score += 36;
		}
		if (skillName.contains("charge"))
		{
			score += 28;
		}
		if (skillName.contains("dash") || skillName.contains("assault"))
		{
			score += 18;
		}
		score += Math.min(Math.max(skill.getCastRange(), 0) / 10, 70);
		score += skill.isDebuff() ? 18 : 0;
		score += skill.hasNegativeEffect() ? 8 : 0;
		score += Math.max(0, 12 - (skill.getHitTime() / 400));
		score -= Math.min(Math.max(skill.getReuseDelay(), 0) / 1000, 25);
		
		final long distanceSq = distanceSquared(npc.getX(), npc.getY(), target.getX(), target.getY());
		final int castRange = Math.max(skill.getCastRange(), 0);
		if ((castRange > 0) && (distanceSq <= ((long) castRange * castRange)))
		{
			score += 20;
		}
		return score;
	}

	private int globalCooldownRuleBias(Skill skill)
	{
		if (skill == null)
		{
			return 0;
		}

		final int reuseDelay = Math.max(skill.getReuseDelay(), 0);
		final int hitTime = Math.max(skill.getHitTime(), 0);
		int score = 0;
		if (reuseDelay <= 1000)
		{
			score += 12;
		}
		else if (reuseDelay <= 2500)
		{
			score += 7;
		}
		else if (reuseDelay <= 5000)
		{
			score += 2;
		}
		else
		{
			score -= Math.min((reuseDelay - 5000) / 500, 20);
		}

		if (hitTime <= 1500)
		{
			score += 10;
		}
		else if (hitTime <= 2500)
		{
			score += 4;
		}
		else if (hitTime >= 3500)
		{
			score -= Math.min((hitTime - 3000) / 200, 8);
		}
		return score;
	}

	private int countNearbyTargets(Attackable npc, Creature target, Skill skill)
	{
		if ((npc == null) || (target == null) || target.isDead())
		{
			return 0;
		}

		final int aoeRange = (skill != null) ? Math.max(skill.getAffectRange(), 0) : 0;
		final int searchRange = Math.max(Math.min((aoeRange > 0) ? aoeRange : 220, 600), 140);
		if (target instanceof Monster targetMonster)
		{
			int count = 1;
			for (Monster nearby : _worldFacade.getVisibleObjectsInRange(targetMonster, Monster.class, searchRange))
			{
				if ((nearby == null) || (nearby == targetMonster) || nearby.isDead() || nearby.isRaid() || nearby.isQuestMonster() || nearby.isFakePlayer() || nearby.isInsideZone(ZoneId.PEACE))
				{
					continue;
				}
				if (!_navigationFacade.canSeeTarget(npc, nearby))
				{
					continue;
				}
				count++;
			}
			return count;
		}

		if (target instanceof Player targetPlayer)
		{
			int count = 1;
			for (Player nearby : _worldFacade.getVisibleObjectsInRange(targetPlayer, Player.class, searchRange))
			{
				if ((nearby == null) || (nearby == targetPlayer) || nearby.isDead() || nearby.isAlikeDead() || nearby.isInvisible() || !nearby.isTargetable() || nearby.isInsideZone(ZoneId.PEACE) || nearby.isInsideZone(ZoneId.NO_PVP))
				{
					continue;
				}
				if (!nearby.isAutoAttackable(npc) || !_navigationFacade.canSeeTarget(npc, nearby))
				{
					continue;
				}
				count++;
			}
			return count;
		}
		if (target instanceof Npc targetNpc)
		{
			int count = 1;
			for (Npc nearby : _worldFacade.getVisibleObjectsInRange(targetNpc, Npc.class, searchRange))
			{
				if ((nearby == null) || (nearby == targetNpc) || !nearby.isFakePlayer() || nearby.isDead() || !nearby.isTargetable() || nearby.isInsideZone(ZoneId.PEACE) || nearby.isInsideZone(ZoneId.NO_PVP))
				{
					continue;
				}
				if (!nearby.isAutoAttackable(npc) || !_navigationFacade.canSeeTarget(npc, nearby))
				{
					continue;
				}
				count++;
			}
			return count;
		}
		return 1;
	}

	private int repeatedSkillPenalty(FpcDefinition definition, Skill skill)
	{
		if ((definition == null) || (skill == null))
		{
			return 0;
		}

		final String actorKey = normalizeActorKey(definition.getId());
		final Integer lastSkillId = _lastOffensiveSkillIds.get(actorKey);
		if ((lastSkillId == null) || (lastSkillId.intValue() != skill.getId()))
		{
			return 0;
		}

		final Long lastUse = _lastOffensiveSkillUseTimes.get(actorKey);
		final long now = System.currentTimeMillis();
		final long sinceLastUse = (lastUse == null) ? Long.MAX_VALUE : (now - lastUse.longValue());
		final long recentWindow = Math.max(1000L, Math.min(Math.max(skill.getReuseDelay(), 0) + (Math.max(skill.getHitTime(), 0) / 2L), 6000L));
		if (sinceLastUse >= recentWindow)
		{
			return 0;
		}

		int penalty = 20;
		penalty += Math.min(Math.max(skill.getReuseDelay(), 0) / 250, 18);
		penalty += Math.min(Math.max(skill.getHitTime(), 0) / 250, 16);
		penalty += (int) Math.min((recentWindow - sinceLastUse) / 250L, 20L);
		return penalty;
	}

	private int standardCombatPolicyBias(ResolvedFpcCombatPower combatPower, Creature target, Skill skill, String context)
	{
		if ((combatPower == null) || (target == null) || (skill == null))
		{
			return 0;
		}

		final CombatScene scene = resolveCombatScene(target, context);
		return switch (resolveStandardCombatPolicy(combatPower))
		{
			case STORM_SCREAMER -> stormScreamerPolicyBias(skill, scene);
			case DOMINATOR -> dominatorPolicyBias(skill, scene);
			case NONE -> 0;
		};
	}

	private StandardCombatPolicy resolveStandardCombatPolicy(ResolvedFpcCombatPower combatPower)
	{
		if (combatPower == null)
		{
			return StandardCombatPolicy.NONE;
		}

		final PlayerClass playerClass = combatPower.getResolvedPlayerClass();
		if (playerClass == PlayerClass.STORM_SCREAMER)
		{
			return StandardCombatPolicy.STORM_SCREAMER;
		}
		if (playerClass == PlayerClass.DOMINATOR)
		{
			return StandardCombatPolicy.DOMINATOR;
		}

		final String playerClassName = safe(combatPower.getResolvedPlayerClassName()).toLowerCase(Locale.ENGLISH);
		if (playerClassName.contains("storm screamer"))
		{
			return StandardCombatPolicy.STORM_SCREAMER;
		}
		if (playerClassName.contains("dominator"))
		{
			return StandardCombatPolicy.DOMINATOR;
		}
		return StandardCombatPolicy.NONE;
	}

	private CombatScene resolveCombatScene(Creature target, String context)
	{
		if ((target instanceof Player) || (target instanceof Npc npcTarget && npcTarget.isFakePlayer()))
		{
			return CombatScene.PVP;
		}
		return CombatScene.FARM;
	}

	private int stormScreamerPolicyBias(Skill skill, CombatScene scene)
	{
		if (isStormScreamerPrimarySkill(skill))
		{
			return (scene == CombatScene.PVP) ? 340 : 360;
		}
		if (isStormScreamerSecondarySkill(skill))
		{
			return (scene == CombatScene.PVP) ? 250 : 220;
		}
		return skill.isMagic() ? -35 : -160;
	}

	private int dominatorPolicyBias(Skill skill, CombatScene scene)
	{
		if (isDominatorPrimarySkill(skill))
		{
			return (scene == CombatScene.PVP) ? 340 : 360;
		}
		if (isDominatorSecondarySkill(skill))
		{
			return (scene == CombatScene.PVP) ? 230 : 240;
		}
		if (isDominatorTertiarySkill(skill))
		{
			return (scene == CombatScene.PVP) ? 140 : 170;
		}
		return skill.isMagic() ? -45 : -160;
	}

	private boolean isStormScreamerPrimarySkill(Skill skill)
	{
		return (skill != null) && ((skill.getId() == THUNDER_EXPLOSION_SKILL_ID) || (skill.getId() == TRANSCENDENT_THUNDER_EXPLOSION_SKILL_ID));
	}

	private boolean isStormScreamerSecondarySkill(Skill skill)
	{
		return (skill != null) && ((skill.getId() == HURRICANE_SKILL_ID) || (skill.getId() == TRANSCENDENT_HURRICANE_SKILL_ID));
	}

	private boolean isDominatorPrimarySkill(Skill skill)
	{
		return (skill != null) && ((skill.getId() == BLOODY_STRIKE_SKILL_ID) || (skill.getId() == TRANSCENDENT_BLOODY_STRIKE_SKILL_ID));
	}

	private boolean isDominatorSecondarySkill(Skill skill)
	{
		return (skill != null) && (skill.getId() == FLAME_BURST_SKILL_ID);
	}

	private boolean isDominatorTertiarySkill(Skill skill)
	{
		return (skill != null) && ((skill.getId() == STEAL_ESSENCE_SKILL_ID) || (skill.getId() == TRANSCENDENT_STEAL_ESSENCE_SKILL_ID));
	}

	private int offensiveProfileBias(FpcDefinition definition, ResolvedFpcCombatPower combatPower, CombatRole role, Skill skill)
	{
		final String profileText = (combatPower.getResolvedLoadoutProfile() + " " + combatPower.getResolvedCombatProfile() + " " + safe((definition != null) ? definition.getArchetype() : "")).toLowerCase(Locale.ENGLISH);
		int score = 0;
		if ((role == CombatRole.FIGHTER) && containsAny(profileText, FRONTLINE_HINTS))
		{
			score += skill.isMagic() ? -4 : 8;
		}
		if ((role != CombatRole.FIGHTER) && containsAny(profileText, MAGIC_HINTS))
		{
			score += skill.isMagic() ? 6 : -4;
		}
		if ((role == CombatRole.SUMMONER) && containsAny(profileText, SUMMONER_HINTS))
		{
			score += skill.isMagic() ? 6 : 0;
		}
		return score;
	}

	private boolean isCombatSkillCadenceReady(String actorKey, Attackable npc)
	{
		final long now = System.currentTimeMillis();
		final Long lastUse = _lastOffensiveSkillUseTimes.get(normalizeActorKey(actorKey));
		return (lastUse == null) || ((now - lastUse.longValue()) >= Math.max(resolveCombatSkillCadenceMs(npc), 350L));
	}

	private long resolveCombatSkillCadenceMs(Attackable npc)
	{
		if (npc == null)
		{
			return _config.getCombatSkillCadenceMs();
		}

		final FpcGearSet gearSet = npc.getVariables().getObject(FakePlayerVariableKey.GEAR_SET_OVERRIDE, FpcGearSet.class);
		if ((gearSet != null) && (gearSet.getCombatSkillCadenceMs() > 0))
		{
			return gearSet.getCombatSkillCadenceMs();
		}
		return _config.getCombatSkillCadenceMs();
	}

	private boolean isPreparationSkillCadenceReady(String actorKey)
	{
		final long now = System.currentTimeMillis();
		final Long lastUse = _lastPreparationSkillUseTimes.get(normalizeActorKey(actorKey));
		return (lastUse == null) || ((now - lastUse.longValue()) >= Math.max(_config.getCombatSkillCadenceMs() * 4L, 5000L));
	}

	private void markOffensiveSkillUsed(String actorKey, int skillId)
	{
		final String normalizedKey = normalizeActorKey(actorKey);
		_lastOffensiveSkillUseTimes.put(normalizedKey, System.currentTimeMillis());
		_lastOffensiveSkillIds.put(normalizedKey, Integer.valueOf(skillId));
	}

	private void markPreparationSkillUsed(String actorKey)
	{
		_lastPreparationSkillUseTimes.put(normalizeActorKey(actorKey), System.currentTimeMillis());
	}

	private boolean isSameCoordinationGroup(FpcDefinition first, FpcDefinition second)
	{
		final String firstFamily = safe(first.getFamilyTag());
		final String secondFamily = safe(second.getFamilyTag());
		if (!firstFamily.isBlank() && firstFamily.equalsIgnoreCase(secondFamily))
		{
			return true;
		}
		return first.getAdventurerProfile().getPartyMode().equalsIgnoreCase(second.getAdventurerProfile().getPartyMode());
	}

	private String normalizeActorKey(String actorKey)
	{
		return ((actorKey == null) || actorKey.isBlank()) ? "afpc" : actorKey.toLowerCase(Locale.ENGLISH);
	}

	private String safe(String value)
	{
		return (value == null) ? "" : value;
	}

	private boolean containsAny(String text, List<String> hints)
	{
		if ((text == null) || text.isBlank() || (hints == null) || hints.isEmpty())
		{
			return false;
		}
		for (String hint : hints)
		{
			if ((hint != null) && !hint.isBlank() && text.contains(hint))
			{
				return true;
			}
		}
		return false;
	}

	private long distanceSquared(int x1, int y1, int x2, int y2)
	{
		final long dx = x1 - x2;
		final long dy = y1 - y2;
		return (dx * dx) + (dy * dy);
	}

	private enum CombatRole
	{
		MAGE,
		SUMMONER,
		FIGHTER
	}

	private enum CombatScene
	{
		FARM,
		PVP
	}

	private enum StandardCombatPolicy
	{
		NONE,
		STORM_SCREAMER,
		DOMINATOR
	}

	private static final class OffensiveCastResolution
	{
		private final WorldObject _castTarget;
		private final String _failureReason;

		private OffensiveCastResolution(WorldObject castTarget, String failureReason)
		{
			_castTarget = castTarget;
			_failureReason = (failureReason == null) ? "" : failureReason;
		}
	}
}
