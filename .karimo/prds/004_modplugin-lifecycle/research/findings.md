# Research Findings — ModPlugin Lifecycle

## Source
Audit `THEME-D`, 2026-05-10.

## Problem
`Nex4xModPlugin` (extends `BaseModPlugin`) overrides only:
- `onApplicationLoad`
- `onGameLoad`
- `onNewGameAfterTimePass`

Missing hooks (per `BaseModPlugin` API):
- `onNewGame` — fires before economy/sector finalized.
- `onNewGameAfterEconomyLoad` — economy ready; **canonical place to seed faction state**. Without this, new-campaign procgen scripts that touch `Nex4xManager` see uninitialised state and NPE.
- `beforeGameSave` — last chance to clear transient state from persisted managers.
- `afterGameSave` — restore transient state.
- `onGameSaveFailed` — logging hook.

Plus: `util/FactionPowerRankings` keeps **class-static maps** that survive save reloads → cross-session pollution (first read after `onGameLoad` returns prior session's data).

`onNewGameAfterTimePass` has an 8-second wall-clock fallback that can apply a default doctrine before the player's first dialog — silent override.

## API verification
```
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.BaseModPlugin
  public void onApplicationLoad();
  public void onNewGame();
  public void onNewGameAfterEconomyLoad();
  public void onNewGameAfterTimePass();
  public void onGameLoad(boolean);
  public void beforeGameSave();
  public void afterGameSave();
  public void onGameSaveFailed();
  ...
```
Signature for `onGameLoad(boolean newGame)` — flag indicates if this is a fresh-on-new-game load. nex4x ignores this flag.

## Recommended approach
1. Add the 4 missing override stubs.
2. Move bootstrap currently in `onGameLoad` that should run on new-campaign init into `onNewGameAfterEconomyLoad`.
3. `onGameLoad(boolean newGame)`: if `newGame == true`, skip work that `onNewGameAfterEconomyLoad` already did.
4. `beforeGameSave`: clear transient caches in `FactionPowerRankings`, any `transient` fields in @Saved managers.
5. `afterGameSave`: re-seed transient caches.
6. `onGameSaveFailed`: log warn with stack.
7. Convert `FactionPowerRankings` static state to a per-Sector singleton stored on `Global.getSector().getPersistentData()` OR mark all caches transient and refill on demand. Static state is wrong by default.
8. Tighten `onNewGameAfterTimePass` so the 8s wall-clock fallback no-ops if the player is mid-dialog; or remove the timer (player will pick a doctrine when first dialog opens).

## Affected
- `src/nex4x/Nex4xModPlugin.java` (line refs: ~36 entrypoint, ~140 advance/teardown area, 286 fallback).
- `src/nex4x/util/FactionPowerRankings.java` (~line 16 fields).

## Risks
- Reordering bootstrap can change which managers see populated sector → keep an explicit init order constant; cover with smoke command that prints (manager, initialized?, populated?).
