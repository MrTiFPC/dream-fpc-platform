# Probe Examples

Use these examples to verify the local fake-player Ollama sidecar before connecting more of the Java draft.

## Health probe

Request:

```bash
curl http://127.0.0.1:8000/health
```

Expected fields:
- `status`
- `service`
- `ollamaReachable`
- `ollamaBaseUrl`
- `defaultModel`

## Personality probe

Request using the sample payload file:

```bash
curl -X POST http://127.0.0.1:8000/personality/decide \
  -H "Content-Type: application/json" \
  --data @sample_request.json
```

Expected advisory response shape:

```json
{
  "moodTag": "confident",
  "intentPreference": "farm",
  "zoneBias": "abandoned_camp",
  "aggressionRiskScore": 0.61,
  "speakNow": true,
  "lineStyleTag": "casual_short",
  "topicTag": "grind",
  "confidence": 0.84
}
```

## PowerShell health probe

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -Method Get
```

## PowerShell personality probe

```powershell
$body = Get-Content .\sample_request.json -Raw
Invoke-RestMethod -Uri "http://127.0.0.1:8000/personality/decide" -Method Post -ContentType "application/json" -Body $body
```

## What to verify

- `ollamaReachable` becomes `true` once Ollama is running
- the returned `intentPreference` is one of the request `allowedIntents`
- the returned `zoneBias` is one of the request `allowedZones`
- the returned `lineStyleTag` is one of the request `availableLineStyleTags`
- the returned `topicTag` is one of the request `availableTopicTags`
- `aggressionRiskScore` is within `0.0..1.0`
- `confidence` is within `0.0..1.0`

## Failure expectations

If Ollama is unavailable or returns malformed output:
- the sidecar should still return a safe default advisory payload
- the Java adapter should still fall back safely if the sidecar itself is unreachable
