# PRD: ModPlugin Lifecycle Completeness

**Slug:** modplugin-lifecycle
**Created:** 2026-05-10
**Severity:** P1 (new-campaign NPE risk + cross-save pollution)
**Source:** `audit/AUDIT_REPORT.md` THEME-D

## Vision
`Nex4xModPlugin` overrides every BaseModPlugin hook it should. Static caches are eliminated. New campaigns initialize cleanly; saves round-trip without leaking transient state.

## Success criteria
- `onNewGame`, `onNewGameAfterEconomyLoad`, `beforeGameSave`, `afterGameSave`, `onGameSaveFailed` all overridden with documented purpose.
- `FactionPowerRankings` carries no class-static state.
- `onGameLoad(boolean newGame)` flag respected — no double-init on fresh game.
- New-campaign smoke: from main menu → new game → in-system within 30 seconds, no NPEs in `starsector.log`.

## Scope
**In:**
- `Nex4xModPlugin.java` lifecycle hook additions and reordering.
- `FactionPowerRankings.java` state migration.
- `onNewGameAfterTimePass` fallback hardening.

**Out:**
- Listener registration changes (PRD-C).
- Time-unit migration (PRD-B).

## Risks
- Init-order regressions if bootstrap moves carelessly. Mitigation: smoke command `nex4x audit-init` that prints each manager's init flag.

## Acceptance
- Build green.
- New campaign: log clean, all managers init.
- Save → reload → log clean, no static-state references.

## Tasks
See `tasks.yaml` (6 tasks).
