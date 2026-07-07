# Research Findings — UI Correctness Fixes

## Source
Audit cluster 5 (negotiation/ui), 2026-05-10. Standalone P0/P1 issues NOT covered by THEME-A/B/D.

## Issues

### [P0] NegotiationPanel singleton race (NegotiationPanel.java:91)
`activeInstance = this` in ctor body. If `BasePopUpDialog.popUpDialog(...)` throws after assignment, `removeUI()` never fires; `activeInstance` stays non-null and locks out further negotiations until reload. Counter-proposal ctor chains via `this(...)` and inherits the hazard.

**Fix:** Wrap `popUpDialog` call in try/catch that nulls `activeInstance` on throw.

### [P0] Overlay leak in CoreUITabInjectorListener (CoreUITabInjectorListener.java:103)
`CoreUITabListener` has no symmetric close callback. Each Intel open adds a fresh overlay UIComponent to intelPanel; transient script removed via `removeTransientScriptsOfClass` but the overlay component itself never explicitly removed. Repeated open/close stacks overlays.

**Fix:** Track previous overlay; `intelPanel.removeComponent(prev)` before adding next.

### [P0] Class shadowing risk (DiplomacyTabOverlayModel.java:469)
File imports `exerelin.campaign.intel.diplomacy.DiplomacyIntel` but `nex4x.ui.DiplomacyIntel` exists in the same package — a future re-import silently swaps to the wrong class and `IntelManagerAPI.getIntel(Class)` returns zero events.

**Fix:** Use FQN at the call site to remove ambiguity.

### [P1] Detached panel rebuild (NegotiationPanel.java:276)
`advance()` does `removeUI(); createUI(panelToInfluence);` but Ashlib's `PopUpUI.removeUI()` clears children **and detaches via parentUIPanel**. `createUI` then renders to a detached panel → silent blank.

**Fix:** Mirror `CoreUITabInjectorListener.refresh` pattern: clear children of `panelToInfluence` directly via `IntelReflectionUtil.getChildrenNonCopy(...).clear()`, then re-call `createUI`.

### [P1] GL state leak — BalanceBarPlugin (BalanceBarPlugin.java:71)
Last `glColor4f` is `(0.55,0.55,0.55,1.0)`. Subsequent textured UI elements drawn the same frame are tinted gray.

**Fix:** `GL11.glColor4f(1f,1f,1f,1f)` before exit.

### [P1] GL state leak — DiplomacyOverlayPlugin (DiplomacyOverlayPlugin.java:75)
Final glColor is BLACK; GL_BLEND enabled without saving prior state. Vanilla content drawn after the overlay risks black-tint multiply.

**Fix:** Restore color to white; `glPushAttrib(GL_CURRENT_BIT | GL_COLOR_BUFFER_BIT) / glPopAttrib`.

### [P1] Per-click LMB log (DiplomacyOverlayPlugin.java:88)
Per-click `log.info(...)` floods starsector.log while overlay alive.

**Fix:** Remove or guard behind debug flag.

### [P1] Double vertical advance (DiplomacyTabOverlayModel.java:299, 456)
`TooltipMakerAPI.addCustom(comp, pad)` already advances cursor by `comp.height + pad`. Both `addIllegalCommoditiesHorizontal` and `addRelatedFactionLinks` also call `addSpacer(rowH + …)` → blank vertical band equal to component height.

**Fix:** Remove the `addSpacer` lines.

### [P1] DiplomacyIntel sort hack (DiplomacyIntel.java:45)
"AAA_…" sort-string forces top placement; fragile vs other mods.

**Fix:** Override `getSortTier()` → `IntelSortTier.TIER_1`.

### [P1] Render() info-log spam (DiplomacyTabOverlayModel.java:81)
Fires every refresh (sort/dir/click/ally-nav). Downgrade to debug.

## API verification
```
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.ui.TooltipMakerAPI | grep addCustom
  public abstract com.fs.starfarer.api.ui.UIComponentAPI addCustom(com.fs.starfarer.api.ui.CustomUIPanelPlugin, float);
  public abstract com.fs.starfarer.api.ui.UIComponentAPI addCustom(com.fs.starfarer.api.ui.UIComponentAPI, float);
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.campaign.IntelInfoPlugin | grep -i sort
  public abstract com.fs.starfarer.api.campaign.IntelInfoPlugin$IntelSortTier getSortTier();
  public abstract java.lang.String getSortString();
```

## Risks
- Overlay-removal needs to handle "no previous" first-open case.
- Singleton try/catch must not swallow caller-relevant exceptions; rethrow after cleanup.
