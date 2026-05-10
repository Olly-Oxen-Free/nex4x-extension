# PRD: Unify Relation Scale

**Slug:** unify-relation-scale
**Created:** 2026-05-10
**Severity:** P0 (visible in-game; gates alliance flow)
**Source:** `audit/AUDIT_REPORT.md` THEME-A

## Vision
Eliminate the -1..1 vs -100..100 confusion that currently makes every faction display "neutral gold", every `ReputationTier` resolve to HOSTILE, and alliance proposals fail `canPropose` unconditionally. Single conversion helper at every API boundary; one canonical threshold convention.

## Why now
- All recent v5 leader-dialogue intel UIs (`WarDeclarationIntel`, `AIProposalIntel`, `BadgeReactionIntel`) consume `ReputationTier` and inherit the bug — the new dialogue copy is unreachable.
- `AgreementManager.proposeAgreementIfViable` is dead code as a result; alliance pacts never offered.

## Success criteria
- Single `Nex4xRelations` helper class is the only place the 100× factor lives.
- Every site listed in `research/findings.md` migrated.
- Console-Commands smoke prints faction-pair tier classifications matching `RepLevel` (HOSTILE@-0.5, NEUTRAL@0, FRIENDLY@0.5, etc.).
- `./build.sh` clean.
- Manual play test: faction browser shows non-uniform relation colors; alliance proposal dialog reachable.

## Scope
**In:**
- New `src/nex4x/util/Nex4xRelations.java`.
- Migrate 11 files identified in research.
- `Nex4xDebugCommand` smoke command: `nex4x audit-rels`.

**Out (separate PRDs):**
- Bilateral relation 2× delta (PRD-F).
- UI lifecycle bugs in same files (PRD-E).
- Time-unit issues (PRD-B).

## Risks & mitigations
- **Missed callsite** → keep raw thresholds compiling but flag with `// TODO(audit-P0)` removal as part of each task. Final task: grep for raw rel-vs-magic-number.
- **JSON data drift** → audit `data/config/nex4x/*.json` after migration; if any threshold authored in raw scale, fix in-place.

## Acceptance
- Build green.
- `audit-rels` console command output sane.
- `// TODO(audit-P*)` markers in scoped files all deleted.

## Tasks
See `tasks.yaml` (10 tasks, max complexity 3, single wave-chain).

## Research findings
See `research/findings.md`.
