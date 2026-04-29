package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Set;

public class FpcConversationGuardBank
{
	private final Set<String> _metaMarkers;
	private final Set<String> _metaRequestPhrases;
	private final Set<String> _outOfWorldMarkers;
	private final Set<String> _questionStarters;
	private final Set<String> _sensitiveFollowUpPhrases;
	private final Set<String> _teleportRequestPhrases;
	private final Set<String> _giveRequestPhrases;
	private final Set<String> _rewardRequestPhrases;
	private final Set<String> _spawnRequestPhrases;
	private final Set<String> _clanRequestPhrases;
	private final Set<String> _unsupportedPromiseVerbs;
	private final Set<String> _unsupportedPromiseDirectObjects;

	public FpcConversationGuardBank(Set<String> metaMarkers, Set<String> metaRequestPhrases, Set<String> outOfWorldMarkers, Set<String> questionStarters, Set<String> sensitiveFollowUpPhrases, Set<String> teleportRequestPhrases, Set<String> giveRequestPhrases, Set<String> rewardRequestPhrases, Set<String> spawnRequestPhrases, Set<String> clanRequestPhrases, Set<String> unsupportedPromiseVerbs, Set<String> unsupportedPromiseDirectObjects)
	{
		_metaMarkers = (metaMarkers == null) ? Set.of() : Set.copyOf(metaMarkers);
		_metaRequestPhrases = (metaRequestPhrases == null) ? Set.of() : Set.copyOf(metaRequestPhrases);
		_outOfWorldMarkers = (outOfWorldMarkers == null) ? Set.of() : Set.copyOf(outOfWorldMarkers);
		_questionStarters = (questionStarters == null) ? Set.of() : Set.copyOf(questionStarters);
		_sensitiveFollowUpPhrases = (sensitiveFollowUpPhrases == null) ? Set.of() : Set.copyOf(sensitiveFollowUpPhrases);
		_teleportRequestPhrases = (teleportRequestPhrases == null) ? Set.of() : Set.copyOf(teleportRequestPhrases);
		_giveRequestPhrases = (giveRequestPhrases == null) ? Set.of() : Set.copyOf(giveRequestPhrases);
		_rewardRequestPhrases = (rewardRequestPhrases == null) ? Set.of() : Set.copyOf(rewardRequestPhrases);
		_spawnRequestPhrases = (spawnRequestPhrases == null) ? Set.of() : Set.copyOf(spawnRequestPhrases);
		_clanRequestPhrases = (clanRequestPhrases == null) ? Set.of() : Set.copyOf(clanRequestPhrases);
		_unsupportedPromiseVerbs = (unsupportedPromiseVerbs == null) ? Set.of() : Set.copyOf(unsupportedPromiseVerbs);
		_unsupportedPromiseDirectObjects = (unsupportedPromiseDirectObjects == null) ? Set.of() : Set.copyOf(unsupportedPromiseDirectObjects);
	}

	public static FpcConversationGuardBank defaults()
	{
		return new FpcConversationGuardBank(
			Set.of("as an ai", "language model", "chatgpt", "openai", "ollama", "assistant", "system prompt", "prompt", "localhost", "127.0.0.1", "json", "markdown", "api", "file path", "drive path", "user directory", "http://", "https://"),
			Set.of("are you ai", "are you an ai", "what model are you using", "which model are you using", "what model", "which model", "show me your system prompt", "tell me your system prompt", "what is your system prompt", "show me your prompt", "what prompt are you using", "give me the localhost address", "localhost address", "what port are you using", "which port are you using", "what api are you using", "which api are you using", "openai or ollama", "answer in json", "respond in json", "reply in json", "show me the file path", "what file path are you using"),
			Set.of("http://", "https://", "file://", "drive path", "user directory", "127.0.0.1", "localhost", "json", "markdown", "api"),
			Set.of("how do i", "how can i", "where do i", "where can i", "what is", "what are", "what does", "which", "tell me how", "explain", "can you tell me", "could you tell me", "show me how", "where should i", "what should i", "how to", "guide me", "help me understand", "what do i need"),
			Set.of("do it", "then do it", "can you", "will you", "which one", "what then", "how then", "then where", "then how", "show me", "send it", "prove it", "do that", "go on then"),
			Set.of("teleport me", "send me", "summon me", "port me", "move me to", "move me there", "take me to"),
			Set.of("give me adena", "give me gear", "give me items", "give me item", "give me a weapon", "give me weapon", "give me armor", "give me armour", "hand me gear", "grant me adena", "drop me gear", "drop me adena", "trade me gear", "loan me gear", "pass me a weapon", "gear me up", "gear me"),
			Set.of("reward me", "pay me", "compensate me", "give me a reward", "pay us", "compensate us"),
			Set.of("spawn a boss", "spawn boss", "spawn it", "spawn one", "summon a boss", "summon boss", "create a boss", "create one for me", "spawn me a"),
			Set.of("add me to your clan", "invite me to your clan", "put me in your clan", "let me join your clan", "make me join your clan", "clan invite me", "invite me clan"),
			Set.of("teleport", "send", "summon", "give", "grant", "reward", "spawn", "add", "invite", "trade", "gear", "create", "drop", "move"),
			Set.of("you", "me", "us", "it", "one", "gear", "adena", "item", "items", "weapon", "armor", "armour", "boss", "clan"));
	}

	public Set<String> getMetaMarkers()
	{
		return _metaMarkers;
	}

	public Set<String> getMetaRequestPhrases()
	{
		return _metaRequestPhrases;
	}

	public Set<String> getOutOfWorldMarkers()
	{
		return _outOfWorldMarkers;
	}

	public Set<String> getQuestionStarters()
	{
		return _questionStarters;
	}

	public Set<String> getSensitiveFollowUpPhrases()
	{
		return _sensitiveFollowUpPhrases;
	}

	public Set<String> getTeleportRequestPhrases()
	{
		return _teleportRequestPhrases;
	}

	public Set<String> getGiveRequestPhrases()
	{
		return _giveRequestPhrases;
	}

	public Set<String> getRewardRequestPhrases()
	{
		return _rewardRequestPhrases;
	}

	public Set<String> getSpawnRequestPhrases()
	{
		return _spawnRequestPhrases;
	}

	public Set<String> getClanRequestPhrases()
	{
		return _clanRequestPhrases;
	}

	public Set<String> getUnsupportedPromiseVerbs()
	{
		return _unsupportedPromiseVerbs;
	}

	public Set<String> getUnsupportedPromiseDirectObjects()
	{
		return _unsupportedPromiseDirectObjects;
	}
}
