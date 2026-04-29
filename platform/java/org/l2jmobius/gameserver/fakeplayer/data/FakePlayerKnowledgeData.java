package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcKnowledgeCard;

public class FakePlayerKnowledgeData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerKnowledgeData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, List<FpcKnowledgeCard>> _entriesByFpcId = new ConcurrentHashMap<>();

	public FakePlayerKnowledgeData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_entriesByFpcId.clear();

		try
		{
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getKnowledgeCardsPath());
			if ((raw == null) || raw.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No knowledge card file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getKnowledgeCardsPath()) + ". Continuing.");
				return;
			}

			for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
			{
				final FpcKnowledgeCard entry = new FpcKnowledgeCard(
					requiredString(objectJson, "fpcId"),
					requiredString(objectJson, "topicId"),
					optionalString(objectJson, "domain", "general"),
					optionalString(objectJson, "questionType", "unknown"),
					optionalString(objectJson, "channel", "any"),
					optionalString(objectJson, "audience", "any"),
					optionalString(objectJson, "triggers", ""),
					optionalInt(objectJson, "priority", 0),
					requiredString(objectJson, "summary"),
					optionalString(objectJson, "facts", ""),
					requiredString(objectJson, "generalAnswer"),
					optionalString(objectJson, "whisperAnswer", ""),
					optionalString(objectJson, "shortAdvice", ""),
					optionalString(objectJson, "confidenceHint", "confident"));
				_entriesByFpcId.computeIfAbsent(FakePlayerJsonDataSupport.normalizeKey(entry.getFpcId(), "default"), key -> new ArrayList<>()).add(entry);
			}

			for (List<FpcKnowledgeCard> entries : _entriesByFpcId.values())
			{
				entries.sort(Comparator.comparingInt(FpcKnowledgeCard::getPriority).reversed());
			}

			final int totalEntries = _entriesByFpcId.values().stream().mapToInt(List::size).sum();
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded knowledge cards=" + totalEntries + " groups=" + _entriesByFpcId.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getKnowledgeCardsPath()));
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading knowledge cards from " + _config.getKnowledgeCardsPath() + " -> " + e.getMessage());
			_entriesByFpcId.clear();
		}
	}

	public List<FpcKnowledgeCard> getEntriesForFpc(String fpcId)
	{
		final List<FpcKnowledgeCard> entries = _entriesByFpcId.get(FakePlayerJsonDataSupport.normalizeKey(fpcId, "default"));
		return (entries != null) ? Collections.unmodifiableList(entries) : List.of();
	}

	public List<FpcKnowledgeCard> getDefaultEntries()
	{
		final List<FpcKnowledgeCard> entries = _entriesByFpcId.get("default");
		return (entries != null) ? Collections.unmodifiableList(entries) : List.of();
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
}
