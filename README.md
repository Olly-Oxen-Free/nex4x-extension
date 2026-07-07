# Nexerelin 4X Expansion (nex4x)

Diplomacy, leadership and 4X-flavored mechanics layered **on top of**
[Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175.0)
for [Starsector](https://fractalsoftworks.com/).

nex4x doesn't replace Nex — it **enhances** it. Wherever Nex already
ships a mechanic (alliances, agents, war/peace events, tribute markets),
nex4x routes through Nex's APIs. Where Nex is silent (casus belli,
agreement tiers, leader personalities, faction memory, policies), nex4x
adds a system.

## Requirements

- Starsector 0.97+
- [Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175.0) 0.12.1d
- [Ashlib](https://fractalsoftworks.com/forum/index.php?topic=29030.0)
- [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444)
- [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25813.0) (optional)
- [WrapUI](https://fractalsoftworks.com/forum/index.php?topic=29030.0)

## Features

### Diplomacy ladder
Five-tier alliance progression beyond Nex's binary war/peace:

```
COLD_WAR → NAP → DEFENSIVE_PACT → MILITARY_PARTNERSHIP → COALITION
                          ECONOMIC_PARTNERSHIP (parallel)
                          TRADE_AGREEMENT (parallel)
```

Each tier enforces a relation floor; COALITION materializes as a real
Nex `Alliance` (with multi-member sync, voting, dissolution).

### Casus Belli
Wars need a reason. Casus belli types (TERRITORIAL_LOSS, MILITARY_INSULT,
IDEOLOGICAL_CONFLICT, BELIEF_DENOUNCED, OATH_BROKEN, CONTAINMENT,
DENOUNCEMENT, DEFENSE_OF_ALLY) suppress Nex's warmonger badboy when
present — justified wars don't ruin your reputation.

### Leaders + personalities
Every faction has a leader with one of 8 personalities (MERCANTILE,
ZEALOUS, HAUGHTY, RUTHLESS, PARANOID, GENIAL, CAPRICIOUS, PRAGMATIC).
Personalities affect deal valuation, dialogue, and reputation tiers.

### Custom covert ops (Nex-registered)
Seven new agent actions appear in Nex's standard agent orders dialog:

| Action | Specialization | Effect |
|---|---|---|
| Deep Cover | SABOTEUR/HYBRID | Temporary detection-immunity |
| Diplomatic Support | NEGOTIATOR/HYBRID | Influence gain, no detection |
| Unofficial Leak | NEGOTIATOR/HYBRID | Pressure; PNG if exposed |
| Build Local Network | HYBRID/SABOTEUR | Heavy buildup boost |
| False-Flag Attack | HYBRID/SABOTEUR | Frame a third faction |
| Hire Mercenaries | HYBRID | Stability hit + pressure |
| Incite Raid | HYBRID/SABOTEUR | Stability dip + pressure |

### Memory + Politics
Per-event diplomatic memory keyed by (holder, about, type) with
faction-trait-scaled decay (IRREDENTIST never forgets territorial,
FOREVERWAR never forgets military). Dynamic political modifiers shift
tendency profiles in response to events.

### Influence + Pressure
Two new soft currencies. **Influence** (cyclic, per-faction): generated
by market size + DiplomaticEmbassy / IntelligenceBureau buildings; spent
on demand issuance, mediation, declaration discounts. **Pressure**
(bilateral, asymmetric): accumulates from grievances, agent actions,
military buildup; consumed to issue demands.

### Demands + Mediation
Formal demands (`TRIBUTE_CREDITS`, `CEDE_MARKET`, `BREAK_ALLIANCE`,
`END_WAR`) cost pressure + influence; acceptance fires real Nex
effects via the integration bridge. Third-party mediation closes wars
without conquest.

### Policies + Vassals + Coalitions
Each faction adopts one policy at a time (10 options). Overlord/vassal
relationships apply persistent Nex `TributeCondition` to vassal markets.
Multi-faction coalitions vote on shared decisions with quorum + expiry.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          Player UI                              │
│   NegotiationPanel · PeaceConferenceDialog · DiplomacyIntel    │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────┴───────────────────────────────────┐
│                      nex4x systems                              │
│                                                                 │
│  Diplomacy Core (agreements, declarations, demands, mediation, │
│  peace, votes)                                                 │
│                                                                 │
│  Strategic AI (grand strategy, goal manager, executor)         │
│                                                                 │
│  Agents (overlay on Nex AgentIntel + 7 new actions)            │
│                                                                 │
│  Conflict (casus belli, war scores, badges)                    │
│                                                                 │
│  Faction State (leaders, coalitions, vassals, memory,          │
│  influence, pressure, policies, contracts, industries)         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  NexDiplomacyBridge                             │
│   12 static helpers: every Nex API call wrapped + fallback     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Nexerelin                                 │
│   DiplomacyManager · AllianceManager · CovertOpsManager        │
│   StrategicDefManager · SectorManager · TributeCondition       │
└─────────────────────────────────────────────────────────────────┘
```

**Boundary**: nex4x **owns** strategic AI, agreements (NAP/DefPact/Mil/
Coalition), casus belli, leaders, influence, pressure, vassals,
wargoals, memory, declarations, policies. Nex **owns** alliances, agent
lifecycle, diplomacy events, market conditions, tribute persistence,
war weariness. Both sides see each other through `NexDiplomacyBridge`.

See [`brain/wiki/`](brain/wiki/) for the full architecture documentation
(Obsidian-readable knowledge base).

## Building

```bash
./build.sh
```

Compiles all `src/nex4x/**/*.java` against Nex + Ashlib + LazyLib + WrapUI
classpaths and produces `jars/Nex4xExpansion.jar`.

## Smoke testing

In-game via Console Commands `runcode`:

```
runcode nex4x.debug.Nex4xDebugCommand.auditNexSync();
runcode nex4x.debug.Nex4xDebugCommand.auditAgents();
runcode nex4x.debug.Nex4xDebugCommand.auditTribute();
```

Full list in [`brain/wiki/player-ui-surface.md`](brain/wiki/player-ui-surface.md).

## Development

- Workspace docs: [`brain/wiki/`](brain/wiki/) — Obsidian vault
- Audit report: [`audit/AUDIT_REPORT.md`](audit/AUDIT_REPORT.md)
- KARIMO PRDs: [`.karimo/prds/`](.karimo/prds/) (12 PRDs documenting all changes)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)

## License

See base mod license terms.
