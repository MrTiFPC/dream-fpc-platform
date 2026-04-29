package org.l2jmobius.gameserver.fakeplayer.platform;

import java.util.Locale;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.platform.mobius.MobiusTierAPlatformAdapter;

public final class FakePlayerPlatformAdapterFactory
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerPlatformAdapterFactory.class.getName());
	public static final String DEFAULT_PLATFORM_ADAPTER_ID = MobiusTierAPlatformAdapter.ID;

	private FakePlayerPlatformAdapterFactory()
	{
	}

	public static FakePlayerPlatformAdapter create(String adapterId)
	{
		final String normalizedId = normalizeAdapterId(adapterId);
		switch (normalizedId)
		{
			case MobiusTierAPlatformAdapter.ID:
				return new MobiusTierAPlatformAdapter();
			default:
				LOGGER.warning(() -> FakePlayerPlatformAdapterFactory.class.getSimpleName() + ": Unknown FPC platform adapter '" + adapterId + "'. Falling back to '" + DEFAULT_PLATFORM_ADAPTER_ID + "'.");
				return new MobiusTierAPlatformAdapter();
		}
	}

	public static String normalizeAdapterId(String adapterId)
	{
		if ((adapterId == null) || adapterId.isBlank())
		{
			return DEFAULT_PLATFORM_ADAPTER_ID;
		}
		return adapterId.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
	}
}
