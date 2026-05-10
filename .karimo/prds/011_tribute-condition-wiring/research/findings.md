# Research Findings — TributeCondition Wiring

## Source
Post-PRD-009 audit. Two persistent-tribute paths bypass Nex's `TributeCondition` system.

## Problem
1. `DemandManager.applyDemandEffect` TRIBUTE_CREDITS pays once and ends — Nex's
   `TributeCondition` is the canonical persistent-tribute mechanism. Player UI doesn't
   show "this market pays tribute".
2. `VassalManager.estimateFactionIncome` computes potential tribute but doesn't actually
   apply it — vassal markets show no tribute condition; Nex's economy UI hides the
   relationship.

## Nex API
```
javap exerelin.campaign.econ.TributeCondition
  public static final String CONDITION_ID;       // "nex_tribute" or similar
  public static final int MAX_SIZE;
  protected FactionAPI faction;
  protected TributeIntel intel;
  public void setup(FactionAPI, TributeIntel);
  public static float getIncomePenalty();
  public static float getImmigrationMult();

javap exerelin.campaign.intel.diplomacy.TributeIntel    // for the persistent intel side
```

Application pattern (Nex internal):
- `market.addCondition(TributeCondition.CONDITION_ID)`
- Then `((TributeCondition) market.getCondition(...).getPlugin()).setup(receiverFaction, tributeIntel)`

`TributeIntel` is the IntelInfoPlugin tracking ongoing tribute; we'll need to instantiate
or fetch one per tribute relationship.

## Affected sites
- `src/nex4x/demands/DemandManager.java` — TRIBUTE_CREDITS branch (lines ~83-92)
- `src/nex4x/vassals/VassalManager.java` — vassalize path (creates VassalRelation; should
  also apply TributeCondition to vassal markets)
- `src/nex4x/integration/NexDiplomacyBridge.java` — extend with helpers

## Approach

### Item 1: TRIBUTE_CREDITS demand
Two interpretations:
- (A) One-shot only (current) — keep credit grant, add `TributeCondition` to a single
  representative market for the duration parsed from payload (e.g. "5000:cycle:hegemony_market_id").
- (B) Persistent — TRIBUTE_CREDITS demand applies tribute to ALL markets owned by the target
  faction; payload gives duration.

Going with (B) for "persistent tribute" semantics. Demand payload format:
`<credits-per-cycle>` (existing) plus optional `:<duration-days>` suffix. Default duration:
360 days (one cycle).

### Item 2: Vassal markets
On `vassalize(overlordId, vassalId, tier)`: walk all markets owned by vassalId, apply
TributeCondition with overlordId as receiver. On `freeVassal(vassalId)`: remove conditions.
Daily advance: ensure conditions still match (vassal markets may flip ownership).

### Helpers in NexDiplomacyBridge

```
static void applyTributeCondition(MarketAPI market, FactionAPI receiver, float days)
static void removeTributeCondition(MarketAPI market)
static void applyTributeToFaction(String giverFid, String receiverFid, float days)  // walks markets
static void removeTributeForFaction(String giverFid, String receiverFid)
```

All wrap Nex calls in try/catch; never throw; log success/failure.

## Risks
- TributeCondition may already exist on a market (player conquered, applied tribute);
  applyTributeCondition must be idempotent — check `market.hasCondition(CONDITION_ID)` first.
- Removing conditions on faction-owned markets that flipped: vassal markets that get
  conquered should not retain tribute to old overlord.
- `TributeIntel` instantiation — if Nex requires one per relationship, we need a managed map.

## Out of scope
- Replacing nex4x's internal vassal economic-income calc with Nex tribute (separate audit).
- Player-applied tribute via UI (negotiation panel) — follow-on.
