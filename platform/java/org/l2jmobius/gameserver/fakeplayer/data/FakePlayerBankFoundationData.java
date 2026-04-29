package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcBehaviorFamily;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgePack;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStateProfile;

public class FakePlayerBankFoundationData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerBankFoundationData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, FpcBehaviorFamily> _behaviorFamiliesById = new ConcurrentHashMap<>();
	private final Map<String, FpcStateProfile> _stateProfilesById = new ConcurrentHashMap<>();
	private final Map<String, FpcKnowledgePack> _knowledgePacksById = new ConcurrentHashMap<>();

	public FakePlayerBankFoundationData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_behaviorFamiliesById.clear();
		_stateProfilesById.clear();
		_knowledgePacksById.clear();

		loadBehaviorFamilies();
		loadStateProfiles();
		loadKnowledgePacks();

		LOGGER.info(() -> getClass().getSimpleName() + ": Loaded behavior families=" + _behaviorFamiliesById.size() + ", state profiles=" + _stateProfilesById.size() + ", knowledge packs=" + _knowledgePacksById.size());
	}

	private void loadBehaviorFamilies()
	{
		try
		{
			for (String objectJson : readObjects(_config.getBehaviorFamiliesPath()))
			{
				final FpcBehaviorFamily family = new FpcBehaviorFamily(requiredString(objectJson, "id"), optionalString(objectJson, "linkedPersonaTemplates", ""), optionalString(objectJson, "linkedArchetypes", ""), requiredString(objectJson, "summary"), optionalString(objectJson, "motivationWeights", ""), optionalString(objectJson, "temperamentProfile", ""), optionalString(objectJson, "socialEtiquette", ""), optionalString(objectJson, "playstyleFlags", ""), optionalString(objectJson, "tacticalPatterns", ""), optionalString(objectJson, "speechProfile", ""), optionalString(objectJson, "memoryStyle", ""), optionalString(objectJson, "notes", ""), optionalString(objectJson, "callbackStyle", ""), optionalString(objectJson, "conflictStyle", ""), optionalString(objectJson, "repairCadence", ""));
				_behaviorFamiliesById.put(FakePlayerJsonDataSupport.normalizeKey(family.getId()), family);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading behavior families from " + _config.getBehaviorFamiliesPath() + " -> " + e.getMessage());
		}
	}

	private void loadStateProfiles()
	{
		try
		{
			for (String objectJson : readObjects(_config.getAfpcStateProfilesPath()))
			{
				final FpcStateProfile profile = new FpcStateProfile(requiredString(objectJson, "id"), optionalString(objectJson, "linkedFpcIds", ""), optionalString(objectJson, "linkedFamilies", ""), requiredString(objectJson, "summary"), optionalString(objectJson, "personalitySliders", ""), optionalString(objectJson, "activeNeeds", ""), optionalString(objectJson, "goalSlots", ""), optionalString(objectJson, "memoryWindows", ""), optionalString(objectJson, "relationshipAxes", ""), optionalString(objectJson, "valueRules", ""), optionalString(objectJson, "emotionalAxes", ""), optionalString(objectJson, "notes", ""), optionalString(objectJson, "stateModes", ""), optionalString(objectJson, "reflectionPolicy", ""), optionalString(objectJson, "memoryRetrievalPolicy", ""), optionalString(objectJson, "goalPersistencePolicy", ""), optionalString(objectJson, "emotionTransitionRules", ""));
				_stateProfilesById.put(FakePlayerJsonDataSupport.normalizeKey(profile.getId()), profile);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading AFPC state profiles from " + _config.getAfpcStateProfilesPath() + " -> " + e.getMessage());
		}
	}

	private void loadKnowledgePacks()
	{
		try
		{
			for (String objectJson : readObjects(_config.getKnowledgePacksPath()))
			{
				final FpcKnowledgePack pack = new FpcKnowledgePack(requiredString(objectJson, "id"), optionalString(objectJson, "packType", "official"), optionalString(objectJson, "domain", "general"), optionalString(objectJson, "sourceTier", "canonical_official"), optionalString(objectJson, "stability", "mostly_stable"), optionalString(objectJson, "lastChecked", ""), optionalString(objectJson, "linkedFamilies", ""), requiredString(objectJson, "summary"), optionalString(objectJson, "facts", ""), optionalString(objectJson, "heuristics", ""), optionalString(objectJson, "scope", ""), optionalString(objectJson, "sourceRefs", ""));
				_knowledgePacksById.put(FakePlayerJsonDataSupport.normalizeKey(pack.getId()), pack);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading knowledge packs from " + _config.getKnowledgePacksPath() + " -> " + e.getMessage());
		}
	}

	private List<String> readObjects(String relativePath) throws Exception
	{
		final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, relativePath);
		if ((raw == null) || raw.isBlank())
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": No foundation data file found at " + FakePlayerDataAssetResolver.describeSource(_config, relativePath) + ". Continuing.");
			return List.of();
		}
		return FakePlayerJsonDataSupport.splitTopLevelObjects(raw);
	}

	private String requiredString(String json, String key)
	{
		return FakePlayerJsonDataSupport.requiredString(json, key);
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	public FpcBehaviorFamily getBehaviorFamily(String id)
	{
		return _behaviorFamiliesById.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public FpcStateProfile getStateProfile(String id)
	{
		return _stateProfilesById.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public FpcKnowledgePack getKnowledgePack(String id)
	{
		return _knowledgePacksById.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public Collection<FpcBehaviorFamily> getBehaviorFamilies()
	{
		return Collections.unmodifiableCollection(_behaviorFamiliesById.values());
	}

	public Collection<FpcStateProfile> getStateProfiles()
	{
		return Collections.unmodifiableCollection(_stateProfilesById.values());
	}

	public Collection<FpcKnowledgePack> getKnowledgePacks()
	{
		return Collections.unmodifiableCollection(_knowledgePacksById.values());
	}
}
