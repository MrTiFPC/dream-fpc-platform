package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FakePlayerJsonDataSupport
{
	private FakePlayerJsonDataSupport()
	{
	}

	static List<String> splitTopLevelObjects(String json)
	{
		if ((json == null) || json.isBlank())
		{
			return List.of();
		}

		final List<String> result = new ArrayList<>();
		boolean inString = false;
		boolean escaped = false;
		int depth = 0;
		int objectStart = -1;

		for (int i = 0; i < json.length(); i++)
		{
			final char c = json.charAt(i);
			if (escaped)
			{
				escaped = false;
				continue;
			}
			if (c == '\\')
			{
				escaped = true;
				continue;
			}
			if (c == '"')
			{
				inString = !inString;
				continue;
			}
			if (inString)
			{
				continue;
			}
			if (c == '{')
			{
				if (depth == 0)
				{
					objectStart = i;
				}
				depth++;
			}
			else if (c == '}')
			{
				depth--;
				if ((depth == 0) && (objectStart >= 0))
				{
					result.add(json.substring(objectStart, i + 1));
					objectStart = -1;
				}
			}
		}
		return result;
	}

	static String requiredString(String json, String key)
	{
		final String value = optionalString(json, key, null);
		if ((value == null) || value.isBlank())
		{
			throw new IllegalArgumentException("Missing string field: " + key);
		}
		return value;
	}

	static String optionalString(String json, String key, String defaultValue)
	{
		if ((json == null) || json.isBlank())
		{
			return defaultValue;
		}

		final Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
		final Matcher matcher = pattern.matcher(json);
		if (!matcher.find())
		{
			return defaultValue;
		}
		return unescapeJson(matcher.group(1));
	}

	static boolean optionalBoolean(String json, String key, boolean defaultValue)
	{
		final Boolean value = optionalBooleanObject(json, key);
		return value != null ? value.booleanValue() : defaultValue;
	}

	static Boolean optionalBooleanObject(String json, String key)
	{
		if ((json == null) || json.isBlank())
		{
			return null;
		}

		final Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
		final Matcher matcher = pattern.matcher(json);
		if (!matcher.find())
		{
			return null;
		}
		return Boolean.valueOf(matcher.group(1));
	}

	static int requiredInt(String json, String key)
	{
		final Integer value = optionalInteger(json, key);
		if (value == null)
		{
			throw new IllegalArgumentException("Missing integer field: " + key);
		}
		return value.intValue();
	}

	static int optionalInt(String json, String key, int defaultValue)
	{
		final Integer value = optionalInteger(json, key);
		return value != null ? value.intValue() : defaultValue;
	}

	static Integer optionalInteger(String json, String key)
	{
		if ((json == null) || json.isBlank())
		{
			return null;
		}

		final Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
		final Matcher matcher = pattern.matcher(json);
		if (!matcher.find())
		{
			return null;
		}
		return Integer.valueOf(matcher.group(1));
	}

	static long optionalLong(String json, String key, long defaultValue)
	{
		final Long value = optionalLongObject(json, key);
		return value != null ? value.longValue() : defaultValue;
	}

	static Long optionalLongObject(String json, String key)
	{
		if ((json == null) || json.isBlank())
		{
			return null;
		}

		final Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
		final Matcher matcher = pattern.matcher(json);
		if (!matcher.find())
		{
			return null;
		}
		return Long.valueOf(matcher.group(1));
	}

	static String optionalObject(String json, String key)
	{
		return optionalWrappedValue(json, key, '{', '}');
	}

	static String optionalArray(String json, String key)
	{
		return optionalWrappedValue(json, key, '[', ']');
	}

	static List<String> parseStringArray(String json, boolean trimValues, boolean skipBlankValues)
	{
		if ((json == null) || json.isBlank())
		{
			return List.of();
		}

		final List<String> values = new ArrayList<>();
		final Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL).matcher(json);
		while (matcher.find())
		{
			String value = unescapeJson(matcher.group(1));
			if (trimValues)
			{
				value = value.trim();
			}
			if (skipBlankValues && value.isBlank())
			{
				continue;
			}
			values.add(value);
		}
		return values;
	}

	static List<Integer> parseIntegerArray(String json)
	{
		if ((json == null) || json.isBlank())
		{
			return List.of();
		}

		final List<Integer> values = new ArrayList<>();
		final Matcher matcher = Pattern.compile("-?\\d+").matcher(json);
		while (matcher.find())
		{
			values.add(Integer.valueOf(matcher.group()));
		}
		return values;
	}

	static String normalizeKey(String value)
	{
		return normalizeKey(value, "");
	}

	static String normalizeKey(String value, String defaultValue)
	{
		return ((value == null) || value.isBlank()) ? defaultValue : value.toLowerCase(Locale.ROOT);
	}

	static String unescapeJson(String value)
	{
		if (value == null)
		{
			return null;
		}

		return value
			.replace("\\\"", "\"")
			.replace("\\\\", "\\")
			.replace("\\n", "\n")
			.replace("\\r", "\r")
			.replace("\\t", "\t")
			.replace("\\b", "\b")
			.replace("\\f", "\f");
	}

	private static String optionalWrappedValue(String json, String key, char openChar, char closeChar)
	{
		if ((json == null) || json.isBlank())
		{
			return null;
		}

		final Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*", Pattern.DOTALL);
		final Matcher matcher = pattern.matcher(json);
		if (!matcher.find())
		{
			return null;
		}

		int start = matcher.end();
		while ((start < json.length()) && Character.isWhitespace(json.charAt(start)))
		{
			start++;
		}
		if ((start >= json.length()) || (json.charAt(start) != openChar))
		{
			return null;
		}

		boolean inString = false;
		boolean escaped = false;
		int depth = 0;
		for (int i = start; i < json.length(); i++)
		{
			final char c = json.charAt(i);
			if (escaped)
			{
				escaped = false;
				continue;
			}
			if (c == '\\')
			{
				escaped = true;
				continue;
			}
			if (c == '"')
			{
				inString = !inString;
				continue;
			}
			if (inString)
			{
				continue;
			}
			if (c == openChar)
			{
				depth++;
			}
			else if (c == closeChar)
			{
				depth--;
				if (depth == 0)
				{
					return json.substring(start, i + 1);
				}
			}
		}
		return null;
	}
}
