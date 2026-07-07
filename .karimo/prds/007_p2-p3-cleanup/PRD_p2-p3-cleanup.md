# PRD: P2 / P3 Cleanup Batch

**Slug:** p2-p3-cleanup
**Created:** 2026-05-10
**Severity:** P2/P3 (defer-able)
**Source:** `audit/AUDIT_REPORT.md` — every finding not promoted into PRDs A-F

## Vision
Post-systemic-fix cleanup pass. Many of these defects become trivial or no-ops after PRDs A-F land (relation scale, time units, listeners, lifecycle, UI, diplomacy logic). Batched into thematic tasks for context efficiency.

## Why now (vs earlier)
**Don't.** Land after PRDs A-F. Doing this first means re-touching files that A-F also modify; conflict-prone.

## Success criteria
- Every P2/P3 in `research/findings.md` either fixed or explicitly deferred (with reason in commit message).
- Build green.
- `// TODO(audit-P*)` markers in scope cleared.

## Scope
**In:** ~50 P2/P3 findings, batched into 5 thematic tasks.
**Out:** Anything covered by A-F.

## Risks
- Scope creep — keep individual fixes minimal; don't refactor surrounding code.
- Some P3s are "fragile patterns" rather than bugs; if a fix would expand surface area, document and skip.

## Acceptance
- Build green.
- Code-search for `TODO(audit-P` returns zero in scoped files.

## Tasks
See `tasks.yaml` (5 batched tasks).
