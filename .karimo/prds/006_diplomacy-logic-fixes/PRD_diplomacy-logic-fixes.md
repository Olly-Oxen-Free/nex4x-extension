# PRD: Diplomacy Logic Fixes

**Slug:** diplomacy-logic-fixes
**Created:** 2026-05-10
**Severity:** P1 cluster (some P2 promoted)
**Source:** `audit/AUDIT_REPORT.md` THEME-E + THEME-F + standalone P1s

## Vision
Fix the standalone correctness bugs across declarations, agents, demands, casus belli, badges, leaders, beliefs, vassals, politics, strategic actions, and Nexerelin integration. These are individual logic errors that don't cluster around a single system but together represent ~15 P1 defects.

## Why now
- Bilateral 2× delta + DeclarationManager dedupe gaps make balance untunable.
- Demands have no in-world effect → entire demand subsystem is decorative.
- Agent cascades hit every faction's diplomats.
- FactionCompatibility reflection layer has been silently dead for unknown duration.
- IRREDENTIST/FOREVERWAR memory multipliers inverted — opposite of design intent.

## Success criteria
- All P1s in `research/findings.md` resolved.
- `// TODO(audit-P*)` markers in scoped files cleared.
- `./build.sh` clean.
- Manual smoke per task acceptance (most via Console Commands).

## Scope
**In:** ~15 P1 fixes across listed files; one P2 promoted (bilateral relation 2×).

**Out:**
- Themes A/B/C/D (separate PRDs).
- UI correctness (PRD-E).
- P2/P3 cleanup (PRD-G).

## Dependencies
- **Prefer to land after PRD-A** (AgreementType migration is in A; this PRD references it).
- **Prefer to land after PRD-B** (decay-scaling task uses Nex4xClock helper).
- **Prefer to land after PRD-D** (VassalManager weakness uses de-static FactionPowerRankings).

If executed before A/B/D, those tasks should temporarily inline equivalents and flag follow-up.

## Risks
- Demand effect dispatch is new behavior; ensure each demand type's effect maps to a real game change. Start conservative (log + status only) for unknown types.
- DeclarationManager dedupe + bilateral fix landing together = 2× behavior change in one wave; verify each independently first.

## Acceptance
- Build green.
- Console smokes per task.

## Tasks
See `tasks.yaml` (15 tasks).
