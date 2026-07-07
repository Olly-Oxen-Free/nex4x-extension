# PRD: Diplomacy Bridge Fallback — Null-Return Detection & Valid Stage Definitions

**Slug:** diplomacy-bridge-fallback
**Created:** 2026-05-31
**Severity:** P0
**Source:** 2026-05-31 nex4x flaw audit

---

## Vision

Every `NexDiplomacyBridge` call that invokes Nex's diplomacy pipeline either (a) produces a real
`DiplomacyIntel` event visible in the intel log **or** (b) unambiguously falls back to a direct
`adjustRelationship`/`setRelationship` call — never silently producing zero relationship change.
Friendship declarations and denouncements that currently apply **zero rep change** will apply the
intended delta reliably every time.

---

## Why Now

PRD-009 ("Nex Diplomacy Event Integration", completed 2026-05-10) introduced `NexDiplomacyBridge`
to route nex4x relation changes through Nex's `DiplomacyManager`. The bridge was designed with a
`try/catch(Throwable)` fallback pattern under the assumption that Nex signals failure via
exception. That assumption is wrong.

**Two confirmed silent no-op failure modes (verified 2026-05-31):**

1. **Invalid stage ids `"respect"` and `"insult"`** — `fireRespectEvent` (line 45) and
   `fireInsultEvent` (line 70) pass these ids to `DiplomacyManager.createDiplomacyEventV2`.
   Neither stage exists in Nex's `diplomacyConfig.json` (verified: full stage list is
   `anti_corporation`, `antiwar_protest`, `ceasefire`, `celebrity_wedding`,
   `celebrity_wedding_jilted`, `contract_cheating`, `cooperation_deal`,
   `cooperation_deal_major`, `culture_boom`, `declare_war`, `diplomat_crimes`,
   `diplomatic_blunder`, `dissident_treatment`, `extradition`, `extradition_refused`,
   `failed_deal`, `helped_foil_terror`, `official_aid`, `peace_treaty`,
   `persecution_majority`, `persecution_minority`, `philanthropy`,
   `political_funding_uncovered`, `priest_scandal`, `prisoner_exchange`, `privateering`,
   `religious_expansion`, `shots_fired`, `spy_ring_uncovered`, `starcrossed`,
   `tech_stolen`, `terrorism_uncovered`, `tourney_cheating`, `xenophobia` — no `respect`,
   no `insult`). `eventDefsById.get("respect")` returns null; the method logs
   "No event available" and returns null. `catch(Throwable)` never runs. Net: friendship
   declarations (`DeclarationManager.declareFriendship`) and denouncements
   (`declareDenouncement` + ripple) apply **zero relationship change**.

2. **Null return when faction has no markets** — `firePeaceTreaty` (line 94) and
   `fireJustifiedWar` (line 146) use `createDiplomacyEvent` / `createDiplomacyEventV2`.
   Nex's internal `marketPicker.pick()` returns null when faction1 has no colonized,
   non-hidden markets → method returns null (no event created). Again the catch never
   fires. Net: peace treaties and justified wars against market-less factions are silent
   no-ops.

**Root masking pattern:** The bridge assumes "failure = exception." Nex signals failure with null
returns. PRD-009 is the regression source.

### API Evidence (verified via `javap` 2026-05-31)

```
public static ExerelinReputationAdjustmentResult createDiplomacyEvent(
    FactionAPI, FactionAPI, String, DiplomacyEventParams);   // returns null on failure

public static DiplomacyIntel createDiplomacyEventV2(
    FactionAPI, FactionAPI, String, DiplomacyEventParams);   // returns null on failure
```

Neither signature throws on unknown stage or no-market condition.

### Merge-Config Verification (verified via `javap` + class strings 2026-05-31)

`DiplomacyManager.loadSettings()` calls `Global.getSettings().getMergedJSONForMod(
"data/config/exerelin/diplomacyConfig.json", "nexerelin")` — the standard Starsector
merged-JSON mechanism. This means any mod can ship its own
`data/config/exerelin/diplomacyConfig.json`; Starsector merges the `"events"` array with
the base file at load time. **nex4x can therefore add `respect` and `insult` stage
definitions** and they will appear in Nex's intel log without patching ExerelinCore.jar.

---

## Success Criteria

1. `fireRespectEvent` and `fireInsultEvent` produce a `DiplomacyIntel` entry visible in the
   in-game intel log when both factions have at least one non-hidden market.
2. When a Nex diplomacy call returns null (unknown stage, no markets, or any other cause),
   the bridge's `adjustRelationship`/`setRelationship` fallback fires instead — relationship
   changes by the expected magnitude (not zero).
3. No double-application: a single bridge call applies the relationship change via exactly
   one path — either the Nex event **or** the fallback, never both.
4. `firePeaceTreaty` and `fireJustifiedWar` fall back correctly when faction has no markets.
5. Build green (`./build.sh` exits 0).
6. Smoke test `auditDiplomacy` runcode: triggers a friendship + a denouncement against a
   market-owning faction and a market-less faction; asserts `getRelationship()` delta is
   non-zero in both cases; asserts `DiplomacyIntel` count increased for the market-owning
   case.

---

## Scope

**In:**
- `src/nex4x/integration/NexDiplomacyBridge.java` — all four diplomacy-event methods:
  `fireRespectEvent` (line 45), `fireInsultEvent` (line 70), `firePeaceTreaty` (line 94),
  `fireJustifiedWar` (line 146). Replace `try/catch`-only fallback with explicit null-return
  detection; gate the fallback on null result.
- `data/config/exerelin/diplomacyConfig.json` (new file in nex4x mod) — add `respect` and
  `insult` custom stage definitions so the Nex event pipeline can produce intel entries for
  these interactions.
- `src/nex4x/debug/Nex4xDebugCommand.java` — extend `auditDiplomacy()` with null-path
  coverage assertions.

**Out:**
- Modifying ExerelinCore.jar or Nexerelin source files.
- Changes to `src/` or `data/` files not listed above.
- `syncCoalitionToAlliance`, `ensureAlliance`, `applyTributeCondition`, `enforceNonAggression`,
  `sweepDiplomacyBrains` — these do not call `createDiplomacyEvent*`; out of scope.
- Daily rep gain/decay paths — already using `adjustRelationship` directly; not affected.

---

## Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| **Double-application** — both Nex event AND fallback fire on the same call, applying rep twice | Medium without care | Each bridge method checks the return value; fallback is in an `else` branch — exactly one path executes per call. |
| **getMergedJSONForMod array merge** — if Nex's merge strategy is append-only, a duplicate stage id from the base file could conflict with a nex4x-defined stage | Low | nex4x-defined `respect`/`insult` ids do not exist in the base config (confirmed); no conflict. |
| **Nex version drift** — future Nex update may add its own `respect`/`insult` stages, potentially conflicting with nex4x's definitions | Medium (long term) | Document in nex4x release notes; fix is to remove nex4x stage defs in that scenario. |
| **Market-less faction fallback mismatches original intent** — `firePeaceTreaty` with setRelationship(0) was already the fallback; null-return now also triggers it | Low | Behavior identical to current fallback; no regression. |
| **Stage field constraints in merged JSON** — merged stage defs must match Nex's `DiplomacyEventDef` deserialization schema | Low | Verified schema from config; use same field names as existing stages. |

---

## Acceptance

- `./build.sh` exits 0 with no compilation errors.
- `auditDiplomacy` runcode (in-game debug command):
  - Triggers `fireRespectEvent(factionWithMarkets, target, 5f)` → asserts `getRelationship()` delta ≥ 0.04 **and** `DiplomacyIntel` count increased by ≥ 1.
  - Triggers `fireInsultEvent(factionWithMarkets, target, 5f)` → asserts `getRelationship()` delta ≤ −0.04 **and** `DiplomacyIntel` count increased by ≥ 1.
  - Triggers `fireRespectEvent(marketlessFaction, target, 5f)` → asserts `getRelationship()` delta ≥ 0.04 (fallback fired, not zero).
  - Triggers `firePeaceTreaty(marketlessFaction, target)` while hostile → asserts no longer hostile after call.
  - All assertions logged; any failure logs `AUDIT FAIL:` prefix.

---

## Tasks

See `tasks.yaml`.
