# PRD: Save Integrity and Correctness

**Slug:** save-integrity-and-correctness
**Created:** 2026-05-31
**Severity:** P1/P2 mix (singleton-lock = P1; static caches + logic flaws = P2)
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every static cache clears on `onGameLoad` so a second save loaded in the same application session
sees a clean state. The negotiation singleton lock never leaks. Memory no-decay, casus-belli
triggers, force-action costs, and influence cycle catch-up all behave correctly by construction,
not by naming convention.

## Why now

- Players who return to the main menu and load a different save (without restarting the
  application) carry faction beliefs, leader configs, and power rankings from session A into
  session B. This produces silent data corruption that is hard to reproduce and impossible to
  diagnose in the field.
- The `AIProposalIntel` counter-propose path leaks `NegotiationPanel.activeInstance` on any
  `popUpDialog` throw, permanently locking out all future negotiation panels until reload. This
  is the only path that bypasses `openScaled`'s guard and is thus a P1 regression.
- Remaining flaws (memory decay string-prefix, territorial/ideological CB over-triggering,
  coalition quorum bypass, force-action cost undercharge, influence cycle skip) are correctness
  defects that compound over long sessions.

## Success criteria

- Load save A, then return to menu and load save B (same application session): beliefs, leader
  configs, and power rankings for factions absent from save B are empty/reloaded from scratch.
- Counter-propose after a `popUpDialog` error (simulated by a debug toggle) allows a new
  negotiation panel to open — `activeInstance` is null after the failed attempt.
- A faction with trait IRREDENTIST and memory id `"border_dispute_hegemony"` (no `"territorial"`
  prefix) still receives the no-decay multiplier.
- Hegemony has a TERRITORIAL belief strength 2+. Pirates own markets. Hegemony vs. Pirates
  territorial CB fires only when Hegemony has a market-specific claim on a market Pirates
  hold, not simply because Pirates own any market.
- Ideological conflict CB fires only when holder and target hold opposing beliefs in the same
  category (e.g. LUDDIC vs TECH_PROGRESSIVE), not merely when holder holds a belief target
  doesn't share.
- A force action spending 100 pressure against a threshold of 60 leaves 40 pressure on the
  ledger, not 40 left (threshold-spend) and 40 phantom (extra).
- `InfluenceManager.advanceDay(65f)` with `CYCLE_DAYS=30` and `daysAccumulated=0` triggers
  exactly 2 cycles, not 1.
- Build green (`./build.sh`).

## Scope

**In:**
- `FactionBeliefsLoader`: clear `factionBeliefs` in `onGameLoad` before `loadForLiveFactions`.
- `LeaderConfigRegistry`: clear `cache` and reset `defaults` on game load (add `invalidate()`
  called from `Nex4xModPlugin.onGameLoad`).
- `FactionPowerRankings`: add `invalidate()` call to `onGameLoad` in addition to `beforeGameSave`
  to prevent cross-session pollution on load.
- `AIProposalIntel.buttonPressConfirmed` (~line 252) and `optionSelected` (~line 312): replace
  `new NegotiationPanel(...) + popUpDialog(...)` with `NegotiationPanel.openScaled(factionId, false)`
  overload that accepts a pre-filled `DealPackage` (add new overload to `openScaled`), OR wrap
  existing direct call in try/catch that sets `activeInstance = null` on throw.
- `MemoryManager.calculateTraitDecayModifier` (~line 82): replace `startsWith("territorial")`
  and `startsWith("military")` with `MemoryTypeDef.category` enum check
  (`MemoryTypeRegistry.get(memoryTypeId).category == MemoryTypeDef.Category.TERRITORIAL`).
- `CasusBelliManager.hasTerritorialClaim` (~line 229): gate on whether holder has a specific
  market-claim belief entry that references a market the target owns (use `BeliefEntry.targetMarketId`
  or equivalent field; if not yet present, add the field and migrate JSON). Until market-specific
  matching is implemented, restrict to factions that have `claims_domain_territory` or similar
  ids explicitly scoped, rather than the blanket TERRITORIAL category.
- `CasusBelliManager.hasIdeologicalConflict` (~line 248): replace the asymmetric non-shared-belief
  check with a mutual-opposition check: both factions must hold beliefs in the same IDEOLOGICAL
  sub-category that are semantically opposed (e.g. both have a belief tagged `luddism` but on
  opposite sides). Scoped approach: add an `opposedTo` list on `BeliefDef` (or use a new
  `IdeologicalOppositionRegistry`); fall back to current check only when no opposition table exists.
- `CoalitionVote.resolve(String)` (~line 78): change back-compat overload to call
  `resolve(blocLeaderId, actualMemberCount)` — callers must supply count. Deprecate the no-count
  overload and audit all call sites.
- `ForceAction.execute` (~line 38): change `pm.spend(fromFaction, toFaction, type.pressureThreshold)`
  to `pm.spend(fromFaction, toFaction, pressure)` (spend the full accumulated pressure, not just the
  threshold floor).
- `InfluenceManager.advanceDay(float)` (~line 66): change `if (daysAccumulated >= CYCLE_DAYS)` to
  `while (daysAccumulated >= CYCLE_DAYS)` so multi-cycle time jumps are fully caught up.

**Out:**
- PRD-022 (quorum overhaul) does not exist as a named PRD; however, the CoalitionVote quorum
  fix (24g) is scoped narrowly to the back-compat overload and its callers — a broader quorum
  policy redesign is deferred.
- Market-id matching on territorial beliefs: the full implementation (tracking specific
  market IDs on BeliefEntry, JSON schema migration) is descoped to a follow-up PRD. Task 24e
  delivers a "restricted" fix that stops false-positive triggers without the full data model.
- Ideological opposition table (`BeliefDef.opposedTo` JSON schema): full data authoring deferred;
  task 24f delivers the code hook and a minimal fallback that prevents the most common mislabeling.

## Risks

- `FactionBeliefsLoader.load()` called at `onApplicationLoad` populates vanilla entries; clearing
  on `onGameLoad` would remove those entries, requiring a re-load of vanilla beliefs from file.
  Mitigation: `invalidate()` clears only the map, then immediately calls `load()` (vanilla reload)
  before `loadForLiveFactions()`.
- `LeaderConfigRegistry.defaults` is loaded at app-start; clearing on game load requires a `load()`
  re-call. Wrap in try/catch; if JSON reload fails, keep existing defaults.
- `NegotiationPanel.openScaled` overload accepting `DealPackage` must set `activeInstance = this`
  inside the try block (same as existing ctor path) and clear on catch. Verify ctor ordering.
- Territorial CB "restricted" fix requires knowing which belief IDs imply a specific claim vs. a
  general disposition. Use the `DEFAULT_SECRET_IF_VISIBILITY_OMITTED` list as a guide; document
  the deferred full fix.

## Acceptance

- Build green: `./build.sh`.
- Runcode smoke A (cross-session cache): `auditCacheIntegrity()` in `Nex4xDebugCommand` — prints
  belief count, leader cache size, and power-rankings cache size before and after a simulated
  `onGameLoad` call. All three must be 0 (empty) immediately after the invalidate sequence runs
  and before `loadForLiveFactions`.
- Runcode smoke B (singleton lock): `auditCounterPropose()` — sets a debug flag that causes the
  next `popUpDialog` call to throw, triggers a counter-propose, asserts `activeInstance == null`
  afterward.
- Runcode smoke C (influence cycles): `auditInfluenceCycles()` — calls `advanceDay(65f)` on a
  fresh `InfluenceManager`, asserts `cyclesRun == 2`.
- Manual: load two different saves in one session; confirm factions in save B have correct
  (freshly-loaded) beliefs and leader configs.

## Tasks

See `tasks.yaml`.
