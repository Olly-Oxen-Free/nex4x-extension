# nex4x Code Piping Audit — 2026-05-10

Branch: feat/v5-leader-dialogue
Scope: all of `src/nex4x/**` (202 .java files, 6 parallel audit clusters)
Validation: `starfarer.api.jar`, `starfarer_obf.jar`, `ExerelinCore.jar`, `reference/knowledge/`

## Totals

| Cluster | Scope | P0 | P1 | P2 | P3 | Total |
|---|---|---|---|---|---|---|
| 1 | agents/agreements/ai/evaluation | 0 | 6 | 6 | 2 | 14 |
| 2 | badges/casusbelli/coalitions | 0 | 2 | 6 | 2 | 10 |
| 3 | contracts/declarations/demands/leaders | 1 | 9 | 13 | 6 | 29 |
| 4 | managers/listeners/integration/influence/mediation/memory | 2 | 2 | 5 | 3 | 12 |
| 5 | negotiation/ui | 4 | 8 | 4 | 2 | 18 |
| 6 | peace/policies/.../ModPlugin | 0 | 7 | 14 | 6 | 27 |
| **Total** | | **7** | **34** | **48** | **21** | **110** |

Inline `// TODO(audit-P{n})` markers placed at every site.

---

## Systemic themes (cross-cluster)

These are not isolated bugs — same root cause replicated across many files. Best fixed as themed PRDs.

### THEME-A: Relation-scale mismatch (-1..1 vs -100..100) — **P0**
`FactionAPI.getRelationship` returns `-1.0..1.0`. Multiple sites compare against `10/20/40/50/60/80/-50/-70`.

**Affected (8+ sites):**
- `leaders/ReputationTier.java:18` — every classification → HOSTILE; called from `NegotiationPanel:748,797`, `WarDeclarationIntel:39`, `AIProposalIntel:174`, `BadgeReactionIntel:52`, `Valuator:40`, `Nex4xDebugCommand:36`
- `ui/DiplomacyIntel.java:200` — `relColor`/`repLabel` thresholds wrong
- `ui/DiplomacyTabPlugin.java:134` — same
- `ui/CoreUITabInjectorListener.java:179` — same (dead)
- `ui/NegotiationPanel.java:798` — `relationBadge` collapses to {-1,0,+1}
- `agreements/AgreementType.java:14` — alliance proposals never pass `canPropose`
- `leaders/LeaderAccessGate.java:18` — scale drift vs `DeclarationConfig` (which uses 0..100)
- `ui/DiplomacyTabOverlayModel.java:248,421` — uses correct convention; centralize helper

**Fix:** Single helper (`Nex4xRelations.toPercent(rel)` / `relCmpPct(rel, pct)`); migrate all sites.

### THEME-B: Time-unit confusion (seconds vs days) — **P1**
`CampaignClockAPI.getTimestamp()` returns seconds (long); used as days throughout strategic AI.

**Affected:**
- `ai/StrategicGoalManager.java:58` — importance recalcs every ~7s
- `ai/archetype/CommitmentLedger.java:100,185,266` — archetype hysteresis runs in seconds
- `ai/goals/GoalScorer.java:71,213` — historical-weight saturates instantly
- `wargoals/WarScoreTracker.java:37` — `declaredDay` is millis-cast-to-float
- `contracts/ContractAuctionManager.java:22` and `demands/DemandManager.java:26` — custom `now() = day + cycle*365` **skips months**
- `agreements/Agreement.java:88-92` — separate epoch math `(cycle-206)*365` diverges from rest

**Fix:** Standardize on `clock.getElapsedDaysSince(long timestamp)` pattern.

### THEME-C: Listener save-game integrity — **P0**
- `listeners/Nex4xInvasionBridge.java:20` — non-Serializable, registered without transient flag
- `listeners/Nex4xRaidBridge.java:12` — same
- `Nex4xAgentActionReportListener` — same pattern (out of cluster scope but identified)

**Fix:** `addListener(x, true)` (transient flag) + re-add each `onGameLoad`.

### THEME-D: Mod lifecycle gaps — **P1/P2**
`Nex4xModPlugin` overrides only `onApplicationLoad`, `onGameLoad`, `onNewGameAfterTimePass`.

**Missing:**
- `onNewGame` / `onNewGameAfterEconomyLoad` — new-campaign procgen NPE risk
- `beforeGameSave` / `afterGameSave` — no transient teardown
- `onGameSaveFailed`

Plus: `util/FactionPowerRankings` static state survives reloads (cross-session pollution).

### THEME-E: Bilateral relation double-application — **P2**
`FactionAPI.adjustRelationship` already mirrors. `DeclarationManager.java:153,173,222,242` applies both sides → 2× delta on friendship/denounce/daily-gain/decay.

### THEME-F: Owner-faction missing on agent actions — **P1**
`Nex4xAgentData` has no `ownerFactionId`. Cascades hit every faction's diplomats:
- `agents/diplomat/DiplomatExpulsionCascade.java:21` — global suspicion+damage
- `agents/diplomat/DiplomatPassiveManager.java:27` — single owner credited regardless of actual

---

## Standalone P0/P1 (not in a theme)

### P0
- `ui/CoreUITabInjectorListener.java:103` — overlay leak: component never removed on intel close
- `ui/DiplomacyTabOverlayModel.java:469` — class-shadow risk (`exerelin.campaign.intel.diplomacy.DiplomacyIntel` vs `nex4x.ui.DiplomacyIntel`)
- `ui/NegotiationPanel.java:91` — singleton guard race; throw in popup ctor leaves `activeInstance` set, locks all future negotiations until reload

### P1
- `casusbelli/CasusBelliManager.java:303` — `countMilitaryMarkets` doesn't filter by industry; Containment CB triggers on raw colony count
- `badges/TrustSensitivity.java:25` — `DiplomacyTraits.getFactionTraits` may return null → NPE on `.contains`
- `integration/FactionCompatibility.java:57` — reflects on non-existent `NexFactionConfig` fields (`militarism`, `diplomacyPositive`); whole layer dead, errors swallowed
- `managers/MemoryManager.java:83,92` — IRREDENTIST/FOREVERWAR multipliers inverted; memory zeroes on first tick instead of never
- `ui/NegotiationPanel.java:276` — `removeUI(); createUI(panelToInfluence);` rebuilds on detached panel
- `ui/BalanceBarPlugin.java:71` + `ui/DiplomacyOverlayPlugin.java:75` — GL state leak (glColor not restored, BLEND not popped) → tints subsequent UI
- `ui/DiplomacyOverlayPlugin.java:88` — info-log on every LMB
- `ui/DiplomacyTabOverlayModel.java:299,456` — double vertical advance (addCustom already advances + addSpacer adds again)
- `declarations/DeclarationManager.java:31` — `declare()` ignores type guards (FRIENDSHIP can be unilateral, bypasses consent)
- `declarations/DeclarationManager.java:146,164` — friendship/denounce helpers don't withdraw prior same-type → duplicates
- `leaders/LeaderConfigRegistry.java:21`, `leaders/DialogueSystem.java:23` — null returns when accessed before `load()`
- `leaders/IntelTierResolver.java:12` — `Throwable` swallowed; UI shows NONE forever
- `demands/DemandManager.java:32,55` — no validation; `accept()` has no in-world effect (no credit/alliance/war hook)
- `vassals/VassalManager.java:90` — `estimateWeakness` hardcoded 0.3; all vassals rebel on same schedule
- `strategic/action/ProposeTieredAgreementAction.java:45` — action only logs; no `AgreementManager.propose` / `DiplomacyManager.createDiplomacyEvent` call
- `data/FactionBeliefsLoader.java:48` — `VANILLA_FACTIONS[]` hardcoded; modded factions get no beliefs
- `politics/PoliticalModifier.java:38` — negative amounts expire instantly (`amount<=0` check)
- `Nex4xModPlugin.java:36` — bootstrap missing in `onNewGameAfterEconomyLoad`

---

## Detail by cluster

(Inline TODO markers in source. Full detail in transcript notification trail; canonical detail per finding lives at the inline TODO in source code. Cluster-by-cluster severity tables above.)

---

## Recommended PRD slicing

Suggest 6 themed PRDs + 1 cleanup PRD:

1. **PRD-A: Unify relationship scale** (THEME-A, ~9 sites) — single helper, migrate
2. **PRD-B: Standardize time-unit math** (THEME-B, ~8 sites) — switch to `getElapsedDaysSince`
3. **PRD-C: Fix listener save integrity** (THEME-C, 3 listeners) — transient flag + re-register
4. **PRD-D: Complete ModPlugin lifecycle** (THEME-D + static-state fix) — add 4 hooks, drop static caches
5. **PRD-E: Standalone correctness fixes — UI** (negotiation/intel P0/P1: singleton race, overlay leak, class shadow, GL leaks, panel detachment)
6. **PRD-F: Standalone correctness fixes — diplomacy logic** (DeclarationManager double-apply + type-guard bypass, demands no-op, owner-faction tracking, casus belli filter, etc.)
7. **PRD-G: P2/P3 cleanup batch** (defer; low risk)
