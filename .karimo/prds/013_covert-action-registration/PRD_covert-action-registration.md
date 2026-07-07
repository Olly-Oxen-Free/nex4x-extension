# PRD: Covert Action Registration Fix

**Slug:** covert-action-registration
**Created:** 2026-05-31
**Severity:** P0
**Source:** 2026-05-31 nex4x flaw audit

## Vision

All 7 nex4x covert action defs are registered with Nex's `CovertOpsManager` at the correct
lifecycle point (game-load, after Nex has constructed the manager), appear in the agent
orders dialog, run through Nex's scheduling/detection/resolution pipeline, and are sorted
correctly relative to vanilla actions.

## Why now

Registration currently fires from `Nex4xModPlugin.onApplicationLoad` (line 82). The first
access to `CovertOpsManager.actionDefsById` triggers Nex's `static{}` initializer (confirmed
via `javap`), which calls `Global.getSector().getAllFactions()` at offset 95/98 — but
`Global.getSector()` is **null at application load**. The result is an
`ExceptionInInitializerError`, swallowed by the `catch (Throwable t)` at line 88 of
`Nex4xCovertActionRegistry.java`. This is a **silent no-op**: all 7 defs are never added,
`auditAgents()` reports "0/7", and none of the custom covert actions appear in-game.

Secondary issue: even if registration succeeded, `sortOrder=100f` entries are appended to
`actionDefs` after Nex's `loadSettings` has already called `Collections.sort(actionDefs)`
(confirmed at bytecode offset 445 of `loadSettings`). The list is not re-sorted, so the 7
defs appear at the end of the UI in insertion order rather than sort-order position.

## Success criteria

- `auditAgents()` reports "Registered nex4x defs in CovertOpsManager: 7/7" on every
  game-load (new game and loaded save).
- All 7 defs appear in the agent orders dialog, sorted by `sortOrder` relative to vanilla.
- No double-registration on save/load cycle (idempotency guard holds).
- `./build.sh` compiles without errors or warnings introduced by this change.
- In-game runcode smoke: `nex4x.debug.Nex4xDebugCommand.auditAgents()` returns
  "7/7" after loading any save; `nex4x.debug.Nex4xDebugCommand.auditCovertDefs()` lists
  all 7 defs with their sort positions relative to surrounding vanilla defs.

## Scope

**In:**
- Move `Nex4xCovertActionRegistry.register()` call from `onApplicationLoad` (line 82) to
  `onGameLoad` in `Nex4xModPlugin.java`, after `Nex4xManager.getOrCreateManager()` (line 142),
  guarded by a Nex-loaded check.
- Add `Collections.sort(CovertOpsManager.actionDefs)` call after appending each def, within
  `Nex4xCovertActionRegistry.registerOne` (after the map put at line 125), or as a
  single sort after all 7 are appended (end of `register()`, before the log at line 84).
- Extend `Nex4xDebugCommand.auditAgents` (line 129) to assert all 7 def ids present and
  print their resolved sort positions; or add a companion `auditCovertDefs()` method.

**Out:**
- No changes to the 7 action implementation classes (`DeepCoverAction`, etc.).
- No changes to `ActionDefIds`.
- No changes to Nex config or data files.
- Nex `CovertOpsManager` source is third-party — no patches to the jar.

## Risks

- **Nex not yet active at `onGameLoad`**: Nex's `ExerelinModPlugin.onGameLoad` runs before or
  after ours depending on mod load order. Guard with `CovertOpsManager.getManager() != null`
  or a class-exists check rather than relying on order. The static fields (`actionDefs`,
  `actionDefsById`) are initialized by the class's `static{}` block, which fires at first
  class-load — safe as long as `Global.getSector()` is non-null (it is, at game-load).
- **`Collections.sort` comparator**: `CovertActionDef` must implement `Comparable` or Nex
  must pass a `Comparator` to `sort`. Confirm via `javap` that `CovertActionDef` implements
  `Comparable` before calling the zero-arg overload. If not, sort with
  `Comparator.comparingDouble(d -> d.sortOrder)`.
- **Double-sort cost**: Re-sorting after every `registerOne` call is O(n log n) × 7; sorting
  once after all 7 are appended is preferred and already guarded by the idempotency check.
- **Save-cycle double-add**: The idempotency guard in `registerOne` (line 100,
  `actionDefsById.containsKey(id)`) prevents double-add; it must remain in place.

## Acceptance

- `./build.sh` exits 0 with no new errors.
- In-game runcode: `nex4x.debug.Nex4xDebugCommand.auditAgents()` — "7/7" on a fresh new
  game AND on a loaded existing save.
- In-game runcode: `nex4x.debug.Nex4xDebugCommand.auditCovertDefs()` — lists all 7 defs
  with names, sort positions, and adjacent vanilla def names to confirm ordering.
- Save → load → `auditAgents()` again: still "7/7" (no double-add, no loss).

## Tasks

See `tasks.yaml`.
