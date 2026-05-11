---
title: Faction State
theme: Faction State
source_files:
  - src/nex4x/leaders/
  - src/nex4x/coalitions/
  - src/nex4x/vassals/
  - src/nex4x/influence/
  - src/nex4x/pressure/
  - src/nex4x/memory/
  - src/nex4x/politics/
  - src/nex4x/policies/
  - src/nex4x/contracts/
  - src/nex4x/industries/
  - src/nex4x/data/
---

# Faction State

The composite of who runs a faction, who they're aligned with, what they
owe whom, and what they remember. None of this is in vanilla or Nex —
pure nex4x value-add.

## Leaders

`nex4x.leaders.LeaderRegistry` overlays leader profiles on faction
`PersonAPI`s. Per-faction `LeaderProfile` carries Personality
(MERCANTILE / ZEALOUS / HAUGHTY / RUTHLESS / PARANOID / GENIAL /
CAPRICIOUS / PRAGMATIC), title strings, and resolved-PersonAPI cache
(invalidated on `LeaderChangeEvent`).

`LeaderAccessGate` resolves player audience access via OR-logic gates:
- RAPPORT — player relation ≥ configured threshold
- COMMISSION — active commission with target faction
- OWN_FACTION — player owns N+ markets
- OTHER_MEANS — forward hook (plot tokens, etc)

`DialogueSystem` resolves greetings/situations via personality- +
faction-specific dialogue pools. Lazy-loads on first `get()` to survive
init-order issues.

`ReputationTier` (HOSTILE / SUSPICIOUS / NEUTRAL / FAVORABLE / COOPERATIVE)
classifies relations by **percent** (-100..100). Use `fromRawRelation(rel)`
to convert from raw `FactionAPI.getRelationship` (-1..1) → percent → tier.
See [[time-and-relations]] for the conversion helper.

## Coalitions

`CoalitionGovernance` runs multi-faction blocs above the AgreementType
ladder. Tracks tensions per pair (split read/write API:
`findTension` is pure, `getOrCreateTension` mutates). Votes via
`CoalitionVote` (quorum ≥ 50% participation; 14-day expiry; ties fail).

Coalition member changes mirror to Nex Alliance via
`NexDiplomacyBridge.syncCoalitionToAlliance` (multi-member join/leave).
COALITION agreements get a Nex Alliance shadow on creation.

## Vassals

`VassalManager` tracks overlord/vassal relationships. Vassal markets
auto-receive Nex `TributeCondition` (via `NexDiplomacyBridge.applyTributeToFaction`
on `vassalize` and reconciled daily for new conquests).
`estimateWeakness` reads `FactionPowerRankings` rank-percentile so
strong overlords resist rebellion better than weak ones.

## Influence + Pressure

`InfluenceManager` is per-faction soft-currency. Income from market size
plus building bonuses (DiplomaticEmbassy, IntelligenceBureau) polled via
`hasIndustry`. Cycle-based (`CYCLE_DAYS=30`) with float-accumulator to
absorb tick jitter. Spent on demand issuance, mediation, declaration
discounts.

`PressureManager` is asymmetric bilateral tension: pressure(A→B) is
independent of pressure(B→A). Sources include AGENT_ACTION,
GRIEVANCE, MILITARY. Decay rates loaded from `pressure_sources.json`.

## Memory + Politics

`MemoryManager` is per-event, persistent diplomatic memory keyed by
(holder, about). Memory types include `military_victory`, `military_defeat`,
`territorial_loss`, `commercial_partner`, `covert_operation`. Decay scaled
by faction traits: IRREDENTIST never forgets territorial; FOREVERWAR
never forgets military. Complementary to Nex's `MEM_KEY_BADBOY`.

`DynamicModifierManager` + `PoliticalModifier` are timed tendency-profile
shifts triggered by events (e.g., embassy bombing → +Militarist mood).
Sign-aware decay walks magnitude toward zero from either direction.

## Policies

`PolicyManager` lets a faction adopt one of N policies (MILITARY_BUILDUP,
AGGRESSIVE_POSTURE, OPEN_DIPLOMACY, COLLECTIVE_SECURITY, FREE_TRADE,
ECONOMIC_SANCTIONS, IDEOLOGICAL_PURITY, INFRASTRUCTURE_INVESTMENT,
AUTARKY, MISSIONARY_CAMPAIGN, CONSERVATION_MANDATE). `PolicyEffect.getModifier(type, stat)`
returns stat-modifier values consumed by other systems.

## Contracts + Industries

`ContractAuctionManager` runs second-price auctions for diplomatic
contracts. Three nex4x buildings declare themselves via `hasIndustry`
queries:
- CyberSecuritySuite — counter-espionage bonus
- DiplomaticEmbassy — influence income
- IntelligenceBureau — counter-espionage + influence

Effects are polled by `InfluenceManager` / `DetectionEngine`; the
industries themselves only call `super.apply(true)`.

## Data + Beliefs

`FactionBeliefsLoader` loads per-faction belief JSONs from
`data/config/nex4x/faction_beliefs/*.json`. Vanilla factions loaded
in `onApplicationLoad`; modded factions loaded in `onGameLoad` via
`loadForLiveFactions()`. Beliefs feed CB recognition (ideological
conflict) and archetype tendency drift.

## Related

- [[diplomacy-core]] — agreements/declarations/demands consume faction state
- [[strategic-ai]] — AI reads beliefs, tendencies, memories to score goals
- [[nex-integration-layer]] — TributeCondition, FactionPowerRankings handoff
