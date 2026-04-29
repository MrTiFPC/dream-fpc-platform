package org.l2jmobius.gameserver.fakeplayer.platform;

import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.network.enums.ChatType;

/**
 * Transport seam for fakeplayer chat ingress/egress.
 */
public interface FakePlayerChatTransport
{
	void sendOutgoingWhisperEcho(Player sender, String targetDisplayName, String text);

	void sendTargetNotOnline(Player sender);

	void sendPrivateReply(Player targetPlayer, Npc speaker, String displayName, String text);

	default void sendPrivateReply(Player targetPlayer, Npc speaker, String displayName, String text, boolean shareLocation)
	{
		sendPrivateReply(targetPlayer, speaker, displayName, text);
	}

	void broadcastNearbyReply(Npc speaker, String displayName, ChatType chatType, String text);

	void broadcastPublicReply(Npc speaker, String displayName, ChatType chatType, String text);

	void broadcastPartyReply(Party party, Npc speaker, String displayName, String text);

	default void broadcastPartyReply(Party party, Npc speaker, String displayName, String text, boolean shareLocation)
	{
		broadcastPartyReply(party, speaker, displayName, text);
	}

	void broadcastClanReply(Clan clan, Npc speaker, String displayName, String text);
}
