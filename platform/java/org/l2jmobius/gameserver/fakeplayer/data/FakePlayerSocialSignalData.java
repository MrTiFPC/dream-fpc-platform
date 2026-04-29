package org.l2jmobius.gameserver.fakeplayer.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSocialSignalBank;

public class FakePlayerSocialSignalData
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerSocialSignalData.class.getName());

	private final FakePlayerConfig _config;
	private final AtomicReference<FpcSocialSignalBank> _bank = new AtomicReference<>(FpcSocialSignalBank.defaults());

	public FakePlayerSocialSignalData(FakePlayerConfig config)
	{
		_config = config;
	}

	public void load()
	{
		try
		{
			final String json = FakePlayerDataAssetResolver.readOptionalText(_config, _config.getSocialSignalPatternsPath());
			if ((json == null) || json.isBlank())
			{
				LOGGER.info(() -> getClass().getSimpleName() + ": No social signal bank found at " + FakePlayerDataAssetResolver.describeSource(_config, _config.getSocialSignalPatternsPath()) + ". Using defaults.");
				_bank.set(FpcSocialSignalBank.defaults());
				return;
			}

			final FpcSocialSignalBank defaults = FpcSocialSignalBank.defaults();
			final FpcSocialSignalBank loaded = new FpcSocialSignalBank(
				readStringArray(json, "insultTokens", defaults.getInsultTokens()),
				readStringArray(json, "negativeTraits", defaults.getNegativeTraits()),
				readStringArray(json, "positiveTraits", defaults.getPositiveTraits()),
				readStringArray(json, "negativePhrases", defaults.getNegativePhrases()),
				readStringArray(json, "positivePhrases", defaults.getPositivePhrases()),
				readStringArray(json, "negativeVerbs", defaults.getNegativeVerbs()),
				readStringArray(json, "positiveVerbs", defaults.getPositiveVerbs()),
				readStringArray(json, "apologyPhrases", defaults.getApologyPhrases()),
				readStringArray(json, "gratitudePhrases", defaults.getGratitudePhrases()),
				readStringArray(json, "affectionPhrases", defaults.getAffectionPhrases()),
				readStringArray(json, "respectPhrases", defaults.getRespectPhrases()),
				readStringArray(json, "praisePhrases", defaults.getPraisePhrases()),
				readStringArray(json, "reassurancePhrases", defaults.getReassurancePhrases()),
				readStringArray(json, "resentmentPhrases", defaults.getResentmentPhrases()),
				readStringArray(json, "distrustPhrases", defaults.getDistrustPhrases()),
				readStringArray(json, "repairPhrases", defaults.getRepairPhrases()),
				readStringArray(json, "threatPhrases", defaults.getThreatPhrases()),
				readStringArray(json, "abandonmentPhrases", defaults.getAbandonmentPhrases()),
				readStringArray(json, "worthDenialTerms", defaults.getWorthDenialTerms()),
				readStringArray(json, "harmWishTerms", defaults.getHarmWishTerms()),
				readStringArray(json, "intensifiers", defaults.getIntensifiers()),
				readStringArray(json, "secondPersonTokens", defaults.getSecondPersonTokens()),
				readStringArray(json, "carryPronouns", defaults.getCarryPronouns()),
				readStringArray(json, "directFlamePhrases", defaults.getDirectFlamePhrases()));
			_bank.set(loaded);
			LOGGER.info(() -> getClass().getSimpleName() + ": Loaded social signal bank from " + FakePlayerDataAssetResolver.describeSource(_config, _config.getSocialSignalPatternsPath()) + " insultTokens=" + loaded.getInsultTokens().size() + " negativeTraits=" + loaded.getNegativeTraits().size() + " positiveTraits=" + loaded.getPositiveTraits().size());
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed loading social signal bank from " + _config.getSocialSignalPatternsPath() + " -> " + e.getMessage());
			_bank.set(FpcSocialSignalBank.defaults());
		}
	}

	public FpcSocialSignalBank getBank()
	{
		return _bank.get();
	}

	private Set<String> readStringArray(String json, String key, Set<String> defaultValue)
	{
		final Set<String> values = new LinkedHashSet<>(FakePlayerJsonDataSupport.parseStringArray(FakePlayerJsonDataSupport.optionalArray(json, key), false, false));
		if (values.isEmpty())
		{
			return defaultValue;
		}
		return Set.copyOf(values);
	}
}
