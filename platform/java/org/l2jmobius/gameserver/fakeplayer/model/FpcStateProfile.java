package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public class FpcStateProfile
{
	private final String _id;
	private final String _linkedFpcIds;
	private final String _linkedFamilies;
	private final String _summary;
	private final String _personalitySliders;
	private final String _activeNeeds;
	private final String _goalSlots;
	private final String _memoryWindows;
	private final String _relationshipAxes;
	private final String _valueRules;
	private final String _emotionalAxes;
	private final String _notes;
	private final String _stateModes;
	private final String _reflectionPolicy;
	private final String _memoryRetrievalPolicy;
	private final String _goalPersistencePolicy;
	private final String _emotionTransitionRules;

	public FpcStateProfile(String id, String linkedFpcIds, String linkedFamilies, String summary, String personalitySliders, String activeNeeds, String goalSlots, String memoryWindows, String relationshipAxes, String valueRules, String emotionalAxes, String notes, String stateModes, String reflectionPolicy, String memoryRetrievalPolicy, String goalPersistencePolicy, String emotionTransitionRules)
	{
		_id = Objects.requireNonNull(id);
		_linkedFpcIds = Objects.requireNonNull(linkedFpcIds);
		_linkedFamilies = Objects.requireNonNull(linkedFamilies);
		_summary = Objects.requireNonNull(summary);
		_personalitySliders = Objects.requireNonNull(personalitySliders);
		_activeNeeds = Objects.requireNonNull(activeNeeds);
		_goalSlots = Objects.requireNonNull(goalSlots);
		_memoryWindows = Objects.requireNonNull(memoryWindows);
		_relationshipAxes = Objects.requireNonNull(relationshipAxes);
		_valueRules = Objects.requireNonNull(valueRules);
		_emotionalAxes = Objects.requireNonNull(emotionalAxes);
		_notes = Objects.requireNonNull(notes);
		_stateModes = Objects.requireNonNull(stateModes);
		_reflectionPolicy = Objects.requireNonNull(reflectionPolicy);
		_memoryRetrievalPolicy = Objects.requireNonNull(memoryRetrievalPolicy);
		_goalPersistencePolicy = Objects.requireNonNull(goalPersistencePolicy);
		_emotionTransitionRules = Objects.requireNonNull(emotionTransitionRules);
	}

	public String getId()
	{
		return _id;
	}

	public String getLinkedFpcIds()
	{
		return _linkedFpcIds;
	}

	public String getLinkedFamilies()
	{
		return _linkedFamilies;
	}

	public String getSummary()
	{
		return _summary;
	}

	public String getPersonalitySliders()
	{
		return _personalitySliders;
	}

	public String getActiveNeeds()
	{
		return _activeNeeds;
	}

	public String getGoalSlots()
	{
		return _goalSlots;
	}

	public String getMemoryWindows()
	{
		return _memoryWindows;
	}

	public String getRelationshipAxes()
	{
		return _relationshipAxes;
	}

	public String getValueRules()
	{
		return _valueRules;
	}

	public String getEmotionalAxes()
	{
		return _emotionalAxes;
	}

	public String getNotes()
	{
		return _notes;
	}

	public String getStateModes()
	{
		return _stateModes;
	}

	public String getReflectionPolicy()
	{
		return _reflectionPolicy;
	}

	public String getMemoryRetrievalPolicy()
	{
		return _memoryRetrievalPolicy;
	}

	public String getGoalPersistencePolicy()
	{
		return _goalPersistencePolicy;
	}

	public String getEmotionTransitionRules()
	{
		return _emotionTransitionRules;
	}
}
