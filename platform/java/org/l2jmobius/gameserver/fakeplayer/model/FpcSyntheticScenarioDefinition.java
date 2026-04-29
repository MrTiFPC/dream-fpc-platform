package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class FpcSyntheticScenarioDefinition
{
	private final String _id;
	private final String _displayName;
	private final boolean _enabled;
	private final int _sourceQuestId;
	private final String _sourceQuestName;
	private final Set<String> _assignedFpcIds;
	private final String _stagingZoneId;
	private final String _objectiveZoneId;
	private final String _returnZoneId;
	private final Set<Integer> _targetNpcIds;
	private final int _targetCount;
	private final int _searchRadius;
	private final int _leashRadius;
	private final int _returnRadius;
	private final boolean _repeatable;
	private final String _startLine;
	private final String _progressLine;
	private final String _completeLine;
	private final String _notes;

	public FpcSyntheticScenarioDefinition(String id, String displayName, boolean enabled, int sourceQuestId, String sourceQuestName, Set<String> assignedFpcIds, String stagingZoneId, String objectiveZoneId, String returnZoneId, Set<Integer> targetNpcIds, int targetCount, int searchRadius, int leashRadius, int returnRadius, boolean repeatable, String startLine, String progressLine, String completeLine, String notes)
	{
		_id = sanitize(id);
		_displayName = sanitize(displayName);
		_enabled = enabled;
		_sourceQuestId = Math.max(0, sourceQuestId);
		_sourceQuestName = sanitize(sourceQuestName);
		_assignedFpcIds = normalizeFpcIds(assignedFpcIds);
		_stagingZoneId = sanitize(stagingZoneId);
		_objectiveZoneId = sanitize(objectiveZoneId);
		_returnZoneId = sanitize(returnZoneId);
		_targetNpcIds = Collections.unmodifiableSet(new LinkedHashSet<>((targetNpcIds == null) ? Set.of() : targetNpcIds));
		_targetCount = Math.max(1, targetCount);
		_searchRadius = Math.max(200, searchRadius);
		_leashRadius = Math.max(_searchRadius, leashRadius);
		_returnRadius = Math.max(100, returnRadius);
		_repeatable = repeatable;
		_startLine = sanitize(startLine);
		_progressLine = sanitize(progressLine);
		_completeLine = sanitize(completeLine);
		_notes = sanitize(notes);
	}

	private static String sanitize(String value)
	{
		return (value == null) ? "" : value.trim();
	}

	private static Set<String> normalizeFpcIds(Set<String> values)
	{
		if ((values == null) || values.isEmpty())
		{
			return Set.of();
		}

		final Set<String> result = new LinkedHashSet<>();
		for (String value : values)
		{
			final String normalized = sanitize(value).toLowerCase(Locale.ROOT);
			if (!normalized.isBlank())
			{
				result.add(normalized);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	public String getId()
	{
		return _id;
	}

	public String getDisplayName()
	{
		return _displayName;
	}

	public boolean isEnabled()
	{
		return _enabled;
	}

	public int getSourceQuestId()
	{
		return _sourceQuestId;
	}

	public String getSourceQuestName()
	{
		return _sourceQuestName;
	}

	public Set<String> getAssignedFpcIds()
	{
		return _assignedFpcIds;
	}

	public String getStagingZoneId()
	{
		return _stagingZoneId;
	}

	public String getObjectiveZoneId()
	{
		return _objectiveZoneId;
	}

	public String getReturnZoneId()
	{
		return _returnZoneId.isBlank() ? _objectiveZoneId : _returnZoneId;
	}

	public Set<Integer> getTargetNpcIds()
	{
		return _targetNpcIds;
	}

	public int getTargetCount()
	{
		return _targetCount;
	}

	public int getSearchRadius()
	{
		return _searchRadius;
	}

	public int getLeashRadius()
	{
		return _leashRadius;
	}

	public int getReturnRadius()
	{
		return _returnRadius;
	}

	public boolean isRepeatable()
	{
		return _repeatable;
	}

	public String getStartLine()
	{
		return _startLine;
	}

	public String getProgressLine()
	{
		return _progressLine;
	}

	public String getCompleteLine()
	{
		return _completeLine;
	}

	public String getNotes()
	{
		return _notes;
	}

	public boolean isAssignedTo(FpcDefinition definition)
	{
		return (definition != null) && _assignedFpcIds.contains(definition.getId().toLowerCase(Locale.ROOT));
	}

	public boolean matchesTargetNpcId(int npcId)
	{
		return _targetNpcIds.contains(npcId);
	}
}
