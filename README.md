# Dream FPC Platform

Dream FPC Platform is an experimental AI/FPC platform for server-side game NPC and player-simulation workflows.

The first public lane is **L2J Mobius Essence 09.1 Warg**. The platform code, control plane, content examples, and Mobius integration material in this release are Warg-first and should be read as an early community source drop, not a broad all-games support claim.

## What Is Included

- Public FPC platform Java source under `platform/java`.
- Public FPC Studio sidecar/control-plane code under `control-plane/sidecar`.
- Warg example FPC data and NPC carrier examples under `content/warg`.
- Warg/Mobius config and SQL schema material under `integration/mobius`.
- Public release notes, attribution, and roadmap docs under `docs`.

## Current Boundaries

- This is an unofficial integration for L2J Mobius Essence 09.1 Warg.
- L2J Mobius is credited as the first supported host and integration target.
- The first public release is Warg-only.
- RoseVain and Classic work are deferred and are not included in this first public release.
- Broader host portability is a roadmap direction, not a current support guarantee.
- No NCSoft client files, geodata, private/subscriber Mobius distribution files, build output, runtime server folders, or local machine artifacts are included.

## Layout

```text
dream-fpc-platform/
  README.md
  LICENSE
  NOTICE.md
  docs/
    integration/
    release/
    roadmap/
  platform/
    java/
  control-plane/
    sidecar/
  content/
    warg/
  integration/
    mobius/
      config/
      patches/
      sql/
```

## How To Read This Release

The `platform/` tree contains the FPC platform source that currently integrates with Mobius packages.

The `control-plane/` tree contains the public FPC Studio sidecar: local authoring, Studio UI, assistant/runtime helpers, sample requests, and contract tests.

The `content/` tree contains public Warg example data and schemas. It uses the public `availabilityProfile` key and generic sample player labels.

The `integration/mobius/` tree is intentionally separate from the platform code. Mobius-specific hooks, config, SQL, and future patches belong there so the project stays readable as an FPC platform first and a Mobius host integration second.

## Quick Start

For fuller setup notes, read `INSTALL.md`.

1. Start from a clean L2J Mobius Essence 09.1 Warg workspace.
2. Review `docs/integration/mobius-warg.md`.
3. Place the platform Java source into the matching Mobius package paths.
4. Copy the Warg config and example data into the matching datapack locations.
5. Apply the Warg host hook patch with `integration/mobius/apply-warg-host-hooks.ps1`.
6. Start the sidecar from `control-plane/sidecar` if you want Studio or external personality decisions.
7. Build and validate in your own Warg server runtime.

The public source is intentionally conservative. It is meant to be inspected, adapted, and improved by server operators who understand their own Mobius tree.

## License And Attribution

Project-owned files are released under the MIT License unless a file states otherwise. Mobius-derived or Mobius-integrated files retain their existing upstream notices.

See `LICENSE`, `NOTICE.md`, and `docs/release/disclaimer.md`.
