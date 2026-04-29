package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.List;
import java.util.Objects;

/**
 * Narrow fake-player runtime snapshot used to build advisory personality context.
 */
public class FakePlayerRuntimeSnapshot
{
	private final String _fakePlayerId;
	private final String _archetype;
	private final String _currentZone;
	private final String _state;
	private final String _hpBand;
	private final String _mpBand;
	private final int _nearbyPlayerCount;
	private final boolean _chatCooldownReady;
	private final boolean _teleportCooldownReady;
	private final String _lastLineTag;
	private final double _boredomScore;
	private final List<String> _allowedIntents;
	private final List<String> _allowedZones;
	private final List<String> _availableLineStyleTags;
	private final List<String> _availableTopicTags;

	public FakePlayerRuntimeSnapshot(String fakePlayerId, String archetype, String currentZone, String state, String hpBand, String mpBand, int nearbyPlayerCount, boolean chatCooldownReady, boolean teleportCooldownReady, String lastLineTag, double boredomScore, List<String> allowedIntents, List<String> allowedZones, List<String> availableLineStyleTags, List<String> availableTopicTags)
	{
		_fakePlayerId = Objects.requireNonNull(fakePlayerId);
		_archetype = Objects.requireNonNull(archetype);
		_currentZone = Objects.requireNonNull(currentZone);
		_state = Objects.requireNonNull(state);
		_hpBand = Objects.requireNonNull(hpBand);
		_mpBand = Objects.requireNonNull(mpBand);
		_nearbyPlayerCount = nearbyPlayerCount;
		_chatCooldownReady = chatCooldownReady;
		_teleportCooldownReady = teleportCooldownReady;
		_lastLineTag = Objects.requireNonNull(lastLineTag);
		_boredomScore = boredomScore;
		_allowedIntents = List.copyOf(allowedIntents);
		_allowedZones = List.copyOf(allowedZones);
		_availableLineStyleTags = List.copyOf(availableLineStyleTags);
		_availableTopicTags = List.copyOf(availableTopicTags);
	}

	public String getFakePlayerId()
	{
		return _fakePlayerId;
	}

	public String getArchetype()
	{
		return _archetype;
	}

	public String getCurrentZone()
	{
		return _currentZone;
	}

	public String getState()
	{
		return _state;
	}

	public String getHpBand()
	{
		return _hpBand;
	}

	public String getMpBand()
	{
		return _mpBand;
	}

	public int getNearbyPlayerCount()
	{
		return _nearbyPlayerCount;
	}

	public boolean isChatCooldownReady()
	{
		return _chatCooldownReady;
	}

	public boolean isTeleportCooldownReady()
	{
		return _teleportCooldownReady;
	}

	public String getLastLineTag()
	{
		return _lastLineTag;
	}

	public double getBoredomScore()
	{
		return _boredomScore;
	}

	public List<String> getAllowedIntents()
	{
		return _allowedIntents;
	}

	public List<String> getAllowedZones()
	{
		return _allowedZones;
	}

	public List<String> getAvailableLineStyleTags()
	{
		return _availableLineStyleTags;
	}

	public List<String> getAvailableTopicTags()
	{
		return _availableTopicTags;
	}
}
