# PRD: Agent Ownership Wiring

**Slug:** agent-ownership-wiring
**Created:** 2026-05-31
**Severity:** P1
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every live `AgentIntel` that nex4x cares about has a populated `ownerFactionId` in its
`Nex4xAgentData` companion record from the first daily tick onward — not only after a
reported covert action completes. Diplomat passive influence drip and expulsion cascade
apply to all qualifying agents unconditionally; no agent is silently skipped because its
record was never created or its owner field never set.

## Why now

`DiplomatPassiveManager.advanceDay` (line 31) and `DiplomatExpulsionCascade.apply`
(line 24) both guard on `ownerFactionId != null`. The field is written in exactly one
place — `Nex4xAgentActionReportListener.reportAgentAction` (line 45) — and only after a
Nex-reported covert action resolves. Companion records are also created only there (line
41-42). A diplomat that has never completed a reported action produces zero passive
influence and is immune to expulsion cascade for the entire duration of a campaign. This
compounds PRD-013 (covert actions never register), but ownership wiring is independently
broken: even if covert registration were fixed, a diplomat assigned mid-save and advancing
several in-game days before its first action resolves would still be invisible to both
systems.

Key verified facts (line numbers as of 2026-05-31 audit):

- `Nex4xAgentData.ownerFactionId` — field at line 14; getter/setter lines 46-47.
- `Nex4xAgentActionReportListener.reportAgentAction` — sole writer, lines 41-47.
- `DiplomatPassiveManager.advanceDay` — null-owner skip at line 31.
- `DiplomatExpulsionCascade.apply` — null-owner skip at line 24.
- `Nex4xAgentManager.advanceAll` / companion record store — lines 54-58, map at line 22.
- `Nex4xManager` v3 daily tick — calls `Nex4xAgentManager.getOrCreate().advanceAll` at
  line 162, then iterates factions for `DiplomatPassiveManager.advanceDay` at line 167.
- Discovery path: `AgentIntel.getAgentsStatic()` returns all live `AgentIntel`; each has
  a `faction` field (protected) exposed indirectly — the owning faction is accessible from
  `CovertActionIntel.getAgentFaction()` for action-scoped contexts, or by reading
  `AgentIntel.faction` via the constructor's faction parameter. No public `getFaction()`
  exists on `AgentIntel` itself; `updateAgentDisplayedFaction()` returns a display-only
  copy. The canonical approach is `AgentIntel.getAgentsStatic()` for discovery +
  `AgentTypeMap.fromAgent(ai)` for type + the `faction` field accessed reflectively or via
  the existing `CovertActionIntel.getAgentFaction()` pattern in the listener (which already
  has it as `action.getAgentFaction()`). For the sweep path (no action context), use
  `AgentIntel.updateAgentDisplayedFaction()` as the faction source — it is the least-bad
  public API available without reflection.

## Success criteria

1. After one full daily tick, every non-dismissed `AgentIntel` of type DIPLOMAT has a
   non-null `ownerFactionId` in its `Nex4xAgentData` record.
2. `DiplomatPassiveManager.advanceDay` produces non-zero influence for at least one
   DIPLOMAT per faction sweep when buildup level > 0 (verifiable via `InfluenceManager`
   ledger).
3. `DiplomatExpulsionCascade.apply` hits > 0 diplomats when the owning faction has live
   diplomats (no silent zero-affected log).
4. Build green (`./build.sh` exits 0).
5. Runcode smoke: listing live agents via `AgentIntel.getAgentsStatic()` shows all with
   non-null owner; invoking `DiplomatPassiveManager.advanceDay(1f, factionId)` returns
   non-zero drip for known-diplomat factions.
6. Post-action update in `Nex4xAgentActionReportListener` retained as a type-sync
   fallback; no duplicate record creation.

## Scope

**In:**
- New `AgentOwnershipSweep` helper (or inline logic in `Nex4xManager`) that runs once
  per daily tick before `DiplomatPassiveManager` and `advanceAll`: iterates
  `AgentIntel.getAgentsStatic()`, calls `getOrCreate` + `setOwnerFactionId` for any
  record with null owner.
- `Nex4xAgentManager.getOrCreate` call sites in the sweep: must be idempotent (already
  is — it skips creation if key exists).
- `Nex4xManager` v3 daily tick block (lines 160-171): insert sweep call before
  `advanceAll`.
- `Nex4xAgentActionReportListener` lines 41-47: keep as fallback but remove redundant
  record creation if sweep already populated it (noop — `getOrCreate` is idempotent).
- Debug smoke in `Nex4xDebugCommand`: new `auditAgentOwnership()` command listing agent
  id, type, ownerFactionId, buildup level.

**Out:**
- Fixing PRD-013 (covert action registration) — dependency noted; out of scope here.
- Changing `DiplomatPassiveManager` or `DiplomatExpulsionCascade` logic beyond removing
  the now-unnecessary null guard (leave guard in place with a warning log for legacy
  safety).
- Reflection-based access to `AgentIntel.faction` — use public API only.
- Any change to `AgentTypeMap`, `BuildupTracker`, or serialization format.

## Risks

- `AgentIntel.updateAgentDisplayedFaction()` may return a different faction than the
  true owner in edge cases (e.g. disguised agents). Mitigation: use it only when no
  cached `ownerFactionId` exists; the post-action path in the listener will correct it
  when the first action resolves.
- `getAgentsStatic()` may include dead/dismissed agents. Mitigation: check
  `ai.isDeadOrDismissed()` before creating/updating records; remove stale records from
  `Nex4xAgentManager` on death.
- Sweep runs before `advanceAll` on the same tick, so the first tick of a new agent has
  owner set and buildup ticked — intended behavior.
- Large agent counts (many factions, many agents): sweep is O(N agents) per day, same
  complexity as `advanceAll`; acceptable.

## Dependencies

- **PRD-013** (`013_covert-action-registration`): For full end-to-end diplomat behavior,
  covert actions must register with nex4x so buildup accrues on action completion. This
  PRD fixes ownership wiring independently — passive influence will drip once records
  exist, even with zero covert-action-derived buildup boost.
- No other PRDs block this fix.

## Acceptance

- `./build.sh` exits 0, no new compile errors.
- Runcode smoke (`auditAgentOwnership`): all live non-dismissed `AgentIntel` entries show
  non-null `ownerFactionId` after one simulated tick.
- Runcode smoke (`auditDiplomatPassive`): calling `DiplomatPassiveManager.advanceDay(30f,
  factionId)` for a faction with a DIPLOMAT agent at buildup level >= 1 produces a
  positive influence delta visible in `InfluenceManager.getLedger(factionId)`.
- Manual: trigger `DiplomatExpulsionCascade.apply(mgr, actorFaction, victimFaction, 30f)`;
  log line shows `affected > 0`.

## Tasks

See `tasks.yaml`.
