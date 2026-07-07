# PRD: Standardize Time Units

**Slug:** standardize-time-units
**Created:** 2026-05-10
**Severity:** P1 (strategic AI broken; save-format change required)
**Source:** `audit/AUDIT_REPORT.md` THEME-B

## Vision
One canonical helper for game-time math. Eliminate seconds-as-days confusion across strategic AI, archetype hysteresis, goal scoring, and war-score tracking. Reconcile two divergent calendar-day formulas in contracts/demands/agreements.

## Why now
Strategic AI archetypes lock for "60 days" but expire in 60 *seconds*; importance recalcs every 7 seconds; goal age saturates in 90 seconds. The whole strategic layer is effectively non-functional. Demand/contract expiry skips months silently.

## Success criteria
- Single `Nex4xClock` helper used at every call site.
- Strategic AI archetypes hold for actual in-game days (verified via Console Commands smoke).
- Demand/contract expiries respect month boundaries.
- Existing saves load without crash via legacy-value migration in `onGameLoad`.
- `./build.sh` clean.

## Scope
**In:**
- `src/nex4x/util/Nex4xClock.java` (new).
- 8 file migrations: StrategicGoalManager, CommitmentLedger, GoalScorer, WarScoreTracker, ContractAuctionManager, DemandManager, Agreement, plus migration hook in Nex4xModPlugin.
- Save-format migration for legacy timestamp fields.

**Out:**
- Logical fixes to AI/strategic semantics (separate work).
- ModPlugin lifecycle gaps (PRD-D).

## Risks
- **Save corruption** if migration misfires → migration is read-only on load (sentinel detection: magnitude check), no destructive rewrite. Worst case: archetype timer resets, no data loss.
- Float-to-long field type change in Agreement → may need XStream alias.

## Acceptance
- Build green.
- Console smoke `nex4x audit-clock` prints (now-ts, days-since-creation) for live goals/archetypes/agreements with sane values.
- Loading a pre-fix save logs a one-shot migration line and resumes.

## Tasks
See `tasks.yaml` (8 tasks).
