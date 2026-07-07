# PRD: Listener Save Integrity

**Slug:** listener-save-integrity
**Created:** 2026-05-10
**Severity:** P0 (save corruption / silent listener drop)
**Source:** `audit/AUDIT_REPORT.md` THEME-C

## Vision
nex4x bridge listeners are stateless dispatchers. Register them as **transient** on every `onGameLoad`; never let sector try to serialize them.

## Success criteria
- Three bridge listeners registered with `addListener(x, true)`.
- Stale persistent entries (from prior sessions) purged on first load.
- Save round-trip clean (no `NotSerializableException` warnings in `starsector.log`).
- Bridges still fire on game events (verified manually).

## Scope
**In:**
- `Nex4xInvasionBridge`, `Nex4xRaidBridge`, `Nex4xAgentActionReportListener` registration.
- `Nex4xModPlugin.registerNexCampaignBridges()` rewrite.

**Out:**
- Other listener classes elsewhere in mod (audit cluster scope).
- Lifecycle gaps (PRD-D).

## Risks
- Bridge lifecycle change → if a downstream callsite assumes listeners persist, it breaks. Mitigation: stateless classes only.

## Acceptance
- Build green.
- Save → quit → reload → invasion event fires bridge handler.
- `starsector.log` has zero `NotSerializableException` lines on save.

## Tasks
See `tasks.yaml` (4 tasks).
