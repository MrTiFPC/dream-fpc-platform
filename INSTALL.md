# Install Notes

These notes are for integrating Dream FPC Platform into a clean L2J Mobius Essence 09.1 Warg source tree.

This repository is not a complete game server distribution. It does not include NCSoft client files, geodata, private/subscriber Mobius files, generated server output, or a one-click installer.

## Requirements

- A clean L2J Mobius Essence 09.1 Warg source workspace.
- Java and Ant configured the same way your Warg tree normally builds.
- Python 3.11 or newer for the optional sidecar and Studio.
- Node.js if you want to run the included JavaScript syntax checks.
- Access to your game database if you enable the included SQL-backed social or hybrid clan state.

## What To Copy

Copy only the approved folders you need. Do not copy this whole repository into a Warg server root.

```text
platform/java/                  -> <warg-source>/java/
integration/mobius/config/*.ini -> <warg-source>/dist/game/config/Custom/
integration/mobius/sql/*.sql    -> your game database or DB installer flow
content/warg/fake_players/*.json -> <warg-source>/dist/game/data/fake_players/
content/warg/npcs/fpc_*.xml     -> <warg-source>/dist/game/data/stats/npcs/custom/
```

Keep `integration/mobius` separate when reviewing host-specific changes. Platform source and Mobius integration hooks are intentionally separate.

## Important Host Hook Note

The platform Java source needs the matching Mobius host hooks to load config, bootstrap the FPC module, load FPC carrier NPC data, and route the selected chat/admin surfaces.

If your Warg tree does not already have those hooks, apply the public Mobius Warg patch kit when it is available, or port the hooks manually from the integration notes. Without those hooks, copied platform files can compile but the runtime may not start or expose FPC behavior.

Start with:

```text
docs/integration/mobius-warg.md
```

## Basic Install Flow

1. Start from a clean Warg source workspace.
2. Copy `platform/java` into the matching `java` package tree.
3. Copy `integration/mobius/config/FakePlayerPlatform.ini` into `dist/game/config/Custom`.
4. Copy `content/warg/fake_players` into `dist/game/data/fake_players`.
5. Copy `content/warg/npcs/fpc_*.xml` into `dist/game/data/stats/npcs/custom`.
6. Apply the SQL files under `integration/mobius/sql` if you enable those features.
7. Apply or port the Mobius host hooks described in the integration notes.
8. Build the Warg server from source with your normal Mobius build target.
9. Start the server only after the build/deploy step is fully complete.
10. Validate logs first, then test in game.

## Optional Sidecar And Studio

The sidecar is optional for Studio and external personality/control-plane workflows.

From `control-plane/sidecar`:

```powershell
py -3 -m pip install -r requirements.txt
py -3 -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Then open:

```text
http://127.0.0.1:8000/studio
```

The sidecar is advisory. Gameplay authority remains in the server.

For local model setup, rules-only mode, and Ollama notes, read:

```text
control-plane/sidecar/llm_notes.md
```

## Quick Checks

From `control-plane/sidecar`:

```powershell
py -3 -m py_compile app.py assistant_runtime.py tests\test_studio_introspection_contract.py tests\test_studio_assistant_contract.py
node --check studio\assets\studio.js
node --check tools\run_probe_suite.cjs
py -3 -m unittest discover -s tests -v
```

In your Warg source tree, run your normal Java build after copying platform and integration files.

## First FPC Walkthrough

For a creator-facing first pass, read:

```text
control-plane/sidecar/FIRST_FPC_IN_WARG_TUTORIAL.md
```

## Troubleshooting

- If Java compiles but no FPC behavior appears, check the Mobius host hooks and `FakePlayerPlatform.ini`.
- If FPC carrier NPCs do not load, check the copied `fpc_*.xml` files and any host-side fake-player NPC load gate.
- If Studio opens but has no useful data, confirm the sidecar can see the expected Warg data paths and runtime snapshots.
- If startup errors appear immediately after deployment, do one clean post-deploy restart before assuming source logic is broken.
