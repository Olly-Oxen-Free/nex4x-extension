# Research Findings — Listener Save Integrity

## Source
Audit `THEME-C`, 2026-05-10.

## Problem
`ListenerManagerAPI.addListener(Object)` registers a listener as **permanent** (saved with sector). nex4x registers three non-Serializable listeners via this overload from `Nex4xModPlugin.onGameLoad`:

- `Nex4xInvasionBridge` (listeners/Nex4xInvasionBridge.java:20) — implements `InvasionListener` only.
- `Nex4xRaidBridge` (listeners/Nex4xRaidBridge.java:12) — implements `RaidListener` only.
- `Nex4xAgentActionReportListener` — same pattern.

Risk: at save time, sector serializes its listener list. Non-Serializable members trigger `NotSerializableException` (caught by sector save logic and silently dropped from save), or in older Starsector builds, abort the save outright.

Additionally, `hasListenerOfClass` guard means once registered, the corrupted listener entry persists across reloads — re-registration on `onGameLoad` is skipped.

## API verification
```
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI
  public abstract void addListener(java.lang.Object);
  public abstract void addListener(java.lang.Object, boolean);   // boolean = transient
  ...
```
Second-arg `true` registers as **transient** — not saved with sector. Required pattern for any listener that holds non-serializable state or whose lifecycle is owned by a mod plugin.

## Recommended approach
Two options:
1. **Transient listeners + re-register each load** (simpler):
   - Change `Nex4xModPlugin.registerNexCampaignBridges()` (line 163,166,169) to call `addListener(x, true)`.
   - Remove `hasListenerOfClass` guard (no longer needed; transient listeners reset between sessions).
2. **Implement Serializable** (more work, persists cross-save):
   - Add `implements java.io.Serializable` + serial UID.
   - Verify no transitive non-serializable fields (these classes hold no fields → trivial).

Option 1 is cleaner and aligns with how the mod actually uses these (stateless dispatchers).

## Affected
- `src/nex4x/Nex4xModPlugin.java:163,166,169`
- `src/nex4x/listeners/Nex4xInvasionBridge.java`
- `src/nex4x/listeners/Nex4xRaidBridge.java`
- `src/nex4x/listeners/Nex4xAgentActionReportListener.java` (out of audit cluster scope but same defect)

## Save compatibility
Existing saves may already contain dropped listener entries (silent corruption). Mitigation: on first onGameLoad after fix, run `removeListenerOfClass(...)` to purge any stale entries before re-adding as transient.

## Risks
- Forgetting to re-register on `onGameLoad` → bridges never fire. Guard via fail-fast log if listener missing post-init.
