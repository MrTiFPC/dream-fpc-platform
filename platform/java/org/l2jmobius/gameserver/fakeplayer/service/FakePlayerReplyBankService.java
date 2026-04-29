package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerReplyBankData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplyBankEntry;
import org.l2jmobius.gameserver.fakeplayer.model.FpcReplyBankSelection;

public class FakePlayerReplyBankService
{
	private final FakePlayerReplyBankData _data;
	private final FakePlayerSelectionMatchSupport _matchSupport;

	public FakePlayerReplyBankService(FakePlayerReplyBankData data, FakePlayerMessageClassifier classifier)
	{
		_data = data;
		_matchSupport = new FakePlayerSelectionMatchSupport(classifier);
	}

	public FpcReplyBankSelection select(String fakePlayerId, String audienceName, String channel, String messageCategory, String routeLane, String incomingText)
	{
		final List<ScoredEntry> scoredEntries = new ArrayList<>();
		final String normalizedAudience = _matchSupport.normalize(audienceName);
		final String normalizedChannel = _matchSupport.normalize(channel);
		final String normalizedCategory = _matchSupport.normalize(messageCategory);
		final String normalizedRouteLane = _matchSupport.normalize(routeLane);
		final String normalizedText = _matchSupport.normalizeText(incomingText);

		addMatches(scoredEntries, _data.getEntriesForFpc(fakePlayerId), true, normalizedAudience, normalizedChannel, normalizedCategory, normalizedRouteLane, normalizedText);
		addMatches(scoredEntries, _data.getDefaultEntries(), false, normalizedAudience, normalizedChannel, normalizedCategory, normalizedRouteLane, normalizedText);
		if (scoredEntries.isEmpty())
		{
			return FpcReplyBankSelection.EMPTY;
		}

		scoredEntries.sort(Comparator.comparingInt(ScoredEntry::score).reversed());

		final Set<String> candidateLines = new LinkedHashSet<>();
		final StringBuilder summary = new StringBuilder();
		int summaryCount = 0;
		for (ScoredEntry scoredEntry : scoredEntries)
		{
			final String line = scoredEntry.entry().getLine();
			if ((line == null) || line.isBlank())
			{
				continue;
			}
			candidateLines.add(line.trim());
			if (summaryCount < 4)
			{
				if (summaryCount > 0)
				{
					summary.append(" ");
				}
				summary.append("[").append(scoredEntry.entry().getBankType()).append("/").append(scoredEntry.entry().getCategory()).append("] ").append(cleanSummaryLine(line));
				summaryCount++;
			}
			if (candidateLines.size() >= 6)
			{
				break;
			}
		}

		return candidateLines.isEmpty() ? FpcReplyBankSelection.EMPTY : new FpcReplyBankSelection(summary.toString(), List.copyOf(candidateLines));
	}

	private void addMatches(List<ScoredEntry> scoredEntries, List<FpcReplyBankEntry> entries, boolean exactFpc, String normalizedAudience, String normalizedChannel, String normalizedCategory, String normalizedRouteLane, String normalizedText)
	{
		for (FpcReplyBankEntry entry : entries)
		{
			final boolean triggerMatch = _matchSupport.matchesPipeTriggers(entry.getTriggers(), normalizedText);
			if (!_matchSupport.matchesChannel(entry.getChannel(), normalizedChannel) || !_matchSupport.matchesAudience(entry.getAudience(), normalizedAudience))
			{
				continue;
			}
			if (!triggerMatch && !matchesCategory(entry.getCategory(), normalizedCategory))
			{
				continue;
			}
			if ("knowledge".equals(normalizedRouteLane) && !triggerMatch)
			{
				continue;
			}

			int score = entry.getPriority();
			score += exactFpc ? 1000 : 100;
			score += _matchSupport.audienceScore(entry.getAudience(), normalizedAudience);
			score += _matchSupport.channelScore(entry.getChannel(), normalizedChannel);
			score += categoryScore(entry.getCategory(), normalizedCategory);
			score += triggerMatch ? 180 : 0;
			if ("specialty".equalsIgnoreCase(entry.getBankType()) || "quest_story".equalsIgnoreCase(entry.getBankType()))
			{
				score += 40;
			}
			scoredEntries.add(new ScoredEntry(entry, score));
		}
	}

	private boolean matchesCategory(String entryCategory, String normalizedCategory)
	{
		final String category = _matchSupport.normalize(entryCategory);
		return category.isBlank() || "any".equals(category) || category.equals(normalizedCategory);
	}

	private int categoryScore(String entryCategory, String normalizedCategory)
	{
		final String category = _matchSupport.normalize(entryCategory);
		if (category.isBlank() || "any".equals(category))
		{
			return 0;
		}
		return category.equals(normalizedCategory) ? 120 : 0;
	}

	private String cleanSummaryLine(String line)
	{
		final String cleaned = line.trim().replaceAll("\\s+", " ");
		if (cleaned.length() <= 90)
		{
			return cleaned;
		}
		int cut = cleaned.lastIndexOf(' ', 90);
		if (cut <= 0)
		{
			cut = 90;
		}
		return cleaned.substring(0, cut).trim() + "...";
	}

	private static record ScoredEntry(FpcReplyBankEntry entry, int score)
	{
	}
}
