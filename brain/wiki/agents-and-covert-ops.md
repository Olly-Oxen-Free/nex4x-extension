---
title: Agents and Covert Ops
theme: Agents and Covert Ops
source_files:
  - src/nex4x/agents/
  - src/nex4x/agents/actions/
  - src/nex4x/agents/diplomat/
  - src/nex4x/agents/security/
  - src/nex4x/agents/ai/
---

# Agents and Covert Ops

nex4x **overlays** companion data on top of Nex's `AgentIntel` system. We
never replace Nex's agent lifecycle — we listen, augment, and contribute
new action defs.

## Overlay model

`Nex4xAgentData` (keyed by `PersonAPI.id`) tracks:
- type (COVERT / GUERRILLA / DIPLOMAT — mapped from Nex Specialization)
- ownerFactionId (filled at first sighting, preserved)
- BuildupTracker (per-level standing)
- retrain state, synergy state, expulsion cooldown, cascade suspicion

`Nex4xAgentManager` is a `Sector.persistentData` singleton holding the data map.

## Specialization mapping

`AgentTypeMap.fromAgent(AgentIntel)` reads `getSpecializationsCopy()` with
priority NEGOTIATOR > HYBRID > SABOTEUR. We never invent agent types —
Nex owns canonical Specialization; nex4x AgentType is a shadow.

| Nex Specialization | nex4x AgentType |
|---|---|
| NEGOTIATOR | DIPLOMAT |
| HYBRID | GUERRILLA |
| SABOTEUR | COVERT |

## Listener

`Nex4xAgentActionReportListener implements AgentActionListener` is
registered transient on every `onGameLoad`. On `reportAgentAction`, we:
- Emit memory: `covert_operation` for agent → host
- Apply pressure: `agentFid → host` (AGENT_ACTION source)
- Update companion buildup
- Seed ownerFactionId once

## Custom action defs

`Nex4xCovertActionRegistry.register()` adds **7 nex4x-defined actions** to
`CovertOpsManager.actionDefsById` at `onApplicationLoad`:

| Action ID | Spec gate | Effect |
|---|---|---|
| `nex4x_deep_cover` | SABOTEUR/HYBRID | Detection-immunity window via synergy flag |
| `nex4x_diplomat_official_support` | NEGOTIATOR/HYBRID | Influence gain for actor, no detection |
| `nex4x_diplomat_unofficial_leak` | NEGOTIATOR/HYBRID | Pressure on host; PNG + cascade on detect |
| `nex4x_guerrilla_build_network` | HYBRID/SABOTEUR | Buildup boost; level loss on detect |
| `nex4x_guerrilla_false_flag` | HYBRID/SABOTEUR | Frames a third faction (reads `thirdFaction` param) |
| `nex4x_guerrilla_hire_mercs` | HYBRID | Stability hit + military pressure |
| `nex4x_guerrilla_incite_raid` | HYBRID/SABOTEUR | Stability dip + military pressure |

All 7 extend `Nex4xCovertAction` (which extends Nex `CovertActionIntel`).
Nex owns the lifecycle (cost, time, detection roll, success/failure); we
just supply `applyNex4xEffect` / `applyNex4xFallout` callbacks.

## Diplomat passive

`DiplomatPassiveManager.advanceDay` walks player-owned, non-retraining,
non-suspended diplomats and accrues influence based on buildup level.
`DiplomatExpulsionCascade` propagates suspicion across an actor's
diplomats when one is exposed — filtered by ownerFactionId so it
doesn't bleed onto other factions' agents.

## Security

`DetectionEngine` reads counter-espionage bonuses from market industries
(CyberSecuritySuite, DiplomaticEmbassy, IntelligenceBureau). Industries
contribute by `hasIndustry` polling — they don't override `apply()`.

## Related

- [[nex-integration-layer]] — CovertActionDef registration mechanics
- [[faction-state]] — Influence consumption by passive diplomats
- [[player-ui-surface]] — `auditAgents()` debug command
