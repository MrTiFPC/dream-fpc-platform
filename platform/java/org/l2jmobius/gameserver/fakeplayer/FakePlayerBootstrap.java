package org.l2jmobius.gameserver.fakeplayer;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.custom.FakePlayerPlatformConfig;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapter;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerPlatformAdapterFactory;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetAdapter;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetFactory;
import org.l2jmobius.gameserver.managers.FakePlayerChatManager;

/**
 * Central bootstrap owner for legacy fakeplayer chat and the newer FPC platform.
 */
public final class FakePlayerBootstrap
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerBootstrap.class.getName());

	private static volatile FakePlayerModule PLATFORM_MODULE;

	private FakePlayerBootstrap()
	{
	}

	public static void bootstrapLegacyChat()
	{
		if (!FakePlayerPlatformConfig.isLegacyBootstrapEnabled())
		{
			LOGGER.info(() -> getSimpleClassName() + ": Legacy fakeplayer bootstrap skipped by mode=" + FakePlayerPlatformConfig.FPC_BOOTSTRAP_MODE + ".");
			return;
		}

		FakePlayerChatManager.getInstance();
	}

	public static void bootstrapPlatform()
	{
		if (!FakePlayerPlatformConfig.isPlatformBootstrapEnabled())
		{
			LOGGER.info(() -> getSimpleClassName() + ": FPC platform bootstrap disabled. enabled=" + FakePlayerPlatformConfig.ENABLE_FPC_PLATFORM + " mode=" + FakePlayerPlatformConfig.FPC_BOOTSTRAP_MODE + ".");
			return;
		}

		FakePlayerModule module = PLATFORM_MODULE;
		if (module == null)
		{
			synchronized (FakePlayerBootstrap.class)
			{
				module = PLATFORM_MODULE;
				if (module == null)
				{
					final FakePlayerConfig config = new FakePlayerConfig();
					final FakePlayerPlatformAdapter platformAdapter = FakePlayerPlatformAdapterFactory.create(FakePlayerPlatformConfig.FPC_PLATFORM_ADAPTER_ID);
					final FakePlayerRulesetAdapter ruleset = FakePlayerRulesetFactory.create(FakePlayerPlatformConfig.FPC_RULESET_ID);
					LOGGER.info(() -> getSimpleClassName() + ": Bootstrapping FPC platform mode=" + FakePlayerPlatformConfig.FPC_BOOTSTRAP_MODE + " adapter=" + platformAdapter.getId() + " ruleset=" + ruleset.getId() + ".");
					module = new FakePlayerModule(config, ruleset, platformAdapter);
					PLATFORM_MODULE = module;
				}
			}
		}

		module.init();
	}

	public static FakePlayerModule getPlatformModule()
	{
		return PLATFORM_MODULE;
	}

	private static String getSimpleClassName()
	{
		return FakePlayerBootstrap.class.getSimpleName();
	}
}
