---
title: Conflict System
theme: Conflict System
source_files:
  - src/nex4x/casusbelli/
  - src/nex4x/wargoals/
  - src/nex4x/badges/
---

# Conflict System

How wars are justified, scored, and how the mod tags reputational
consequences. The conflict system is **alongside** Nex's war/peace — we
inject explicit casus belli and per-war tracking that Nex doesn't model,
while letting Nex own the actual war state.

## Casus Belli

`nex4x.casusbelli.CasusBelliType` defines reasons for war:

- Stored types: `TERRITORIAL_LOSS`, `MILITARY_INSULT`, `IDEOLOGICAL_CONFLICT`,
  `BELIEF_DENOUNCED`, `OATH_BROKEN`
- Computed types: `CONTAINMENT` (target's military markets exceed holder ratio),
  `DENOUNCEMENT` (active Denounce + 90d unlock), `DEFENSE_OF_ALLY`

`CasusBelliManager.getActiveCasusBelliFor(holder, target)` returns the
first active CB. Military-market count uses `MarketAPI.hasIndustry` filter
for MILITARYBASE / HIGHCOMMAND / PATROLHQ.

CB recognition surfaces at war declaration: `DiplomaticExecutor` and
`DeclareWarFromDesireAction` route through
`NexDiplomacyBridge.fireJustifiedWar(declarer, target, cbId)` when a CB
is present. The bridge snapshots `DiplomacyManager.MEM_KEY_BADBOY` before
the event, then reverts the delta after — so justified wars don't accrue
warmonger reputation.

## War Goals

`nex4x.wargoals.WarGoal` is created when war is declared, resolved at peace.
States: active / achieved (scopeProgress ≥ 1) / abandoned / obsolete.
Obsolete triggers when target market is destroyed/decivilized
(checked daily by `WarScoreTracker.advanceDay`).

Types: `TERRITORIAL_CLAIM` (specific market), `MILITARY_DEFEAT`,
`POLITICAL_HUMILIATION`, `IDEOLOGICAL_VICTORY`, `ECONOMIC_SUBJUGATION`.

## War Score

`WarScoreTracker` keys scores by canonical pair (alphabetical-first faction
gets positive). Adjustments:
- Battle win/loss: ±5 / ±3
- Market capture/loss: ±20 / ±15

Score feeds `DesperationCalculator.getWarScoreContribution`: +5 per −10
war score per active hostile pair, capped at +25.

## Badges

`nex4x.badges.BadgeType` tracks reputational tags:
- Negative (360-day decay): OATHBREAKER, WARMONGER, AGGRESSOR, BETRAYER
- Positive (permanent): RELIABLE_PARTNER, PEACEMAKER

Each has a `positive` boolean field used by `BadgeReactionIntel` to pick
positive/negative leader-reaction dialogue. `FactionBadges.progress`
counter resets on earn so the threshold mechanic re-applies on next cycle.

## Related

- [[strategic-ai]] — CBs influence goal feasibility
- [[diplomacy-core]] — declarations unlock denouncement CBs
- [[nex-integration-layer]] — `fireJustifiedWar` mechanics
