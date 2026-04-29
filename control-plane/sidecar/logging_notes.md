# Logging Notes

## Goal

Keep the first live runs diagnosable without turning the alphastage sidecar into a noisy production logging system.

## Minimum fields to log

### Sidecar
- `event`
- `fakePlayerId`
- `model`
- `recentEvent`
- `currentZone`
- `latencyMs`
- `ollamaReachable`
- `fallback`
- `reason`

### Java adapter
- adapter health probe result
- sidecar HTTP status
- fake player id
- retry attempt
- parse rejection reason
- fallback to `PersonalityDecision.DEFAULT`

## Recommended event names

### Sidecar
- `sidecar_health_probe`
- `personality_request_start`
- `personality_request_success`
- `personality_request_fallback`
- `ollama_call_failure`

### Java adapter
- `adapter_health_failed`
- `adapter_request_attempt`
- `adapter_http_non_200`
- `adapter_parse_failed`
- `adapter_fallback_default`

## First-run focus

During the first live runs, the most useful questions are:
- is the sidecar reachable?
- is Ollama reachable?
- how often are we falling back?
- are advisory payloads being rejected for schema or parsing reasons?
- are timeouts too aggressive?

## Safety note

Do not log raw gameplay commands or any authority-changing output because the advisory system must not become an execution channel.

## Privacy note

Keep logs narrow and technical. Avoid logging unnecessary user chat content.
