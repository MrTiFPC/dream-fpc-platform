# Local LLM Notes

The public sidecar can run with local model help through Ollama, but Ollama is not required for every workflow.

Use these notes to decide how much model setup you want.

## Modes

### Rules-only mode

Use this when you want to run Studio, inspect data, use deterministic routing, or test the public sidecar without a local model.

```powershell
$env:FAKEPLAYER_PLANNER_PROVIDER = "rules"
$env:FAKEPLAYER_REPLY_PROVIDER = "rules"
py -3 -m uvicorn app:app --host 127.0.0.1 --port 8000
```

In this mode, model-authored personality replies are disabled. The sidecar should still expose Studio, health, data, traces, tests, and deterministic behavior where supported.

### Ollama reply mode

Use this when you want local model-authored replies.

Install Ollama from the official docs:

```text
https://docs.ollama.com/
https://ollama.com/download
```

Then install or select a local model:

```powershell
ollama pull llama3.2:3b
```

Configure the sidecar to use that installed model:

```powershell
$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
$env:OLLAMA_MODEL = "llama3.2:3b"
$env:OLLAMA_REPLY_MODEL = "llama3.2:3b"
$env:FAKEPLAYER_PLANNER_PROVIDER = "rules"
$env:FAKEPLAYER_REPLY_PROVIDER = "ollama"
py -3 -m uvicorn app:app --host 127.0.0.1 --port 8000
```

The planner is kept on `rules` by default because gameplay routing should stay predictable. The local model is best used for reply style and natural language shaping.

## Public Defaults

`env_template.md` includes the tuning keys used by the public sidecar.

The name `llama3.2-3b-fpc` is a local model alias from development and is not bundled in this repository. If you do not have that alias, set `OLLAMA_MODEL` and `OLLAMA_REPLY_MODEL` to a model that exists on your machine.

Check local models with:

```powershell
ollama list
```

## Studio Assistant Notes

The Studio assistant can use Ollama for embeddings and richer grounded answers when matching local models are available.

The default embedding model name is:

```text
embeddinggemma
```

If you want assistant indexing with embeddings, install the model with Ollama or set `FPC_CONSOLE_EMBEDDING_MODEL` to another local embedding model. If no embedding model is available, lexical retrieval remains the safer fallback.

## Boundaries

- No cloud model account is required for the public sidecar.
- No model weights are included in this repository.
- Local models can be slow on small machines; start with a small model before trying larger ones.
- AI output is advisory. The Warg server remains authoritative for gameplay.
