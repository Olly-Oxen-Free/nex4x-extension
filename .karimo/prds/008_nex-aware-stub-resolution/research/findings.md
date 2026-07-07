# Research Findings — Nex-Aware Stub Resolution

## Source
Post-PRD-1..7 cleanup survey (2026-05-10). Nex API verified via `javap` over `ExerelinCore.jar` (Nexerelin 0.12.1d).

## Principle
**Don't duplicate Nex. Enhance or delegate.** Every nex4x stub gets one of four dispositions:
- **DELETE** — Nex already does this; we'd duplicate.
- **DELEGATE** — call into Nex API; thin nex4x wrapper for type safety.
- **IMPLEMENT** — true gap in Nex; nex4x adds it.
- **KEEP** — explicit forward-looking hook with no current owner.

## Nex APIs that already cover our gaps
- `exerelin.campaign.CovertOpsManager` — full covert ops scheduling/execution (~40k bytecode).
- `exerelin.campaign.intel.agents.AgentIntel` — agent UI, orders, status, specialization.
- `exerelin.campaign.intel.agents.CovertActionIntel` — covert action lifecycle + StoryPointUse.
- `exerelin.campaign.AllianceManager` — `createAlliance`, `joinAlliance`, `leaveAlliance`, `dissolveAlliance`, `mergeAlliance`, `canAlly`.
- `exerelin.campaign.alliances.AllianceVoter` — vote with abstain/defy/hawkishness/weariness/strength weights.
- `exerelin.campaign.DiplomacyManager` — `createDiplomacyEvent(stage, params)`, `adjustRelations`, `clampRelations`, `setRelationship`. Stage strings: `peace_treaty`, `cease_fire`, `surrender`, `tribute`, etc.
- `exerelin.campaign.SectorManager.transferMarket(market, oldFaction, newFaction, …)` — direct market transfer.
- `exerelin.campaign.ai.StrategicAI` + `StrategicDefManager.STRATEGIC_CONCERNS` — pluggable concern/action registry.

## Per-stub disposition matrix

| # | Stub | Disposition | Rationale |
|---|---|---|---|
| 1 | `agents/Nex4xAgentActionProposer` (empty) | **DELETE** | `CovertOpsManager` already proposes covert actions. Our role is reactive (already wired via `Nex4xAgentActionReportListener`). |
| 2 | `agents/Nex4xAgentManager.getDiplomatIntelScore` returns 0 | **IMPLEMENT** | True gap. Derive from per-faction diplomat count × buildup level (data already tracked on `Nex4xAgentData`). |
| 3 | `ui/AgentUIStub` | **DELETE** | Duplicates `AgentIntel` vanilla Nex UI. |
| 4 | `ui/ProfileExtender` | **DELETE** | Already deprecated; replaced by `FactionBrowserIntel`. Sweep already runs in `removeLegacyDossierIntels`. |
| 5 | `integration/Nex4xStrategicAIConcerns.register()` no-op | **IMPLEMENT** | Register our `BeliefAlignmentConcern` + `GoalWrapConcern` with `StrategicDefManager.STRATEGIC_CONCERNS`. Bridges nex4x goals into Nex StrategicAI. |
| 6 | `negotiation/NegotiableItemCatalog` credits-only | **IMPLEMENT** | Real gap. Expand with: market access, fleet support, prisoner exchange, technology share, declarations. (Original Phase 11 scope.) |
| 7 | `agents/ai/AgentAI` placeholder | **DELETE** | Duplicates `CovertOpsManager` AI loop. |
| 8 | `negotiation/Valuator.scarcityMod = 1.0` | **IMPLEMENT** | Query `MarketAPI.getCommodityData(id).getAvailable()` + monthly demand. |
| 9 | `demands/DemandManager.applyDemandEffect` BREAK_ALLIANCE/CEDE_MARKET | **DELEGATE** | `AllianceManager.leaveAlliance` for BREAK_ALLIANCE; `SectorManager.transferMarket` for CEDE_MARKET. |
| 10 | `leaders/LeaderAccessGate.checkOtherMeans` false | **KEEP** | Explicit forward hook for plot tokens; document in javadoc. |
| 11 | `evaluation/DesperationCalculator` "war score not implemented" | **IMPLEMENT** | Pull from `WarScoreTracker.getWarScore`. |
| 12 | `listeners/Nex4xEventListener` "trade memory placeholder" | **IMPLEMENT** | Hook `reportPlayerOpenedMarket` / `reportPlayerEngagement`-adjacent for trade-relationship memories. Minimal version. |
| 13 | `industries/CyberSecuritySuite/DiplomaticEmbassy/IntelligenceBureau` no economic effect | **IMPLEMENT** | Wire `apply()` with demand/supply/income via `BaseIndustry` patterns. |
| 14 | `ui/BadgeReactionIntel` hardcoded polarity | **IMPLEMENT** | Move `isPositive` to `BadgeType` enum field. |
| 15 | `negotiation/Valuator` v5 stub for goal-alignment | **IMPLEMENT** | Look up `Nex4xManager.getGoalManager(factionId).getActiveGoals()` and check overlap. |
| 16 | Audit P2 — `peace/PeaceConference` no state machine | **IMPLEMENT** | Real gap (Nex has `MakePeaceAction` only). Add PROPOSED → COUNTER_OFFERED → FINAL states. |
| 17 | Audit P2 — `votes/VoteOutcome` no abstain | **DELEGATE** | Mirror `AllianceVoter.Vote` (FOR/AGAINST/ABSTAIN). |
| 18 | Audit P2 — `coalitions/CoalitionVote` no quorum/expiry | **IMPLEMENT** | Add quorum + creation-day timestamp + auto-discard. |
| 19 | Audit P2 — `coalitions/CoalitionGovernance.getTension` lazy mutation | **IMPLEMENT** | Split read/create. |
| 20 | Audit P2 — `mediation/MediationManager` no validation | **IMPLEMENT** | Validate inputs; tick-vs-day fix uses `Nex4xClock`. |
| 21 | Audit P2 — `wargoals/WarGoal` no expired state | **IMPLEMENT** | Add OBSOLETE state for destroyed-target claims. |
| 22 | Audit P2 — `pressure/PressureManager.accumulationPerDay` parsed-not-consumed | **IMPLEMENT** | Wire into per-day accumulation. |
| 23 | Audit P2 — `vassals/VassalManager.estimateFactionIncome` size×10000 | **IMPLEMENT** | Sum `MarketAPI.getNetIncome()`. |
| 24 | Audit P2 — `influence/InfluenceManager.daysAccumulated` ticks-not-days | **IMPLEMENT** | Pass elapsed through. |
| 25 | Audit P2 — `policies/PolicyEffect` MISSIONARY/CONSERVATION no branches | **IMPLEMENT** OR **DELETE** | Add branches if used; otherwise prune enum values. |
| 26 | `BadgeReactionIntel:55` `TODO(v5.1)` polarity | already covered by #14 | — |

## Out of scope
- Tier-1 trait overlay pools / dialogue copy expansions (content work, separate PRD).
- Refactoring `FactionPowerRankings` to instance state (PRD-D 4d covered the invalidate hook; full de-static deferred).

## Risks
- **Nex API drift** — we lock to Nexerelin 0.12.1d. If a player runs a different version, reflection-based calls (FactionCompatibility) already handle gracefully; our new direct calls will fail-fast with NoSuchMethodError. Mitigation: wrap each Nex API call in try/catch with log.warn; never let a Nex incompat brick nex4x.
- **StrategicAI registration ordering** — concerns must register on `onApplicationLoad`. Verify Nex's `StrategicDefManager` is ready by then.
- **Industry economic changes** — wiring real demand/supply will shift colony balance. Numbers should be conservative.
