# Research Findings — Nex Alliance + CB Sync

## Source
Post-PRD-009 integration audit. Three distinct gaps where Nex still doesn't see nex4x state.

## Problem
After PRD-009 nex4x diplomacy events flow through `DiplomacyManager`, but:

1. **Justified wars still incur warmonger penalty** — our CasusBelli system grants an
   in-fiction justification, but `DiplomacyManager.createDiplomacyEvent("declare_war", ..)`
   still triggers `WarmongerEvent` + `MEM_KEY_BADBOY` accumulation. Player gets full
   warmonger rep loss even with valid CB.

2. **Coalition member changes don't sync to Nex Alliance** — PRD-009 9c creates a
   Nex `Alliance` only when a COALITION pair is formed. If a third faction joins
   our coalition (or one leaves), the Nex Alliance stays stale.

3. **AgreementManager NAP/DefensivePact/Mil Partnership tiers (1–3) invisible to Nex AI** —
   Nex's `DiplomacyManager.disallowedFactions` is a static per-faction blacklist,
   not per-pair. NAP signatories can still have Nex declare war between them.

4. **DiplomacyBrain re-spawn risk (verification)** — we `StrategicAI.removeAIs()` on
   load, but new factions created mid-session (procgen, faction creation event) may
   spawn fresh brains.

## API verification
```
javap exerelin.campaign.DiplomacyManager$DiplomacyEventParams
  public boolean onlyPositive;
  public boolean onlyNegative;
  public boolean useDominance;
  public float positiveChanceMult;
  public float negativeChanceMult;
javap exerelin.campaign.AllianceManager
  public void joinAlliance(String, Alliance);
  public void leaveAlliance(String, Alliance, boolean, boolean);
  public static Alliance getFactionAlliance(String);
javap exerelin.campaign.events.WarmongerEvent
  // Triggered by DiplomacyManager on aggressive war declarations.
javap exerelin.campaign.ai.StrategicAI
  public static void removeAIs();
```

`DiplomacyEventParams` doesn't have an explicit "no warmonger" flag. Strategy: check
WarmongerEvent's source to see what gates it. If it reads `MEM_KEY_BADBOY` directly,
we can decrement after the war event fires. If gated by mem key on the faction, we
can set a "$nex4x_justified" flag pre-event and clear it after.

Inspect `WarmongerEvent.class` body (decompile or javap -c) to confirm.

## Affected sites
- `src/nex4x/ai/DiplomaticExecutor.java:181/201` — declare_war + ceasefire createDiplomacyEvent calls
- `src/nex4x/integration/NexDiplomacyBridge.java` — extend with `fireJustifiedWar`, `syncCoalitionMembership`, `enforceNonAggression`
- `src/nex4x/coalitions/CoalitionGovernance.java` — when adding/removing members, mirror to Nex Alliance
- `src/nex4x/agreements/AgreementManager.java` — when NAP/DefPact/MilPartner created, populate per-pair veto

## Approach

### Item 1 (CB recognition)
Two paths, pick simpler:
- (A) Decrement badboy memory after `createDiplomacyEvent("declare_war", ..)` if CB present
- (B) Set a pre-event memory flag `$nex4x_justified_war` and have a Nex listener (we'd
  add) clear `MEM_KEY_BADBOY` increments

Option A is simpler — fire the event normally, then read `MEM_KEY_BADBOY` value, undo
the latest increment if our CasusBelli check passes. Implement in `NexDiplomacyBridge.fireJustifiedWar`.

### Item 2 (Coalition Alliance sync)
- In `CoalitionGovernance.addMember(coalition, factionId)` or equivalent, after
  internal add, call `AllianceManager.getManager().joinAlliance(factionId, allianceFor(coalition))`
- In `removeMember`, call `AllianceManager.getManager().leaveAlliance(factionId, alliance, false, false)`
- Track which Nex Alliance corresponds to which nex4x Coalition (use a map keyed by
  coalition id → alliance id)

### Item 3 (NAP/DefPact pair veto)
Nex doesn't have a per-pair veto API directly. Options:
- (A) Apply a relationship floor: `MIN_RELATIONSHIP_TO_JOIN`-style clamp via
  `DiplomacyManager.clampRelations(factionA, factionB, +0.3)` daily
- (B) Listen to `DiplomacyManager` events and reject war declarations between
  NAP signatories — requires hooking into Nex's event flow
- (C) On NAP/DefPact creation, immediately set relationship to FAVORABLE/FRIENDLY via
  `setRelationship` so Nex's internal hostility floor never triggers war between them

Option C is simplest. NAP → ensure rel >= 0.10 (FAVORABLE); DefPact → 0.25 (WELCOMING);
Mil Partnership → 0.50 (FRIENDLY).

### Item 4 (DiplomacyBrain re-sweep)
Add a daily check in `Nex4xManager.advance` (after the existing sub-manager block):
`if (!NexConfig.enableStrategicAI) StrategicAI.removeAIs();` — idempotent if no brains
exist; cheap to call.

## Risks
- WarmongerEvent suppression — if we miss a path, badboy still accumulates incorrectly.
  Wrap each suppression in try/catch with log; verify via test scenario.
- Coalition→Alliance sync — risk of joining a faction to an alliance Nex has already
  marked incompatible. `AllianceManager.canAlly` should be checked first.
- Setting relationship to FAVORABLE on NAP creation may surprise players if the prior
  rel was lower. Document in NAP description; only nudge floor (Math.max), don't
  override higher existing rel.

## Out of scope
- TributeCondition wiring (PRD-011).
- Per-faction Nex disposition tweaks beyond pair-veto enforcement.
