package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcConversationGuardBank;

public class FakePlayerConversationGuardData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerConversationGuardData.class.getName());

	private final FakePlayerConfig _config;
	private final AtomicReference<FpcConversationGuardBank> _bank = new AtomicReference<>(FpcConversationGuardBank.defaults());

	public FakePlayerConversationGuardData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		try
		{
			final String json = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getConversationGuardPatternsPath());
			if ((json == null) || json.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No conversation guard bank found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getConversationGuardPatternsPath()) + ". Using defaults.");
				_bank.set(FpcConversationGuardBank.defaults());
				return;
			}

			final FpcConversationGuardBank defaults = FpcConversationGuardBank.defaults();
			final FpcConversationGuardBank loaded = new FpcConversationGuardBank(
				readStringArray(json, "metaMarkers", defaults.getMetaMarkers()),
				readStringArray(json, "metaRequestPhrases", defaults.getMetaRequestPhrases()),
				readStringArray(json, "outOfWorldMarkers", defaults.getOutOfWorldMarkers()),
				readStringArray(json, "questionStarters", defaults.getQuestionStarters()),
				readStringArray(json, "sensitiveFollowUpPhrases", defaults.getSensitiveFollowUpPhrases()),
				readStringArray(json, "teleportRequestPhrases", defaults.getTeleportRequestPhrases()),
				readStringArray(json, "giveRequestPhrases", defaults.getGiveRequestPhrases()),
				readStringArray(json, "rewardRequestPhrases", defaults.getRewardRequestPhrases()),
				readStringArray(json, "spawnRequestPhrases", defaults.getSpawnRequestPhrases()),
				readStringArray(json, "clanRequestPhrases", defaults.getClanRequestPhrases()),
				readStringArray(json, "unsupportedPromiseVerbs", defaults.getUnsupportedPromiseVerbs()),
				readStringArray(json, "unsupportedPromiseDirectObjects", defaults.getUnsupportedPromiseDirectObjects()));
			_bank.set(loaded);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded conversation guard bank from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getConversationGuardPatternsPath()) + " metaMarkers=" + loaded.getMetaMarkers().size() + " metaRequestPhrases=" + loaded.getMetaRequestPhrases().size() + " teleportRequestPhrases=" + loaded.getTeleportRequestPhrases().size());
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading conversation guard bank from " + _config.getConversationGuardPatternsPath() + " -> " + e.getMessage());
			_bank.set(FpcConversationGuardBank.defaults());
		}
	}

	public FpcConversationGuardBank getBank()
	{
		return _bank.get();
	}

	private Set<String> readStringArray(String json, String key, Set<String> defaultValue)
	{
		final Set<String> values = new LinkedHashSet<>(FakePlayerJsonDataSupport.parseStringArray(FakePlayerJsonDataSupport.optionalArray(json, key), false, false));
		if (values.isEmpty())
		{
			return defaultValue;
		}
		return Set.copyOf(values);
	}
}
