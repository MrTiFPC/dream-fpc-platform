package org.l2jmobius.gameserver.fakeplayer.config;

import org.l2jmobius.gameserver.config.custom.FakePlayerPlatformConfig;

public class FakePlayerConfig
{
	private final boolean _enabled = FakePlayerPlatformConfig.ENABLE_FPC_PLATFORM;
	private final int _squadSize = 5;
	private final long _decisionIntervalMs = 650;
	private final long _chatCooldownMs = 45000;
	private final long _globalChatCooldownMs = 15000;
	private final long _whisperReplyCooldownMs = 1000;
	private final long _partyReplyCooldownMs = 2500;
	private final long _teleportCooldownMs = 120000;
	private final long _zoneBoredomMs = 180000;
	private final long _antiStuckTimeoutMs = 30000;
	private final long _plannerRefreshMs = 45000;
	private final long _plannerPlanReuseMs = 90000;
	private final long _respawnDelayMs = 20000;
	private final long _combatSkillCadenceMs = 1200;
	private final int _buffRefreshWindowSeconds = 5;
	private final int _coordinationRange = 1200;
	private final long _partyContractDurationMs = 1800000;
	private final int _partyFollowRange = 450;
	private final int _partyTeleportRange = 1800;
	private final int _afpcCombatAssistRange = 3000;
	private final int _personalityConflictEngageRange = 2000;
	private final int _personalityConflictChaseRange = 2000;
	private final int _personalityAllyAssistRange = 3000;
	private final long _personalityRetaliationMemoryMs = 90000L;
	private final long _personalityPkWitnessMemoryMs = 120000L;
	private final long _personalityClanAssistMemoryMs = 120000L;
	private final int _hybridClanWarEngageRange = 2000;
	private final int _hybridClanWarChaseRange = 4000;
	private final long _hybridClanWarDeathWindowMs = 3600000L;
	private final int _hybridClanWarDeathRetreatThreshold = 5;
	private final long _hybridClanWarRepeatKillerWindowMs = 1200000L;
	private final int _hybridClanWarRepeatKillerThreshold = 2;
	private final int _hybridClanMemberPressureLeaveThreshold = 12;
	private final String _debugLogPath = "log/fpc-debug.log";
	private final String _studioRootPath = "fpc_studio";
	private final long _studioSnapshotIntervalMs = 5000L;
	private final long _studioCommandPollIntervalMs = 1000L;
	private final String _productEdition = FakePlayerPlatformConfig.FPC_PRODUCT_EDITION;
	private final boolean _studioEnabled = FakePlayerPlatformConfig.ENABLE_FPC_STUDIO;
	
	private final String _personalityMode = FakePlayerPlatformConfig.ENABLE_FPC_EXTERNAL_PERSONALITY ? "ollama" : "noop";
	private final boolean _personalityEnabled = FakePlayerPlatformConfig.ENABLE_FPC_EXTERNAL_PERSONALITY;
	private final String _personalityEndpoint = "http://127.0.0.1:8000/personality/decide";
	private final String _personalityHealthEndpoint = "http://127.0.0.1:8000/health";
	private final int _personalityTimeoutMs = 30000;
	private final int _personalityHealthCacheMs = 5000;
	private final int _personalityMaxRetries = 0;
	private final String _personalityModel = "llama3.2-3b-fpc";
	private final double _personalityTemperature = 0.18;
	
	private final String _profilesPath = "data/fake_players/profiles.json";
	private final String _zonesPath = "data/fake_players/zones.json";
	private final String _gearPath = "data/fake_players/gear_sets.json";
	private final String _chatLinesPath = "data/fake_players/chat_lines.json";
	private final String _replyBanksPath = "data/fake_players/reply_banks.json";
	private final String _replySettingsPath = "data/fake_players/reply_settings.json";
	private final String _knowledgeCardsPath = "data/fake_players/knowledge_cards.json";
	private final String _behaviorFamiliesPath = "data/fake_players/behavior_families.json";
	private final String _afpcStateProfilesPath = "data/fake_players/afpc_state_profiles.json";
	private final String _knowledgePacksPath = "data/fake_players/knowledge_packs.json";
	private final String _adventurerRoutesPath = "data/fake_players/adventurer_routes.json";
	private final String _syntheticScenariosPath = "data/fake_players/synthetic_scenarios.json";
	private final String _fpcDefinitionsPath = "data/fake_players/fpcs.json";
	private final String _fpcDefinitionTemplatesPath = "data/fake_players/fpc_templates.json";
	private final String _fpcStageBandsPath = "data/fake_players/fpc_stage_bands.json";
	private final String _personaTemplatesPath = "data/fake_players/persona_templates.json";
	private final String _personaProfilesPath = "data/fake_players/persona_profiles.json";
	private final String _personaRelationshipsPath = "data/fake_players/persona_relationships.json";
	private final String _storyArcsPath = "data/fake_players/story_arcs.json";
	private final String _socialSignalPatternsPath = "data/fake_players/social_signal_patterns.json";
	private final String _conversationGuardPatternsPath = "data/fake_players/conversation_guard_patterns.json";
	private final String _routingRulesPath = "data/fake_players/routing_rules.json";
	
	public boolean isEnabled()
	{
		return _enabled;
	}
	
	public int getSquadSize()
	{
		return _squadSize;
	}
	
	public long getDecisionIntervalMs()
	{
		return _decisionIntervalMs;
	}
	
	public long getChatCooldownMs()
	{
		return _chatCooldownMs;
	}
	
	public long getGlobalChatCooldownMs()
	{
		return _globalChatCooldownMs;
	}

	public long getWhisperReplyCooldownMs()
	{
		return _whisperReplyCooldownMs;
	}

	public long getPartyReplyCooldownMs()
	{
		return _partyReplyCooldownMs;
	}
	
	public long getTeleportCooldownMs()
	{
		return _teleportCooldownMs;
	}
	
	public long getZoneBoredomMs()
	{
		return _zoneBoredomMs;
	}
	
	public long getAntiStuckTimeoutMs()
	{
		return _antiStuckTimeoutMs;
	}
	
	public long getPlannerRefreshMs()
	{
		return _plannerRefreshMs;
	}
	
	public long getPlannerPlanReuseMs()
	{
		return _plannerPlanReuseMs;
	}

	public long getRespawnDelayMs()
	{
		return _respawnDelayMs;
	}

	public long getCombatSkillCadenceMs()
	{
		return _combatSkillCadenceMs;
	}

	public int getBuffRefreshWindowSeconds()
	{
		return _buffRefreshWindowSeconds;
	}

	public int getCoordinationRange()
	{
		return _coordinationRange;
	}

	public long getPartyContractDurationMs()
	{
		return _partyContractDurationMs;
	}

	public int getPartyFollowRange()
	{
		return _partyFollowRange;
	}

	public int getPartyTeleportRange()
	{
		return _partyTeleportRange;
	}

	public int getAfpcCombatAssistRange()
	{
		return _afpcCombatAssistRange;
	}

	public int getPersonalityConflictEngageRange()
	{
		return _personalityConflictEngageRange;
	}

	public int getPersonalityConflictChaseRange()
	{
		return _personalityConflictChaseRange;
	}

	public int getPersonalityAllyAssistRange()
	{
		return _personalityAllyAssistRange;
	}

	public long getPersonalityRetaliationMemoryMs()
	{
		return _personalityRetaliationMemoryMs;
	}

	public long getPersonalityPkWitnessMemoryMs()
	{
		return _personalityPkWitnessMemoryMs;
	}

	public long getPersonalityClanAssistMemoryMs()
	{
		return _personalityClanAssistMemoryMs;
	}

	public int getHybridClanWarEngageRange()
	{
		return _hybridClanWarEngageRange;
	}

	public int getHybridClanWarChaseRange()
	{
		return _hybridClanWarChaseRange;
	}

	public long getHybridClanWarDeathWindowMs()
	{
		return _hybridClanWarDeathWindowMs;
	}

	public int getHybridClanWarDeathRetreatThreshold()
	{
		return _hybridClanWarDeathRetreatThreshold;
	}

	public long getHybridClanWarRepeatKillerWindowMs()
	{
		return _hybridClanWarRepeatKillerWindowMs;
	}

	public int getHybridClanWarRepeatKillerThreshold()
	{
		return _hybridClanWarRepeatKillerThreshold;
	}

	public int getHybridClanMemberPressureLeaveThreshold()
	{
		return _hybridClanMemberPressureLeaveThreshold;
	}

	public String getDebugLogPath()
	{
		return _debugLogPath;
	}

	public String getStudioRootPath()
	{
		return _studioRootPath;
	}

	public boolean isStudioEnabled()
	{
		return _studioEnabled;
	}

	public boolean isCommunityEdition()
	{
		return !_productEdition.equalsIgnoreCase("dev");
	}

	public String getProductEdition()
	{
		return _productEdition;
	}

	public long getStudioSnapshotIntervalMs()
	{
		return _studioSnapshotIntervalMs;
	}

	public long getStudioCommandPollIntervalMs()
	{
		return _studioCommandPollIntervalMs;
	}
	
	public String getPersonalityMode()
	{
		return _personalityMode;
	}
	
	public boolean isPersonalityEnabled()
	{
		return _personalityEnabled;
	}
	
	public String getPersonalityEndpoint()
	{
		return _personalityEndpoint;
	}
	
	public String getPersonalityHealthEndpoint()
	{
		return _personalityHealthEndpoint;
	}
	
	public int getPersonalityTimeoutMs()
	{
		return _personalityTimeoutMs;
	}
	
	public int getPersonalityHealthCacheMs()
	{
		return _personalityHealthCacheMs;
	}
	
	public int getPersonalityMaxRetries()
	{
		return _personalityMaxRetries;
	}
	
	public String getPersonalityModel()
	{
		return _personalityModel;
	}
	
	public double getPersonalityTemperature()
	{
		return _personalityTemperature;
	}
	
	public String getProfilesPath()
	{
		return _profilesPath;
	}
	
	public String getZonesPath()
	{
		return _zonesPath;
	}
	
	public String getGearPath()
	{
		return _gearPath;
	}
	
	public String getChatLinesPath()
	{
		return _chatLinesPath;
	}

	public String getReplyBanksPath()
	{
		return _replyBanksPath;
	}

	public String getReplySettingsPath()
	{
		return _replySettingsPath;
	}

	public String getKnowledgeCardsPath()
	{
		return _knowledgeCardsPath;
	}

	public String getBehaviorFamiliesPath()
	{
		return _behaviorFamiliesPath;
	}

	public String getAfpcStateProfilesPath()
	{
		return _afpcStateProfilesPath;
	}

	public String getKnowledgePacksPath()
	{
		return _knowledgePacksPath;
	}

	public String getAdventurerRoutesPath()
	{
		return _adventurerRoutesPath;
	}

	public String getSyntheticScenariosPath()
	{
		return _syntheticScenariosPath;
	}
	
	public String getFpcDefinitionsPath()
	{
		return _fpcDefinitionsPath;
	}

	public String getFpcDefinitionTemplatesPath()
	{
		return _fpcDefinitionTemplatesPath;
	}

	public String getFpcStageBandsPath()
	{
		return _fpcStageBandsPath;
	}

	public String getPersonaTemplatesPath()
	{
		return _personaTemplatesPath;
	}

	public String getPersonaProfilesPath()
	{
		return _personaProfilesPath;
	}

	public String getPersonaRelationshipsPath()
	{
		return _personaRelationshipsPath;
	}

	public String getStoryArcsPath()
	{
		return _storyArcsPath;
	}

	public String getSocialSignalPatternsPath()
	{
		return _socialSignalPatternsPath;
	}

	public String getConversationGuardPatternsPath()
	{
		return _conversationGuardPatternsPath;
	}

	public String getRoutingRulesPath()
	{
		return _routingRulesPath;
	}

}
