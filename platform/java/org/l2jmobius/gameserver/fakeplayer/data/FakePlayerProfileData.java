package org.l2jmobius.gameserver.fakeplayer.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.FakePlayerData;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcAdventurerProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcChannelProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcCompanionProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSpawnProfile;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerWorldFacade;
import org.l2jmobius.gameserver.fakeplayer.service.FpcRegistry;

/**
 * Generic FPC definition loader for the first platform phase.
 * This currently uses a small controlled JSON parser because the file format is project-owned and intentionally narrow.
 */
public class FakePlayerProfileData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerProfileData.class.getName());
	
	private static final class FpcTemplateDefinition
	{
		private final String _templateId;
		private final String _title;
		private final Boolean _enabled;
		private final String _category;
		private final String _archetype;
		private final String _personaTemplate;
		private final String _carrierTemplate;
		private final Integer _carrierNpcId;
		private final Boolean _autoSpawnOnBoot;
		private final TemplateSpawnProfile _spawnProfile;
		private final TemplateChannelProfile _channelProfile;
		private final Boolean _talkable;
		private final String _fallbackProfile;
		private final String _availabilityProfile;
		private final String _fpcTag;
		private final String _presentationMode;
		private final String _carrierPolicy;
		private final String _presentationCarrierTemplate;
		private final Integer _presentationCarrierNpcId;
		private final String _speciesTag;
		private final String _familyTag;
		private final TemplateAdventurerProfile _adventurerProfile;
		
		private FpcTemplateDefinition(String templateId, String title, Boolean enabled, String category, String archetype, String personaTemplate, String carrierTemplate, Integer carrierNpcId, Boolean autoSpawnOnBoot, TemplateSpawnProfile spawnProfile, TemplateChannelProfile channelProfile, Boolean talkable, String fallbackProfile, String availabilityProfile, String fpcTag, String presentationMode, String carrierPolicy, String presentationCarrierTemplate, Integer presentationCarrierNpcId, String speciesTag, String familyTag, TemplateAdventurerProfile adventurerProfile)
		{
			_templateId = templateId;
			_title = title;
			_enabled = enabled;
			_category = category;
			_archetype = archetype;
			_personaTemplate = personaTemplate;
			_carrierTemplate = carrierTemplate;
			_carrierNpcId = carrierNpcId;
			_autoSpawnOnBoot = autoSpawnOnBoot;
			_spawnProfile = spawnProfile;
			_channelProfile = channelProfile;
			_talkable = talkable;
			_fallbackProfile = fallbackProfile;
			_availabilityProfile = availabilityProfile;
			_fpcTag = fpcTag;
			_presentationMode = presentationMode;
			_carrierPolicy = carrierPolicy;
			_presentationCarrierTemplate = presentationCarrierTemplate;
			_presentationCarrierNpcId = presentationCarrierNpcId;
			_speciesTag = speciesTag;
			_familyTag = familyTag;
			_adventurerProfile = adventurerProfile;
		}
	}
	
	private static final class TemplateSpawnProfile
	{
		private final String _mode;
		private final String _zone;
		private final Integer _x;
		private final Integer _y;
		private final Integer _z;
		private final Integer _heading;
		
		private TemplateSpawnProfile(String mode, String zone, Integer x, Integer y, Integer z, Integer heading)
		{
			_mode = mode;
			_zone = zone;
			_x = x;
			_y = y;
			_z = z;
			_heading = heading;
		}
	}
	
	private static final class TemplateChannelProfile
	{
		private final Boolean _whisper;
		private final Boolean _general;
		private final Boolean _shout;
		private final Boolean _world;
		
		private TemplateChannelProfile(Boolean whisper, Boolean general, Boolean shout, Boolean world)
		{
			_whisper = whisper;
			_general = general;
			_shout = shout;
			_world = world;
		}
	}

	private static final class TemplateAdventurerProfile
	{
		private final String _tier;
		private final String _carrierMode;
		private final String _inventoryMode;
		private final String _pickupPolicy;
		private final String _combatMode;
		private final String _partyMode;
		private final String _clanMode;
		private final String _matchmakingMode;
		private final String _progressionMode;
		private final String _stageBandId;
		private final Integer _fixedLevel;
		private final String _loadoutProfile;
		private final String _consumableProfile;
		private final String _routeId;

		private TemplateAdventurerProfile(String tier, String carrierMode, String inventoryMode, String pickupPolicy, String combatMode, String partyMode, String clanMode, String matchmakingMode, String progressionMode, String stageBandId, Integer fixedLevel, String loadoutProfile, String consumableProfile, String routeId)
		{
			_tier = tier;
			_carrierMode = carrierMode;
			_inventoryMode = inventoryMode;
			_pickupPolicy = pickupPolicy;
			_combatMode = combatMode;
			_partyMode = partyMode;
			_clanMode = clanMode;
			_matchmakingMode = matchmakingMode;
			_progressionMode = progressionMode;
			_stageBandId = stageBandId;
			_fixedLevel = fixedLevel;
			_loadoutProfile = loadoutProfile;
			_consumableProfile = consumableProfile;
			_routeId = routeId;
		}
	}

	private final FakePlayerConfig _config;
	private final FpcRegistry _registry;
	private volatile long _lastDefinitionsLoadAtMs;
	private volatile int _lastDefinitionsLoadCount;
	private volatile String _lastDefinitionsLoadStatus = "not_loaded";
	private volatile String _lastDefinitionsLoadMessage = "FPC definitions have not been loaded yet.";
	private volatile boolean _lastDefinitionsLoadPreserved;

	public FakePlayerProfileData(FakePlayerConfig config, FakePlayerWorldFacade worldFacade)
	{
		_config = config;
		_registry = new FpcRegistry(worldFacade);
	}

	public void load()
	{
		final long now = System.currentTimeMillis();
		try
		{
			final Map<String, FpcTemplateDefinition> templates = loadTemplates();
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getFpcDefinitionsPath());
			if ((raw == null) || raw.isBlank())
			{
				final int preservedCount = _registry.getDefinitions().size();
				final boolean preserved = preservedCount > 0;
				final String message = "No FPC definition catalog found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcDefinitionsPath()) + ".";
				LOGGER.info(() -> getClass().getSimpleName() + ": " + message + (preserved ? " Preserving previous registry size=" + preservedCount + "." : " Continuing with empty registry."));
				if (!preserved)
				{
					_registry.replaceDefinitions(List.of());
				}
				recordDefinitionsLoad(preserved ? "preserved_missing_file" : "missing_file", message, preserved ? preservedCount : 0, preserved, now);
				return;
			}

			final List<FpcDefinition> catalog = parseDefinitions(raw, templates);
			final List<FpcDefinition> definitions = catalog;
			_registry.replaceDefinitions(definitions);
			registerNames(definitions);
			recordDefinitionsLoad("loaded", "Loaded " + definitions.size() + " generic FPC definition(s) from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcDefinitionsPath()) + " using templates=" + templates.size() + ".", definitions.size(), false, now);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded " + definitions.size() + " generic FPC definition(s) from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcDefinitionsPath()) + " using templates=" + templates.size());
		}
		catch (Exception e)
		{
			final int preservedCount = _registry.getDefinitions().size();
			final boolean preserved = preservedCount > 0;
			final String message = "Failed to load FPC definitions from " + _config.getFpcDefinitionsPath() + " -> " + e.getMessage();
			recordDefinitionsLoad(preserved ? "preserved_previous" : "empty_after_failure", message, preserved ? preservedCount : 0, preserved, now);
			LOGGER.warning(getClass().getSimpleName() + ": " + message + (preserved ? " Preserving previous registry size=" + preservedCount + "." : " Registry remains empty."));
			if (!preserved)
			{
				_registry.replaceDefinitions(List.of());
			}
		}
	}

	private void recordDefinitionsLoad(String status, String message, int count, boolean preserved, long now)
	{
		_lastDefinitionsLoadAtMs = now;
		_lastDefinitionsLoadCount = Math.max(count, 0);
		_lastDefinitionsLoadStatus = ((status == null) || status.isBlank()) ? "unknown" : status;
		_lastDefinitionsLoadMessage = ((message == null) || message.isBlank()) ? "No FPC definition load message recorded." : message;
		_lastDefinitionsLoadPreserved = preserved;
	}

	private void registerNames(Collection<FpcDefinition> definitions)
	{
		for (FpcDefinition definition : definitions)
		{
			final String lowercaseName = definition.getName().toLowerCase();
			FakePlayerData.getInstance().addFakePlayerName(lowercaseName, definition.getName());

			final int carrierNpcId = resolveCarrierNpcId(definition);
			if (carrierNpcId > 0)
			{
				FakePlayerData.getInstance().addFakePlayerId(definition.getName(), carrierNpcId);
			}
			else
			{
				LOGGER.warning(getClass().getSimpleName() + ": Could not resolve carrier NPC id for FPC " + definition.getName() + " using carrierTemplate=" + definition.getCarrierTemplate() + " carrierPolicy=" + definition.getCarrierPolicy() + " presentationCarrierTemplate=" + definition.getPresentationCarrierTemplate());
			}

			if (definition.isTalkable())
			{
				FakePlayerData.getInstance().addTalkableFakePlayerName(lowercaseName);
			}
		}
	}

	private int resolveCarrierNpcId(FpcDefinition definition)
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
			npcId = safeNpcIdLookup(definition.getCarrierTemplate());
		}
		if (npcId <= 0)
		{
			npcId = safeNpcIdLookup(definition.getName());
		}
		if ((npcId <= 0) && (definition.getCarrierNpcId() > 0))
		{
			npcId = definition.getCarrierNpcId();
		}
		return npcId;
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

	private Map<String, FpcTemplateDefinition> loadTemplates() throws IOException
	{
		final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getFpcDefinitionTemplatesPath());
		if ((raw == null) || raw.isBlank())
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": No FPC template catalog found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcDefinitionTemplatesPath()) + ". Continuing without reusable FPC templates.");
			return Map.of();
		}
		
		final Map<String, FpcTemplateDefinition> templates = new LinkedHashMap<>();
		for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
		{
			final FpcTemplateDefinition template = parseTemplate(objectJson);
			final String key = FakePlayerJsonDataSupport.normalizeKey(template._templateId);
			if (templates.putIfAbsent(key, template) != null)
			{
				throw new IllegalArgumentException("Duplicate FPC template id: " + template._templateId);
			}
		}
		LOGGER.info(() -> getClass().getSimpleName() + ": Loaded " + templates.size() + " FPC template(s) from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getFpcDefinitionTemplatesPath()));
		return templates;
	}

	private List<FpcDefinition> parseDefinitions(String json, Map<String, FpcTemplateDefinition> templates) throws IOException
	{
		final List<FpcDefinition> result = new ArrayList<>();
		for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(json))
		{
			result.add(parseDefinition(objectJson, templates));
		}
		return result;
	}

	private FpcTemplateDefinition parseTemplate(String objectJson)
	{
		final String templateId = requiredString(objectJson, "templateId");
		final TemplateSpawnProfile spawnProfile = parseOptionalTemplateSpawnProfile(optionalObject(objectJson, "spawnProfile"));
		final TemplateChannelProfile channelProfile = parseOptionalTemplateChannelProfile(optionalObject(objectJson, "channels"));
		final TemplateAdventurerProfile adventurerProfile = parseOptionalTemplateAdventurerProfile(optionalObject(objectJson, "adventurerProfile"));
		return new FpcTemplateDefinition(
			templateId,
			optionalString(objectJson, "title", null),
			optionalBooleanObject(objectJson, "enabled"),
			optionalString(objectJson, "category", null),
			optionalString(objectJson, "archetype", null),
			optionalString(objectJson, "personaTemplate", null),
			optionalString(objectJson, "carrierTemplate", null),
			optionalInteger(objectJson, "carrierNpcId"),
			optionalBooleanObject(objectJson, "autoSpawnOnBoot"),
			spawnProfile,
			channelProfile,
			optionalBooleanObject(objectJson, "talkable"),
			optionalString(objectJson, "fallbackProfile", null),
			optionalString(objectJson, "availabilityProfile", null),
			optionalString(objectJson, "fpcTag", null),
			optionalString(objectJson, "presentationMode", null),
			optionalString(objectJson, "carrierPolicy", null),
			optionalString(objectJson, "presentationCarrierTemplate", null),
			optionalInteger(objectJson, "presentationCarrierNpcId"),
			optionalString(objectJson, "speciesTag", null),
			optionalString(objectJson, "familyTag", null),
			adventurerProfile);
	}
	
	private FpcDefinition parseDefinition(String objectJson, Map<String, FpcTemplateDefinition> templates)
	{
		final String templateId = optionalString(objectJson, "template", "");
		final FpcTemplateDefinition template = templateId.isBlank() ? null : templates.get(FakePlayerJsonDataSupport.normalizeKey(templateId));
		if (!templateId.isBlank() && (template == null))
		{
			throw new IllegalArgumentException("Unknown FPC template: " + templateId);
		}
		
		final String id = requiredString(objectJson, "id");
		final String name = requiredString(objectJson, "name");
		final String title = resolveString(objectJson, "title", template != null ? template._title : null, "");
		final boolean enabled = resolveBoolean(objectJson, "enabled", template != null ? template._enabled : null, true);
		final String category = resolveString(objectJson, "category", template != null ? template._category : null, "generic_fpc");
		final String archetype = resolveString(objectJson, "archetype", template != null ? template._archetype : null, "generic");
		final String personaTemplate = resolveString(objectJson, "personaTemplate", template != null ? template._personaTemplate : null, "default");
		final String carrierTemplate = resolveString(objectJson, "carrierTemplate", template != null ? template._carrierTemplate : null, "fpc_passive");
		final int carrierNpcId = resolveInt(objectJson, "carrierNpcId", template != null ? template._carrierNpcId : null, 0);
		final boolean autoSpawnOnBoot = resolveBoolean(objectJson, "autoSpawnOnBoot", template != null ? template._autoSpawnOnBoot : null, false);
		final String fallbackProfile = resolveString(objectJson, "fallbackProfile", template != null ? template._fallbackProfile : null, "default");
		final String availabilityProfile = resolveString(objectJson, "availabilityProfile", template != null ? template._availabilityProfile : null, "always_on");
		final boolean talkable = resolveBoolean(objectJson, "talkable", template != null ? template._talkable : null, true);
		final String fpcTag = resolveString(objectJson, "fpcTag", template != null ? template._fpcTag : null, "fpc");
		final String presentationMode = resolveString(objectJson, "presentationMode", template != null ? template._presentationMode : null, "player_like");
		final String carrierPolicy = resolveString(objectJson, "carrierPolicy", template != null ? template._carrierPolicy : null, "fixed");
		final String presentationCarrierTemplate = resolveString(objectJson, "presentationCarrierTemplate", template != null ? template._presentationCarrierTemplate : null, "");
		final int presentationCarrierNpcId = resolveInt(objectJson, "presentationCarrierNpcId", template != null ? template._presentationCarrierNpcId : null, 0);
		final String speciesTag = resolveString(objectJson, "speciesTag", template != null ? template._speciesTag : null, "human");
		final String familyTag = resolveString(objectJson, "familyTag", template != null ? template._familyTag : null, "independent");
		final String adventurerJson = optionalObject(objectJson, "adventurerProfile");
		final TemplateAdventurerProfile templateAdventurerProfile = template != null ? template._adventurerProfile : null;
		final String adventurerTier = resolveNestedString(adventurerJson, "tier", templateAdventurerProfile != null ? templateAdventurerProfile._tier : null, "social");
		final String carrierMode = resolveNestedString(adventurerJson, "carrierMode", templateAdventurerProfile != null ? templateAdventurerProfile._carrierMode : null, "npc_backed");
		final String inventoryMode = resolveNestedString(adventurerJson, "inventoryMode", templateAdventurerProfile != null ? templateAdventurerProfile._inventoryMode : null, "none");
		final String pickupPolicy = resolveNestedString(adventurerJson, "pickupPolicy", templateAdventurerProfile != null ? templateAdventurerProfile._pickupPolicy : null, "disabled");
		final String combatMode = resolveNestedString(adventurerJson, "combatMode", templateAdventurerProfile != null ? templateAdventurerProfile._combatMode : null, "none");
		final String partyMode = resolveNestedString(adventurerJson, "partyMode", templateAdventurerProfile != null ? templateAdventurerProfile._partyMode : null, "none");
		final String clanMode = resolveNestedString(adventurerJson, "clanMode", templateAdventurerProfile != null ? templateAdventurerProfile._clanMode : null, "none");
		final String matchmakingMode = resolveNestedString(adventurerJson, "matchmakingMode", templateAdventurerProfile != null ? templateAdventurerProfile._matchmakingMode : null, "none");
		final String progressionMode = resolveNestedString(adventurerJson, "progressionMode", templateAdventurerProfile != null ? templateAdventurerProfile._progressionMode : null, "fixed");
		final String stageBandId = resolveNestedString(adventurerJson, "stageBandId", templateAdventurerProfile != null ? templateAdventurerProfile._stageBandId : null, "");
		final int fixedLevel = resolveNestedInt(adventurerJson, "fixedLevel", templateAdventurerProfile != null ? templateAdventurerProfile._fixedLevel : null, 0);
		final String loadoutProfile = resolveNestedString(adventurerJson, "loadoutProfile", templateAdventurerProfile != null ? templateAdventurerProfile._loadoutProfile : null, "");
		final String consumableProfile = resolveNestedString(adventurerJson, "consumableProfile", templateAdventurerProfile != null ? templateAdventurerProfile._consumableProfile : null, "");
		final String routeId = resolveNestedString(adventurerJson, "routeId", templateAdventurerProfile != null ? templateAdventurerProfile._routeId : null, "");
		final String companionJson = optionalObject(objectJson, "companionProfile");
		final String relationGate = resolveNestedString(companionJson, "relationGate", null, "none");
		final String sessionMode = resolveNestedString(companionJson, "sessionMode", null, "default");
		final String idleBehavior = resolveNestedString(companionJson, "idleBehavior", null, "none");
		final long idleReturnHomeMs = Math.max(0L, resolveNestedLong(companionJson, "idleReturnHomeMs", null, 0L));
		final boolean allowInstanceFollow = resolveNestedBoolean(companionJson, "allowInstanceFollow", null, false);
		final String followupMode = resolveNestedString(companionJson, "followupMode", null, "none");
		final String clanJoinMode = resolveNestedString(companionJson, "clanJoinMode", null, "default");

		final String spawnProfileJson = optionalObject(objectJson, "spawnProfile");
		final TemplateSpawnProfile templateSpawnProfile = template != null ? template._spawnProfile : null;
		final String spawnMode = resolveNestedString(spawnProfileJson, "mode", templateSpawnProfile != null ? templateSpawnProfile._mode : null, "fixed");
		final String spawnZone = resolveNestedString(spawnProfileJson, "zone", templateSpawnProfile != null ? templateSpawnProfile._zone : null, "giran");
		final int spawnX = resolveRequiredNestedInt(spawnProfileJson, "x", templateSpawnProfile != null ? templateSpawnProfile._x : null);
		final int spawnY = resolveRequiredNestedInt(spawnProfileJson, "y", templateSpawnProfile != null ? templateSpawnProfile._y : null);
		final int spawnZ = resolveRequiredNestedInt(spawnProfileJson, "z", templateSpawnProfile != null ? templateSpawnProfile._z : null);
		final int spawnHeading = resolveNestedInt(spawnProfileJson, "heading", templateSpawnProfile != null ? templateSpawnProfile._heading : null, 0);

		final String channelsJson = optionalObject(objectJson, "channels");
		final TemplateChannelProfile templateChannelProfile = template != null ? template._channelProfile : null;
		final boolean whisper = resolveNestedBoolean(channelsJson, "whisper", templateChannelProfile != null ? templateChannelProfile._whisper : null, false);
		final boolean general = resolveNestedBoolean(channelsJson, "general", templateChannelProfile != null ? templateChannelProfile._general : null, false);
		final boolean shout = resolveNestedBoolean(channelsJson, "shout", templateChannelProfile != null ? templateChannelProfile._shout : null, false);
		final boolean world = resolveNestedBoolean(channelsJson, "world", templateChannelProfile != null ? templateChannelProfile._world : null, false);

		return new FpcDefinition(
			id,
			name,
			title,
			enabled,
			category,
			archetype,
			personaTemplate,
			carrierTemplate,
			carrierNpcId,
			autoSpawnOnBoot,
			new FpcSpawnProfile(spawnMode, spawnZone, spawnX, spawnY, spawnZ, spawnHeading),
			new FpcChannelProfile(whisper, general, shout, world),
			talkable,
			fallbackProfile,
			availabilityProfile,
			fpcTag,
			presentationMode,
			carrierPolicy,
			presentationCarrierTemplate,
			presentationCarrierNpcId,
			speciesTag,
			familyTag,
			new FpcAdventurerProfile(adventurerTier, carrierMode, inventoryMode, pickupPolicy, combatMode, partyMode, clanMode, matchmakingMode, progressionMode, stageBandId, fixedLevel, loadoutProfile, consumableProfile, routeId),
			new FpcCompanionProfile(relationGate, sessionMode, idleBehavior, idleReturnHomeMs, allowInstanceFollow, followupMode, clanJoinMode));
	}

	private TemplateSpawnProfile parseOptionalTemplateSpawnProfile(String spawnProfileJson)
	{
		if ((spawnProfileJson == null) || spawnProfileJson.isBlank())
		{
			return null;
		}
		return new TemplateSpawnProfile(
			optionalString(spawnProfileJson, "mode", null),
			optionalString(spawnProfileJson, "zone", null),
			optionalInteger(spawnProfileJson, "x"),
			optionalInteger(spawnProfileJson, "y"),
			optionalInteger(spawnProfileJson, "z"),
			optionalInteger(spawnProfileJson, "heading"));
	}

	private TemplateChannelProfile parseOptionalTemplateChannelProfile(String channelsJson)
	{
		if ((channelsJson == null) || channelsJson.isBlank())
		{
			return null;
		}
		return new TemplateChannelProfile(
			optionalBooleanObject(channelsJson, "whisper"),
			optionalBooleanObject(channelsJson, "general"),
			optionalBooleanObject(channelsJson, "shout"),
			optionalBooleanObject(channelsJson, "world"));
	}

	private TemplateAdventurerProfile parseOptionalTemplateAdventurerProfile(String adventurerJson)
	{
		if ((adventurerJson == null) || adventurerJson.isBlank())
		{
			return null;
		}
		return new TemplateAdventurerProfile(
			optionalString(adventurerJson, "tier", null),
			optionalString(adventurerJson, "carrierMode", null),
			optionalString(adventurerJson, "inventoryMode", null),
			optionalString(adventurerJson, "pickupPolicy", null),
			optionalString(adventurerJson, "combatMode", null),
			optionalString(adventurerJson, "partyMode", null),
			optionalString(adventurerJson, "clanMode", null),
			optionalString(adventurerJson, "matchmakingMode", null),
			optionalString(adventurerJson, "progressionMode", null),
			optionalString(adventurerJson, "stageBandId", null),
			optionalInteger(adventurerJson, "fixedLevel"),
			optionalString(adventurerJson, "loadoutProfile", null),
			optionalString(adventurerJson, "consumableProfile", null),
			optionalString(adventurerJson, "routeId", null));
	}

	private String optionalObject(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalObject(json, key);
	}

	private String requiredString(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredString(json, key);
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	private boolean optionalBoolean(String json, String key, boolean defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalBoolean(json, key, defaultValue);
	}

	private Boolean optionalBooleanObject(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalBooleanObject(json, key);
	}

	private int requiredInt(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredInt(json, key);
	}

	private int optionalInt(String json, String key, int defaultValue)
	{
		final Integer value = optionalInteger(json, key);
		return value != null ? value.intValue() : defaultValue;
	}

	private Integer optionalInteger(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalInteger(json, key);
	}

	private Long optionalLong(String json, String key)
	{
		return FakePlayerJsonDataSupport.optionalLongObject(json, key);
	}

	private String resolveString(String json, String key, String templateValue, String defaultValue)
	{
		final String directValue = optionalString(json, key, null);
		return (directValue != null) ? directValue : ((templateValue != null) ? templateValue : defaultValue);
	}

	private boolean resolveBoolean(String json, String key, Boolean templateValue, boolean defaultValue)
	{
		final Boolean directValue = optionalBooleanObject(json, key);
		return (directValue != null) ? directValue.booleanValue() : ((templateValue != null) ? templateValue.booleanValue() : defaultValue);
	}

	private int resolveInt(String json, String key, Integer templateValue, int defaultValue)
	{
		final Integer directValue = optionalInteger(json, key);
		return (directValue != null) ? directValue.intValue() : ((templateValue != null) ? templateValue.intValue() : defaultValue);
	}

	private String resolveNestedString(String nestedJson, String key, String templateValue, String defaultValue)
	{
		final String directValue = optionalString(nestedJson, key, null);
		return (directValue != null) ? directValue : ((templateValue != null) ? templateValue : defaultValue);
	}

	private boolean resolveNestedBoolean(String nestedJson, String key, Boolean templateValue, boolean defaultValue)
	{
		final Boolean directValue = optionalBooleanObject(nestedJson, key);
		return (directValue != null) ? directValue.booleanValue() : ((templateValue != null) ? templateValue.booleanValue() : defaultValue);
	}

	private int resolveNestedInt(String nestedJson, String key, Integer templateValue, int defaultValue)
	{
		final Integer directValue = optionalInteger(nestedJson, key);
		return (directValue != null) ? directValue.intValue() : ((templateValue != null) ? templateValue.intValue() : defaultValue);
	}

	private long resolveNestedLong(String nestedJson, String key, Long templateValue, long defaultValue)
	{
		final Long directValue = optionalLong(nestedJson, key);
		return (directValue != null) ? directValue.longValue() : ((templateValue != null) ? templateValue.longValue() : defaultValue);
	}

	private int resolveRequiredNestedInt(String nestedJson, String key, Integer templateValue)
	{
		final Integer directValue = optionalInteger(nestedJson, key);
		if (directValue != null)
		{
			return directValue.intValue();
		}
		if (templateValue != null)
		{
			return templateValue.intValue();
		}
		throw new IllegalArgumentException("Missing integer field: " + key);
	}

	public Collection<FpcDefinition> getDefinitions()
	{
		return _registry.getDefinitions();
	}

	public FpcDefinition getDefinitionById(String id)
	{
		return _registry.getDefinitionById(id);
	}

	public FpcDefinition getDefinitionByName(String name)
	{
		return _registry.getDefinitionByName(name);
	}

	public FpcRegistry getRegistry()
	{
		return _registry;
	}

	public long getLastDefinitionsLoadAtMs()
	{
		return _lastDefinitionsLoadAtMs;
	}

	public int getLastDefinitionsLoadCount()
	{
		return _lastDefinitionsLoadCount;
	}

	public String getLastDefinitionsLoadStatus()
	{
		return _lastDefinitionsLoadStatus;
	}

	public String getLastDefinitionsLoadMessage()
	{
		return _lastDefinitionsLoadMessage;
	}

	public boolean isLastDefinitionsLoadPreserved()
	{
		return _lastDefinitionsLoadPreserved;
	}
}
