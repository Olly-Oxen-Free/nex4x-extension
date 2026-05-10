# Research Findings — P2/P3 Cleanup Batch

## Source
Audit `audit/AUDIT_REPORT.md` — every P2 and P3 finding not promoted into PRDs A-F. 2026-05-10.

## Triage groups

### Group 1 — Null guards / chain safety (P2)
- `agreements/AgreementManager.java:119-121` — chained Global.getSector().getFaction(...).getRelationship(...). (Also covered by PRD-A 1h; only do here if A skipped.)
- `ai/goals/FeasibilityChecker.java:60-61` — same chain pattern, no `us` null guard.
- `agents/Nex4xAgentData.java:53` — config NPE in startRetrain (sister advanceDay guards correctly; mirror it).
- `coalitions/CoalitionGovernance.java:60` — `m.getFactionId().equals(fid)` NPE; invert.
- `casusbelli/CasusBelliManager.java:257, 271` — sub-managers (declMgr, agrMgr) not null-checked.
- `leaders/LeaderRegistry.java:60` — `factionId.hashCode()` NPE on null id.
- `leaders/LeaderProfile.java:27` — stale transient `resolved` cache; invalidate on LeaderChangeEvent.
- `leaders/LeaderAccessGate.java:51` — blind cast in getIntel filter loop; defensive instanceof.
- `declarations/Declaration.java:39` — ctor doesn't reject null declarer/target ids.

### Group 2 — Logic edge cases (P2)
- `coalitions/CoalitionVote.java:36` — no quorum, tie loses, no expiry. Add timestamp + auto-discard after N days; reject zero-vote resolve().
- `coalitions/CoalitionGovernance.java:24` — getTension is hidden write (lazy create on read); split to findTension (nullable) + getOrCreateTension.
- `casusbelli/CasusBelliManager.java:239` — IDEOLOGICAL_CONFLICT triggers on shared beliefs; compute set difference.
- `casusbelli/CasusBelli.java:72` — bespoke day calc; use Nex4xClock from PRD-B.
- `badges/FactionBadges.java:31` — progress counter never reset; reset on earn.
- `wargoals/WarScoreTracker.java:128` — score map leaks for eliminated factions; prune null FactionAPI.
- `wargoals/WarGoal.java:42` — no expired/obsolete state for TERRITORIAL_CLAIM on destroyed market.
- `peace/PeaceConference.java:35` — single concluded/accepted booleans; needs PROPOSED→COUNTER→FINAL state machine.
- `votes/VoteOutcome.java:35` — abstentions unmodeled; getPercentFor returns 0.5 for empty AND tie.
- `pressure/PressureManager.java:26` — accumulationPerDay parsed, never consumed; lazy config not retried.
- `vassals/VassalManager.java:70` — tribute uses `size*10000` instead of MarketAPI.getNetIncome().
- `industries/CyberSecuritySuite.java:15`, `DiplomaticEmbassy.java:14`, `IntelligenceBureau.java:14` — apply() is super.apply only; no demand/supply/income hooks.
- `data/TendencyProfileLoader.java:87` — getProfile caches auto-derived into same map as explicit; hasExplicitProfile becomes true after first lookup.
- `policies/PolicyEffect.java:43` — MISSIONARY_CAMPAIGN, CONSERVATION_MANDATE no case branches.
- `policies/PolicyManager.java:92` — advanceDay iterates entrySet while refresh callbacks could mutate (CME risk).

### Group 3 — Listener / lifecycle (P2)
- `listeners/Nex4xEventListener.java:65` — double-fire on PersonAPI rep change (override at line 76 delegates to String form).
- `mediation/MediationManager.java:31` — no input validation in propose() (zero-cost session auto-resolves).
- `influence/InfluenceManager.java:61` — tick-vs-day drift via IntervalUtil jitter.
- `integration/Nex4xDiplomacyBrainListener.java:14` — dead class (never instantiated). Delete.
- `managers/Nex4xManager.java:144` — v2 block all-or-nothing try/catch + missing elapsed forward.
- `data/FactionBeliefsLoader.java:54` — JSON errors swallowed as log.info; not surfaced.
- `declarations/DeclarationConfig.java:48`, `leaders/DialoguePool.java:66`, `leaders/LeaderAccessConfig.java:43` — swallowed exceptions; log with stack.

### Group 4 — UI minor (P2 / P3)
- `ui/FactionBrowserPanelModel.java:60` — Serializable selectedFactionId not validated; reset if faction missing.
- `ui/BalanceBarPlugin.java:48` — glBlendFunc not saved (PRD-E covers glColor; this is the blend func variant).
- `ui/DiplomacyTabOverlayModel.java:248` — scale convention undocumented; add Javadoc.
- `ui/NegotiationPanel.java:329, 334` — hardcoded balance-bar label colors; use Misc.getBasePlayerColor() and targetFaction.getBaseUIColor().
- `ui/DiplomacyIntel.java:45` — partly covered by PRD-E (sort-tier).

### Group 5 — P3 dead code / minor
- `agents/Nex4xAgentManager.java:76-78` — stub returns 0 unconditionally.
- `agents/Nex4xAgentActionProposer.java` — empty placeholder; delete or rename.
- `managers/MemoryManager.java:20` — dead lastAdvanceDay field still serialized.
- `managers/AIProposalManager.java:58` — `month*30f` should be `(month-1)*30f`.
- `influence/InfluenceManager.java:159` — getDeclarationDiscountFactor lacks null guard.
- `leaders/LeaderRegistry.java:35, 78` — hardcoded "factionLeader"; use Ranks.POST_FACTION_LEADER.
- `leaders/DialogueSystem.java:37` — hardcoded faction id strings; use Factions.* (cosmetic — values match).
- `data/Nex4xSettings.java:68` — static-field reload semantics undocumented.
- `data/TendencyProfile.java:48` — getDominant returns null for all-zero profiles.
- `pressure/PressureLedger.java:30` — comment says "highest first" but impl is uniform-proportional.
- `util/FactionPowerRankings.java:90` — getNetIncome try/catch is dead code (primitive returns).
- `casusbelli/CasusBelliManager.java:35` — static final Logger at class init couples to Global readiness.
- `strategic/concern/BeliefAlignmentConcern.java:59` — O(n*m) clashScore in O(n) loop.
- `debug/Nex4xDebugCommand.java:10` — no BaseCommand registration for Console Commands name dispatch.
- `agreements/Agreement.java:88-92` — covered by PRD-B.
- `coalitions/CoalitionVote.java`, `CoalitionGovernance.java` — covered above.
- `leaders/IntelTierResolver` — covered by PRD-F.
- `declarations/DeclarationType.java:45` — float vs int duration drift.
- `contracts/Contract.java:21` — getBids() exposes internal list; unmodifiableList.

## Approach
- Group these into logical task batches; each batch is one task in tasks.yaml so the implementer keeps related context.
- Defer behind PRDs A-F. Many of these become trivial after the systemic fixes land.
- Use this PRD as a "post-systemic cleanup sprint".
