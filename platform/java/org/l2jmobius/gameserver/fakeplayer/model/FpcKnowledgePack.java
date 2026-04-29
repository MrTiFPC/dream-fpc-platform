package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Objects;

public class FpcKnowledgePack
{
	private final String _id;
	private final String _packType;
	private final String _domain;
	private final String _sourceTier;
	private final String _stability;
	private final String _lastChecked;
	private final String _linkedFamilies;
	private final String _summary;
	private final String _facts;
	private final String _heuristics;
	private final String _scope;
	private final String _sourceRefs;

	public FpcKnowledgePack(String id, String packType, String domain, String sourceTier, String stability, String lastChecked, String linkedFamilies, String summary, String facts, String heuristics, String scope, String sourceRefs)
	{
		_id = Objects.requireNonNull(id);
		_packType = Objects.requireNonNull(packType);
		_domain = Objects.requireNonNull(domain);
		_sourceTier = Objects.requireNonNull(sourceTier);
		_stability = Objects.requireNonNull(stability);
		_lastChecked = Objects.requireNonNull(lastChecked);
		_linkedFamilies = Objects.requireNonNull(linkedFamilies);
		_summary = Objects.requireNonNull(summary);
		_facts = Objects.requireNonNull(facts);
		_heuristics = Objects.requireNonNull(heuristics);
		_scope = Objects.requireNonNull(scope);
		_sourceRefs = Objects.requireNonNull(sourceRefs);
	}

	public String getId()
	{
		return _id;
	}

	public String getPackType()
	{
		return _packType;
	}

	public String getDomain()
	{
		return _domain;
	}

	public String getSourceTier()
	{
		return _sourceTier;
	}

	public String getStability()
	{
		return _stability;
	}

	public String getLastChecked()
	{
		return _lastChecked;
	}

	public String getLinkedFamilies()
	{
		return _linkedFamilies;
	}

	public String getSummary()
	{
		return _summary;
	}

	public String getFacts()
	{
		return _facts;
	}

	public String getHeuristics()
	{
		return _heuristics;
	}

	public String getScope()
	{
		return _scope;
	}

	public String getSourceRefs()
	{
		return _sourceRefs;
	}
}
