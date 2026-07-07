# PRD: Dead Code Removal

**Slug:** dead-code-removal
**Created:** 2026-05-31
**Severity:** P3 — cleanup
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Delete every verified-unreferenced class and dead private method identified in the May 2026
audit, leaving the compile surface lean and making future refactors safer.  The build stays
green after each individual removal; no `data/**` resource names are broken; Serializable
classes that were never persisted are hard-deleted (no empty-shell needed).

## Why now

~20 dead classes and several dead methods accumulated during rapid iteration on the diplomacy
overlay, agent system, and early vote/negotiation prototypes.  They inflate compile times, confuse
code search, and create false "entry points" for future developers.  All rescued items have been
confirmed rescued by other planned PRDs; the remaining items are inert.

## Success criteria

- `./build.sh` exits 0 after every task (no task may leave the build broken).
- `grep -rn "<ClassName>" src/ data/` returns zero hits outside the now-deleted file for every
  removed class.
- `grep -rn "<methodName>" src/` returns zero external hits for every removed method.
- No `data/**` JSON or CSV references a removed class path.
- `VoteProposal`, `VoteOutcome`, `AgentUIStub`, `ReflectionBridge`, `AutoBalanceSolver`,
  `BalanceSurface`, `CounterOfferGenerator`, `AgentAI`, `AgentSynergy`, `TrustSensitivity`,
  `nex4x.ui.DiplomacyIntel` no longer exist in `src/`.

## Scope

### In — CONFIRM-DEAD (remove now)

**Dead classes** (zero external references, not in `data/**`):

| Class | File | Verified ref count |
|-------|------|--------------------|
| `AgentUIStub` | `src/nex4x/ui/AgentUIStub.java` | 0 |
| `ReflectionBridge` | `src/nex4x/ui/ReflectionBridge.java` | 0 |
| `nex4x.ui.DiplomacyIntel` | `src/nex4x/ui/DiplomacyIntel.java` | 0 (superseded by overlay; `DiplomacyTabOverlayModel` imports only `exerelin.campaign.intel.diplomacy.DiplomacyIntel` via FQN) |
| `AutoBalanceSolver` | `src/nex4x/negotiation/AutoBalanceSolver.java` | 0 |
| `BalanceSurface` | `src/nex4x/negotiation/BalanceSurface.java` | 0 |
| `CounterOfferGenerator` | `src/nex4x/negotiation/CounterOfferGenerator.java` | 0 |
| `AgentSynergy` | `src/nex4x/agents/synergy/AgentSynergy.java` | 0 |
| `AgentAI` | `src/nex4x/agents/ai/AgentAI.java` | 0 |
| `VoteProposal` | `src/nex4x/votes/VoteProposal.java` | 0 external (only referenced by `VoteOutcome` field inside same package; PRD-022 explicitly defers removal here) |
| `VoteOutcome` | `src/nex4x/votes/VoteOutcome.java` | 0 external |
| `TrustSensitivity` | `src/nex4x/badges/TrustSensitivity.java` | 0 |

**Dead methods inside live classes:**

| Method | Class | Verified ref count |
|--------|--------|--------------------|
| `buildFactionCardList` | `CoreUITabInjectorListener` | 0 external; not called within class either |
| `getBrowsableFactions(String)` | `CoreUITabInjectorListener` | 0 external |
| `hasMarkets(String)` | `CoreUITabInjectorListener` | 0 external |
| `relColor(float)` | `CoreUITabInjectorListener` | 0 external |
| `repLabel(float)` | `CoreUITabInjectorListener` | 0 external |
| `setTabButtons(UIComponentAPI, List, Runnable, Runnable)` | `DiplomacyOverlayPlugin` | 0 (never called; the 4 fields it sets remain null, causing `processInput` to always short-circuit) |
| `diplomacyBtnComp` field + `otherBtnComps` + `showCallback` + `hideCallback` fields | `DiplomacyOverlayPlugin` | set only by `setTabButtons`; effectively unreachable |
| `getAvailableAgreementTypes()` | `NegotiationPanel` | 0 |
| `hasMarkets(String)` | `NegotiationPanel` | 0 |
| Unused imports: `Nex4xSettings`, `PressureManager`, `PressureSource` | `NegotiationPanel` | 0 usages in file |
| `DiploEventTooltip` (private inner class) | `DiplomacyTabOverlayModel` | constructed nowhere (0 `new DiploEventTooltip` hits) |
| `getAttackerStrength` | `DetectionEngine` | 0 |
| `detectionChance(float, float)` | `DetectionEngine` | 0 external (`rollDetection` calls it; but `rollDetection` is also dead) |
| `rollDetection(float, float)` | `DetectionEngine` | 0 |
| `applyCycleExpense(float, InfluenceSource)` | `InfluenceLedger` | 0 |

### Out — rescued / blocked (do NOT remove)

| Item | Reason |
|------|--------|
| `src/nex4x/strategic/concern/BadgeProvocationConcern.java` | LIVE — already referenced in `data/config/exerelin/strategicAIConfig.json` classPath; PRD-015 also registers it |
| `src/nex4x/strategic/action/DeclareWarFromDesireAction.java` | LIVE — same `strategicAIConfig.json` |
| `src/nex4x/strategic/action/SeekPeaceFromWillingnessAction.java` | LIVE — same `strategicAIConfig.json` |
| `src/nex4x/negotiation/BaseValueTable.java` | LIVE — called in `Nex4xModPlugin.onGameLoad`; PRD-016 single value source |
| `src/nex4x/negotiation/BalanceCalculator.java` | Called by `Nex4xDebugCommand` (live); PRD-016 deprecates but removal only after 016 folds logic — see task 25k |
| `src/nex4x/agents/security/MarketSecurityData.java` | Used as local variable inside live `DetectionEngine.getMarketDetection` — NOT dead |
| `NegotiationPanel.addThreeZoneRow` | PRD-020 explicitly repurposes this method for catalog request dispatch |
| `ItemCategory` unused enum constants (`STAR_CHART/NAP/TRADE_PACT/DENOUNCEMENT_DECLARATION`) | PRD-016 `mapCategory` will use them |

## Risks

### Savegame deserialization

Any class that `implements Serializable` and was **ever persisted** in a manager stored in the
campaign save will cause `ClassNotFoundException` on load after deletion.

Assessment of candidates:

- `VoteProposal` / `VoteOutcome` — both `implements Serializable` but grep confirms they were
  **never instantiated** anywhere (`new VoteProposal` / `new VoteOutcome` = 0 hits). Safe to
  hard-delete.
- `nex4x.ui.DiplomacyIntel` — extends `BaseIntelPlugin` (intel items are saved), but
  `new DiplomacyIntel` = 0 hits; never added to IntelManager. Safe to hard-delete.
- `MarketSecurityData` — `implements Serializable` but only used as a transient local variable
  inside `getMarketDetection`. Safe — but it is **KEEP** (live reference from that method).
- All other dead classes are plain non-Serializable POJOs/utilities. No save risk.

**Recommendation:** No empty-shell pattern needed for any removed class. If a save was created
with a version of the mod that never registered these classes, there is nothing to deserialize.

## Acceptance

- `./build.sh` green after each task.
- `grep -rn "nex4x.ui.AgentUIStub\|nex4x.ui.ReflectionBridge\|nex4x.ui.DiplomacyIntel\|nex4x.negotiation.AutoBalanceSolver\|nex4x.negotiation.BalanceSurface\|nex4x.negotiation.CounterOfferGenerator\|nex4x.agents.synergy.AgentSynergy\|nex4x.agents.ai.AgentAI\|nex4x.votes.\|nex4x.badges.TrustSensitivity" src/ data/` → zero hits.
- `grep -rn "setTabButtons\|buildFactionCardList\|getAvailableAgreementTypes\|DiploEventTooltip\|applyCycleExpense" src/` → zero hits.

## Tasks

See `tasks.yaml`.
