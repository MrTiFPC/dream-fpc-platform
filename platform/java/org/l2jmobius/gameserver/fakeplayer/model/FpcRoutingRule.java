package org.l2jmobius.gameserver.fakeplayer.model;

import java.util.Collection;
import java.util.List;

public class FpcRoutingRule
{
	private final String _ruleId;
	private final String _targetType;
	private final String _result;
	private final String _scope;
	private final boolean _enabled;
	private final int _priority;
	private final List<String> _anyPhrases;
	private final List<String> _allPhrases;
	private final List<String> _excludePhrases;
	private final String _notes;

	public FpcRoutingRule(String ruleId, String targetType, String result, String scope, boolean enabled, int priority, Collection<String> anyPhrases, Collection<String> allPhrases, Collection<String> excludePhrases, String notes)
	{
		_ruleId = (ruleId == null) ? "" : ruleId;
		_targetType = (targetType == null) ? "" : targetType;
		_result = (result == null) ? "" : result;
		_scope = (scope == null) ? "current_utterance" : scope;
		_enabled = enabled;
		_priority = priority;
		_anyPhrases = List.copyOf(anyPhrases == null ? List.of() : anyPhrases);
		_allPhrases = List.copyOf(allPhrases == null ? List.of() : allPhrases);
		_excludePhrases = List.copyOf(excludePhrases == null ? List.of() : excludePhrases);
		_notes = (notes == null) ? "" : notes;
	}

	public String getRuleId()
	{
		return _ruleId;
	}

	public String getTargetType()
	{
		return _targetType;
	}

	public String getResult()
	{
		return _result;
	}

	public String getScope()
	{
		return _scope;
	}

	public boolean isEnabled()
	{
		return _enabled;
	}

	public int getPriority()
	{
		return _priority;
	}

	public List<String> getAnyPhrases()
	{
		return _anyPhrases;
	}

	public List<String> getAllPhrases()
	{
		return _allPhrases;
	}

	public List<String> getExcludePhrases()
	{
		return _excludePhrases;
	}

	public String getNotes()
	{
		return _notes;
	}
}
