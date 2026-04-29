package org.l2jmobius.gameserver.fakeplayer.service;

import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerAdvisoryPlan;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetAdapter;
import org.l2jmobius.gameserver.model.actor.Player;

public class FakePlayerCompanionDirectiveService
{
	private static final String[] RETURN_HOME_PHRASES =
	{
		"return to town",
		"go to town",
		"back to town",
		"return town",
		"go home",
		"head back",
		"back to giran",
		"return to giran",
		"lets return",
		"let's return",
		"town first"
	};

	private static final String[] PVP_SUPPORT_PHRASES =
	{
		"pvp",
		"pk",
		"fight back",
		"help me fight",
		"they hit me",
		"someone hit me",
		"protect me",
		"under attack",
		"war",
		"enemy on me"
	};

	private static final String[] FARM_PHRASES =
	{
		"farm",
		"grind",
		"hunt",
		"pull",
		"spot",
		"aoe",
		"buff up",
		"rebuff",
		"keep the pace"
	};

	private static final String[] FOLLOW_PHRASES =
	{
		"follow me",
		"stay with me",
		"stay close",
		"stick with me",
		"come with me",
		"come here",
		"come to me",
		"come marc",
		"marc come",
		"keep up",
		"wait here",
		"hold here",
		"move with me",
		"stay on me",
		"on me",
		"assist me"
	};

	private final FakePlayerRulesetAdapter _ruleset;
	private final FakePlayerMessageClassifier _messageClassifier;
	private final FakePlayerHybridPartyService _hybridPartyService;
	private final FakePlayerTaskService _taskService;
	private final FakePlayerAfpcCombatPolicyService _afpcCombatPolicyService;

	public FakePlayerCompanionDirectiveService(FakePlayerRulesetAdapter ruleset, FakePlayerMessageClassifier messageClassifier, FakePlayerHybridPartyService hybridPartyService, FakePlayerTaskService taskService, FakePlayerAfpcCombatPolicyService afpcCombatPolicyService)
	{
		_ruleset = ruleset;
		_messageClassifier = messageClassifier;
		_hybridPartyService = hybridPartyService;
		_taskService = taskService;
		_afpcCombatPolicyService = afpcCombatPolicyService;
	}

	public String classifyDirectiveCategory(FpcDefinition definition, String fakePlayerName, Player sender, String normalized, boolean shareLocation)
	{
		if ((definition == null) || (sender == null) || (normalized == null) || normalized.isBlank())
		{
			return "";
		}

		if (FakePlayerSharedLocationSupport.asksToShareLocation(normalized))
		{
			if (!_ruleset.supportsSharedLocationPins())
			{
				return FakePlayerSharedLocationSupport.SHARE_LOCATION_BLOCKED;
			}
			return canShareLocationWithPlayer(definition, sender) ? FakePlayerSharedLocationSupport.SHARE_LOCATION : FakePlayerSharedLocationSupport.SHARE_LOCATION_BLOCKED;
		}
		if (_messageClassifier.containsPhrase(normalized, RETURN_HOME_PHRASES))
		{
			return isActiveCompanionLeader(definition, sender) ? "return_memory" : "";
		}
		if (_messageClassifier.containsPhrase(normalized, PVP_SUPPORT_PHRASES))
		{
			return isActiveCompanionLeader(definition, sender) ? "pvp_conflict" : "";
		}
		if (shareLocation && FakePlayerSharedLocationSupport.asksToUseSharedLocation(normalized))
		{
			if (!isActiveCompanionLeader(definition, sender))
			{
				return "";
			}
			return ((_taskService != null) && _taskService.canUseSharedLocationTeleport(fakePlayerName, sender)) ? FakePlayerSharedLocationSupport.SHARED_LOCATION_FOLLOW : FakePlayerSharedLocationSupport.SHARED_LOCATION_FOLLOW_BLOCKED;
		}
		if (_messageClassifier.containsPhrase(normalized, FARM_PHRASES))
		{
			return isActiveCompanionLeader(definition, sender) ? "party_farm" : "";
		}
		if (_messageClassifier.containsPhrase(normalized, FOLLOW_PHRASES))
		{
			return isActiveCompanionLeader(definition, sender) ? "companionship" : "";
		}
		return "";
	}

	public void applyDirectiveAction(String fakePlayerId, Player sender, FakePlayerAdvisoryPlan plan)
	{
		if ((fakePlayerId == null) || fakePlayerId.isBlank() || (sender == null) || (plan == null) || (_hybridPartyService == null))
		{
			return;
		}

		final Player leader = _hybridPartyService.getActivePlayerLeader(fakePlayerId);
		if ((leader == null) || (leader.getObjectId() != sender.getObjectId()))
		{
			return;
		}

		if ("return_memory".equalsIgnoreCase(plan.getTopicTag()))
		{
			_hybridPartyService.clearHybridContract(fakePlayerId, "leader_return_home");
			return;
		}
		if (FakePlayerSharedLocationSupport.isSharedLocationFollowCategory(plan.getTopicTag()))
		{
			if ((_taskService != null) && _taskService.teleportToSharedLocation(fakePlayerId, sender))
			{
				_hybridPartyService.markLeaderActivity(fakePlayerId, sender, "shared_location_follow");
			}
			return;
		}
		if ((_taskService != null) && shouldPromptImmediateFollow(plan.getTopicTag()))
		{
			_taskService.promptImmediateFollow(fakePlayerId, sender);
		}

		_hybridPartyService.markLeaderActivity(fakePlayerId, sender, "chat_" + plan.getTopicTag());
	}

	private boolean shouldPromptImmediateFollow(String category)
	{
		if ((category == null) || category.isBlank())
		{
			return false;
		}
		return "companionship".equalsIgnoreCase(category) || "party_farm".equalsIgnoreCase(category) || "pvp_conflict".equalsIgnoreCase(category);
	}

	public String chooseFallbackLine(String category)
	{
		final String sharedLocationLine = FakePlayerSharedLocationSupport.chooseFallbackLine(category);
		if (!sharedLocationLine.isBlank())
		{
			return sharedLocationLine;
		}
		if ("return_memory".equalsIgnoreCase(category))
		{
			return "Then say town cleanly and I'll take the proper road back.";
		}
		if ("party_farm".equalsIgnoreCase(category))
		{
			return "Set the pull cleanly. I'll keep pace with you.";
		}
		if ("pvp_conflict".equalsIgnoreCase(category))
		{
			return "Call the first threat and stay close. I'll answer from there.";
		}
		if ("companionship".equalsIgnoreCase(category))
		{
			return "Stay moving. I'll keep with you.";
		}
		return "";
	}

	private boolean canShareLocationWithPlayer(FpcDefinition definition, Player sender)
	{
		if ((definition == null) || (sender == null) || !_ruleset.supportsSharedLocationPins())
		{
			return false;
		}
		if (isActiveCompanionLeader(definition, sender))
		{
			return true;
		}
		return (_afpcCombatPolicyService != null) && _afpcCombatPolicyService.isGoodFriend(definition, sender);
	}

	private boolean isActiveCompanionLeader(FpcDefinition definition, Player sender)
	{
		if ((_hybridPartyService == null) || (definition == null) || (sender == null))
		{
			return false;
		}

		final Player leader = _hybridPartyService.getActivePlayerLeader(definition.getId());
		return (leader != null) && (leader.getObjectId() == sender.getObjectId());
	}
}
