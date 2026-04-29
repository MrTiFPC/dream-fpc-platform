package org.l2jmobius.gameserver.fakeplayer.model;

public class FpcConversationGuardAssessment
{
	public static final String CATEGORY_NONE = "none";
	public static final String CATEGORY_META_OOC = "meta_ooc";
	public static final String CATEGORY_UNSUPPORTED_REQUEST = "unsupported_request";

	public static final FpcConversationGuardAssessment NONE = new FpcConversationGuardAssessment(CATEGORY_NONE, "", "", false, false);

	private final String _category;
	private final String _domain;
	private final String _knowledgeHint;
	private final boolean _mixedKnowledgeRedirect;
	private final boolean _sensitiveFollowUp;

	private FpcConversationGuardAssessment(String category, String domain, String knowledgeHint, boolean mixedKnowledgeRedirect, boolean sensitiveFollowUp)
	{
		_category = (category == null) ? CATEGORY_NONE : category;
		_domain = (domain == null) ? "" : domain;
		_knowledgeHint = (knowledgeHint == null) ? "" : knowledgeHint;
		_mixedKnowledgeRedirect = mixedKnowledgeRedirect;
		_sensitiveFollowUp = sensitiveFollowUp;
	}

	public static FpcConversationGuardAssessment meta(String domain, boolean sensitiveFollowUp)
	{
		return new FpcConversationGuardAssessment(CATEGORY_META_OOC, domain, "", false, sensitiveFollowUp);
	}

	public static FpcConversationGuardAssessment unsupported(String domain, String knowledgeHint, boolean mixedKnowledgeRedirect, boolean sensitiveFollowUp)
	{
		return new FpcConversationGuardAssessment(CATEGORY_UNSUPPORTED_REQUEST, domain, knowledgeHint, mixedKnowledgeRedirect, sensitiveFollowUp);
	}

	public String getCategory()
	{
		return _category;
	}

	public String getDomain()
	{
		return _domain;
	}

	public String getKnowledgeHint()
	{
		return _knowledgeHint;
	}

	public boolean isMixedKnowledgeRedirect()
	{
		return _mixedKnowledgeRedirect;
	}

	public boolean isSensitiveFollowUp()
	{
		return _sensitiveFollowUp;
	}

	public boolean isSensitive()
	{
		return !_category.equals(CATEGORY_NONE);
	}

	public boolean suppressesSocialSignal()
	{
		return isSensitive();
	}

	public boolean suppressesPositiveMemory()
	{
		return isSensitive();
	}

	public boolean suppressesConversationCarry()
	{
		return isSensitive();
	}

	public String getHistoryKnowledgeType()
	{
		if (!_knowledgeHint.isBlank())
		{
			return _knowledgeHint;
		}
		return isSensitive() ? _category : "unknown";
	}

	public String getHistoryTopic()
	{
		if (!_domain.isBlank())
		{
			return _domain;
		}
		return _category;
	}

	public String describeForLog()
	{
		if (!isSensitive())
		{
			return "none";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(_category);
		if (!_domain.isBlank())
		{
			sb.append('/').append(_domain);
		}
		if (_mixedKnowledgeRedirect)
		{
			sb.append("/mixed");
		}
		if (_sensitiveFollowUp)
		{
			sb.append("/followup");
		}
		return sb.toString();
	}

	public static boolean isSensitiveCategory(String category)
	{
		return CATEGORY_META_OOC.equalsIgnoreCase(category) || CATEGORY_UNSUPPORTED_REQUEST.equalsIgnoreCase(category);
	}
}
