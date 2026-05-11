# PRD: Player UI Gaps

**Slug:** player-ui-gaps
**Created:** 2026-05-11
**Severity:** P2 (mechanics shipped but invisible to player)
**Source:** Post-PRD-011 audit

## Vision
Mechanics added in PRDs 006–011 (expanded item catalog, peace state machine, mediation)
are reachable from the negotiation panel and minimal dialogs, not just console runcode.

## Success criteria
- Expanding a negotiation category surfaces concrete add-buttons for each catalog ID
  in that category (CREDITS, TRIBUTE, PEACE_TERMS, INTEL, KNOWLEDGE, PRISONERS,
  AGREEMENTS, DECLARATIONS, COMMODITIES at minimum).
- Defender of a `PeaceConference` can Accept / Reject / Counter-offer via dialog.
- `Nex4xDebugCommand.proposeMediation(...)` exposes a runcode entrypoint with sensible defaults.
- Build green; no regressions to negotiation panel singleton/defer behavior.

## Scope
**In:**
- `NegotiableItemCatalog`: add `ID_WAR_DECLARATION`, `PREFIX_WAR_TARGET` helpers.
- `NegotiationPanel.addCatalogItemsThreeZone`: wire catalog IDs as add-buttons per category.
- New `src/nex4x/ui/PeaceConferenceDialog.java` — minimal dialog (Accept/Reject/Counter-offer
  with pre-baked counter terms = "drop war reparations" as v1).
- `Nex4xDebugCommand.proposeMediation` runcode entrypoint.

**Out (deferred to PRD-013):**
- ContractAuctionManager UI
- Concessions third-party picker
- Mediation full dialog
- TERRITORY / WAR_DECLARATION concrete target pickers

## Risks
- Negotiation panel layout already complex; risk of breaking three-zone grid.
  Add buttons defensively; keep deferred categories no-op.
- Counter-offer creates a second BasePopUpDialog while parent NegotiationPanel
  may still be open. Test singleton.

## Acceptance
- `./build.sh` clean
- Manual: open NegotiationPanel → expand each category → confirm buttons appear
- Manual: trigger PeaceConference via console, run dialog, accept/reject/counter

## Tasks
See `tasks.yaml` (4 tasks).
