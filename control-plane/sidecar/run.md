# Running The Public Sidecar

From `control-plane/sidecar`:

```powershell
py -3 -m pip install -r requirements.txt
py -3 -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Open:

```text
http://127.0.0.1:8000/studio
```

Use the public Warg config template from the integration kit when wiring this sidecar to the server.

For local model setup, rules-only mode, and Ollama notes, read `llm_notes.md`.

This public staging copy intentionally excludes training data, local model files, generated logs, private release tooling, and the personal desktop console shell.
