package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplyBankEntry;

public class FakePlayerReplyBankData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerReplyBankData.class.getName());

	private final FakePlayerConfig _config;
	private final Map<String, List<FpcReplyBankEntry>> _entriesByFpcId = new ConcurrentHashMap<>();

	public FakePlayerReplyBankData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_entriesByFpcId.clear();

		try
		{
			final String raw = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getReplyBanksPath());
			if ((raw == null) || raw.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No reply bank file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getReplyBanksPath()) + ". Continuing.");
				return;
			}

			for (String objectJson : FakePlayerJsonDataSupport.splitTopLevelObjects(raw))
			{
				final FpcReplyBankEntry entry = new FpcReplyBankEntry(
					requiredString(objectJson, "fpcId"),
					optionalString(objectJson, "bankType", "social"),
					optionalString(objectJson, "category", "unknown"),
					optionalString(objectJson, "channel", "any"),
					optionalString(objectJson, "audience", "any"),
					optionalString(objectJson, "triggers", ""),
					optionalInt(objectJson, "priority", 0),
					requiredString(objectJson, "line"));
				_entriesByFpcId.computeIfAbsent(FakePlayerJsonDataSupport.normalizeKey(entry.getFpcId(), "default"), key -> new ArrayList<>()).add(entry);
			}

			for (List<FpcReplyBankEntry> entries : _entriesByFpcId.values())
			{
				entries.sort(Comparator.comparingInt(FpcReplyBankEntry::getPriority).reversed());
			}

			final int totalEntries = _entriesByFpcId.values().stream().mapToInt(List::size).sum();
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded reply bank entries=" + totalEntries + " groups=" + _entriesByFpcId.size() + " from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getReplyBanksPath()));
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading reply banks from " + _config.getReplyBanksPath() + " -> " + e.getMessage());
			_entriesByFpcId.clear();
		}
	}

	public List<FpcReplyBankEntry> getEntriesForFpc(String fpcId)
	{
		final List<FpcReplyBankEntry> entries = _entriesByFpcId.get(FakePlayerJsonDataSupport.normalizeKey(fpcId, "default"));
		return (entries != null) ? Collections.unmodifiableList(entries) : List.of();
	}

	public List<FpcReplyBankEntry> getDefaultEntries()
	{
		final List<FpcReplyBankEntry> entries = _entriesByFpcId.get("default");
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
