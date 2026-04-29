# Smoke Test

Run this raw live test in order.

## 1. Make sure Ollama is up

Check Ollama locally:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -Method Get
```

If that fails, start Ollama and make sure your imported model, for example `qwen3-14b-local`, is available.

## 2. Start the sidecar

From `alphastage\fakeainpcs\sidecar`, run your FastAPI app with uvicorn so it serves `app:app` on port `8000`.

## 3. Health probe

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -Method Get
```

What you want to see:
- `status = ok`
- `ollamaReachable = true`
- `defaultModel` matches your intended model

## 4. Advisory request probe

```powershell
$body = Get-Content .\sample_request.json -Raw
Invoke-RestMethod -Uri "http://127.0.0.1:8000/personality/decide" -Method Post -ContentType "application/json" -Body $body
```

Minimum success checks:
- `moodTag` is present
- `intentPreference` is present
- `zoneBias` is present
- `lineStyleTag` is present
- `topicTag` is present
- `aggressionRiskScore` is between `0.0` and `1.0`
- `confidence` is between `0.0` and `1.0`

## 5. Fallback test

Stop Ollama, keep the sidecar running, then call the advisory endpoint again:

```powershell
$body = Get-Content .\sample_request.json -Raw
Invoke-RestMethod -Uri "http://127.0.0.1:8000/personality/decide" -Method Post -ContentType "application/json" -Body $body
```

What you want to see:
- the sidecar still returns a safe advisory payload
- `moodTag` should fall back to a calm/default value
- `speakNow` should remain conservative
- logs should show a fallback event

## 6. Sidecar log events to watch

- `sidecar_health_probe`
- `personality_request_start`
- `personality_request_success`
- `personality_request_normalized`
- `personality_request_fallback`

## 7. Java-side expectation

Once the sidecar is healthy:
- Java adapter health probe should pass
- advisory requests should stop falling back immediately
- incomplete payloads should still be rejected on the Java side
