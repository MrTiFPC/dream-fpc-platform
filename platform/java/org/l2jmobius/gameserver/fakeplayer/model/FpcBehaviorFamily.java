package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public class FpcBehaviorFamily
{
	private final String _id;
	private final String _linkedPersonaTemplates;
	private final String _linkedArchetypes;
	private final String _summary;
	private final String _motivationWeights;
	private final String _temperamentProfile;
	private final String _socialEtiquette;
	private final String _playstyleFlags;
	private final String _tacticalPatterns;
	private final String _speechProfile;
	private final String _memoryStyle;
	private final String _notes;
	private final String _callbackStyle;
	private final String _conflictStyle;
	private final String _repairCadence;

	public FpcBehaviorFamily(String id, String linkedPersonaTemplates, String linkedArchetypes, String summary, String motivationWeights, String temperamentProfile, String socialEtiquette, String playstyleFlags, String tacticalPatterns, String speechProfile, String memoryStyle, String notes, String callbackStyle, String conflictStyle, String repairCadence)
	{
		_id = Objects.requireNonNull(id);
		_linkedPersonaTemplates = Objects.requireNonNull(linkedPersonaTemplates);
		_linkedArchetypes = Objects.requireNonNull(linkedArchetypes);
		_summary = Objects.requireNonNull(summary);
		_motivationWeights = Objects.requireNonNull(motivationWeights);
		_temperamentProfile = Objects.requireNonNull(temperamentProfile);
		_socialEtiquette = Objects.requireNonNull(socialEtiquette);
		_playstyleFlags = Objects.requireNonNull(playstyleFlags);
		_tacticalPatterns = Objects.requireNonNull(tacticalPatterns);
		_speechProfile = Objects.requireNonNull(speechProfile);
		_memoryStyle = Objects.requireNonNull(memoryStyle);
		_notes = Objects.requireNonNull(notes);
		_callbackStyle = Objects.requireNonNull(callbackStyle);
		_conflictStyle = Objects.requireNonNull(conflictStyle);
		_repairCadence = Objects.requireNonNull(repairCadence);
	}

	public String getId()
	{
		return _id;
	}

	public String getLinkedPersonaTemplates()
	{
		return _linkedPersonaTemplates;
	}

	public String getLinkedArchetypes()
	{
		return _linkedArchetypes;
	}

	public String getSummary()
	{
		return _summary;
	}

	public String getMotivationWeights()
	{
		return _motivationWeights;
	}

	public String getTemperamentProfile()
	{
		return _temperamentProfile;
	}

	public String getSocialEtiquette()
	{
		return _socialEtiquette;
	}

	public String getPlaystyleFlags()
	{
		return _playstyleFlags;
	}

	public String getTacticalPatterns()
	{
		return _tacticalPatterns;
	}

	public String getSpeechProfile()
	{
		return _speechProfile;
	}

	public String getMemoryStyle()
	{
		return _memoryStyle;
	}

	public String getNotes()
	{
		return _notes;
	}

	public String getCallbackStyle()
	{
		return _callbackStyle;
	}

	public String getConflictStyle()
	{
		return _conflictStyle;
	}

	public String getRepairCadence()
	{
		return _repairCadence;
	}
}
