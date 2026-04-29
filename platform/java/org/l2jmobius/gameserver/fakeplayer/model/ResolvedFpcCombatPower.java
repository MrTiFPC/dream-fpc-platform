package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.skill.Skill;

public class ResolvedFpcCombatPower
{
	private final int _effectiveLevel;
	private final String _combatTierTag;
	private final int _targetClassDepth;
	private final PlayerClass _resolvedPlayerClass;
	private final String _resolvedPlayerClassName;
	private final String _resolvedLoadoutProfile;
	private final String _resolvedConsumableProfile;
	private final String _resolvedCombatProfile;
	private final int _combatWeight;
	private final List<Skill> _passiveSkills;
	private final List<Skill> _activeSkills;
	private final List<Skill> _knownSkills;

	public ResolvedFpcCombatPower(int effectiveLevel, String combatTierTag, int targetClassDepth, PlayerClass resolvedPlayerClass, String resolvedPlayerClassName, String resolvedLoadoutProfile, String resolvedConsumableProfile, String resolvedCombatProfile, int combatWeight, List<Skill> passiveSkills, List<Skill> activeSkills)
	{
		_effectiveLevel = Math.max(1, effectiveLevel);
		_combatTierTag = ((combatTierTag == null) || combatTierTag.isBlank()) ? "base_class" : combatTierTag;
		_targetClassDepth = Math.max(0, targetClassDepth);
		_resolvedPlayerClass = resolvedPlayerClass;
		_resolvedPlayerClassName = ((resolvedPlayerClassName == null) || resolvedPlayerClassName.isBlank()) ? "Unknown" : resolvedPlayerClassName;
		_resolvedLoadoutProfile = (resolvedLoadoutProfile == null) ? "" : resolvedLoadoutProfile;
		_resolvedConsumableProfile = (resolvedConsumableProfile == null) ? "" : resolvedConsumableProfile;
		_resolvedCombatProfile = (resolvedCombatProfile == null) ? "" : resolvedCombatProfile;
		_combatWeight = Math.max(0, combatWeight);
		_passiveSkills = List.copyOf(passiveSkills == null ? List.of() : passiveSkills);
		_activeSkills = List.copyOf(activeSkills == null ? List.of() : activeSkills);
		final List<Skill> allSkills = new ArrayList<>(_passiveSkills.size() + _activeSkills.size());
		allSkills.addAll(_passiveSkills);
		allSkills.addAll(_activeSkills);
		_knownSkills = List.copyOf(allSkills);
	}

	public int getEffectiveLevel()
	{
		return _effectiveLevel;
	}

	public String getCombatTierTag()
	{
		return _combatTierTag;
	}

	public int getTargetClassDepth()
	{
		return _targetClassDepth;
	}

	public PlayerClass getResolvedPlayerClass()
	{
		return _resolvedPlayerClass;
	}

	public String getResolvedPlayerClassName()
	{
		return _resolvedPlayerClassName;
	}

	public String getResolvedLoadoutProfile()
	{
		return _resolvedLoadoutProfile;
	}

	public String getResolvedConsumableProfile()
	{
		return _resolvedConsumableProfile;
	}

	public String getResolvedCombatProfile()
	{
		return _resolvedCombatProfile;
	}

	public int getCombatWeight()
	{
		return _combatWeight;
	}

	public List<Skill> getPassiveSkills()
	{
		return _passiveSkills;
	}

	public List<Skill> getActiveSkills()
	{
		return _activeSkills;
	}

	public List<Skill> getKnownSkills()
	{
		return _knownSkills;
	}

	public int getPassiveSkillCount()
	{
		return _passiveSkills.size();
	}

	public int getActiveSkillCount()
	{
		return _activeSkills.size();
	}

	public int getKnownSkillCount()
	{
		return _knownSkills.size();
	}

	public String describe()
	{
		final StringBuilder summary = new StringBuilder();
		summary.append("lvl=").append(_effectiveLevel);
		summary.append(" tier=").append(_combatTierTag);
		summary.append(" class=").append(_resolvedPlayerClassName);
		summary.append(" skills=").append(getKnownSkillCount());
		summary.append("(").append(getPassiveSkillCount()).append("p/").append(getActiveSkillCount()).append("a)");
		summary.append(" power=").append(_combatWeight);
		if (!_resolvedCombatProfile.isBlank())
		{
			summary.append(" combat=").append(_resolvedCombatProfile);
		}
		if (!_resolvedLoadoutProfile.isBlank())
		{
			summary.append(" loadout=").append(_resolvedLoadoutProfile);
		}
		if (!_resolvedConsumableProfile.isBlank())
		{
			summary.append(" consumables=").append(_resolvedConsumableProfile);
		}
		return summary.toString();
	}
}
