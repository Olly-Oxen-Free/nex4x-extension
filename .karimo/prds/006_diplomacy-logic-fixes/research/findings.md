# Research Findings — Diplomacy Logic Fixes

## Source
Audit clusters 1, 2, 3, 4 standalone P0/P1 findings (THEME-E, THEME-F, plus orphans). 2026-05-10.

## Issues

### [P2 → promoted] Bilateral adjustRelationship 2× delta (THEME-E)
`FactionAPI.adjustRelationship` already mirrors. `DeclarationManager.java:153, 173, 222, 242` calls both sides → 2× delta on friendship/denounce/daily-gain/decay. Promoted to P1 because compounded effect makes balance tuning impossible.

**Fix:** Single-sided call.

### [P1] Owner-faction missing on agent actions (THEME-F)
`Nex4xAgentData` has no `ownerFactionId`. Cascades hit every faction's diplomats:
- `agents/diplomat/DiplomatExpulsionCascade.java:21` — global suspicion+damage
- `agents/diplomat/DiplomatPassiveManager.java:27` — single owner credited regardless of actual

**Fix:** Add `ownerFactionId` field on `Nex4xAgentData`, seed at creation (derive from `Nex4xAgentActionReportListener` via `CovertActionIntel.getAgentFaction()`). Filter cascades.

### [P1] DeclarationManager.declare() ignores type guards
`declarations/DeclarationManager.java:31` — no validation that DeclarationType respects unilateral / requiresReceiverConsent flags. FRIENDSHIP can be unilateral, bypassing accept flow. **Fix:** Branch on type guards in `declare()`.

### [P1] DeclarationManager friendship/denounce duplicates
Lines 146, 164 — helpers add directly without withdrawing prior same-type → duplicates accumulate, doubling effects (compounds with THEME-E).

**Fix:** Route through `declare()` which already has dedupe logic.

### [P1] DeclarationManager daily decay decoupled from elapsed days
Lines 218, 237 — `dailyRepGain`/`decayStep` applied once per call, not scaled by elapsed days; under/over-applies depending on tick rate.

**Fix:** Take and scale by `days` argument; or fire on `nextDayChanged()`. Combines well with PRD-B helper.

### [P1] Demands accept() is no-op
`demands/DemandManager.java:32, 55` — no validation in `issue()`; `accept()` only sets status, no credit transfer / alliance dissolution / war end. Demands have no in-world effect.

**Fix:** Validate inputs in `issue()` (non-null demanderId/targetId/type, non-self-target). Dispatch on type in `accept()` to apply effects.

### [P1] CasusBelliManager.countMilitaryMarkets ignores industry filter
`casusbelli/CasusBelliManager.java:303` — counts every market for faction; Containment CB triggers on raw colony count.

**Fix:** Add `m.hasIndustry(Industries.MILITARYBASE) || hasIndustry(Industries.HIGHCOMMAND) || hasIndustry(Industries.PATROLHQ)` and `!m.isHidden()`.

### [P1] TrustSensitivity NPE on null trait list
`badges/TrustSensitivity.java:25` — `DiplomacyTraits.getFactionTraits(String)` may return null; subsequent `traits.contains(...)` NPEs. Method also internally calls `Global.getSector()` — unsafe pre-game-load.

**Fix:** Null-guard. Never invoke before `onGameLoad`.

### [P1] FactionCompatibility reflects on non-existent NexFactionConfig fields
`integration/FactionCompatibility.java:57` — reads `militarism` and `diplomacyPositive`. `javap exerelin.utilities.NexFactionConfig` shows neither exists. Reflection always throws, swallowed silently → "Layer 2 auto-derive" never works.

**Fix:** Drop the layer or use real fields (`diplomacyPositiveChance`/`diplomacyNegativeChance` maps, `morality`, `pirateFaction`).

### [P1] MemoryManager IRREDENTIST/FOREVERWAR multipliers inverted
`managers/MemoryManager.java:83, 92` — returns `0.001f` shrinks decayDays toward zero → decay rate explodes → memory zeroes on first tick. Intent is opposite.

**Fix:** Return large multiplier (≥1000f) or invert the formula.

### [P1] AgreementType relationThreshold scale (also covered by PRD-A)
Already in PRD-A (1h). Note here for traceability.

### [P1] PoliticalModifier negative amounts expire instantly
`politics/PoliticalModifier.java:38` — `decay()/isExpired()` use `amount<=0`; negative-direction shifts dropped.

**Fix:** Track sign or use magnitude.

### [P1] strategic ProposeTieredAgreementAction has no side-effect
`strategic/action/ProposeTieredAgreementAction.java:45` — returns true after only `log.info`; no `AgreementManager.propose` / `DiplomacyManager.createDiplomacyEvent` call.

**Fix:** Invoke the actual proposal API.

### [P1] FactionBeliefsLoader vanilla-only
`data/FactionBeliefsLoader.java:48` — `VANILLA_FACTIONS[]` hardcoded; modded factions never load belief JSONs.

**Fix:** Iterate `Global.getSector().getAllFactions()` after sector exists.

### [P1] LeaderConfigRegistry/DialogueSystem null returns pre-load
`leaders/LeaderConfigRegistry.java:21`, `leaders/DialogueSystem.java:23` — `get()` returns null if `load()` not called.

**Fix:** Lazy autoload on first `get()`.

### [P1] IntelTierResolver swallowed Throwable
`leaders/IntelTierResolver.java:12` — hides real bugs; UI silently shows NONE intel forever.

**Fix:** `log.warn("...", t)`.

### [P1] WarScoreTracker declared-day (also PRD-B)
Cross-ref only.

## Not in scope
- Coalition vote quorum (P2 → PRD-G).
- Coalition tension hidden-write (P2 → PRD-G).
- VassalManager hardcoded estimateWeakness (P1 → see below; small, included).

### [P1] VassalManager hardcoded weakness
`vassals/VassalManager.java:90` — `estimateWeakness` hardcoded 0.3; all vassals rebel on same schedule.

**Fix:** Compute from `FactionPowerRankings` (post-PRD-D de-static) or recent market loss.

## Risks
- Cross-coupling with PRD-A (AgreementType) and PRD-B (decay scaling) — order matters: A and B before F's daily-decay task.
- Bilateral fix in DeclarationManager affects every relation event; verify behaviour via Console smoke before/after.
