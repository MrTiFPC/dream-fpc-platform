package org.l2jmobius.gameserver.fakeplayer.service;

public final class FakePlayerSharedLocationSupport
{
	public static final String SHARE_LOCATION = "share_location";
	public static final String SHARE_LOCATION_BLOCKED = "share_location_blocked";
	public static final String SHARED_LOCATION_FOLLOW = "shared_location_follow";
	public static final String SHARED_LOCATION_FOLLOW_BLOCKED = "shared_location_follow_blocked";

	private FakePlayerSharedLocationSupport()
	{
		// utility class
	}

	public static boolean asksToShareLocation(String normalized)
	{
		return containsAny(normalized, "share loc", "share your loc", "share your location", "share location", "send your loc", "send your location", "drop your pin", "pin your loc", "pin your location", "send loc");
	}

	public static boolean asksToUseSharedLocation(String normalized)
	{
		return containsAny(normalized, "come here", "come to me", "on me", "take my loc", "take the pin", "use my loc", "use the pin", "teleport here", "tp here", "farm with me", "join me here", "come farm");
	}

	public static boolean isShareLocationCategory(String topicTag)
	{
		return SHARE_LOCATION.equalsIgnoreCase(topicTag);
	}

	public static boolean isSharedLocationFollowCategory(String topicTag)
	{
		return SHARED_LOCATION_FOLLOW.equalsIgnoreCase(topicTag);
	}

	public static String chooseFallbackLine(String category)
	{
		if (SHARE_LOCATION.equalsIgnoreCase(category))
		{
			return "I'll pin where I am now. Use it cleanly.";
		}
		if (SHARE_LOCATION_BLOCKED.equalsIgnoreCase(category))
		{
			return "Not yet. I only hand over a live pin to someone I trust.";
		}
		if (SHARED_LOCATION_FOLLOW.equalsIgnoreCase(category))
		{
			return "I see your pin. I'll take it.";
		}
		if (SHARED_LOCATION_FOLLOW_BLOCKED.equalsIgnoreCase(category))
		{
			return "I can't take a shared pin from here right now.";
		}
		return "";
	}

	private static boolean containsAny(String normalized, String... phrases)
	{
		if ((normalized == null) || normalized.isBlank() || (phrases == null))
		{
			return false;
		}
		final String source = normalized.toLowerCase();
		for (String phrase : phrases)
		{
			if ((phrase != null) && !phrase.isBlank() && source.contains(phrase.toLowerCase()))
			{
				return true;
			}
		}
		return false;
	}
}
