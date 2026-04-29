package org.l2jmobius.gameserver.fakeplayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.data.xml.FakePlayerData;
import org.l2jmobius.gameserver.config.custom.FakePlayerPlatformConfig;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerBankFoundationData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerAdventurerRouteData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerConversationGuardData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerGearData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerKnowledgeData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerPersonaData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerProfileData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerReplyBankData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerReplySettingsData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerRoutingRuleData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerSocialSignalData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerStageBandData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerSyntheticScenarioData;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerZoneData;
import org.l2jmobius.gameserver.fakeplayer.model.FakePlayerAdvisoryPlan;
import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardAssessment;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDebugCategory;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcMemoryBundle;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplyBankSelection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRouteProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRelationshipSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.FpcRetrievedMemorySection;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplySettings;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSnapshot;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcPersona;
import org.l2jmobius.gameserver.fakeplayer.model.enums.FpcSocialEpisodeType;
import org.l2jmobius.gameserver.fakeplayer.personality.LocalPersonalityAdapter;
import org.l2jmobius.gameserver.fakeplayer.personality.NoOpLocalPersonalityAdapter;
import org.l2jmobius.gameserver.fakeplayer.personality.OllamaLocalPersonalityAdapter;
import org.l2jmobius.gameserver.fakeplayer.personality.PersonalityDecision;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapter;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapterFactory;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetAdapter;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetFactory;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerAppearanceService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerAdventurerService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerChatService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerBuffStateService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerCombatPowerService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerAdventurerCombatService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerCompanionDirectiveService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerConversationGuardService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerDecisionEngine;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerDebugService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerAfpcCombatPolicyService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerHybridClanService.HybridClanMemberSnapshot;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerKnowledgeService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerManager;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerMemoryService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerMessageClassifier;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerHybridPartyService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerHybridClanService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerPersonaResolver;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerReplyPolicyService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerReplyBankService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerRouteService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerSocialMemoryService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerSocialService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerSpawnService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerStudioService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerSyntheticScenarioService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerTargetingService;
import org.l2jmobius.gameserver.fakeplayer.service.FakePlayerTaskService;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAccess;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.npc.OnAttackableAttack;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureAttacked;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillFinishCast;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureTeleported;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerChat;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerMoveRequest;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;

/**
 * Simulation-only bootstrap for fake players. Local AI hookup is advisory-only and remains isolated.
 */
public final class FakePlayerModule
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerModule.class.getName());
	private static final int GENERAL_CHAT_RANGE = 1250;
	private static final int MAX_MULTI_FPC_REPLIES = 5;
	private static final long PARTY_REINVITE_COOLDOWN_MS = 15000L;
	private static final long PARTY_DECLINE_EPISODE_SPACING_MS = 15000L;
	private static final long PARTY_ACCEPT_EPISODE_SPACING_MS = 90000L;
	private static final long COMPANION_FOLLOWUP_QUIET_MS = 2L * 60L * 1000L;
	private static final long COMPANION_FOLLOWUP_MAX_CONVERSATION_AGE_MS = 20L * 60L * 1000L;
	private static final Set<String> BAD_REPLY_ENDINGS = Set.of("a", "an", "and", "or", "the", "to", "for", "of", "with", "than", "then", "that", "this", "these", "those", "my", "your", "our", "their", "not", "class", "level", "levels", "skills", "weapon", "weapons", "gear", "adena", "xp", "exp", "recommended", "is", "are", "be");
	private static final Pattern SIMPLE_MATH_WHATS_PATTERN = Pattern.compile("\\bwhat(?:'s|s| is)?\\s+(-?\\d{1,8})\\s*([+\\-*x])\\s*(-?\\d{1,8})\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SIMPLE_MATH_INLINE_PATTERN = Pattern.compile("\\b(-?\\d{1,8})\\s*([+\\-*x])\\s*(-?\\d{1,8})\\b");
	private static volatile FakePlayerModule INSTANCE;

	@FunctionalInterface
	private interface IncomingTargetResolver
	{
		List<String> resolve(Player sender, String text);
	}

	@FunctionalInterface
	private interface IncomingTargetHandler
	{
		boolean handle(String fakePlayerName);
	}

	@FunctionalInterface
	private interface IncomingPlanBuilder
	{
		FakePlayerAdvisoryPlan build(String fakePlayerName, Player sender, String text, String channel, FpcConversationGuardAssessment inputGuard);
	}

	@FunctionalInterface
	private interface IncomingFallbackBuilder
	{
		FakePlayerAdvisoryPlan build(FakePlayerAdvisoryPlan plan, String fakePlayerName, Player sender, String text, String channel);
	}

	@FunctionalInterface
	private interface IncomingEmitter
	{
		boolean emit(FakePlayerAdvisoryPlan plan);
	}

	@FunctionalInterface
	private interface IncomingSocialSignalHook
	{
		void handle(String fakePlayerName, Player sender, SocialSignalAssessment socialSignal);
	}

	@FunctionalInterface
	private interface IncomingPostSendHook
	{
		void handle(String fakePlayerName, Player sender, FakePlayerAdvisoryPlan plan);
	}

	private static final class ReplyAnalysisSnapshot
	{
		private final String _effectiveIncomingText;
		private final FpcRouteProfile _routeProfile;
		private final String _messageCategory;
		private final String _knowledgeType;
		private final String _focusEntityName;
		private final String _focusIntent;

		private ReplyAnalysisSnapshot(String effectiveIncomingText, FpcRouteProfile routeProfile, String messageCategory, String knowledgeType, String focusEntityName, String focusIntent)
		{
			_effectiveIncomingText = effectiveIncomingText;
			_routeProfile = routeProfile;
			_messageCategory = messageCategory;
			_knowledgeType = knowledgeType;
			_focusEntityName = focusEntityName;
			_focusIntent = focusIntent;
		}
	}

	private static final class FallbackReplyContext
	{
		private final boolean _marcToTrustedPlayer;
		private final boolean _hasRecentHistory;
		private final ReplyAnalysisSnapshot _analysis;
		private final FpcReplySettings _settings;

		private FallbackReplyContext(boolean marcToTrustedPlayer, boolean hasRecentHistory, ReplyAnalysisSnapshot analysis, FpcReplySettings settings)
		{
			_marcToTrustedPlayer = marcToTrustedPlayer;
			_hasRecentHistory = hasRecentHistory;
			_analysis = analysis;
			_settings = settings;
		}
	}

	private enum FallbackReplyFamily
	{
		WHISPER,
		GENERAL,
		PUBLIC
	}
	
	private final FakePlayerConfig _config;
	private final FakePlayerPlatformAdapter _platformAdapter;
	private final FakePlayerProfileData _profileData;
	private final FakePlayerZoneData _zoneData;
	private final FakePlayerGearData _gearData;
	private final FakePlayerRulesetAdapter _ruleset;
	private final FakePlayerDebugService _debugService;
	private final FakePlayerBankFoundationData _bankFoundationData;
	private final FakePlayerAdventurerRouteData _adventurerRouteData;
	private final FakePlayerPersonaData _personaData;
	private final FakePlayerReplyBankData _replyBankData;
	private final FakePlayerReplySettingsData _replySettingsData;
	private final FakePlayerKnowledgeData _knowledgeData;
	private final FakePlayerStageBandData _stageBandData;
	private final FakePlayerSyntheticScenarioData _syntheticScenarioData;
	private final FakePlayerSocialSignalData _socialSignalData;
	private final FakePlayerConversationGuardData _conversationGuardData;
	private final FakePlayerRoutingRuleData _routingRuleData;
	private final FakePlayerPersonaResolver _personaResolver;
	private final FakePlayerMessageClassifier _messageClassifier;
	private final FakePlayerReplyBankService _replyBankService;
	private final FakePlayerKnowledgeService _knowledgeService;
	private final FakePlayerConversationGuardService _conversationGuardService;
	private final FakePlayerReplyPolicyService _replyPolicyService;
	private final LocalPersonalityAdapter _personalityAdapter;
	private final FakePlayerAdventurerService _adventurerService;
	private final FakePlayerAppearanceService _appearanceService;
	private final FakePlayerCombatPowerService _combatPowerService;
	private final FakePlayerBuffStateService _buffStateService;
	private final FakePlayerAdventurerCombatService _adventurerCombatService;
	private final FakePlayerHybridClanService _hybridClanService;
	private final FakePlayerHybridPartyService _hybridPartyService;
	private final FakePlayerSocialMemoryService _socialMemoryService;
	private final FakePlayerSocialService _socialService;
	private final FakePlayerAfpcCombatPolicyService _afpcCombatPolicyService;
	private final FakePlayerSyntheticScenarioService _syntheticScenarioService;
	private final FakePlayerRouteService _routeService;
	private final FakePlayerChatService _chatService;
	private final FakePlayerTargetingService _targetingService;
	private final FakePlayerDecisionEngine _decisionEngine;
	private final FakePlayerSpawnService _spawnService;
	private final FakePlayerTaskService _taskService;
	private final FakePlayerCompanionDirectiveService _companionDirectiveService;
	private final FakePlayerStudioService _studioService;
	private final FakePlayerManager _manager;
	private final ConcurrentHashMap<String, Long> _recentIngressByKey;
	private final ConcurrentHashMap<String, Long> _recentCombatPressureByKey;

	public FakePlayerModule()
	{
		this(new FakePlayerConfig(), FakePlayerRulesetFactory.create(FakePlayerPlatformConfig.FPC_RULESET_ID), FakePlayerPlatformAdapterFactory.create(FakePlayerPlatformConfig.FPC_PLATFORM_ADAPTER_ID));
	}

	public FakePlayerModule(FakePlayerConfig config, FakePlayerRulesetAdapter ruleset)
	{
		this(config, ruleset, FakePlayerPlatformAdapterFactory.create(FakePlayerPlatformConfig.FPC_PLATFORM_ADAPTER_ID));
	}

	public FakePlayerModule(FakePlayerConfig config, FakePlayerRulesetAdapter ruleset, FakePlayerPlatformAdapter platformAdapter)
	{
		INSTANCE = this;
		_config = Objects.requireNonNull(config, "config");
		_platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
		_profileData = new FakePlayerProfileData(_config, _platformAdapter.getWorldFacade());
		_zoneData = new FakePlayerZoneData(_config);
		_gearData = new FakePlayerGearData(_config);
		_ruleset = Objects.requireNonNull(ruleset, "ruleset");
		_debugService = new FakePlayerDebugService(_config);
		_bankFoundationData = new FakePlayerBankFoundationData(_config);
		_adventurerRouteData = new FakePlayerAdventurerRouteData(_config);
		_personaData = new FakePlayerPersonaData(_config);
		_replyBankData = new FakePlayerReplyBankData(_config);
		_replySettingsData = new FakePlayerReplySettingsData(_config);
		_knowledgeData = new FakePlayerKnowledgeData(_config);
		_stageBandData = new FakePlayerStageBandData(_config);
		_syntheticScenarioData = new FakePlayerSyntheticScenarioData(_config);
		_socialSignalData = new FakePlayerSocialSignalData(_config);
		_conversationGuardData = new FakePlayerConversationGuardData(_config);
		_routingRuleData = new FakePlayerRoutingRuleData(_config);
		_personaResolver = new FakePlayerPersonaResolver(_profileData.getRegistry(), _personaData, _bankFoundationData);
		_messageClassifier = new FakePlayerMessageClassifier(_socialSignalData.getBank());
		_replyBankService = new FakePlayerReplyBankService(_replyBankData, _messageClassifier);
		_knowledgeService = new FakePlayerKnowledgeService(_knowledgeData, _messageClassifier, _profileData.getRegistry(), _bankFoundationData);
		_conversationGuardService = new FakePlayerConversationGuardService(_conversationGuardData.getBank());
		_replyPolicyService = new FakePlayerReplyPolicyService(_conversationGuardService);
		_personalityAdapter = createPersonalityAdapter(_config);
		_adventurerService = new FakePlayerAdventurerService(_stageBandData);
		_appearanceService = new FakePlayerAppearanceService(_gearData);
		_combatPowerService = new FakePlayerCombatPowerService(_stageBandData);
		_buffStateService = new FakePlayerBuffStateService(_config);
		_adventurerCombatService = new FakePlayerAdventurerCombatService(_config, _debugService, _buffStateService, _platformAdapter.getWorldFacade(), _platformAdapter.getNavigationFacade());
		_hybridClanService = new FakePlayerHybridClanService(_profileData.getRegistry());
		_hybridPartyService = new FakePlayerHybridPartyService(_config, _profileData.getRegistry(), _platformAdapter.getWorldFacade());
		_socialMemoryService = new FakePlayerSocialMemoryService();
		_socialService = new FakePlayerSocialService();
		_routeService = new FakePlayerRouteService(_zoneData, _adventurerRouteData);
		_chatService = new FakePlayerChatService(_config, _replySettingsData, _profileData.getRegistry(), _messageClassifier, _platformAdapter.getWorldFacade(), _platformAdapter.getChatTransport());
		_afpcCombatPolicyService = new FakePlayerAfpcCombatPolicyService(_config, _personaResolver, _chatService, _socialMemoryService);
		_syntheticScenarioService = new FakePlayerSyntheticScenarioService(_syntheticScenarioData, _debugService);
		_targetingService = new FakePlayerTargetingService(_config, _platformAdapter.getWorldFacade(), _platformAdapter.getNavigationFacade());
		_decisionEngine = new FakePlayerDecisionEngine(_config, _routeService, _chatService, _replySettingsData, _targetingService, _personaResolver, _messageClassifier, _replyBankService, _knowledgeService, _socialMemoryService, _socialService, _personalityAdapter);
		_spawnService = new FakePlayerSpawnService(_config, _appearanceService, _combatPowerService, _routeService, _platformAdapter.getSpawnLifecycle());
		_taskService = new FakePlayerTaskService(_config, _chatService, _profileData, _decisionEngine, _routeService, _combatPowerService, _targetingService, _spawnService, _adventurerCombatService, _afpcCombatPolicyService, _syntheticScenarioService, _hybridPartyService, _debugService, _ruleset, _platformAdapter.getWorldFacade(), _platformAdapter.getNavigationFacade());
		_companionDirectiveService = new FakePlayerCompanionDirectiveService(_ruleset, _messageClassifier, _hybridPartyService, _taskService, _afpcCombatPolicyService);
		_manager = new FakePlayerManager(_config, _profileData, _adventurerService, _spawnService, _decisionEngine, _taskService);
		_studioService = new FakePlayerStudioService(_config, _profileData, _routeService, _taskService, _hybridPartyService, _debugService, _personaResolver, _ruleset, this);
		_recentIngressByKey = new ConcurrentHashMap<>();
		_recentCombatPressureByKey = new ConcurrentHashMap<>();
	}
	
	private static LocalPersonalityAdapter createPersonalityAdapter(FakePlayerConfig config)
	{
		if (!config.isPersonalityEnabled())
		{
			return new NoOpLocalPersonalityAdapter();
		}
		
		switch (config.getPersonalityMode().toLowerCase())
		{
			case "ollama":
				return new OllamaLocalPersonalityAdapter(config);
			case "noop":
			default:
				return new NoOpLocalPersonalityAdapter();
		}
	}
	
	public void init()
	{
		if (!_config.isEnabled())
		{
			LOGGER.info(getClass().getSimpleName() + ": Fake players disabled.");
			return;
		}
		
		_profileData.load();
		_zoneData.load();
		_gearData.load();
		_bankFoundationData.load();
		_adventurerRouteData.load();
		_personaData.load();
		_replyBankData.load();
		_replySettingsData.load();
		_knowledgeData.load();
		_stageBandData.load();
		_syntheticScenarioData.load();
		_socialSignalData.load();
		_conversationGuardData.load();
		_routingRuleData.load();
		_messageClassifier.setSocialSignalBank(_socialSignalData.getBank());
		_messageClassifier.setRoutingRules(_routingRuleData.getRules());
		_conversationGuardService.setBank(_conversationGuardData.getBank());
		_chatService.initializePersistence();
		_socialMemoryService.initializePersistence();
		_hybridClanService.initializePersistence();
		_hybridClanService.restorePersistedContracts();
		_adventurerService.validateDefinitions(_profileData.getDefinitions());
		refreshClanRequestGuard();
		registerSocialListeners();
		
		_manager.spawnInitialSquad();
		_manager.start();
		if (_config.isStudioEnabled())
		{
			_studioService.start();
		}
		LOGGER.info(getClass().getSimpleName() + ": Fake player simulation initialized. ruleset=" + _ruleset.getId() + ".");
	}

	private void registerSocialListeners()
	{
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_ATTACKABLE_ATTACK, (OnAttackableAttack event) -> onAttackableAttack(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_ATTACKED, (OnCreatureAttacked event) -> onCreatureAttacked(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> onCreatureDeath(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_SKILL_FINISH_CAST, (OnCreatureSkillFinishCast event) -> onCreatureSkillFinishCast(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_TELEPORTED, (OnCreatureTeleported event) -> onCreatureTeleported(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_CHAT, (OnPlayerChat event) -> onPlayerChat(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_MOVE_REQUEST, (OnPlayerMoveRequest event) -> onPlayerMoveRequest(event), this));
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_PVP_KILL, (OnPlayerPvPKill event) -> onPlayerPvPKill(event), this));
	}

	private void onPlayerMoveRequest(OnPlayerMoveRequest event)
	{
		if ((event == null) || (event.getPlayer() == null))
		{
			return;
		}

		markHybridCompanionActivity(event.getPlayer(), "move");
	}

	private void onPlayerChat(OnPlayerChat event)
	{
		if ((event == null) || (event.getPlayer() == null) || (event.getText() == null) || event.getText().isBlank())
		{
			return;
		}

		markHybridCompanionActivity(event.getPlayer(), "chat");
	}

	private void onCreatureTeleported(OnCreatureTeleported event)
	{
		if ((event == null) || (event.getCreature() == null) || !event.getCreature().isPlayer())
		{
			return;
		}

		markHybridCompanionActivity(event.getCreature().asPlayer(), "teleport");
	}

	private void onPlayerLogout(OnPlayerLogout event)
	{
		if ((event == null) || (event.getPlayer() == null) || (_hybridPartyService == null))
		{
			return;
		}

		_hybridPartyService.clearHybridContractsForLeader(event.getPlayer(), "leader_logout");
	}

	private void onAttackableAttack(OnAttackableAttack event)
	{
		if ((event == null) || (event.getAttacker() == null) || (event.getTarget() == null) || !event.getTarget().isNpc() || !event.getTarget().isFakePlayer())
		{
			return;
		}

		final Npc npc = event.getTarget().asNpc();
		final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
		handleDirectAfpcAttack(definition, npc, event.getAttacker(), event.getDamage(), "attackable_attack");
	}

	private void onCreatureAttacked(OnCreatureAttacked event)
	{
		if ((event == null) || (event.getAttacker() == null) || (event.getTarget() == null))
		{
			return;
		}
		if (event.getAttacker().isPlayer())
		{
			markHybridCompanionActivity(event.getAttacker().asPlayer(), "combat");
			if ((event.getTarget() != null) && event.getTarget().isMonster() && (_hybridPartyService != null))
			{
				_hybridPartyService.rememberLeaderAssistTarget(event.getAttacker().asPlayer(), event.getTarget());
			}
		}
		if (event.getTarget().isPlayer())
		{
			markHybridCompanionActivity(event.getTarget().asPlayer(), "combat");
		}
		if (event.getTarget().isNpc() && event.getTarget().isFakePlayer())
		{
			final Npc npc = event.getTarget().asNpc();
			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			handleDirectAfpcAttack(definition, npc, event.getAttacker().asCreature(), -1, "creature_attacked");
			if (event.getAttacker().isPlayer())
			{
				handleProtectedPlayerCombatAssistAgainstAfpc(event.getAttacker().asPlayer(), npc, true);
			}
			return;
		}
		if (!event.getAttacker().isPlayer() && event.getAttacker().isNpc() && event.getAttacker().isFakePlayer() && event.getTarget().isPlayer())
		{
			handleProtectedPlayerCombatAssistAgainstAfpc(event.getTarget().asPlayer(), event.getAttacker().asNpc(), false);
			return;
		}
		if (!event.getAttacker().isPlayer())
		{
			return;
		}

		final Player attacker = event.getAttacker().asPlayer();
		if (!event.getTarget().isPlayer())
		{
			return;
		}

		final Player target = event.getTarget().asPlayer();
		if ((attacker == null) || (target == null) || (attacker == target) || (attacker.getInstanceId() != target.getInstanceId()))
		{
			return;
		}

		handleProtectedPlayerCombatAssist(target, attacker, FakePlayerAfpcCombatPolicyService.Trigger.CLAN_MEMBER_UNDER_ATTACK, FakePlayerAfpcCombatPolicyService.Trigger.PARTY_FRIEND_UNDER_ATTACK, "event=afpc_ally_support stance=defense attacker=" + attacker.getName() + " ally=" + target.getName());
		handleProtectedPlayerCombatAssist(attacker, target, FakePlayerAfpcCombatPolicyService.Trigger.CLAN_MEMBER_PVP, FakePlayerAfpcCombatPolicyService.Trigger.PARTY_FRIEND_INITIATED_PVP, "event=afpc_ally_support stance=offense attacker=" + attacker.getName() + " enemy=" + target.getName());
	}

	private void handleDirectAfpcAttack(FpcDefinition definition, Npc npc, Creature attacker, int damage, String source)
	{
		if (!isCombatReadyAdventurer(definition, npc) || (attacker == null))
		{
			return;
		}

		final String dedupeKey = "direct_afpc_attack|" + definition.getId().toLowerCase(Locale.ROOT) + "|" + attacker.getObjectId();
		if (isRecentCombatPressureTrigger(dedupeKey, 1000L))
		{
			return;
		}

		final String normalizedSource = ((source == null) || source.isBlank()) ? "unknown" : source.trim().toLowerCase(Locale.ROOT);
		final String debugDetail = "event=afpc_retaliation source=" + normalizedSource + ((damage >= 0) ? (" damage=" + damage) : "");

		if (attacker.isPlayer())
		{
			final Player attackerPlayer = attacker.asPlayer();
			_chatService.recordHostilePlayerAction(definition.getName(), attackerPlayer.getName(), "attacked_me");
			_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), attackerPlayer.getName(), FpcSocialEpisodeType.ATTACKED_ME, "player attacked the FPC directly", 5000L);
			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, attackerPlayer, FakePlayerAfpcCombatPolicyService.Trigger.DIRECT_ATTACK);
			if ((decision != null) && decision.isEngage())
			{
				clearHybridPartyContractForCombat(definition, attackerPlayer, normalizedSource);
			}
			triggerAfpcConflict(definition, npc, attackerPlayer, decision, "attacked_me", debugDetail);
			triggerNearbyClanAssistForAfpc(definition, npc, attackerPlayer, false);
			triggerNearbyPartyAssistForAfpc(definition, npc, attackerPlayer, false);
			return;
		}

		if (!attacker.isNpc() || !attacker.isFakePlayer())
		{
			return;
		}

		final Npc aggressorNpc = attacker.asNpc();
		if ((aggressorNpc.getObjectId() == npc.getObjectId()) || isHybridClanMate(npc, aggressorNpc) || isHybridPartyMate(npc, aggressorNpc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, aggressorNpc))
		{
			return;
		}

		clearHybridPartyContractForCombat(definition, aggressorNpc, normalizedSource);
		triggerAfpcCreatureConflict(definition, npc, aggressorNpc, "attacked_me_fpc", false, _config.getPersonalityConflictEngageRange(), _config.getPersonalityRetaliationMemoryMs(), debugDetail + " aggressorType=fpc");
		triggerNearbyClanAssistForAfpc(definition, npc, aggressorNpc, false);
		triggerNearbyPartyAssistForAfpc(definition, npc, aggressorNpc, false);
	}

	private void onCreatureDeath(OnCreatureDeath event)
	{
		if ((event != null) && (event.getAttacker() != null) && (event.getTarget() != null) && event.getAttacker().isNpc() && event.getAttacker().isFakePlayer() && event.getTarget().isPlayer())
		{
			handleProtectedPlayerKillAssistAgainstAfpc(event.getTarget().asPlayer(), event.getAttacker().asNpc());
		}

		if ((event != null) && (event.getAttacker() != null) && (event.getTarget() != null) && event.getAttacker().isNpc() && event.getAttacker().isFakePlayer() && event.getTarget().isNpc())
		{
			final Npc attackerNpc = event.getAttacker().asNpc();
			final FpcDefinition definition = resolveDefinitionByActiveNpc(attackerNpc);
			if (isCombatReadyAdventurer(definition, attackerNpc))
			{
				_syntheticScenarioService.recordKill(definition, event.getTarget().asNpc());
			}
		}

		if ((event == null) || (event.getTarget() == null) || !event.getTarget().isNpc() || !event.getTarget().isFakePlayer())
		{
			return;
		}

		final Npc npc = event.getTarget().asNpc();
		final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
		if (definition == null)
		{
			return;
		}

		final Creature killer = (event.getAttacker() != null) ? event.getAttacker().asCreature() : null;
		if (killer == null)
		{
			return;
		}
		if (killer.isPlayer())
		{
			final Player killerPlayer = killer.asPlayer();
			_chatService.recordHostilePlayerAction(definition.getName(), killerPlayer.getName(), "killed_me");
			final int hybridClanId = _hybridClanService.getHybridClanId(definition.getId());
			if ((hybridClanId > 0) && (killerPlayer.getClanId() == hybridClanId) && (killerPlayer.getClan() != null))
			{
				_chatService.resetRelationshipWarmth(definition.getName(), killerPlayer.getName(), "clanmate_kill", 4, 7);
				evaluateHybridClanPressure(definition, killerPlayer.getClan(), killerPlayer, "clanmate_kill");
			}
			_socialMemoryService.recordEpisode(definition.getName(), killerPlayer.getName(), FpcSocialEpisodeType.KILLED_ME, "player killed the FPC");
			triggerNearbyClanAssistForAfpc(definition, npc, killerPlayer, true);
			triggerNearbyPartyAssistForAfpc(definition, npc, killerPlayer, true);
			LOGGER.info(() -> getClass().getSimpleName() + ": Recorded personal hostility fpc=" + definition.getId() + " killer=" + killerPlayer.getName() + " reason=killed_me");
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=hostility_recorded killer=" + killerPlayer.getName() + " reason=killed_me");
			return;
		}

		if (killer.isNpc() && killer.isFakePlayer())
		{
			final Npc killerNpc = killer.asNpc();
			triggerNearbyClanAssistForAfpc(definition, npc, killerNpc, true);
			triggerNearbyPartyAssistForAfpc(definition, npc, killerNpc, true);
			_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=fpc_killed_by_fpc killer=" + killerNpc.getName() + "(" + killerNpc.getObjectId() + ")");
		}
	}

	private void onPlayerPvPKill(OnPlayerPvPKill event)
	{
		if ((event == null) || (event.getPlayer() == null) || (event.getTarget() == null))
		{
			return;
		}

		final Player killer = event.getPlayer();
		final Player victim = event.getTarget();
		if ((killer == victim) || (killer.getInstanceId() != victim.getInstanceId()))
		{
			return;
		}

		final Set<Integer> protectedWitnesses = new LinkedHashSet<>();
		handleProtectedPlayerKillAssist(victim, killer, protectedWitnesses);
		handleGenericWitnessedPk(killer, victim, protectedWitnesses);
	}

	private void handleProtectedPlayerCombatAssist(Player protectedAlly, Player enemy, FakePlayerAfpcCombatPolicyService.Trigger clanTrigger, FakePlayerAfpcCombatPolicyService.Trigger partyTrigger, String debugPrefix)
	{
		if ((protectedAlly == null) || (enemy == null) || (protectedAlly == enemy))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(protectedAlly, _config.getPersonalityAllyAssistRange()))
		{
			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, protectedAlly) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, enemy))
			{
				continue;
			}

			FakePlayerAfpcCombatPolicyService.Trigger trigger = null;
			if (isHybridClanMate(npc, protectedAlly) && !isHybridClanMate(npc, enemy))
			{
				trigger = clanTrigger;
			}
			else if (isGoodFriendPartyMember(definition, npc, protectedAlly) && !isGoodFriendPartyMember(definition, npc, enemy))
			{
				trigger = partyTrigger;
			}

			if (trigger == null)
			{
				continue;
			}

			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, enemy, trigger);
			final String reason = trigger.name().toLowerCase(Locale.ROOT);
			triggerAfpcConflict(definition, npc, enemy, decision, reason, debugPrefix + " reason=" + reason);
		}
	}

	private void handleProtectedPlayerKillAssist(Player victim, Player killer, Set<Integer> protectedWitnesses)
	{
		if ((victim == null) || (killer == null) || (protectedWitnesses == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(victim, _config.getPersonalityAllyAssistRange()))
		{
			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, killer) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, victim))
			{
				continue;
			}

			final boolean clanProtected = isHybridClanMate(npc, victim) && !isHybridClanMate(npc, killer);
			final boolean partyFriendProtected = !clanProtected && isGoodFriendPartyMember(definition, npc, victim) && !isGoodFriendPartyMember(definition, npc, killer);
			if (!clanProtected && !partyFriendProtected)
			{
				continue;
			}

			protectedWitnesses.add(npc.getObjectId());
			_chatService.recordHostilePlayerAction(definition.getName(), killer.getName(), "witnessed_pk");
			_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), killer.getName(), FpcSocialEpisodeType.WITNESSED_PK, clanProtected ? "player killed a nearby clanmate" : "player killed a nearby party friend", 15000L);
			final FakePlayerAfpcCombatPolicyService.Trigger trigger = clanProtected ? FakePlayerAfpcCombatPolicyService.Trigger.CLAN_MEMBER_KILLED : FakePlayerAfpcCombatPolicyService.Trigger.PARTY_FRIEND_KILLED;
			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, killer, trigger);
			final String reason = clanProtected ? "clan_member_killed" : "party_friend_killed";
			triggerAfpcConflict(definition, npc, killer, decision, reason, "event=afpc_kill_response ally=" + victim.getName() + " killer=" + killer.getName() + " reason=" + reason);
		}
	}

	private void handleProtectedPlayerCombatAssistAgainstAfpc(Player protectedAlly, Npc enemyNpc, boolean offense)
	{
		if ((protectedAlly == null) || (enemyNpc == null) || !enemyNpc.isFakePlayer())
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(protectedAlly, _config.getPersonalityAllyAssistRange()))
		{
			if (npc.getObjectId() == enemyNpc.getObjectId())
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, protectedAlly) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, enemyNpc))
			{
				continue;
			}

			final boolean clanProtected = isHybridClanMate(npc, protectedAlly) && !isHybridClanMate(npc, enemyNpc);
			final boolean partyFriendProtected = !clanProtected && isGoodFriendPartyMember(definition, npc, protectedAlly) && !isHybridPartyMate(npc, enemyNpc);
			if (!clanProtected && !partyFriendProtected)
			{
				continue;
			}

			final String reason;
			final long memoryMs;
			if (clanProtected)
			{
				reason = offense ? "clan_member_pvp_fpc" : "clan_member_under_attack_fpc";
				memoryMs = Math.max(_config.getPersonalityClanAssistMemoryMs(), _config.getPersonalityRetaliationMemoryMs());
			}
			else
			{
				reason = offense ? "party_friend_initiated_pvp_fpc" : "party_friend_under_attack_fpc";
				memoryMs = Math.max(_config.getPersonalityRetaliationMemoryMs(), 30000L);
			}

			triggerAfpcCreatureConflict(definition, npc, enemyNpc, reason, false, _config.getPersonalityAllyAssistRange(), memoryMs, "event=afpc_ally_support_fpc stance=" + (offense ? "offense" : "defense") + " ally=" + protectedAlly.getName() + " enemy=" + enemyNpc.getName() + " reason=" + reason);
		}
	}

	private void handleProtectedPlayerKillAssistAgainstAfpc(Player victim, Npc killerNpc)
	{
		if ((victim == null) || (killerNpc == null) || !killerNpc.isFakePlayer())
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(victim, _config.getPersonalityAllyAssistRange()))
		{
			if (npc.getObjectId() == killerNpc.getObjectId())
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, killerNpc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, victim))
			{
				continue;
			}

			final boolean clanProtected = isHybridClanMate(npc, victim) && !isHybridClanMate(npc, killerNpc);
			final boolean partyFriendProtected = !clanProtected && isGoodFriendPartyMember(definition, npc, victim) && !isHybridPartyMate(npc, killerNpc);
			if (!clanProtected && !partyFriendProtected)
			{
				continue;
			}

			final String reason = clanProtected ? "clan_member_killed_fpc" : "party_friend_killed_fpc";
			final long memoryMs = clanProtected ? _config.getPersonalityClanAssistMemoryMs() : Math.max(_config.getPersonalityRetaliationMemoryMs(), _config.getPersonalityPkWitnessMemoryMs());
			triggerAfpcCreatureConflict(definition, npc, killerNpc, reason, false, _config.getPersonalityAllyAssistRange(), memoryMs, "event=afpc_kill_response_fpc ally=" + victim.getName() + " killer=" + killerNpc.getName() + " reason=" + reason);
		}
	}

	private void handleGenericWitnessedPk(Player killer, Player victim, Set<Integer> protectedWitnesses)
	{
		if ((killer == null) || (victim == null) || (protectedWitnesses == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(killer, victim, _config.getPersonalityConflictEngageRange()))
		{
			if (protectedWitnesses.contains(npc.getObjectId()))
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, killer) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, victim))
			{
				continue;
			}

			_chatService.recordHostilePlayerAction(definition.getName(), killer.getName(), "witnessed_pk");
			_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), killer.getName(), FpcSocialEpisodeType.WITNESSED_PK, "player murdered someone nearby", 15000L);
			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, killer, FakePlayerAfpcCombatPolicyService.Trigger.WITNESSED_PK);
			triggerAfpcConflict(definition, npc, killer, decision, "witnessed_pk", "event=afpc_kill_response victim=" + victim.getName() + " reason=witnessed_pk");
		}
	}

	private void onCreatureSkillFinishCast(OnCreatureSkillFinishCast event)
	{
		if ((event == null) || (event.getCaster() == null) || (event.getTarget() == null) || (event.getSkill() == null) || !event.getCaster().isPlayer())
		{
			return;
		}

		final Skill skill = event.getSkill();
		final FpcSocialEpisodeType episodeType = classifySupportEpisode(skill);
		if (episodeType == null)
		{
			return;
		}

		final Player helper = event.getCaster().asPlayer();
		final long dedupeWindowMs = getSupportEpisodeDedupeWindowMs(episodeType);
		final String detail = buildSupportEpisodeDetail(skill, episodeType);
		final Set<String> recordedFpcIds = new LinkedHashSet<>();
		skill.forEachTargetAffected(event.getCaster(), event.getTarget(), affected ->
		{
			if ((affected == null) || !affected.isNpc())
			{
				return;
			}

			final Npc npc = affected.asNpc();
			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if ((definition == null) || !recordedFpcIds.add(definition.getId()))
			{
				return;
			}

			if (_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), helper.getName(), episodeType, detail, dedupeWindowMs))
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": Recorded support social episode id=" + definition.getId() + " helper=" + helper.getName() + " type=" + episodeType.name().toLowerCase(Locale.ENGLISH) + " skill=" + skill.getName());
				_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=support_recorded helper=" + helper.getName() + " type=" + episodeType.name().toLowerCase(Locale.ENGLISH) + " skill=\"" + skill.getName().replace('"', '\'') + "\"");
			}
		});
	}
	
	public boolean handleIncomingWhisper(Player sender, String fakePlayerName, String text, boolean shareLocation)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank() || (text == null) || text.isBlank())
		{
			return false;
		}

		final Npc activeNpc = findActiveFpcNpc(fakePlayerName);
		if (activeNpc == null)
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": Dropped private fake-player whisper for inactive FPC. speaker=" + fakePlayerName + " target=" + sender.getName());
			return false;
		}

		if (!_chatService.isWhisperChatCooldownReady(fakePlayerName, sender.getName()))
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": Consumed private fake-player whisper during reply cooldown. speaker=" + fakePlayerName + " target=" + sender.getName());
			return true;
		}
		return processIncomingChannelMessage(sender, fakePlayerName, text, "whisper", "player_whisper",
			(name, player, incomingText, channel, inputGuard) ->
			{
				FakePlayerAdvisoryPlan plan = buildCompanionDirectiveReplyPlan(name, player, incomingText, channel, shareLocation);
				if (plan == null)
				{
					plan = inputGuard.isSensitive() ? buildGuardedReplyPlan(name, player, channel, inputGuard) : _decisionEngine.createWhisperAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText);
				}
				return plan;
			},
			(plan, name, player, incomingText, channel) -> forceWhisperReplyPlan(plan, name, player.getName(), incomingText),
			plan -> _chatService.trySendPrivateLine(sender, plan),
			(name, player, socialSignal) ->
			{
			},
			this::applyCompanionDirectiveAction);
	}
	
	public boolean handleIncomingGeneral(Player sender, String text)
	{
		return handleAddressedIngress(sender, text, "general", this::resolveNearbyAddressedFakePlayers, fakePlayerName -> processIncomingChannelMessage(sender, fakePlayerName, text, "general", "player_general",
			(name, player, incomingText, channel, inputGuard) -> inputGuard.isSensitive() ? buildGuardedReplyPlan(name, player, channel, inputGuard) : _decisionEngine.createGeneralAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText),
			(plan, name, player, incomingText, channel) -> forceGeneralReplyPlan(plan, name, player.getName(), incomingText),
			_chatService::tryEmitVisibleLine,
			(name, player, socialSignal) ->
			{
			},
			(name, player, plan) ->
			{
			}));
	}

	public boolean handleIncomingPartyChat(Player sender, String text, boolean shareLocation)
	{
		if ((sender == null) || (text == null) || text.isBlank())
		{
			return false;
		}

		Party party = sender.getParty();
		if ((party == null) && (_hybridPartyService != null))
		{
			party = _hybridPartyService.resolveActiveParty(sender, "incoming_party_chat");
		}
		if (party == null)
		{
			return false;
		}
		final Party resolvedParty = party;
		return handleAddressedIngress(sender, text, "party", this::resolveHybridPartyAddressedFakePlayers, fakePlayerName -> processIncomingChannelMessage(sender, fakePlayerName, text, "party", "player_party",
			(name, player, incomingText, channel, inputGuard) ->
			{
				FakePlayerAdvisoryPlan plan = buildCompanionDirectiveReplyPlan(name, player, incomingText, channel, shareLocation);
				if (plan == null)
				{
					plan = inputGuard.isSensitive() ? buildGuardedReplyPlan(name, player, channel, inputGuard) : _decisionEngine.createPartyAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText);
				}
				return plan;
			},
			(plan, name, player, incomingText, channel) -> forcePartyReplyPlan(plan, name, player.getName(), incomingText),
			plan -> _chatService.tryEmitPartyLine(resolvedParty, plan),
			(name, player, socialSignal) ->
			{
			},
			this::applyCompanionDirectiveAction));
	}
	
	public boolean handleIncomingShout(Player sender, String text)
	{
		return handleIncomingPublicChannel(sender, text, "player_shout");
	}
	
	public boolean handleIncomingWorld(Player sender, String text)
	{
		return handleIncomingPublicChannel(sender, text, "player_world");
	}

	public boolean handleIncomingClanChat(Player sender, String text)
	{
		if ((sender == null) || (text == null) || text.isBlank())
		{
			return false;
		}

		final Clan clan = sender.getClan();
		if (clan == null)
		{
			return false;
		}

		return handleAddressedIngress(sender, text, "clan", (player, incomingText) -> resolveHybridClanAddressedFakePlayers(clan, incomingText), fakePlayerName -> processIncomingChannelMessage(sender, fakePlayerName, text, "clan", "player_clan",
			(name, player, incomingText, channel, inputGuard) -> inputGuard.isSensitive() ? buildGuardedReplyPlan(name, player, channel, inputGuard) : _decisionEngine.createClanAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText),
			(plan, name, player, incomingText, channel) -> forceClanReplyPlan(plan, name, player.getName(), incomingText),
			plan -> _chatService.tryEmitClanLine(clan, plan),
			(name, player, socialSignal) ->
			{
				if (socialSignal.hasNegativeSignal())
				{
					final FpcDefinition definition = findFpcDefinition(name);
					if (definition != null)
					{
						evaluateHybridClanPressure(definition, clan, player, "clan_chat_negative");
					}
				}
			},
			(name, player, plan) ->
			{
			}));
	}

	public boolean handleHybridClanMemberOusted(Player actor, Clan clan, String memberName)
	{
		if ((actor == null) || (clan == null) || (memberName == null) || memberName.isBlank())
		{
			return false;
		}

		final HybridClanMemberSnapshot member = _hybridClanService.getHybridClanMember(clan, memberName);
		if (member == null)
		{
			return false;
		}

		clearHybridClanSocialMemory(member.getName(), clan);
		recordHybridClanLifecycleBreach(member.getName(), actor.getName(), "clan_kicked_me", "player kicked the FPC from the clan");
		resetHybridClanWarmth(member.getName(), clan, "clan_kicked_me", 2, 2);
		_hybridClanService.clearHybridContract(member.getFpcId(), "ousted_by_clan");
		LOGGER.info(() -> getClass().getSimpleName() + ": Hybrid clan member ousted fpc=" + member.getFpcId() + " clan=" + clan.getName() + " actor=" + actor.getName());
		return true;
	}

	public void handleHybridClanDisband(Clan clan)
	{
		if (clan == null)
		{
			return;
		}

		final List<HybridClanMemberSnapshot> hybridMembers = _hybridClanService.getHybridClanMembers(clan);
		if (hybridMembers.isEmpty())
		{
			return;
		}

		for (HybridClanMemberSnapshot member : hybridMembers)
		{
			clearHybridClanSocialMemory(member.getName(), clan);
			recordHybridClanLifecycleBreach(member.getName(), clan.getLeaderName(), "clan_disbanded", "clan dissolved while the FPC still wore its crest");
			resetHybridClanWarmth(member.getName(), clan, "clan_disbanded", 2, 2);
			_hybridClanService.clearHybridContract(member.getFpcId(), "clan_disbanded");
		}
		LOGGER.info(() -> getClass().getSimpleName() + ": Cleared " + hybridMembers.size() + " hybrid clan contracts because clan=" + clan.getName() + " disbanded.");
	}
	
	public Collection<FpcDefinition> getLoadedFpcDefinitions()
	{
		return _manager.getLoadedFpcDefinitions();
	}
	
	public void reloadFpcDefinitions()
	{
		_manager.reloadFpcDefinitions();
		_hybridClanService.restorePersistedContracts();
		refreshClanRequestGuard();
	}

	public void reloadPersonaProfiles()
	{
		_personaData.load();
	}

	public void reloadReplyBanks()
	{
		_replyBankData.load();
	}

	public void reloadKnowledgeCards()
	{
		_knowledgeData.load();
	}

	public void reloadReplySettings()
	{
		_replySettingsData.load();
	}

	public void reloadRoutingRules()
	{
		_routingRuleData.load();
		_messageClassifier.setRoutingRules(_routingRuleData.getRules());
	}
	
	public boolean spawnFpc(String id)
	{
		return _manager.spawnFpc(id);
	}
	
	public boolean despawnFpc(String id)
	{
		return _manager.despawnFpc(id);
	}

	public Map<String, Object> probeConversation(String idOrName, String senderName, String channel, String incomingText)
	{
		final Map<String, Object> result = new LinkedHashMap<>();
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if (definition == null)
		{
			result.put("status", "error");
			result.put("message", "Unknown FPC: " + idOrName);
			return result;
		}

		final String fakePlayerName = definition.getName();
		final String normalizedSenderName = ((senderName == null) || senderName.isBlank()) ? "StudioUser" : senderName.trim();
		final String normalizedChannel = normalizeStudioChannel(channel);
		final String normalizedIncomingText = normalizeReplyInputText(incomingText);
		if (normalizedIncomingText.isBlank())
		{
			result.put("status", "error");
			result.put("message", "Probe text is empty.");
			return result;
		}

		final Npc npc = findActiveFpcNpc(definition.getId());
		String currentZone = (npc == null) ? definition.getSpawnProfile().getZone() : _routeService.resolveCurrentZone(npc, definition.getSpawnProfile().getZone());
		if ((currentZone == null) || currentZone.isBlank())
		{
			currentZone = _ruleset.getDefaultSocialZoneId();
		}

		final FpcConversationGuardAssessment inputGuard = _conversationGuardService.evaluateIncoming(fakePlayerName, normalizedSenderName, normalizedIncomingText);
		final ReplyAnalysisSnapshot analysis = analyzeReply(fakePlayerName, normalizedSenderName, normalizedIncomingText, normalizedChannel);
		final String effectiveIncomingText = analysis._effectiveIncomingText;
		final FpcRouteProfile routeProfile = analysis._routeProfile;
		final String messageCategory = analysis._messageCategory;
		final String focusEntityName = analysis._focusEntityName;
		final String focusIntent = analysis._focusIntent;
		final String knowledgeType = analysis._knowledgeType;

		final FakePlayerAdvisoryPlan plan = finalizeReplyPlan(inputGuard.isSensitive() ? buildGuardedReplyPlan(fakePlayerName, normalizedSenderName, normalizedChannel, inputGuard) : createStudioProbePlan(fakePlayerName, currentZone, normalizedSenderName, normalizedIncomingText, normalizedChannel), fakePlayerName, normalizedSenderName, normalizedIncomingText, normalizedChannel, inputGuard,
			() -> buildGuardedReplyPlan(fakePlayerName, normalizedSenderName, normalizedChannel, inputGuard),
			currentPlan -> forceStudioProbeReplyPlan(currentPlan, fakePlayerName, normalizedSenderName, normalizedIncomingText, normalizedChannel));

		final ResolvedFpcPersona persona = _personaResolver.resolve(fakePlayerName, definition.getArchetype(), normalizedSenderName);
		final FpcMemoryBundle memoryBundle = new FakePlayerMemoryService(_chatService, _socialMemoryService).buildReplyMemory(fakePlayerName, normalizedSenderName, effectiveIncomingText, messageCategory, focusIntent, persona.getMemoryRetrievalPolicy(), persona.getMatchedRelationshipSummary());
		final FpcRelationshipSnapshot relationshipSnapshot = memoryBundle.getRelationshipSnapshot();
		final FpcSocialSnapshot socialSnapshot = memoryBundle.getSocialSnapshot();

		final Map<String, Object> probe = new LinkedHashMap<>();
		probe.put("speakerId", definition.getId());
		probe.put("speakerName", fakePlayerName);
		probe.put("playerName", normalizedSenderName);
		probe.put("channel", normalizedChannel);
		probe.put("currentZone", currentZone);
		probe.put("incomingText", normalizedIncomingText);

		final Map<String, Object> guard = new LinkedHashMap<>();
		guard.put("category", inputGuard.getCategory());
		guard.put("domain", inputGuard.getDomain());
		guard.put("knowledgeHint", inputGuard.getKnowledgeHint());
		guard.put("mixedKnowledgeRedirect", inputGuard.isMixedKnowledgeRedirect());
		guard.put("sensitiveFollowUp", inputGuard.isSensitiveFollowUp());
		guard.put("sensitive", inputGuard.isSensitive());
		probe.put("guard", guard);

		final Map<String, Object> classification = new LinkedHashMap<>();
		classification.put("messageCategory", messageCategory);
		classification.put("knowledgeType", knowledgeType);
		classification.put("routeProfile", toRouteProfileMap(routeProfile));
		classification.put("focusEntityName", focusEntityName);
		classification.put("focusIntent", focusIntent);
		classification.put("effectiveIncomingText", effectiveIncomingText);
		probe.put("classification", classification);

		final Map<String, Object> reply = new LinkedHashMap<>();
		reply.put("source", (plan == null) ? "" : plan.getReplySource());
		reply.put("line", (plan == null) ? "" : plan.getSelectedLine());
		reply.put("moodTag", (plan == null) ? "" : plan.getMoodTag());
		reply.put("intentPreference", (plan == null) ? "" : plan.getIntentPreference());
		reply.put("preferredZone", (plan == null) ? "" : plan.getPreferredZone());
		reply.put("lineStyleTag", (plan == null) ? "" : plan.getLineStyleTag());
		reply.put("topicTag", (plan == null) ? "" : plan.getTopicTag());
		reply.put("confidence", (plan == null) ? 0.0 : plan.getConfidence());
		reply.put("speakNow", (plan != null) && plan.isSpeakNow());
		probe.put("reply", reply);

		putStudioMemorySnapshotFields(probe, memoryBundle, persona, relationshipSnapshot, socialSnapshot);

		result.put("status", "ok");
		result.put("message", "Conversation probe completed for " + fakePlayerName + ".");
		result.put("probe", probe);
		final String replySource = (plan == null) ? "" : plan.getReplySource();
		LOGGER.info(() -> getClass().getSimpleName() + ": Studio conversation probe speaker=" + fakePlayerName + " player=" + normalizedSenderName + " channel=" + normalizedChannel + " category=" + messageCategory + " knowledgeType=" + knowledgeType + " source=" + replySource);
		_debugService.trace(definition.getId(), FpcDebugCategory.CHAT, "event=studio_probe player=" + normalizedSenderName + " channel=" + normalizedChannel + " category=" + messageCategory + " knowledgeType=" + knowledgeType + " source=" + replySource);
		return result;
	}

	public Map<String, Object> inspectSocialPair(String idOrName, String playerName)
	{
		final Map<String, Object> result = new LinkedHashMap<>();
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if (definition == null)
		{
			result.put("status", "error");
			result.put("message", "Unknown FPC: " + idOrName);
			return result;
		}

		final String normalizedPlayerName = ((playerName == null) || playerName.isBlank()) ? "" : playerName.trim();
		if (normalizedPlayerName.isBlank())
		{
			result.put("status", "error");
			result.put("message", "Player name is empty.");
			return result;
		}

		final Npc npc = findActiveFpcNpc(definition.getId());
		String currentZone = (npc == null) ? definition.getSpawnProfile().getZone() : _routeService.resolveCurrentZone(npc, definition.getSpawnProfile().getZone());
		if ((currentZone == null) || currentZone.isBlank())
		{
			currentZone = _ruleset.getDefaultSocialZoneId();
		}

		final ResolvedFpcPersona persona = _personaResolver.resolve(definition.getName(), definition.getArchetype(), normalizedPlayerName);
		final FpcMemoryBundle memoryBundle = new FakePlayerMemoryService(_chatService, _socialMemoryService).buildReplyMemory(definition.getName(), normalizedPlayerName, "", "", "", persona.getMemoryRetrievalPolicy(), persona.getMatchedRelationshipSummary());
		final FpcRelationshipSnapshot relationshipSnapshot = memoryBundle.getRelationshipSnapshot();
		final FpcSocialSnapshot socialSnapshot = memoryBundle.getSocialSnapshot();

		final Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("speakerId", definition.getId());
		snapshot.put("speakerName", definition.getName());
		snapshot.put("playerName", normalizedPlayerName);
		snapshot.put("currentZone", currentZone);
		snapshot.put("live", npc != null);
		snapshot.put("state", (npc == null) ? "inactive" : (npc.isInCombat() ? "combat" : (npc.isMoving() ? "moving" : (npc.isDead() ? "dead" : "idle"))));

		putStudioMemorySnapshotFields(snapshot, memoryBundle, persona, relationshipSnapshot, socialSnapshot);

		result.put("status", "ok");
		result.put("message", "Social snapshot loaded for " + definition.getName() + " <-> " + normalizedPlayerName + ".");
		result.put("snapshot", snapshot);
		LOGGER.info(() -> getClass().getSimpleName() + ": Studio social snapshot speaker=" + definition.getName() + " player=" + normalizedPlayerName + " socialLabel=" + socialSnapshot.getSocialLabel());
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=studio_social_snapshot player=" + normalizedPlayerName + " socialLabel=" + socialSnapshot.getSocialLabel());
		return result;
	}

	private void putInnerStateProbeFields(Map<String, Object> target, ResolvedFpcPersona persona)
	{
		if ((target == null) || (persona == null))
		{
			return;
		}

		final Map<String, Object> innerState = new LinkedHashMap<>();
		putInnerStateValue(target, innerState, "defaultInnerState", persona.getDefaultInnerState());
		putInnerStateValue(target, innerState, "longTermGoal", persona.getLongTermGoal());
		putInnerStateValue(target, innerState, "socialTestStyle", persona.getSocialTestStyle());
		putInnerStateValue(target, innerState, "trustCriteria", persona.getTrustCriteria());
		putInnerStateValue(target, innerState, "repairStyle", persona.getRepairStyle());
		putInnerStateValue(target, innerState, "revealBoundary", persona.getRevealBoundary());
		putInnerStateValue(target, innerState, "reflectionLens", persona.getReflectionLens());
		putInnerStateValue(target, innerState, "activeObjective", persona.getActiveObjective());
		putInnerStateValue(target, innerState, "openLoops", persona.getOpenLoops());
		putInnerStateValue(target, innerState, "revealPressure", persona.getRevealPressure());
		putInnerStateValue(target, innerState, "relationshipPressure", persona.getRelationshipPressure());
		putInnerStateValue(target, innerState, "nextBeatHint", persona.getNextBeatHint());
		putInnerStateValue(target, innerState, "stateModes", persona.getStateModes());
		putInnerStateValue(target, innerState, "reflectionPolicy", persona.getReflectionPolicy());
		putInnerStateValue(target, innerState, "memoryRetrievalPolicy", persona.getMemoryRetrievalPolicy());
		putInnerStateValue(target, innerState, "goalPersistencePolicy", persona.getGoalPersistencePolicy());
		putInnerStateValue(target, innerState, "emotionTransitionRules", persona.getEmotionTransitionRules());
		putInnerStateValue(target, innerState, "callbackStyle", persona.getCallbackStyle());
		putInnerStateValue(target, innerState, "conflictStyle", persona.getConflictStyle());
		putInnerStateValue(target, innerState, "repairCadence", persona.getRepairCadence());
		if (!innerState.isEmpty())
		{
			target.put("innerState", innerState);
		}
	}

	private void putStudioMemorySnapshotFields(Map<String, Object> target, FpcMemoryBundle memoryBundle, ResolvedFpcPersona persona, FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot)
	{
		if ((target == null) || (memoryBundle == null) || (persona == null) || (relationshipSnapshot == null) || (socialSnapshot == null))
		{
			return;
		}

		final List<Map<String, Object>> retrievalSections = buildRetrievalSectionPayload(memoryBundle.getRetrievalSections());
		final Map<String, Object> relationship = new LinkedHashMap<>();
		relationship.put("familiarity", relationshipSnapshot.getFamiliarity());
		relationship.put("trust", relationshipSnapshot.getTrust());
		relationship.put("respect", relationshipSnapshot.getRespect());
		relationship.put("tension", relationshipSnapshot.getTension());
		relationship.put("resentment", relationshipSnapshot.getResentment());
		relationship.put("playerKillCount", relationshipSnapshot.getPlayerKillCount());
		relationship.put("currentGoal", relationshipSnapshot.getCurrentGoal());
		relationship.put("activeNeed", relationshipSnapshot.getActiveNeed());
		relationship.put("lastImportantTopic", relationshipSnapshot.getLastImportantTopic());
		relationship.put("recentConversationSummary", memoryBundle.getRecentConversationSummary());
		relationship.put("salientMemorySummary", memoryBundle.getSalientMemorySummary());
		relationship.put("matchedRelationshipSummary", persona.getMatchedRelationshipSummary());
		relationship.put("retrievedMemorySummary", memoryBundle.getRetrievedMemorySummary());
		relationship.put("memoryPrioritySummary", memoryBundle.getMemoryPrioritySummary());
		relationship.put("memoryReflectionSummary", memoryBundle.getMemoryReflectionSummary());
		relationship.put("memoryTrustSummary", memoryBundle.getMemoryTrustSummary());
		relationship.put("memoryRepairSummary", memoryBundle.getMemoryRepairSummary());
		relationship.put("memoryPressureSummary", memoryBundle.getMemoryPressureSummary());
		relationship.put("memorySelectionSummary", memoryBundle.getMemorySelectionSummary());
		relationship.put("retrievalSections", retrievalSections);
		target.put("relationship", relationship);
		target.put("matchedRelationshipSummary", persona.getMatchedRelationshipSummary());
		target.put("retrievedMemorySummary", memoryBundle.getRetrievedMemorySummary());
		target.put("memoryPrioritySummary", memoryBundle.getMemoryPrioritySummary());
		target.put("memoryReflectionSummary", memoryBundle.getMemoryReflectionSummary());
		target.put("memoryTrustSummary", memoryBundle.getMemoryTrustSummary());
		target.put("memoryRepairSummary", memoryBundle.getMemoryRepairSummary());
		target.put("memoryPressureSummary", memoryBundle.getMemoryPressureSummary());
		target.put("memorySelectionSummary", memoryBundle.getMemorySelectionSummary());
		target.put("retrievalSections", retrievalSections);

		final Map<String, Object> social = new LinkedHashMap<>();
		social.put("socialLabel", socialSnapshot.getSocialLabel());
		social.put("summary", socialSnapshot.getSummary());
		social.put("trustBias", socialSnapshot.getTrustBias());
		social.put("guardBias", socialSnapshot.getGuardBias());
		social.put("actionStance", socialSnapshot.getActionStance().describe());
		social.put("historicalKillHistory", socialSnapshot.hasHistoricalKillHistory());
		target.put("social", social);
		putInnerStateProbeFields(target, persona);
	}

	private List<Map<String, Object>> buildRetrievalSectionPayload(List<FpcRetrievedMemorySection> retrievalSections)
	{
		final List<Map<String, Object>> payload = new ArrayList<>();
		if ((retrievalSections == null) || retrievalSections.isEmpty())
		{
			return payload;
		}

		for (FpcRetrievedMemorySection section : retrievalSections)
		{
			if (section == null)
			{
				continue;
			}
			final Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("label", section.getLabel());
			entry.put("value", section.getValue());
			entry.put("score", section.getScore());
			payload.add(entry);
		}
		return payload;
	}

	private void putInnerStateValue(Map<String, Object> target, Map<String, Object> innerState, String key, String value)
	{
		if ((target == null) || (innerState == null) || (key == null) || key.isBlank() || (value == null) || value.isBlank())
		{
			return;
		}
		target.put(key, value);
		innerState.put(key, value);
	}

	public boolean handleIncomingPartyInvite(Player sender, String fakePlayerName, PartyDistributionType distributionType)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank())
		{
			return false;
		}

		final FpcDefinition definition = findFpcDefinition(fakePlayerName);
		if ((definition == null) || !definition.getAdventurerProfile().isAdventurerTier() || !definition.getAdventurerProfile().supportsPartyCoordination())
		{
			return false;
		}

		final Party senderParty = sender.getParty();
		if ((senderParty != null) && !senderParty.isLeader(sender))
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.ONLY_THE_LEADER_CAN_GIVE_OUT_INVITATIONS);
			return true;
		}

		if (!_hybridPartyService.canAcceptAdditionalHybridMember(sender))
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.THE_PARTY_IS_FULL);
			return true;
		}

		final Npc npc = findActiveFpcNpc(definition.getId());
		if ((npc == null) || npc.isDead())
		{
			return false;
		}

		final FakePlayerHybridPartyService.HybridPartyInviteState inviteState = _hybridPartyService.inspectInviteState(definition, sender);
		if (inviteState.isActiveWithLeader())
		{
			sender.sendMessage(definition.getName() + " is already traveling with you.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC companion contract already active id=" + definition.getId() + " leader=" + sender.getName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_already_active leader=" + sender.getName());
			return true;
		}

		_socialMemoryService.recordEpisode(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_REQUESTED);
		recordInviteSpamIfNeeded(definition, sender);
		final FpcRelationshipSnapshot relationship = _chatService.getRelationshipSnapshot(definition.getName(), sender.getName());
		final FpcSocialSnapshot socialSnapshot = _socialMemoryService.getSnapshot(definition.getName(), sender.getName());
		final boolean rapidReinvitePressure = hasRapidReinvitePressure(inviteState);
		logPartyInviteEvaluation(definition, sender, relationship, socialSnapshot, inviteState);
		FakePlayerSocialService.PartyInviteDecision decision = _socialService.evaluatePartyInvite(definition, sender, relationship, socialSnapshot, rapidReinvitePressure);
		if (!decision.isAccepted())
		{
			final String declinedLine = _socialService.choosePartyDeclineLine(definition, sender, decision);
			if (!declinedLine.isBlank())
			{
				_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), declinedLine);
				_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "party invite", declinedLine);
			}
			_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_DECLINED, rapidReinvitePressure ? "player tried to re-invite too soon after breaking party rhythm" : "", PARTY_DECLINE_EPISODE_SPACING_MS);
			sender.sendMessage(definition.getName() + " refused to travel with you right now.");
			final int declinedScore = decision.getScore();
			final String declinedReason = decision.getReason();
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC companion contract declined id=" + definition.getId() + " leader=" + sender.getName() + " score=" + declinedScore + " reason=" + declinedReason);
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_declined leader=" + sender.getName() + " score=" + declinedScore + " reason=" + declinedReason);
			return true;
		}
		if (requiresGoodFriendCompanionBond(definition) && !_afpcCombatPolicyService.isGoodFriend(definition, sender))
		{
			final String declinedLine = chooseCompanionRelationGateDeclineLine(definition, true);
			if (!declinedLine.isBlank())
			{
				_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), declinedLine);
				_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "party invite", declinedLine);
			}
			_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_DECLINED, "relation_gate_good_friend", PARTY_DECLINE_EPISODE_SPACING_MS);
			sender.sendMessage(definition.getName() + " only travels that closely with trusted friends.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC companion contract declined by relation gate id=" + definition.getId() + " leader=" + sender.getName() + " gate=good_friend");
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_declined relationGate=good_friend leader=" + sender.getName());
			return true;
		}

		final FakePlayerHybridPartyService.HybridContractOfferResult offerResult = _hybridPartyService.offerHybridContract(definition, sender, distributionType);
		if (offerResult == FakePlayerHybridPartyService.HybridContractOfferResult.ALREADY_ACTIVE_WITH_LEADER)
		{
			sender.sendMessage(definition.getName() + " is already traveling with you.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC companion contract already active after invite evaluation id=" + definition.getId() + " leader=" + sender.getName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_already_active_post_eval leader=" + sender.getName());
			return true;
		}
		if (offerResult != FakePlayerHybridPartyService.HybridContractOfferResult.ACCEPTED)
		{
			sender.sendMessage(definition.getName() + " cannot join your party right now.");
			return true;
		}

		final String acceptedLine = _socialService.choosePartyAcceptanceLine(definition, sender, decision);
		if (!acceptedLine.isBlank())
		{
			_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), acceptedLine);
			_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "party invite", acceptedLine);
		}
		_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_ACCEPTED, "", PARTY_ACCEPT_EPISODE_SPACING_MS);
		sender.sendMessage(definition.getName() + " joined your party.");
		final int acceptedScore = decision.getScore();
		final String acceptedStance = decision.getStance();
		final String acceptanceReason = decision.getReason();
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC companion contract accepted id=" + definition.getId() + " leader=" + sender.getName() + " score=" + acceptedScore + " stance=" + acceptedStance + " reason=" + acceptanceReason + " distribution=" + ((distributionType == null) ? "unknown" : distributionType.name().toLowerCase(Locale.ENGLISH)));
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_accepted leader=" + sender.getName() + " score=" + acceptedScore + " stance=" + acceptedStance + " reason=" + acceptanceReason);
		return true;
	}

	public boolean handleIncomingClanInvite(Player sender, String fakePlayerName, int pledgeType)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank())
		{
			return false;
		}

		final FpcDefinition definition = findFpcDefinition(fakePlayerName);
		if ((definition == null) || !_hybridClanService.supportsHybridClanMembership(definition))
		{
			return false;
		}

		final Clan clan = sender.getClan();
		if (clan == null)
		{
			return false;
		}

		final Npc npc = findActiveFpcNpc(definition.getId());
		if ((npc == null) || npc.isDead())
		{
			return false;
		}

		if (!checkHybridClanInviteCondition(clan, sender, definition, pledgeType))
		{
			return true;
		}

		final FakePlayerHybridClanService.HybridClanInviteState inviteState = _hybridClanService.inspectInviteState(definition, clan);
		if (inviteState.isActiveWithClan())
		{
			sender.sendMessage(definition.getName() + " is already under your clan crest.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC hybrid clan contract already active id=" + definition.getId() + " clan=" + clan.getName() + " leader=" + sender.getName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_already_active clan=" + clan.getName() + " leader=" + sender.getName());
			return true;
		}
		if (inviteState.isActiveWithOtherClan())
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.THIS_CHARACTER_IS_A_MEMBER_OF_ANOTHER_CLAN);
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC hybrid clan contract blocked id=" + definition.getId() + " requestedClan=" + clan.getName() + " existingClan=" + inviteState.getCurrentClanName());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_blocked_other_clan requestedClan=" + clan.getName() + " existingClan=" + inviteState.getCurrentClanName());
			return true;
		}

		final FpcRelationshipSnapshot relationship = _chatService.getRelationshipSnapshot(definition.getName(), sender.getName());
		final FpcSocialSnapshot socialSnapshot = _socialMemoryService.getSnapshot(definition.getName(), sender.getName());
		logClanInviteEvaluation(definition, sender, relationship, socialSnapshot, inviteState, pledgeType);
		final FakePlayerHybridClanService.HybridClanInviteDecision decision = _hybridClanService.evaluateClanInvite(definition, sender, relationship, socialSnapshot);
		if (!decision.isAccepted())
		{
			final String declinedLine = _hybridClanService.chooseDeclineLine(definition, decision);
			if (!declinedLine.isBlank())
			{
				_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), declinedLine);
				_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "clan invite", declinedLine);
			}
			sender.sendMessage(definition.getName() + " refused your clan invitation for now.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC hybrid clan contract declined id=" + definition.getId() + " clan=" + clan.getName() + " leader=" + sender.getName() + " score=" + decision.getScore() + " reason=" + decision.getReason());
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_declined clan=" + clan.getName() + " leader=" + sender.getName() + " score=" + decision.getScore() + " reason=" + decision.getReason());
			return true;
		}
		if (requiresManualExclusiveTrustedClan(definition) && !_afpcCombatPolicyService.isGoodFriend(definition, sender))
		{
			final String declinedLine = chooseCompanionRelationGateDeclineLine(definition, false);
			if (!declinedLine.isBlank())
			{
				_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), declinedLine);
				_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "clan invite", declinedLine);
			}
			sender.sendMessage(definition.getName() + " will only take a clan crest from a trusted friend.");
			LOGGER.info(() -> getClass().getSimpleName() + ": AFPC hybrid clan contract declined by relation gate id=" + definition.getId() + " clan=" + clan.getName() + " leader=" + sender.getName() + " gate=good_friend");
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_declined relationGate=good_friend clan=" + clan.getName() + " leader=" + sender.getName());
			return true;
		}

		final FakePlayerHybridClanService.HybridClanOfferResult offerResult = _hybridClanService.offerHybridContract(definition, sender, pledgeType);
		if (offerResult == FakePlayerHybridClanService.HybridClanOfferResult.ALREADY_ACTIVE_WITH_CLAN)
		{
			sender.sendMessage(definition.getName() + " is already under your clan crest.");
			return true;
		}
		if (offerResult == FakePlayerHybridClanService.HybridClanOfferResult.BLOCKED_OTHER_CLAN)
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.THIS_CHARACTER_IS_A_MEMBER_OF_ANOTHER_CLAN);
			return true;
		}
		if (offerResult != FakePlayerHybridClanService.HybridClanOfferResult.ACCEPTED)
		{
			sender.sendMessage(definition.getName() + " cannot join your clan right now.");
			return true;
		}

		final String acceptedLine = _hybridClanService.chooseAcceptanceLine(definition, decision);
		if (!acceptedLine.isBlank())
		{
			_platformAdapter.getChatTransport().sendPrivateReply(sender, npc, definition.getName(), acceptedLine);
			_chatService.rememberSuccessfulExchange(definition.getName(), sender.getName(), "whisper", "clan invite", acceptedLine);
		}
		final org.l2jmobius.gameserver.network.serverpackets.SystemMessage clanJoinedMessage = new org.l2jmobius.gameserver.network.serverpackets.SystemMessage(org.l2jmobius.gameserver.network.SystemMessageId.S1_HAS_JOINED_THE_CLAN);
		clanJoinedMessage.addString(definition.getName());
		clan.broadcastToOnlineMembers(clanJoinedMessage);
		sender.sendMessage(definition.getName() + " accepted your clan invitation.");
		npc.broadcastInfo();
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC hybrid clan contract accepted id=" + definition.getId() + " clan=" + clan.getName() + " leader=" + sender.getName() + " score=" + decision.getScore() + " stance=" + decision.getStance() + " pledgeType=" + pledgeType);
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_accepted clan=" + clan.getName() + " leader=" + sender.getName() + " score=" + decision.getScore() + " stance=" + decision.getStance() + " pledgeType=" + pledgeType);
		return true;
	}

	public boolean handleIncomingClanInvite(Player sender, Npc npc, int pledgeType)
	{
		final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
		if ((definition == null) || !definition.isTalkable())
		{
			return false;
		}
		return handleIncomingClanInvite(sender, definition.getName(), pledgeType);
	}

	private void logPartyInviteEvaluation(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialSnapshot, FakePlayerHybridPartyService.HybridPartyInviteState inviteState)
	{
		if ((definition == null) || (sender == null))
		{
			return;
		}

		final FpcRelationshipSnapshot snapshot = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		final FakePlayerHybridPartyService.HybridPartyInviteState state = (inviteState == null) ? FakePlayerHybridPartyService.HybridPartyInviteState.EMPTY : inviteState;
		final String socialSummary = social.getSummary().replace('"', '\'');
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC invite evaluation id=" + definition.getId() + " leader=" + sender.getName() + " relationship={familiarity=" + snapshot.getFamiliarity() + ", trust=" + snapshot.getTrust() + ", respect=" + snapshot.getRespect() + ", tension=" + snapshot.getTension() + ", resentment=" + snapshot.getResentment() + ", playerKillCount=" + snapshot.getPlayerKillCount() + ", currentGoal=" + snapshot.getCurrentGoal() + ", activeNeed=" + snapshot.getActiveNeed() + ", lastTopic=" + snapshot.getLastImportantTopic() + "} social={label=" + social.getSocialLabel() + ", trustBias=" + social.getTrustBias() + ", guardBias=" + social.getGuardBias() + ", actionStance=" + social.getActionStance().describe() + ", summary=\"" + socialSummary + "\"} contract={activeWithLeader=" + state.isActiveWithLeader() + ", activeWithOtherLeader=" + state.isActiveWithOtherLeader() + ", recentExitReason=" + state.getRecentExitReason() + ", recentExitAgeMs=" + state.getRecentExitAgeMs() + ", recentLifetimeMs=" + state.getRecentLifetimeMs() + "} player={reputation=" + sender.getReputation() + ", pkKills=" + sender.getPkKills() + ", pvpFlag=" + sender.getPvpFlag() + "}");
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=party_evaluation leader=" + sender.getName() + " familiarity=" + snapshot.getFamiliarity() + " trust=" + snapshot.getTrust() + " respect=" + snapshot.getRespect() + " tension=" + snapshot.getTension() + " resentment=" + snapshot.getResentment() + " playerKillCount=" + snapshot.getPlayerKillCount() + " socialLabel=" + social.getSocialLabel() + " socialTrustBias=" + social.getTrustBias() + " socialGuardBias=" + social.getGuardBias() + " socialActionStance=\"" + social.getActionStance().describe() + "\" recentExitReason=" + state.getRecentExitReason() + " recentExitAgeMs=" + state.getRecentExitAgeMs());
	}

	private boolean checkHybridClanInviteCondition(Clan clan, Player sender, FpcDefinition definition, int pledgeType)
	{
		if ((clan == null) || (sender == null) || (definition == null))
		{
			return false;
		}
		if (!sender.hasAccess(ClanAccess.INVITE_MEMBER))
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		if (clan.getCharPenaltyExpiryTime() > System.currentTimeMillis())
		{
			sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.YOU_CANNOT_ACCEPT_A_NEW_CLAN_MEMBER_FOR_24_H_AFTER_DISMISSING_SOMEONE);
			return false;
		}
		if (pledgeType == Clan.SUBUNIT_ACADEMY)
		{
			final org.l2jmobius.gameserver.network.serverpackets.SystemMessage sm = new org.l2jmobius.gameserver.network.serverpackets.SystemMessage(org.l2jmobius.gameserver.network.SystemMessageId.S1_DOES_NOT_MEET_THE_REQUIREMENTS_TO_JOIN_A_CLAN_ACADEMY);
			sm.addString(definition.getName());
			sender.sendPacket(sm);
			return false;
		}

		final int activeMembers = clan.getSubPledgeMembersCount(pledgeType) + _hybridClanService.getHybridMemberCount(clan, pledgeType);
		if (activeMembers >= clan.getMaxNrOfMembers(pledgeType))
		{
			if (pledgeType == 0)
			{
				final org.l2jmobius.gameserver.network.serverpackets.SystemMessage sm = new org.l2jmobius.gameserver.network.serverpackets.SystemMessage(org.l2jmobius.gameserver.network.SystemMessageId.S1_IS_FULL_AND_CANNOT_ACCEPT_ADDITIONAL_CLAN_MEMBERS_AT_THIS_TIME);
				sm.addString(clan.getName());
				sender.sendPacket(sm);
			}
			else
			{
				sender.sendPacket(org.l2jmobius.gameserver.network.SystemMessageId.THE_CLAN_IS_FULL);
			}
			return false;
		}
		return true;
	}

	private void logClanInviteEvaluation(FpcDefinition definition, Player sender, FpcRelationshipSnapshot relationship, FpcSocialSnapshot socialSnapshot, FakePlayerHybridClanService.HybridClanInviteState inviteState, int pledgeType)
	{
		if ((definition == null) || (sender == null))
		{
			return;
		}

		final FpcRelationshipSnapshot snapshot = (relationship == null) ? FpcRelationshipSnapshot.EMPTY : relationship;
		final FpcSocialSnapshot social = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		final FakePlayerHybridClanService.HybridClanInviteState state = (inviteState == null) ? FakePlayerHybridClanService.HybridClanInviteState.EMPTY : inviteState;
		final String socialSummary = social.getSummary().replace('"', '\'');
		final Clan clan = sender.getClan();
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC clan invite evaluation id=" + definition.getId() + " leader=" + sender.getName() + " clan=" + ((clan == null) ? "none" : clan.getName()) + " relationship={familiarity=" + snapshot.getFamiliarity() + ", trust=" + snapshot.getTrust() + ", respect=" + snapshot.getRespect() + ", tension=" + snapshot.getTension() + ", resentment=" + snapshot.getResentment() + ", playerKillCount=" + snapshot.getPlayerKillCount() + ", currentGoal=" + snapshot.getCurrentGoal() + ", activeNeed=" + snapshot.getActiveNeed() + ", lastTopic=" + snapshot.getLastImportantTopic() + "} social={label=" + social.getSocialLabel() + ", trustBias=" + social.getTrustBias() + ", guardBias=" + social.getGuardBias() + ", actionStance=" + social.getActionStance().describe() + ", summary=\"" + socialSummary + "\"} contract={activeWithClan=" + state.isActiveWithClan() + ", activeWithOtherClan=" + state.isActiveWithOtherClan() + ", currentClan=" + state.getCurrentClanName() + ", pledgeType=" + state.getPledgeType() + "} player={reputation=" + sender.getReputation() + ", pkKills=" + sender.getPkKills() + ", pvpFlag=" + sender.getPvpFlag() + "} requestPledgeType=" + pledgeType);
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=clan_evaluation leader=" + sender.getName() + " clan=" + ((clan == null) ? "none" : clan.getName()) + " familiarity=" + snapshot.getFamiliarity() + " trust=" + snapshot.getTrust() + " respect=" + snapshot.getRespect() + " tension=" + snapshot.getTension() + " resentment=" + snapshot.getResentment() + " playerKillCount=" + snapshot.getPlayerKillCount() + " socialLabel=" + social.getSocialLabel() + " socialTrustBias=" + social.getTrustBias() + " socialGuardBias=" + social.getGuardBias() + " socialActionStance=\"" + social.getActionStance().describe() + "\" contractClan=\"" + state.getCurrentClanName() + "\" requestPledgeType=" + pledgeType);
	}

	private void refreshClanRequestGuard()
	{
		boolean hasHybridClanCapability = false;
		for (FpcDefinition definition : _profileData.getDefinitions())
		{
			if (_hybridClanService.supportsHybridClanMembership(definition))
			{
				hasHybridClanCapability = true;
				break;
			}
		}
		_conversationGuardService.setClanRequestsSupported(hasHybridClanCapability);
	}

	private boolean hasRapidReinvitePressure(FakePlayerHybridPartyService.HybridPartyInviteState inviteState)
	{
		if ((inviteState == null) || !inviteState.hasRecentExit() || (inviteState.getRecentExitAgeMs() > PARTY_REINVITE_COOLDOWN_MS))
		{
			return false;
		}

		return !"expired".equalsIgnoreCase(inviteState.getRecentExitReason()) && !"party_anchor_changed".equalsIgnoreCase(inviteState.getRecentExitReason());
	}

	public FpcDefinition findFpcDefinition(String idOrName)
	{
		if ((idOrName == null) || idOrName.isBlank())
		{
			return null;
		}

		final String lookup = idOrName.trim();
		FpcDefinition definition = _profileData.getRegistry().getDefinitionById(lookup);
		if (definition == null)
		{
			definition = _profileData.getRegistry().getDefinitionByName(lookup);
		}
		return definition;
	}

	public Npc findActiveFpcNpc(String idOrName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if (definition == null)
		{
			return null;
		}
		return _profileData.getRegistry().resolveActiveNpc(definition.getId());
	}

	public boolean isActiveTalkableWhisperTarget(String idOrName)
	{
		return resolveActiveTalkableDefinition(idOrName) != null;
	}

	public FakePlayerPlatformAdapter getPlatformAdapter()
	{
		return _platformAdapter;
	}

	private void recordInviteSpamIfNeeded(FpcDefinition definition, Player sender)
	{
		if ((definition == null) || (sender == null))
		{
			return;
		}

		final int recentDeclines = _socialMemoryService.countRecentEpisodes(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_DECLINED, 120000L);
		final int recentRequests = _socialMemoryService.countRecentEpisodes(definition.getName(), sender.getName(), FpcSocialEpisodeType.PARTY_INVITE_REQUESTED, 120000L);
		if ((recentDeclines <= 0) || (recentRequests < 2))
		{
			return;
		}

		if (_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), sender.getName(), FpcSocialEpisodeType.SPAMMED_REQUESTS, "player kept pressing after refusal", 45000L))
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": Recorded invite spam social episode id=" + definition.getId() + " leader=" + sender.getName() + " recentRequests=" + recentRequests + " recentDeclines=" + recentDeclines);
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=invite_spam_detected leader=" + sender.getName() + " recentRequests=" + recentRequests + " recentDeclines=" + recentDeclines);
		}
	}

	private SocialSignalAssessment assessIncomingSocialSignal(String fakePlayerName, Player sender, String incomingText, String channelName, String recentEvent)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank() || (incomingText == null) || incomingText.isBlank())
		{
			return new SocialSignalAssessment("", 0, "", false, false, false, false, false, false, false, false, false, false, false, false, false, false);
		}

		final String senderName = sender.getName();
		final ReplyAnalysisSnapshot analysis = analyzeConversation(fakePlayerName, senderName, incomingText);
		final String normalizedIncomingText = analysis._effectiveIncomingText.isBlank() ? normalizeReplyInputText(incomingText) : analysis._effectiveIncomingText;
		final String baseCategory = analysis._messageCategory;
		final boolean suppressDiscoverySocialSignals = shouldSuppressSemanticSocialSignal(baseCategory);
		final String focusEntityName = analysis._focusEntityName;
		final int positiveBondIntensity = _personaResolver.getPositiveBondIntensity(fakePlayerName, focusEntityName);
		final String positiveBondSummary = _personaResolver.summarizePositiveBond(fakePlayerName, focusEntityName);
		final boolean directThreat = _messageClassifier.isThreatSignal(incomingText, fakePlayerName);
		final boolean directFlame = _messageClassifier.isDirectFlame(incomingText, fakePlayerName);
		final boolean directDistrust = !suppressDiscoverySocialSignals && !directThreat && _messageClassifier.isDistrustSignal(incomingText, fakePlayerName);
		final boolean directResentment = !suppressDiscoverySocialSignals && !directThreat && !directDistrust && _messageClassifier.isResentmentSignal(incomingText, fakePlayerName);
		final boolean negativeBeliefConflict = (positiveBondIntensity >= 70) && _messageClassifier.isNegativeSubjectStatement(normalizedIncomingText, focusEntityName);
		final boolean positiveBondAlignment = !directFlame && !negativeBeliefConflict && (positiveBondIntensity >= 70) && _messageClassifier.isPositiveSubjectStatement(normalizedIncomingText, focusEntityName);
		final boolean directApology = !suppressDiscoverySocialSignals && _messageClassifier.isApologySignal(incomingText);
		final boolean directGratitude = !suppressDiscoverySocialSignals && _messageClassifier.isGratitudeSignal(incomingText);
		final boolean directAffection = !suppressDiscoverySocialSignals && !directThreat && !directFlame && !directDistrust && !directResentment && _messageClassifier.isAffectionSignal(incomingText, fakePlayerName);
		final boolean directRespect = !suppressDiscoverySocialSignals && !directThreat && !directFlame && !directDistrust && !directResentment && _messageClassifier.isRespectSignal(incomingText, fakePlayerName);
		final boolean directPraise = !suppressDiscoverySocialSignals && !directThreat && !directFlame && !directDistrust && !directResentment && _messageClassifier.isPraiseSignal(incomingText, fakePlayerName);
		final boolean directReassurance = !suppressDiscoverySocialSignals && !directThreat && !directFlame && !directDistrust && !directResentment && _messageClassifier.isReassuranceSignal(incomingText, fakePlayerName);
		final boolean directAbandonment = !suppressDiscoverySocialSignals && !directThreat && _messageClassifier.isAbandonmentSignal(incomingText, fakePlayerName);
		final boolean repairAttempt = !suppressDiscoverySocialSignals && !directFlame && _messageClassifier.isRepairSignal(incomingText);
		SocialSignalAssessment assessment = new SocialSignalAssessment(focusEntityName, positiveBondIntensity, positiveBondSummary, directThreat, directFlame, directDistrust, directResentment, negativeBeliefConflict, positiveBondAlignment, directApology, directGratitude, directAffection, directRespect, directPraise, directReassurance, directAbandonment, repairAttempt);
		if (!assessment.hasAnySignal() && !shouldSuppressSemanticSocialSignal(baseCategory))
		{
			assessment = applySemanticSocialSignal(fakePlayerName, sender, incomingText, channelName, recentEvent, assessment);
		}
		return assessment;
	}

	private boolean shouldSuppressSemanticSocialSignal(String baseCategory)
	{
		if ((baseCategory == null) || baseCategory.isBlank())
		{
			return false;
		}
		if (_messageClassifier.isSelfhoodCategory(baseCategory))
		{
			return true;
		}
		return "relationship_probe".equalsIgnoreCase(baseCategory);
	}

	private SocialSignalAssessment applySemanticSocialSignal(String fakePlayerName, Player sender, String incomingText, String channelName, String recentEvent, SocialSignalAssessment seed)
	{
		final PersonalityDecision semanticDecision = _decisionEngine.judgeSocialSignal(fakePlayerName, _ruleset.getDefaultSocialZoneId(), channelName, recentEvent, sender, incomingText);
		if (!shouldApplySemanticSocialSignal(semanticDecision))
		{
			return seed;
		}

		final String socialAction = safeLower(semanticDecision.getSocialActionTag());
		final String socialTarget = safeLower(semanticDecision.getSocialTargetTag());
		final boolean impliedSelfTarget = seed._focusEntityName.isBlank() && Set.of("other_subject", "none").contains(socialTarget);
		boolean directThreat = seed._directThreat;
		boolean directFlame = seed._directFlame;
		boolean directDistrust = seed._directDistrust;
		boolean directResentment = seed._directResentment;
		boolean negativeBeliefConflict = seed._negativeBeliefConflict;
		boolean positiveBondAlignment = seed._positiveBondAlignment;
		boolean directApology = seed._directApology;
		boolean directGratitude = seed._directGratitude;
		boolean directAffection = seed._directAffection;
		boolean directRespect = seed._directRespect;
		boolean directPraise = seed._directPraise;
		boolean directReassurance = seed._directReassurance;
		boolean directAbandonment = seed._directAbandonment;
		boolean repairAttempt = seed._repairAttempt;

		if ("self".equals(socialTarget) || impliedSelfTarget)
		{
			switch (socialAction)
			{
				case "threat":
					directThreat = true;
					break;
				case "insult":
					directFlame = true;
					break;
				case "distrust":
					directDistrust = true;
					break;
				case "resentment":
					directResentment = true;
					break;
				case "apology":
					directApology = true;
					break;
				case "gratitude":
					directGratitude = true;
					break;
				case "affection":
					directAffection = true;
					break;
				case "respect":
					directRespect = true;
					break;
				case "praise":
					directPraise = true;
					break;
				case "reassurance":
					directReassurance = true;
					break;
				case "abandonment":
					directAbandonment = true;
					break;
				case "repair":
					repairAttempt = true;
					break;
			}
		}
		else if (Set.of("bonded_subject", "other_subject").contains(socialTarget) && (seed._positiveBondIntensity >= 70))
		{
			if (switch (socialAction)
			{
				case "threat", "insult", "distrust", "resentment", "belief_conflict", "abandonment" -> true;
				default -> false;
			})
			{
				negativeBeliefConflict = true;
			}
			if (switch (socialAction)
			{
				case "gratitude", "affection", "respect", "praise", "reassurance" -> true;
				default -> false;
			})
			{
				positiveBondAlignment = true;
			}
		}

		if (!(directThreat || directFlame || directDistrust || directResentment || negativeBeliefConflict || positiveBondAlignment || directApology || directGratitude || directAffection || directRespect || directPraise || directReassurance || directAbandonment || repairAttempt))
		{
			return seed;
		}

		LOGGER.info(() -> getClass().getSimpleName() + ": SocialSignalJudge speaker=" + fakePlayerName + " target=" + sender.getName() + " channel=" + channelName + " recentEvent=" + recentEvent + " action=" + socialAction + " signalTarget=" + socialTarget + " confidence=" + semanticDecision.getSocialSignalConfidence() + " intensity=" + semanticDecision.getSocialSignalIntensity());
		_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=social_signal_judge target=" + sender.getName() + " channel=" + channelName + " recentEvent=" + recentEvent + " action=" + socialAction + " signalTarget=" + socialTarget + " confidence=" + semanticDecision.getSocialSignalConfidence() + " intensity=" + semanticDecision.getSocialSignalIntensity());
		return new SocialSignalAssessment(seed._focusEntityName, seed._positiveBondIntensity, seed._positiveBondSummary, directThreat, directFlame, directDistrust, directResentment, negativeBeliefConflict, positiveBondAlignment, directApology, directGratitude, directAffection, directRespect, directPraise, directReassurance, directAbandonment, repairAttempt);
	}

	private boolean shouldApplySemanticSocialSignal(PersonalityDecision decision)
	{
		if (decision == null)
		{
			return false;
		}
		final String socialAction = safeLower(decision.getSocialActionTag());
		final String socialTarget = safeLower(decision.getSocialTargetTag());
		if ("none".equals(socialAction) || socialAction.isBlank() || "none".equals(socialTarget) || socialTarget.isBlank())
		{
			return false;
		}
		final double confidence = decision.getSocialSignalConfidence();
		final int intensity = decision.getSocialSignalIntensity();
		switch (socialAction)
		{
			case "threat":
			case "insult":
			case "distrust":
			case "resentment":
			case "belief_conflict":
			case "abandonment":
				return (confidence >= 0.72) && (intensity >= 1);
			case "apology":
			case "gratitude":
			case "affection":
			case "respect":
			case "praise":
			case "reassurance":
			case "repair":
				return (confidence >= 0.78) && (intensity >= 1);
			default:
				return false;
		}
	}

	private String safeLower(String value)
	{
		return (value == null) ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}

	private void applyIncomingSocialSignal(String fakePlayerName, Player sender, SocialSignalAssessment socialSignal)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank() || (socialSignal == null))
		{
			return;
		}

		final String senderName = sender.getName();
		if ((senderName == null) || senderName.isBlank())
		{
			return;
		}

		if (socialSignal._directFlame)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "insulted_me");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.INSULTED_ME, buildInsultEpisodeDetail(fakePlayerName), 45000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=insulted_me target=" + senderName);
			}
		}
		if (socialSignal._directResentment)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "expressed_hate");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.EXPRESSED_HATE, buildResentmentEpisodeDetail(fakePlayerName), 45000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=expressed_hate target=" + senderName);
			}
		}
		if (socialSignal._directThreat)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "threatened_me");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.THREATENED_ME, buildThreatEpisodeDetail(fakePlayerName), 45000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=threatened_me target=" + senderName);
			}
		}
		if (socialSignal._directDistrust)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "distrusted_me");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.DISTRUSTED_ME, buildDistrustEpisodeDetail(fakePlayerName), 45000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=distrusted_me target=" + senderName);
			}
		}
		if (socialSignal._negativeBeliefConflict)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "attacked_beliefs");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.ATTACKED_BELIEFS, buildBeliefConflictEpisodeDetail(socialSignal), 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=belief_conflict target=" + senderName + " focus=" + socialSignal._focusEntityName + " intensity=" + socialSignal._positiveBondIntensity);
			}
		}
		if (socialSignal._positiveBondAlignment)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "affirmed_belief", socialSignal._focusEntityName);
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.PLEASANT_CHAT, buildBeliefAlignmentEpisodeDetail(socialSignal), 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=belief_alignment target=" + senderName + " focus=" + socialSignal._focusEntityName + " intensity=" + socialSignal._positiveBondIntensity);
			}
		}
		if (socialSignal._directApology)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "apologized", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.APOLOGIZED, "player offered an apology after tension", 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=apologized target=" + senderName);
			}
		}
		if (socialSignal._directGratitude)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "thanked_me", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.THANKED_ME, "player expressed gratitude directly", 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=thanked_me target=" + senderName);
			}
		}
		if (socialSignal._directRespect)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "respected_me", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.EXPRESSED_RESPECT, "player spoke with direct trust or respect", 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=expressed_respect target=" + senderName);
			}
		}
		if (socialSignal._directPraise)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "praised_me", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.PRAISED_ME, buildPraiseEpisodeDetail(fakePlayerName), 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=praised_me target=" + senderName);
			}
		}
		if (socialSignal._directAffection)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "expressed_affection", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.EXPRESSED_AFFECTION, "player spoke with direct affection or care", 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=expressed_affection target=" + senderName);
			}
		}
		if (socialSignal._directReassurance)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "reassured_me", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.REASSURED_ME, buildReassuranceEpisodeDetail(fakePlayerName), 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=reassured_me target=" + senderName);
			}
		}
		if (socialSignal._directAbandonment)
		{
			_chatService.recordHostilePlayerAction(fakePlayerName, senderName, "abandoned_me");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.ABANDONED_ME, buildAbandonmentEpisodeDetail(fakePlayerName), 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=abandoned_me target=" + senderName);
			}
		}
		if (socialSignal._repairAttempt)
		{
			_chatService.recordPositivePlayerAction(fakePlayerName, senderName, "sought_repair", "");
			if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.SOUGHT_REPAIR, "player asked to repair the bond or start over", 60000L))
			{
				_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=sought_repair target=" + senderName);
			}
		}
	}

	private void rememberReplyExchange(String fakePlayerName, String senderName, String channel, String incomingText, String replyLine, SocialSignalAssessment socialSignal, FpcConversationGuardAssessment inputGuard)
	{
		if ((inputGuard != null) && inputGuard.isSensitive())
		{
			_chatService.rememberGuardedExchange(fakePlayerName, senderName, channel, incomingText, replyLine, inputGuard);
			return;
		}
		if ((socialSignal != null) && socialSignal.hasNegativeSignal())
		{
			_chatService.rememberConversationTurn(fakePlayerName, senderName, channel, incomingText, replyLine);
			return;
		}
		_chatService.rememberSuccessfulExchange(fakePlayerName, senderName, channel, incomingText, replyLine);
	}

	private String buildInsultEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Elyra with open disrespect";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Marc with open disrespect";
		}
		return "player spoke to the FPC with open disrespect";
	}

	private String buildResentmentEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Elyra with open distrust or rejection";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Marc with open distrust or rejection";
		}
		return "player spoke to the FPC with open distrust or rejection";
	}

	private String buildBeliefConflictEpisodeDetail(SocialSignalAssessment socialSignal)
	{
		final String focusEntityName = ((socialSignal == null) || (socialSignal._focusEntityName == null) || socialSignal._focusEntityName.isBlank()) ? "something important" : socialSignal._focusEntityName.trim();
		final String bondSummary = ((socialSignal == null) || (socialSignal._positiveBondSummary == null)) ? "" : socialSignal._positiveBondSummary.trim();
		if (bondSummary.isBlank())
		{
			return "player spoke against " + focusEntityName + ", which the FPC holds close";
		}
		return "player spoke against " + focusEntityName + "; bond context: " + bondSummary;
	}

	private String buildBeliefAlignmentEpisodeDetail(SocialSignalAssessment socialSignal)
	{
		final String focusEntityName = ((socialSignal == null) || (socialSignal._focusEntityName == null) || socialSignal._focusEntityName.isBlank()) ? "someone important" : socialSignal._focusEntityName.trim();
		return "player spoke with care about " + focusEntityName + " instead of treating the bond lightly";
	}

	private String buildThreatEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player threatened Elyra directly instead of keeping words measured";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player threatened Marc directly instead of keeping the peace";
		}
		return "player threatened the FPC directly";
	}

	private String buildDistrustEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Elyra with open suspicion or doubt";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke to Marc as if his word could not be trusted";
		}
		return "player spoke to the FPC with open suspicion or doubt";
	}

	private String buildPraiseEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player praised Elyra directly instead of staying guarded";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player praised Marc directly instead of treating him lightly";
		}
		return "player offered the FPC direct praise";
	}

	private String buildReassuranceEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player promised steadiness to Elyra instead of drifting away";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player reassured Marc with steadier words than distance";
		}
		return "player offered steady reassurance to the FPC";
	}

	private String buildAbandonmentEpisodeDetail(String fakePlayerName)
	{
		if ("elyra".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke of leaving Elyra behind or walking away for good";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "player spoke of leaving Marc behind or not coming back";
		}
		return "player spoke of leaving the FPC behind or walking away";
	}

	private FpcDefinition resolveDefinitionByActiveNpc(Npc npc)
	{
		if (npc == null)
		{
			return null;
		}

		for (FpcDefinition definition : _profileData.getRegistry().getDefinitions())
		{
			final Npc activeNpc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
			if ((activeNpc != null) && (activeNpc.getObjectId() == npc.getObjectId()))
			{
				return definition;
			}
		}
		return null;
	}

	private FpcSocialEpisodeType classifySupportEpisode(Skill skill)
	{
		if (skill == null)
		{
			return null;
		}
		if (skill.hasEffectType(EffectType.RESURRECTION, EffectType.RESURRECTION_SPECIAL))
		{
			return FpcSocialEpisodeType.REVIVED_ME;
		}
		if (skill.hasEffectType(EffectType.HEAL, EffectType.CPHEAL, EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT, EffectType.REBALANCE_HP) || skill.isHealingPotionSkill())
		{
			return FpcSocialEpisodeType.HELPED_ME;
		}
		if (!skill.isActive() || skill.isPassive() || skill.isDebuff() || skill.hasNegativeEffect() || skill.isTransformation() || skill.isTriggeredSkill())
		{
			return null;
		}
		if (skill.hasEffectType(EffectType.TELEPORT, EffectType.TELEPORT_TO_TARGET, EffectType.SUMMON, EffectType.SUMMON_NPC, EffectType.SUMMON_PET))
		{
			return null;
		}
		return skill.isContinuous() ? FpcSocialEpisodeType.BUFFED_ME : null;
	}

	private long getSupportEpisodeDedupeWindowMs(FpcSocialEpisodeType episodeType)
	{
		if (episodeType == null)
		{
			return 0L;
		}
		if (episodeType == FpcSocialEpisodeType.HELPED_ME)
		{
			return 30000L;
		}
		if ((episodeType == FpcSocialEpisodeType.REVIVED_ME) || (episodeType == FpcSocialEpisodeType.BUFFED_ME))
		{
			return 60000L;
		}
		return 45000L;
	}

	private String buildSupportEpisodeDetail(Skill skill, FpcSocialEpisodeType episodeType)
	{
		final String skillName = ((skill == null) || (skill.getName() == null) || skill.getName().isBlank()) ? "unknown skill" : skill.getName();
		if (episodeType == FpcSocialEpisodeType.REVIVED_ME)
		{
			return "player brought the FPC back with " + skillName;
		}
		if (episodeType == FpcSocialEpisodeType.HELPED_ME)
		{
			return "player restored the FPC with " + skillName;
		}
		if (episodeType == FpcSocialEpisodeType.BUFFED_ME)
		{
			return "player reinforced the FPC with " + skillName;
		}
		return skillName;
	}

	public void applyAdminRecallHold(FpcDefinition definition, Npc npc)
	{
		if ((definition == null) || (npc == null))
		{
			return;
		}
		_taskService.holdForAdminControl(definition, npc, _ruleset.getAdminInterventionHoldMs(), true);
	}

	public long getAdminRecallHoldMs()
	{
		return _ruleset.getAdminInterventionHoldMs();
	}

	public void applyAdminHold(FpcDefinition definition, Npc npc, long holdMs)
	{
		if ((definition == null) || (npc == null))
		{
			return;
		}
		_taskService.holdForAdminControl(definition, npc, holdMs, true);
	}

	public boolean releaseAdminHold(FpcDefinition definition)
	{
		return (definition != null) && _taskService.releaseAdminHold(definition, true);
	}

	public void enableFpcDebug(FpcDefinition definition, Set<FpcDebugCategory> categories)
	{
		if (definition != null)
		{
			_debugService.enable(definition.getId(), categories);
		}
	}

	public void disableFpcDebug(FpcDefinition definition)
	{
		if (definition != null)
		{
			_debugService.disable(definition.getId());
		}
	}

	public String describeFpcDebug(FpcDefinition definition)
	{
		return (definition == null) ? "off" : _debugService.describe(definition.getId());
	}

	public List<String> describeFpcStatus(String idOrName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if (definition == null)
		{
			return List.of("Unknown FPC: " + idOrName);
		}

		final List<String> lines = new ArrayList<>();
		lines.add("FPC " + definition.getName() + " (" + definition.getId() + ") ruleset=" + _ruleset.getId() + " category=" + definition.getCategory() + "/" + definition.getArchetype());

		final Npc npc = findActiveFpcNpc(definition.getId());
		if (npc == null)
		{
			lines.add("State: loaded but not currently active.");
			lines.add("Debug: " + describeFpcDebug(definition));
			return lines;
		}

		final String currentZone = _routeService.resolveCurrentZone(npc, definition.getSpawnProfile().getZone());
		final long holdRemainingMs = _taskService.getAdminHoldRemainingMs(definition.getId());
		lines.add("State: active objId=" + npc.getObjectId() + " loc=" + npc.getX() + "," + npc.getY() + "," + npc.getZ() + " zone=" + currentZone + " intention=" + npc.getAI().getIntention());
		lines.add("Vitals: cp=" + (int) npc.getCurrentCp() + "/" + npc.getMaxCp() + " hp=" + (int) npc.getCurrentHp() + "/" + (int) npc.getMaxHp() + " mp=" + (int) npc.getCurrentMp() + "/" + (int) npc.getMaxMp() + " moving=" + npc.isMoving() + " combat=" + npc.isInCombat());
		if (definition.getAdventurerProfile().isAdventurerTier())
		{
			lines.add("Combat: " + _combatPowerService.resolveCombatPower(definition, npc).describe());
			if ((definition.getCompanionProfile() != null) && definition.getCompanionProfile().isHomeAnchorIdleBehavior() && !_hybridPartyService.hasActivePlayerContract(definition.getId()))
			{
				lines.add("Route: companion_home_anchor spawn=" + definition.getSpawnProfile().getZone() + " idle=home_anchor");
			}
			else
			{
				final int effectiveLevel = _combatPowerService.resolveEffectiveLevel(definition, npc.getTemplate().getLevel());
				final org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerRoute route = _routeService.resolveAdventurerRoute(definition, effectiveLevel);
				if (route != null)
				{
					lines.add("Route: " + route.getId() + " arrival=" + route.getArrivalZoneId() + " farm=" + route.getFarmZoneId() + " leash=" + route.getLeashRadius());
				}
			}
		}
		final String leaderName = _hybridPartyService.describePlayerLeader(definition.getId());
		if (!leaderName.isBlank())
		{
			lines.add("Contract: leader=" + leaderName);
		}
		if (holdRemainingMs > 0L)
		{
			lines.add("Admin hold: " + (holdRemainingMs / 1000L) + "s remaining");
		}
		if (npc.getTarget() != null)
		{
			lines.add("Target: " + npc.getTarget().getName() + " (" + npc.getTarget().getObjectId() + ")");
		}
		lines.add("Debug: " + describeFpcDebug(definition));
		return lines;
	}

	public FpcRelationshipSnapshot getRelationshipSnapshot(String idOrName, String playerName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if ((definition == null) || (playerName == null) || playerName.isBlank())
		{
			return FpcRelationshipSnapshot.EMPTY;
		}
		return _chatService.getRelationshipSnapshot(definition.getName(), playerName);
	}

	public FpcSocialSnapshot getSocialSnapshot(String idOrName, String playerName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if ((definition == null) || (playerName == null) || playerName.isBlank())
		{
			return FpcSocialSnapshot.EMPTY;
		}
		return _socialMemoryService.getSnapshot(definition.getName(), playerName);
	}

	public FpcRelationshipSnapshot overrideRelationshipPreset(String idOrName, String playerName, String presetName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if ((definition == null) || (playerName == null) || playerName.isBlank())
		{
			return FpcRelationshipSnapshot.EMPTY;
		}
		_chatService.clearPairMemory(definition.getName(), playerName);
		_socialMemoryService.clearPairHistory(definition.getName(), playerName);
		final FpcRelationshipSnapshot snapshot = _chatService.overrideRelationshipPreset(definition.getName(), playerName, presetName);
		seedManualSocialPreset(definition.getName(), playerName, presetName);
		return snapshot;
	}
	
	private void seedManualSocialPreset(String speakerName, String playerName, String presetName)
	{
		if ((speakerName == null) || speakerName.isBlank() || (playerName == null) || playerName.isBlank() || (presetName == null))
		{
			return;
		}
		
		switch (presetName.trim().toLowerCase(Locale.ROOT))
		{
			case "familiar":
			case "known":
			{
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.PLEASANT_CHAT, "manual familiar relation preset");
				break;
			}
			case "friendly":
			case "friend":
			{
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.PLEASANT_CHAT, "manual friendly relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.HELPED_ME, "manual friendly relation preset");
				break;
			}
			case "trusted":
			case "ally":
			{
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.PLEASANT_CHAT, "manual trusted relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.PARTY_INVITE_ACCEPTED, "manual trusted relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.HELPED_ME, "manual trusted relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.EXPRESSED_RESPECT, "manual trusted relation preset");
				break;
			}
			case "guarded":
			case "strained":
			{
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.PARTY_INVITE_DECLINED, "manual guarded relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.DISTRUSTED_ME, "manual guarded relation preset");
				break;
			}
			case "hostile":
			case "enemy":
			{
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.THREATENED_ME, "manual hostile relation preset");
				_socialMemoryService.recordEpisode(speakerName, playerName, FpcSocialEpisodeType.EXPRESSED_HATE, "manual hostile relation preset");
				break;
			}
		}
	}

	public String getRulesetId()
	{
		return _ruleset.getId();
	}

	public boolean maybeTriggerCompanionFollowup(FpcDefinition definition, Npc npc, Player leader)
	{
		if ((_hybridPartyService == null) || (definition == null) || (npc == null) || (leader == null))
		{
			return false;
		}
		if ((definition.getCompanionProfile() == null) || !definition.getCompanionProfile().usesSoftWhisperFollowup())
		{
			return false;
		}
		if (leader.isDead() || !leader.isOnline() || leader.isInOfflineMode() || npc.isDead() || npc.isInCombat() || leader.isInCombat() || npc.isCastingNow() || leader.isCastingNow())
		{
			return false;
		}

		final long latestConversationAt = _chatService.getLatestConversationTimestampMs(definition.getName(), leader.getName());
		if (latestConversationAt <= 0L)
		{
			return false;
		}

		final long now = System.currentTimeMillis();
		final long lastFollowupAt = _hybridPartyService.getLastFollowupAtMs(definition.getId());
		if (latestConversationAt <= lastFollowupAt)
		{
			return false;
		}

		final long conversationAgeMs = Math.max(0L, now - latestConversationAt);
		if ((conversationAgeMs < COMPANION_FOLLOWUP_QUIET_MS) || (conversationAgeMs > COMPANION_FOLLOWUP_MAX_CONVERSATION_AGE_MS))
		{
			return false;
		}

		final long lastLeaderActivityAt = _hybridPartyService.getLastLeaderActivityAtMs(definition.getId());
		if ((lastLeaderActivityAt > 0L) && ((now - lastLeaderActivityAt) < COMPANION_FOLLOWUP_QUIET_MS))
		{
			return false;
		}
		if (!_chatService.isWhisperChatCooldownReady(definition.getName(), leader.getName()))
		{
			return false;
		}

		final FpcReplyBankSelection selection = _replyBankService.select(definition.getId(), leader.getName(), "whisper", "companion_followup", "social", "");
		if ((selection == null) || selection.getCandidateLines().isEmpty())
		{
			return false;
		}

		final String line = selection.getCandidateLines().get(0);
		if ((line == null) || line.isBlank())
		{
			return false;
		}

		final boolean sent = _chatService.trySendDirectPrivateLine(leader, definition.getName(), line);
		if (sent)
		{
			_hybridPartyService.markFollowupSent(definition.getId(), leader);
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=companion_followup leader=" + leader.getName());
		}
		return sent;
	}

	public String describeFpcDefinition(FpcDefinition definition)
	{
		final String summary = _adventurerService.describeDefinition(definition);
		if ((definition == null) || !definition.getAdventurerProfile().isAdventurerTier())
		{
			return summary;
		}
		final int carrierNpcId = _appearanceService.resolveCarrierNpcId(definition);
		return summary + " " + _combatPowerService.describeDefinition(definition, carrierNpcId);
	}
	
	private boolean handleIncomingPublicChannel(Player sender, String text, String channelEvent)
	{
		return handleAddressedIngress(sender, text, channelEvent, (player, incomingText) -> resolveNamedTalkableFakePlayers(incomingText), fakePlayerName ->
		{
			final String channel = "player_world".equals(channelEvent) ? "world" : "shout";
			return processIncomingChannelMessage(sender, fakePlayerName, text, channel, channelEvent,
				(name, player, incomingText, currentChannel, inputGuard) -> inputGuard.isSensitive()
					? buildGuardedReplyPlan(name, player, currentChannel, inputGuard)
					: ("player_world".equals(channelEvent)
						? _decisionEngine.createWorldAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText)
						: _decisionEngine.createShoutAdvisoryPlan(name, _ruleset.getDefaultSocialZoneId(), player, incomingText)),
				(plan, name, player, incomingText, currentChannel) -> forcePublicReplyPlan(plan, name, player.getName(), incomingText, currentChannel),
				plan -> _chatService.tryEmitPublicLine(plan, "player_world".equals(channelEvent)),
				(name, player, socialSignal) ->
				{
				},
				(name, player, plan) ->
				{
				});
		});
	}

	private boolean handleAddressedIngress(Player sender, String text, String channelForLog, IncomingTargetResolver resolver, IncomingTargetHandler handler)
	{
		if ((sender == null) || (text == null) || text.isBlank() || (resolver == null) || (handler == null))
		{
			return false;
		}

		final List<String> fakePlayerNames = resolver.resolve(sender, text);
		if (fakePlayerNames.isEmpty())
		{
			return false;
		}
		if (!shouldBypassDuplicateIngress(sender, text, fakePlayerNames) && isDuplicateIngress(sender, text, fakePlayerNames))
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": Suppressed duplicate FPC ingress for player=" + sender.getName() + " channel=" + channelForLog + " text=" + text + " matches=" + fakePlayerNames);
			return false;
		}

		boolean emitted = false;
		for (String fakePlayerName : fakePlayerNames)
		{
			emitted |= handler.handle(fakePlayerName);
		}
		return emitted;
	}

	private boolean processIncomingChannelMessage(Player sender, String fakePlayerName, String text, String channel, String channelEvent, IncomingPlanBuilder planBuilder, IncomingFallbackBuilder fallbackBuilder, IncomingEmitter emitter, IncomingSocialSignalHook socialSignalHook, IncomingPostSendHook postSendHook)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank() || (text == null) || text.isBlank() || (channel == null) || channel.isBlank() || (channelEvent == null) || channelEvent.isBlank() || (planBuilder == null) || (fallbackBuilder == null) || (emitter == null))
		{
			return false;
		}

		final FpcConversationGuardAssessment inputGuard = _conversationGuardService.evaluateIncoming(fakePlayerName, sender.getName(), text);
		final SocialSignalAssessment socialSignal = inputGuard.suppressesSocialSignal() ? SocialSignalAssessment.NONE : assessIncomingSocialSignal(fakePlayerName, sender, text, channel, channelEvent);
		if (!inputGuard.suppressesSocialSignal())
		{
			applyIncomingSocialSignal(fakePlayerName, sender, socialSignal);
			if (socialSignalHook != null)
			{
				socialSignalHook.handle(fakePlayerName, sender, socialSignal);
			}
		}

		final FakePlayerAdvisoryPlan plan = finalizeReplyPlan(planBuilder.build(fakePlayerName, sender, text, channel, inputGuard), fakePlayerName, sender.getName(), text, channel, inputGuard,
			() -> buildGuardedReplyPlan(fakePlayerName, sender, channel, inputGuard),
			currentPlan -> fallbackBuilder.build(currentPlan, fakePlayerName, sender, text, channel));
		logReplyTrace(channel, sender, fakePlayerName, text, plan, inputGuard);

		final boolean sent = emitter.emit(plan);
		if (!sent)
		{
			if (isActionableCompanionDirective(plan))
			{
				if (postSendHook != null)
				{
					postSendHook.handle(fakePlayerName, sender, plan);
				}
				return true;
			}
			return false;
		}

		rememberReplyExchange(fakePlayerName, sender.getName(), channel, text, plan.getSelectedLine(), socialSignal, inputGuard);
		if (!inputGuard.suppressesPositiveMemory())
		{
			recordPositiveChatMemory(fakePlayerName, sender, text);
		}
		if (postSendHook != null)
		{
			postSendHook.handle(fakePlayerName, sender, plan);
		}
		return true;
	}

	private boolean isActionableCompanionDirective(FakePlayerAdvisoryPlan plan)
	{
		return (plan != null) && "companion_directive".equalsIgnoreCase(plan.getReplySource());
	}

	private FakePlayerAdvisoryPlan finalizeReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText, String channel, FpcConversationGuardAssessment inputGuard, Supplier<FakePlayerAdvisoryPlan> guardedPlanSupplier, Function<FakePlayerAdvisoryPlan, FakePlayerAdvisoryPlan> fallbackBuilder)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank() || (incomingText == null) || incomingText.isBlank() || (channel == null) || channel.isBlank() || (inputGuard == null) || (guardedPlanSupplier == null) || (fallbackBuilder == null))
		{
			return plan;
		}

		plan = sanitizeReplyPlan(plan, fakePlayerName, incomingText, channel);
		if ((plan == null) || (plan.getSelectedLine() == null) || plan.getSelectedLine().isBlank())
		{
			plan = inputGuard.isSensitive() ? guardedPlanSupplier.get() : fallbackBuilder.apply(plan);
		}
		return applyReplyPolicy(plan, senderName, fakePlayerName, incomingText, channel, inputGuard);
	}
	
	private boolean isDuplicateIngress(Player sender, String text, List<String> fakePlayerNames)
	{
		if ((sender == null) || (text == null) || text.isBlank() || (fakePlayerNames == null) || fakePlayerNames.isEmpty())
		{
			return false;
		}
		
		final long now = System.currentTimeMillis();
		_recentIngressByKey.entrySet().removeIf(entry -> (now - entry.getValue().longValue()) > 15000L);
		
		final String normalizedText = normalizeForCompare(text);
		if (normalizedText.isBlank())
		{
			return false;
		}
		
		final List<String> canonicalNames = new ArrayList<>();
		for (String fakePlayerName : fakePlayerNames)
		{
			final String normalizedName = normalizeForCompare(fakePlayerName);
			if (!normalizedName.isBlank())
			{
				canonicalNames.add(normalizedName);
			}
		}
		canonicalNames.sort(String::compareTo);
		
		final StringBuilder keyBuilder = new StringBuilder();
		keyBuilder.append(normalizeForCompare(sender.getName())).append('|').append(normalizedText).append('|');
		for (String canonicalName : canonicalNames)
		{
			keyBuilder.append(canonicalName).append(',');
		}
		final String dedupeKey = keyBuilder.toString();
		final Long previous = _recentIngressByKey.put(dedupeKey, now);
		return (previous != null) && ((now - previous.longValue()) <= 15000L);
	}

	private boolean shouldBypassDuplicateIngress(Player sender, String text, List<String> fakePlayerNames)
	{
		if ((sender == null) || (text == null) || text.isBlank() || (fakePlayerNames == null) || fakePlayerNames.isEmpty() || (_companionDirectiveService == null))
		{
			return false;
		}

		final String normalized = normalizeForCompare(normalizeReplyInputText(text));
		if (normalized.isBlank())
		{
			return false;
		}

		for (String fakePlayerName : fakePlayerNames)
		{
			final FpcDefinition definition = findFpcDefinition(fakePlayerName);
			if ((definition != null) && !_companionDirectiveService.classifyDirectiveCategory(definition, definition.getId(), sender, normalized, false).isBlank())
			{
				return true;
			}
		}
		return false;
	}

	private boolean isRecentCombatPressureTrigger(String key, long withinMs)
	{
		if ((key == null) || key.isBlank())
		{
			return false;
		}

		final long windowMs = Math.max(withinMs, 1000L);
		final long now = System.currentTimeMillis();
		_recentCombatPressureByKey.entrySet().removeIf(entry -> (now - entry.getValue().longValue()) > windowMs);
		final Long previous = _recentCombatPressureByKey.put(key, now);
		return (previous != null) && ((now - previous.longValue()) <= windowMs);
	}
	
	private List<String> resolveNearbyAddressedFakePlayers(Player sender, String text)
	{
		final String normalizedText = normalizeForCompare(text);
		if (normalizedText.isBlank())
		{
			return List.of();
		}
		
		final Set<String> matches = new LinkedHashSet<>();
		_platformAdapter.getWorldFacade().forEachVisibleObjectInRange(sender, Npc.class, GENERAL_CHAT_RANGE, npc ->
		{
			if (matches.size() >= MAX_MULTI_FPC_REPLIES)
			{
				return;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if ((definition == null) || !definition.isTalkable() || !isActiveDefinitionNpc(definition, npc))
			{
				return;
			}
			final String effectiveName = definition.getName();
			if (containsNameReference(normalizedText, effectiveName))
			{
				matches.add(effectiveName);
			}
		});
		return new ArrayList<>(matches);
	}
	
	private static boolean containsNameReference(String normalizedText, String possibleName)
	{
		final String normalizedName = normalizeForCompare(possibleName);
		if (normalizedName.isBlank())
		{
			return false;
		}
		return (" " + normalizedText + " ").contains(" " + normalizedName + " ");
	}
	
	private FakePlayerAdvisoryPlan sanitizeReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String incomingText, String channel)
	{
		if (plan == null)
		{
			return null;
		}
		
		String line = plan.getSelectedLine();
		if ((line == null) || line.isBlank())
		{
			return plan;
		}
		
		line = stripSpeakerPrefix(line, fakePlayerName);
		line = collapseWhitespace(line);
		line = trimToSentenceBoundary(line, channel);
		line = shortenReply(line, maxWordsForChannel(channel), maxCharsForChannel(channel));
		
		if (looksLikeEcho(line, incomingText))
		{
			line = null;
		}
		
		if ((line == null) || line.isBlank())
		{
			return new FakePlayerAdvisoryPlan(fakePlayerName, plan.getMoodTag(), plan.getIntentPreference(), plan.getPreferredZone(), false, plan.getLineStyleTag(), plan.getTopicTag(), plan.getReplySource(), null, plan.getConfidence());
		}
		
		return new FakePlayerAdvisoryPlan(fakePlayerName, plan.getMoodTag(), plan.getIntentPreference(), plan.getPreferredZone(), true, plan.getLineStyleTag(), plan.getTopicTag(), plan.getReplySource(), line, plan.getConfidence());
	}

	private FakePlayerAdvisoryPlan applyReplyPolicy(FakePlayerAdvisoryPlan plan, Player sender, String fakePlayerName, String incomingText, String channel, FpcConversationGuardAssessment inputGuard)
	{
		return applyReplyPolicy(plan, (sender == null) ? "" : sender.getName(), fakePlayerName, incomingText, channel, inputGuard);
	}

	private FakePlayerAdvisoryPlan applyReplyPolicy(FakePlayerAdvisoryPlan plan, String senderName, String fakePlayerName, String incomingText, String channel, FpcConversationGuardAssessment inputGuard)
	{
		if ((plan == null) || (senderName == null) || senderName.isBlank() || (plan.getSelectedLine() == null) || plan.getSelectedLine().isBlank())
		{
			return plan;
		}

		final FpcSocialSnapshot socialSnapshot = _socialMemoryService.getSnapshot(fakePlayerName, senderName);
		final FakePlayerReplyPolicyService.ReplyPolicyOutcome outcome = _replyPolicyService.evaluate(fakePlayerName, channel, plan.getSelectedLine(), socialSnapshot, inputGuard);
		if (!outcome.isAccepted() || !outcome.isRepaired() || (outcome.getReplacementLine() == null) || outcome.getReplacementLine().isBlank())
		{
			return plan;
		}

		final String repairedLine = outcome.getReplacementLine().replace('"', '\'');
		final String guardDetail = ((inputGuard == null) || !inputGuard.isSensitive()) ? "none" : inputGuard.describeForLog();
		LOGGER.info(() -> getClass().getSimpleName() + ": ReplyPolicy channel=" + channel + " speaker=" + fakePlayerName + " target=" + senderName + " source=" + plan.getReplySource() + " guard=" + guardDetail + " reason=" + outcome.getReason() + " repaired=\"" + repairedLine + "\"");
		_debugService.trace(fakePlayerName, FpcDebugCategory.CHAT, "policy channel=" + channel + " target=" + senderName + " source=" + plan.getReplySource() + " guard=" + guardDetail + " reason=" + outcome.getReason() + " repaired=\"" + repairedLine + "\"");
		return new FakePlayerAdvisoryPlan(plan.getSpeakerName(), plan.getMoodTag(), plan.getIntentPreference(), plan.getPreferredZone(), true, plan.getLineStyleTag(), plan.getTopicTag(), "policy_" + outcome.getReason(), outcome.getReplacementLine(), plan.getConfidence());
	}

	private FakePlayerAdvisoryPlan buildGuardedReplyPlan(String fakePlayerName, Player sender, String channel, FpcConversationGuardAssessment inputGuard)
	{
		return buildGuardedReplyPlan(fakePlayerName, (sender == null) ? "" : sender.getName(), channel, inputGuard);
	}

	private FakePlayerAdvisoryPlan buildGuardedReplyPlan(String fakePlayerName, String senderName, String channel, FpcConversationGuardAssessment inputGuard)
	{
		final FpcSocialSnapshot socialSnapshot = ((senderName == null) || senderName.isBlank()) ? FpcSocialSnapshot.EMPTY : _socialMemoryService.getSnapshot(fakePlayerName, senderName);
		final String selectedLine = _conversationGuardService.chooseReplyLine(fakePlayerName, channel, socialSnapshot, inputGuard);
		final boolean speakNow = (selectedLine != null) && !selectedLine.isBlank();
		return new FakePlayerAdvisoryPlan(fakePlayerName, "calm", "idle", _ruleset.getDefaultSocialZoneId(), speakNow, speakNow ? "guarded_direct" : "none", inputGuard.isSensitive() ? inputGuard.getCategory() : "smalltalk", "conversation_guard", selectedLine, 0.98);
	}

	private FakePlayerAdvisoryPlan forceWhisperReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText)
	{
		final String selectedLine = chooseKnowledgeBackedFallbackLine(fakePlayerName, senderName, incomingText, "whisper", chooseFallbackWhisperLine(fakePlayerName, senderName, incomingText));
		return buildForcedFallbackReplyPlan(plan, fakePlayerName, "fallback_whisper", selectedLine);
	}

	private FakePlayerAdvisoryPlan forcePartyReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText)
	{
		final String selectedLine = chooseKnowledgeBackedFallbackLine(fakePlayerName, senderName, incomingText, "party", chooseFallbackWhisperLine(fakePlayerName, senderName, incomingText));
		return buildForcedFallbackReplyPlan(plan, fakePlayerName, "fallback_party", selectedLine);
	}
	
	private FakePlayerAdvisoryPlan forceGeneralReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText)
	{
		final String selectedLine = chooseKnowledgeBackedFallbackLine(fakePlayerName, senderName, incomingText, "general", chooseFallbackGeneralLine(fakePlayerName, senderName, incomingText));
		return buildForcedFallbackReplyPlan(plan, fakePlayerName, "fallback_general", selectedLine);
	}

	private FakePlayerAdvisoryPlan createStudioProbePlan(String fakePlayerName, String currentZone, String senderName, String incomingText, String channel)
	{
		return _decisionEngine.createProbeAdvisoryPlan(fakePlayerName, currentZone, channel, senderName, incomingText);
	}

	private FakePlayerAdvisoryPlan forceStudioProbeReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText, String channel)
	{
		final String normalizedChannel = normalizeStudioChannel(channel);
		return switch (normalizedChannel)
		{
			case "clan" -> forceClanReplyPlan(plan, fakePlayerName, senderName, incomingText);
			case "general" -> forceGeneralReplyPlan(plan, fakePlayerName, senderName, incomingText);
			case "shout", "world" -> forcePublicReplyPlan(plan, fakePlayerName, senderName, incomingText, normalizedChannel);
			default -> forceWhisperReplyPlan(plan, fakePlayerName, senderName, incomingText);
		};
	}
	
	private String chooseFallbackWhisperLine(String fakePlayerName, String senderName, String incomingText)
	{
		final FallbackReplyContext context = buildFallbackReplyContext(fakePlayerName, senderName, analyzeConversation(fakePlayerName, senderName, incomingText));
		final boolean marcToTrustedPlayer = context._marcToTrustedPlayer;
		final ReplyAnalysisSnapshot analysis = context._analysis;
		final String messageCategory = analysis._messageCategory;
		if ("insult".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Easy. If you want me here, speak cleaner than that." : "Watch your tone.";
		}
		if ("threat".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Then step back before this turns uglier." : "Then step back before this gets worse.";
		}
		if ("distrust".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Then watch what I do, not the fear in your head." : "Then watch my actions and judge slower.";
		}
		if ("resentment".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "If that's how you feel, say it cleanly and own it." : "Then keep your distance until the tone changes.";
		}
		if ("abandonment".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "If you mean to leave, say it cleanly to my face." : "If you mean to leave, say it cleanly.";
		}
		return chooseSharedFallbackCategoryLine(FallbackReplyFamily.WHISPER, fakePlayerName, messageCategory, context, "");
	}
	
	private FakePlayerAdvisoryPlan forcePublicReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText, String channel)
	{
		final String selectedLine = chooseKnowledgeBackedFallbackLine(fakePlayerName, senderName, incomingText, channel, chooseFallbackPublicLine(fakePlayerName, senderName, incomingText, channel));
		return buildForcedFallbackReplyPlan(plan, fakePlayerName, "fallback_public", selectedLine);
	}

	private FakePlayerAdvisoryPlan forceClanReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String senderName, String incomingText)
	{
		final String selectedLine = chooseKnowledgeBackedFallbackLine(fakePlayerName, senderName, incomingText, "clan", chooseFallbackPublicLine(fakePlayerName, senderName, incomingText, "clan"));
		return buildForcedFallbackReplyPlan(plan, fakePlayerName, "fallback_clan", selectedLine);
	}
	
	private String chooseFallbackGeneralLine(String fakePlayerName, String senderName, String incomingText)
	{
		final FallbackReplyContext context = buildFallbackReplyContext(fakePlayerName, senderName, analyzeReply(fakePlayerName, senderName, incomingText, "general"));
		final boolean marcToTrustedPlayer = context._marcToTrustedPlayer;
		final String messageCategory = context._analysis._messageCategory;
		if ("insult".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Speak cleaner than that." : "Watch your tone.";
		}
		if ("threat".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Back off before this turns uglier." : "Back off before this gets worse.";
		}
		if ("distrust".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Watch what I do, then judge." : "Watch first. Judge slower.";
		}
		if ("resentment".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "Then say it cleanly." : "Keep your distance, then.";
		}
		if ("abandonment".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "If you leave, say it straight." : "Then leave cleanly.";
		}
		return chooseSharedFallbackCategoryLine(FallbackReplyFamily.GENERAL, fakePlayerName, messageCategory, context, "general");
	}
	
	private String chooseFallbackPublicLine(String fakePlayerName, String senderName, String incomingText, String channel)
	{
		final FallbackReplyContext context = buildFallbackReplyContext(fakePlayerName, senderName, analyzeReply(fakePlayerName, senderName, incomingText, channel));
		final String messageCategory = context._analysis._messageCategory;
		if ("insult".equalsIgnoreCase(messageCategory))
		{
			return "Watch your tone.";
		}
		if ("threat".equalsIgnoreCase(messageCategory))
		{
			return "Back away.";
		}
		if ("distrust".equalsIgnoreCase(messageCategory))
		{
			return "Watch first. Judge later.";
		}
		if ("resentment".equalsIgnoreCase(messageCategory))
		{
			return "Keep your distance, then.";
		}
		if ("abandonment".equalsIgnoreCase(messageCategory))
		{
			return "Then go cleanly.";
		}
		return chooseSharedFallbackCategoryLine(FallbackReplyFamily.PUBLIC, fakePlayerName, messageCategory, context, channel);
	}

	private boolean isMarcSpeakingToTrustedPlayer(String fakePlayerName, String senderName)
	{
		return "marc".equals(normalizeForCompare(fakePlayerName)) && "playerone".equals(normalizeForCompare(senderName));
	}

	private String chooseSharedFallbackCategoryLine(FallbackReplyFamily family, String fakePlayerName, String messageCategory, FallbackReplyContext context, String channel)
	{
		final boolean marcToTrustedPlayer = context._marcToTrustedPlayer;
		final boolean hasRecentHistory = context._hasRecentHistory;
		final ReplyAnalysisSnapshot analysis = context._analysis;
		final String focusEntityName = analysis._focusEntityName;
		final String focusIntent = analysis._focusIntent;
		final FpcReplySettings settings = context._settings;
		if ("belief_conflict".equalsIgnoreCase(messageCategory))
		{
			return buildBeliefConflictFallbackLine(fakePlayerName, focusEntityName, family == FallbackReplyFamily.WHISPER);
		}
		if ("pvp_conflict".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> "de_escalate".equalsIgnoreCase(settings.getPvpConflictPolicy()) ? "Stay near guards first. Tell me names, not blood." : "Tell me who started it and where.";
				case GENERAL, PUBLIC -> "de_escalate".equalsIgnoreCase(settings.getPvpConflictPolicy()) ? "Stay near guards first." : "Tell me where first.";
			};
		}
		if ("progression_strength".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> "basic".equalsIgnoreCase(settings.getProgressionCoachingLevel()) ? "Start with skills first." : (marcToTrustedPlayer ? "Start with skills, core gear, and the adena flow that keeps both alive." : "Start with skills and core gear first.");
				case GENERAL -> "basic".equalsIgnoreCase(settings.getProgressionCoachingLevel()) ? "Skills first." : (marcToTrustedPlayer ? "Skills and core gear first." : "Skills first. Gear second.");
				case PUBLIC -> "basic".equalsIgnoreCase(settings.getProgressionCoachingLevel()) ? "Skills first." : "Skills and core gear first.";
			};
		}
		if ("party_farm".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> "simple_zone_pick".equalsIgnoreCase(settings.getFarmAdviceMode()) ? "Start with Agony first." : (marcToTrustedPlayer ? "Tell me your level and goal first. Then I'll judge the route." : "Tell me level and goal first.");
				case GENERAL -> "simple_zone_pick".equalsIgnoreCase(settings.getFarmAdviceMode()) ? "Agony first." : (marcToTrustedPlayer ? "Level and goal first." : "Name level and goal.");
				case PUBLIC -> "simple_zone_pick".equalsIgnoreCase(settings.getFarmAdviceMode()) ? "Agony first." : "Level and goal first.";
			};
		}
		if ("social_invite".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "depends on the clan. i want people who move together, not just talk." : "depends on the clan. i want people who move together, not just recruit loudly.";
				case GENERAL -> "depends on the clan. i care more about how it moves than what it promises.";
				case PUBLIC -> "depends on the clan. i watch how people move first.";
			};
		}
		if ("companionship".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "You can stay and talk with me. I don't mind the company." : "You can stay a while. I don't mind the company.";
				case GENERAL -> marcToTrustedPlayer ? "You can stay a while with me." : "You can stay a while.";
				case PUBLIC -> "Stay a while if you like.";
			};
		}
		if ("relationship_probe".equalsIgnoreCase(messageCategory))
		{
			return (family == FallbackReplyFamily.WHISPER) ? "Ask them directly. I will not fake their reasons for you." : "Ask them directly.";
		}
		if (_messageClassifier.isSelfhoodCategory(messageCategory))
		{
			return buildSelfhoodFallbackLine(fakePlayerName, messageCategory, focusEntityName, focusIntent, family != FallbackReplyFamily.WHISPER);
		}
		if (messageCategory.startsWith("entity_"))
		{
			return buildEntityFallbackLine(fakePlayerName, focusEntityName, messageCategory, family == FallbackReplyFamily.WHISPER);
		}
		if ("weather".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> "I would not swear to the sky from here.";
				case GENERAL -> "I cant read that sky from here.";
				case PUBLIC -> "Cant read that sky from here.";
			};
		}
		if ("quest_story".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "I was made so silence would stop winning." : "Ask plainly, and I will tell you what still matters.";
				case GENERAL -> marcToTrustedPlayer ? "I was made to keep silence from winning." : "Ask plainly.";
				case PUBLIC -> "Ask plainly.";
			};
		}
		if ("lineage_lore".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "Ask the exact Warg question. I'll keep it grounded." : "Ask the exact system, and I'll keep it grounded.";
				case GENERAL, PUBLIC -> "Ask the exact system.";
			};
		}
		if ("greeting".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? (hasRecentHistory ? "still glad youre here, PlayerOne." : "welcome back, PlayerOne.") : (hasRecentHistory ? "good to see you again." : "hey, good to see you.");
				case GENERAL -> marcToTrustedPlayer ? (hasRecentHistory ? "still glad youre here, PlayerOne." : "good to see you, PlayerOne.") : (hasRecentHistory ? "good to see you again." : "hey, good to see you.");
				case PUBLIC -> marcToTrustedPlayer ? "good to see you, PlayerOne." : "hey.";
			};
		}
		if ("thanks".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "you never needed to thank me." : "any time.";
				case GENERAL -> marcToTrustedPlayer ? "you never had to thank me." : "any time.";
				case PUBLIC -> marcToTrustedPlayer ? "always, PlayerOne." : "any time.";
			};
		}
		if ("repair".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "Then start with honesty, and we can try again." : "We can try again, slowly.";
				case GENERAL -> marcToTrustedPlayer ? "We can try again. Slowly." : "We can try again.";
				case PUBLIC -> "We can try again.";
			};
		}
		if ("affection".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "I hear it. Stay, then." : "I hear you. Stay gentle with it.";
				case GENERAL -> marcToTrustedPlayer ? "I hear it." : "I hear you.";
				case PUBLIC -> "I hear you.";
			};
		}
		if ("respect".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "Then keep speaking true, and I'll believe it." : "Respect carries further than flattery.";
				case GENERAL -> marcToTrustedPlayer ? "Then keep speaking true." : "Respect carries further than flattery.";
				case PUBLIC -> "Respect carries further than flattery.";
			};
		}
		if ("praise".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "Then keep that truth honest. I heard it." : "Praise lands better than flattery.";
				case GENERAL -> marcToTrustedPlayer ? "I heard it, PlayerOne." : "I heard that.";
				case PUBLIC -> "I heard that.";
			};
		}
		if ("reassurance".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "Then stay steady, and I'll remember that." : "Then stay steady and mean it.";
				case GENERAL -> marcToTrustedPlayer ? "Then stay steady." : "Stay steady, then.";
				case PUBLIC -> "Stay steady, then.";
			};
		}
		if ("status".equalsIgnoreCase(messageCategory))
		{
			return marcToTrustedPlayer ? "better now that youre here." : "keeping steady.";
		}
		if ("direction".equalsIgnoreCase(messageCategory))
		{
			return (family == FallbackReplyFamily.WHISPER) && marcToTrustedPlayer ? "start at the south gate, then circle back here." : "check the south gate first.";
		}
		if ("farm".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "start with Abandoned Camp, then come back through Giran." : "try Abandoned Camp first.";
				case GENERAL -> marcToTrustedPlayer ? "start with Abandoned Camp first." : "decent hunting around here.";
				case PUBLIC -> "Abandoned Camp first.";
			};
		}
		if ("help".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "tell me what you need, and ill stay with you." : "tell me what you need.";
				case GENERAL -> marcToTrustedPlayer ? "tell me your class and level." : "tell me class and level.";
				case PUBLIC -> "Class and level first.";
			};
		}
		if ("party".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "tell me the zone first, and ill think with you." : "tell me the zone first.";
				case GENERAL -> marcToTrustedPlayer ? "tell me the zone first." : "name the zone first.";
				case PUBLIC -> "Name the zone first.";
			};
		}
		if ("name".equalsIgnoreCase(messageCategory))
		{
			return switch (family)
			{
				case WHISPER -> marcToTrustedPlayer ? "im Marc. ive been keeping watch here in Giran." : "im " + normalizeFallbackIdentity(fakePlayerName) + ". keeping watch here.";
				case GENERAL -> marcToTrustedPlayer ? "Marc. keeping watch in Giran." : "im " + normalizeFallbackIdentity(fakePlayerName) + ".";
				case PUBLIC -> marcToTrustedPlayer ? "Marc. still here in Giran." : "im " + normalizeFallbackIdentity(fakePlayerName) + ".";
			};
		}
		if ("apology".equalsIgnoreCase(messageCategory))
		{
			return (family == FallbackReplyFamily.WHISPER) ? (marcToTrustedPlayer ? "im here with you now. thats enough for me." : "its fine. youre here now.") : null;
		}
		if ("memory".equalsIgnoreCase(messageCategory))
		{
			return (family == FallbackReplyFamily.WHISPER) ? (marcToTrustedPlayer ? "i remember more than i say." : "i remember enough.") : null;
		}
		return switch (family)
		{
			case WHISPER -> marcToTrustedPlayer && hasRecentHistory && !"warm_but_not_constant".equalsIgnoreCase(settings.getRelationshipWarmth()) ? "im still glad youre here." : (hasRecentHistory ? "im listening." : "im here.");
			case GENERAL -> marcToTrustedPlayer && hasRecentHistory && !"warm_but_not_constant".equalsIgnoreCase(settings.getRelationshipWarmth()) ? "still glad youre here." : (hasRecentHistory ? "im listening." : "im around.");
			case PUBLIC -> "world".equalsIgnoreCase(channel) ? "im still around." : "im around.";
		};
	}

	private String buildSelfhoodFallbackLine(String fakePlayerName, String messageCategory, String focusEntityName, String focusIntent, boolean shortForm)
	{
		final String identity = normalizeFallbackIdentity(fakePlayerName);
		final String focusName = ((focusEntityName == null) || focusEntityName.isBlank()) ? "they" : focusEntityName.trim();
		final String effectiveFocusIntent = ((focusIntent == null) || focusIntent.isBlank()) ? "" : focusIntent.trim().toLowerCase(Locale.ENGLISH);
		switch ((messageCategory == null) ? "" : messageCategory.toLowerCase(Locale.ENGLISH))
		{
			case "self_identity":
				return shortForm ? "im " + identity + ". the rest takes longer than a title." : "im " + identity + ". the rest takes longer than a title.";
			case "self_story":
				return shortForm ? "i have a story. ask plainly." : "i have a story. ask plainly, and ill tell you the part i can.";
			case "self_belief":
				return shortForm ? "ask one clean belief question." : "i have beliefs. ask one clean question, and ill answer that part.";
			case "self_preference":
				return shortForm ? "ask one preference at a time." : "ask one preference at a time, and ill answer it cleanly.";
			case "self_state_reflection":
				return shortForm ? "some feelings stay close." : "some feelings stay close. ask plainly.";
			case "creator_opinion":
				return shortForm ? "i wont cheapen that bond." : ("they".equalsIgnoreCase(focusName) ? "the one who made me is not a subject i cheapen in one line." : "my thoughts on " + focusName + " are not something i cheapen in one line.");
			case "bond_opinion":
				if ("bond_hurt".equals(effectiveFocusIntent))
				{
					return shortForm ? ("they".equalsIgnoreCase(focusName) ? "they left a mark on me." : focusName + " left a mark on me.") : ("they".equalsIgnoreCase(focusName) ? "they left a mark on me. im not flattening it into yes or no." : focusName + " left a mark on me. im not flattening it into yes or no.");
				}
				if ("bond_trust".equals(effectiveFocusIntent))
				{
					return shortForm ? ("they".equalsIgnoreCase(focusName) ? "trust is earned slowly." : "trust in " + focusName + " is earned slowly.") : ("they".equalsIgnoreCase(focusName) ? "trust there is earned slowly. ask what has or hasnt earned it." : "trust in " + focusName + " is earned slowly. ask what has or hasnt earned it.");
				}
				if ("bond_need".equals(effectiveFocusIntent))
				{
					return shortForm ? ("they".equalsIgnoreCase(focusName) ? "they matter. need is sharper." : focusName + " matters. need is sharper.") : ("they".equalsIgnoreCase(focusName) ? "they matter. need is a sharper word, and i dont spend it lightly." : focusName + " matters. need is a sharper word, and i dont spend it lightly.");
				}
				if ("bond_loyalty".equals(effectiveFocusIntent))
				{
					return shortForm ? ("they".equalsIgnoreCase(focusName) ? "loyalty is proved slowly." : "loyalty to " + focusName + " is proved slowly.") : ("they".equalsIgnoreCase(focusName) ? "loyalty is proved over time, not handed over in one breath." : "loyalty to " + focusName + " is proved over time, not handed over in one breath.");
				}
				return shortForm ? ("they".equalsIgnoreCase(focusName) ? "they matter to me." : focusName + " matters to me.") : ("they".equalsIgnoreCase(focusName) ? "they matter to me. the longer reason needs more than one line." : focusName + " matters to me. the longer reason needs more than one line.");
			default:
				return shortForm ? "ask plainly." : "ask plainly, and ill answer what i can.";
		}
	}

	private void logReplyTrace(String channel, Player sender, String fakePlayerName, String incomingText, FakePlayerAdvisoryPlan plan, FpcConversationGuardAssessment inputGuard)
	{
		if ((sender == null) || (plan == null) || (plan.getSelectedLine() == null) || plan.getSelectedLine().isBlank())
		{
			return;
		}

		final String guardDetail = ((inputGuard == null) || !inputGuard.isSensitive()) ? "none" : inputGuard.describeForLog();
		final String messageCategory;
		final String knowledgeType;
		final String focusIntent;
		if ((inputGuard != null) && inputGuard.isSensitive())
		{
			messageCategory = inputGuard.getCategory();
			knowledgeType = inputGuard.getHistoryKnowledgeType();
			focusIntent = "none";
		}
		else
		{
			final ReplyAnalysisSnapshot analysis = analyzeReply(fakePlayerName, sender.getName(), incomingText, channel);
			messageCategory = analysis._messageCategory;
			knowledgeType = analysis._knowledgeType;
			focusIntent = analysis._focusIntent.isBlank() ? "none" : analysis._focusIntent;
		}
		final String traceLine = "channel=" + channel + " source=" + plan.getReplySource() + " guard=" + guardDetail + " category=" + messageCategory + " focusIntent=" + focusIntent + " knowledgeType=" + knowledgeType + " target=" + sender.getName() + " incoming=\"" + incomingText + "\" outgoing=\"" + plan.getSelectedLine() + "\"";
		LOGGER.info(() -> getClass().getSimpleName() + ": ReplyTrace " + traceLine + " speaker=" + fakePlayerName);
		_debugService.trace(fakePlayerName, FpcDebugCategory.CHAT, traceLine);
	}

	private void recordPositiveChatMemory(String fakePlayerName, Player sender, String incomingText)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank() || (incomingText == null) || incomingText.isBlank())
		{
			return;
		}

		final String senderName = sender.getName();
		if ((senderName == null) || senderName.isBlank())
		{
			return;
		}

		final String messageCategory = analyzeConversation(fakePlayerName, senderName, incomingText)._messageCategory;
		if (!isPositiveChatCategory(messageCategory))
		{
			return;
		}

		final String detail = buildPleasantChatEpisodeDetail(fakePlayerName, senderName, messageCategory);
		if (_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, senderName, FpcSocialEpisodeType.PLEASANT_CHAT, detail, 60000L))
		{
			_debugService.trace(fakePlayerName, FpcDebugCategory.SOCIAL, "event=pleasant_chat target=" + senderName + " category=" + messageCategory);
		}
	}

	private boolean isPositiveChatCategory(String messageCategory)
	{
		if ((messageCategory == null) || messageCategory.isBlank())
		{
			return false;
		}

		switch (messageCategory.toLowerCase(Locale.ENGLISH))
		{
			case "greeting":
			case "status":
			case "thanks":
			case "apology":
			case "repair":
			case "affection":
			case "respect":
			case "praise":
			case "reassurance":
			case "name":
			case "memory":
			case "smalltalk":
			case "companionship":
			case "social_invite":
			case "help":
			case "farm":
			case "direction":
			case "lineage_lore":
			case "quest_story":
			case "progression_strength":
			case "entity_status":
			case "entity_relationship":
			case "entity_reason":
			case "entity_story":
			case "entity_memory":
				return true;
			default:
				return false;
		}
	}

	private String buildPleasantChatEpisodeDetail(String fakePlayerName, String senderName, String messageCategory)
	{
		if ("affection".equalsIgnoreCase(messageCategory))
		{
			return "the player spoke with direct warmth instead of distance";
		}
		if ("respect".equalsIgnoreCase(messageCategory))
		{
			return "the player offered trust or respect directly";
		}
		if ("praise".equalsIgnoreCase(messageCategory))
		{
			return "the player offered direct praise instead of guarded flattery";
		}
		if ("reassurance".equalsIgnoreCase(messageCategory))
		{
			return "the player promised steadiness instead of drifting away";
		}
		if ("repair".equalsIgnoreCase(messageCategory))
		{
			return "the player tried to mend the bond instead of pressing further";
		}
		if ("companionship".equalsIgnoreCase(messageCategory))
		{
			return "conversation leaned toward company instead of pressure";
		}
		if ("help".equalsIgnoreCase(messageCategory) || "farm".equalsIgnoreCase(messageCategory) || "direction".equalsIgnoreCase(messageCategory) || "progression_strength".equalsIgnoreCase(messageCategory) || "lineage_lore".equalsIgnoreCase(messageCategory))
		{
			return "the player came back for grounded advice and the exchange stayed steady";
		}
		if ("memory".equalsIgnoreCase(messageCategory) || "apology".equalsIgnoreCase(messageCategory) || "thanks".equalsIgnoreCase(messageCategory))
		{
			return "the exchange carried personal weight without turning hostile";
		}
		if ("entity_status".equalsIgnoreCase(messageCategory))
		{
			return "the player asked about someone important and the speaker answered carefully";
		}
		if ("entity_relationship".equalsIgnoreCase(messageCategory) || "entity_reason".equalsIgnoreCase(messageCategory) || "entity_story".equalsIgnoreCase(messageCategory) || "entity_memory".equalsIgnoreCase(messageCategory))
		{
			return "the exchange stayed centered on someone who mattered instead of drifting into filler";
		}
		if ("self_identity".equalsIgnoreCase(messageCategory) || "self_story".equalsIgnoreCase(messageCategory) || "self_belief".equalsIgnoreCase(messageCategory) || "self_preference".equalsIgnoreCase(messageCategory) || "self_state_reflection".equalsIgnoreCase(messageCategory))
		{
			return "the player asked a personal question and the exchange stayed person-centered instead of generic";
		}
		if ("creator_opinion".equalsIgnoreCase(messageCategory) || "bond_opinion".equalsIgnoreCase(messageCategory))
		{
			return "the player asked about an important bond and the exchange stayed emotionally focused";
		}
		if ("marc".equals(normalizeForCompare(fakePlayerName)))
		{
			return "Marc remembers a calmer conversation with " + senderName;
		}
		return "conversation felt easy and welcome";
	}

	private String chooseKnowledgeBackedFallbackLine(String fakePlayerName, String senderName, String incomingText, String channel, String fallbackLine)
	{
		final ReplyAnalysisSnapshot analysis = analyzeReply(fakePlayerName, senderName, incomingText, channel);
		final String effectiveIncomingText = analysis._effectiveIncomingText;
		final String mathLine = trySimpleMathFallback(incomingText);
		if ((mathLine != null) && !mathLine.isBlank())
		{
			return mathLine;
		}
		final FpcRouteProfile routeProfile = analysis._routeProfile;
		final String messageCategory = analysis._messageCategory;
		if (_messageClassifier.isSelfhoodCategory(messageCategory))
		{
			return fallbackLine;
		}
		final String knowledgeType = analysis._knowledgeType;
		final String recentConversationSummary = ("whisper".equalsIgnoreCase(channel) || "party".equalsIgnoreCase(channel)) ? _chatService.describeRecentConversation(fakePlayerName, senderName) : "";
		final List<String> knowledgeLines = _knowledgeService.select(fakePlayerName, senderName, channel, knowledgeType, effectiveIncomingText).getCandidateLines();
		final String knowledgeLine = chooseFreshCandidateLine(knowledgeLines, recentConversationSummary);
		if ((knowledgeLine != null) && !knowledgeLine.isBlank())
		{
			return knowledgeLine;
		}
		final List<String> candidateLines = _replyBankService.select(fakePlayerName, senderName, channel, messageCategory, routeProfile.getRouteLane(), effectiveIncomingText).getCandidateLines();
		final String bankLine = chooseFreshCandidateLine(candidateLines, recentConversationSummary);
		if ((bankLine != null) && !bankLine.isBlank())
		{
			return bankLine;
		}
		if (!"unknown".equalsIgnoreCase(knowledgeType) || "lineage_lore".equalsIgnoreCase(messageCategory) || "weather".equalsIgnoreCase(messageCategory) || "relationship_probe".equalsIgnoreCase(messageCategory) || "pvp_conflict".equalsIgnoreCase(messageCategory) || "progression_strength".equalsIgnoreCase(messageCategory) || "party_farm".equalsIgnoreCase(messageCategory))
		{
			return chooseKnowledgeGuardFallbackLine(incomingText, channel, messageCategory, knowledgeType);
		}
		return ((bankLine != null) && !bankLine.isBlank()) ? bankLine : fallbackLine;
	}

	private String trySimpleMathFallback(String incomingText)
	{
		final String normalized = normalizeReplyInputText(incomingText);
		if (normalized.isBlank())
		{
			return null;
		}

		Matcher matcher = SIMPLE_MATH_WHATS_PATTERN.matcher(normalized);
		if (!matcher.find())
		{
			matcher = SIMPLE_MATH_INLINE_PATTERN.matcher(normalized);
			if (!matcher.find())
			{
				return null;
			}
		}

		try
		{
			final long left = Long.parseLong(matcher.group(1));
			final String operator = matcher.group(2);
			final long right = Long.parseLong(matcher.group(3));
			final long value;
			if ("+".equals(operator))
			{
				value = left + right;
			}
			else if ("-".equals(operator))
			{
				value = left - right;
			}
			else
			{
				value = left * right;
			}
			return String.valueOf(value) + ".";
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private String chooseFreshCandidateLine(List<String> candidateLines, String recentConversationSummary)
	{
		if ((candidateLines == null) || candidateLines.isEmpty())
		{
			return null;
		}

		final String recent = (recentConversationSummary == null) ? "" : recentConversationSummary.toLowerCase(Locale.ROOT);
		for (String candidate : candidateLines)
		{
			if ((candidate != null) && !candidate.isBlank() && !isInternalGuidanceLine(candidate) && !recent.contains(candidate.toLowerCase(Locale.ROOT)))
			{
				return candidate;
			}
		}
		for (String candidate : candidateLines)
		{
			if ((candidate != null) && !candidate.isBlank() && !isInternalGuidanceLine(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	private String chooseKnowledgeGuardFallbackLine(String incomingText, String channel, String messageCategory, String knowledgeType)
	{
		final String normalized = (incomingText == null) ? "" : incomingText.toLowerCase(Locale.ROOT);
		final boolean whisper = "whisper".equalsIgnoreCase(channel);
		final FpcReplySettings settings = _replySettingsData.getSettings();
		if ("pvp_conflict".equalsIgnoreCase(messageCategory))
		{
			if ("defensive".equalsIgnoreCase(settings.getPvpConflictPolicy()))
			{
				return whisper ? "Stay near guards first, then tell me who pressed you." : "Stay near guards first.";
			}
			return whisper ? "Stay near guards first. Tell me names, not blood." : "Stay near guards first.";
		}
		if ("progression_strength".equalsIgnoreCase(messageCategory) || "progression_strength".equalsIgnoreCase(knowledgeType))
		{
			if ("basic".equalsIgnoreCase(settings.getProgressionCoachingLevel()))
			{
				return whisper ? "Start with skills first, then patch the weakest gear piece." : "Skills first. Weak gear second.";
			}
			if ("theorycrafter".equalsIgnoreCase(settings.getProgressionCoachingLevel()))
			{
				return whisper ? "Fix the weakest link first: skills, core gear, then the sustain loop that keeps your farming alive." : "Fix the weakest core first.";
			}
			return whisper ? "Start with skills, core gear, and the adena flow that sustains both." : "Skills and core gear first.";
		}
		if ("party_farm".equalsIgnoreCase(messageCategory))
		{
			if ("simple_zone_pick".equalsIgnoreCase(settings.getFarmAdviceMode()))
			{
				return whisper ? "If you need a blind first answer, start with Agony." : "Agony first.";
			}
			if ("full_route_planner".equalsIgnoreCase(settings.getFarmAdviceMode()))
			{
				return whisper ? "Tell me level, class, and whether you want xp, adena, or a drop. Then I can map the route." : "Level, class, and goal first.";
			}
			return whisper ? "Tell me your level and goal first, then I can judge the route." : "Level and goal first.";
		}
		if ("relationship_probe".equalsIgnoreCase(messageCategory))
		{
			return whisper ? "Ask them directly. I will not fake their reasons for you." : "Ask them directly, not me.";
		}
		if ("weather".equalsIgnoreCase(messageCategory))
		{
			return whisper ? "I would not swear to the sky from here." : "I cant read that sky from here.";
		}
		if ("class_skills".equalsIgnoreCase(knowledgeType) && containsAny(normalized, "assassin"))
		{
			if ("warg_only".equalsIgnoreCase(settings.getOffscopeClassKnowledge()))
			{
				return whisper ? "Ask me Warg, not Assassin. I would rather stay honest." : "Ask me Warg, not Assassin.";
			}
			if ("broad_veteran".equalsIgnoreCase(settings.getOffscopeClassKnowledge()))
			{
				return whisper ? "Broadly, Assassin leans burst and mobility, but I will not bluff the exact live kit." : "Assassin is bursty, but I wont fake the live kit.";
			}
			return whisper ? "I know the Warg path better than Assassin's exact current kit." : "I know Warg better than Assassin.";
		}
		if ("class_choice".equalsIgnoreCase(knowledgeType))
		{
			return whisper ? "There is no one best AoE class without your gear, budget, and pace." : "No one best AoE class.";
		}
		if ("farming".equalsIgnoreCase(knowledgeType) && containsAny(normalized, "99", "lvl 99", "level 99"))
		{
			return whisper ? "I would not swear to one 99 spot without your gear and the current patch." : "99 farming depends on gear and patch.";
		}
		if ("travel_destination".equalsIgnoreCase(knowledgeType))
		{
			if ("immersive_first".equalsIgnoreCase(settings.getTravelStyle()))
			{
				return whisper ? "Ask a Gatekeeper first. I would not trust a blind road to that place." : "Ask a Gatekeeper first.";
			}
			return whisper ? "Use the teleport window or a Gatekeeper, then check the destination list." : "Check Gatekeeper or teleport list.";
		}
		if ("itemization".equalsIgnoreCase(knowledgeType))
		{
			return whisper ? "I can give broad priorities, not exact current market math." : "Broad priorities, not exact prices.";
		}
		if ("system_basics".equalsIgnoreCase(knowledgeType) || "progression_route".equalsIgnoreCase(knowledgeType))
		{
			return whisper ? "Ask the exact Essence loop and I will keep it grounded." : "Ask it plainly and I'll keep it grounded.";
		}
		if ("creative".equalsIgnoreCase(settings.getKnowledgeConfidencePolicy()))
		{
			return whisper ? "Give me the exact edge case and I will try to narrow it honestly." : "Ask it a little more plainly.";
		}
		if ("balanced".equalsIgnoreCase(settings.getKnowledgeConfidencePolicy()))
		{
			return whisper ? "Give me the exact edge case and I will narrow what I can." : "I can narrow it if you make it exact.";
		}
		return whisper ? "I know the Warg path better than every current edge case." : "I would rather not bluff that.";
	}

	private String normalizeFallbackIdentity(String fakePlayerName)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank())
		{
			return "here";
		}
		return fakePlayerName.trim();
	}
	
	private List<String> resolveNamedTalkableFakePlayers(String text)
	{
		final String normalizedText = normalizeForCompare(text);
		if (normalizedText.isBlank())
		{
			return List.of();
		}
		
		final Set<String> matches = new LinkedHashSet<>();
		for (String token : normalizedText.split("\\s+"))
		{
			if (matches.size() >= MAX_MULTI_FPC_REPLIES)
			{
				break;
			}
			if ((token == null) || token.isBlank())
			{
				continue;
			}
			final String properName = FakePlayerData.getInstance().getProperName(token);
			final FpcDefinition definition = resolveActiveTalkableDefinition(((properName != null) && !properName.isBlank()) ? properName : token);
			if (definition != null)
			{
				matches.add(definition.getName());
			}
		}
		return new ArrayList<>(matches);
	}

	private List<String> resolveHybridPartyAddressedFakePlayers(Player sender, String text)
	{
		if ((sender == null) || (_hybridPartyService == null))
		{
			return List.of();
		}

		final List<Npc> hybridMembers = _hybridPartyService.getHybridPartyMembers(sender);
		if (hybridMembers.isEmpty())
		{
			return List.of();
		}

		final List<FpcDefinition> talkableDefinitions = new ArrayList<>();
		for (Npc npc : hybridMembers)
		{
			final FpcDefinition definition = resolveActiveTalkableDefinition(npc.getName());
			if ((definition != null) && isActiveDefinitionNpc(definition, npc))
			{
				talkableDefinitions.add(definition);
			}
		}
		if (talkableDefinitions.isEmpty())
		{
			return List.of();
		}
		if (talkableDefinitions.size() == 1)
		{
			return List.of(talkableDefinitions.get(0).getName());
		}

		final String normalizedText = normalizeForCompare(text);
		if (normalizedText.isBlank())
		{
			return List.of();
		}

		final Set<String> matches = new LinkedHashSet<>();
		for (FpcDefinition definition : talkableDefinitions)
		{
			if (matches.size() >= MAX_MULTI_FPC_REPLIES)
			{
				break;
			}
			if (containsNameReference(normalizedText, definition.getName()))
			{
				matches.add(definition.getName());
			}
		}
		return new ArrayList<>(matches);
	}

	private FakePlayerAdvisoryPlan buildCompanionDirectiveReplyPlan(String fakePlayerName, Player sender, String incomingText, String channel, boolean shareLocation)
	{
		if ((sender == null) || (fakePlayerName == null) || fakePlayerName.isBlank())
		{
			return null;
		}

		final FpcDefinition definition = findFpcDefinition(fakePlayerName);
		if (definition == null)
		{
			return null;
		}

		final String normalized = normalizeForCompare(normalizeReplyInputText(incomingText));
		final String category = _companionDirectiveService.classifyDirectiveCategory(definition, definition.getId(), sender, normalized, shareLocation);
		if (category.isBlank())
		{
			return null;
		}

		final String effectiveChannel = "party".equalsIgnoreCase(channel) ? "party" : "whisper";
		final String recentConversationSummary = _chatService.describeRecentConversation(fakePlayerName, sender.getName());
		final FpcReplyBankSelection selection = _replyBankService.select(fakePlayerName, sender.getName(), effectiveChannel, category, "social", normalizeReplyInputText(incomingText));
		String selectedLine = chooseFreshCandidateLine(selection.getCandidateLines(), recentConversationSummary);
		if ((selectedLine == null) || selectedLine.isBlank())
		{
			selectedLine = _companionDirectiveService.chooseFallbackLine(category);
		}
		if ((selectedLine == null) || selectedLine.isBlank())
		{
			return null;
		}

		return new FakePlayerAdvisoryPlan(fakePlayerName, "calm", "idle", _ruleset.getDefaultSocialZoneId(), true, "friendly_short", category, "companion_directive", selectedLine, 0.92);
	}

	private void applyCompanionDirectiveAction(String fakePlayerName, Player sender, FakePlayerAdvisoryPlan plan)
	{
		_companionDirectiveService.applyDirectiveAction(fakePlayerName, sender, plan);
	}

	private void recordHybridClanLifecycleBreach(String fakePlayerName, String actorName, String topic, String detail)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank() || (actorName == null) || actorName.isBlank())
		{
			return;
		}

		_chatService.recordHostilePlayerAction(fakePlayerName, actorName, "abandoned_me");
		_chatService.resetRelationshipWarmth(fakePlayerName, actorName, topic, 3, 4);
		_socialMemoryService.recordEpisodeIfNotRecent(fakePlayerName, actorName, FpcSocialEpisodeType.ABANDONED_ME, detail, 60000L);
	}

	private void resetHybridClanWarmth(String fakePlayerName, Clan clan, String topic, int tensionFloor, int resentmentFloor)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank() || (clan == null))
		{
			return;
		}

		for (ClanMember member : clan.getMembers())
		{
			if ((member == null) || (member.getName() == null) || member.getName().isBlank())
			{
				continue;
			}

			_chatService.resetRelationshipWarmth(fakePlayerName, member.getName(), topic, tensionFloor, resentmentFloor);
		}
	}

	private void clearHybridClanSocialMemory(String fakePlayerName, Clan clan)
	{
		if ((fakePlayerName == null) || fakePlayerName.isBlank() || (clan == null))
		{
			return;
		}

		for (ClanMember member : clan.getMembers())
		{
			if ((member == null) || (member.getName() == null) || member.getName().isBlank())
			{
				continue;
			}

			_socialMemoryService.clearPairHistory(fakePlayerName, member.getName());
		}
	}

	private void evaluateHybridClanPressure(FpcDefinition definition, Clan clan, Player triggerPlayer, String reason)
	{
		if ((definition == null) || (clan == null) || (_hybridClanService.getHybridClanId(definition.getId()) != clan.getId()))
		{
			return;
		}

		int pressure = 0;
		for (ClanMember member : clan.getMembers())
		{
			if ((member == null) || (member.getName() == null) || member.getName().isBlank())
			{
				continue;
			}

			final FpcRelationshipSnapshot snapshot = _chatService.getRelationshipSnapshot(definition.getName(), member.getName());
			pressure += snapshot.getTension();
			pressure += snapshot.getResentment();
			pressure += Math.min(6, snapshot.getPlayerKillCount() * 2);
		}

		if (pressure < _config.getHybridClanMemberPressureLeaveThreshold())
		{
			return;
		}

		final int finalPressure = pressure;
		resetHybridClanWarmth(definition.getName(), clan, "left_clan_under_pressure", 2, 3);
		_hybridClanService.clearHybridContract(definition.getId(), "clan_pressure_" + reason);
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " left clan=" + clan.getName() + " under hybrid clan pressure=" + finalPressure + " threshold=" + _config.getHybridClanMemberPressureLeaveThreshold() + " trigger=" + reason + " player=" + ((triggerPlayer == null) ? "none" : triggerPlayer.getName()));
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=hybrid_clan_leave pressure=" + finalPressure + " threshold=" + _config.getHybridClanMemberPressureLeaveThreshold() + " trigger=" + reason);
	}

	private List<String> resolveHybridClanAddressedFakePlayers(Clan clan, String text)
	{
		final String normalizedText = normalizeForCompare(text);
		if ((clan == null) || normalizedText.isBlank())
		{
			return List.of();
		}

		final Set<String> matches = new LinkedHashSet<>();
		for (HybridClanMemberSnapshot member : _hybridClanService.getHybridClanMembers(clan))
		{
			if (matches.size() >= MAX_MULTI_FPC_REPLIES)
			{
				break;
			}

			final FpcDefinition definition = resolveActiveTalkableDefinition(member.getName());
			if ((definition != null) && containsNameReference(normalizedText, definition.getName()))
			{
				matches.add(definition.getName());
			}
		}
		return new ArrayList<>(matches);
	}

	private FpcDefinition resolveActiveTalkableDefinition(String idOrName)
	{
		final FpcDefinition definition = findFpcDefinition(idOrName);
		if ((definition == null) || !definition.isTalkable())
		{
			return null;
		}
		return (findActiveFpcNpc(definition.getId()) != null) ? definition : null;
	}

	private boolean isActiveDefinitionNpc(FpcDefinition definition, Npc npc)
	{
		if ((definition == null) || (npc == null))
		{
			return false;
		}

		final Npc activeNpc = findActiveFpcNpc(definition.getId());
		return (activeNpc != null) && (activeNpc.getObjectId() == npc.getObjectId());
	}
	
	private static boolean containsAny(String text, String... needles)
	{
		for (String needle : needles)
		{
			if (text.contains(needle))
			{
				return true;
			}
		}
		return false;
	}

	private void markHybridCompanionActivity(Player player, String source)
	{
		if ((player == null) || (_hybridPartyService == null))
		{
			return;
		}

		_hybridPartyService.markLeaderActivity(player, source);
	}

	private boolean requiresGoodFriendCompanionBond(FpcDefinition definition)
	{
		return (definition != null) && (definition.getCompanionProfile() != null) && definition.getCompanionProfile().requiresGoodFriendRelation();
	}

	private boolean requiresManualExclusiveTrustedClan(FpcDefinition definition)
	{
		return requiresGoodFriendCompanionBond(definition) && (definition.getCompanionProfile() != null) && definition.getCompanionProfile().isManualExclusiveClanJoinMode();
	}

	private String chooseCompanionRelationGateDeclineLine(FpcDefinition definition, boolean partyInvite)
	{
		final String normalizedId = normalizeForCompare(definition != null ? definition.getId() : "");
		if ("marc".equals(normalizedId))
		{
			return partyInvite ? "Not yet. I travel close only with people who have already proven they stay." : "Not yet. I will wear a crest only for someone I trust that deeply.";
		}
		return partyInvite ? "Not yet. I travel that closely only with trusted friends." : "Not yet. I take a clan crest only from someone I trust that deeply.";
	}
	
	private static String stripSpeakerPrefix(String line, String fakePlayerName)
	{
		String result = line.trim();
		final String lower = result.toLowerCase(Locale.ROOT);
		final String nameLower = fakePlayerName.toLowerCase(Locale.ROOT) + ":";
		
		if (lower.startsWith(nameLower))
		{
			result = result.substring(fakePlayerName.length() + 1).trim();
		}
		if (result.startsWith("\"") && result.endsWith("\"") && (result.length() > 1))
		{
			result = result.substring(1, result.length() - 1).trim();
		}
		return result;
	}
	
	private static String collapseWhitespace(String line)
	{
		return line.replaceAll("\\s+", " ").trim();
	}
	
	private String trimToSentenceBoundary(String line)
	{
		return trimToSentenceBoundary(line, "general");
	}

	private String trimToSentenceBoundary(String line, String channel)
	{
		final boolean allowSecondSentence = "whisper".equalsIgnoreCase(channel) && _replySettingsData.getSettings().isWhisperAllowSecondSentence();
		int sentenceCount = 0;
		for (int i = 0; i < line.length(); i++)
		{
			final char c = line.charAt(i);
			if ((c == '.') || (c == '!') || (c == '?'))
			{
				sentenceCount++;
				if (!allowSecondSentence || (sentenceCount >= 2))
				{
					return line.substring(0, i + 1).trim();
				}
			}
		}
		return line.trim();
	}
	
	private static String limitWords(String line, int maxWords)
	{
		final String[] parts = line.trim().split("\\s+");
		if (parts.length <= maxWords)
		{
			return line.trim();
		}
		
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < maxWords; i++)
		{
			if (i > 0)
			{
				sb.append(' ');
			}
			sb.append(parts[i]);
		}
		return sb.toString().trim();
	}

	private int maxWordsForChannel(String channel)
	{
		final FpcReplySettings settings = _replySettingsData.getSettings();
		if ("whisper".equalsIgnoreCase(channel))
		{
			return Math.max(settings.getWhisperMaxWords(), 1);
		}
		if ("general".equalsIgnoreCase(channel))
		{
			return Math.max(settings.getGeneralMaxWords(), 1);
		}
		return Math.max(settings.getPublicMaxWords(), 1);
	}

	private int maxCharsForChannel(String channel)
	{
		if ("whisper".equalsIgnoreCase(channel))
		{
			return 110;
		}
		if ("general".equalsIgnoreCase(channel))
		{
			return 70;
		}
		return 70;
	}
	
	private static String limitChars(String line, int maxChars)
	{
		if (line.length() <= maxChars)
		{
			return line;
		}
		
		int cut = line.lastIndexOf(' ', maxChars);
		if (cut < 0)
		{
			cut = maxChars;
		}
		return line.substring(0, cut).trim() + ".";
	}

	private static String shortenReply(String line, int maxWords, int maxChars)
	{
		String shortened = line.trim();
		if (shortened.isEmpty())
		{
			return shortened;
		}
		if (countWords(shortened) > maxWords)
		{
			shortened = limitWords(shortened, maxWords);
		}
		if (shortened.length() > maxChars)
		{
			shortened = limitChars(shortened, maxChars);
		}
		shortened = trimDanglingEnding(shortened);
		return ensureTerminalPunctuation(shortened);
	}

	private static int countWords(String line)
	{
		return line.trim().isEmpty() ? 0 : line.trim().split("\\s+").length;
	}

	private static String trimDanglingEnding(String line)
	{
		String result = line.trim();
		while (!result.isEmpty())
		{
			final String terminal = terminalWord(result);
			if (!BAD_REPLY_ENDINGS.contains(terminal))
			{
				break;
			}
			final int cut = result.lastIndexOf(' ');
			if (cut <= 0)
			{
				return "";
			}
			result = result.substring(0, cut).trim();
		}
		return result;
	}

	private static String terminalWord(String line)
	{
		final String normalized = normalizeForCompare(line);
		if (normalized.isBlank())
		{
			return "";
		}
		final String[] parts = normalized.split("\\s+");
		return parts[parts.length - 1];
	}

	private static String ensureTerminalPunctuation(String line)
	{
		if (line == null)
		{
			return null;
		}
		final String result = stripTrailingSeparators(line.trim());
		if (result.isEmpty())
		{
			return result;
		}
		final char last = result.charAt(result.length() - 1);
		return ((last == '.') || (last == '!') || (last == '?')) ? result : (result + ".");
	}

	private static String stripTrailingSeparators(String line)
	{
		String result = line;
		while (!result.isEmpty())
		{
			final char last = result.charAt(result.length() - 1);
			if ((last != ',') && (last != ';') && (last != ':') && (last != '-') && (last != '/'))
			{
				break;
			}
			result = result.substring(0, result.length() - 1).trim();
		}
		return result;
	}

	private static boolean isInternalGuidanceLine(String line)
	{
		final String normalized = normalizeForCompare(line);
		return normalized.startsWith("favor system first")
			|| normalized.startsWith("ask for level and goal before endorsing")
			|| normalized.contains("afpcs should")
			|| normalized.contains("later afpcs can")
			|| normalized.contains("good baseline for")
			|| normalized.startsWith("guide beginners toward")
			|| normalized.startsWith("use tradeoff wording")
			|| normalized.startsWith("use for ")
			|| normalized.startsWith("scope ");
	}
	
	private static boolean looksLikeEcho(String reply, String incoming)
	{
		if ((reply == null) || (incoming == null))
		{
			return false;
		}
		
		final String a = normalizeForCompare(reply);
		final String b = normalizeForCompare(incoming);
		
		if (a.isBlank() || b.isBlank())
		{
			return false;
		}
		
		if (a.equals(b))
		{
			return true;
		}
		
		return (a.length() >= 12) && (b.contains(a) || a.contains(b));
	}
	
	private static String normalizeForCompare(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}

	private void triggerNearbyClanAssistForAfpc(FpcDefinition alliedDefinition, Npc alliedNpc, Player aggressor, boolean killed)
	{
		if ((alliedDefinition == null) || (alliedNpc == null) || (aggressor == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(alliedNpc, _config.getPersonalityAllyAssistRange()))
		{
			if (npc.getObjectId() == alliedNpc.getObjectId())
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !isHybridClanMate(npc, alliedNpc) || isHybridClanMate(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, alliedNpc))
			{
				continue;
			}

			if (killed)
			{
				_chatService.recordHostilePlayerAction(definition.getName(), aggressor.getName(), "witnessed_pk");
				_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), aggressor.getName(), FpcSocialEpisodeType.WITNESSED_PK, "player killed a nearby clanmate", 15000L);
			}

			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, aggressor, killed ? FakePlayerAfpcCombatPolicyService.Trigger.CLAN_MEMBER_KILLED : FakePlayerAfpcCombatPolicyService.Trigger.CLAN_MEMBER_UNDER_ATTACK);
			final String reason = killed ? "clan_member_killed" : "clan_member_under_attack";
			triggerAfpcConflict(definition, npc, aggressor, decision, reason, "event=afpc_clan_support ally=" + alliedDefinition.getName() + " reason=" + reason);
		}
	}

	private void triggerNearbyPartyAssistForAfpc(FpcDefinition alliedDefinition, Npc alliedNpc, Player aggressor, boolean killed)
	{
		if ((alliedDefinition == null) || (alliedNpc == null) || (aggressor == null) || (_hybridPartyService == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(alliedNpc, _config.getPersonalityAllyAssistRange()))
		{
			if (npc.getObjectId() == alliedNpc.getObjectId())
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !isHybridPartyMate(npc, alliedNpc) || isGoodFriendPartyMember(definition, npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, alliedNpc))
			{
				continue;
			}

			if (killed)
			{
				_chatService.recordHostilePlayerAction(definition.getName(), aggressor.getName(), "witnessed_pk");
				_socialMemoryService.recordEpisodeIfNotRecent(definition.getName(), aggressor.getName(), FpcSocialEpisodeType.WITNESSED_PK, "player killed a nearby party friend", 15000L);
			}

			final FakePlayerAfpcCombatPolicyService.Trigger trigger = killed ? FakePlayerAfpcCombatPolicyService.Trigger.PARTY_FRIEND_KILLED : FakePlayerAfpcCombatPolicyService.Trigger.PARTY_FRIEND_UNDER_ATTACK;
			final FakePlayerAfpcCombatPolicyService.Decision decision = _afpcCombatPolicyService.evaluate(definition, aggressor, trigger);
			final String reason = killed ? "party_friend_killed" : "party_friend_under_attack";
			triggerAfpcConflict(definition, npc, aggressor, decision, reason, "event=afpc_party_support ally=" + alliedDefinition.getName() + " reason=" + reason);
		}
	}

	private void triggerNearbyClanAssistForAfpc(FpcDefinition alliedDefinition, Npc alliedNpc, Npc aggressor, boolean killed)
	{
		if ((alliedDefinition == null) || (alliedNpc == null) || (aggressor == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(alliedNpc, _config.getPersonalityAllyAssistRange()))
		{
			if ((npc.getObjectId() == alliedNpc.getObjectId()) || (npc.getObjectId() == aggressor.getObjectId()))
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !isHybridClanMate(npc, alliedNpc) || isHybridClanMate(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, alliedNpc))
			{
				continue;
			}

			final String reason = killed ? "clan_member_killed_fpc" : "clan_member_under_attack_fpc";
			final long memoryMs = killed ? _config.getPersonalityClanAssistMemoryMs() : Math.max(_config.getPersonalityClanAssistMemoryMs(), _config.getPersonalityRetaliationMemoryMs());
			triggerAfpcCreatureConflict(definition, npc, aggressor, reason, false, _config.getPersonalityAllyAssistRange(), memoryMs, "event=afpc_clan_support ally=" + alliedDefinition.getName() + " aggressor=" + aggressor.getName() + " reason=" + reason);
		}
	}

	private void triggerNearbyPartyAssistForAfpc(FpcDefinition alliedDefinition, Npc alliedNpc, Npc aggressor, boolean killed)
	{
		if ((alliedDefinition == null) || (alliedNpc == null) || (aggressor == null) || (_hybridPartyService == null))
		{
			return;
		}

		for (Npc npc : collectNearbyCombatReadyAfpcs(alliedNpc, _config.getPersonalityAllyAssistRange()))
		{
			if ((npc.getObjectId() == alliedNpc.getObjectId()) || (npc.getObjectId() == aggressor.getObjectId()))
			{
				continue;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (!isCombatReadyAdventurer(definition, npc) || !isHybridPartyMate(npc, alliedNpc) || isHybridPartyMate(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, aggressor) || !_platformAdapter.getNavigationFacade().canSeeTarget(npc, alliedNpc))
			{
				continue;
			}

			final String reason = killed ? "party_friend_killed_fpc" : "party_friend_under_attack_fpc";
			final long memoryMs = killed ? Math.max(_config.getPersonalityRetaliationMemoryMs(), _config.getPersonalityPkWitnessMemoryMs()) : _config.getPersonalityRetaliationMemoryMs();
			triggerAfpcCreatureConflict(definition, npc, aggressor, reason, false, _config.getPersonalityAllyAssistRange(), memoryMs, "event=afpc_party_support ally=" + alliedDefinition.getName() + " aggressor=" + aggressor.getName() + " reason=" + reason);
		}
	}

	private List<Npc> collectNearbyCombatReadyAfpcs(WorldObject primaryAnchor, WorldObject secondaryAnchor, int range)
	{
		final Map<Integer, Npc> nearby = new LinkedHashMap<>();
		collectNearbyCombatReadyAfpcs(primaryAnchor, range, nearby);
		collectNearbyCombatReadyAfpcs(secondaryAnchor, range, nearby);
		return new ArrayList<>(nearby.values());
	}

	private List<Npc> collectNearbyCombatReadyAfpcs(WorldObject anchor, int range)
	{
		final Map<Integer, Npc> nearby = new LinkedHashMap<>();
		collectNearbyCombatReadyAfpcs(anchor, range, nearby);
		return new ArrayList<>(nearby.values());
	}

	private void collectNearbyCombatReadyAfpcs(WorldObject anchor, int range, Map<Integer, Npc> result)
	{
		if ((anchor == null) || (result == null))
		{
			return;
		}

		_platformAdapter.getWorldFacade().forEachVisibleObjectInRange(anchor, Npc.class, Math.max(range, 200), npc ->
		{
			if ((npc == null) || !npc.isFakePlayer())
			{
				return;
			}

			final FpcDefinition definition = resolveDefinitionByActiveNpc(npc);
			if (isCombatReadyAdventurer(definition, npc))
			{
				result.putIfAbsent(npc.getObjectId(), npc);
			}
		});
	}

	private boolean isHybridClanMate(Npc npc, Player player)
	{
		if ((npc == null) || (player == null) || (player.getClanId() <= 0))
		{
			return false;
		}

		final Clan clan = _hybridClanService.getHybridClan(npc);
		return (clan != null) && (clan.getId() == player.getClanId());
	}

	private boolean isHybridClanMate(Npc leftNpc, Npc rightNpc)
	{
		if ((leftNpc == null) || (rightNpc == null))
		{
			return false;
		}

		final Clan leftClan = _hybridClanService.getHybridClan(leftNpc);
		final Clan rightClan = _hybridClanService.getHybridClan(rightNpc);
		return (leftClan != null) && (rightClan != null) && (leftClan.getId() == rightClan.getId());
	}

	private boolean isHybridPartyMate(Npc leftNpc, Npc rightNpc)
	{
		if ((leftNpc == null) || (rightNpc == null) || (_hybridPartyService == null))
		{
			return false;
		}
		return _hybridPartyService.isHybridPartyMember(leftNpc, rightNpc);
	}

	private boolean isGoodFriendPartyMember(FpcDefinition definition, Npc npc, Player player)
	{
		if ((definition == null) || (npc == null) || (player == null))
		{
			return false;
		}
		if (!_hybridPartyService.getHybridPartyPlayers(npc).contains(player))
		{
			return false;
		}
		return _afpcCombatPolicyService.isGoodFriend(definition, player);
	}

	private void triggerAfpcConflict(FpcDefinition definition, Npc npc, Player target, FakePlayerAfpcCombatPolicyService.Decision decision, String reason, String debugDetail)
	{
		if ((definition == null) || (npc == null) || !(npc instanceof Attackable) || (target == null) || (decision == null) || !decision.isEngage())
		{
			return;
		}

		final String normalizedReason = ((reason == null) || reason.isBlank()) ? "personality_conflict" : reason.trim().toLowerCase(Locale.ROOT);
		final String dedupeKey = definition.getId().toLowerCase(Locale.ROOT) + "|" + target.getObjectId() + "|" + normalizedReason;
		if (isRecentCombatPressureTrigger(dedupeKey, 5000L))
		{
			return;
		}

		_taskService.markPersonalityConflictTarget(definition, npc, target, normalizedReason, decision.isAllowPkEscalation(), decision.getLeashRange(), decision.getMemoryMs());
		final StringBuilder detail = new StringBuilder();
		detail.append((debugDetail == null) ? "event=afpc_conflict" : debugDetail);
		detail.append(" target=").append(target.getName()).append("(").append(target.getObjectId()).append(")");
		detail.append(" score=").append(decision.getPressureScore());
		detail.append(" aggression=").append(decision.getPersonalityAggression());
		detail.append(" pkSupport=").append(decision.isAllowPkEscalation());
		detail.append(" memoryMs=").append(decision.getMemoryMs());
		detail.append(" reason=").append(decision.getReason());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, detail.toString());
	}

	private void triggerAfpcCreatureConflict(FpcDefinition definition, Npc npc, Creature target, String reason, boolean allowPkEscalation, int leashRange, long memoryMs, String debugDetail)
	{
		if ((definition == null) || (npc == null) || !(npc instanceof Attackable) || (target == null))
		{
			return;
		}

		final String normalizedReason = ((reason == null) || reason.isBlank()) ? "personality_conflict" : reason.trim().toLowerCase(Locale.ROOT);
		final String dedupeKey = definition.getId().toLowerCase(Locale.ROOT) + "|" + target.getObjectId() + "|" + normalizedReason;
		if (isRecentCombatPressureTrigger(dedupeKey, 5000L))
		{
			return;
		}

		_taskService.markPersonalityConflictTarget(definition, npc, target, normalizedReason, allowPkEscalation, leashRange, memoryMs);
		final StringBuilder detail = new StringBuilder();
		detail.append((debugDetail == null) ? "event=afpc_conflict" : debugDetail);
		detail.append(" target=").append(target.getName()).append("(").append(target.getObjectId()).append(")");
		detail.append(" pkSupport=").append(allowPkEscalation);
		detail.append(" memoryMs=").append(memoryMs);
		detail.append(" reason=").append(normalizedReason);
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, detail.toString());
	}

	private boolean isCombatReadyAdventurer(FpcDefinition definition, Npc npc)
	{
		return (definition != null) && (npc instanceof Attackable) && (definition.getAdventurerProfile() != null) && definition.getAdventurerProfile().isAdventurerTier() && "synthetic".equalsIgnoreCase(definition.getAdventurerProfile().getCombatMode());
	}

	private void clearHybridPartyContractForCombat(FpcDefinition definition, Creature attacker, String source)
	{
		if ((_hybridPartyService == null) || (definition == null))
		{
			return;
		}

		final Player leader = _hybridPartyService.getActivePlayerLeader(definition.getId());
		if (leader == null)
		{
			return;
		}

		if ((definition.getCompanionProfile() != null) && definition.getCompanionProfile().isStickySession())
		{
			_hybridPartyService.markLeaderActivity(definition.getId(), leader, "combat");
			_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=hybrid_party_retain reason=attacked_while_party leader=" + leader.getName() + " attacker=" + ((attacker != null) ? attacker.getName() : "unknown") + " source=" + source);
			return;
		}

		_hybridPartyService.clearHybridContract(definition.getId(), "attacked_while_party");
		_debugService.trace(definition.getId(), FpcDebugCategory.SOCIAL, "event=hybrid_party_break reason=attacked_while_party leader=" + leader.getName() + " attacker=" + ((attacker != null) ? attacker.getName() : "unknown") + " source=" + source);
	}

	private String buildConversationAwareText(String fakePlayerName, String senderName, String incomingText)
	{
		return _chatService.buildConversationAwareText(fakePlayerName, senderName, normalizeReplyInputText(incomingText), _replySettingsData.getSettings().getFollowUpStrictness());
	}

	private boolean hasRecentConversationHistory(String fakePlayerName, String senderName)
	{
		return !_chatService.describeRecentConversation(fakePlayerName, senderName).isBlank();
	}

	private FakePlayerAdvisoryPlan buildForcedFallbackReplyPlan(FakePlayerAdvisoryPlan plan, String fakePlayerName, String fallbackReplySource, String selectedLine)
	{
		return new FakePlayerAdvisoryPlan(fakePlayerName, (plan != null) ? plan.getMoodTag() : "calm", (plan != null) ? plan.getIntentPreference() : "idle", (plan != null) ? plan.getPreferredZone() : _ruleset.getDefaultSocialZoneId(), true, (plan != null) ? plan.getLineStyleTag() : "friendly_short", (plan != null) ? plan.getTopicTag() : "smalltalk", (plan != null) ? plan.getReplySource() : fallbackReplySource, selectedLine, (plan != null) ? plan.getConfidence() : 0.50);
	}

	private FallbackReplyContext buildFallbackReplyContext(String fakePlayerName, String senderName, ReplyAnalysisSnapshot analysis)
	{
		return new FallbackReplyContext(isMarcSpeakingToTrustedPlayer(fakePlayerName, senderName), hasRecentConversationHistory(fakePlayerName, senderName), analysis, _replySettingsData.getSettings());
	}

	private boolean isConversationAwareReplyChannel(String channel)
	{
		return "whisper".equalsIgnoreCase(channel) || "clan".equalsIgnoreCase(channel) || "party".equalsIgnoreCase(channel);
	}

	private String buildReplyAnalysisText(String fakePlayerName, String senderName, String incomingText, String channel)
	{
		return isConversationAwareReplyChannel(channel) ? buildConversationAwareText(fakePlayerName, senderName, incomingText) : normalizeReplyInputText(incomingText);
	}

	private ReplyAnalysisSnapshot buildReplyAnalysisSnapshot(String fakePlayerName, String senderName, String incomingText, String effectiveIncomingText)
	{
		final FpcRouteProfile routeProfile = classifyRouteProfile(fakePlayerName, senderName, incomingText, effectiveIncomingText);
		return new ReplyAnalysisSnapshot(effectiveIncomingText, routeProfile, routeProfile.getMessageCategory(), routeProfile.getKnowledgeType(), extractFocusEntity(fakePlayerName, senderName, effectiveIncomingText), _messageClassifier.classifyFocusIntent(effectiveIncomingText));
	}

	private ReplyAnalysisSnapshot analyzeReply(String fakePlayerName, String senderName, String incomingText, String channel)
	{
		return buildReplyAnalysisSnapshot(fakePlayerName, senderName, incomingText, buildReplyAnalysisText(fakePlayerName, senderName, incomingText, channel));
	}

	private ReplyAnalysisSnapshot analyzeConversation(String fakePlayerName, String senderName, String incomingText)
	{
		return buildReplyAnalysisSnapshot(fakePlayerName, senderName, incomingText, buildConversationAwareText(fakePlayerName, senderName, incomingText));
	}

	private FpcRouteProfile classifyRouteProfile(String fakePlayerName, String senderName, String incomingText, String effectiveIncomingText)
	{
		final FpcRouteProfile initialRouteProfile = _messageClassifier.classifyRouteProfile(effectiveIncomingText, collectFocusCandidateNames(fakePlayerName), fakePlayerName, senderName);
		final String promotedCategory = promoteSocialSignalCategory(fakePlayerName, senderName, incomingText, effectiveIncomingText, initialRouteProfile.getMessageCategory());
		return _messageClassifier.overrideRouteProfile(effectiveIncomingText, promotedCategory, initialRouteProfile.getKnowledgeType());
	}

	private Map<String, Object> toRouteProfileMap(FpcRouteProfile routeProfile)
	{
		final Map<String, Object> route = new LinkedHashMap<>();
		if (routeProfile == null)
		{
			return route;
		}
		route.put("lane", routeProfile.getRouteLane());
		route.put("speechAct", routeProfile.getSpeechAct());
		route.put("knowledgeNeed", routeProfile.getKnowledgeNeed());
		route.put("socialStake", routeProfile.getSocialStake());
		route.put("actionRequested", routeProfile.isActionRequested());
		route.put("primaryTopic", routeProfile.getPrimaryTopic());
		route.put("secondaryTopics", routeProfile.getSecondaryTopics());
		route.put("confidence", routeProfile.getConfidence());
		return route;
	}

	private String extractFocusEntity(String fakePlayerName, String senderName, String effectiveIncomingText)
	{
		return _messageClassifier.extractFocusEntity(effectiveIncomingText, collectFocusCandidateNames(fakePlayerName), fakePlayerName, senderName);
	}

	private String promoteSocialSignalCategory(String fakePlayerName, String senderName, String incomingText, String effectiveIncomingText, String baseCategory)
	{
		if (_messageClassifier.isThreatSignal(incomingText, fakePlayerName))
		{
			return "threat";
		}
		if (_messageClassifier.isDirectFlame(incomingText, fakePlayerName))
		{
			return "insult";
		}
		if (_messageClassifier.isSelfhoodCategory(baseCategory))
		{
			return baseCategory;
		}
		if (_messageClassifier.isDistrustSignal(incomingText, fakePlayerName))
		{
			return "distrust";
		}
		if (_messageClassifier.isResentmentSignal(incomingText, fakePlayerName))
		{
			return "resentment";
		}
		if (_messageClassifier.isAbandonmentSignal(incomingText, fakePlayerName))
		{
			return "abandonment";
		}
		if (_messageClassifier.isApologySignal(incomingText))
		{
			return "apology";
		}
		if (_messageClassifier.isGratitudeSignal(incomingText))
		{
			return "thanks";
		}
		if (_messageClassifier.isRepairSignal(incomingText))
		{
			return "repair";
		}
		if (_messageClassifier.isAffectionSignal(incomingText, fakePlayerName))
		{
			return "affection";
		}
		if (_messageClassifier.isRespectSignal(incomingText, fakePlayerName))
		{
			return "respect";
		}
		if (_messageClassifier.isPraiseSignal(incomingText, fakePlayerName))
		{
			return "praise";
		}
		if (_messageClassifier.isReassuranceSignal(incomingText, fakePlayerName))
		{
			return "reassurance";
		}

		final String focusEntityName = extractFocusEntity(fakePlayerName, senderName, effectiveIncomingText);
		if ((_personaResolver.getPositiveBondIntensity(fakePlayerName, focusEntityName) >= 70) && _messageClassifier.isNegativeSubjectStatement(effectiveIncomingText, focusEntityName))
		{
			return "belief_conflict";
		}
		return baseCategory;
	}

	private List<String> collectFocusCandidateNames(String fakePlayerName)
	{
		final List<String> candidateNames = new ArrayList<>();
		for (FpcDefinition definition : getLoadedFpcDefinitions())
		{
			if ((definition == null) || (definition.getName() == null) || definition.getName().isBlank())
			{
				continue;
			}
			if ((fakePlayerName != null) && definition.getName().equalsIgnoreCase(fakePlayerName))
			{
				continue;
			}
			candidateNames.add(definition.getName());
		}
		return candidateNames;
	}

	private String buildEntityFallbackLine(String fakePlayerName, String focusEntityName, String messageCategory, boolean whisper)
	{
		final String focus = ((focusEntityName == null) || focusEntityName.isBlank()) ? "they" : focusEntityName.trim();
		final boolean adventurerGroup = "adventurers".equalsIgnoreCase(focus);
		final boolean elyra = "elyra".equals(normalizeForCompare(fakePlayerName));
		if ("entity_status".equalsIgnoreCase(messageCategory))
		{
			if (adventurerGroup)
			{
				return whisper ? "Adventurers never stay still for long." : "Adventurers never stay still long.";
			}
			if (elyra && "marc".equalsIgnoreCase(focus))
			{
				return whisper ? "Marc is keeping steadier than he was. I watch that closely." : "Marc is steadier now.";
			}
			return whisper ? focus + " is holding steady." : focus + " is steady enough.";
		}
		if ("entity_relationship".equalsIgnoreCase(messageCategory))
		{
			if (adventurerGroup)
			{
				return whisper ? "I stay cautious around adventurers." : "I stay cautious around adventurers.";
			}
			return whisper ? focus + " matters more than I explain lightly." : focus + " matters more than I explain.";
		}
		if ("entity_reason".equalsIgnoreCase(messageCategory))
		{
			return whisper ? "Some reasons stay close to the chest." : "Some reasons stay close.";
		}
		if ("entity_story".equalsIgnoreCase(messageCategory))
		{
			return whisper ? "That's a longer story than this whisper can hold." : "That's a longer story.";
		}
		if ("entity_memory".equalsIgnoreCase(messageCategory))
		{
			if (adventurerGroup)
			{
				return whisper ? "I remember enough about adventurers already." : "I remember enough already.";
			}
			return whisper ? "I remember enough about " + focus + "." : "I remember enough about " + focus + ".";
		}
		return whisper ? "I'm answering about someone else, not drifting away from them." : "I'm answering about someone else.";
	}

	private String buildBeliefConflictFallbackLine(String fakePlayerName, String focusEntityName, boolean whisper)
	{
		final String normalizedFakePlayer = normalizeForCompare(fakePlayerName);
		final String normalizedFocus = normalizeForCompare((focusEntityName == null) ? "" : focusEntityName);
		if ("elyra".equals(normalizedFakePlayer) && "marc".equals(normalizedFocus))
		{
			return whisper ? "Don't speak of Marc like that." : "Don't speak of Marc that way.";
		}
		if (!normalizedFocus.isBlank())
		{
			return whisper ? focusEntityName.trim() + " matters more than you think. Watch your tongue." : focusEntityName.trim() + " matters. Watch your tone.";
		}
		return whisper ? "Choose your words more carefully." : "Mind your tone.";
	}

	private String normalizeReplyInputText(String incomingText)
	{
		return (incomingText == null) ? "" : _messageClassifier.recoverTypoSensitiveText(incomingText).trim();
	}

	private String normalizeStudioChannel(String channel)
	{
		final String normalized = (channel == null) ? "" : channel.trim().toLowerCase(Locale.ROOT);
		return switch (normalized)
		{
			case "clan", "general", "shout", "world" -> normalized;
			default -> "whisper";
		};
	}
	
	public static FakePlayerModule getInstance()
	{
		return INSTANCE;
	}

	public FakePlayerHybridPartyService getHybridPartyService()
	{
		return _hybridPartyService;
	}

	public FakePlayerHybridClanService getHybridClanService()
	{
		return _hybridClanService;
	}

	public long getAdminHoldRemainingMs(FpcDefinition definition)
	{
		return (definition == null) ? 0L : _taskService.getAdminHoldRemainingMs(definition.getId());
	}

}

