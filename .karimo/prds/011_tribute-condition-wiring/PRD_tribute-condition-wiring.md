# PRD: TributeCondition Wiring

**Slug:** tribute-condition-wiring
**Created:** 2026-05-10
**Severity:** P2 (cosmetic UI gap + missing persistence)
**Source:** Post-PRD-009 integration audit

## Vision
Persistent tribute relationships use Nex's canonical `TributeCondition` market plugin.
Demand-issued tribute and vassal-relationship tribute both materialize as conditions
on host markets — visible in vanilla economy UI and consumed by Nex's income/immigration logic.

## Why now
TRIBUTE_CREDITS demand pays once then ends; vassal income is calculated but invisible.
Both should be persistent and player-visible via the existing market-condition UI.

## Success criteria
- Accepted TRIBUTE_CREDITS demand applies `TributeCondition` to all target-faction markets
  for configured duration.
- Vassalize call applies TributeCondition to all vassal markets; freeVassal removes them.
- Conditions appear in vanilla market UI tooltip.
- Idempotent — applying twice doesn't stack.
- Build green.

## Scope
**In:**
- Extend `NexDiplomacyBridge` with TributeCondition helpers.
- `DemandManager` TRIBUTE_CREDITS persistent path.
- `VassalManager.vassalize`/`freeVassal` apply/remove conditions.
- `VassalManager.advanceDay` reconcile drift (conquered vassal markets).

**Out:**
- Player negotiation-panel-issued tribute (follow-on).
- Replacing nex4x vassal income calc with Nex's TributeCondition income (audit needed).

## Risks
- `TributeIntel` may need to exist before condition can be set up; verify in implementation.
- Conditions on conquered markets — handle via `freeVassal` + reconcile pass.
- Save format — TributeCondition is part of MarketAPI persistence (Nex owns); no nex4x
  save format change.

## Acceptance
- Build green.
- `auditTribute()` smoke command lists all markets with TributeCondition + receiver.
- Manual: issue + accept TRIBUTE_CREDITS demand → check target market conditions.
- Manual: vassalize → check vassal markets show TributeCondition.

## Tasks
See `tasks.yaml` (4 tasks).
