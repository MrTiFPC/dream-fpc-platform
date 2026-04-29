package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum FpcDebugCategory
{
	LIFECYCLE,
	MOVEMENT,
	COMBAT,
	SOCIAL,
	PLANNER,
	CHAT,
	QUEST;

	public static EnumSet<FpcDebugCategory> allCategories()
	{
		return EnumSet.allOf(FpcDebugCategory.class);
	}

	public static EnumSet<FpcDebugCategory> parseCategories(String rawCategories)
	{
		if ((rawCategories == null) || rawCategories.isBlank() || "all".equalsIgnoreCase(rawCategories.trim()))
		{
			return allCategories();
		}

		final EnumSet<FpcDebugCategory> categories = EnumSet.noneOf(FpcDebugCategory.class);
		for (String token : rawCategories.split("[,;\\s]+"))
		{
			if ((token == null) || token.isBlank())
			{
				continue;
			}

			final String normalized = token.trim().toUpperCase(Locale.ENGLISH).replace('-', '_');
			try
			{
				categories.add(FpcDebugCategory.valueOf(normalized));
			}
			catch (IllegalArgumentException e)
			{
				throw new IllegalArgumentException("Unknown debug category: " + token);
			}
		}
		return categories.isEmpty() ? allCategories() : categories;
	}

	public static String describe(Set<FpcDebugCategory> categories)
	{
		if ((categories == null) || categories.isEmpty())
		{
			return "off";
		}
		return categories.stream().sorted().map(category -> category.name().toLowerCase(Locale.ENGLISH)).collect(Collectors.joining(","));
	}
}
