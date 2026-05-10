# PRD: Nex Alliance + CB Sync

**Slug:** nex-alliance-cb-sync
**Created:** 2026-05-10
**Severity:** P1 (Nex sees stale state for justified wars / multi-member coalitions / NAPs)
**Source:** Post-PRD-009 integration audit

## Vision
Nex's diplomacy AI sees the full nex4x state:
- Justified wars don't incur warmonger penalty
- Coalition membership mirrors to Nex Alliance in real time
- NAP/DefensivePact/Mil Partnership prevent Nex AI from declaring war between signatories
- DiplomacyBrain stays disabled even after mid-session faction creation

## Why now
Without these, the AI experience is jarring: players get warmonger rep for justified wars,
coalition allies vote against each other in Nex events, and NAPs are paper tigers.

## Success criteria
- CB-justified war declaration leaves `MEM_KEY_BADBOY` unchanged (or only minimally affected).
- Adding a 3rd member to a coalition adds them to the matching Nex Alliance.
- NAP signatories' relationship floors are enforced; Nex AI cannot declare war between them.
- After 30 in-game days, no `StrategicAI` instances exist on tracked factions.
- Build green.

## Scope
**In:**
- Extend `NexDiplomacyBridge` with `fireJustifiedWar`, `syncCoalitionToAlliance`,
  `enforceNonAggression`, `sweepDiplomacyBrains`.
- `DiplomaticExecutor` declare_war path uses `fireJustifiedWar` when CB present.
- `CoalitionGovernance.addMember`/`removeMember` mirror to Nex Alliance.
- `AgreementManager.createAgreement` enforces relation floor for NAP/DefPact/Mil tiers.
- `Nex4xManager.advance` daily sweep of leftover DiplomacyBrains.

**Out:**
- TributeCondition wiring (PRD-011).
- Replacing Nex's badboy system entirely.

## Risks
- WarmongerEvent suppression may miss paths. Mitigation: log every suppression attempt
  with before/after badboy values for verification.
- Coalition Alliance sync may try to join factions Nex has marked incompatible
  (`canAlly` check before join).
- Forcing relation floor on agreement creation may surprise; clamp only upward.

## Acceptance
- Build green.
- New `auditNexSync()` smoke command reports: justified-war suppression count,
  coalition-alliance shadow coverage, NAP/DefPact relation-floor enforcement count.
- Manual: declare CB-justified war → check `MEM_KEY_BADBOY` unchanged.

## Tasks
See `tasks.yaml` (6 tasks).
