# PRD: Valuation Engine Wiring

**Slug:** valuation-engine-wiring
**Created:** 2026-05-31
**Severity:** P1
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every AI accept/reject decision and every UI value label in the negotiation system is
driven by the full `Valuator` pipeline (personality weight × goal alignment × relation
scalar × EconomyAPI scarcity), with a single authoritative base-value source
(`base_values.json` via `BaseValueTable`). The dead `BalanceCalculator`/`AutoBalanceSolver`/
`BalanceSurface` path is absorbed into the live path so nothing is unreachable.

## Why Now

The live path (`DealEvaluator` → `DealPackage.getBalance` → `ItemValuator.evaluate`)
uses flat hardcoded constants that do not vary by leader personality, strategic goals,
faction relations, or commodity scarcity. The fully-designed engine (`Valuator.valueTo`)
already exists with all four axes implemented but is called only from dead test-only
classes (`BalanceCalculator`, `AutoBalanceSolver`, `Nex4xDebugCommand`). Until wired,
every deal evaluation is personality-blind and scarcity-blind:

- Mercantile leaders value commodities/trade pacts identically to Ruthless leaders.
- A faction starving for fuel sees fuel exactly as valuable as one flush with it.
- `BaseValueTable` loads `base_values.json` at startup but zero fields are ever read.
- `NegotiableItemCatalog.estimatedUnitValue` maintains a second independent constant
  table that disagrees with `ItemValuator` (ceasefire: 30 000 vs 5 000; knowledge:
  50 000 vs 5 000; prisoner: 15 000 vs 3 000). This drives `AutoBalanceSolver.pickBestAdd`
  with wrong values, making auto-balance add incorrect items.
- `mapCategory` never produces `ItemCategory.TRADE_PACT` or `ItemCategory.NAP` so
  the personality multipliers for those categories (MERCANTILE +25%, goal BUILD_ALLIANCE
  +30%) are permanently unreachable.

## Success Criteria

1. `DealEvaluator.evaluate` produces different numeric balances for two leaders with
   different `Personality` values on an identical `DealPackage` (proves `Valuator` wired).
2. A commodity item with `itemId = "fuel"` produces a higher value from `Valuator.valueTo`
   when the receiving faction's markets show `available < demand` than when `available > demand`.
3. `NegotiableItemCatalog.estimatedUnitValue` returns values that are within 30% of what
   `ItemValuator.valueForLeader` returns for the same item (eliminates split-table divergence).
4. `ItemValuator.mapCategory` produces `ItemCategory.TRADE_PACT` for `AgreementType.TRADE_AGREEMENT`
   items and `ItemCategory.NAP` for `AgreementType.NAP` items.
5. `BaseValueTable` fields are the single source for all agreement/declaration/one-time
   base constants in `ItemValuator`; inline `CEASEFIRE_BASE`, `PEACE_TREATY_BASE`, etc.
   are removed.
6. `BalanceCalculator` and `BalanceSurface` are folded into the live path; their callers
   (`Nex4xDebugCommand`, `AutoBalanceSolver`) migrated. Dead `BalanceCalculator.java` and
   `BalanceSurface.java` are marked `@Deprecated` with a note pointing to the new path
   (deletion deferred to PRD-025 dead-code sweep).
7. Build (`./build.sh`) is green.
8. Runcode smoke: two leaders (MERCANTILE vs RUTHLESS personality) evaluate an identical
   TRADE_AGREEMENT item; values differ by at least 20%.

## Scope

**In:**
- Wire `DealEvaluator` to accept a `LeaderProfile` and call `ItemValuator.valueForLeader`
  (which calls `Valuator.valueTo`) instead of the plain `evaluate(item, factionId)` path.
- `NegotiationPanel`: pass `leader` (already held at line 123) into the `DealEvaluator`
  constructor or a new `DealEvaluator(LeaderProfile)` overload so UI display values and
  the accept/reject decision use the same wired pipeline.
- `DealPackage.getBalance`: add an overload accepting `LeaderProfile` + proposer faction
  ID; route through `ItemValuator.valueForLeader`.
- Fix `mapCategory` to produce `TRADE_PACT` for `TRADE_AGREEMENT` items and `NAP` for
  `NAP` items (sub-type discriminated via `item.getAgreementType()`).
- Drive `ItemValuator` base constants from `BaseValueTable` fields. Remove inline
  `CEASEFIRE_BASE`, `PEACE_TREATY_BASE`, `NAP_BASE`, etc. that duplicate JSON values.
- Align `NegotiableItemCatalog.estimatedUnitValue` to call `ItemValuator.getBaseValue`
  (or delegate to `BaseValueTable`) so both paths share one source.
- Deprecate (do not delete) `BalanceCalculator` and `BalanceSurface`; migrate the two
  call sites in `Nex4xDebugCommand` and `AutoBalanceSolver` to use `DealEvaluator`/
  `ItemValuator.valueForLeader` directly.
- Runcode smoke in `Nex4xDebugCommand.auditValuation()`.

**Out (deferred follow-up PRD):**
- Real war-declaration value from live Nex fleet-strength data (currently flat 15 000).
- Real blueprint/knowledge value from tech-tree rarity data (currently flat 5 000).
- Real concession value from faction-resource calculation (currently flat 4 000).
- Deletion of `BalanceCalculator.java` and `BalanceSurface.java` source files
  (deferred to PRD-025 dead-code sweep).
- `AutoBalanceSolver` replacement with a leader-aware solver (currently uses
  `NegotiableItemCatalog.estimatedUnitValue` which will be fixed to a consistent
  base value, but the full greedy-solver redesign is deferred).

## Verified File:Line Findings

| File | Finding |
|------|---------|
| `src/nex4x/evaluation/DealEvaluator.java:32` | `DealEvaluator()` constructs a plain `new ItemValuator()` — no leader, no Valuator pipeline |
| `src/nex4x/evaluation/DealEvaluator.java:49` | `deal.getBalance(valuator)` — live balance call uses personality-blind valuator |
| `src/nex4x/ui/NegotiationPanel.java:112` | `new DealEvaluator()` — no leader passed |
| `src/nex4x/ui/NegotiationPanel.java:123` | `this.leader` is already available here |
| `src/nex4x/ui/NegotiationPanel.java:365,409,434` | UI display calls `valuator.evaluate(item, targetFactionId)` — leader-blind |
| `src/nex4x/negotiation/ItemValuator.java:270-276` | `valueForLeader` exists but called only from `BalanceCalculator` (dead path) |
| `src/nex4x/negotiation/ItemValuator.java:288` | `mapCategory`: AGREEMENTS always → `ALLIANCE`; comment "refined per item-id in v5.1" |
| `src/nex4x/negotiation/ItemValuator.java:79-99` | Inline constants duplicate `BaseValueTable` fields (e.g. `CEASEFIRE_BASE=5000` vs `BaseValueTable.CEASEFIRE=5000`) |
| `src/nex4x/negotiation/ItemValuator.java:213-216` | `getWarDeclarationValue` returns flat 15 000; comment "deferred to Nexerelin integration" |
| `src/nex4x/negotiation/ItemValuator.java:228-231` | `getKnowledgeValue` returns flat 5 000; comment "Full evaluation deferred" |
| `src/nex4x/negotiation/ItemValuator.java:257-260` | `getConcessionValue` returns flat 4 000; comment "use moderate base for now" |
| `src/nex4x/negotiation/BaseValueTable.java:22-48` | `load()` parses JSON into static fields; zero fields are read anywhere outside this class |
| `src/nex4x/negotiation/NegotiableItemCatalog.java:93-95` | `estimatedUnitValue`: ceasefire=30 000, knowledge=50 000, prisoner=15 000 — all differ from `ItemValuator` constants |
| `src/nex4x/negotiation/BalanceCalculator.java:19,23,26` | Only caller of `ItemValuator.valueForLeader` — itself called only from `Nex4xDebugCommand:115` and `AutoBalanceSolver:19,31` |
| `src/nex4x/negotiation/AutoBalanceSolver.java:19,31` | Uses `BalanceCalculator` which reaches `Valuator`; but `AutoBalanceSolver` is called nowhere in live code |
| `src/nex4x/negotiation/BalanceSurface.java:21-22` | Adapts `BalanceCalculator.Result` for display; called nowhere |
| `src/nex4x/negotiation/ItemCategory.java:1-20` | Enum defines `NAP`, `TRADE_PACT`, `STAR_CHART`, `DENOUNCEMENT_DECLARATION` — none ever produced by `mapCategory` |
| `src/nex4x/negotiation/Valuator.java:35-63` | `scarcityMod` calls `EconomyAPI.getMarketsCopy()` + `CommodityOnMarketAPI.getMaxDemand()`/`getAvailable()` — both confirmed to exist in `starfarer.api.jar` |
| `src/nex4x/negotiation/Valuator.java:81-111` | `personalityWeight` references `TRADE_PACT` (line 85), `COMMODITY` (87), `ALLIANCE` (91), `TRIBUTE` (94,97), `INTEL` (100), `SPARE_CONCESSION` (97) — all reachable after mapCategory fix |

## Risks

- `DealEvaluator` is constructed in three call sites (`NegotiationPanel:112`,
  `AIProposalManager:98`, `AutoNegotiator:28`). All need the `LeaderProfile` overload.
  Mitigation: add `DealEvaluator(LeaderProfile, String proposerFactionId)` constructor;
  existing `DealEvaluator()` becomes a leader-null fallback that logs a warning, so
  sites not yet migrated degrade gracefully.
- `DealPackage.getBalance(ItemValuator)` is called from `CounterOfferGenerator` (lines
  98, 132) and `NegotiationPanel` (line 365). Adding a leader-aware overload avoids
  breaking existing callers.
- `BaseValueTable` fields are `static` mutable ints loaded once at startup. Replacing
  inline constants with field reads is safe provided `load()` is already called before
  any valuation; verify call site in `Nex4xModPlugin`.
- Personality noise for `CAPRICIOUS` is seeded by `(p.hashCode()*31 + cat.hashCode())`
  — deterministic, no randomness risk.

## Acceptance

1. `./build.sh` exits 0 with no errors.
2. Runcode in `Nex4xDebugCommand`:
   ```
   nex4x audit_valuation
   ```
   Output must include two lines like:
   ```
   [Nex4x] MERCANTILE leader values trade_agreement item: XXXX
   [Nex4x] RUTHLESS leader values trade_agreement item: YYYY
   PASS: values differ by >= 20%
   ```
3. Runcode includes a commodity scarcity check:
   ```
   [Nex4x] scarcityMod(leaderFactionId="hegemony", itemId="fuel"): Z.ZZ
   ```
   (value != 1.0 confirms EconomyAPI integration).

## Tasks

See `tasks.yaml`.
