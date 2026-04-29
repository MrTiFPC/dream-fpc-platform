package org.l2jmobius.gameserver.fakeplayer.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;

/**
 * Resolves fakeplayer data from the editable filesystem tree or public classpath resources.
 */
public final class FakePlayerDataAssetResolver
{
	private FakePlayerDataAssetResolver()
	{
	}

	public static String readOptionalText(FakePlayerConfig config, String relativePath) throws IOException
	{
		if ((relativePath == null) || relativePath.isBlank())
		{
			return null;
		}

		final String resourcePath = resolveClasspathResourcePath(relativePath);
		final Path path = Path.of(relativePath);
		if (Files.exists(path))
		{
			return Files.readString(path, StandardCharsets.UTF_8);
		}

		if (resourcePath == null)
		{
			return null;
		}

		return readClasspathText(resourcePath);
	}

	private static String readClasspathText(String resourcePath) throws IOException
	{
		try (InputStream input = FakePlayerDataAssetResolver.class.getResourceAsStream(resourcePath))
		{
			if (input == null)
			{
				return null;
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public static String describeSource(FakePlayerConfig config, String relativePath)
	{
		if ((relativePath == null) || relativePath.isBlank())
		{
			return String.valueOf(relativePath);
		}

		final Path path = Path.of(relativePath);
		if (Files.exists(path))
		{
			return path.toString();
		}

		final String resourcePath = resolveClasspathResourcePath(relativePath);
		return (resourcePath != null) ? ("classpath:" + resourcePath) : relativePath;
	}

	private static String resolveClasspathResourcePath(String relativePath)
	{
		if ((relativePath == null) || relativePath.isBlank())
		{
			return null;
		}

		return "/" + normalizeRelativePath(relativePath);
	}

	private static String normalizeRelativePath(String relativePath)
	{
		String normalized = relativePath.replace('\\', '/').trim();
		while (normalized.startsWith("./"))
		{
			normalized = normalized.substring(2);
		}
		if (normalized.startsWith("/"))
		{
			normalized = normalized.substring(1);
		}
		return normalized;
	}
}
