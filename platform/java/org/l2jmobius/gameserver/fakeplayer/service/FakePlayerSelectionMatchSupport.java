package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Locale;

final class FakePlayerSelectionMatchSupport
{
	private final FakePlayerMessageClassifier _classifier;

	FakePlayerSelectionMatchSupport(FakePlayerMessageClassifier classifier)
	{
		_classifier = classifier;
	}

	String normalize(String value)
	{
		return ((value == null) || value.isBlank()) ? "" : value.toLowerCase(Locale.ROOT).trim();
	}

	String normalizeText(String incomingText)
	{
		return _classifier.normalize(incomingText);
	}

	boolean containsPhrase(String normalizedText, String phrase)
	{
		return _classifier.containsPhrase(normalizedText, phrase);
	}

	boolean matchesChannel(String entryChannel, String normalizedChannel)
	{
		final String channel = normalize(entryChannel);
		return channel.isBlank() || "any".equals(channel) || channel.equals(normalizedChannel);
	}

	boolean matchesAudience(String entryAudience, String normalizedAudience)
	{
		final String audience = normalize(entryAudience);
		if (audience.isBlank() || "any".equals(audience))
		{
			return true;
		}
		if ("adventurers".equals(audience))
		{
			return !normalizedAudience.isBlank();
		}
		return audience.equals(normalizedAudience);
	}

	boolean matchesPipeTriggers(String triggerText, String normalizedText)
	{
		final String triggers = normalize(triggerText);
		if (triggers.isBlank())
		{
			return false;
		}

		for (String rawPart : triggers.split("\\|"))
		{
			final String phrase = normalize(rawPart);
			if (!phrase.isBlank() && containsPhrase(normalizedText, phrase))
			{
				return true;
			}
		}
		return false;
	}

	boolean hasPipeValues(String value)
	{
		return !normalize(value).isBlank();
	}

	int audienceScore(String entryAudience, String normalizedAudience)
	{
		final String audience = normalize(entryAudience);
		if (audience.isBlank() || "any".equals(audience))
		{
			return 0;
		}
		if ("adventurers".equals(audience))
		{
			return normalizedAudience.isBlank() ? 0 : 80;
		}
		return audience.equals(normalizedAudience) ? 250 : -1000;
	}

	int channelScore(String entryChannel, String normalizedChannel)
	{
		final String channel = normalize(entryChannel);
		if (channel.isBlank() || "any".equals(channel))
		{
			return 0;
		}
		return channel.equals(normalizedChannel) ? 60 : -500;
	}
}
