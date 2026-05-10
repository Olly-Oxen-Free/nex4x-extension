# PRD: UI Correctness Fixes

**Slug:** ui-correctness-fixes
**Created:** 2026-05-10
**Severity:** P0/P1 mix
**Source:** `audit/AUDIT_REPORT.md` cluster 5 (standalone)

## Vision
Fix the UI correctness bugs in negotiation + diplomacy intel/overlay that aren't part of THEME-A/B/D: singleton race, overlay leak, class shadow, GL state leaks, panel detachment, double layout advance, intel sort hack, log spam.

## Why now
- Singleton race can brick negotiation flow.
- Overlay leak compounds across save sessions.
- GL state leaks visibly tint UI.
- Each is small but they cluster in recently-changed files.

## Success criteria
- All 10 issues in `research/findings.md` resolved.
- `// TODO(audit-P*)` markers in scoped files cleared.
- `./build.sh` clean.
- Manual: open/close DiplomacyIntel 5x — no overlay stacking, no log spam.
- Trigger negotiation cancel mid-popup — `activeInstance` resets.

## Scope
**In:** NegotiationPanel, BalanceBarPlugin, DiplomacyOverlayPlugin, DiplomacyTabOverlayModel, DiplomacyIntel, CoreUITabInjectorListener (specific issues only).

**Out:**
- Relation-scale fixes in same files (PRD-A).
- FactionBrowserPanelModel persisted-id validation (P2 → PRD-G).
- Hardcoded balance-bar colors (P3 → PRD-G).

## Risks
- GL state push/pop must match — wrong attribute mask will silently leak.
- Detached-panel fix must verify `panelToInfluence` ref stays alive across `advance()`.

## Acceptance
- Build green.
- Smoke each fix per task acceptance.

## Tasks
See `tasks.yaml` (10 tasks, mostly independent).
