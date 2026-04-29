package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.data.xml.AgathionData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.fakeplayer.FakePlayerVariableKey;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerStageBandData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcGearSet;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStageBand;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.platform.mobius.MobiusTierASkillLearnAccess;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.npc.FakePlayerHolder;
import org.l2jmobius.gameserver.model.actor.holders.player.ClassInfoHolder;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemSkillType;
import org.l2jmobius.gameserver.model.item.holders.AgathionSkillHolder;
import org.l2jmobius.gameserver.model.item.holders.ItemSkillHolder;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.SkillConditionScope;

public class FakePlayerCombatPowerService
{
	private final FakePlayerStageBandData _stageBandData;

	public FakePlayerCombatPowerService(FakePlayerStageBandData stageBandData)
	{
		_stageBandData = stageBandData;
	}

	public int resolveEffectiveLevel(FpcDefinition definition, int fallbackLevel)
	{
		if (definition == null)
		{
			return Math.max(1, fallbackLevel);
		}

		final FpcAdventurerProfile profile = definition.getAdventurerProfile();
		if ((profile == null) || !profile.isAdventurerTier())
		{
			return Math.max(1, fallbackLevel);
		}

		if (profile.usesStagedProgression())
		{
			final FpcStageBand stageBand = _stageBandData.getStageBand(profile.getStageBandId());
			if (stageBand != null)
			{
				return clamp(stageBand.getRepresentativeLevel(), stageBand.getMinLevel(), stageBand.getMaxLevel());
			}
		}

		if (profile.getFixedLevel() > 0)
		{
			return profile.getFixedLevel();
		}
		return Math.max(1, fallbackLevel);
	}

	public ResolvedFpcCombatPower resolveDefinitionCombatPower(FpcDefinition definition, int carrierNpcId)
	{
		final NpcTemplate template = (carrierNpcId > 0) ? NpcData.getInstance().getTemplate(carrierNpcId) : null;
		final int fallbackLevel = (template != null) ? template.getLevel() : 1;
		return buildCombatPower(definition, template, fallbackLevel);
	}

	public ResolvedFpcCombatPower resolveCombatPower(FpcDefinition definition, Npc npc)
	{
		final NpcTemplate template = (npc != null) ? npc.getTemplate() : null;
		final int fallbackLevel = (template != null) ? template.getLevel() : 1;
		return buildCombatPower(definition, template, fallbackLevel);
	}

	public ResolvedFpcCombatPower applyCombatPower(Npc npc, FpcDefinition definition)
	{
		if ((npc == null) || (definition == null))
		{
			return null;
		}

		final ResolvedFpcCombatPower power = resolveCombatPower(definition, npc);
		applyCombatPower(npc, power);
		npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());
		return power;
	}

	public ResolvedFpcCombatPower applyCombatPower(Npc npc, ResolvedFpcCombatPower power)
	{
		if ((npc == null) || (power == null))
		{
			return power;
		}

		for (Skill skill : power.getKnownSkills())
		{
			npc.addSkill(skill);
		}
		applyGearSetPassiveSkills(npc);
		return power;
	}

	public String describeDefinition(FpcDefinition definition, int carrierNpcId)
	{
		return resolveDefinitionCombatPower(definition, carrierNpcId).describe();
	}

	private ResolvedFpcCombatPower buildCombatPower(FpcDefinition definition, NpcTemplate template, int fallbackLevel)
	{
		final int effectiveLevel = resolveEffectiveLevel(definition, fallbackLevel);
		final int targetClassDepth = resolveTargetClassDepth(effectiveLevel);
		final String combatTierTag = resolveCombatTierTag(effectiveLevel);
		final FpcStageBand stageBand = resolveStageBand(definition);
		final FpcAdventurerProfile profile = (definition != null) ? definition.getAdventurerProfile() : null;
		final String resolvedLoadoutProfile = resolveLoadoutProfile(profile, stageBand);
		final String resolvedConsumableProfile = resolveConsumableProfile(profile, stageBand);
		final String resolvedCombatProfile = resolveCombatProfile(profile, stageBand);

		PlayerClass resolvedPlayerClass = null;
		List<Skill> passiveSkills = List.of();
		List<Skill> activeSkills = List.of();
		if ((template != null) && (template.getFakePlayerInfo() != null))
		{
			resolvedPlayerClass = resolvePlayerClassForTier(template.getFakePlayerInfo(), targetClassDepth);
			final SkillBuckets buckets = collectEligibleSkills(resolvedPlayerClass, effectiveLevel);
			passiveSkills = buckets.passiveSkills();
			activeSkills = buckets.activeSkills();
		}

		final String resolvedPlayerClassName = resolveClassName(resolvedPlayerClass);
		final int combatWeight = calculateCombatWeight(effectiveLevel, combatTierTag, passiveSkills.size(), activeSkills.size());
		return new ResolvedFpcCombatPower(effectiveLevel, combatTierTag, targetClassDepth, resolvedPlayerClass, resolvedPlayerClassName, resolvedLoadoutProfile, resolvedConsumableProfile, resolvedCombatProfile, combatWeight, passiveSkills, activeSkills);
	}

	private FpcStageBand resolveStageBand(FpcDefinition definition)
	{
		if (definition == null)
		{
			return null;
		}

		final FpcAdventurerProfile profile = definition.getAdventurerProfile();
		if ((profile == null) || !profile.usesStagedProgression())
		{
			return null;
		}
		return _stageBandData.getStageBand(profile.getStageBandId());
	}

	private String resolveLoadoutProfile(FpcAdventurerProfile profile, FpcStageBand stageBand)
	{
		if ((profile != null) && !profile.getLoadoutProfile().isBlank())
		{
			return profile.getLoadoutProfile();
		}
		return (stageBand != null) ? stageBand.getLoadoutProfile() : "";
	}

	private String resolveConsumableProfile(FpcAdventurerProfile profile, FpcStageBand stageBand)
	{
		if ((profile != null) && !profile.getConsumableProfile().isBlank())
		{
			return profile.getConsumableProfile();
		}
		return (stageBand != null) ? stageBand.getConsumableProfile() : "";
	}

	private String resolveCombatProfile(FpcAdventurerProfile profile, FpcStageBand stageBand)
	{
		if ((stageBand != null) && !stageBand.getCombatProfile().isBlank())
		{
			return stageBand.getCombatProfile();
		}
		if ((profile != null) && !profile.getCombatMode().isBlank())
		{
			return profile.getCombatMode();
		}
		return "";
	}

	private PlayerClass resolvePlayerClassForTier(FakePlayerHolder fakePlayerHolder, int targetClassDepth)
	{
		if (fakePlayerHolder == null)
		{
			return null;
		}

		PlayerClass resolved = fakePlayerHolder.getPlayerClass();
		if (resolved == null)
		{
			return null;
		}

		while ((resolved.getParent() != null) && (resolved.level() > targetClassDepth))
		{
			resolved = resolved.getParent();
		}

		while (resolved.level() < targetClassDepth)
		{
			if (resolved.getNextClasses().size() != 1)
			{
				return resolved;
			}
			resolved = resolved.getNextClasses().iterator().next();
		}
		return resolved;
	}

	private SkillBuckets collectEligibleSkills(PlayerClass playerClass, int effectiveLevel)
	{
		if (playerClass == null)
		{
			return new SkillBuckets(List.of(), List.of());
		}

		final Map<Integer, Skill> selectedSkills = new LinkedHashMap<>();
		for (Object skillLearn : SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values())
		{
			if ((skillLearn == null) || (MobiusTierASkillLearnAccess.getAcquireLevel(skillLearn) > effectiveLevel) || MobiusTierASkillLearnAccess.isLearnedByFS(skillLearn) || MobiusTierASkillLearnAccess.isResidentialSkill(skillLearn))
			{
				continue;
			}

			final Skill skill = SkillData.getInstance().getSkill(MobiusTierASkillLearnAccess.getSkillId(skillLearn), MobiusTierASkillLearnAccess.getSkillLevel(skillLearn));
			if (skill == null)
			{
				continue;
			}

			final Skill existing = selectedSkills.get(skill.getId());
			if ((existing == null) || isHigherSkillVersion(skill, existing))
			{
				selectedSkills.put(skill.getId(), skill);
			}
		}

		final List<Skill> passiveSkills = new ArrayList<>();
		final List<Skill> activeSkills = new ArrayList<>();
		for (Skill skill : selectedSkills.values())
		{
			if (skill.isPassive())
			{
				passiveSkills.add(skill);
			}
			else
			{
				activeSkills.add(skill);
			}
		}

		passiveSkills.sort(Comparator.comparingInt(Skill::getId));
		activeSkills.sort(Comparator.comparingInt(Skill::getId));
		return new SkillBuckets(passiveSkills, activeSkills);
	}

	private boolean isHigherSkillVersion(Skill candidate, Skill existing)
	{
		if (candidate.getLevel() != existing.getLevel())
		{
			return candidate.getLevel() > existing.getLevel();
		}
		return candidate.getSubLevel() > existing.getSubLevel();
	}

	private void applyGearSetPassiveSkills(Npc npc)
	{
		if ((npc == null) || !npc.isFakePlayer())
		{
			return;
		}

		final FpcGearSet gearSet = npc.getVariables().getObject(FakePlayerVariableKey.GEAR_SET_OVERRIDE, FpcGearSet.class);
		if ((gearSet == null) || gearSet.getPassiveSkillIds().isEmpty())
		{
			applyGearSetItemSkills(npc, gearSet);
			return;
		}

		for (int skillId : gearSet.getPassiveSkillIds())
		{
			final Skill skill = SkillData.getInstance().getSkill(skillId, 1);
			if (skill != null)
			{
				npc.addSkill(skill);
			}
		}
		applyGearSetItemSkills(npc, gearSet);
	}

	private void applyGearSetItemSkills(Npc npc, FpcGearSet gearSet)
	{
		if ((npc == null) || (gearSet == null))
		{
			return;
		}

		for (var entry : gearSet.getPaperdollItems().entrySet())
		{
			final ItemTemplate itemTemplate = ItemData.getInstance().getTemplate(entry.getValue());
			if (itemTemplate == null)
			{
				continue;
			}

			final int slot = entry.getKey();
			final int enchantLevel = gearSet.getResolvedEnchantLevel(slot, itemTemplate.isWeapon());
			applyItemSkillHolders(npc, itemTemplate.getSkills(ItemSkillType.NORMAL), enchantLevel, false);
			applyItemSkillHolders(npc, itemTemplate.getSkills(ItemSkillType.ON_ENCHANT), enchantLevel, true);
			if (itemTemplate.isBlessed())
			{
				applyItemSkillHolders(npc, itemTemplate.getSkills(ItemSkillType.ON_BLESSING), enchantLevel, true);
			}
			applyAgathionSkills(npc, slot, itemTemplate.getId(), enchantLevel);
		}
	}

	private void applyItemSkillHolders(Npc npc, List<ItemSkillHolder> holders, int enchantLevel, boolean requiresThreshold)
	{
		if ((npc == null) || (holders == null) || holders.isEmpty())
		{
			return;
		}

		for (ItemSkillHolder holder : holders)
		{
			if ((holder == null) || (requiresThreshold && (enchantLevel < holder.getValue())))
			{
				continue;
			}

			final Skill skill = holder.getSkill();
			applySkillIfEligible(npc, skill);
		}
	}

	private void applyAgathionSkills(Npc npc, int slot, int itemId, int enchantLevel)
	{
		if ((npc == null) || (slot < Inventory.PAPERDOLL_AGATHION1) || (slot > Inventory.PAPERDOLL_AGATHION5))
		{
			return;
		}

		final AgathionSkillHolder agathionSkills = AgathionData.getInstance().getSkills(itemId);
		if (agathionSkills == null)
		{
			return;
		}

		if (slot == Inventory.PAPERDOLL_AGATHION1)
		{
			for (Skill skill : agathionSkills.getMainSkills(enchantLevel))
			{
				applySkillIfEligible(npc, skill);
			}
		}

		for (Skill skill : agathionSkills.getSubSkills(enchantLevel))
		{
			applySkillIfEligible(npc, skill);
		}
	}

	private void applySkillIfEligible(Npc npc, Skill skill)
	{
		if ((npc == null) || (skill == null) || (npc.getSkillLevel(skill.getId()) >= skill.getLevel()))
		{
			return;
		}

		if (skill.isPassive() && !skill.checkConditions(SkillConditionScope.PASSIVE, npc, npc))
		{
			return;
		}

		npc.addSkill(skill);
	}

	private String resolveClassName(PlayerClass playerClass)
	{
		if (playerClass == null)
		{
			return "Unknown";
		}

		final ClassInfoHolder classInfo = ClassListData.getInstance().getClass(playerClass);
		if ((classInfo != null) && (classInfo.getClassName() != null) && !classInfo.getClassName().isBlank())
		{
			return classInfo.getClassName();
		}
		return humanize(playerClass.name());
	}

	private int resolveTargetClassDepth(int effectiveLevel)
	{
		if (effectiveLevel >= 76)
		{
			return 3;
		}
		if (effectiveLevel >= 40)
		{
			return 2;
		}
		if (effectiveLevel >= 20)
		{
			return 1;
		}
		return 0;
	}

	private String resolveCombatTierTag(int effectiveLevel)
	{
		if (effectiveLevel >= 99)
		{
			return "heroic";
		}
		if (effectiveLevel >= 76)
		{
			return "third_class";
		}
		if (effectiveLevel >= 40)
		{
			return "second_class";
		}
		if (effectiveLevel >= 20)
		{
			return "first_class";
		}
		return "base_class";
	}

	private int calculateCombatWeight(int effectiveLevel, String combatTierTag, int passiveSkillCount, int activeSkillCount)
	{
		return (effectiveLevel * 100) + resolveTierBonus(combatTierTag) + (passiveSkillCount * 14) + (activeSkillCount * 8);
	}

	private int resolveTierBonus(String combatTierTag)
	{
		if ("heroic".equalsIgnoreCase(combatTierTag))
		{
			return 3200;
		}
		if ("third_class".equalsIgnoreCase(combatTierTag))
		{
			return 2100;
		}
		if ("second_class".equalsIgnoreCase(combatTierTag))
		{
			return 1100;
		}
		if ("first_class".equalsIgnoreCase(combatTierTag))
		{
			return 400;
		}
		return 0;
	}

	private int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private String humanize(String value)
	{
		if ((value == null) || value.isBlank())
		{
			return "Unknown";
		}
		final String lower = value.toLowerCase().replace('_', ' ');
		final StringBuilder result = new StringBuilder(lower.length());
		boolean capitalizeNext = true;
		for (char c : lower.toCharArray())
		{
			if (capitalizeNext && Character.isLetter(c))
			{
				result.append(Character.toUpperCase(c));
				capitalizeNext = false;
			}
			else
			{
				result.append(c);
				capitalizeNext = c == ' ';
			}
		}
		return result.toString();
	}

	private static class SkillBuckets
	{
		private final List<Skill> _passiveSkills;
		private final List<Skill> _activeSkills;

		public SkillBuckets(List<Skill> passiveSkills, List<Skill> activeSkills)
		{
			_passiveSkills = passiveSkills;
			_activeSkills = activeSkills;
		}

		public List<Skill> passiveSkills()
		{
			return _passiveSkills;
		}

		public List<Skill> activeSkills()
		{
			return _activeSkills;
		}
	}
}
