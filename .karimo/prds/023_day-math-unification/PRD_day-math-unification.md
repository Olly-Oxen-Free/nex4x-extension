# PRD: Day-Math Unification

**Slug:** day-math-unification
**Created:** 2026-05-31
**Severity:** P2
**Source:** 2026-05-31 nex4x flaw audit

## Vision

One canonical helper — `Nex4xClock.currentAbsoluteDay()` — computes absolute-day values using
Starsector's correct 360-day calendar (12 months × 30 days) with a single epoch of cycle 0.
Every manager that currently hard-codes its own formula calls it instead, making cross-manager
day values numerically consistent and eliminating a ~5-day/cycle accumulating drift.

## Why now

Starsector's calendar has 12 × 30 = 360 days per cycle. Every site in nex4x that computes
an absolute day index uses `cycle * 365f`, which overcounts by 5 days per cycle. After just
one cycle, timers (declaration expiry, modifier decay, vassal tenure, policy baseline) drift
by 5 days. After the game's nominal starting cycle (206), the drift relative to reality is
1 030 days — any comparison between a value from a 365-based site and one from a 360-based
site silently produces wrong results.

Additionally, five sites ignore the month entirely (`day + cycle*365f`), adding up to 30
days of month-dependent jitter. And three distinct epochs exist (`cycle-0`, `cycle-206`, and
partial variants), making stored values from different managers non-comparable.

PRD-002 standardized the `getElapsedDaysSince`/`daysSince` API for duration math but left the
absolute-day helpers broken.

## Affected sites — verified (2026-05-31)

| # | File | Line(s) | Formula | Bug class |
|---|------|---------|---------|-----------|
| A | `src/nex4x/util/Nex4xClock.java` | 36 | `cycle*365 + (month-1)*30 + day` | **365 multiplier** |
| B | `src/nex4x/declarations/Declaration.java` | 99–101, 105–107 | `(cycle-206)*365 + (month-1)*30 + day` | **365 mult + −206 epoch** |
| C | `src/nex4x/managers/MemoryManager.java` | 45–47 | `(cycle-206)*365 + (month-1)*30 + day` | **365 mult + −206 epoch** |
| D | `src/nex4x/politics/DynamicModifierManager.java` | 32–33 | `day + cycle*365` | **365 mult + missing month** |
| E | `src/nex4x/vassals/VassalManager.java` | 25–26 | `day + cycle*365` | **365 mult + missing month** |
| F | `src/nex4x/policies/PolicyManager.java` | 46–47 | `day + cycle*365` | **365 mult + missing month** |

### Sites using `Nex4xClock.currentAbsoluteDay()` directly

These inherit bug A (365 multiplier) from the canonical helper. Within a single manager their
deltas are internally consistent (epoch cancels), but they drift against duration-based timers
and cannot be compared across managers.

| Site | File | Line | Delta-only? | Truly affected? |
|------|------|------|-------------|-----------------|
| `WarScoreTracker.createWarGoal` | `src/nex4x/wargoals/WarScoreTracker.java` | 37 | Stores as baseline; diff never crosses managers | **Harmless-but-inconsistent** — fix anyway to match canonical |
| `CoalitionVote` constructor | `src/nex4x/coalitions/CoalitionVote.java` | 33 | Diff with later `currentAbsoluteDay()` at line 85 | **Harmless-but-inconsistent** — fix anyway |
| `ContractAuctionManager` | `src/nex4x/contracts/ContractAuctionManager.java` | 23 | Calls helper | **Harmless-but-inconsistent** |
| `DemandManager` | `src/nex4x/demands/DemandManager.java` | 27 | Calls helper | **Harmless-but-inconsistent** |
| `MediationManager` | `src/nex4x/mediation/MediationManager.java` | 27 | Calls helper | **Harmless-but-inconsistent** |
| `DeclarationManager` | `src/nex4x/declarations/DeclarationManager.java` | 216 | Calls `Declaration.currentAbsoluteDay()` (not the canonical helper) | **Affected via site B** |
| `AIProposalManager` | `src/nex4x/managers/AIProposalManager.java` | 58 | `Nex4xClock.currentAbsoluteDay() - 206f*365f` | **Affected: −206 offset baked in** |
| `CasusBelli` | `src/nex4x/casusbelli/CasusBelli.java` | 73 | `Nex4xClock.currentAbsoluteDay() - 206f*365f` | **Affected: −206 offset baked in** |
| `Agreement.getCurrentDay` | `src/nex4x/agreements/Agreement.java` | 92–95 | `Nex4xClock.currentAbsoluteDay() - 206f*365f` | **Affected: −206 offset baked in** |
| `Nex4xDebugCommand` | `src/nex4x/debug/Nex4xDebugCommand.java` | 229 | Display only | Harmless display drift |

### Summary of bug classes

- **365 mult (overcounts by 5 days/cycle):** ALL sites via A or direct inline formula.
- **Missing month term (±30 days month-dependent jitter):** sites D, E, F.
- **−206 epoch offset:** sites B, C, `Agreement`, `AIProposalManager`, `CasusBelli` — values
  are stored with this offset; after the fix (epoch=0) stored baselines change meaning.

## Design — the fix

1. **`Nex4xClock.currentAbsoluteDay()`** — change multiplier `365f` → `360f`. No epoch shift;
   cycle 0 remains the epoch (correct, minimal, easy to reason about).

2. **`Declaration.currentAbsoluteDay()` and `Declaration.getCurrentDay()`** — delete both.
   Replace every call to them with `Nex4xClock.currentAbsoluteDay()`. The `−206` offset
   previously ensured stored `creationDay`/`expiryDay` fields were small numbers; after the
   fix they'll be cycle-0-relative (e.g. cycle 206 day 1 → 74 160). That is fine — fields
   are compared only within Declaration via `getDaysRemaining`/`isExpired`, so the epoch
   cancels and correctness is preserved as long as all reads/writes use the same formula.

3. **`MemoryManager`** — replace inline formula at line 45–47 with
   `Nex4xClock.currentAbsoluteDay()`.

4. **`DynamicModifierManager`**, **`VassalManager`**, **`PolicyManager`** — replace
   `day + cycle*365f` inline with `Nex4xClock.currentAbsoluteDay()`. The month term that was
   missing starts contributing; stored baselines shift by up to 30 days, which is smaller
   than the existing inter-manager drift. These are all event-timestamp baselines where the
   delta is computed immediately on each tick (e.g. `PoliticalModifier.decay()` uses elapsed
   game days), so the semantic impact is: freshly-created records after the patch use correct
   values; old records loaded from save will have a one-time jump of ≤30 days + 5×cycle days
   on first comparison. For P2, document but do not block on a save migration for these three.

5. **`Agreement.getCurrentDay()`**, **`AIProposalManager`**, **`CasusBelli`** — remove the
   `- 206f * 365f` call-site offsets; call `Nex4xClock.currentAbsoluteDay()` directly.
   `Agreement` fields are stored and compared internally so the epoch cancels.
   `AIProposalManager.lastProposalDay` values in existing saves will be large (cycle-0 epoch)
   after the patch; old saves will not compare against fresh values for one cycle, which may
   suppress AI proposals for up to ~360 days on loaded saves — acceptable for P2.

6. **`Nex4xDebugCommand`** — update display comment to match new epoch.

7. **`isLegacyDayValue`** — the threshold `> 1e6` was designed to detect raw-timestamp values
   masquerading as day counts. After the fix, cycle-0 days for year 206 are ~74 000, well below
   1e6. The threshold remains valid. The comment should be updated to note the new epoch.

### Save-migration note

Stored `creationDay`/`expiryDay` fields in `Declaration`, `Agreement`, `CasusBelli`,
`VassalRelation`, `Policy`, and `FactionMemory` objects in existing saves will be in the
old (−206 epoch, 365-based) format. After the patch:

- **Declaration / Agreement / CasusBelli**: fields are compared only to `currentAbsoluteDay()`
  at the same call site, so the before/after delta is a one-time correction of at most
  `206*5 = 1030 days`. Declarations that should have expired may survive one extra advance;
  declarations that haven't expired yet may briefly see a "negative remaining" then correct
  themselves on next load. This is the main P2 risk.
- **`isLegacyDayValue`** remains valid as a best-effort guard. No automated migration of
  individual field values is planned; the inconsistency self-heals within one game session.
- If a future save-migration ticket is desired, a one-time `onGameLoad` walk over all live
  Declarations/Agreements converting `creationDay` from `(old) + 206*360 - 206*365` to the
  new epoch is straightforward but deferred.

## Success criteria

- `Nex4xClock.currentAbsoluteDay()` uses `* 360f`.
- No other file contains an inline `* 365f` day formula or a `- 206f * 365f` offset.
- `Declaration.currentAbsoluteDay()` and `Declaration.getCurrentDay()` are deleted; no
  compilation errors.
- `./build.sh` exits 0.
- Debug runcode smoke: `currentAbsoluteDay` at cycle=1, month=1, day=1 returns exactly 360;
  at cycle=2 returns 720. All managers that store a baseline produce the same value as
  `Nex4xClock.currentAbsoluteDay()` at the same calendar moment.

## Scope

**In:**
- `Nex4xClock.currentAbsoluteDay()` — fix multiplier.
- `Declaration.currentAbsoluteDay()` / `Declaration.getCurrentDay()` — delete; migrate callers.
- `MemoryManager` — replace inline formula.
- `DynamicModifierManager` — replace inline formula.
- `VassalManager` — replace inline formula.
- `PolicyManager` — replace inline formula.
- `Agreement.getCurrentDay()` — remove −206 offset.
- `AIProposalManager` — remove −206 offset.
- `CasusBelli.getCurrentDay()` — remove −206 offset.
- `Nex4xClock.isLegacyDayValue` Javadoc update.
- `Nex4xDebugCommand` display line update.
- Build verification.

**Out:**
- Automated save-migration of individual stored day fields (defer; self-healing).
- Any changes to `daysSince` / `now` / `getElapsedDaysSince`-based code paths.
- Changes to data files, jars, or non-Java source.

## Risks

1. **Save compatibility (main risk):** Stored day baselines shift by up to 1 030 days. Short
   declarations/agreements (< 1 030 days duration) could briefly appear expired on first load
   of an old save. Mitigation: document in changelog; `isLegacyDayValue` remains a fallback
   guard. Full save migration deferred but path is clear.
2. **`AIProposalManager` cooldown loss:** On a loaded save the `lastProposalDay` map will hold
   old-epoch values; all cooldowns will appear expired for one cycle. AI proposals may spike
   briefly. Acceptable for P2.
3. **Missing the `Declaration.currentAbsoluteDay()` callers:** `DeclarationManager` calls
   `Declaration.currentAbsoluteDay()` (not the canonical helper) at line 216. Must update
   to `Nex4xClock.currentAbsoluteDay()` as part of the Declaration cleanup task.

## Acceptance

- `./build.sh` exits 0.
- `runcode` smoke in `Nex4xDebugCommand` (`auditDayMath`):
  - Reports `Nex4xClock.currentAbsoluteDay()` at current calendar position.
  - Asserts that a synthetic cycle-1/month-1/day-1 value would equal exactly 360.
  - Confirms no inline `365` formula survives by checking the compiled constant pool
    (or simply: build passes and assertions hold).

## Tasks

See `tasks.yaml`.
