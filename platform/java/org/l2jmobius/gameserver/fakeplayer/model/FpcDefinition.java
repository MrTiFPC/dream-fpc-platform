package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcDefinition
{
	private final String _id;
	private final String _name;
	private final String _title;
	private final boolean _enabled;
	private final String _category;
	private final String _archetype;
	private final String _personaTemplate;
	private final String _carrierTemplate;
	private final int _carrierNpcId;
	private final boolean _autoSpawnOnBoot;
	private final FpcSpawnProfile _spawnProfile;
	private final FpcChannelProfile _channelProfile;
	private final boolean _talkable;
	private final String _fallbackProfile;
	private final String _availabilityProfile;
	private final String _fpcTag;
	private final String _presentationMode;
	private final String _carrierPolicy;
	private final String _presentationCarrierTemplate;
	private final int _presentationCarrierNpcId;
	private final String _speciesTag;
	private final String _familyTag;
	private final FpcAdventurerProfile _adventurerProfile;
	private final FpcCompanionProfile _companionProfile;
	
	public FpcDefinition(String id, String name, String title, boolean enabled, String category, String archetype, String personaTemplate, String carrierTemplate, int carrierNpcId, boolean autoSpawnOnBoot, FpcSpawnProfile spawnProfile, FpcChannelProfile channelProfile, boolean talkable, String fallbackProfile, String availabilityProfile, String fpcTag, String presentationMode, String carrierPolicy, String presentationCarrierTemplate, int presentationCarrierNpcId, String speciesTag, String familyTag, FpcAdventurerProfile adventurerProfile)
	{
		this(id, name, title, enabled, category, archetype, personaTemplate, carrierTemplate, carrierNpcId, autoSpawnOnBoot, spawnProfile, channelProfile, talkable, fallbackProfile, availabilityProfile, fpcTag, presentationMode, carrierPolicy, presentationCarrierTemplate, presentationCarrierNpcId, speciesTag, familyTag, adventurerProfile, new FpcCompanionProfile("none", "default", "none", 0L, false, "none", "default"));
	}

	public FpcDefinition(String id, String name, String title, boolean enabled, String category, String archetype, String personaTemplate, String carrierTemplate, int carrierNpcId, boolean autoSpawnOnBoot, FpcSpawnProfile spawnProfile, FpcChannelProfile channelProfile, boolean talkable, String fallbackProfile, String availabilityProfile, String fpcTag, String presentationMode, String carrierPolicy, String presentationCarrierTemplate, int presentationCarrierNpcId, String speciesTag, String familyTag, FpcAdventurerProfile adventurerProfile, FpcCompanionProfile companionProfile)
	{
		_id = id;
		_name = name;
		_title = title;
		_enabled = enabled;
		_category = category;
		_archetype = archetype;
		_personaTemplate = personaTemplate;
		_carrierTemplate = carrierTemplate;
		_carrierNpcId = carrierNpcId;
		_autoSpawnOnBoot = autoSpawnOnBoot;
		_spawnProfile = spawnProfile;
		_channelProfile = channelProfile;
		_talkable = talkable;
		_fallbackProfile = fallbackProfile;
		_availabilityProfile = availabilityProfile;
		_fpcTag = fpcTag;
		_presentationMode = presentationMode;
		_carrierPolicy = carrierPolicy;
		_presentationCarrierTemplate = presentationCarrierTemplate;
		_presentationCarrierNpcId = presentationCarrierNpcId;
		_speciesTag = speciesTag;
		_familyTag = familyTag;
		_adventurerProfile = adventurerProfile;
		_companionProfile = (companionProfile == null) ? new FpcCompanionProfile("none", "default", "none", 0L, false, "none", "default") : companionProfile;
	}
	
	public String getId()
	{
		return _id;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public String getTitle()
	{
		return _title;
	}
	
	public boolean isEnabled()
	{
		return _enabled;
	}
	
	public String getCategory()
	{
		return _category;
	}
	
	public String getArchetype()
	{
		return _archetype;
	}
	
	public String getPersonaTemplate()
	{
		return _personaTemplate;
	}
	
	public String getCarrierTemplate()
	{
		return _carrierTemplate;
	}
	
	public int getCarrierNpcId()
	{
		return _carrierNpcId;
	}
	
	public boolean isAutoSpawnOnBoot()
	{
		return _autoSpawnOnBoot;
	}
	
	public FpcSpawnProfile getSpawnProfile()
	{
		return _spawnProfile;
	}
	
	public FpcChannelProfile getChannelProfile()
	{
		return _channelProfile;
	}
	
	public boolean isTalkable()
	{
		return _talkable;
	}
	
	public String getFallbackProfile()
	{
		return _fallbackProfile;
	}
	
	public String getAvailabilityProfile()
	{
		return _availabilityProfile;
	}
	
	public String getFpcTag()
	{
		return _fpcTag;
	}
	
	public String getPresentationMode()
	{
		return _presentationMode;
	}
	
	public String getCarrierPolicy()
	{
		return _carrierPolicy;
	}
	
	public String getPresentationCarrierTemplate()
	{
		return _presentationCarrierTemplate;
	}
	
	public int getPresentationCarrierNpcId()
	{
		return _presentationCarrierNpcId;
	}
	
	public String getSpeciesTag()
	{
		return _speciesTag;
	}
	
	public String getFamilyTag()
	{
		return _familyTag;
	}

	public FpcAdventurerProfile getAdventurerProfile()
	{
		return _adventurerProfile;
	}

	public FpcCompanionProfile getCompanionProfile()
	{
		return _companionProfile;
	}
	
	public boolean isPlayerLikePresentation()
	{
		return "player_like".equalsIgnoreCase(_presentationMode);
	}
	
	public boolean isQuestNpcLikePresentation()
	{
		return "quest_npc_like".equalsIgnoreCase(_presentationMode);
	}
	
	public boolean isStoryNpcLikePresentation()
	{
		return "story_npc_like".equalsIgnoreCase(_presentationMode);
	}
	
	public boolean isFixedCarrierPolicy()
	{
		return (_carrierPolicy == null) || _carrierPolicy.isBlank() || "fixed".equalsIgnoreCase(_carrierPolicy);
	}
	
	public boolean isPresentationDrivenCarrierPolicy()
	{
		return "presentation_driven".equalsIgnoreCase(_carrierPolicy);
	}
}
