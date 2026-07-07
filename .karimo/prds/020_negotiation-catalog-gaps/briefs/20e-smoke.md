# Smoke Verification Steps — PRD-020 (20e)

## Prerequisites

- Deploy the mod: `./build.sh` then copy `jars/Nex4xExpansion.jar` to your Starsector mods folder
  (or run via the game symlink: `game/starsector.sh` / `game/starsector.exe`).
- Start or load a save with at least one non-hostile faction that owns visible markets.

## Check 1 — Knowledge Share catalog add

1. Open a negotiation with any faction (in-game campaign → faction menu → Negotiate, or
   via `Nex4xDebugCommand` runcode: `NegotiationPanel.openScaled("hegemony", false)`).
2. In the AVAILABLE ITEMS catalog, find the **KNOWLEDGE** category row. Click it to expand.
3. Click **[+ Offer] Technology Share** button.
4. Verify: "Technology Share" (or similar) appears in YOUR OFFER column.
5. Verify: NO warn-log line "catalog.build returned null" in `starsector.log`.

## Check 2 — Prisoner Exchange catalog add

1. Same panel, find the **PRISONERS** category, expand it.
2. Click **[+ Offer] Prisoner Exchange**.
3. Verify: item appears in YOUR OFFER. No null-build warn-log.

## Check 3 — Territory list correctness

1. Open a negotiation with a faction that owns 3+ visible markets (e.g. Hegemony).
2. Expand the **TERRITORY** category.
3. Verify: every listed market is a market owned by that faction (not hidden, not player-owned).
4. Verify: if that faction owns fewer than 5 markets only those markets appear (not hidden/unowned ones).

## Check 4 — Request button for commodities

1. In the same negotiation panel, expand **COMMODITIES**.
2. Click **[Request +] Commodity: supplies** (right-side button).
3. Verify: "supplies" item (or "500 x supplies") appears in YOUR REQUESTS column.
4. Verify: the item has a [✕] remove button in the requests column.
5. Verify: no warn-log "catalog.build returned null for req id".

## Runcode shortcut (via Nex4xDebugCommand)

If the debug command supports runcode, open the in-game console and type:

```
runcode nex4x.ui.NegotiationPanel.openScaled("hegemony", false)
```

Then perform all four checks above.

## Pass criteria

All four checks pass with no new error/warn logs from the negotiation system.
