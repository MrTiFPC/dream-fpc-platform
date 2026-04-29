package org.l2jmobius.gameserver.fakeplayer.ruleset;

import java.util.Locale;
import java.util.logging.Logger;

public final class FakePlayerRulesetFactory
{
	public static final String DEFAULT_RULESET_ID = EssenceWargRulesetAdapter.ID;

	private static final Logger LOGGER = Logger.getLogger(FakePlayerRulesetFactory.class.getName());

	private FakePlayerRulesetFactory()
	{
	}

	public static FakePlayerRulesetAdapter create(String rulesetId)
	{
		final String normalizedId = normalizeRulesetId(rulesetId);
		switch (normalizedId)
		{
			case EssenceWargRulesetAdapter.ID:
				return new EssenceWargRulesetAdapter();
			default:
				LOGGER.warning(() -> FakePlayerRulesetFactory.class.getSimpleName() + ": Unknown FPC ruleset '" + rulesetId + "'. Falling back to '" + DEFAULT_RULESET_ID + "'.");
				return new EssenceWargRulesetAdapter();
		}
	}

	public static String normalizeRulesetId(String rulesetId)
	{
		if ((rulesetId == null) || rulesetId.isBlank())
		{
			return DEFAULT_RULESET_ID;
		}
		return rulesetId.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
	}
}
