---
title: Diplomacy Core
theme: Diplomacy Core
source_files:
  - src/nex4x/agreements/
  - src/nex4x/declarations/
  - src/nex4x/demands/
  - src/nex4x/mediation/
  - src/nex4x/peace/
  - src/nex4x/votes/
---

# Diplomacy Core

The interlocking systems players actually interact with at the negotiation
table. nex4x layers a richer ladder + state-machine model on top of
Nexerelin's binary war/peace primitives — both sides see each other's state
through [[nex-integration-layer]].

## Agreements ladder

`nex4x.agreements.AgreementType` defines a 5-tier ladder:

| Tier | Type | Notes |
|---|---|---|
| 0 | COLD_WAR | No agreement; aggression allowed |
| 1 | NAP (Non-Aggression Pact) | Floor: rel ≥ 0.10 |
| 2 | DEFENSIVE_PACT | Floor: rel ≥ 0.25 |
| 3 | MILITARY_PARTNERSHIP | Floor: rel ≥ 0.50 |
| 3 | ECONOMIC_PARTNERSHIP | Parallel, rel ≥ 0.25 |
| 4 | COALITION | Materializes as Nex `Alliance` |
| — | TRADE_AGREEMENT | Parallel track, no ladder gating |

`AgreementManager.createAgreement` performs the tier transition AND fires
`NexDiplomacyBridge.enforceNonAggression(a, b, floor)` so Nex's AI cannot
declare war between signatories. COALITION additionally calls
`NexDiplomacyBridge.ensureAlliance` (creates/joins the real Nex Alliance).
Daily `advanceDay` re-applies the floor so Nex drift doesn't sink relations.

## Declarations

`nex4x.declarations.DeclarationManager` issues `FRIENDSHIP` and `DENOUNCE`
declarations with timed expiry + linear post-expiry decay. Each declaration
fires a Nex DiplomacyManager event (`respect` / `insult` stage) via
`NexDiplomacyBridge.fireRespectEvent` / `fireInsultEvent`, so the change
appears in vanilla DiplomacyIntel log and accumulates badboy correctly.
Denouncements ripple to allies of the target (insult per ally).

`declare()` enforces type guards (only `unilateral` types accepted) and
withdraws prior same-type declarations to prevent stacking. `advanceDay`
takes `elapsedDays` to scale daily rep gain/decay correctly under tick
jitter.

## Demands

`nex4x.demands.DemandManager` issues formal demands between factions with
pressure + influence cost. Types: `TRIBUTE_CREDITS`, `CEDE_MARKET`,
`BREAK_ALLIANCE`, `END_WAR`. On accept, dispatches to real in-world effects:

- `TRIBUTE_CREDITS` → one-shot credit grant + persistent `TributeCondition`
  on all target-faction markets (via `NexDiplomacyBridge.applyTributeToFaction`)
- `CEDE_MARKET` → `SectorManager.transferMarket`
- `BREAK_ALLIANCE` → `AllianceManager.leaveAlliance`
- `END_WAR` → Nex peace_treaty event via `NexDiplomacyBridge.firePeaceTreaty`

`issue()` validates non-null + non-self-target + non-negative cost +
payload (e.g. CEDE_MARKET requires marketId).

## Mediation

`nex4x.mediation.MediationManager` lets a third-party faction broker peace
between two hostiles. `propose(mediator, A, B, influence)` validates:
both factions non-null, distinct, currently hostile, mediator not a
belligerent, positive investment, mediator can afford the influence.
On SUCCESS resolution, fires Nex peace_treaty.

## Peace Conferences

`nex4x.peace.PeaceConference` is a state machine for war resolution:
PROPOSED → COUNTER_OFFERED ↔ → ACCEPTED | REJECTED | EXPIRED.
`accept()` fires Nex peace_treaty via the bridge.

Player surface: [[player-ui-surface]] documents the
`PeaceConferenceDialog` and negotiation catalog buttons.

## Votes

`nex4x.votes.VoteOutcome` models advisory votes with per-tendency
breakdown. Tracks for/against/abstain separately; `getPercentFor`
returns -1 when no decisive votes cast (distinguishable from tie).
Coalition votes (`CoalitionVote`) add quorum + 14-day expiry.

## Related

- [[strategic-ai]] — AI's perspective on diplomacy
- [[conflict-system]] — what justifies war and how it resolves
- [[nex-integration-layer]] — how all of the above flows into Nex APIs
- [[player-ui-surface]] — negotiation panel + dialogs
