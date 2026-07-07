# PRD-018 Smoke Test Steps (18g)

Run these via LazyWizard Console Commands mod in-game (`devmode` + Console).

## Step 1 — Seed AI markets

```
runcode nex4x.industries.IndustrySeeder.seedAll();
```

Check `starsector.log` for:
```
[Nex4x] IndustrySeeder seeded N embassy, M bureau across K markets
```
N should be > 0 on any standard sector (many size-3+ non-pirate AI markets exist).

## Step 2 — Audit industry placement

```
runcode nex4x.debug.Nex4xDebugCommand.auditIndustries();
```

Expected output: multiple markets listed with `embassy=true`, a positive Embassy count at the end.
Pass criterion: at least one non-player AI market (hegemony, tritachyon, etc.) shows `embassy=true`.

## Step 3 — Disrupt an Embassy and verify income drop

```
runcode
com.fs.starfarer.api.campaign.econ.MarketAPI m = null;
for (com.fs.starfarer.api.campaign.econ.MarketAPI x : Global.getSector().getEconomy().getMarketsCopy()) {
    if (x.hasIndustry("nex4x_diplomatic_embassy") && !x.getFaction().isPlayerFaction()) { m = x; break; }
}
if (m == null) { Console.showMessage("No AI embassy market found"); return; }
Console.showMessage("Using market: " + m.getName() + " (" + m.getFactionId() + ")");

// Income BEFORE disruption
nex4x.influence.InfluenceManager im = nex4x.influence.InfluenceManager.getOrCreate();
nex4x.influence.InfluenceManager.Config cfg = im.loadConfig_FOR_TEST();
float before = im.calculateCycleIncome(m.getFactionId(), cfg);
Console.showMessage("Income before disruption: " + before);

// Disrupt the embassy
m.getIndustry("nex4x_diplomatic_embassy").setDisrupted(60f, true);

// Income AFTER disruption
float after = im.calculateCycleIncome(m.getFactionId(), cfg);
Console.showMessage("Income after disruption: " + after);

// Pass if difference equals the embassy's buildingIncome value (check influence_sources.json)
Console.showMessage("Delta: " + (before - after) + " (should equal embassy buildingIncome value)");
```

**Note:** `InfluenceManager` does not expose `loadConfig_FOR_TEST()` publicly. Use the private
accessor via reflection, or simply compare before/after by triggering `runCycle()` and
checking `getIncomeBreakdown(factionId)`. Alternatively, use the simpler approach:

```
runcode
com.fs.starfarer.api.campaign.econ.MarketAPI m = null;
for (com.fs.starfarer.api.campaign.econ.MarketAPI x : Global.getSector().getEconomy().getMarketsCopy()) {
    if (x.hasIndustry("nex4x_diplomatic_embassy") && !x.getFaction().isPlayerFaction()) { m = x; break; }
}
String fid = m.getFactionId();
nex4x.influence.InfluenceManager im = nex4x.influence.InfluenceManager.getOrCreate();

// Capture pre-disruption income by running a cycle and reading breakdown
im.runCycle();
float before = im.getBalance(fid);
Console.showMessage("Balance after cycle (pre-disrupt): " + before);

// Disrupt
m.getIndustry("nex4x_diplomatic_embassy").setDisrupted(60f, true);

// Advance another cycle
im.runCycle();
float after = im.getBalance(fid);
Console.showMessage("Balance after cycle (post-disrupt): " + after);

Console.showMessage("Cycle income dropped by: " + (before - after) + " (building contribution absent)");
```

Pass criterion: the second cycle income is lower than the first by approximately the embassy
`buildingIncome` value (check `data/config/nex4x/influence_sources.json`).

## Step 4 — Check starsector.log

Search for:
- `[Nex4x] IndustrySeeder` — confirms seeding ran
- Any `NPE` or `[Nex4x] error` lines during sector generation — should be none

## Summary of what was implemented

| Task | Change |
|------|--------|
| 18a | `Nex4xConstants.PATH_INDUSTRY_SEEDING` added after `PATH_MARKET_SECURITY` |
| 18b | `data/config/nex4x/industry_seeding.json` created (embassy minSize=3, bureau minSize=4) |
| 18c | `src/nex4x/industries/IndustrySeeder.java` — static `seedAll()` with config-driven seeding |
| 18d | `Nex4xModPlugin.onNewGameAfterEconomyLoad` — `IndustrySeeder.seedAll()` call added |
| 18e | `InfluenceManager.calculateCycleIncome` — `isFunctional()` guard added to building income loop |
| 18f | `Nex4xDebugCommand.auditIndustries()` added — prints per-market embassy/bureau presence + disruption flags |
| 18g | Build green (206 files, 0 errors, 4 pre-existing `-source 7` warnings) |
