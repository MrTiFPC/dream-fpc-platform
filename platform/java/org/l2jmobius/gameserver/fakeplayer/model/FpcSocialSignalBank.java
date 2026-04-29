package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class FpcSocialSignalBank
{
	private static final Set<String> DEFAULT_INSULT_TOKENS = Set.of("fuck", "shit", "stupid", "dumb", "idiot", "moron", "loser", "useless", "worthless", "pathetic", "trash", "garbage", "clown", "annoying", "fake", "liar", "weak", "coward", "boring", "creep", "bastard", "awful", "dumbass", "dipshit", "asshole", "bitch", "shithead", "jerk", "prick", "douche", "douchebag", "scum", "scumbag", "filth", "parasite", "rat", "snake", "pig", "tool", "fraud", "wanker", "twat", "jackass");
	private static final Set<String> DEFAULT_NEGATIVE_TRAITS = Set.of("stupid", "dumb", "idiot", "moron", "loser", "useless", "worthless", "pathetic", "trash", "garbage", "clown", "fake", "liar", "weak", "coward", "boring", "creep", "bastard", "awful", "dumbass", "dipshit", "asshole", "bitch", "shithead", "joke", "shit", "jerk", "prick", "douche", "douchebag", "scum", "scumbag", "filth", "parasite", "rat", "snake", "pig", "tool", "fraud", "wanker", "twat", "jackass", "toxic", "vile", "nasty", "disgusting", "rotten", "slime");
	private static final Set<String> DEFAULT_POSITIVE_TRAITS = Set.of("important", "worthy", "valuable", "steady", "strong");
	private static final Set<String> DEFAULT_NEGATIVE_PHRASES = Set.of("means nothing", "does not matter", "doesnt matter", "doesn't matter", "shouldn't exist", "shouldnt exist", "waste of space", "piece of shit", "piece of trash", "piece of garbage", "good for nothing", "nothing but trouble", "not worth anything");
	private static final Set<String> DEFAULT_POSITIVE_PHRASES = Set.of("means a lot", "matters", "is important", "deserves better", "is worth protecting");
	private static final Set<String> DEFAULT_NEGATIVE_VERBS = Set.of("hate", "despise", "resent", "dont trust", "don't trust", "cant stand", "can't stand", "mock", "belittle", "loathe", "disgust", "spit on", "reject");
	private static final Set<String> DEFAULT_POSITIVE_VERBS = Set.of("love", "care about", "trust", "respect", "admire", "protect", "miss");
	private static final Set<String> DEFAULT_APOLOGY_PHRASES = Set.of("sorry", "i am sorry", "i'm sorry", "apologize", "apologise", "forgive me", "my bad", "my fault", "i was wrong", "i messed up", "didn't mean it", "didnt mean it", "pardon me");
	private static final Set<String> DEFAULT_GRATITUDE_PHRASES = Set.of("thanks", "thank you", "ty", "thx", "appreciate it", "appreciate you", "grateful", "means a lot", "owe you");
	private static final Set<String> DEFAULT_AFFECTION_PHRASES = Set.of("i like you", "i care about you", "i love you", "glad you're here", "glad youre here", "happy you're here", "happy youre here", "i miss you", "enjoy your company", "you matter to me", "i want you here");
	private static final Set<String> DEFAULT_RESPECT_PHRASES = Set.of("i respect you", "i trust you", "i admire you", "i value you", "i believe in you", "you were right", "you deserve respect");
	private static final Set<String> DEFAULT_PRAISE_PHRASES = Set.of("you did well", "you did great", "you handled that well", "good work", "well done", "nice work", "im proud of you", "i'm proud of you", "i am proud of you", "you were clever", "you are clever", "you're clever", "you are strong", "you're strong", "you are capable", "you're capable", "you were right");
	private static final Set<String> DEFAULT_REASSURANCE_PHRASES = Set.of("im with you", "i'm with you", "i am with you", "you can trust me", "i wont leave", "i won't leave", "im not leaving", "i'm not leaving", "i am not leaving", "you are safe with me", "you're safe with me", "youre safe with me", "i will stay", "i'll stay", "ill stay", "im on your side", "i'm on your side", "i am on your side", "i will stand with you", "i'll stand with you", "ill stand with you", "i wont hurt you", "i won't hurt you");
	private static final Set<String> DEFAULT_RESENTMENT_PHRASES = Set.of("i hate you", "can't stand you", "cant stand you", "i resent you", "i despise you", "you disgust me", "you make me sick", "i reject you");
	private static final Set<String> DEFAULT_DISTRUST_PHRASES = Set.of("i dont trust you", "i don't trust you", "can't rely on you", "cant rely on you", "you hide too much", "you are hiding something", "you're hiding something", "youre hiding something", "i doubt you", "you feel wrong", "you are not honest", "you're not honest", "youre not honest", "i cant believe you", "i can't believe you");
	private static final Set<String> DEFAULT_REPAIR_PHRASES = Set.of("start fresh", "start over", "make it right", "make things right", "fix this", "try again", "make peace", "new relationship", "be friends again", "can we fix this", "can we start over", "can we try again");
	private static final Set<String> DEFAULT_THREAT_PHRASES = Set.of("i will kill you", "ill kill you", "i'll kill you", "i will hurt you", "ill hurt you", "i'll hurt you", "i will ruin you", "ill ruin you", "i'll ruin you", "i will destroy you", "ill destroy you", "i'll destroy you", "i will hunt you", "ill hunt you", "i'll hunt you", "i am coming for you", "im coming for you", "i'm coming for you", "watch your back");
	private static final Set<String> DEFAULT_ABANDONMENT_PHRASES = Set.of("im leaving you", "i'm leaving you", "i am leaving you", "im done with you", "i'm done with you", "i am done with you", "i wont come back", "i won't come back", "im not coming back", "i'm not coming back", "i am not coming back", "you are on your own", "you're on your own", "youre on your own", "i leave you alone", "im leaving for good", "i'm leaving for good", "i am leaving for good");
	private static final Set<String> DEFAULT_WORTH_DENIAL_TERMS = Set.of("anything", "nothing", "better", "respect", "trust", "kindness", "mercy", "care", "love", "time", "life");
	private static final Set<String> DEFAULT_HARM_WISH_TERMS = Set.of("suffer", "die", "burn", "gone", "rot", "choke", "bleed");
	private static final Set<String> DEFAULT_INTENSIFIERS = Set.of("very", "really", "pretty", "quite", "too", "extra", "total", "totally", "complete", "completely", "absolute", "absolutely", "fucking", "damn", "so", "such", "massive", "utter", "utterly", "goddamn");
	private static final Set<String> DEFAULT_SECOND_PERSON_TOKENS = Set.of("you", "your", "youre", "u");
	private static final Set<String> DEFAULT_CARRY_PRONOUNS = Set.of("he", "hes", "he's", "him", "she", "shes", "she's", "her", "they", "theyre", "they're", "them");
	private static final Set<String> DEFAULT_DIRECT_FLAME_PHRASES = Set.of("fuck you", "screw you", "fuck off", "hate you", "kill yourself", "go fuck yourself", "fuck yourself", "go to hell", "rot in hell", "drop dead", "eat shit", "shut up", "shut the fuck up", "piece of shit", "waste of space", "stfu", "gtfo", "kys");

	private final Set<String> _insultTokens;
	private final Set<String> _negativeTraits;
	private final Set<String> _positiveTraits;
	private final Set<String> _negativePhrases;
	private final Set<String> _positivePhrases;
	private final Set<String> _negativeVerbs;
	private final Set<String> _positiveVerbs;
	private final Set<String> _apologyPhrases;
	private final Set<String> _gratitudePhrases;
	private final Set<String> _affectionPhrases;
	private final Set<String> _respectPhrases;
	private final Set<String> _praisePhrases;
	private final Set<String> _reassurancePhrases;
	private final Set<String> _resentmentPhrases;
	private final Set<String> _distrustPhrases;
	private final Set<String> _repairPhrases;
	private final Set<String> _threatPhrases;
	private final Set<String> _abandonmentPhrases;
	private final Set<String> _worthDenialTerms;
	private final Set<String> _harmWishTerms;
	private final Set<String> _intensifiers;
	private final Set<String> _secondPersonTokens;
	private final Set<String> _carryPronouns;
	private final Set<String> _directFlamePhrases;

	public FpcSocialSignalBank(Collection<String> insultTokens, Collection<String> negativeTraits, Collection<String> positiveTraits, Collection<String> negativePhrases, Collection<String> positivePhrases, Collection<String> negativeVerbs, Collection<String> positiveVerbs, Collection<String> apologyPhrases, Collection<String> gratitudePhrases, Collection<String> affectionPhrases, Collection<String> respectPhrases, Collection<String> praisePhrases, Collection<String> reassurancePhrases, Collection<String> resentmentPhrases, Collection<String> distrustPhrases, Collection<String> repairPhrases, Collection<String> threatPhrases, Collection<String> abandonmentPhrases, Collection<String> worthDenialTerms, Collection<String> harmWishTerms, Collection<String> intensifiers, Collection<String> secondPersonTokens, Collection<String> carryPronouns, Collection<String> directFlamePhrases)
	{
		_insultTokens = normalizeSet(insultTokens, DEFAULT_INSULT_TOKENS);
		_negativeTraits = normalizeSet(negativeTraits, DEFAULT_NEGATIVE_TRAITS);
		_positiveTraits = normalizeSet(positiveTraits, DEFAULT_POSITIVE_TRAITS);
		_negativePhrases = normalizeSet(negativePhrases, DEFAULT_NEGATIVE_PHRASES);
		_positivePhrases = normalizeSet(positivePhrases, DEFAULT_POSITIVE_PHRASES);
		_negativeVerbs = normalizeSet(negativeVerbs, DEFAULT_NEGATIVE_VERBS);
		_positiveVerbs = normalizeSet(positiveVerbs, DEFAULT_POSITIVE_VERBS);
		_apologyPhrases = normalizeSet(apologyPhrases, DEFAULT_APOLOGY_PHRASES);
		_gratitudePhrases = normalizeSet(gratitudePhrases, DEFAULT_GRATITUDE_PHRASES);
		_affectionPhrases = normalizeSet(affectionPhrases, DEFAULT_AFFECTION_PHRASES);
		_respectPhrases = normalizeSet(respectPhrases, DEFAULT_RESPECT_PHRASES);
		_praisePhrases = normalizeSet(praisePhrases, DEFAULT_PRAISE_PHRASES);
		_reassurancePhrases = normalizeSet(reassurancePhrases, DEFAULT_REASSURANCE_PHRASES);
		_resentmentPhrases = normalizeSet(resentmentPhrases, DEFAULT_RESENTMENT_PHRASES);
		_distrustPhrases = normalizeSet(distrustPhrases, DEFAULT_DISTRUST_PHRASES);
		_repairPhrases = normalizeSet(repairPhrases, DEFAULT_REPAIR_PHRASES);
		_threatPhrases = normalizeSet(threatPhrases, DEFAULT_THREAT_PHRASES);
		_abandonmentPhrases = normalizeSet(abandonmentPhrases, DEFAULT_ABANDONMENT_PHRASES);
		_worthDenialTerms = normalizeSet(worthDenialTerms, DEFAULT_WORTH_DENIAL_TERMS);
		_harmWishTerms = normalizeSet(harmWishTerms, DEFAULT_HARM_WISH_TERMS);
		_intensifiers = normalizeSet(intensifiers, DEFAULT_INTENSIFIERS);
		_secondPersonTokens = normalizeSet(secondPersonTokens, DEFAULT_SECOND_PERSON_TOKENS);
		_carryPronouns = normalizeSet(carryPronouns, DEFAULT_CARRY_PRONOUNS);
		_directFlamePhrases = normalizeSet(directFlamePhrases, DEFAULT_DIRECT_FLAME_PHRASES);
	}

	public static FpcSocialSignalBank defaults()
	{
		return new FpcSocialSignalBank(DEFAULT_INSULT_TOKENS, DEFAULT_NEGATIVE_TRAITS, DEFAULT_POSITIVE_TRAITS, DEFAULT_NEGATIVE_PHRASES, DEFAULT_POSITIVE_PHRASES, DEFAULT_NEGATIVE_VERBS, DEFAULT_POSITIVE_VERBS, DEFAULT_APOLOGY_PHRASES, DEFAULT_GRATITUDE_PHRASES, DEFAULT_AFFECTION_PHRASES, DEFAULT_RESPECT_PHRASES, DEFAULT_PRAISE_PHRASES, DEFAULT_REASSURANCE_PHRASES, DEFAULT_RESENTMENT_PHRASES, DEFAULT_DISTRUST_PHRASES, DEFAULT_REPAIR_PHRASES, DEFAULT_THREAT_PHRASES, DEFAULT_ABANDONMENT_PHRASES, DEFAULT_WORTH_DENIAL_TERMS, DEFAULT_HARM_WISH_TERMS, DEFAULT_INTENSIFIERS, DEFAULT_SECOND_PERSON_TOKENS, DEFAULT_CARRY_PRONOUNS, DEFAULT_DIRECT_FLAME_PHRASES);
	}

	private Set<String> normalizeSet(Collection<String> values, Set<String> fallback)
	{
		final Collection<String> source = ((values == null) || values.isEmpty()) ? fallback : values;
		final Set<String> normalized = new LinkedHashSet<>();
		for (String value : source)
		{
			final String next = normalize(value);
			if (!next.isBlank())
			{
				normalized.add(next);
			}
		}
		return Set.copyOf(normalized);
	}

	private String normalize(String value)
	{
		return (value == null) ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]+", " ").trim().replaceAll("\\s+", " ");
	}

	public Set<String> getInsultTokens()
	{
		return _insultTokens;
	}

	public Set<String> getNegativeTraits()
	{
		return _negativeTraits;
	}

	public Set<String> getPositiveTraits()
	{
		return _positiveTraits;
	}

	public Set<String> getNegativePhrases()
	{
		return _negativePhrases;
	}

	public Set<String> getPositivePhrases()
	{
		return _positivePhrases;
	}

	public Set<String> getNegativeVerbs()
	{
		return _negativeVerbs;
	}

	public Set<String> getPositiveVerbs()
	{
		return _positiveVerbs;
	}

	public Set<String> getApologyPhrases()
	{
		return _apologyPhrases;
	}

	public Set<String> getGratitudePhrases()
	{
		return _gratitudePhrases;
	}

	public Set<String> getAffectionPhrases()
	{
		return _affectionPhrases;
	}

	public Set<String> getRespectPhrases()
	{
		return _respectPhrases;
	}

	public Set<String> getPraisePhrases()
	{
		return _praisePhrases;
	}

	public Set<String> getReassurancePhrases()
	{
		return _reassurancePhrases;
	}

	public Set<String> getResentmentPhrases()
	{
		return _resentmentPhrases;
	}

	public Set<String> getDistrustPhrases()
	{
		return _distrustPhrases;
	}

	public Set<String> getRepairPhrases()
	{
		return _repairPhrases;
	}

	public Set<String> getThreatPhrases()
	{
		return _threatPhrases;
	}

	public Set<String> getAbandonmentPhrases()
	{
		return _abandonmentPhrases;
	}

	public Set<String> getWorthDenialTerms()
	{
		return _worthDenialTerms;
	}

	public Set<String> getHarmWishTerms()
	{
		return _harmWishTerms;
	}

	public Set<String> getIntensifiers()
	{
		return _intensifiers;
	}

	public Set<String> getSecondPersonTokens()
	{
		return _secondPersonTokens;
	}

	public Set<String> getCarryPronouns()
	{
		return _carryPronouns;
	}

	public Set<String> getDirectFlamePhrases()
	{
		return _directFlamePhrases;
	}
}
