package org.l2jmobius.gameserver.fakeplayer.service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.fakeplayer.data.FakePlayerSyntheticScenarioData;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDebugCategory;
import org.l2jmobius.gameserver.fakeplayer.model.FpcDefinition;
import org.l2jmobius.gameserver.fakeplayer.model.FpcSyntheticScenarioDefinition;
import org.l2jmobius.gameserver.model.actor.Npc;

public class FakePlayerSyntheticScenarioService
{
	private static final Logger LOGGER = Logger.getLogger(FakePlayerSyntheticScenarioService.class.getName());

	public static final class ScenarioProgress
	{
		private final String _scenarioId;
		private final AtomicInteger _targetCount = new AtomicInteger();
		private final AtomicBoolean _startAnnouncementPending = new AtomicBoolean(true);
		private final AtomicBoolean _completeAnnouncementPending = new AtomicBoolean();
		private final AtomicBoolean _stagingTeleportPending = new AtomicBoolean(true);
		private final AtomicBoolean _returnTeleportPending = new AtomicBoolean(true);
		private volatile boolean _objectiveComplete;
		private volatile boolean _completed;
		private volatile long _startedAtMs;
		private volatile long _completedAtMs;

		private ScenarioProgress(String scenarioId)
		{
			_scenarioId = scenarioId;
			_startedAtMs = System.currentTimeMillis();
		}

		public String getScenarioId()
		{
			return _scenarioId;
		}

		public int getTargetCount()
		{
			return _targetCount.get();
		}

		public boolean isObjectiveComplete()
		{
			return _objectiveComplete;
		}

		public boolean isCompleted()
		{
			return _completed;
		}

		public long getStartedAtMs()
		{
			return _startedAtMs;
		}

		public long getCompletedAtMs()
		{
			return _completedAtMs;
		}

		private int incrementTargetCount(int targetGoal)
		{
			final int updated = _targetCount.updateAndGet(current -> Math.min(Math.max(1, targetGoal), current + 1));
			if (updated >= Math.max(1, targetGoal))
			{
				_objectiveComplete = true;
			}
			return updated;
		}

		public boolean consumeStartAnnouncement()
		{
			return _startAnnouncementPending.compareAndSet(true, false);
		}

		public boolean consumeCompleteAnnouncement()
		{
			return _completeAnnouncementPending.compareAndSet(true, false);
		}

		public boolean consumeStagingTeleport()
		{
			return _stagingTeleportPending.compareAndSet(true, false);
		}

		public boolean consumeReturnTeleport()
		{
			return _returnTeleportPending.compareAndSet(true, false);
		}

		private boolean markCompleted()
		{
			if (_completed)
			{
				return false;
			}
			_completed = true;
			_completedAtMs = System.currentTimeMillis();
			_completeAnnouncementPending.set(true);
			return true;
		}
	}

	private final FakePlayerSyntheticScenarioData _scenarioData;
	private final FakePlayerDebugService _debugService;
	private final ConcurrentHashMap<String, ScenarioProgress> _progressByFpcId = new ConcurrentHashMap<>();

	public FakePlayerSyntheticScenarioService(FakePlayerSyntheticScenarioData scenarioData, FakePlayerDebugService debugService)
	{
		_scenarioData = scenarioData;
		_debugService = debugService;
	}

	public FpcSyntheticScenarioDefinition resolveScenario(FpcDefinition definition)
	{
		if ((definition == null) || (definition.getAdventurerProfile() == null) || !definition.getAdventurerProfile().isAdventurerTier())
		{
			return null;
		}

		final String fpcId = normalize(definition.getId());
		for (FpcSyntheticScenarioDefinition scenario : _scenarioData.getScenarios())
		{
			if ((scenario == null) || !scenario.isEnabled() || !scenario.isAssignedTo(definition))
			{
				continue;
			}

			final ScenarioProgress progress = _progressByFpcId.get(fpcId);
			if ((progress != null) && progress.isCompleted() && !scenario.isRepeatable())
			{
				return null;
			}
			return scenario;
		}
		return null;
	}

	public ScenarioProgress getOrStartProgress(FpcDefinition definition, FpcSyntheticScenarioDefinition scenario)
	{
		if ((definition == null) || (scenario == null))
		{
			return null;
		}
		return _progressByFpcId.computeIfAbsent(normalize(definition.getId()), key ->
		{
			LOGGER.info(() -> FakePlayerSyntheticScenarioService.class.getSimpleName() + ": AFPC " + definition.getId() + " started synthetic scenario=" + scenario.getId() + " sourceQuestId=" + scenario.getSourceQuestId() + " objectiveZone=" + scenario.getObjectiveZoneId());
			_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=synthetic_scenario_start scenario=" + scenario.getId() + " sourceQuestId=" + scenario.getSourceQuestId());
			return new ScenarioProgress(scenario.getId());
		});
	}

	public boolean recordKill(FpcDefinition definition, Npc target)
	{
		if ((definition == null) || (target == null))
		{
			return false;
		}

		final FpcSyntheticScenarioDefinition scenario = resolveScenario(definition);
		if ((scenario == null) || !scenario.matchesTargetNpcId(target.getId()))
		{
			return false;
		}

		final ScenarioProgress progress = getOrStartProgress(definition, scenario);
		if ((progress == null) || progress.isCompleted())
		{
			return false;
		}

		final int updatedCount = progress.incrementTargetCount(scenario.getTargetCount());
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " recorded synthetic scenario kill=" + scenario.getId() + " targetNpcId=" + target.getId() + " count=" + updatedCount + "/" + scenario.getTargetCount());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=synthetic_scenario_kill scenario=" + scenario.getId() + " targetNpcId=" + target.getId() + " count=" + updatedCount + "/" + scenario.getTargetCount());
		return true;
	}

	public boolean recordObjectiveProgress(FpcDefinition definition, FpcSyntheticScenarioDefinition scenario, int targetNpcId, String source)
	{
		if ((definition == null) || (scenario == null) || !scenario.matchesTargetNpcId(targetNpcId))
		{
			return false;
		}

		final ScenarioProgress progress = getOrStartProgress(definition, scenario);
		if ((progress == null) || progress.isCompleted())
		{
			return false;
		}

		final int updatedCount = progress.incrementTargetCount(scenario.getTargetCount());
		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " advanced synthetic scenario=" + scenario.getId() + " source=" + safe(source) + " targetNpcId=" + targetNpcId + " count=" + updatedCount + "/" + scenario.getTargetCount());
		_debugService.trace(definition.getId(), FpcDebugCategory.COMBAT, "event=synthetic_scenario_progress scenario=" + scenario.getId() + " source=" + safe(source) + " targetNpcId=" + targetNpcId + " count=" + updatedCount + "/" + scenario.getTargetCount());
		return true;
	}

	public boolean markCompleted(FpcDefinition definition, FpcSyntheticScenarioDefinition scenario)
	{
		if ((definition == null) || (scenario == null))
		{
			return false;
		}

		final ScenarioProgress progress = _progressByFpcId.get(normalize(definition.getId()));
		if ((progress == null) || !progress.getScenarioId().equalsIgnoreCase(scenario.getId()) || !progress.markCompleted())
		{
			return false;
		}

		LOGGER.info(() -> getClass().getSimpleName() + ": AFPC " + definition.getId() + " completed synthetic scenario=" + scenario.getId() + " sourceQuestId=" + scenario.getSourceQuestId() + " count=" + progress.getTargetCount() + "/" + scenario.getTargetCount());
		_debugService.trace(definition.getId(), FpcDebugCategory.LIFECYCLE, "event=synthetic_scenario_complete scenario=" + scenario.getId() + " sourceQuestId=" + scenario.getSourceQuestId() + " count=" + progress.getTargetCount() + "/" + scenario.getTargetCount());
		return true;
	}

	public void clearRuntimeState(String definitionId)
	{
		_progressByFpcId.remove(normalize(definitionId));
	}

	private String normalize(String value)
	{
		return ((value == null) || value.isBlank()) ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private String safe(String value)
	{
		return (value == null) ? "" : value.trim();
	}
}
