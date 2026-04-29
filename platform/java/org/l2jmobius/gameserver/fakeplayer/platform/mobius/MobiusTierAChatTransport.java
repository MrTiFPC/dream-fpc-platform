package org.l2jmobius.gameserver.fakeplayer.platform.mobius;

import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.util.Broadcast;
import org.l2jmobius.gameserver.fakeplayer.platform.FakePlayerChatTransport;

public class MobiusTierAChatTransport implements FakePlayerChatTransport
{
	private static final long WHISPER_MIN_DELAY_MS = 1400L;
	private static final long WHISPER_MAX_DELAY_MS = 3600L;
	private static final long PARTY_MIN_DELAY_MS = 450L;
	private static final long PARTY_MAX_DELAY_MS = 1400L;
	private static final long GENERAL_MIN_DELAY_MS = 1800L;
	private static final long GENERAL_MAX_DELAY_MS = 4400L;
	private static final long PUBLIC_MIN_DELAY_MS = 2300L;
	private static final long PUBLIC_MAX_DELAY_MS = 5400L;

	@Override
	public void sendOutgoingWhisperEcho(Player sender, String targetDisplayName, String text)
	{
		if ((sender == null) || (targetDisplayName == null) || targetDisplayName.isBlank())
		{
			return;
		}
		sender.sendPacket(new CreatureSay(sender, null, "->" + targetDisplayName, ChatType.WHISPER, text));
	}

	@Override
	public void sendTargetNotOnline(Player sender)
	{
		if (sender != null)
		{
			sender.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
		}
	}

	@Override
	public void sendPrivateReply(Player targetPlayer, Npc speaker, String displayName, String text)
	{
		sendPrivateReply(targetPlayer, speaker, displayName, text, false);
	}

	@Override
	public void sendPrivateReply(Player targetPlayer, Npc speaker, String displayName, String text, boolean shareLocation)
	{
		if ((targetPlayer == null) || (speaker == null))
		{
			return;
		}
		scheduleChatDelivery(ChatType.WHISPER, text, () ->
		{
			if ((targetPlayer.getClient() == null) || targetPlayer.isInOfflineMode())
			{
				return;
			}
			targetPlayer.sendPacket(new CreatureSay(speaker, ChatType.WHISPER, displayName, text, shareLocation));
		});
	}

	@Override
	public void broadcastNearbyReply(Npc speaker, String displayName, ChatType chatType, String text)
	{
		if ((speaker == null) || (chatType == null))
		{
			return;
		}
		scheduleChatDelivery(chatType, text, () -> Broadcast.toKnownPlayers(speaker, new CreatureSay(speaker, chatType, displayName, text)));
	}

	@Override
	public void broadcastPublicReply(Npc speaker, String displayName, ChatType chatType, String text)
	{
		if ((speaker == null) || (chatType == null))
		{
			return;
		}
		scheduleChatDelivery(chatType, text, () ->
		{
			final CreatureSay packet = new CreatureSay(speaker, chatType, displayName, text);
			for (Player player : World.getInstance().getPlayers())
			{
				if (player != null)
				{
					player.sendPacket(packet);
				}
			}
		});
	}

	@Override
	public void broadcastPartyReply(Party party, Npc speaker, String displayName, String text)
	{
		broadcastPartyReply(party, speaker, displayName, text, false);
	}

	@Override
	public void broadcastPartyReply(Party party, Npc speaker, String displayName, String text, boolean shareLocation)
	{
		if ((party == null) || (speaker == null))
		{
			return;
		}
		scheduleChatDelivery(ChatType.PARTY, text, () ->
		{
			final CreatureSay packet = new CreatureSay(speaker, ChatType.PARTY, displayName, text, shareLocation);
			for (Player member : party.getMembers())
			{
				if ((member != null) && (member.getClient() != null) && !member.isInOfflineMode())
				{
					member.sendPacket(packet);
				}
			}
		});
	}

	@Override
	public void broadcastClanReply(Clan clan, Npc speaker, String displayName, String text)
	{
		if ((clan == null) || (speaker == null))
		{
			return;
		}
		scheduleChatDelivery(ChatType.CLAN, text, () -> clan.broadcastToOnlineMembers(new CreatureSay(speaker, ChatType.CLAN, displayName, text)));
	}

	private void scheduleChatDelivery(ChatType chatType, String text, Runnable delivery)
	{
		if (delivery == null)
		{
			return;
		}

		final long delayMs = computeReplyDelayMs(chatType, text);
		if (delayMs <= 0L)
		{
			delivery.run();
			return;
		}
		ThreadPool.schedule(delivery, delayMs);
	}

	private long computeReplyDelayMs(ChatType chatType, String text)
	{
		final int textLength = Math.max(1, sanitizeText(text).length());
		return switch (chatType)
		{
			case WHISPER -> clampDelay(900L + (textLength * 34L) + randomBetween(500L, 1500L), WHISPER_MIN_DELAY_MS, WHISPER_MAX_DELAY_MS);
			case PARTY -> clampDelay(350L + (textLength * 20L) + randomBetween(150L, 650L), PARTY_MIN_DELAY_MS, PARTY_MAX_DELAY_MS);
			case SHOUT, WORLD -> clampDelay(1650L + (textLength * 40L) + randomBetween(850L, 2200L), PUBLIC_MIN_DELAY_MS, PUBLIC_MAX_DELAY_MS);
			case CLAN -> clampDelay(1350L + (textLength * 34L) + randomBetween(650L, 1700L), GENERAL_MIN_DELAY_MS, GENERAL_MAX_DELAY_MS);
			default -> clampDelay(1200L + (textLength * 34L) + randomBetween(600L, 1600L), GENERAL_MIN_DELAY_MS, GENERAL_MAX_DELAY_MS);
		};
	}

	private long randomBetween(long minInclusive, long maxInclusive)
	{
		if (maxInclusive <= minInclusive)
		{
			return minInclusive;
		}
		return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1L);
	}

	private long clampDelay(long value, long minValue, long maxValue)
	{
		return Math.max(minValue, Math.min(maxValue, value));
	}

	private String sanitizeText(String text)
	{
		return (text == null) ? "" : text.trim();
	}
}
