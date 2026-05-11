# Research Findings — Player UI Gaps

## Source
Post-PRD-011 audit. New mechanics shipped but not exposed in player UI.

## Gaps

### Gap 1: NegotiableItemCatalog new IDs not visible in negotiation panel
PRD-008 expanded `NegotiableItemCatalog` with explicit IDs:
- `credits`, `tribute`, `ceasefire`, `peace_treaty`, `war_reparations`
- `intel_basic`, `intel_deep`, `prisoner_exchange`, `knowledge_share`
- prefixed: `commodity:<id>`, `agreement:<type>`, `declaration:<type>`, `territory:<marketId>`

The panel iterates `NegotiableItemType` (12 categories) but `addCatalogItemsThreeZone`
doesn't wire `catalog.getAvailableIds(atWar)` per-category. Player can expand a
category and see headers but no add-buttons for the new IDs.

### Gap 2: PeaceConference COUNTER_OFFERED state has no UI
`PeaceConference` (PRD-007) has full state machine:
PROPOSED → COUNTER_OFFERED ↔ → ACCEPTED|REJECTED|EXPIRED.
But there's no dialog that lets the defender propose a counter or the attacker
accept/reject one. State machine exists in code only.

### Gap 3: MediationManager has no player UI
`MediationManager.propose(mediator, A, B, influence)` is callable only via runcode.
Players can't see active mediations or propose new ones.

## Approach

### Gap 1 — Catalog wiring
Modify `NegotiationPanel.addCatalogItemsThreeZone` to:
1. Build a per-`NegotiableItemType` → list-of-catalog-ids map.
2. For each expanded category row, iterate that type's catalog ids; each becomes
   a clickable add-button with `catalog.getDisplayName(id)` label and
   `catalog.estimatedUnitValue(id, ...)` for value display.

Mapping (NegotiableItemType → catalog-id-prefix or set):
- CREDITS → `[ID_CREDITS]`
- TRIBUTE → `[ID_TRIBUTE]`
- COMMODITIES → walk `Global.getSettings().getAllCommoditySpecs()` → `commodity:<id>`
- TERRITORY → walk markets of target faction → `territory:<marketId>`
- AGREEMENTS → walk `AgreementType.values()` → `agreement:<type>`
- WAR_DECLARATION → faction picker (target faction id) → `war:<factionId>` (catalog
  doesn't have this id yet; add `ID_WAR_DECLARATION` and a `PREFIX_WAR_TARGET`)
- PEACE_TERMS → `[ID_CEASEFIRE, ID_PEACE_TREATY, ID_WAR_REPARATIONS]`
- KNOWLEDGE → `[ID_KNOWLEDGE]`
- INTEL → `[ID_INTEL_BASIC, ID_INTEL_DEEP]`
- CONTRACTS → defer (need ContractAuctionManager picker)
- CONCESSIONS → defer (need third-party picker)
- PRISONERS → `[ID_PRISONER]`
- DECLARATIONS → walk `DeclarationType.values()` → `declaration:<type>`

For categories needing more pickers (TERRITORY, WAR_DECLARATION, CONCESSIONS),
deferral is acceptable — they get the header but no concrete add-buttons in v1.

### Gap 2 — Counter-offer dialog
Add `PeaceConferenceDialog` (new file) using same `BasePopUpDialog` pattern as
`NegotiationPanel`. Show current proposed terms; defender side has "Accept",
"Reject", "Counter-offer" buttons. Counter-offer opens a smaller terms editor.

For v1: simplest possible — buttons that call `PeaceConference.accept/reject/counterOffer`
with the existing terms; full term-editing deferred.

### Gap 3 — Mediation
Defer to PRD-013. Real UI requires faction-pair picker + influence cost slider.
For v1: console-only via `Nex4xDebugCommand.proposeMediation(mediator, a, b, inf)`.

## Risks
- The negotiation panel UI is fragile (recent v5 churn around defer patterns).
  Add catalog buttons in a controlled way; don't refactor the layout.
- Counter-offer dialog adds a second BasePopUpDialog while NegotiationPanel may
  still be open. Test singleton interaction.

## Out of scope
- ContractAuctionManager UI
- Concessions third-party picker
- Mediation full UI (PRD-013 if needed)
