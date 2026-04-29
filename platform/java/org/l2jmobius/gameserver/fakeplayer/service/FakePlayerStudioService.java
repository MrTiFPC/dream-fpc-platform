package org.l2jmobius.gameserver.fakeplayer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.FakePlayerPlatformConfig;
import org.l2jmobius.gameserver.fakeplayer.FakePlayerModule;
import org.l2jmobius.gameserver.fakeplayer.config.FakePlayerConfig;
import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerProfileData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcCombatPower;
import org.l2jmobius.gameserver.fakeplayer.model.ResolvedFpcPersona;
import org.l2jmobius.gameserver.fakeplayer.ruleset.FakePlayerRulesetAdapter;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;

public class FakePlayerStudioService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerStudioService.class.getName());

	private final FakePlayerConfig _config;
	private final FakePlayerProfileData _profileData;
	private final FakePlayerRouteService _routeService;
	private final FakePlayerTaskService _taskService;
	private final FakePlayerHybridPartyService _hybridPartyService;
	private final FakePlayerDebugService _debugService;
	private final FakePlayerPersonaResolver _personaResolver;
	private final FakePlayerRulesetAdapter _ruleset;
	private final FakePlayerModule _module;
	private final Path _studioRoot;
	private final Path _fpcRoot;
	private final Path _socialRoot;
	private final Path _commandsRoot;
	private final Path _resultsRoot;
	private final Path _traceRoot;

	private volatile ScheduledFuture<?> _snapshotTask;
	private volatile ScheduledFuture<?> _commandTask;

	public FakePlayerStudioService(FakePlayerConfig config, FakePlayerProfileData profileData, FakePlayerRouteService routeService, FakePlayerTaskService taskService, FakePlayerHybridPartyService hybridPartyService, FakePlayerDebugService debugService, FakePlayerPersonaResolver personaResolver, FakePlayerRulesetAdapter ruleset, FakePlayerModule module)
	{
		_config = config;
		_profileData = profileData;
		_routeService = routeService;
		_taskService = taskService;
		_hybridPartyService = hybridPartyService;
		_debugService = debugService;
		_personaResolver = personaResolver;
		_ruleset = ruleset;
		_module = module;
		_studioRoot = Paths.get(config.getStudioRootPath());
		_fpcRoot = _studioRoot.resolve("fpc");
		_socialRoot = _studioRoot.resolve("social");
		_commandsRoot = _studioRoot.resolve("commands");
		_resultsRoot = _studioRoot.resolve("results");
		_traceRoot = _studioRoot.resolve("trace");
	}

	public synchronized void start()
	{
		if ((_snapshotTask != null) && !_snapshotTask.isDone())
		{
			return;
		}

		ensureBridgeDirectories();
		writeAllSnapshots();
		_snapshotTask = ThreadPool.scheduleAtFixedRate(this::safeWriteAllSnapshots, _config.getStudioSnapshotIntervalMs(), _config.getStudioSnapshotIntervalMs());
		_commandTask = ThreadPool.scheduleAtFixedRate(this::safeProcessCommands, _config.getStudioCommandPollIntervalMs(), _config.getStudioCommandPollIntervalMs());
		LOGGER.info(() -> getClass().getSimpleName() + ": FPC Studio bridge started root=" + _studioRoot + " intervalMs=" + _config.getStudioSnapshotIntervalMs());
	}

	private void safeProcessCommands()
	{
		try
		{
			processCommands();
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed to process studio commands -> " + e.getMessage());
		}
	}

	private void safeWriteAllSnapshots()
	{
		try
		{
			writeAllSnapshots();
		}
		catch (Exception e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Failed to refresh studio snapshots -> " + e.getMessage());
		}
	}

	private void writeAllSnapshots()
	{
		ensureBridgeDirectories();
		final Collection<FpcDefinition> definitions = _profileData.getDefinitions();
		final long now = System.currentTimeMillis();
		writeJson(_studioRoot.resolve("runtime.json"), buildRuntimeSnapshot(definitions, now));
		writeJson(_studioRoot.resolve("roster.json"), buildRosterSnapshot(definitions, now));
		for (FpcDefinition definition : definitions)
		{
			writeJson(_fpcRoot.resolve(definition.getId().toLowerCase(Locale.ROOT) + ".json"), buildFpcDetailSnapshot(definition, now));
		}
	}

	private void processCommands()
	{
		ensureBridgeDirectories();
		try (Stream<Path> stream = Files.list(_commandsRoot))
		{
			stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cmd"))
				.sorted()
				.forEach(this::processCommandFile);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read studio command queue from " + _commandsRoot, e);
		}
	}

	private void processCommandFile(Path path)
	{
		final String fileName = path.getFileName().toString();
		final String commandId = fileName.endsWith(".cmd") ? fileName.substring(0, fileName.length() - 4) : fileName;
		final Map<String, String> command = readCommandFile(path);
		final Map<String, Object> result = executeCommand(commandId, command);
		writeJson(_resultsRoot.resolve(commandId + ".json"), result);
		writeAllSnapshots();
		try
		{
			Files.deleteIfExists(path);
		}
		catch (IOException e)
		{
			LOGGER.warning(getClass().getSimpleName() + ": Could not delete processed studio command " + fileName + " -> " + e.getMessage());
		}
	}

	private Map<String, Object> executeCommand(String commandId, Map<String, String> command)
	{
		final String action = normalizeCommandValue(command.get("action"));
		final String requestedFpcId = normalizeCommandValue(command.get("fpcId"));
		final long now = System.currentTimeMillis();

		final Map<String, Object> result = orderedMap();
		result.put("commandId", commandId);
		result.put("action", action);
		result.put("fpcId", requestedFpcId);
		result.put("processedAtMs", now);

		if (action.isBlank() || requestedFpcId.isBlank())
		{
			result.put("status", "error");
			result.put("message", "Missing action or fpcId.");
			return result;
		}

		final FpcDefinition definition = _profileData.getDefinitionById(requestedFpcId);
		final String resolvedFpcId = (definition == null) ? requestedFpcId : definition.getId();
		result.put("fpcId", resolvedFpcId);

		switch (action)
		{
			case "reload_fpc_definitions":
			{
				_module.reloadFpcDefinitions();
				final String loadStatus = _profileData.getLastDefinitionsLoadStatus();
				final String loadMessage = _profileData.getLastDefinitionsLoadMessage();
				result.put("status", "loaded".equals(loadStatus) ? "ok" : "warning");
				result.put("message", "loaded".equals(loadStatus) ? "Reloaded generic FPC definitions." : loadMessage);
				result.put("definitionsLoadStatus", loadStatus);
				result.put("definitionsLoadMessage", loadMessage);
				result.put("definitionsLoadCount", _profileData.getLastDefinitionsLoadCount());
				result.put("definitionsLoadGeneratedAtMs", _profileData.getLastDefinitionsLoadAtMs());
				result.put("definitionsLoadPreserved", _profileData.isLastDefinitionsLoadPreserved());
				return result;
			}
			case "reload_persona_profiles":
			{
				_module.reloadPersonaProfiles();
				result.put("status", "ok");
				result.put("message", "Reloaded persona profiles.");
				return result;
			}
			case "reload_reply_banks":
			{
				_module.reloadReplyBanks();
				result.put("status", "ok");
				result.put("message", "Reloaded reply banks.");
				return result;
			}
			case "reload_knowledge_cards":
			{
				_module.reloadKnowledgeCards();
				result.put("status", "ok");
				result.put("message", "Reloaded knowledge cards.");
				return result;
			}
			case "reload_reply_settings":
			{
				_module.reloadReplySettings();
				result.put("status", "ok");
				result.put("message", "Reloaded reply settings.");
				return result;
			}
			case "reload_routing_rules":
			{
				_module.reloadRoutingRules();
				result.put("status", "ok");
				result.put("message", "Reloaded routing rules.");
				return result;
			}
			case "spawn":
			{
				final boolean success = _module.spawnFpc(requestedFpcId);
				result.put("status", success ? "ok" : "error");
				result.put("message", success ? ("Spawned " + resolvedFpcId + ".") : ("Failed to spawn " + requestedFpcId + "."));
				return result;
			}
			case "despawn":
			{
				final boolean success = _module.despawnFpc(requestedFpcId);
				result.put("status", success ? "ok" : "error");
				result.put("message", success ? ("Despawned " + resolvedFpcId + ".") : ("Failed to despawn " + requestedFpcId + "."));
				return result;
			}
			case "hold":
			{
				if (definition == null)
				{
					result.put("status", "error");
					result.put("message", "Unknown FPC: " + requestedFpcId);
					return result;
				}

				final Npc npc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
				if (npc == null)
				{
					result.put("status", "error");
					result.put("message", "FPC is not currently active: " + definition.getId());
					return result;
				}

				final long holdMs = Math.max(parseLong(command.get("holdMs"), 30000L), 1000L);
				_taskService.holdForAdminControl(definition, npc, holdMs, true);
				result.put("status", "ok");
				result.put("message", "Held " + definition.getId() + " for " + holdMs + " ms.");
				result.put("holdMs", holdMs);
				return result;
			}
			case "resume":
			{
				if (definition == null)
				{
					result.put("status", "error");
					result.put("message", "Unknown FPC: " + requestedFpcId);
					return result;
				}

				final boolean success = _taskService.releaseAdminHold(definition, true);
				result.put("status", success ? "ok" : "error");
				result.put("message", success ? ("Released hold for " + definition.getId() + ".") : ("FPC is not currently held: " + definition.getId()));
				return result;
			}
			case "conversation_probe":
			{
				final long traceStartedAtMs = System.currentTimeMillis();
				final Map<String, Object> probeResult = _module.probeConversation(requestedFpcId, normalizeCommandValue(command.get("playerName")), normalizeCommandValue(command.get("channel")), normalizeCommandValue(command.get("text")));
				final long traceFinishedAtMs = System.currentTimeMillis();
				final FpcDefinition probeDefinition = (definition != null) ? definition : _profileData.getDefinitionById(resolvedFpcId);
				if ((probeDefinition != null) && "ok".equals(String.valueOf(probeResult.get("status"))))
				{
					final String speakerName = probeDefinition.getName();
					final String playerName = normalizeCommandValue(command.get("playerName"));
					final String channel = normalizeCommandValue(command.get("channel"));
					final Map<String, Object> probe = valueAsMap(probeResult.get("probe"));
					final String currentZone = valueAsString(probe.get("currentZone"));
					final Map<String, Object> classification = valueAsMap(probe.get("classification"));
					final Map<String, Object> reply = valueAsMap(probe.get("reply"));
					final Map<String, Object> relationship = valueAsMap(probe.get("relationship"));
					final Map<String, Object> social = valueAsMap(probe.get("social"));
					final String relationshipSummary = buildRelationshipSummaryText(relationship);
					final String socialSummary = buildSocialSummaryText(social);
					final String recentConversationSummary = valueAsString(relationship.get("recentConversationSummary"));
					final String salientMemorySummary = valueAsString(relationship.get("salientMemorySummary"));
					final String retrievedMemorySummary = valueAsString(relationship.get("retrievedMemorySummary"));
					final String memoryPrioritySummary = valueAsString(relationship.get("memoryPrioritySummary"));
					final String memoryReflectionSummary = valueAsString(relationship.get("memoryReflectionSummary"));
					final String memoryTrustSummary = valueAsString(relationship.get("memoryTrustSummary"));
					final String memoryRepairSummary = valueAsString(relationship.get("memoryRepairSummary"));
					final String memoryPressureSummary = valueAsString(relationship.get("memoryPressureSummary"));
					final String memorySelectionSummary = valueAsString(relationship.get("memorySelectionSummary"));
					final List<Map<String, Object>> retrievalSections = valueAsMapList(relationship.get("retrievalSections"));
					probe.put("trace", buildConversationProbeTrace(probeDefinition, speakerName, playerName, channel, currentZone, traceStartedAtMs, traceFinishedAtMs, classification, reply, relationship, social, relationshipSummary, socialSummary, recentConversationSummary, salientMemorySummary, retrievedMemorySummary, memoryPrioritySummary, memoryReflectionSummary, memoryTrustSummary, memoryRepairSummary, memoryPressureSummary, memorySelectionSummary, retrievalSections));
				}
				appendTraceLine(_traceRoot.resolve("replies.jsonl"), probeResult);
				return probeResult;
			}
			case "social_snapshot":
			{
				final long traceStartedAtMs = System.currentTimeMillis();
				final String playerName = normalizeCommandValue(command.get("playerName"));
				final Map<String, Object> socialResult = _module.inspectSocialPair(requestedFpcId, playerName);
				final long traceFinishedAtMs = System.currentTimeMillis();
				if ("ok".equals(String.valueOf(socialResult.get("status"))))
				{
					final String fileName = buildSocialSnapshotFileName(resolvedFpcId, playerName);
					writeJson(_socialRoot.resolve(fileName), socialResult);
					socialResult.put("snapshotPath", "social/" + fileName);
					final FpcDefinition socialDefinition = (definition != null) ? definition : _profileData.getDefinitionById(resolvedFpcId);
					if (socialDefinition != null)
					{
						final String speakerName = socialDefinition.getName();
						final Map<String, Object> snapshot = valueAsMap(socialResult.get("snapshot"));
						final String currentZone = valueAsString(snapshot.get("currentZone"));
						final Map<String, Object> relationship = valueAsMap(snapshot.get("relationship"));
						final Map<String, Object> social = valueAsMap(snapshot.get("social"));
						final String relationshipSummary = buildRelationshipSummaryText(relationship);
						final String socialSummary = buildSocialSummaryText(social);
						final String recentConversationSummary = valueAsString(relationship.get("recentConversationSummary"));
						final String salientMemorySummary = valueAsString(relationship.get("salientMemorySummary"));
						final String retrievedMemorySummary = valueAsString(relationship.get("retrievedMemorySummary"));
						final String memoryPrioritySummary = valueAsString(relationship.get("memoryPrioritySummary"));
						final String memoryReflectionSummary = valueAsString(relationship.get("memoryReflectionSummary"));
						final String memoryTrustSummary = valueAsString(relationship.get("memoryTrustSummary"));
						final String memoryRepairSummary = valueAsString(relationship.get("memoryRepairSummary"));
						final String memoryPressureSummary = valueAsString(relationship.get("memoryPressureSummary"));
						final String memorySelectionSummary = valueAsString(relationship.get("memorySelectionSummary"));
						final List<Map<String, Object>> retrievalSections = valueAsMapList(relationship.get("retrievalSections"));
						snapshot.put("trace", buildSocialSnapshotTrace(socialDefinition, speakerName, playerName, currentZone, traceStartedAtMs, traceFinishedAtMs, relationship, social, relationshipSummary, socialSummary, recentConversationSummary, salientMemorySummary, retrievedMemorySummary, memoryPrioritySummary, memoryReflectionSummary, memoryTrustSummary, memoryRepairSummary, memoryPressureSummary, memorySelectionSummary, retrievalSections));
					}
					appendTraceLine(_traceRoot.resolve("debug.jsonl"), socialResult);
				}
				return socialResult;
			}
			default:
			{
				result.put("status", "error");
				result.put("message", "Unsupported studio action: " + action);
				return result;
			}
		}
	}

	private Map<String, Object> buildConversationProbeTrace(FpcDefinition definition, String speakerName, String playerName, String channel, String currentZone, long startedAtMs, long finishedAtMs, Map<String, Object> classification, Map<String, Object> reply, Map<String, Object> relationship, Map<String, Object> social, String relationshipSummary, String socialSummary, String recentConversationSummary, String salientMemorySummary, String retrievedMemorySummary, String memoryPrioritySummary, String memoryReflectionSummary, String memoryTrustSummary, String memoryRepairSummary, String memoryPressureSummary, String memorySelectionSummary, List<Map<String, Object>> retrievalSections)
	{
		final Map<String, Object> trace = orderedMap();
		trace.put("schemaVersion", 3);
		trace.put("traceType", "conversation_probe");
		trace.put("speakerId", definition.getId());
		trace.put("speakerName", speakerName);
		trace.put("playerName", playerName);
		trace.put("channel", channel);
		trace.put("currentZone", currentZone);
		trace.put("capturedAtMs", finishedAtMs);
		trace.put("startedAtMs", startedAtMs);
		trace.put("finishedAtMs", finishedAtMs);
		trace.put("durationMs", Math.max(0L, finishedAtMs - startedAtMs));
		trace.put("provider", _config.getPersonalityMode());
		trace.put("model", _config.getPersonalityModel());
		trace.put("endpoint", _config.getPersonalityEndpoint());
		trace.put("temperature", _config.getPersonalityTemperature());
		trace.put("timeoutMs", _config.getPersonalityTimeoutMs());
		trace.put("maxRetries", _config.getPersonalityMaxRetries());
		trace.put("classification", classification);
		trace.put("reply", reply);

		final Map<String, Object> source = orderedMap();
		final String replySource = valueAsString(reply.get("source"));
		source.put("replySource", replySource);
		source.put("sourceClass", classifyReplySource(replySource));
		source.put("fallbackUsed", isFallbackReplySource(replySource));
		source.put("fallbackReason", deriveFallbackReason(replySource));
		trace.put("source", source);

		final Map<String, Object> memory = orderedMap();
		memory.put("matchedRelationshipSummary", valueAsString(relationship.get("matchedRelationshipSummary")));
		memory.put("recentConversationSummary", recentConversationSummary);
		memory.put("salientMemorySummary", salientMemorySummary);
		memory.put("retrievedMemorySummary", retrievedMemorySummary);
		memory.put("memoryPrioritySummary", memoryPrioritySummary);
		memory.put("memoryReflectionSummary", memoryReflectionSummary);
		memory.put("memoryTrustSummary", memoryTrustSummary);
		memory.put("memoryRepairSummary", memoryRepairSummary);
		memory.put("memoryPressureSummary", memoryPressureSummary);
		memory.put("memorySelectionSummary", memorySelectionSummary);
		memory.put("retrievalSections", retrievalSections);
		memory.put("relationshipSummary", relationshipSummary);
		memory.put("socialSummary", socialSummary);
		trace.put("memory", memory);
		trace.put("relationship", relationship);
		trace.put("social", social);
		trace.put("relationshipSummary", relationshipSummary);
		trace.put("socialSummary", socialSummary);
		return trace;
	}

	private Map<String, Object> buildSocialSnapshotTrace(FpcDefinition definition, String speakerName, String playerName, String currentZone, long startedAtMs, long finishedAtMs, Map<String, Object> relationship, Map<String, Object> social, String relationshipSummary, String socialSummary, String recentConversationSummary, String salientMemorySummary, String retrievedMemorySummary, String memoryPrioritySummary, String memoryReflectionSummary, String memoryTrustSummary, String memoryRepairSummary, String memoryPressureSummary, String memorySelectionSummary, List<Map<String, Object>> retrievalSections)
	{
		final Map<String, Object> trace = orderedMap();
		trace.put("schemaVersion", 3);
		trace.put("traceType", "social_snapshot");
		trace.put("speakerId", definition.getId());
		trace.put("speakerName", speakerName);
		trace.put("playerName", playerName);
		trace.put("currentZone", currentZone);
		trace.put("capturedAtMs", finishedAtMs);
		trace.put("startedAtMs", startedAtMs);
		trace.put("finishedAtMs", finishedAtMs);
		trace.put("durationMs", Math.max(0L, finishedAtMs - startedAtMs));
		trace.put("provider", _config.getPersonalityMode());
		trace.put("model", _config.getPersonalityModel());
		trace.put("endpoint", _config.getPersonalityEndpoint());
		trace.put("temperature", _config.getPersonalityTemperature());
		trace.put("timeoutMs", _config.getPersonalityTimeoutMs());
		trace.put("maxRetries", _config.getPersonalityMaxRetries());
		final Map<String, Object> memory = orderedMap();
		memory.put("matchedRelationshipSummary", valueAsString(relationship.get("matchedRelationshipSummary")));
		memory.put("recentConversationSummary", recentConversationSummary);
		memory.put("salientMemorySummary", salientMemorySummary);
		memory.put("retrievedMemorySummary", retrievedMemorySummary);
		memory.put("memoryPrioritySummary", memoryPrioritySummary);
		memory.put("memoryReflectionSummary", memoryReflectionSummary);
		memory.put("memoryTrustSummary", memoryTrustSummary);
		memory.put("memoryRepairSummary", memoryRepairSummary);
		memory.put("memoryPressureSummary", memoryPressureSummary);
		memory.put("memorySelectionSummary", memorySelectionSummary);
		memory.put("retrievalSections", retrievalSections);
		memory.put("relationshipSummary", relationshipSummary);
		memory.put("socialSummary", socialSummary);
		trace.put("memory", memory);
		trace.put("relationship", relationship);
		trace.put("social", social);
		trace.put("relationshipSummary", relationshipSummary);
		trace.put("socialSummary", socialSummary);
		return trace;
	}

	private String buildRelationshipSummaryText(Map<String, Object> relationship)
	{
		if ((relationship == null) || relationship.isEmpty())
		{
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("familiarity=").append(valueAsString(relationship.get("familiarity")));
		sb.append(", trust=").append(valueAsString(relationship.get("trust")));
		sb.append(", respect=").append(valueAsString(relationship.get("respect")));
		sb.append(", tension=").append(valueAsString(relationship.get("tension")));
		sb.append(", resentment=").append(valueAsString(relationship.get("resentment")));
		final String killCount = valueAsString(relationship.get("playerKillCount"));
		if (!"0".equals(killCount) && !killCount.isBlank())
		{
			sb.append(", playerKillCount=").append(killCount);
		}
		final String currentGoal = valueAsString(relationship.get("currentGoal"));
		if (!currentGoal.isBlank())
		{
			sb.append(", currentGoal=").append(currentGoal);
		}
		final String activeNeed = valueAsString(relationship.get("activeNeed"));
		if (!activeNeed.isBlank())
		{
			sb.append(", activeNeed=").append(activeNeed);
		}
		final String lastTopic = valueAsString(relationship.get("lastImportantTopic"));
		if (!lastTopic.isBlank())
		{
			sb.append(", lastTopic=").append(lastTopic);
		}
		return sb.toString();
	}

	private String buildSocialSummaryText(Map<String, Object> social)
	{
		if ((social == null) || social.isEmpty())
		{
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		final String socialLabel = valueAsString(social.get("socialLabel"));
		sb.append("socialLabel=").append(socialLabel);
		final String summary = valueAsString(social.get("summary"));
		if (!summary.isBlank())
		{
			sb.append(", summary=").append(summary);
		}
		sb.append(", trustBias=").append(valueAsString(social.get("trustBias")));
		sb.append(", guardBias=").append(valueAsString(social.get("guardBias")));
		if (Boolean.parseBoolean(valueAsString(social.get("historicalKillHistory"))))
		{
			sb.append(", historicalKillHistory=true");
		}
		return sb.toString();
	}

	private String classifyReplySource(String replySource)
	{
		if ((replySource == null) || replySource.isBlank())
		{
			return "unknown";
		}
		if ("reply_bank".equalsIgnoreCase(replySource) || replySource.startsWith("bank_"))
		{
			return "bank";
		}
		if ("knowledge".equalsIgnoreCase(replySource) || replySource.startsWith("knowledge_"))
		{
			return "knowledge";
		}
		if ("guarded_direct".equalsIgnoreCase(replySource))
		{
			return "guard";
		}
		if (replySource.startsWith("fallback_"))
		{
			return "fallback";
		}
		if (replySource.startsWith("policy_"))
		{
			return "policy";
		}
		if ("conversation_guard".equalsIgnoreCase(replySource))
		{
			return "guard";
		}
		return "model";
	}

	private boolean isFallbackReplySource(String replySource)
	{
		return ("fallback".equals(classifyReplySource(replySource))) || ("policy".equals(classifyReplySource(replySource))) || ("guard".equals(classifyReplySource(replySource)));
	}

	private String deriveFallbackReason(String replySource)
	{
		if ((replySource == null) || replySource.isBlank())
		{
			return "";
		}
		if (replySource.startsWith("policy_"))
		{
			return replySource.substring("policy_".length());
		}
		if (replySource.startsWith("fallback_"))
		{
			return replySource;
		}
		if ("conversation_guard".equalsIgnoreCase(replySource))
		{
			return "guarded_reply";
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> valueAsMap(Object value)
	{
		if (value instanceof Map)
		{
			return (Map<String, Object>) value;
		}
		return orderedMap();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> valueAsMapList(Object value)
	{
		if (!(value instanceof List<?> values))
		{
			return List.of();
		}

		final List<Map<String, Object>> result = new ArrayList<>();
		for (Object entry : values)
		{
			if (entry instanceof Map)
			{
				result.add((Map<String, Object>) entry);
			}
		}
		return result;
	}

	private String valueAsString(Object value)
	{
		return (value == null) ? "" : String.valueOf(value);
	}

	private Map<String, String> readCommandFile(Path path)
	{
		final Map<String, String> values = new LinkedHashMap<>();
		try
		{
			for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8))
			{
				final String line = rawLine.trim();
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				final int separatorIndex = line.indexOf('=');
				if (separatorIndex <= 0)
				{
					continue;
				}
				final String key = line.substring(0, separatorIndex).trim();
				final String value = line.substring(separatorIndex + 1).trim();
				if (!key.isBlank())
				{
					values.put(key, value);
				}
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read studio command " + path.getFileName(), e);
		}
		return values;
	}

	private String normalizeCommandValue(String value)
	{
		return (value == null) ? "" : value.trim();
	}

	private long parseLong(String raw, long fallback)
	{
		if ((raw == null) || raw.isBlank())
		{
			return fallback;
		}
		try
		{
			return Long.parseLong(raw.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private String buildSocialSnapshotFileName(String fpcId, String playerName)
	{
		return normalizeSnapshotComponent(fpcId) + "__" + normalizeSnapshotComponent(playerName) + ".json";
	}

	private String normalizeSnapshotComponent(String value)
	{
		final String normalized = normalizeCommandValue(value);
		if (normalized.isBlank())
		{
			return "unknown";
		}

		final StringBuilder sb = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++)
		{
			final char current = normalized.charAt(i);
			if (Character.isLetterOrDigit(current) || (current == '_') || (current == '-'))
			{
				sb.append(Character.toLowerCase(current));
			}
			else
			{
				sb.append('_');
			}
		}
		return sb.toString();
	}

	private Map<String, Object> buildRuntimeSnapshot(Collection<FpcDefinition> definitions, long now)
	{
		int enabledCount = 0;
		int liveCount = 0;
		int liveSocialCount = 0;
		int liveAdventurerCount = 0;
		for (FpcDefinition definition : definitions)
		{
			if (definition.isEnabled())
			{
				enabledCount++;
			}

			if (_profileData.getRegistry().resolveActiveNpc(definition.getId()) != null)
			{
				liveCount++;
				if (definition.getAdventurerProfile().isAdventurerTier())
				{
					liveAdventurerCount++;
				}
				else
				{
					liveSocialCount++;
				}
			}
		}

		final Map<String, Object> runtime = orderedMap();
		runtime.put("generatedAtMs", now);
		runtime.put("bridgeMode", "snapshot_bridge_with_command_queue");
		runtime.put("platformAdapterId", FakePlayerPlatformConfig.FPC_PLATFORM_ADAPTER_ID);
		runtime.put("rulesetId", _ruleset.getId());
		runtime.put("loadedCount", definitions.size());
		runtime.put("enabledCount", enabledCount);
		runtime.put("liveCount", liveCount);
		runtime.put("liveSocialCount", liveSocialCount);
		runtime.put("liveAdventurerCount", liveAdventurerCount);
		runtime.put("definitionsLoadStatus", _profileData.getLastDefinitionsLoadStatus());
		runtime.put("definitionsLoadMessage", _profileData.getLastDefinitionsLoadMessage());
		runtime.put("definitionsLoadCount", _profileData.getLastDefinitionsLoadCount());
		runtime.put("definitionsLoadGeneratedAtMs", _profileData.getLastDefinitionsLoadAtMs());
		runtime.put("definitionsLoadPreserved", _profileData.isLastDefinitionsLoadPreserved());
		runtime.put("studioRoot", _studioRoot.toString());
		return runtime;
	}

	private Map<String, Object> buildRosterSnapshot(Collection<FpcDefinition> definitions, long now)
	{
		final List<Map<String, Object>> entries = new ArrayList<>();
		for (FpcDefinition definition : definitions)
		{
			entries.add(buildRosterEntry(definition, now));
		}

		final Map<String, Object> roster = orderedMap();
		roster.put("generatedAtMs", now);
		roster.put("entries", entries);
		return roster;
	}

	private Map<String, Object> buildFpcDetailSnapshot(FpcDefinition definition, long now)
	{
		final Npc activeNpc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
		final Npc lastSpawn = _profileData.getRegistry().resolveLastSpawnNpc(definition.getId());
		final String currentZone = resolveCurrentZone(definition, activeNpc);
		final long adminHoldRemainingMs = _taskService.getAdminHoldRemainingMs(definition.getId());
		final Player leader = (_hybridPartyService == null) ? null : _hybridPartyService.getActivePlayerLeader(definition.getId());
		final ResolvedFpcCombatPower combatPower = _profileData.getRegistry().getActiveCombatPower(definition.getId());
		final ResolvedFpcPersona persona = _personaResolver.resolve(definition.getName(), definition.getArchetype());

		final Map<String, Object> detail = orderedMap();
		detail.put("generatedAtMs", now);
		detail.put("definition", buildDefinitionBlock(definition));
		detail.put("runtime", buildRuntimeBlock(definition, activeNpc, lastSpawn, currentZone, adminHoldRemainingMs, leader));
		detail.put("persona", buildPersonaBlock(persona));
		detail.put("combatPower", buildCombatPowerBlock(combatPower));
		return detail;
	}

	private Map<String, Object> buildRosterEntry(FpcDefinition definition, long now)
	{
		final Npc activeNpc = _profileData.getRegistry().resolveActiveNpc(definition.getId());
		final Npc lastSpawn = _profileData.getRegistry().resolveLastSpawnNpc(definition.getId());
		final String currentZone = resolveCurrentZone(definition, activeNpc);
		final long adminHoldRemainingMs = _taskService.getAdminHoldRemainingMs(definition.getId());
		final Player leader = (_hybridPartyService == null) ? null : _hybridPartyService.getActivePlayerLeader(definition.getId());
		final WorldObject target = (activeNpc == null) ? null : activeNpc.getTarget();

		final Map<String, Object> entry = orderedMap();
		entry.put("generatedAtMs", now);
		entry.put("id", definition.getId());
		entry.put("name", definition.getName());
		entry.put("title", definition.getTitle());
		entry.put("type", definition.getAdventurerProfile().isAdventurerTier() ? "AFPC" : "FPC");
		entry.put("category", definition.getCategory());
		entry.put("archetype", definition.getArchetype());
		entry.put("personaTemplate", definition.getPersonaTemplate());
		entry.put("enabled", definition.isEnabled());
		entry.put("talkable", definition.isTalkable());
		entry.put("autoSpawnOnBoot", definition.isAutoSpawnOnBoot());
		entry.put("live", activeNpc != null);
		entry.put("state", resolveState(activeNpc, lastSpawn, adminHoldRemainingMs));
		entry.put("zone", currentZone);
		entry.put("debugCategories", _debugService.describe(definition.getId()));
		entry.put("adminHoldRemainingMs", adminHoldRemainingMs);
		entry.put("leaderName", (leader == null) ? "" : leader.getName());
		entry.put("targetName", (target == null) ? "" : target.getName());
		populateRosterVitals(entry, activeNpc, lastSpawn);
		return entry;
	}

	private Map<String, Object> buildDefinitionBlock(FpcDefinition definition)
	{
		final Map<String, Object> block = orderedMap();
		block.put("id", definition.getId());
		block.put("name", definition.getName());
		block.put("title", definition.getTitle());
		block.put("enabled", definition.isEnabled());
		block.put("category", definition.getCategory());
		block.put("archetype", definition.getArchetype());
		block.put("personaTemplate", definition.getPersonaTemplate());
		block.put("talkable", definition.isTalkable());
		block.put("autoSpawnOnBoot", definition.isAutoSpawnOnBoot());
		block.put("presentationMode", definition.getPresentationMode());
		block.put("carrierPolicy", definition.getCarrierPolicy());
		block.put("speciesTag", definition.getSpeciesTag());
		block.put("familyTag", definition.getFamilyTag());

		final Map<String, Object> spawnProfile = orderedMap();
		spawnProfile.put("mode", definition.getSpawnProfile().getMode());
		spawnProfile.put("zone", definition.getSpawnProfile().getZone());
		spawnProfile.put("x", definition.getSpawnProfile().getX());
		spawnProfile.put("y", definition.getSpawnProfile().getY());
		spawnProfile.put("z", definition.getSpawnProfile().getZ());
		spawnProfile.put("heading", definition.getSpawnProfile().getHeading());
		block.put("spawnProfile", spawnProfile);

		final Map<String, Object> adventurer = buildAdventurerProfileBlock(definition);
		block.put("adventurerProfile", adventurer);
		if ((definition.getCompanionProfile() != null) && definition.getCompanionProfile().isConfigured())
		{
			final Map<String, Object> companion = orderedMap();
			companion.put("relationGate", definition.getCompanionProfile().getRelationGate());
			companion.put("sessionMode", definition.getCompanionProfile().getSessionMode());
			companion.put("idleBehavior", definition.getCompanionProfile().getIdleBehavior());
			companion.put("idleReturnHomeMs", definition.getCompanionProfile().getIdleReturnHomeMs());
			companion.put("allowInstanceFollow", definition.getCompanionProfile().isAllowInstanceFollow());
			companion.put("followupMode", definition.getCompanionProfile().getFollowupMode());
			companion.put("clanJoinMode", definition.getCompanionProfile().getClanJoinMode());
			block.put("companionProfile", companion);
		}
		return block;
	}

	private Map<String, Object> buildRuntimeBlock(FpcDefinition definition, Npc activeNpc, Npc lastSpawn, String currentZone, long adminHoldRemainingMs, Player leader)
	{
		final Map<String, Object> block = orderedMap();
		block.put("live", activeNpc != null);
		block.put("state", resolveState(activeNpc, lastSpawn, adminHoldRemainingMs));
		block.put("zone", currentZone);
		block.put("adminHoldRemainingMs", adminHoldRemainingMs);
		block.put("debugCategories", _debugService.describe(definition.getId()));
		block.put("leaderName", (leader == null) ? "" : leader.getName());

		if (activeNpc == null)
		{
			populateInactiveIdentifiers(block, lastSpawn);
			return block;
		}

		final WorldObject target = activeNpc.getTarget();
		block.put("objectId", activeNpc.getObjectId());
		block.put("npcId", activeNpc.getId());
		block.put("moving", activeNpc.isMoving());
		block.put("inCombat", activeNpc.isInCombat());
		block.put("targetName", (target == null) ? "" : target.getName());
		block.put("x", activeNpc.getX());
		block.put("y", activeNpc.getY());
		block.put("z", activeNpc.getZ());
		block.put("hpPercent", roundPercent(activeNpc.getCurrentHp(), activeNpc.getMaxHp()));
		block.put("mpPercent", roundPercent(activeNpc.getCurrentMp(), activeNpc.getMaxMp()));
		block.put("cpPercent", roundPercent(activeNpc.getCurrentCp(), activeNpc.getMaxCp()));
		block.put("hp", Math.round(activeNpc.getCurrentHp()));
		block.put("maxHp", Math.round(activeNpc.getMaxHp()));
		block.put("mp", Math.round(activeNpc.getCurrentMp()));
		block.put("maxMp", Math.round(activeNpc.getMaxMp()));
		block.put("cp", Math.round(activeNpc.getCurrentCp()));
		block.put("maxCp", activeNpc.getMaxCp());
		return block;
	}

	private Map<String, Object> buildAdventurerProfileBlock(FpcDefinition definition)
	{
		final Map<String, Object> adventurer = orderedMap();
		adventurer.put("tier", definition.getAdventurerProfile().getTier());
		adventurer.put("carrierMode", definition.getAdventurerProfile().getCarrierMode());
		adventurer.put("inventoryMode", definition.getAdventurerProfile().getInventoryMode());
		adventurer.put("pickupPolicy", definition.getAdventurerProfile().getPickupPolicy());
		adventurer.put("combatMode", definition.getAdventurerProfile().getCombatMode());
		adventurer.put("partyMode", definition.getAdventurerProfile().getPartyMode());
		adventurer.put("clanMode", definition.getAdventurerProfile().getClanMode());
		adventurer.put("matchmakingMode", definition.getAdventurerProfile().getMatchmakingMode());
		adventurer.put("progressionMode", definition.getAdventurerProfile().getProgressionMode());
		adventurer.put("stageBandId", definition.getAdventurerProfile().getStageBandId());
		adventurer.put("fixedLevel", definition.getAdventurerProfile().getFixedLevel());
		adventurer.put("loadoutProfile", definition.getAdventurerProfile().getLoadoutProfile());
		adventurer.put("consumableProfile", definition.getAdventurerProfile().getConsumableProfile());
		adventurer.put("routeId", definition.getAdventurerProfile().getRouteId());
		return adventurer;
	}

	private void populateRosterVitals(Map<String, Object> block, Npc activeNpc, Npc lastSpawn)
	{
		if (activeNpc == null)
		{
			populateInactiveIdentifiers(block, lastSpawn);
			block.put("hpPercent", 0);
			block.put("mpPercent", 0);
			block.put("cpPercent", 0);
			return;
		}

		block.put("objectId", activeNpc.getObjectId());
		block.put("npcId", activeNpc.getId());
		block.put("hpPercent", roundPercent(activeNpc.getCurrentHp(), activeNpc.getMaxHp()));
		block.put("mpPercent", roundPercent(activeNpc.getCurrentMp(), activeNpc.getMaxMp()));
		block.put("cpPercent", roundPercent(activeNpc.getCurrentCp(), activeNpc.getMaxCp()));
	}

	private void populateInactiveIdentifiers(Map<String, Object> block, Npc lastSpawn)
	{
		block.put("objectId", 0);
		block.put("npcId", (lastSpawn == null) ? 0 : lastSpawn.getId());
	}

	private Map<String, Object> buildPersonaBlock(ResolvedFpcPersona persona)
	{
		final Map<String, Object> block = orderedMap();
		if (persona == null)
		{
			return block;
		}
		block.put("personaTemplate", persona.getPersonaTemplate());
		block.put("responseStyle", persona.getResponseStyle());
		block.put("playerStance", persona.getPlayerStance());
		block.put("coreNeed", persona.getCoreNeed());
		block.put("personaSummary", persona.getPersonaSummary());
		block.put("selfKnowledgeSummary", persona.getSelfKnowledgeSummary());
		block.put("publicMaskSummary", persona.getPublicMaskSummary());
		block.put("hiddenTruthSummary", persona.getHiddenTruthSummary());
		block.put("selfConcept", persona.getSelfConcept());
		block.put("coreWound", persona.getCoreWound());
		block.put("coreDesire", persona.getCoreDesire());
		block.put("loyaltyAnchor", persona.getLoyaltyAnchor());
		block.put("resentmentAnchor", persona.getResentmentAnchor());
		block.put("privateTaboo", persona.getPrivateTaboo());
		block.put("speechAnchor", persona.getSpeechAnchor());
		block.put("privateContradiction", persona.getPrivateContradiction());
		block.put("guidancePosture", persona.getGuidancePosture());
		block.put("guidancePriority", persona.getGuidancePriority());
		block.put("uncertaintyStyle", persona.getUncertaintyStyle());
		block.put("recommendationStyle", persona.getRecommendationStyle());
		block.put("riskStyle", persona.getRiskStyle());
		block.put("teachingStyle", persona.getTeachingStyle());
		block.put("storySummary", persona.getStorySummary());
		block.put("relationshipSummary", persona.getRelationshipSummary());
		block.put("behaviorFamilyId", persona.getBehaviorFamilyId());
		block.put("behaviorSummary", persona.getBehaviorSummary());
		block.put("stateProfileId", persona.getStateProfileId());
		block.put("stateSummary", persona.getStateSummary());
		return block;
	}

	private Map<String, Object> buildCombatPowerBlock(ResolvedFpcCombatPower combatPower)
	{
		final Map<String, Object> block = orderedMap();
		if (combatPower == null)
		{
			return block;
		}

		block.put("effectiveLevel", combatPower.getEffectiveLevel());
		block.put("combatTierTag", combatPower.getCombatTierTag());
		block.put("targetClassDepth", combatPower.getTargetClassDepth());
		block.put("resolvedPlayerClassName", combatPower.getResolvedPlayerClassName());
		block.put("resolvedLoadoutProfile", combatPower.getResolvedLoadoutProfile());
		block.put("resolvedConsumableProfile", combatPower.getResolvedConsumableProfile());
		block.put("resolvedCombatProfile", combatPower.getResolvedCombatProfile());
		block.put("combatWeight", combatPower.getCombatWeight());
		block.put("passiveSkillCount", combatPower.getPassiveSkillCount());
		block.put("activeSkillCount", combatPower.getActiveSkillCount());
		block.put("knownSkillCount", combatPower.getKnownSkillCount());
		block.put("summary", combatPower.describe());
		return block;
	}

	private String resolveCurrentZone(FpcDefinition definition, Npc activeNpc)
	{
		if (activeNpc == null)
		{
			return definition.getSpawnProfile().getZone();
		}
		return _routeService.resolveCurrentZone(activeNpc, definition.getSpawnProfile().getZone());
	}

	private String resolveState(Npc activeNpc, Npc lastSpawn, long adminHoldRemainingMs)
	{
		if (adminHoldRemainingMs > 0L)
		{
			return "held";
		}
		if (activeNpc != null)
		{
			if (activeNpc.isDead())
			{
				return "dead";
			}
			final WorldObject target = activeNpc.getTarget();
			final Intention intention = (activeNpc.getAI() == null) ? null : activeNpc.getAI().getIntention();
			final boolean combatIntent = activeNpc.isInCombat() || activeNpc.isAttackingNow() || activeNpc.isCastingNow() || (intention == Intention.ATTACK) || (intention == Intention.CAST);
			if (combatIntent)
			{
				return "combat";
			}
			if ((target != null) && target.isCreature() && ((intention == Intention.ATTACK) || (intention == Intention.CAST)))
			{
				return "combat";
			}
			if (activeNpc.isMoving())
			{
				return "moving";
			}
			if ((intention == Intention.FOLLOW) || (intention == Intention.MOVE_TO))
			{
				return "moving";
			}
			return "idle";
		}
		if ((lastSpawn != null) && (lastSpawn.isDead() || lastSpawn.isDecayed()))
		{
			return "dead_or_respawning";
		}
		return "inactive";
	}

	private int roundPercent(double current, double max)
	{
		if (max <= 0.0)
		{
			return 0;
		}
		return (int) Math.max(0, Math.min(100, Math.round((current / max) * 100.0)));
	}

	private void ensureBridgeDirectories()
	{
		try
		{
			Files.createDirectories(_studioRoot);
			Files.createDirectories(_fpcRoot);
			Files.createDirectories(_socialRoot);
			Files.createDirectories(_commandsRoot);
			Files.createDirectories(_resultsRoot);
			Files.createDirectories(_traceRoot);
			ensurePlaceholderFile(_traceRoot.resolve("replies.jsonl"));
			ensurePlaceholderFile(_traceRoot.resolve("debug.jsonl"));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not initialize FPC Studio bridge at " + _studioRoot, e);
		}
	}

	private void ensurePlaceholderFile(Path path) throws IOException
	{
		if (!Files.exists(path))
		{
			Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
		}
	}

	private void appendTraceLine(Path path, Object payload)
	{
		try
		{
			Files.writeString(path, toJson(payload) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not append studio trace " + path, e);
		}
	}

	private void writeJson(Path path, Object payload)
	{
		final String json = toJson(payload);
		final Path parent = path.getParent();
		try
		{
			if (parent != null)
			{
				Files.createDirectories(parent);
			}
			final Path tempPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
			Files.writeString(tempPath, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			try
			{
				Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not write studio snapshot " + path, e);
		}
	}

	private static Map<String, Object> orderedMap()
	{
		return new LinkedHashMap<>();
	}

	@SuppressWarnings("unchecked")
	private String toJson(Object value)
	{
		if (value == null)
		{
			return "null";
		}
		if (value instanceof String)
		{
			return "\"" + escapeJson((String) value) + "\"";
		}
		if ((value instanceof Number) || (value instanceof Boolean))
		{
			return String.valueOf(value);
		}
		if (value instanceof Map)
		{
			final StringBuilder sb = new StringBuilder();
			sb.append("{");
			boolean first = true;
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
			{
				if (!first)
				{
					sb.append(",");
				}
				first = false;
				sb.append(toJson(String.valueOf(entry.getKey())));
				sb.append(":");
				sb.append(toJson(entry.getValue()));
			}
			sb.append("}");
			return sb.toString();
		}
		if (value instanceof Collection)
		{
			final StringBuilder sb = new StringBuilder();
			sb.append("[");
			boolean first = true;
			for (Object entry : (Collection<Object>) value)
			{
				if (!first)
				{
					sb.append(",");
				}
				first = false;
				sb.append(toJson(entry));
			}
			sb.append("]");
			return sb.toString();
		}
		return toJson(String.valueOf(value));
	}

	private String escapeJson(String value)
	{
		final StringBuilder sb = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++)
		{
			final char ch = value.charAt(i);
			switch (ch)
			{
				case '\\':
					sb.append("\\\\");
					break;
				case '"':
					sb.append("\\\"");
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\f':
					sb.append("\\f");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (ch < 0x20)
					{
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
					}
					else
					{
						sb.append(ch);
					}
			}
		}
		return sb.toString();
	}
}
