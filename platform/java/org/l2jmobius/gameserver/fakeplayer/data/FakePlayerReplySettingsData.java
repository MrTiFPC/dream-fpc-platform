package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplySettings;

public class FakePlayerReplySettingsData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerReplySettingsData.class.getName());

	private final FakePlayerConfig _config;
	private volatile FpcReplySettings _settings = FpcReplySettings.DEFAULT;

	public FakePlayerReplySettingsData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		_settings = FpcReplySettings.DEFAULT;
		try
		{
			final String json = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getReplySettingsPath());
			if ((json == null) || json.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No reply settings file found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getReplySettingsPath()) + ". Using defaults.");
				return;
			}
			_settings = new FpcReplySettings(
				optionalInt(json, "recentConversationTurns", FpcReplySettings.DEFAULT.getRecentConversationTurns()),
				optionalLong(json, "recentConversationTtlMs", FpcReplySettings.DEFAULT.getRecentConversationTtlMs()),
				optionalString(json, "followUpStrictness", FpcReplySettings.DEFAULT.getFollowUpStrictness()),
				optionalInt(json, "generalMaxWords", FpcReplySettings.DEFAULT.getGeneralMaxWords()),
				optionalInt(json, "whisperMaxWords", FpcReplySettings.DEFAULT.getWhisperMaxWords()),
				optionalInt(json, "publicMaxWords", FpcReplySettings.DEFAULT.getPublicMaxWords()),
				optionalBoolean(json, "whisperAllowSecondSentence", FpcReplySettings.DEFAULT.isWhisperAllowSecondSentence()),
				optionalString(json, "knowledgeConfidencePolicy", FpcReplySettings.DEFAULT.getKnowledgeConfidencePolicy()),
				optionalString(json, "progressionCoachingLevel", FpcReplySettings.DEFAULT.getProgressionCoachingLevel()),
				optionalString(json, "pvpConflictPolicy", FpcReplySettings.DEFAULT.getPvpConflictPolicy()),
				optionalString(json, "relationshipWarmth", FpcReplySettings.DEFAULT.getRelationshipWarmth()),
				optionalString(json, "offscopeClassKnowledge", FpcReplySettings.DEFAULT.getOffscopeClassKnowledge()),
				optionalString(json, "farmAdviceMode", FpcReplySettings.DEFAULT.getFarmAdviceMode()),
				optionalString(json, "travelStyle", FpcReplySettings.DEFAULT.getTravelStyle()),
				optionalString(json, "socialReplyMode", FpcReplySettings.DEFAULT.getSocialReplyMode()));

			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded reply settings from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getReplySettingsPath()) + " turns=" + _settings.getRecentConversationTurns() + " followUpStrictness=" + _settings.getFollowUpStrictness() + " generalMaxWords=" + _settings.getGeneralMaxWords() + " whisperMaxWords=" + _settings.getWhisperMaxWords());
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading reply settings from " + _config.getReplySettingsPath() + " -> " + e.getMessage());
			_settings = FpcReplySettings.DEFAULT;
		}
	}

	public FpcReplySettings getSettings()
	{
		return _settings;
	}

	private String optionalString(String json, String key, String defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalString(json, key, defaultValue);
	}

	private int optionalInt(String json, String key, int defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalInt(json, key, defaultValue);
	}

	private long optionalLong(String json, String key, long defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalLong(json, key, defaultValue);
	}

	private boolean optionalBoolean(String json, String key, boolean defaultValue)
	{
		return FakePlayerJsonDataSupport.optionalBoolean(json, key, defaultValue);
	}
}
