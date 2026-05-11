# PRD: Nex Diplomacy Event Integration

**Slug:** nex-diplomacy-event-integration
**Created:** 2026-05-10
**Severity:** P0/P1 mix (relation events bypass Nex; coalitions unknown to AllianceManager)
**Source:** Post-PRD-008 Nex integration audit

## Vision
Every nex4x relation change of size flows through Nex's `DiplomacyManager` event pipeline.
Top-tier alliances (`COALITION`) materialize as actual Nex `Alliance` instances. Mediation,
peace conferences, and END_WAR demands all create real Nex peace events.

## Why now
- Friendship/denouncement currently invisible in `DiplomacyIntel` event log → player can't
  see why relations changed.
- `MEM_KEY_BADBOY` not accumulating from nex4x events → repeated denouncements escape
  warmonger penalty.
- COALITION tier never engages Nex's voter/alignment/dissolution logic → fragile alliance modeling.
- Mediation, peace conferences, END_WAR demand all "succeed" without ending the war.

## Success criteria
- Friendship declaration produces a `DiplomacyIntel` "respect" event visible to player.
- Denouncement produces an "insult" event; ripple events visible.
- COALITION agreement produces matching Nex `Alliance`; `AllianceManager.getFactionAlliance(fid)`
  returns it.
- Mediation success ends the war (factions no longer hostile).
- PeaceConference.accept() ends the war.
- END_WAR demand acceptance ends the war.
- Build green; no double-event firing (rep changes match expected magnitude, not 2×).

## Scope
**In:**
- `DeclarationManager.declareFriendship`/`declareDenouncement`/`applyDenouncementRipple`.
- `AgreementManager.createAgreement` (alliance-track only).
- `MediationManager.advanceDay` SUCCESS branch.
- `PeaceConference.accept()`.
- `DemandManager.applyDemandEffect` END_WAR.
- One-shot save-load migration in `onGameLoad` to backfill Nex Alliance for in-flight COALITION agreements.

**Out:**
- Daily rep gain/decay events (high log noise; defer).
- `TRIBUTE_CREDITS` → `TributeCondition` (bigger refactor; separate PRD).
- Reverse direction: shadowing Nex alliance dissolution back into our agreements (defer).

## Risks
- Double-rep-application if both `createDiplomacyEventV2` and our `adjustRelationship`
  fire. Mitigation: replace, don't add — pick one path per site.
- Nex version drift — wrap each Nex call in try/catch with adjustRelationship fallback.
- COALITION-without-alliance-creation savestate — handled by load migration.

## Acceptance
- Build green.
- Debug smoke (`auditDiplomacy()` new): triggers a friendship + a denouncement; reports
  `DiplomacyIntel` count delta.
- Debug smoke for coalition: createAgreement(COALITION) + verify
  `AllianceManager.getFactionAlliance(fid) != null`.

## Tasks
See `tasks.yaml`.
