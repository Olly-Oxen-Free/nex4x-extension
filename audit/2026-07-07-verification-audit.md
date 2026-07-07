# nex4x Verification Audit — 2026-07-07

Branch: `feat/v5-leader-dialogue`. Third audit pass: verifies the **uncommitted** working-tree fixes
(PRDs 013–023, executed ~2026-05/06 but never committed; ~2750 insertions across 52 files + 5 new files)
against the 2026-05-31 findings, and scopes remaining work (PRDs 024/025 still `planned`).

Method: 5 parallel read-only audit agents tracing full call chains, validating Nexerelin APIs against
`ExerelinCore.jar` (0.12.1d). Build: `./build.sh` compiles all 208 sources clean, jar OK.

---

## Verdict on prior findings

| 2026-05-31 finding | Status | Evidence |
|---|---|---|
| P0: covert actions never register (app-load NPE swallowed) | **FIXED** | registration moved to `onGameLoad` (`Nex4xModPlugin.java:143-151`), idempotent via `actionDefsById.containsKey` guard, single caller |
| P0: respect/insult stages don't exist → declarations apply zero rep | **FIXED** | new `data/config/exerelin/diplomacyConfig.json` (events-only) **merges** — Nex loads via `getMergedJSONForMod` (verified `DiplomacyManager.java:120` + Iron Shell precedent); bridge now checks null return, warns, applies `adjustRelationship` fallback; "log success on failure" removed |
| Strategic AI war path unreachable (setPosture never called) | **FIXED** | `GoalGenerator.applyPosture` post-pass (`:194-208`) sets HOSTILE; `DiplomaticExecutor.evaluateWarDecisions` → `declareWar` → real `DiplomacyManager.createDiplomacyEvent("declare_war")` / `fireJustifiedWar` |
| Peace/agreement proposals log-only | **FIXED** | `firePeaceTreaty` → real `createDiplomacyEvent("peace_treaty")`; agreements via `AgreementManager.createAgreement` + `AllianceManager.ensureAlliance` |
| Valuator (EconomyAPI engine) dead; live path hardcoded | **FIXED** | live chain NegotiationPanel → DealEvaluator → DealPackage → `ItemValuator.valueForLeader` → `Valuator.valueTo` (personality×goal×relation×scarcity, scarcity reads EconomyAPI). `BalanceCalculator` now `@Deprecated` off-path |
| Territory priced but transfers nothing | **FIXED** | `SectorManager.transferMarket(...)` (Nex-aware, not raw setFactionId), ownership-guarded — both `NegotiationDealExecutor.java:90` and `PeaceConference.java:108` |
| PeaceConference.accept() ignores terms; counter-offer placeholder | **FIXED** | accept iterates terms → applyTerm (cede/reparations/treaty); counter-offer editor real (remove-terms-only by design) |
| Industries never seeded to AI factions | **FIXED** | new `IndustrySeeder.seedAll()` from `onNewGameAfterEconomyLoad` (new-game only, documented); ids match industries.csv; effects consumed by InfluenceManager income + DetectionEngine |
| Agent ownership broken | **FIXED** | new `AgentOwnershipSweep` daily from Nex4xManager; owner consumed by passive drip, expulsion cascade, contract auctions |
| Demands/contracts/declarations/agreements effect-free | **FIXED** (mostly) | real credits/transferMarket/peace/alliance-leave/tribute effects; contract payoffs real (covert dispatch deferred, documented) |
| Relation scale −1..1 vs −100..100 | **FIXED** | all sites route through new `Nex4xRelations.toPercent/toPercentInt/atLeastPct` (22 call sites); grep-clean |
| Day math 365 vs 360, mixed epochs | **PARTIAL** | unified on `Nex4xClock.currentAbsoluteDay()` = `cycle*360+(month-1)*30+day`, no 365 left in prod. **BUT no legacy-save re-basing** — `isLegacyDayValue` helper only used by debug command; pre-fix saves get wrong expiry math |
| Save integrity (PRD 024) | **NOT RUN** | listener registration is actually clean (transient + guards); remaining backlog below |
| Dead code removal (PRD 025) | **NOT RUN** | list refreshed below |

Java `Nex4xStrategicAIConcerns.register()` id-mismatch: fixed by making it a no-op and removing the call.

---

## New / remaining issues (this pass)

### A. Commit hygiene — CRITICAL first step
The entire verified fix campaign (PRDs 013–023) is **uncommitted**: 52 modified src files, 5 new files,
untracked `diplomacyConfig.json` + `industry_seeding.json` + PRD dirs. One crash/`git checkout` loses it.

### B. Exploits / economy holes (negotiation & viceroy)
1. **No affordability check on player credit offers** — `transferCredits` clamps to `min(amount, balance)` at execution but the AI evaluates face value → offer 1M you don't have, pay what you hold, keep the planet. `NegotiationDealExecutor.java:178-180`.
2. **Tribute priced but never paid** — executor writes memory only; no recurring transfer ever scheduled. AI accepting player tribute is defrauded by the engine. Same for KNOWLEDGE/INTEL/PRISONERS items (memory-only), CONTRACTS/CONCESSIONS (mostly unreachable/no-op).
3. **Wetwork: 25 000 cr for nothing** — deducts + writes `wetwork_contract` memory; `onDiscovered` helper dangling, action never executes. `WetworkHandler.java:94-114`.
4. **Intel purchase cosmetic** — real credits for flavor-text-only `ViceroyIntelItem`.
5. **Territory offer-side wrong** — `idsForType(TERRITORY)` lists only target-owned markets, but panel renders `[+ Offer]` too → player "offers" the AI its own planet (silent no-op). Offer side should list player-owned markets. `NegotiableItemCatalog.java:160-172`, `NegotiationPanel.java:593-600`.

### C. AI tuning gaps
6. **Peace under-fires** — `PEACE_SCOPE_ACHIEVED_BONUS` (40) and `PEACE_ACCEPT_THRESHOLD` (50) loaded but never referenced in `calculatePeaceWillingness`; willingness>80 needs weariness ≈10 000 while END_WAR goal spawns at 5 000. `DiplomaticExecutor.java:108-110,324-339`.
7. **No war-declaration cooldown** — a faction can declare on multiple targets same day (≤8 goals); no ally/pact hard-block (soft penalties only). Dead JSON path had `cooldown:60`.
8. **buildThreat only sees already-hostile factions** — pre-war aggressors never register as crisis. `StrategicGoalManager.java:181,195`.

### D. Save integrity (PRD-024 backlog, refreshed)
9. **Legacy day values not re-based on load** — epoch changed (365→360, epoch-206 removed) with `isLegacyDayValue` helper written but wired only into `Nex4xDebugCommand.java:530`. Pre-fix saves: wrong days-remaining/expiry everywhere absolute days are stored.
10. `CoreUITabInjectorListener.java:38-39` static `lastIntelPanel`/`lastOverlay` never reset on load — stale cross-save UI graph retention (Medium).
11. `AIProposalIntel.java:66-67` non-transient `addScript(this)` + PopupDialogScript → save bloat (Low).
12. `PeaceConferenceDialog`/`NegotiationPanel` static `activeInstance` not reset on onGameLoad (Low).
13. `FactionPowerRankings.denom` cache invalidated only in beforeGameSave, not onGameLoad (Low).

### E. Dead code (PRD-025 list, refreshed 2026-07-07)
- **Delete:** `AgentUIStub`, `ReflectionBridge`, `nex4x.ui.DiplomacyIntel`, `AutoBalanceSolver`, `BalanceSurface` (both modified but still unreferenced), `CounterOfferGenerator`, `AgentSynergy`, `AgentAI`, `VoteProposal`+`VoteOutcome` (cluster), `TrustSensitivity`, `BadgeProvocationConcern`+`DeclareWarFromDesireAction` (cluster), `SeekPeaceFromWillingnessAction`.
- **Delete or document-as-inert:** `data/config/exerelin/strategicAIConfig.json` + `src/nex4x/strategic/*` action/concern classes — they target Nex's StrategicAI which nex4x itself disables (`enableStrategicAI=false`); active path is DiplomaticExecutor.
- **KEEP:** `BaseValueTable` (live: ModPlugin, ItemValuator, NegotiableItemCatalog).
- Stale locked git worktree `.claude/worktrees/agent-aee84118a095902dd` (orphan commit fda4c23, NexDiplomacyBridge draft superseded by current file) — prune.

### F. Documented deferred no-ops (accept or wire)
- Coalition votes ADD_MEMBER / KICK_MEMBER / DISSOLVE — logged no-ops, `// TODO PRD-022 follow-up`.
- Contract covert-action dispatch — payoff real, dispatch deferred (no Nex API).
- Vassal `rebellionActive` flag set/read only inside vassals package — no consequence consumes it.
- Counter-offer remove-terms-only (design limitation).
- Embassy CSV desc claims "boosts market relations" — no such effect; `excludeMarketTags` empty (can seed on stations).
- Nuance: on diplomacy-event success path, rep magnitude comes from config (0.04–0.10), caller's `repPercent` honored only in fallback.

---

## Recommended fix order

1. **Commit the verified PRD 013–023 work** (checkpoint before anything else). Mark PRDs 013–023 complete.
2. **B: exploits** — affordability pre-check; tribute recurring payment or catalog removal; wetwork execute-or-refund; intel real payload or price cut; territory offer-side fix. *(interactive: design decisions)*
3. **C: AI tuning** — wire peace scope bonus, add war cooldown + ally hard-block, widen buildThreat.
4. **D: save integrity (PRD-024)** — legacy day re-base on load (or declare save-break), static resets, transient scripts.
5. **E: dead code (PRD-025)** — deletions above + strategic/* decision + worktree prune.
6. **F:** wire coalition membership votes; document the rest as accepted deferrals.
