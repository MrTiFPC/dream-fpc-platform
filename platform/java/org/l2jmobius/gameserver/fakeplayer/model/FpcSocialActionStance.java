package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.ArrayList;
import java.util.List;

public final class FpcSocialActionStance
{
	public static final FpcSocialActionStance EMPTY = new FpcSocialActionStance(false, false, false, false, false, false);

	private final boolean _partyOk;
	private final boolean _partyGuarded;
	private final boolean _refuseParty;
	private final boolean _refuseLead;
	private final boolean _rumorShareOk;
	private final boolean _avoidPlayer;

	public FpcSocialActionStance(boolean partyOk, boolean partyGuarded, boolean refuseParty, boolean refuseLead, boolean rumorShareOk, boolean avoidPlayer)
	{
		_partyOk = partyOk;
		_partyGuarded = partyGuarded;
		_refuseParty = refuseParty;
		_refuseLead = refuseLead;
		_rumorShareOk = rumorShareOk;
		_avoidPlayer = avoidPlayer;
	}

	public boolean isPartyOk()
	{
		return _partyOk;
	}

	public boolean isPartyGuarded()
	{
		return _partyGuarded;
	}

	public boolean isRefuseParty()
	{
		return _refuseParty;
	}

	public boolean isRefuseLead()
	{
		return _refuseLead;
	}

	public boolean isRumorShareOk()
	{
		return _rumorShareOk;
	}

	public boolean isAvoidPlayer()
	{
		return _avoidPlayer;
	}

	public boolean hasSignals()
	{
		return _partyOk || _partyGuarded || _refuseParty || _refuseLead || _rumorShareOk || _avoidPlayer;
	}

	public String describe()
	{
		final List<String> tags = new ArrayList<>(6);
		if (_partyOk)
		{
			tags.add("party_ok");
		}
		if (_partyGuarded)
		{
			tags.add("party_guarded");
		}
		if (_refuseParty)
		{
			tags.add("refuse_party");
		}
		if (_refuseLead)
		{
			tags.add("refuse_lead");
		}
		if (_rumorShareOk)
		{
			tags.add("rumor_share_ok");
		}
		if (_avoidPlayer)
		{
			tags.add("avoid_player");
		}
		return tags.isEmpty() ? "none" : String.join(", ", tags);
	}
}
