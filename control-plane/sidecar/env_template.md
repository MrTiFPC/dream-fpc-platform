# Environment Template

Use these environment variables when running the fake-player Ollama sidecar locally.

```env
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_HEALTH_PATH=/api/tags
OLLAMA_CHAT_PATH=/api/chat
OLLAMA_MODEL=llama3.2-3b-fpc
OLLAMA_TIMEOUT_SECONDS=45.0
OLLAMA_PLANNER_MODEL=llama3.2-3b-fpc
OLLAMA_REPLY_MODEL=llama3.2-3b-fpc
OLLAMA_PLANNER_TIMEOUT_SECONDS=6.0
OLLAMA_REPLY_TIMEOUT_SECONDS=12.0
OLLAMA_TOP_P=0.90
OLLAMA_REPEAT_PENALTY=1.08
OLLAMA_SOCIAL_TEMPERATURE_FLOOR=0.45
FAKEPLAYER_PLANNER_PROVIDER=rules
FAKEPLAYER_REPLY_PROVIDER=ollama
```

## Notes

- `OLLAMA_BASE_URL` should point to your local Ollama server.
- `OLLAMA_HEALTH_PATH` is used by `/health` to check Ollama reachability.
- `OLLAMA_CHAT_PATH` is the structured chat endpoint used by the sidecar.
- `OLLAMA_MODEL` is the default local model name used when the request does not override it.
- `OLLAMA_TIMEOUT_SECONDS` controls outbound Ollama request timeout.
- `OLLAMA_PLANNER_MODEL` and `OLLAMA_REPLY_MODEL` let planner/reply scope use different local models.
- `OLLAMA_PLANNER_TIMEOUT_SECONDS` and `OLLAMA_REPLY_TIMEOUT_SECONDS` keep the two scopes on separate latency budgets.
- `OLLAMA_TOP_P` and `OLLAMA_REPEAT_PENALTY` tune reply variety without changing the live model.
- `OLLAMA_SOCIAL_TEMPERATURE_FLOOR` keeps low-risk social chat from freezing into flat kiosk phrasing.
- `FAKEPLAYER_PLANNER_PROVIDER` accepts `rules` or `ollama`; current recommended default is `rules`.
- `FAKEPLAYER_REPLY_PROVIDER` accepts `rules` or `ollama`; current recommended default is `ollama`.
- current tested live reply baseline is `llama3.2-3b-fpc`; keep planner on `rules` unless a later benchmark proves a cheap planner model is worth it.
- current humanized live tuning bundle is `personalityTemperature=0.18`, `OLLAMA_TOP_P=0.90`, `OLLAMA_REPEAT_PENALTY=1.08`, `OLLAMA_SOCIAL_TEMPERATURE_FLOOR=0.45`, and `whisperMaxWords=20`.
