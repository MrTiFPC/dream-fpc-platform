/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.config.custom;

import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapterFactory;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetFactory;

/**
 * Configuration owner for the branch-local fakeplayer/FPC platform bootstrap.
 */
public class FakePlayerPlatformConfig
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerPlatformConfig.class.getName());
	private static final String FAKE_PLAYER_PLATFORM_CONFIG_FILE = "./config/Custom/FakePlayerPlatform.ini";

	public static boolean ENABLE_FPC_PLATFORM;
	public static String FPC_BOOTSTRAP_MODE;
	public static String FPC_PLATFORM_ADAPTER_ID;
	public static String FPC_RULESET_ID;
	public static String FPC_PRODUCT_EDITION;
	public static boolean ENABLE_FPC_STUDIO;
	public static boolean ENABLE_FPC_EXTERNAL_PERSONALITY;

	public static void load()
	{
		final ConfigReader config = new ConfigReader(FAKE_PLAYER_PLATFORM_CONFIG_FILE);
		ENABLE_FPC_PLATFORM = config.getBoolean("EnableFpcPlatform", true);
		FPC_BOOTSTRAP_MODE = normalizeBootstrapMode(config.getString("FpcBootstrapMode", "both"));
		FPC_PLATFORM_ADAPTER_ID = FakePlayerPlatformAdapterFactory.normalizeAdapterId(config.getString("FpcPlatformAdapterId", FakePlayerPlatformAdapterFactory.DEFAULT_PLATFORM_ADAPTER_ID));
		FPC_RULESET_ID = FakePlayerRulesetFactory.normalizeRulesetId(config.getString("FpcRulesetId", FakePlayerRulesetFactory.DEFAULT_RULESET_ID));
		FPC_PRODUCT_EDITION = normalizeProductEdition(config.getString("FpcProductEdition", "community"));
		ENABLE_FPC_STUDIO = config.getBoolean("EnableFpcStudio", true);
		ENABLE_FPC_EXTERNAL_PERSONALITY = config.getBoolean("EnableFpcExternalPersonality", true);
	}

	public static boolean isLegacyBootstrapEnabled()
	{
		return !"platform".equals(FPC_BOOTSTRAP_MODE);
	}

	public static boolean isPlatformBootstrapEnabled()
	{
		return ENABLE_FPC_PLATFORM && !"legacy".equals(FPC_BOOTSTRAP_MODE);
	}

	public static boolean isCommunityEdition()
	{
		return !"dev".equalsIgnoreCase(FPC_PRODUCT_EDITION);
	}

	private static String normalizeBootstrapMode(String mode)
	{
		final String normalized = (mode != null) ? mode.trim().toLowerCase(Locale.ENGLISH) : "";
		switch (normalized)
		{
			case "legacy":
			case "platform":
			case "both":
				return normalized;
			default:
				LOGGER.warning(() -> getSimpleClassName() + ": Unknown FpcBootstrapMode '" + mode + "'. Falling back to 'both'.");
				return "both";
		}
	}

	private static String normalizeProductEdition(String edition)
	{
		final String normalized = (edition != null) ? edition.trim().toLowerCase(Locale.ENGLISH) : "";
		switch (normalized)
		{
			case "community":
			case "dev":
				return normalized;
			default:
				LOGGER.warning(() -> getSimpleClassName() + ": Unknown FpcProductEdition '" + edition + "'. Falling back to 'community'.");
				return "community";
		}
	}

	private static String getSimpleClassName()
	{
		return FakePlayerPlatformConfig.class.getSimpleName();
	}
}
