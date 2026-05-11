---
title: Strategic AI
theme: Strategic AI
source_files:
  - src/nex4x/ai/
  - src/nex4x/ai/archetype/
  - src/nex4x/ai/goals/
  - src/nex4x/ai/posture/
  - src/nex4x/strategic/
  - src/nex4x/evaluation/
---

# Strategic AI

nex4x runs its own strategic AI in place of Nex's `DiplomacyBrain` (which we
explicitly disable in `Nex4xModPlugin.applyNex4xAuthorityOverNexDiplomacy`).
The decision pipeline is goal-driven, not stage-driven.

## Pipeline

```
Beliefs + Tendency Profile
        ↓
GoalGenerator    (per faction, per day)
        ↓
GoalScorer       (importance + urgency)
        ↓
StrategicGoalManager   (top-K active goals)
        ↓
DiplomaticExecutor     (turns goals into actions)
        ↓
Nex DiplomacyManager + AllianceManager (via NexDiplomacyBridge)
```

## Components

**`GrandStrategyManager`** owns per-faction `CommitmentLedger`s tracking
archetype affinity (MILITARY_SUPREMACY, ECONOMIC_HEGEMONY, COALITION_BUILDER,
DEFENSIVE_CONSOLIDATION, IDEOLOGICAL_CRUSADE, OPPORTUNIST). Daily score
update from active goals + beliefs + threats; transitions with hysteresis
(MIN_TREND_DAYS=30) and crisis-locked forced transitions.

**`StrategicGoalManager`** runs per faction. Generates candidate goals from
`GoalGenerator` (belief-driven + grievance + maintenance + security +
opportunistic + diplomacy), filters via `FeasibilityChecker`, scores
importance (weekly) + urgency (daily), keeps top-K active.

**Time math**: all `archetypeSinceTs`, `lockExpiryTs`, `goal.createdTimestamp`
fields use [[time-and-relations]]' `Nex4xClock` long timestamps. Days
elapsed computed via `clock.getElapsedDaysSince(ts)`. Pre-PRD-002 saves'
legacy float-day fields are migrated on load.

**`DiplomaticExecutor`** consumes goals + posture and turns them into Nex
diplomacy events. War declaration path is CB-aware: if a `CasusBelli`
exists, fires `NexDiplomacyBridge.fireJustifiedWar` (snapshots and reverts
badboy delta); otherwise plain `createDiplomacyEvent("declare_war", ..)`.

**`ReactiveHandler`** queues urgent events for immediate re-evaluation
between daily ticks. Null-guards `getGoalManager`/`getExecutor` for
non-tracked factions.

**`DesperationCalculator`** synthesizes pressure score from war weariness
(via Nex), fleet pool depletion, multi-front wars, and war score
(via `WarScoreTracker`). Drives concession willingness in negotiations.

## Nex StrategicAI integration

`Nex4xStrategicAIConcerns.register()` adds nex4x concerns + the
`ProposeTieredAgreementAction` to Nex's `StrategicDefManager.CONCERN_DEFS_BY_ID`
and `ACTION_DEFS_BY_ID`. This bridges nex4x goals into Nex's pluggable
concern framework. `Nex4xManager.advance` periodically sweeps any
`DiplomacyBrain` instances that mid-session faction creation might spawn.

## Related

- [[diplomacy-core]] — how AI decisions become diplomacy events
- [[conflict-system]] — CBs and war scoring feed AI desperation
- [[nex-integration-layer]] — Nex AI replacement boundary
