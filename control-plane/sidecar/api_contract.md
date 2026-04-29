# Sidecar API Contract

## Overview

The sidecar exposes a narrow local HTTP API for advisory fake-player personality decisions.

It does not execute gameplay actions.

The gameserver remains authoritative.

## Base URL

Default local development target:
- `http://127.0.0.1:8000`

## Ollama upstream target

Default local Ollama target used by `app.py`:
- base URL: `http://127.0.0.1:11434`
- health path: `/api/tags`
- chat path: `/api/chat`

These are configurable through environment variables.

## Endpoints

### `GET /health`

Purpose:
- confirm the sidecar process is alive
- confirm whether local Ollama is reachable
- expose the configured provider split, model split, timeout split, and active reply-sampling knobs

Current response shape:

```json
{
  "status": "ok",
  "service": "fakeplayer-ollama-sidecar",
  "ollamaReachable": true,
  "ollamaBaseUrl": "http://127.0.0.1:11434",
  "defaultModel": "llama3.2-3b-fpc",
  "plannerProvider": "rules",
  "replyProvider": "ollama",
  "plannerModel": "llama3.2-3b-fpc",
  "replyModel": "llama3.2-3b-fpc",
  "plannerTimeoutSeconds": 6.0,
  "replyTimeoutSeconds": 12.0,
  "replyTopP": 0.9,
  "replyRepeatPenalty": 1.08,
  "socialTemperatureFloor": 0.45
}
```

### `POST /personality/decide`

Purpose:
- receive a `PersonalityContext`
- query local Ollama
- return a validated `PersonalityDecision`
- support reply-scope advisory generation from a narrow server-built context slice rather than full game state

## Request body

Expected request fields:
- `fakePlayerId` : string
- `archetype` : string
- `currentZone` : string
- `state` : string
- `hpBand` : string
- `mpBand` : string
- `nearbyPlayerCount` : integer
- `decisionScope` : string
- `recentEvent` : string
- `chatCooldownReady` : boolean
- `teleportCooldownReady` : boolean
- `allowedIntents` : string array
- `allowedZones` : string array
- `availableLineStyleTags` : string array
- `availableTopicTags` : string array
- `lastLineTag` : string
- `boredomScore` : number
- `model` : string
- `temperature` : number

Reply scopes also commonly include a narrow in-world context slice so the sidecar can answer like the addressed FPC without receiving broad gameplay authority:
- persona canon fields such as `personaSummary`, `selfKnowledgeSummary`, `publicMaskSummary`, `hiddenTruthSummary`, `selfConcept`, `coreWound`, `coreDesire`, `loyaltyAnchor`, `resentmentAnchor`, `privateTaboo`, `speechAnchor`, and `privateContradiction`
- focus and bond fields such as `focusEntityName`, `focusEntityType`, `focusIntent`, and `focusSummary`
- relationship and memory fields such as `relationshipSummary`, `matchedRelationshipSummary`, `recentConversationSummary`, `salientMemorySummary`, `currentRelationshipGoal`, `currentRelationshipNeed`, and `lastRelationshipTopic`
- social state fields such as `socialLabel`, `socialSummary`, `socialTrustBias`, `socialGuardBias`, `socialActionStance`, and `sceneSummary`
- local reply guidance such as `messageCategory`, `replyBankSummary`, `replyBankLines`, `knowledgeType`, `knowledgeSummary`, `knowledgeFacts`, `knowledgeReplyLines`, `knowledgeHeuristicLines`, `knowledgeScope`, and `knowledgeConfidenceHint`
- the incoming player turn via `incomingPlayerName` and `incomingPlayerMessage`

Typical `decisionScope` values:
- `planner`
- `whisper_reply`
- `general_reply`
- `shout_reply`
- `world_reply`

## Response body

Expected response fields:
- `moodTag` : string
- `intentPreference` : string
- `zoneBias` : string
- `aggressionRiskScore` : number
- `speakNow` : boolean
- `lineStyleTag` : string
- `topicTag` : string
- `confidence` : number

## Runtime normalization rules

The current sidecar implementation normalizes that:
- `intentPreference` is in `allowedIntents`
- `zoneBias` is in `allowedZones` or safely falls back
- `lineStyleTag` is in `availableLineStyleTags`
- `topicTag` is in `availableTopicTags`
- `aggressionRiskScore` is clamped to `0.0..1.0`
- `confidence` is clamped to `0.0..1.0`
- `speakNow` is gated by `chatCooldownReady`

## Error handling

On any upstream or parsing failure, the current sidecar implementation returns a safe default advisory payload instead of propagating raw model errors.

This means the Java adapter can continue to operate with conservative fallback behavior.

## Provider split

The sidecar now supports a per-scope provider split:
- planner scope can use `rules` or `ollama`
- reply scopes can use `rules` or `ollama`

Current recommended baseline:
- planner provider = `rules`
- reply provider = `ollama`
- reply model = `llama3.2-3b-fpc`
- reply sampling = `top_p=0.90`, `repeat_penalty=1.08`
- low-risk social temperature floor = `0.45`

## Phase-1 rules

- no raw generated player-facing text as primary output
- no direct movement commands
- no direct teleport commands
- no direct target ids
- no direct combat or skill instructions
- no packet-level directives

The output remains advisory only.

## Reply pipeline

The current live reply path is intentionally split across gameserver and sidecar ownership.

1. Player talks to the NPC/FPC.
   - Whisper ingress enters through the GameServer fake-player hooks and the public channels (`general`, `shout`, `world`) resolve addressed talkable FPCs first.

2. The server checks the message before the sidecar sees it.
   - `FakePlayerConversationGuardService` classifies sensitive/meta/unsupported requests.
   - `FakePlayerMessageClassifier` and the social-signal path classify normal chat, bond pressure, hostility, thanks, repair, and other reply categories.

3. The server builds the FPC brain context.
   - `FakePlayerDecisionEngine.buildReplyContext(...)` resolves persona canon, matched relationship summary, recent short-term conversation memory, selective longer memory, trust/guard bias, relationship goal/need/topic, reply-bank guidance, and knowledge-card guidance.
   - `FakePlayerChatService` owns short-term conversation memory, salient memory, and relationship state.
   - `FakePlayerSocialMemoryService` and `FakePlayerSocialService` contribute emotional/social memory and current reply stance.

4. The server sends only a small advisory summary to the sidecar.
   - The Java adapter serializes `PersonalityContext`, which is intentionally narrow and advisory-only.
   - It does not send the whole game or transfer gameplay authority.

5. The sidecar builds a suggested reply.
   - `app.py` applies rules, local-model prompting, reply-bank and knowledge guidance, relationship/memory guidance, anti-echo handling, anti-repeat handling, and generic-filler suppression before returning a `PersonalityDecision`.

6. The server checks the suggested reply again.
   - `FakePlayerModule` sanitizes the returned line for prefix stripping, length, sentence shape, and echo reduction.
   - `FakePlayerReplyPolicyService` performs the second server-side guard pass for meta leaks, unsupported promises, and social-tone mismatch, and can repair the line with a safer fallback.

7. The final chat line is emitted only if it survives the server checks.
   - `FakePlayerChatService` handles the actual visible/public/private send.
   - If no safe usable reply survives, the GameServer falls back to a local safe line instead of trusting raw model output.

8. Memory is updated after the spoken exchange.
   - Successful replies feed short-term conversation memory, salient memory, relationship state, and pair social episodes back into the server-owned memory services for the next turn.

This keeps the GameServer authoritative while still allowing the sidecar to advise on personality, tone, topic, and direct short-form reply wording.
