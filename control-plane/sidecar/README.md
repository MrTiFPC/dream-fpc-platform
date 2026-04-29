# FPC Studio Sidecar

Public Warg-first control plane for the community FPC platform.

This staged copy includes the FastAPI sidecar, bounded project assistant runtime, FPC Studio web UI, safe sample requests, and lightweight contract tests.

## Scope

- First supported host: L2J Mobius Essence 09.1 Warg.
- Public runtime posture: community/dev, no protected Core product flow.
- Deferred from this public pass: training pipelines, generated model artifacts, personal desktop console shell, private release/customer tooling, and non-Warg lanes.

## Run

```powershell
py -3 -m pip install -r requirements.txt
py -3 -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Studio is served from:

```text
http://127.0.0.1:8000/studio
```

The sidecar expects to run beside a Warg integration tree that provides `dist/game/data/fake_players` and Studio runtime snapshots.

For local model setup, rules-only mode, and Ollama notes, read `llm_notes.md`.

## Checks

```powershell
py -3 -m py_compile app.py assistant_runtime.py tests\test_studio_introspection_contract.py tests\test_studio_assistant_contract.py
node --check studio\assets\studio.js
node --check tools\run_probe_suite.cjs
```
