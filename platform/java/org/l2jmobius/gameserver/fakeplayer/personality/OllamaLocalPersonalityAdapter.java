package org.l2jmobius.gameserver.fakeplayer.personality;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;

/**
 * Advisory-only adapter for a local Ollama-backed personality sidecar.
 */
public class OllamaLocalPersonalityAdapter implements LocalPersonalityAdapter
{
	private static final Logger LOGGER = Logger.getLogger(OllamaLocalPersonalityAdapter.class.getName());
	private static final long SIDECAR_FAILURE_LOG_INTERVAL_MS = 60000L;
	
	private final FakePlayerConfig _config;
	private final HttpClient _httpClient;
	private volatile long _lastHealthProbeTimeMs;
	private volatile boolean _lastHealthProbeHealthy = true;
	private volatile long _lastSidecarFailureLogTimeMs;
	private volatile String _lastSidecarFailureDetail = "";
	
	public OllamaLocalPersonalityAdapter(FakePlayerConfig config)
	{
		_config = config;
		_httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(_config.getPersonalityTimeoutMs())).build();
	}
	
	@Override
	public PersonalityDecision decide(PersonalityContext context)
	{
		if (!isHealthy())
		{
			logUnavailableFallback(context);
			return PersonalityDecision.DEFAULT;
		}
		
		boolean transportFailure = false;
		for (int attempt = 0; attempt <= _config.getPersonalityMaxRetries(); attempt++)
		{
			try
			{
				final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(_config.getPersonalityEndpoint())).version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofMillis(_config.getPersonalityTimeoutMs())).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(context))).build();
				final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				
				if (response.statusCode() != 200)
				{
					LOGGER.warning(() -> "OllamaLocalPersonalityAdapter: Sidecar returned status " + response.statusCode() + " for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope());
					continue;
				}
				
				final String responseBody = response.body();
				final PersonalityDecision parsed = parseResponse(responseBody);
				if (parsed != null)
				{
					return parsed;
				}
				LOGGER.warning(() -> "OllamaLocalPersonalityAdapter: Unable to parse sidecar response for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope());
			}
			catch (IOException e)
			{
				transportFailure = true;
				recordSidecarFailure("personality request", _config.getPersonalityEndpoint(), describeFailure(e));
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				transportFailure = true;
				recordSidecarFailure("personality request", _config.getPersonalityEndpoint(), "interrupted");
				break;
			}
			catch (RuntimeException e)
			{
				LOGGER.log(Level.WARNING, "OllamaLocalPersonalityAdapter: Unexpected failure for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope(), e);
			}
		}
		
		if (transportFailure)
		{
			LOGGER.fine(() -> "OllamaLocalPersonalityAdapter: Falling back to PersonalityDecision.DEFAULT after sidecar transport failure for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope());
		}
		else
		{
			LOGGER.warning("OllamaLocalPersonalityAdapter: Falling back to PersonalityDecision.DEFAULT for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope());
		}
		return PersonalityDecision.DEFAULT;
	}
	
	@Override
	public boolean isHealthy()
	{
		final long now = System.currentTimeMillis();
		if ((now - _lastHealthProbeTimeMs) < Math.max(_config.getPersonalityHealthCacheMs(), 1000))
		{
			return _lastHealthProbeHealthy;
		}
		
		synchronized (this)
		{
			final long checkNow = System.currentTimeMillis();
			if ((checkNow - _lastHealthProbeTimeMs) < Math.max(_config.getPersonalityHealthCacheMs(), 1000))
			{
				return _lastHealthProbeHealthy;
			}
			
			final boolean wasHealthy = _lastHealthProbeHealthy;
			final boolean healthy = probeHealth();
			_lastHealthProbeTimeMs = checkNow;
			_lastHealthProbeHealthy = healthy;
			if (healthy && !wasHealthy)
			{
				_lastSidecarFailureDetail = "";
				LOGGER.info("OllamaLocalPersonalityAdapter: Sidecar health restored at " + _config.getPersonalityHealthEndpoint());
			}
			return healthy;
		}
	}
	
	private boolean probeHealth()
	{
		try
		{
			final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(_config.getPersonalityHealthEndpoint())).version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofMillis(_config.getPersonalityTimeoutMs())).GET().build();
			
			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				recordSidecarFailure("health probe", _config.getPersonalityHealthEndpoint(), "HTTP status " + response.statusCode());
				return false;
			}
			
			final String body = response.body();
			final boolean healthy = body.contains("\"status\"") && body.contains("\"ok\"");
			if (!healthy)
			{
				recordSidecarFailure("health probe", _config.getPersonalityHealthEndpoint(), "unexpected health payload");
			}
			return healthy;
		}
		catch (IOException e)
		{
			recordSidecarFailure("health probe", _config.getPersonalityHealthEndpoint(), describeFailure(e));
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			recordSidecarFailure("health probe", _config.getPersonalityHealthEndpoint(), "interrupted");
		}
		catch (RuntimeException e)
		{
			LOGGER.log(Level.WARNING, "OllamaLocalPersonalityAdapter: Unexpected failure during sidecar health probe", e);
		}
		return false;
	}

	private void logUnavailableFallback(PersonalityContext context)
	{
		if (LOGGER.isLoggable(Level.FINE))
		{
			LOGGER.fine(() -> "OllamaLocalPersonalityAdapter: Sidecar unavailable; using PersonalityDecision.DEFAULT for fake player " + context.getFakePlayerId() + " scope=" + context.getDecisionScope() + lastFailureSuffix());
		}
	}

	private void recordSidecarFailure(String phase, String endpoint, String detail)
	{
		final String normalizedDetail = ((detail == null) || detail.isBlank()) ? "unavailable" : detail;
		_lastSidecarFailureDetail = phase + " " + normalizedDetail;
		final long now = System.currentTimeMillis();
		if ((now - _lastSidecarFailureLogTimeMs) >= SIDECAR_FAILURE_LOG_INTERVAL_MS)
		{
			_lastSidecarFailureLogTimeMs = now;
			LOGGER.warning("OllamaLocalPersonalityAdapter: Sidecar " + phase + " unavailable at " + endpoint + " (" + normalizedDetail + "); using local fallback decisions until it recovers.");
		}
		else if (LOGGER.isLoggable(Level.FINE))
		{
			LOGGER.fine(() -> "OllamaLocalPersonalityAdapter: Suppressed repeated sidecar " + phase + " failure at " + endpoint + " (" + normalizedDetail + ").");
		}
	}

	private String lastFailureSuffix()
	{
		final String detail = _lastSidecarFailureDetail;
		return ((detail == null) || detail.isBlank()) ? "" : " lastFailure=" + detail;
	}

	private static String describeFailure(Throwable failure)
	{
		if (failure == null)
		{
			return "unavailable";
		}
		final StringBuilder detail = new StringBuilder(failure.getClass().getSimpleName());
		if ((failure.getMessage() != null) && !failure.getMessage().isBlank())
		{
			detail.append(": ").append(failure.getMessage());
		}
		final Throwable cause = failure.getCause();
		if ((cause != null) && (cause != failure))
		{
			detail.append("; cause=").append(cause.getClass().getSimpleName());
			if ((cause.getMessage() != null) && !cause.getMessage().isBlank())
			{
				detail.append(": ").append(cause.getMessage());
			}
		}
		return detail.toString();
	}
	
	private String buildRequestBody(PersonalityContext context)
	{
		return "{" + quote("fakePlayerId") + ":" + quote(context.getFakePlayerId()) + "," + quote("archetype") + ":" + quote(context.getArchetype()) + "," + quote("personaTemplate") + ":" + quote(safe(context.getPersonaTemplate())) + "," + quote("speciesTag") + ":" + quote(safe(context.getSpeciesTag())) + "," + quote("familyTag") + ":" + quote(safe(context.getFamilyTag())) + "," + quote("responseStyle") + ":" + quote(safe(context.getResponseStyle())) + "," + quote("playerStance") + ":" + quote(safe(context.getPlayerStance())) + "," + quote("coreNeed") + ":" + quote(safe(context.getCoreNeed())) + "," + quote("personaSummary") + ":" + quote(safe(context.getPersonaSummary())) + "," + quote("selfKnowledgeSummary") + ":" + quote(safe(context.getSelfKnowledgeSummary())) + "," + quote("publicMaskSummary") + ":" + quote(safe(context.getPublicMaskSummary())) + "," + quote("hiddenTruthSummary") + ":" + quote(safe(context.getHiddenTruthSummary())) + "," + quote("selfConcept") + ":" + quote(safe(context.getSelfConcept())) + "," + quote("coreWound") + ":" + quote(safe(context.getCoreWound())) + "," + quote("coreDesire") + ":" + quote(safe(context.getCoreDesire())) + "," + quote("loyaltyAnchor") + ":" + quote(safe(context.getLoyaltyAnchor())) + "," + quote("resentmentAnchor") + ":" + quote(safe(context.getResentmentAnchor())) + "," + quote("privateTaboo") + ":" + quote(safe(context.getPrivateTaboo())) + "," + quote("speechAnchor") + ":" + quote(safe(context.getSpeechAnchor())) + "," + quote("privateContradiction") + ":" + quote(safe(context.getPrivateContradiction())) + "," + quote("guidancePosture") + ":" + quote(safe(context.getGuidancePosture())) + "," + quote("guidancePriority") + ":" + quote(safe(context.getGuidancePriority())) + "," + quote("uncertaintyStyle") + ":" + quote(safe(context.getUncertaintyStyle())) + "," + quote("recommendationStyle") + ":" + quote(safe(context.getRecommendationStyle())) + "," + quote("riskStyle") + ":" + quote(safe(context.getRiskStyle())) + "," + quote("teachingStyle") + ":" + quote(safe(context.getTeachingStyle())) + "," + quote("focusEntityName") + ":" + quote(safe(context.getFocusEntityName())) + "," + quote("focusEntityType") + ":" + quote(safe(context.getFocusEntityType())) + "," + quote("focusIntent") + ":" + quote(safe(context.getFocusIntent())) + "," + quote("focusSummary") + ":" + quote(safe(context.getFocusSummary())) + "," + quote("relationshipSummary") + ":" + quote(safe(context.getRelationshipSummary())) + "," + quote("storySummary") + ":" + quote(safe(context.getStorySummary())) + "," + quote("matchedRelationshipSummary") + ":" + quote(safe(context.getMatchedRelationshipSummary())) + "," + quote("recentConversationSummary") + ":" + quote(safe(context.getRecentConversationSummary())) + "," + quote("behaviorFamilyId") + ":" + quote(safe(context.getBehaviorFamilyId())) + "," + quote("behaviorSummary") + ":" + quote(safe(context.getBehaviorSummary())) + "," + quote("behaviorRuleSummary") + ":" + quote(safe(context.getBehaviorRuleSummary())) + "," + quote("stateProfileId") + ":" + quote(safe(context.getStateProfileId())) + "," + quote("stateSummary") + ":" + quote(safe(context.getStateSummary())) + "," + quote("stateRuleSummary") + ":" + quote(safe(context.getStateRuleSummary())) + "," + quote("salientMemorySummary") + ":" + quote(safe(context.getSalientMemorySummary())) + "," + quote("retrievedMemorySummary") + ":" + quote(safe(context.getRetrievedMemorySummary())) + "," + quote("memoryPrioritySummary") + ":" + quote(safe(context.getMemoryPrioritySummary())) + "," + quote("memoryReflectionSummary") + ":" + quote(safe(context.getMemoryReflectionSummary())) + "," + quote("memoryTrustSummary") + ":" + quote(safe(context.getMemoryTrustSummary())) + "," + quote("memoryRepairSummary") + ":" + quote(safe(context.getMemoryRepairSummary())) + "," + quote("memoryPressureSummary") + ":" + quote(safe(context.getMemoryPressureSummary())) + "," + quote("memorySelectionSummary") + ":" + quote(safe(context.getMemorySelectionSummary())) + "," + quote("socialLabel") + ":" + quote(safe(context.getSocialLabel())) + "," + quote("socialSummary") + ":" + quote(safe(context.getSocialSummary())) + "," + quote("socialTrustBias") + ":" + context.getSocialTrustBias() + "," + quote("socialGuardBias") + ":" + context.getSocialGuardBias() + "," + quote("socialActionStance") + ":" + quote(safe(context.getSocialActionStance())) + "," + quote("sceneSummary") + ":" + quote(safe(context.getSceneSummary())) + "," + quote("currentRelationshipGoal") + ":" + quote(safe(context.getCurrentRelationshipGoal())) + "," + quote("currentRelationshipNeed") + ":" + quote(safe(context.getCurrentRelationshipNeed())) + "," + quote("lastRelationshipTopic") + ":" + quote(safe(context.getLastRelationshipTopic())) + "," + quote("currentZone") + ":" + quote(context.getCurrentZone()) + "," + quote("state") + ":" + quote(context.getState()) + "," + quote("hpBand") + ":" + quote(context.getHpBand()) + "," + quote("mpBand") + ":" + quote(context.getMpBand()) + "," + quote("nearbyPlayerCount") + ":" + context.getNearbyPlayerCount() + "," + quote("decisionScope") + ":" + quote(context.getDecisionScope()) + "," + quote("recentEvent") + ":" + quote(context.getRecentEvent()) + "," + quote("chatCooldownReady") + ":" + context.isChatCooldownReady() + "," + quote("teleportCooldownReady") + ":" + context.isTeleportCooldownReady() + "," + quote("allowedIntents") + ":" + quoteArray(context.getAllowedIntents()) + "," + quote("allowedZones") + ":" + quoteArray(context.getAllowedZones()) + "," + quote("availableLineStyleTags") + ":" + quoteArray(context.getAvailableLineStyleTags()) + "," + quote("availableTopicTags") + ":" + quoteArray(context.getAvailableTopicTags()) + "," + quote("messageCategory") + ":" + quote(safe(context.getMessageCategory())) + "," + quote("routeLane") + ":" + quote(safe(context.getRouteLane())) + "," + quote("routeSpeechAct") + ":" + quote(safe(context.getRouteSpeechAct())) + "," + quote("routeKnowledgeNeed") + ":" + quote(safe(context.getRouteKnowledgeNeed())) + "," + quote("routeSocialStake") + ":" + quote(safe(context.getRouteSocialStake())) + "," + quote("routeActionRequested") + ":" + context.isRouteActionRequested() + "," + quote("routePrimaryTopic") + ":" + quote(safe(context.getRoutePrimaryTopic())) + "," + quote("routeSecondaryTopics") + ":" + quoteArray(context.getRouteSecondaryTopics()) + "," + quote("routeConfidence") + ":" + clamp(context.getRouteConfidence()) + "," + quote("replyBankSummary") + ":" + quote(safe(context.getReplyBankSummary())) + "," + quote("replyBankLines") + ":" + quoteArray(context.getReplyBankLines()) + "," + quote("knowledgeType") + ":" + quote(safe(context.getKnowledgeType())) + "," + quote("knowledgeSummary") + ":" + quote(safe(context.getKnowledgeSummary())) + "," + quote("knowledgeFacts") + ":" + quoteArray(context.getKnowledgeFacts()) + "," + quote("knowledgeReplyLines") + ":" + quoteArray(context.getKnowledgeReplyLines()) + "," + quote("knowledgeHeuristicLines") + ":" + quoteArray(context.getKnowledgeHeuristicLines()) + "," + quote("knowledgeScope") + ":" + quote(safe(context.getKnowledgeScope())) + "," + quote("knowledgeConfidenceHint") + ":" + quote(safe(context.getKnowledgeConfidenceHint())) + "," + quote("lastLineTag") + ":" + quote(context.getLastLineTag()) + "," + quote("boredomScore") + ":" + clamp(context.getBoredomScore()) + "," + quote("incomingPlayerName") + ":" + quote(context.getIncomingPlayerName() == null ? "" : context.getIncomingPlayerName()) + "," + quote("incomingPlayerMessage") + ":" + quote(context.getIncomingPlayerMessage() == null ? "" : context.getIncomingPlayerMessage()) + "," + quote("defaultInnerState") + ":" + quote(safe(context.getDefaultInnerState())) + "," + quote("longTermGoal") + ":" + quote(safe(context.getLongTermGoal())) + "," + quote("socialTestStyle") + ":" + quote(safe(context.getSocialTestStyle())) + "," + quote("trustCriteria") + ":" + quote(safe(context.getTrustCriteria())) + "," + quote("repairStyle") + ":" + quote(safe(context.getRepairStyle())) + "," + quote("revealBoundary") + ":" + quote(safe(context.getRevealBoundary())) + "," + quote("reflectionLens") + ":" + quote(safe(context.getReflectionLens())) + "," + quote("activeObjective") + ":" + quote(safe(context.getActiveObjective())) + "," + quote("openLoops") + ":" + quote(safe(context.getOpenLoops())) + "," + quote("revealPressure") + ":" + quote(safe(context.getRevealPressure())) + "," + quote("relationshipPressure") + ":" + quote(safe(context.getRelationshipPressure())) + "," + quote("nextBeatHint") + ":" + quote(safe(context.getNextBeatHint())) + "," + quote("stateModes") + ":" + quote(safe(context.getStateModes())) + "," + quote("reflectionPolicy") + ":" + quote(safe(context.getReflectionPolicy())) + "," + quote("memoryRetrievalPolicy") + ":" + quote(safe(context.getMemoryRetrievalPolicy())) + "," + quote("goalPersistencePolicy") + ":" + quote(safe(context.getGoalPersistencePolicy())) + "," + quote("emotionTransitionRules") + ":" + quote(safe(context.getEmotionTransitionRules())) + "," + quote("callbackStyle") + ":" + quote(safe(context.getCallbackStyle())) + "," + quote("conflictStyle") + ":" + quote(safe(context.getConflictStyle())) + "," + quote("repairCadence") + ":" + quote(safe(context.getRepairCadence())) + "," + quote("model") + ":" + quote(_config.getPersonalityModel()) + "," + quote("temperature") + ":" + clamp(_config.getPersonalityTemperature()) + "}";
	}
	
	private PersonalityDecision parseResponse(String body)
	{
		final String moodTag = extractString(body, "moodTag");
		final String intentPreference = extractString(body, "intentPreference");
		final String zoneBias = extractString(body, "zoneBias");
		final String lineStyleTag = extractString(body, "lineStyleTag");
		final String topicTag = extractString(body, "topicTag");
		final String directReplyLine = extractString(body, "directReplyLine");
		final String socialActionTag = extractString(body, "socialActionTag");
		final String socialTargetTag = extractString(body, "socialTargetTag");
		final Double aggressionRiskScore = extractDouble(body, "aggressionRiskScore");
		final Double confidence = extractDouble(body, "confidence");
		final Double socialSignalConfidence = extractDouble(body, "socialSignalConfidence");
		final Integer socialSignalIntensity = extractInt(body, "socialSignalIntensity");
		final Boolean speakNow = extractBoolean(body, "speakNow");
		
		if ((moodTag == null) || (intentPreference == null) || (zoneBias == null) || (lineStyleTag == null) || (topicTag == null) || (aggressionRiskScore == null) || (confidence == null) || (speakNow == null))
		{
			LOGGER.warning("OllamaLocalPersonalityAdapter: Invalid required values in sidecar response. moodTag=" + moodTag + ", intentPreference=" + intentPreference + ", zoneBias=" + zoneBias + ", lineStyleTag=" + lineStyleTag + ", topicTag=" + topicTag + ", aggressionRiskScore=" + aggressionRiskScore + ", confidence=" + confidence + ", speakNow=" + speakNow + ", body=" + body);
			return null;
		}
		
		if (moodTag.isBlank() || intentPreference.isBlank() || zoneBias.isBlank() || lineStyleTag.isBlank() || topicTag.isBlank())
		{
			LOGGER.warning("OllamaLocalPersonalityAdapter: Blank required values in sidecar response: " + body);
			return null;
		}
		
		return new PersonalityDecision(moodTag, intentPreference, zoneBias, clamp(aggressionRiskScore), speakNow, lineStyleTag, topicTag, clamp(confidence), ((directReplyLine == null) || directReplyLine.isBlank()) ? null : directReplyLine, ((socialActionTag == null) || socialActionTag.isBlank()) ? "none" : socialActionTag, ((socialTargetTag == null) || socialTargetTag.isBlank()) ? "none" : socialTargetTag, clamp((socialSignalConfidence == null) ? 0.0 : socialSignalConfidence.doubleValue()), clampIntensity((socialSignalIntensity == null) ? 0 : socialSignalIntensity.intValue()));
	}
	
	private static String extractString(String body, String fieldName)
	{
		final int valueStart = findValueStart(body, fieldName);
		if ((valueStart < 0) || (valueStart >= body.length()) || (body.charAt(valueStart) != '"'))
		{
			return null;
		}
		
		final StringBuilder sb = new StringBuilder();
		boolean escaping = false;
		for (int i = valueStart + 1; i < body.length(); i++)
		{
			final char c = body.charAt(i);
			if (escaping)
			{
				switch (c)
				{
					case '"':
					case '\\':
					case '/':
						sb.append(c);
						break;
					case 'n':
						sb.append('\n');
						break;
					case 'r':
						sb.append('\r');
						break;
					case 't':
						sb.append('\t');
						break;
					default:
						sb.append(c);
						break;
				}
				escaping = false;
			}
			else if (c == '\\')
			{
				escaping = true;
			}
			else if (c == '"')
			{
				return sb.toString();
			}
			else
			{
				sb.append(c);
			}
		}
		return null;
	}
	
	private static Double extractDouble(String body, String fieldName)
	{
		final int valueStart = findValueStart(body, fieldName);
		if (valueStart < 0)
		{
			return null;
		}
		
		final int valueEnd = findValueEnd(body, valueStart);
		if (valueEnd <= valueStart)
		{
			return null;
		}
		
		try
		{
			return Double.parseDouble(body.substring(valueStart, valueEnd).trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Integer extractInt(String body, String fieldName)
	{
		final int valueStart = findValueStart(body, fieldName);
		if (valueStart < 0)
		{
			return null;
		}

		final int valueEnd = findValueEnd(body, valueStart);
		if (valueEnd <= valueStart)
		{
			return null;
		}

		try
		{
			return Integer.parseInt(body.substring(valueStart, valueEnd).trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
	
	private static Boolean extractBoolean(String body, String fieldName)
	{
		final int valueStart = findValueStart(body, fieldName);
		if (valueStart < 0)
		{
			return null;
		}
		
		if (body.startsWith("true", valueStart))
		{
			return true;
		}
		if (body.startsWith("false", valueStart))
		{
			return false;
		}
		return null;
	}
	
	private static int findValueStart(String body, String fieldName)
	{
		final String needle = "\"" + fieldName + "\"";
		final int fieldPos = body.indexOf(needle);
		if (fieldPos < 0)
		{
			return -1;
		}
		
		final int colonPos = body.indexOf(':', fieldPos + needle.length());
		if (colonPos < 0)
		{
			return -1;
		}
		
		int valueStart = colonPos + 1;
		while ((valueStart < body.length()) && Character.isWhitespace(body.charAt(valueStart)))
		{
			valueStart++;
		}
		return valueStart;
	}
	
	private static int findValueEnd(String body, int valueStart)
	{
		int valueEnd = valueStart;
		while (valueEnd < body.length())
		{
			final char c = body.charAt(valueEnd);
			if ((c == ',') || (c == '}') || Character.isWhitespace(c))
			{
				break;
			}
			valueEnd++;
		}
		return valueEnd;
	}
	
	private static String quoteArray(Iterable<String> values)
	{
		final StringBuilder sb = new StringBuilder("[");
		boolean first = true;
		for (String value : values)
		{
			if (!first)
			{
				sb.append(',');
			}
			first = false;
			sb.append(quote(value));
		}
		sb.append(']');
		return sb.toString();
	}
	
	private static String quote(String value)
	{
		return "\"" + escape(value) + "\"";
	}
	
	private static String escape(String value)
	{
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
	}

	private static String safe(String value)
	{
		return value == null ? "" : value;
	}
	
	private static double clamp(double value)
	{
		if (value < 0.0)
		{
			return 0.0;
		}
		if (value > 1.0)
		{
			return 1.0;
		}
		return value;
	}

	private static int clampIntensity(int value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 3)
		{
			return 3;
		}
		return value;
	}
}
