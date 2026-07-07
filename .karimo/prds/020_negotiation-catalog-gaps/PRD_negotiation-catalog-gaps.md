# PRD: Negotiation Catalog Gaps

**Slug:** negotiation-catalog-gaps
**Created:** 2026-05-31
**Severity:** P1
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every item category that the negotiation catalog advertises to the player — Knowledge Share,
Prisoner Exchange, and Territory — actually works end-to-end: clicking a category button builds
a valid `NegotiableItem`, territory lists show real faction-owned markets, and the player can
both OFFER and REQUEST items through the catalog UI. No silent no-ops, no dead buttons.

## Why now

- Knowledge Share (`ID_KNOWLEDGE`) and Prisoner Exchange (`ID_PRISONER`) are listed in
  `getAvailableIds` and in `idsForType(KNOWLEDGE)` / `idsForType(PRISONERS)`, but `build()`
  has no branch for either. Every click into those categories silently warn-logs and no-ops
  (`NegotiationPanel.java:300`). Two fully-designed item types are completely inaccessible.
- The territory loop in `idsForType(TERRITORY)` at `NegotiableItemCatalog.java:160` checks
  `if (count >= 5) break` **before** the `isHidden()` and faction-ownership filters at lines
  161-163. On any sector with five hidden or player-owned markets at the head of the economy
  list, the result set is empty or wrong — owned markets never appear for cession offers.
- `addCatalogItemsThreeZone` (`NegotiationPanel.java:520-548`) renders a single button per
  catalog id routed to `deal.addOffer(...)` only, despite the header reading
  "ADD TO OFFER … ADD TO REQUESTS →". The player has no way to REQUEST a commodity, territory,
  intel, knowledge share, or prisoner exchange from the AI. The UI surface implies parity;
  the implementation provides none.

All three bugs are regressions against the advertised feature surface and together make the
negotiation screen misleading. They are independently fixable with low risk.

## Success criteria

1. `catalog.build("knowledge_share", 1)` returns a non-null `NegotiableItem`; the Knowledge
   category button adds the item to the active deal.
2. `catalog.build("prisoner_exchange", 1)` returns a non-null `NegotiableItem`; the Prisoner
   category button adds the item to the active deal.
3. `idsForType(TERRITORY, ...)` with a target faction that owns 3 markets in a sector also
   containing 10 hidden markets returns exactly those 3 markets (not 0 or a mix of hidden ones).
4. `addCatalogItemsThreeZone` renders both an Offer button and a Request button per catalog id
   for items where requesting is logically valid (all non-declaration items).
5. Clicking a Request catalog button calls `deal.addRequest(item)` and the item appears in
   the "YOUR REQUESTS" panel.
6. Build green (`./build.sh`).

## Scope

### In
- `NegotiableItemCatalog.build()` — add `else if` branches for `ID_KNOWLEDGE` and
  `ID_PRISONER` at `NegotiableItemCatalog.java:32-68`.
- `NegotiableItemCatalog.idsForType(TERRITORY)` — move `count >= 5` cap to after the
  `isHidden()` and faction-ownership guards at `NegotiableItemCatalog.java:156-167`.
- `NegotiationPanel.addCatalogItemsThreeZone()` — add a second `NEX4X_REQUEST_PREFIX`
  button per item alongside the existing offer button; add `NEX4X_REQUEST_PREFIX` constant
  and corresponding `buttonPressed` dispatch branch at `NegotiationPanel.java:285-304`.
- `NegotiationPanel.buttonToCatalogId` map — extend to cover request-side button ids
  (may use a second map or a naming convention on the same map).

### Out
- CONTRACTS, CONCESSIONS, WAR_DECLARATION categories are explicitly deferred in
  `idsForType` (`NegotiableItemCatalog.java:197-200`) and return empty; their "(not yet
  configurable)" placeholder is correct and intentional. Do not add build branches for them here.
- Execution of accepted deals: territory transfer, prisoner release, knowledge handoff — those
  are execution stubs handled in **PRD-021 (player-UI-stubs)**. This PRD only ensures the
  catalog data pipeline works so items can reach the deal object.
- `NegotiableItem.knowledge()` / `NegotiableItem.prisoner()` factory internals: they already
  exist (confirmed by `estimatedUnitValue` entries at lines 93-94 and `getDisplayName` at
  lines 214-215). If a factory method is missing, adding it is a sub-task of 20a.

## Risks

- `NegotiableItem.knowledge()` and `NegotiableItem.prisoner()` factory methods may not exist
  (only `estimatedUnitValue` and `getDisplayName` confirm the ids are known, not that factories
  exist). Task 20a must verify and add factories if absent before wiring `build()`.
- Adding a second button per row in `addCatalogItemsThreeZone` changes panel height
  calculations. If the catalog panel height is hardcoded upstream, items may overflow.
  Task 20c should verify the scrollable container wraps the zone correctly.
- `buttonToCatalogId` is a single map; if offer and request buttons share the same encoded
  key, dispatch will be ambiguous. Use distinct prefixes (`nex4x_add_` vs `nex4x_req_`) as
  separate constants with separate map lookups or a unified map with prefix-encoded keys.

## Acceptance

- `./build.sh` exits 0.
- Runcode smoke (via `Nex4xDebugCommand`): open negotiation panel against any faction;
  expand Knowledge → click Add to Offer → item appears in YOUR OFFER; expand Prisoners →
  click Add to Offer → item appears. Territory section lists markets owned by target faction.
  For any commodity, clicking "Request →" moves the item to YOUR REQUESTS.
- No warn-log line containing "catalog.build returned null" fires during the above smoke.

## Tasks

See `tasks.yaml`.
