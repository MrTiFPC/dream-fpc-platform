package org.l2jmobius.gameserver.fakeplayer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDebugCategory;

public class FakePlayerDebugService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerDebugService.class.getName());
	private static final Logger TRACE_LOGGER = Logger.getLogger("org.l2jmobius.gameserver.fakeplayer.debug");
	private static final DateTimeFormatter TRACE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

	private final Map<String, EnumSet<FpcDebugCategory>> _enabledCategoriesByFpc = new ConcurrentHashMap<>();

	public FakePlayerDebugService(FakePlayerConfig config)
	{
		configureLogger(config.getDebugLogPath());
	}

	public void enable(String fpcId, Set<FpcDebugCategory> categories)
	{
		_enabledCategoriesByFpc.put(normalize(fpcId), EnumSet.copyOf((categories == null) || categories.isEmpty() ? FpcDebugCategory.allCategories() : categories));
	}

	public void disable(String fpcId)
	{
		_enabledCategoriesByFpc.remove(normalize(fpcId));
	}

	public boolean isEnabled(String fpcId, FpcDebugCategory category)
	{
		if (category == null)
		{
			return false;
		}

		final EnumSet<FpcDebugCategory> categories = _enabledCategoriesByFpc.get(normalize(fpcId));
		return (categories != null) && categories.contains(category);
	}

	public Set<FpcDebugCategory> getEnabledCategories(String fpcId)
	{
		final EnumSet<FpcDebugCategory> categories = _enabledCategoriesByFpc.get(normalize(fpcId));
		return (categories == null) ? Collections.emptySet() : EnumSet.copyOf(categories);
	}

	public String describe(String fpcId)
	{
		return FpcDebugCategory.describe(getEnabledCategories(fpcId));
	}

	public void trace(String fpcId, FpcDebugCategory category, String message)
	{
		if (!isEnabled(fpcId, category) || (message == null) || message.isBlank())
		{
			return;
		}

		TRACE_LOGGER.info(() -> "fpc=" + normalize(fpcId) + " cat=" + category.name().toLowerCase() + " " + message);
	}

	private void configureLogger(String debugLogPath)
	{
		synchronized (TRACE_LOGGER)
		{
			if (TRACE_LOGGER.getHandlers().length > 0)
			{
				return;
			}

			TRACE_LOGGER.setUseParentHandlers(false);
			TRACE_LOGGER.setLevel(Level.INFO);
			try
			{
				final Path logPath = Paths.get(((debugLogPath == null) || debugLogPath.isBlank()) ? "log/fpc-debug.log" : debugLogPath);
				final Path parent = logPath.getParent();
				if (parent != null)
				{
					Files.createDirectories(parent);
				}

				final FileHandler fileHandler = new FileHandler(logPath.toString(), true);
				fileHandler.setEncoding(StandardCharsets.UTF_8.name());
				fileHandler.setFormatter(new CompactTraceFormatter());
				TRACE_LOGGER.addHandler(fileHandler);
			}
			catch (IOException e)
			{
				LOGGER.warning(getClass().getSimpleName() + ": Failed to initialize fakeplayer debug logger -> " + e.getMessage());
			}
		}
	}

	private String normalize(String value)
	{
		return ((value == null) || value.isBlank()) ? "unknown-fpc" : value.trim().toLowerCase();
	}

	private static final class CompactTraceFormatter extends java.util.logging.Formatter
	{
		@Override
		public String format(LogRecord record)
		{
			final String timestamp = TRACE_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(record.getMillis()));
			final String level = record.getLevel().getName();
			final String message = formatMessage(record);
			return timestamp + " " + level + " " + message + System.lineSeparator();
		}
	}
}
