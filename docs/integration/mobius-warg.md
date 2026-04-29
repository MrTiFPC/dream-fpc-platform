# Mobius Warg Integration Notes

The first public integration target is L2J Mobius Essence 09.1 Warg.

## Status

This release is a Warg-first source and integration kit. It is not a standalone game server distribution.

The public platform source keeps Mobius package context so it can be reviewed and integrated into a matching Warg server tree. The Mobius-specific material is separated under `integration/mobius`.

## Expected Public Inputs

- `platform/java`: FPC platform Java source with Mobius package context.
- `control-plane/sidecar`: Studio and sidecar control-plane code.
- `content/warg/fake_players`: public Warg FPC data examples.
- `content/warg/npcs`: public Warg FPC carrier NPC examples.
- `integration/mobius/config/FakePlayerPlatform.ini`: public Warg community config template.
- `integration/mobius/sql`: public FPC social and hybrid-clan schemas.

## Integration Shape

1. Copy the platform Java files into the matching package paths in a Warg source tree.
2. Copy the config template into the Warg `dist/game/config/Custom` folder.
3. Copy the Warg FPC content into the matching `dist/game/data` folders.
4. Apply the SQL schemas to the game database if you enable social memory or hybrid clan state.
5. Apply Mobius host hooks or patches once the public patch kit is generated.
6. Build the Warg server from source and validate logs before in-game testing.

## Boundaries

- The GameServer remains authoritative for gameplay.
- The sidecar is advisory and owns external personality/control-plane workflows.
- The public Warg examples are community examples, not a protected runtime pack.
- Keep integration patches visibly separate from platform code.
