package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.List;
import java.util.Objects;

public final class FpcMemoryBundle
{
	public static final FpcMemoryBundle EMPTY = new FpcMemoryBundle(FpcRelationshipSnapshot.EMPTY, FpcSocialSnapshot.EMPTY, "", "", "", "", "", "", "", "", "", List.of());

	private final FpcRelationshipSnapshot _relationshipSnapshot;
	private final FpcSocialSnapshot _socialSnapshot;
	private final String _recentConversationSummary;
	private final String _salientMemorySummary;
	private final String _retrievedMemorySummary;
	private final String _memoryPrioritySummary;
	private final String _memoryReflectionSummary;
	private final String _memoryTrustSummary;
	private final String _memoryRepairSummary;
	private final String _memoryPressureSummary;
	private final String _memorySelectionSummary;
	private final List<FpcRetrievedMemorySection> _retrievalSections;

	public FpcMemoryBundle(FpcRelationshipSnapshot relationshipSnapshot, FpcSocialSnapshot socialSnapshot, String recentConversationSummary, String salientMemorySummary, String retrievedMemorySummary, String memoryPrioritySummary, String memoryReflectionSummary, String memoryTrustSummary, String memoryRepairSummary, String memoryPressureSummary, String memorySelectionSummary, List<FpcRetrievedMemorySection> retrievalSections)
	{
		_relationshipSnapshot = (relationshipSnapshot == null) ? FpcRelationshipSnapshot.EMPTY : relationshipSnapshot;
		_socialSnapshot = (socialSnapshot == null) ? FpcSocialSnapshot.EMPTY : socialSnapshot;
		_recentConversationSummary = Objects.requireNonNullElse(recentConversationSummary, "");
		_salientMemorySummary = Objects.requireNonNullElse(salientMemorySummary, "");
		_retrievedMemorySummary = Objects.requireNonNullElse(retrievedMemorySummary, "");
		_memoryPrioritySummary = Objects.requireNonNullElse(memoryPrioritySummary, "");
		_memoryReflectionSummary = Objects.requireNonNullElse(memoryReflectionSummary, "");
		_memoryTrustSummary = Objects.requireNonNullElse(memoryTrustSummary, "");
		_memoryRepairSummary = Objects.requireNonNullElse(memoryRepairSummary, "");
		_memoryPressureSummary = Objects.requireNonNullElse(memoryPressureSummary, "");
		_memorySelectionSummary = Objects.requireNonNullElse(memorySelectionSummary, "");
		_retrievalSections = List.copyOf(Objects.requireNonNullElse(retrievalSections, List.of()));
	}

	public FpcRelationshipSnapshot getRelationshipSnapshot()
	{
		return _relationshipSnapshot;
	}

	public FpcSocialSnapshot getSocialSnapshot()
	{
		return _socialSnapshot;
	}

	public String getRecentConversationSummary()
	{
		return _recentConversationSummary;
	}

	public String getSalientMemorySummary()
	{
		return _salientMemorySummary;
	}

	public String getRetrievedMemorySummary()
	{
		return _retrievedMemorySummary;
	}

	public String getMemoryPrioritySummary()
	{
		return _memoryPrioritySummary;
	}

	public String getMemoryReflectionSummary()
	{
		return _memoryReflectionSummary;
	}

	public String getMemoryTrustSummary()
	{
		return _memoryTrustSummary;
	}

	public String getMemoryRepairSummary()
	{
		return _memoryRepairSummary;
	}

	public String getMemoryPressureSummary()
	{
		return _memoryPressureSummary;
	}

	public String getMemorySelectionSummary()
	{
		return _memorySelectionSummary;
	}

	public List<FpcRetrievedMemorySection> getRetrievalSections()
	{
		return _retrievalSections;
	}

	public boolean hasMeaningfulMemory()
	{
		return _relationshipSnapshot.hasMeaningfulHistory() || _socialSnapshot.hasMeaningfulHistory() || !_recentConversationSummary.isBlank() || !_salientMemorySummary.isBlank() || !_retrievedMemorySummary.isBlank() || !_memoryPrioritySummary.isBlank() || !_memoryReflectionSummary.isBlank() || !_memoryTrustSummary.isBlank() || !_memoryRepairSummary.isBlank() || !_memoryPressureSummary.isBlank() || !_memorySelectionSummary.isBlank() || !_retrievalSections.isEmpty();
	}
}
