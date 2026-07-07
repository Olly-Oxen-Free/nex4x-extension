# PRD: Industries Wiring — AI Faction Seeding + Disruption Gate

**Slug:** industries-wiring
**Created:** 2026-05-31
**Severity:** P1
**Source:** 2026-05-31 nex4x flaw audit

## Vision

Every AI faction market of qualifying size automatically holds a `nex4x_diplomatic_embassy` and/or
`nex4x_intelligence_bureau` when a new game starts, making influence generation a faction-wide
economic reality rather than a player-only manual action. Concurrently, disrupted instances of
those buildings (and vanilla buildings listed in `buildingIncome`) no longer contribute influence
income, so the tooltip claim "Disrupted — no effects applied" becomes truthful.

## Why now

Three compounding defects were confirmed in the 2026-05-31 audit:

1. **No AI seeding.** `data/campaign/industries.csv` registers the three nex4x industries and their
   Java plugin classes. No call to `market.addIndustry(...)` exists anywhere in `src/` for AI
   markets; `onNewGameAfterEconomyLoad` in `Nex4xModPlugin` only seeds the manager, not industries.
   Vanilla's econ JSON (`data/campaign/econ/*.json`) seeds industries via the `"industries"` array
   in market definitions, but those files are vanilla-owned and cannot list mod-specific industries.
   Nexerelin's `ColonyManager.buildIndustry(market, id)` (jar source, line 1776) is the standard
   pattern for procedurally adding mod industries post-economy-load. The correct fix is a new
   `IndustrySeeder` utility invoked from `onNewGameAfterEconomyLoad`, walking all non-player AI
   markets and calling `ColonyManager.buildIndustry(market, industryId, true)` (instant=true so
   they start functional rather than under construction). Seeding rules come from a new config file
   `data/config/nex4x/industry_seeding.json`.

2. **Disrupted buildings still pay income.** `InfluenceManager.calculateCycleIncome`
   (`src/nex4x/influence/InfluenceManager.java`, line 103-105) iterates `cfg.buildingIncome` and
   calls `m.hasIndustry(b.getKey())` with no `isFunctional()` / `isDisrupted()` guard. Both
   `isFunctional()` and `isDisrupted()` exist on `com.fs.starfarer.api.campaign.econ.Industry`
   (confirmed via `javap` against `starfarer.api.jar`). The fix is a one-line addition of
   `m.getIndustry(b.getKey()).isFunctional()` after the `hasIndustry` check.

3. **Vanilla industry IDs confirmed correct.** `influence_sources.json` keys `highcommand`,
   `militarybase`, `patrolhq`, and `commerce` were verified against the vanilla
   `com.fs.starfarer.api.impl.campaign.ids.Industries` constants — all four match exactly. No ID
   corrections needed.

## Seeding mechanism chosen

**Campaign listener / `onNewGameAfterEconomyLoad` + `ColonyManager.buildIndustry(instant=true)`.**

Rationale: vanilla econ JSON files are vanilla-owned; faction `.json` files in
`data/world/factions/` have no `"industries"` array (confirmed by inspecting `hegemony.faction`).
Nexerelin itself seeds mod industries at economy-load time via `ColonyManager.buildIndustry`
(line 1776 of jar source). A config-driven `IndustrySeeder.seedAll()` called from
`onNewGameAfterEconomyLoad` is the idiomatic nex4x pattern, mirrors PRD-003/PRD-009 listener
registration approach, and avoids touching any vanilla data files.

Seeding rules in `data/config/nex4x/industry_seeding.json`:
- `diplomaticEmbassy.minMarketSize` (default 3) — markets >= this size get an Embassy.
- `intelligenceBureau.minMarketSize` (default 4) — markets >= this size get a Bureau.
- `excludeFactions` array — factions that never get seeded (e.g. `"pirates"`, `"remnants"`).
- `excludeMarketTags` array — market tags that block seeding (e.g. `"station"`).

## Success criteria

- Build green (`./build.sh`).
- `runcode` smoke: at least one AI faction market (size >= 3, non-pirate) has
  `nex4x_diplomatic_embassy` after `IndustrySeeder.seedAll()` is called.
- `runcode` smoke: disrupting an Embassy on a test market and advancing one influence cycle
  produces zero building income for that market (was non-zero before the fix).
- `InfluenceManager.calculateCycleIncome` loop (line 103) contains an `isFunctional()` guard.

## Scope

**In:**
- New `src/nex4x/industries/IndustrySeeder.java` — static `seedAll()` called from `onNewGameAfterEconomyLoad`.
- New `data/config/nex4x/industry_seeding.json` — seeding thresholds and exclusion lists.
- `src/nex4x/Nex4xModPlugin.java` `onNewGameAfterEconomyLoad` — add `IndustrySeeder.seedAll()` call.
- `src/nex4x/influence/InfluenceManager.java` `calculateCycleIncome` line 103 — add `isFunctional()` guard.
- `src/nex4x/debug/Nex4xDebugCommand.java` — add `auditIndustries` runcode command for smoke testing.
- `src/nex4x/Nex4xConstants.java` — add `PATH_INDUSTRY_SEEDING` constant.

**Out:**
- No changes to `data/campaign/industries.csv` — IDs and plugin class refs already correct.
- No changes to `data/config/nex4x/influence_sources.json` — vanilla IDs confirmed correct.
- No changes to vanilla faction `.faction` files or econ JSON.
- No retroactive seeding of existing saves (save-compat: do not walk markets on `onGameLoad`; new games only).
- CyberSecuritySuite seeding (no `buildingIncome` entry; separate balance decision, defer).

## Risks

- **Save compatibility** — `data/config/nex4x/industry_seeding.json` is a new file; no existing
  data changes. Adding an industry via `addIndustry` on an existing save is unsafe
  (object already initialized by the save). The seeder must only run in `onNewGameAfterEconomyLoad`
  and must be guarded to avoid running on `onGameLoad`. Mark `data/**` per KARIMO
  `require_review` boundary.
- **Instant build vs. under-construction** — passing `instant=true` to
  `ColonyManager.buildIndustry` means AI markets start fully functional. This is the correct
  behavior (mirrors how vanilla seeds its industries in econ JSON) but reviewers should confirm
  no upkeep bootstrapping issues arise.
- **Exclusion list accuracy** — if `excludeFactions` is under-tuned, factions like `remnants`
  (no econ) or `pirates` (lore mismatch) get Embassies. Default exclusions should be reviewed.
- **Nex4xModPlugin.java is in `require_review`** — the `onNewGameAfterEconomyLoad` hook addition
  must be reviewed; wrong hook placement causes save corruption.

## Acceptance

1. `./build.sh` exits 0.
2. `runcode` (LazyWizard Console):
   ```
   IndustrySeeder.seedAll();
   for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
     if (m.hasIndustry("nex4x_diplomatic_embassy"))
       Console.showMessage(m.getName() + " has Embassy");
   }
   ```
   At least one non-player AI market prints.
3. `runcode` disruption smoke:
   ```
   MarketAPI m = /* any market with embassy */;
   m.getIndustry("nex4x_diplomatic_embassy").setDisrupted(60, true);
   float income = InfluenceManager.getOrCreate().calculateCycleIncome(m.getFactionId(),
       InfluenceManager.getOrCreate().getConfig_FOR_TEST());
   Console.showMessage("income=" + income);  // must match same market without building contribution
   ```
4. No NPE or ClassCastException in starsector.log during new-game sector generation.

## Tasks

See `tasks.yaml`.
