# Research Findings — Standardize Time Units

## Source
Audit `THEME-B`, 2026-05-10.

## Problem
`CampaignClockAPI.getTimestamp()` returns **seconds** (long, game-time epoch). Multiple sites store it as a "day index" and compare against day-scaled constants — strategic AI runs ~86400× too fast. Separately, two managers reinvent calendar-day math (custom epoch `cycle-206`, dropping months) which silently diverges from the rest of the codebase.

## API verification
```
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.campaign.CampaignClockAPI
  public abstract long getTimestamp();
  public abstract float getElapsedDaysSince(long);
  public abstract int getCycle();
  public abstract int getMonth();
  public abstract int getDay();
  ...
```
`getElapsedDaysSince(long timestamp)` is the canonical day-delta helper.

## Affected sites
**Seconds-as-days bug:**
- `src/nex4x/ai/StrategicGoalManager.java:58` — `currentDay = clock.getTimestamp()`; `IMPORTANCE_RECALC_INTERVAL=7f` → recalc every 7s.
- `src/nex4x/ai/archetype/CommitmentLedger.java:100, 185, 266` — `archetypeSince`, `lockedUntil` derived from raw timestamp.
- `src/nex4x/ai/goals/GoalScorer.java:71, 213` — `ageDays = currentDay - createdDay` saturates ~90s.
- `src/nex4x/wargoals/WarScoreTracker.java:37` — `declaredDay` cast from millis-style long to float; meaningless.

**Custom epoch math (skips months):**
- `src/nex4x/contracts/ContractAuctionManager.java:22` — `now() = day + cycle*365`.
- `src/nex4x/demands/DemandManager.java:26` — same.
- `src/nex4x/agreements/Agreement.java:88-92` — `(cycle-206)*365 + (month-1)*30 + day` (correct, but diverges from rest).

## Recommended approach
1. Add helper `src/nex4x/util/Nex4xClock.java`:
   - `static long now()` → `Global.getSector().getClock().getTimestamp()` (seconds; opaque baseline).
   - `static float daysSince(long ts)` → `clock.getElapsedDaysSince(ts)`.
   - `static float currentAbsoluteDay()` → `cycle*365 + (month-1)*30 + day` for cases that genuinely need a calendar-day index (UI display, JSON serialization).
2. Replace every flagged call site:
   - For "store creation time, compute age" → store `long ts = Nex4xClock.now()`, compute `Nex4xClock.daysSince(ts)`.
   - For "day index for log/display" → `Nex4xClock.currentAbsoluteDay()`.
3. `Agreement.java` keeps its `currentAbsoluteDay()` semantics but delegates to helper; deprecate inline math.
4. Verify saved fields: any field currently typed `float` storing a "day" must migrate to `long` storing a timestamp. **Save-game compatibility risk** — see Risks.

## Save-game compatibility
- `Agreement.creationDay` (float, current cycle*365 calendar day) — already correct in absolute-day form; rewrite to use helper but keep field semantics identical.
- `Goal.createdDay` and `CommitmentLedger.archetypeSince`, `lockedUntil` — currently store seconds-as-day. After fix, fields hold timestamps (long). Existing saves contain garbage values (large floats); on load, treat any value with magnitude > 1e6 as "reset to current timestamp". Add migration in `Nex4xModPlugin.onGameLoad`.

## Risks
- Migrating field types breaks save format → must add legacy-value detector.
- Mixing the new helper with files that hold raw `getTimestamp()` calls during partial migration → wrap migrations to land per-class atomically.
