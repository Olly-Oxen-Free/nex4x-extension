# Changelog

All notable changes to nex4x-extension.

## [Unreleased] — 2026-07-07

### Changed — SAVE-BREAK

- **Day-math epoch changed** to Starsector's real 360-day calendar (12 months
  × 30 days per cycle). Time and day-index math now derive from
  `CampaignClockAPI` cycle/month/day instead of a raw timestamp epoch.
  **Saves created before this version are NOT compatible — start a new game.**
  No migration is provided; the legacy raw-timestamp heuristic
  (`Nex4xClock.isLegacyDayValue`) has been removed.

### Fixed — save integrity (PRD-024)

- Reset transient static caches on game load: `CoreUITabInjectorListener`
  overlay refs, `PeaceConferenceDialog`/`NegotiationPanel` singletons, and
  `FactionPowerRankings` denom cache — prevents cross-session pollution.
- `AIProposalIntel` popup driver switched to a transient script to avoid
  serializing UI drivers into saves.

## [Unreleased] — 2026-05-11

Full code-piping audit (110 findings) + complete Nexerelin integration
campaign delivered via 12 KARIMO PRDs.

### Added — Nex integration layer

- **NexDiplomacyBridge** (`nex4x.integration.NexDiplomacyBridge`) — 12 static
  helpers wrapping every Nex API call with try/catch + fallback so version
  drift can't crash nex4x.
- **AgentTypeMap** — bidirectional mapping between `nex4x.agents.AgentType`
  and Nex `AgentIntel.Specialization` (NEGOTIATOR/HYBRID/SABOTEUR).
- **Nex4xCovertActionRegistry** — registers 7 nex4x covert actions
  (DeepCover, DiplomaticSupport, UnofficialLeak, BuildNetwork, FalseFlag,
  HireMercenaries, InciteRaid) with `CovertOpsManager.actionDefsById`.
- **Nex4xStrategicAIConcerns** — registers `BeliefAlignmentConcern`,
  `GoalWrapConcern`, and `ProposeTieredAgreementAction` with
  `StrategicDefManager`.

### Added — Player UI

- **PeaceConferenceDialog** — minimal accept / reject / counter-offer dialog
  for war resolution.
- **NegotiationPanel** catalog rows now render concrete add-buttons per
  category (`NegotiableItemCatalog.idsForType(type, atWar, targetFid)`).
- 7 new `runcode` smoke commands in `Nex4xDebugCommand`: `auditRels`,
  `auditClock`, `auditInit`, `auditAgents`, `auditDiplomacy`, `auditNexSync`,
  `auditTribute`, `auditUI`, `proposeMediation`, `openPeaceConference`.

### Added — Helpers

- **Nex4xRelations** — single conversion point between Nex's raw -1..1
  relation and nex4x's percent (-100..100) scale.
- **Nex4xClock** — game-time helpers (`now`, `daysSince`, `currentAbsoluteDay`).

### Added — State machines + features

- `PeaceConference` full state machine: PROPOSED → COUNTER_OFFERED ↔ →
  ACCEPTED | REJECTED | EXPIRED.
- `VoteOutcome` abstain support; tie/empty distinguishable.
- `CoalitionVote` quorum (50%) + 14-day expiry.
- `BadgeType.positive` field — replaces hardcoded polarity.
- `WarGoal.markObsolete()` — territorial claims auto-obsolete on
  market destruction.
- `Nex4xModPlugin` full lifecycle: `onNewGame`,
  `onNewGameAfterEconomyLoad`, `beforeGameSave`, `afterGameSave`,
  `onGameSaveFailed` all overridden.
- `FactionPowerRankings.invalidate()` — clear cache on save to prevent
  cross-session pollution.
- `NegotiableItemCatalog` full ID surface (credits, tribute, ceasefire,
  peace_treaty, war_reparations, intel_basic/deep, prisoner_exchange,
  knowledge_share, plus prefixed commodity/agreement/declaration/territory).
- Persistent **TributeCondition** application for TRIBUTE_CREDITS demands
  and vassal markets.

### Changed — Nex integration routing

- `DeclarationManager.declareFriendship` → Nex "respect" event
- `DeclarationManager.declareDenouncement` → Nex "insult" event + per-target ripple
- `AgreementManager.createAgreement(.., COALITION)` → real Nex Alliance via
  `AllianceManager.createAlliance` / multi-member `joinAlliance`
- `AgreementManager.createAgreement(.., NAP/DefPact/Mil/Econ)` → enforces
  relation floor; daily reapply
- `MediationManager` SUCCESS → Nex `peace_treaty` event
- `PeaceConference.accept()` → Nex `peace_treaty` event
- `DemandManager.applyDemandEffect` END_WAR / BREAK_ALLIANCE / CEDE_MARKET /
  TRIBUTE_CREDITS → real Nex effects via bridge
- `DiplomaticExecutor` declare_war → `fireJustifiedWar` when CB present
  (reverts `MEM_KEY_BADBOY` delta)
- `Nex4xAgentActionReportListener` reads canonical AgentType from
  `AgentIntel.Specialization` instead of forcing COVERT
- `Nex4xManager.advance` periodically sweeps `DiplomacyBrain` instances

### Fixed — From audit (110 findings across 6 themes)

- **Relation scale**: 9 sites converting raw -1..1 ↔ percent -100..100
  through `Nex4xRelations`. Previously `ReputationTier` always returned
  HOSTILE; alliance proposals always failed `canPropose`.
- **Time units**: 8 sites migrated from `getTimestamp()`-as-days to
  `Nex4xClock.daysSince(long)`. Previously strategic AI archetypes
  expired in 60 seconds instead of 60 days.
- **Listener save integrity**: 3 bridges (Nex4xInvasionBridge,
  Nex4xRaidBridge, Nex4xAgentActionReportListener) re-registered as
  transient on every `onGameLoad`. Previously risked save corruption.
- **UI correctness**: NegotiationPanel singleton race (try/catch around
  popUpDialog), overlay leak on Intel close (track and remove previous),
  class shadow in `DiplomacyTabOverlayModel` (use FQN), panel detachment
  in `advance()` (clear children directly), GL state leaks
  (glPushAttrib/glPopAttrib in BalanceBarPlugin + DiplomacyOverlayPlugin),
  double layout advance (remove redundant addSpacer), DiplomacyIntel
  sort hack (use `IntelSortTier.TIER_1`), render log spam (debug level).
- **Diplomacy logic**: bilateral 2x delta in DeclarationManager
  (engine already mirrors), type-guard bypass in declare(), friendship/
  denounce dedupe, daily-decay elapsed-day scaling. Demand validation +
  effect dispatch. Owner-faction tracking on Nex4xAgentData prevents
  cascade bleed across factions. CasusBelli military-industry filter
  (Containment CB no longer triggers on raw colony count). TrustSensitivity
  null guard. FactionCompatibility uses real NexFactionConfig fields.
  MemoryManager IRREDENTIST/FOREVERWAR multipliers fixed (was zero-decay
  → instant decay; now extends memory). PoliticalModifier sign-aware decay.
  ProposeTieredAgreementAction creates actual agreement.
- ~50 P2/P3 cleanup items (null guards, dead code removal, defensive
  patterns, log demotions).

### Removed

- `Nex4xAgentActionProposer` (empty placeholder; Nex's `CovertOpsManager`
  owns action proposal).
- `nex4x.agents.actions.SabotageIndustryAction` (duplicate of Nex's
  `exerelin.campaign.ai.action.covert.SabotageIndustryAction`).
- `BaseAgentAction` + `ActionConfig` + `ActionConfigLoader` (replaced by
  Nex `CovertActionIntel` subclasses).

### Documentation

- Audit report (`audit/AUDIT_REPORT.md`) — 110 findings categorized
  by severity + theme.
- 12 KARIMO PRDs (`.karimo/prds/001..012/`) — research findings,
  PRD doc, tasks.yaml, execution_plan.yaml, status.json for each
  delivery.
- Obsidian knowledge base (`brain/wiki/`) — 7 thematic articles
  covering Diplomacy Core, Strategic AI, Agents and Covert Ops,
  Conflict System, Faction State, Nex Integration Layer, Player UI
  Surface.
- This `CHANGELOG.md`.

---

## [Previous: feat/v5-leader-dialogue] — pre-2026-05-10

v5 leader dialogue branch baseline. Leader profiles, dialogue system,
NegotiationPanel scaffolding, FactionBrowserIntel.
