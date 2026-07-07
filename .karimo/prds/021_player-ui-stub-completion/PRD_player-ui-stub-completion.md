# PRD: Player UI Stub Completion

**Slug:** player-ui-stub-completion
**Created:** 2026-05-31
**Severity:** P2 — feature-completion (advertised UI surfaces apply no real effects; no crash)
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every player-facing menu option in nex4x that currently dead-ends in flavor text or a
"(Phase 12)" stub must either apply a real game effect or be marked permanently deferred
with a locked-out UI state. After this PRD: the Viceroy services buy/grant real items and
commission status; the Peace Conference counter-offer editor lets the player edit terms, and
`accept()` applies every ratified term (TERRITORY_CEDE, REPARATIONS); the negotiation panel's
CONTRACTS/CONCESSIONS/WAR_DECLARATION rows either show real catalog items or are grayed out
with an honest tooltip instead of a misleading placeholder; and a TERRITORY deal execution
calls `exerelin.campaign.SectorManager.transferMarket()` rather than logging a no-op.

## Why now

- Players reaching the Viceroy dialog pay nothing, receive nothing — the entire "faction
  ambassador" feature loop is broken. Discovered in first public testing session.
- Peace conference "Counter Offer" re-submits identical terms, making it a UI trap: the
  accepting side thinks they modified terms but they did not.
- `PeaceConference.accept()` fires the peace *event* (via `NexDiplomacyBridge.firePeaceTreaty`)
  but silently drops any `TERRITORY_CEDE` or `REPARATIONS` terms in `this.proposed`.
- Territory deals are priced by `ItemValuator` at hundreds of thousands of credits. A player
  can pay and receive zero markets — a critical trust-breaking failure.
- CONTRACTS/CONCESSIONS/WAR_DECLARATION show "(not yet configurable in this version)" with no
  indication of whether to expect them; honest deferred state is better UX.

## Success criteria

1. Viceroy → "Purchase intelligence": selecting a tier deducts credits from
   `playerFleet.getCargo().getCredits()`, adds a matching intel entry via
   `IntelManagerAPI.addIntel()`. Player cannot purchase if insufficient credits.
2. Viceroy → "Discuss AI core transfer": purchasing an Alpha/Beta/Gamma core deducts
   credits and adds `CargoAPI.addSpecial(new SpecialItemData(coreItemId, null), 1)` to
   player cargo.
3. Viceroy → "Commission matters": if no active commission, dialog offers to grant one —
   creates `Nex_FactionCommissionIntel(faction)` and registers it via
   `IntelManagerAPI.addIntel()`; if commission already held with same faction, shows resign
   path calling `endMission(null)`; wrong faction shows resign + re-apply flow.
4. Viceroy → "Contract a wetwork job": target faction picker shown; selecting a target
   deducts wetwork fee from player credits and creates a timed `MemoryManager` contract
   entry ("wetwork_contract", giverFactionId, targetFactionId, duration); on discovery
   `WetworkHandler.onDiscovered()` already fires correctly (no change needed there).
5. Peace Conference "Counter Offer": opens a minimal term-removal editor (checkboxes or
   remove-buttons for each term in `pc.getProposed()`); player submits a non-identical
   `PeaceTerms`; `pc.counterOffer(editedTerms)` is called.
6. `PeaceConference.accept()`: after setting status to ACCEPTED, iterates `this.proposed`
   and applies each term: TERRITORY_CEDE via
   `SectorManager.transferMarket(market, giverFaction, receiverFaction, false, false, null, 0)`;
   REPARATIONS / WAR_REPARATIONS via `transferCredits` (already in `NegotiationDealExecutor`
   but not wired here); CEASEFIRE / peace event already fires via `NexDiplomacyBridge`.
7. `NegotiationDealExecutor.TERRITORY` case: replace log-only branch with real
   `SectorManager.transferMarket()` call (same signature as above).
8. `NegotiationPanel` CONTRACTS / CONCESSIONS / WAR_DECLARATION: return a stub label
   "(coming soon — not available in this build)" with the row grayed out
   (`Misc.getGrayColor()`), replacing the current misleading placeholder. No functional
   wiring for these three categories in this PRD.
9. Build green (`./build.sh`).
10. Smoke (runcode): viceroy intel purchase moves credits; territory deal transfers a
    market; peace accept with a TERRITORY_CEDE term calls `SectorManager.transferMarket`.

## Scope

**In:**
- `src/nex4x/ui/viceroy/IntelPurchaseHandler.java` — real credit deduction + intel item grant.
- `src/nex4x/ui/viceroy/AiCoreHandler.java` — real credit deduction + AI core to cargo.
- `src/nex4x/ui/viceroy/CommissionHandler.java` — real commission grant/resign via
  `Nex_FactionCommissionIntel` + `IntelManagerAPI`.
- `src/nex4x/ui/viceroy/WetworkHandler.java` — target faction picker + credit deduction +
  `MemoryManager` contract entry.
- `src/nex4x/ui/ViceroyDialog.java` — add option-loop for submenus that need selection
  (AI core tier, intel tier, wetwork target); currently `open()` just prints and returns
  to main menu — needs a second option pass for these three handlers.
- `src/nex4x/ui/PeaceConferenceDialog.java` lines 88–92 — Counter Offer opens a real
  term-editing sub-dialog; submits edited `PeaceTerms`.
- `src/nex4x/peace/PeaceConference.java` `accept()` — apply each term in `this.proposed`
  after status flip.
- `src/nex4x/negotiation/NegotiationDealExecutor.java` line 69–75 — TERRITORY case calls
  `SectorManager.transferMarket()`.
- `src/nex4x/ui/NegotiationPanel.java` lines 526–529 — CONTRACTS/CONCESSIONS/WAR_DECLARATION
  placeholder text updated to honest "coming soon" + gray color.
- `src/nex4x/debug/Nex4xDebugCommand.java` — new `smokeStubs` runcode command.

**Out (deferred, separate PRDs):**
- Full quest-board UI for `QuestsHandler` — requires mission-board pipe-through; future PRD.
- CONTRACTS category wiring (mercenary/arms contracts) — requires contract lifecycle
  system; future PRD.
- CONCESSIONS category wiring (third-party embargo/alignment shifts) — requires structured
  concession catalog; future PRD.
- WAR_DECLARATION category wiring in `NegotiationPanel` — requires target-picker and war
  validation; future PRD.
- Wetwork contract resolution / bounty payment flow — contracts created here; resolution
  events shipped in a separate wetwork PRD.
- PRD 020 data plumbing for KNOWLEDGE / PRISONERS / request-buttons — do not duplicate.
- Full term-negotiation editor with quantity spinners for REPARATIONS / TRIBUTE amounts
  in the peace counter-offer UI — phase 2 of peace UI; this PRD ships minimum viable
  term-removal only.
- NPC-vs-NPC peace term application (reparations between AI factions) — current
  `transferCredits` already skips NPC-vs-NPC; no change required here.

## Risks

- **`SectorManager.transferMarket` modifies save state** and fires all Nex capture events
  (stabilization, market post changes, submarket updates). Must guard: verify market
  exists and is owned by the giver before calling; wrap in try/catch; log outcome.
  Mitigation: add pre-condition guard in `executeItem` and in `PeaceConference.accept()`.
- **Commission double-grant**: `addIntel` of a second `Nex_FactionCommissionIntel` for
  the same faction while one is still active will create two concurrent commissions.
  Mitigation: `CommissionHandler.findActive()` already finds current intel; gate grant on
  `findActive() == null`.
- **Viceroy sub-option state**: `ViceroyDialog` currently calls handler then immediately
  returns to menu via `showMenu()`. AI core / intel / wetwork handlers need a second
  option pass (tier or target selection) before acting. Adding sub-menu state to the
  stateless `InteractionDialogPlugin` requires either a simple `phase` field or a second
  `open()` path that records selected tier in the handler before deducting.
- **`Nex_FactionCommissionIntel` visibility**: constructor takes a `FactionAPI`; the intel
  must also call `missionAccepted()` after `addIntel()` so stipend/hostility tracking
  begins. Miss this and the commission is inert.

## Acceptance

- `./build.sh` exits 0.
- `smokeStubs` runcode: calls `IntelPurchaseHandler.open()` with a mock dialog for a
  hegemony market; verifies credits reduced by `PRICE_LOCATION_TIP`; verifies
  `IntelManagerAPI.getIntel(IntelInfoPlugin.class).size()` increased by 1.
- `smokeStubs`: builds a TERRITORY deal with a real market id; calls
  `NegotiationDealExecutor.executeDeal()`; verifies market faction changed.
- `smokeStubs`: creates a `PeaceConference` with a `TERRITORY_CEDE` term; calls `accept()`;
  verifies market faction changed and status == ACCEPTED.

## Tasks

See `tasks.yaml`.
