# PRD: Strategic AI Activation

**Slug:** strategic-ai-activation
**Created:** 2026-05-31
**Revised:** 2026-05-31 (wrong-premise correction — see §Why-now)
**Severity:** P1 (AI does nothing — war/peace/agreement paths all inert)
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every nex4x AI faction's `DiplomaticExecutor` loop — nex4x's OWN parallel diplomacy engine,
separate from Nex's StrategicAI — declares war when posture reaches `HOSTILE`, proposes
peace when `END_WAR` goal willingness exceeds threshold, and creates real agreements
via `AgreementManager.createAgreement`. Goals escalate posture correctly, `StrategicFocus`
carries a real `Threat` so crisis weighting activates, and the Java registration in
`Nex4xStrategicAIConcerns` is cleaned up to not pollute `StrategicDefManager` with tagless
orphan defs under wrong ids.

## Background: two AI paths

**Path A — Nex StrategicAI (disabled):**
`strategicAIConfig.json` registers `nex4x_goal`, `nex4x_belief_alignment`,
`nex4x_badge_provocation`, `nex4x_declare_war`, `nex4x_seek_peace`, `nex4x_propose_agreement`
into `StrategicDefManager.CONCERN_DEFS_BY_ID / ACTION_DEFS_BY_ID` with correct tags at
class-init time (static initializer calls `loadConfig()` immediately). These defs are
structurally sound. However, `Nex4xModPlugin.onGameLoad` calls
`applyNex4xAuthorityOverNexDiplomacy()` which sets `NexConfig.enableStrategicAI = false` and
`StrategicAI.removeAIs()`, permanently disabling Nex's StrategicAI loop for all factions.
Path A is intentionally inert. Tags are NOT missing from the JSON-registered defs.

**Path B — nex4x DiplomaticExecutor (broken):**
`DiplomaticExecutor.advanceDay` runs its own war/peace/agreement logic daily. This is the
ACTIVE path. It has four wiring gaps that make it entirely inert (see §Why-now below).

## Why now

Four independent wiring gaps collude to make Path B (the active path) a no-op:

1. **Java registration injects orphan tagless defs under wrong ids** —
   `Nex4xStrategicAIConcerns.register()` runs in `onApplicationLoad` AFTER
   `StrategicDefManager` is already initialised via static class-init. The `containsKey` guard
   prevents overwriting the correctly-tagged JSON defs where ids match, but the Java code uses
   DIFFERENT ids from the JSON:
   - Java inserts `nex4x_goal_wrap` (JSON has `nex4x_goal`) — no tags, different id.
   - Java inserts `nex4x_propose_tiered_agreement` (JSON has `nex4x_propose_agreement`) — no
     tags, different id.
   - `nex4x_belief_alignment` matches — `containsKey` guard skips it correctly.
   These two orphan tagless defs cannot participate in tag-intersection selection. Because
   Path A (Nex StrategicAI) is disabled, the practical harm is that the map is polluted with
   dead entries and the registered log count is misleading. The correct fix is to REMOVE the
   Java registration entirely and let the JSON own all defs; or at minimum replace the wrong
   ids with the JSON ids and add tags. Since Path A is intentionally disabled, the simplest
   correct action is to delete `Nex4xStrategicAIConcerns.register()` and its call site, and
   leave JSON registration as the sole source of truth.

2. **Posture stuck at NEGOTIATING** — `StrategicGoal.setPosture` is never called anywhere in
   the codebase. `DiplomaticExecutor.evaluateWarDecisions` (line 119) gates the war path on
   `goal.getPosture() == HOSTILE`; since all goals are born `NEGOTIATING` and nothing ever
   changes them, the gate is never passed. `GoalScorer.obstacleEscalation` and `cascadeRisk`
   also read from `Obstacle.none()` / null `parentGoalId` defaults because `setObstacle` and
   `setParentGoalId` are also never called, leaving sub-scores permanently at floor values.

3. **Threat never constructed** — `StrategicFocus.greatestThreat` is always `null` because
   `StrategicGoalManager.advanceDay` (step 7) calls
   `focus.update(…, focus.getGreatestThreat(), 1f)` — passing the existing (null) threat back
   in — and no code ever builds a `StrategicFocus.Threat` instance. This means `hasCrisis` is
   always `false` in `GoalScorer.computeEffectivePriority`, locking effective priority into
   peacetime weighting and preventing urgency from dominating during a military crisis.

4. **Peace / agreement effects log-only** — `DiplomaticExecutor.evaluatePeaceDecisions` logs
   "seeking peace" but never calls any bridge or event API. `proposeAgreementIfViable`
   decrements the action budget and logs but never calls `AgreementManager.createAgreement`.

## Success criteria

- `Nex4xStrategicAIConcerns` class is removed (or its `register()` call site deleted) so no
  tagless orphan defs pollute `StrategicDefManager`. JSON remains the sole registration source.
- `GoalGenerator` (or `StrategicGoalManager`) calls `goal.setPosture(…)` based on goal type
  and game-state; goals of category `AGGRESSION` escalate to `HOSTILE` when CB exists or
  `rel < -0.3`.
- `StrategicGoalManager.advanceDay` builds a real `StrategicFocus.Threat` from hostile-faction
  scan and passes it into `focus.update`; `hasCrisis` evaluates correctly.
- `DiplomaticExecutor.evaluatePeaceDecisions` calls `NexDiplomacyBridge.firePeaceTreaty` when
  willingness exceeds `PEACE_SEEK_THRESHOLD`.
- `DiplomaticExecutor.proposeAgreementIfViable` calls `AgreementManager.createAgreement` (in
  addition to existing logging / budget decrement).
- Build green (`./build.sh`).
- Runcode smoke: `auditStrategicAI <factionId>` prints posture distribution across goals (at
  least one `HOSTILE` for a faction at war), `greatestThreat != null` for a faction with
  enemies, and confirms zero orphan defs in `StrategicDefManager`.

## Scope

**In:**
- `src/nex4x/integration/Nex4xStrategicAIConcerns.java` — delete the class (or gut `register()`
  to a no-op) and remove its call site in `Nex4xModPlugin.onApplicationLoad`.
- `src/nex4x/ai/goals/GoalGenerator.java` — derive and set posture/obstacle on generated goals.
- `src/nex4x/ai/StrategicGoalManager.java` — build and inject `StrategicFocus.Threat` after
  step 6.
- `src/nex4x/ai/DiplomaticExecutor.java` — wire `evaluatePeaceDecisions` and
  `proposeAgreementIfViable` to call real effects (via `NexDiplomacyBridge` — see dependency
  on PRD-014).

**Out:**
- `data/config/exerelin/strategicAIConfig.json` — JSON registration is correct; no changes.
- Nex's own concern/action registrations — we only remove our redundant Java shadow.
- `GoalScorer` formula rebalancing (separate tuning PRD if needed).
- `PostureMap` display in UI (no UI work this PRD).
- Agreement tier display in `AgreementManagerIntel` (separate PRD-012 scope).
- Re-enabling Nex StrategicAI Path A (separate architectural decision).

## Risks

- **Double war-declaration** if both `DeclareWarFromDesireAction` (Nex Path A, currently
  disabled) and `DiplomaticExecutor.evaluateWarDecisions` (Path B) were ever both active.
  Not a risk currently since Path A is disabled; note here for future if Path A is re-enabled.
- **NexDiplomacyBridge null-masking** — PRD-014 (P0, planned but not yet executed) fixes the
  bridge's null-return behaviour. Task 15d depends on the bridge being reliable. If PRD-014 is
  not yet merged, task 15d must add its own null-guard fallback.
- **Threat construction performance** — iterating all factions daily for Threat building is
  O(F²) if done naively. Mitigation: cap at 5 candidates and break early on `existential=true`.
- **Orphan-def removal timing** — deleting `Nex4xStrategicAIConcerns.register()` means the
  two orphan defs (`nex4x_goal_wrap`, `nex4x_propose_tiered_agreement`) will no longer exist.
  Confirm no other code references those specific ids before deleting.

## Dependencies

- **PRD-014 (`014_diplomacy-bridge-fallback`)** — task 15d (`evaluatePeaceDecisions` effect
  wiring) routes peace proposals through `NexDiplomacyBridge`. PRD-014 must be executed first
  or task 15d must carry a fallback. Noted inline in tasks.yaml with `depends_on` comment.

## Acceptance

- `./build.sh` exits 0 with no new warnings.
- Runcode command `auditStrategicAI <factionId>` (added in task 15f) prints:
  - zero defs with ids `nex4x_goal_wrap` or `nex4x_propose_tiered_agreement` (orphans gone),
  - posture distribution across active goals (at least one `HOSTILE` after hostile-relation scan),
  - `hasCrisis` flag and `greatestThreat` type for the faction.
- Manual in-game check: a faction with a CB against a hostile neighbour eventually declares war
  via the nex4x `WarDeclarationIntel` (Path B, `DiplomaticExecutor.declareWar`).

## Tasks

See `tasks.yaml`.
