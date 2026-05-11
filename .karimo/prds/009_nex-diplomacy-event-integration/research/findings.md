# Research Findings — Nex Diplomacy Event Integration

## Source
Post-PRD-008 Nex integration audit. The agent layer integrated; this PRD covers the
next layer: diplomacy event flow + alliance lifecycle.

## Problem
Several nex4x sites bypass Nex's diplomacy event pipeline by calling
`FactionAPI.adjustRelationship` / `setRelationship` directly. This skips:
- Nex's `clampRelations` (per-faction-pair caps from JSON)
- `DiplomacyManager.MEM_KEY_BADBOY` accumulation (warmonger flag)
- `DiplomacyIntel` event log entry (player-visible diplomacy history)
- Disposition checks via `DiplomacyManager.disallowedFactions`

Separately, our top-tier alliance (`AgreementType.COALITION`) is internal-only —
Nex's `AllianceManager` never knows the coalition exists, so its alignment/voting/
strength-mixing systems can't engage with our highest-tier alliances.

## Affected sites + Nex API mapping

| File:line | Today | Replace with |
|---|---|---|
| `declarations/DeclarationManager.java:172` (declareFriendship) | `dfa.adjustRelationship(target, +bonus)` | `DiplomacyManager.createDiplomacyEventV2(declarer, target, "respect", params)` |
| `declarations/DeclarationManager.java:191` (declareDenouncement) | `dfa.adjustRelationship(target, -penalty)` | `createDiplomacyEventV2(.., "insult", params)` |
| `declarations/DeclarationManager.java:240,258` (advanceDay rep gain/decay) | direct `adjustRelationship` | small per-day rep events; or keep `adjustRelationship` only after recording the event log via Nex (low-impact daily drift; defer to keep noise down — note in javadoc) |
| `declarations/DeclarationManager.java:211` (denouncement ripple) | direct adjustRelationship to allies | `createDiplomacyEventV2(.., "insult", params)` per ripple target |
| `agreements/AgreementManager.createAgreement(.., COALITION)` | internal only | also call `AllianceManager.createAlliance(a, b)` and tag |
| `mediation/MediationManager` SUCCESS | status flip only | `DiplomacyManager.createDiplomacyEvent(belligerentA, belligerentB, "peace_treaty", null)` |
| `peace/PeaceConference.accept()` | status flip only | same peace_treaty event |
| `demands/DemandManager.applyDemandEffect` END_WAR | `setRelationship(NEUTRAL)` direct | same peace_treaty event |

## API verification

```
javap -classpath ExerelinCore.jar exerelin.campaign.DiplomacyManager | grep createDiplomacyEvent
  public static void createDiplomacyEvent(FactionAPI, FactionAPI);
  public static ExerelinReputationAdjustmentResult createDiplomacyEvent(FactionAPI, FactionAPI, String stage, DiplomacyEventParams params);
  public static DiplomacyIntel createDiplomacyEventV2(FactionAPI, FactionAPI, String stage, DiplomacyEventParams params);

javap -classpath ExerelinCore.jar exerelin.campaign.AllianceManager
  public static Alliance getFactionAlliance(String);
  public static Alliance createAlliance(String, String);
  public static Alliance createAlliance(String, String, Alliance$Alignment);
```

## Stage strings
Nex's `data/config/exerelin/diplomacyConfig.json` defines stages:
`peace_treaty`, `cease_fire`, `respect`, `insult`, `tribute`, `surrender`, `declare_war`.
Our Friendship → "respect". Denouncement → "insult". Mediation/peace → "peace_treaty".

## Risks
- **Double event firing** — `createDiplomacyEventV2` already calls `adjustRelationship`
  internally; we must NOT also call adjustRelationship in the same path.
- **Nex-version compat** — wrap each new call in try/catch (NoSuchMethodError) and fall
  back to direct adjustRelationship as a safety net.
- **AllianceManager.createAlliance signature** — takes faction-id strings, returns Alliance.
  If either faction is already in an alliance, `joinAlliance` is correct call instead.
- **Save migration** — coalition agreements created in-flight before this PRD won't
  have a Nex Alliance shadow. On `onGameLoad`, walk active coalition agreements and
  ensure each pair has a Nex Alliance; create on the fly.

## Out of scope
- Per-day decay events (P3 — high noise on event log).
- TRIBUTE_CREDITS → TributeCondition (separate PRD; needs market-conditions wiring).
- DiplomacyBrain disablement verification (already done in `applyNex4xAuthorityOverNexDiplomacy`).
