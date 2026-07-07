# PRD: Relation Scale Remainder

**Slug:** relation-scale-remainder
**Created:** 2026-05-31
**Severity:** P1
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every display site in the nex4x UI renders relation values as percent integers in
-100..100 and passes percent-scale values to `repBarAscii`. No raw -1..1 float
ever reaches a format string or ASCII-bar helper.

## Why now

PRD-001 (`unify-relation-scale`) created `Nex4xRelations.toPercent()` and migrated
backend threshold comparisons, but the CHANGELOG claim that it "fixed" display sites
was premature. Several UI card-building methods were added or missed during that pass
and still call `String.format("%+.0f", rawRel)` on the raw API value, producing
"+1"/"-1"/"+0" instead of "+75"/"-43". The same raw value is passed to `repBarAscii`,
whose internal formula `(relationship + 100f) / 200f` expects a percent-scale input
(−100..100), so every faction bar renders at ~50% full regardless of actual standing.
Additionally, `DiploEventEntry.delta` comes from
`ExerelinReputationAdjustmentResult.delta`, which inherits from
`ReputationAdjustmentResult(float)` and stores the raw adjustment passed by
Nexerelin's `adjustRelations` — a value in the -1..1 range — so history entries
always display "+0"/"-0" for real ±5–8% events.

This is a focused, low-risk display-correctness migration. The conversion helper
already exists; this PRD is purely the migration PRD-001 missed.

## Success criteria

- `FactionBrowserPanelModel.addFactionRow` shows e.g. "+75" not "+1" for an ally.
- `FactionBrowserPanelModel.addOverviewTab` "Relation:" line shows percent.
- `FactionBrowserPanelModel.addRelationsTab` crest-card shows percent, bar is correct.
- `repBarAscii` is updated to accept percent scale (or every call site converts before
  passing); the bar is visually correct for a neutral faction (~50%) and extreme
  allies/enemies (~100%/~0%).
- `DiplomacyIntel.buildFactionCard` shows percent in the "(... / 100)" label.
- `DiplomacyTabPlugin.buildFactionCard` shows percent; bar correct.
- `CoreUITabInjectorListener.buildFactionCardList` bar correct (numeric already fixed
  in PRD-001; only `repBarAscii` call remains).
- Diplo-history entries in `DiplomacyTabOverlayModel.addDiplomacyHistory` and
  `DiploEventTooltip` show e.g. "+8" not "+0" for a Nexerelin war-weariness event.
- `./build.sh` green.
- Visual confirmation: relation numbers and bars match Nexerelin's own
  `DiplomacyIntel` percent values.

## Scope

**In:**
- `src/nex4x/ui/FactionBrowserPanelModel.java` — lines 121, 259, 528, 535.
- `src/nex4x/ui/DiplomacyIntel.java` — line 114 (`relStr` format), line 123
  (`repBarAscii` call).
- `src/nex4x/ui/DiplomacyTabPlugin.java` — line 65 (`relStr`), line 73
  (`repBarAscii` call).
- `src/nex4x/ui/CoreUITabInjectorListener.java` — line 159 (`repBarAscii` call).
- `src/nex4x/ui/DiplomacyTabOverlayModel.java` — line 400 (`deltaStr`), line 659
  (`ds` in `DiploEventTooltip`).
- `repBarAscii` signature/body updated to accept percent scale (single change
  fixes all bar call sites consistently).

**Out:**
- `relLabel`/`relColor` helpers in `DiplomacyTabOverlayModel` (lines 605–618) — they
  already use raw -1..1 thresholds correctly; do not change.
- `DiplomacyTabOverlayModel.buildDetailMain` line 248 —
  `String.format("%.0f / 100", rel * 100f)` is already correct (inline multiply).
- Any backend threshold comparison; those were handled by PRD-001.
- Non-display logic (sorting, comparators) — those are on raw scale intentionally.

## Risks

- **PRD-001 is the partial predecessor.** It centralized `Nex4xRelations` and fixed
  threshold comparisons but did not update the display format strings or `repBarAscii`.
  This PRD must not re-introduce raw thresholds that PRD-001 already removed.
- **`repBarAscii` signature change** — all callers pass raw rel today; changing the
  signature to expect percent means every call site must convert. Four call sites total;
  all are in-scope and enumerated in tasks.
- **`delta` scale ambiguity** — `ExerelinReputationAdjustmentResult` inherits
  `ReputationAdjustmentResult(float delta)`. Nexerelin's `adjustRelations` passes
  the raw float given to `FactionAPI.adjustRelationship`, which is in -1..1. A ±0.05
  event (±5%) currently displays "+0". Multiply by 100 at display time; do not alter
  the stored value.
- **Build-only test gate.** `./build.sh` is the sole automated check; no runtime
  test harness exists. Accept = build green + visual spot-check.

## Acceptance

- `./build.sh` exits 0.
- In-game: open Intel → Diplomacy, select an allied faction → numeric shows e.g.
  "+73 / 100" and bar is ~3/4 full; select a hostile faction → bar near empty.
- Diplo-history section shows "+5"/"+8"/"-12" style deltas, not "+0"/"-0".
- Values match those shown in Nexerelin's own `DiplomacyIntel` relation display.

## Tasks

See `tasks.yaml`.
