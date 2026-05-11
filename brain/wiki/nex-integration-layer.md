---
title: Nex Integration Layer
theme: Nex Integration Layer
source_files:
  - src/nex4x/integration/NexDiplomacyBridge.java
  - src/nex4x/integration/Nex4xStrategicAIConcerns.java
  - src/nex4x/integration/FactionCompatibility.java
  - src/nex4x/agents/actions/Nex4xCovertActionRegistry.java
  - src/nex4x/agents/AgentTypeMap.java
  - src/nex4x/Nex4xModPlugin.java
  - src/nex4x/managers/Nex4xManager.java
---

# Nex Integration Layer

The single integration surface between nex4x and Nexerelin. Everything
that crosses the boundary goes through here. **No nex4x code calls
`exerelin.*` directly** — it calls the bridge, which wraps in try/catch
with fallback so Nex version drift can't brick nex4x.

## NexDiplomacyBridge — central bridge

12 static methods. Each null-safe, try-catch wrapped, never throws.

### Diplomacy events
- `fireRespectEvent(declarer, target, repPct)` — friendship → "respect" event
- `fireInsultEvent(declarer, target, repPct)` — denouncement → "insult" event
- `firePeaceTreaty(a, b)` — peace_treaty stage; fallback sets rel=0 if hostile
- `fireJustifiedWar(declarer, target, cbId)` — declare_war + reverts badboy delta

### Alliances
- `ensureAlliance(factionA, factionB)` — create or find shared Alliance
- `syncCoalitionToAlliance(coalitionId, memberFids)` — multi-member sync
- `enforceNonAggression(a, b, minRelRaw)` — clamp-up relation floor

### Tribute
- `applyTributeCondition(market, receiver)` — idempotent market condition
- `removeTributeCondition(market)` — remove if present
- `applyTributeToFaction(giverFid, receiverFid)` → int — walk markets
- `removeTributeForFaction(giverFid)` → int — walk markets

### Maintenance
- `sweepDiplomacyBrains()` → int — `StrategicAI.removeAIs()` periodic call

## Registries

`Nex4xStrategicAIConcerns.register()` populates
`StrategicDefManager.CONCERN_DEFS_BY_ID` with our concerns
(`BeliefAlignmentConcern`, `GoalWrapConcern`) and our action
(`ProposeTieredAgreementAction`). Called from `onApplicationLoad`.

`Nex4xCovertActionRegistry.register()` populates
`CovertOpsManager.actionDefsById` with 7 nex4x covert action defs
(see [[agents-and-covert-ops]]). Called from `onApplicationLoad`.
Each def specifies: success/detection chances, cost, time, alert level,
specialization gate, classname. Nex's existing infrastructure handles
the rest.

`FactionCompatibility.deriveFromNexConfig` derives nex4x TendencyProfile
from Nex `NexFactionConfig`. Uses reflection on the
`diplomacyPositiveChance` / `diplomacyNegativeChance` maps (the canonical
fields Nex actually exposes).

`AgentTypeMap` maps nex4x `AgentType` ↔ Nex `AgentIntel.Specialization`.

## Boundary contract

| Domain | nex4x owns | Nex owns |
|---|---|---|
| Strategic AI | DiplomaticExecutor + GoalManager | DiplomacyBrain (disabled) |
| Alliances | Internal ladder + Coalition | `AllianceManager.Alliance` for COALITION tier |
| Agents | Companion data + 7 action defs | AgentIntel lifecycle, CovertOpsManager scheduling |
| Diplomacy events | Trigger via bridge | `DiplomacyManager.createDiplomacyEvent` |
| Market conditions | — | `TributeCondition` (we just apply/remove) |
| Tribute | One-shot demand + vassal model | `TributeCondition` persistence |
| War weariness | — | Read-only via `DiplomacyManager.getWarWeariness` |
| Casus belli | Full system | — |
| Memory | Per-event nex4x memory | `MEM_KEY_BADBOY` (reverted on CB-justified wars) |

## Mod lifecycle

`Nex4xModPlugin` overrides all six BaseModPlugin hooks:
- `onApplicationLoad` — data + config loading + registration with Nex
- `onNewGame` — placeholder
- `onNewGameAfterEconomyLoad` — seed `Nex4xManager`, load modded beliefs
- `onGameLoad(newGame)` — purge stale listeners; register transient bridges; backfill COALITION→Alliance shadows
- `beforeGameSave` — invalidate `FactionPowerRankings` cache
- `afterGameSave` / `onGameSaveFailed` — logging

`Nex4xManager` is the single sector-persisted manager. Daily `advance`
ticks all sub-managers + sweeps `DiplomacyBrain` periodically.

## Version compat

Hard-coded against Nexerelin 0.12.1d. Every Nex API call has try/catch
fallback — graceful degradation under version drift, never crash.
Verification commands: `auditNexSync()` reports registry state.

## Related

- [[diplomacy-core]] — what events are routed through the bridge
- [[agents-and-covert-ops]] — CovertActionDef registration mechanics
- [[strategic-ai]] — StrategicAI replacement boundary
