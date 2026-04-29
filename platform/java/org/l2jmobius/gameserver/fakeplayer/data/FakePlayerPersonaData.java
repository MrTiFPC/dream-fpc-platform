package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaProfile;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaRelationship;
import org.l2jmobius.gameserver.fakeplayer.model.FpcPersonaTemplate;
import org.l2jmobius.gameserver.fakeplayer.model.FpcStoryArc;

/**
 * Structured lore loader for scalable FPC personas.
 * The files are project-owned flat JSON arrays so a narrow parser keeps the dependency surface small.
 */
public class FakePlayerPersonaData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerPersonaData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, FpcPersonaTemplate> _templatesById = new ConcurrentHashMap<>();
	private final Map<String, FpcPersonaProfile> _profilesByFpcId = new ConcurrentHashMap<>();
	private final Map<String, List<FpcPersonaRelationship>> _relationshipsBySourceId = new ConcurrentHashMap<>();
	private final Map<String, FpcStoryArc> _storyArcsByFpcId = new ConcurrentHashMap<>();

	public FakePlayerPersonaData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_templatesById.clear();
		_profilesByFpcId.clear();
		_relationshipsBySourceId.clear();
		_storyArcsByFpcId.clear();

		loadTemplates();
		loadProfiles();
		loadRelationships();
		loadStoryArcs();

		LOGGER.info(() -> getClass().getSimpleName() + ": Loaded persona templates=" + _templatesById.size() + ", profiles=" + _profilesByFpcId.size() + ", relationship groups=" + _relationshipsBySourceId.size() + ", story arcs=" + _storyArcsByFpcId.size());
	}

	private void loadTemplates()
	{
		try
		{
			for (String objectJson : readObjects(_config.getPersonaTemplatesPath()))
			{
				final FpcPersonaTemplate template = new FpcPersonaTemplate(requiredString(objectJson, "id"), optionalString(objectJson, "baseSummary", ""), optionalString(objectJson, "responseStyle", ""), optionalString(objectJson, "playerStance", ""), optionalString(objectJson, "coreNeed", ""));
				_templatesById.put(FakePlayerJsonDataSupport.normalizeKey(template.getId()), template);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading persona templates from " + _config.getPersonaTemplatesPath() + " -> " + e.getMessage());
		}
	}

	private void loadProfiles()
	{
		try
		{
			for (String objectJson : readObjects(_config.getPersonaProfilesPath()))
			{
				final FpcPersonaProfile profile = new FpcPersonaProfile(requiredString(objectJson, "fpcId"), optionalString(objectJson, "originSummary", ""), optionalString(objectJson, "selfKnowledgeSummary", ""), optionalString(objectJson, "defaultInnerState", ""), optionalString(objectJson, "longTermGoal", ""), optionalString(objectJson, "socialTestStyle", ""), optionalString(objectJson, "trustCriteria", ""), optionalString(objectJson, "repairStyle", ""), optionalString(objectJson, "revealBoundary", ""), optionalString(objectJson, "reflectionLens", ""), optionalString(objectJson, "conversationGoal", ""), optionalString(objectJson, "hiddenFear", ""), optionalString(objectJson, "creatorSummary", ""), optionalString(objectJson, "publicMaskSummary", ""), optionalString(objectJson, "hiddenTruthSummary", ""), optionalString(objectJson, "selfConcept", ""), optionalString(objectJson, "coreWound", ""), optionalString(objectJson, "coreDesire", ""), optionalString(objectJson, "loyaltyAnchor", ""), optionalString(objectJson, "resentmentAnchor", ""), optionalString(objectJson, "privateTaboo", ""), optionalString(objectJson, "speechAnchor", ""), optionalString(objectJson, "privateContradiction", ""), optionalString(objectJson, "guidancePosture", ""), optionalString(objectJson, "guidancePriority", ""), optionalString(objectJson, "uncertaintyStyle", ""), optionalString(objectJson, "recommendationStyle", ""), optionalString(objectJson, "riskStyle", ""), optionalString(objectJson, "teachingStyle", ""));
				_profilesByFpcId.put(FakePlayerJsonDataSupport.normalizeKey(profile.getFpcId()), profile);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading persona profiles from " + _config.getPersonaProfilesPath() + " -> " + e.getMessage());
		}
	}

	private void loadRelationships()
	{
		try
		{
			for (String objectJson : readObjects(_config.getPersonaRelationshipsPath()))
			{
				final FpcPersonaRelationship relationship = new FpcPersonaRelationship(requiredString(objectJson, "sourceId"), requiredString(objectJson, "targetId"), optionalString(objectJson, "bondType", ""), optionalString(objectJson, "stance", ""), optionalInt(objectJson, "intensity", 0), optionalString(objectJson, "summary", ""));
				_relationshipsBySourceId.computeIfAbsent(FakePlayerJsonDataSupport.normalizeKey(relationship.getSourceId()), key -> new ArrayList<>()).add(relationship);
			}
			for (List<FpcPersonaRelationship> relationships : _relationshipsBySourceId.values())
			{
				relationships.sort(Comparator.comparingInt(FpcPersonaRelationship::getIntensity).reversed());
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading persona relationships from " + _config.getPersonaRelationshipsPath() + " -> " + e.getMessage());
		}
	}

	private void loadStoryArcs()
	{
		try
		{
			for (String objectJson : readObjects(_config.getStoryArcsPath()))
			{
				final FpcStoryArc storyArc = new FpcStoryArc(requiredString(objectJson, "fpcId"), optionalString(objectJson, "arcId", ""), optionalString(objectJson, "arcRole", ""), optionalString(objectJson, "currentFocus", ""), optionalString(objectJson, "currentConflict", ""), optionalString(objectJson, "chapterSummary", ""), optionalString(objectJson, "activeObjective", ""), optionalString(objectJson, "openLoops", ""), optionalString(objectJson, "revealPressure", ""), optionalString(objectJson, "relationshipPressure", ""), optionalString(objectJson, "nextBeatHint", ""));
				_storyArcsByFpcId.put(FakePlayerJsonDataSupport.normalizeKey(storyArc.getFpcId()), storyArc);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading story arcs from " + _config.getStoryArcsPath() + " -> " + e.getMessage());
		}
	}

	private List<String> readObjects(String relativePath) throws Exception
	{
		final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, relativePath);
		if ((raw == null) || raw.isBlank())
		{
			LOGGER.info(() -> getClass().getSimpleName() + ": No persona data file found at " + FakePlayerDataAssetResolver.describeSource(_config, relativePath) + ". Continuing.");
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

	private int optionalInt(String json, String key, int defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalInt(json, key, defaultValue);
	}

	public FpcPersonaTemplate getTemplateById(String id)
	{
		return _templatesById.get(FakePlayerJsonDataSupport.normalizeKey(id));
	}

	public FpcPersonaProfile getProfileByFpcId(String fpcId)
	{
		return _profilesByFpcId.get(FakePlayerJsonDataSupport.normalizeKey(fpcId));
	}

	public List<FpcPersonaRelationship> getRelationshipsBySourceId(String sourceId)
	{
		final List<FpcPersonaRelationship> relationships = _relationshipsBySourceId.get(FakePlayerJsonDataSupport.normalizeKey(sourceId));
		return relationships != null ? Collections.unmodifiableList(relationships) : List.of();
	}

	public FpcStoryArc getStoryArcByFpcId(String fpcId)
	{
		return _storyArcsByFpcId.get(FakePlayerJsonDataSupport.normalizeKey(fpcId));
	}

	public Collection<FpcPersonaTemplate> getTemplates()
	{
		return Collections.unmodifiableCollection(_templatesById.values());
	}
}
