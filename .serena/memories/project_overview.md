# Nexerelin 4X Expansion v5 Phase 3 - Leader Dialogue

## Project Purpose
A Java mod for Starsector that extends Nexerelin with a 4X diplomacy overhaul. Adds leader personalities, reputation tiers, dialogue systems, and faction-specific leader configurations.

## Tech Stack
- **Language**: Java 1.7 (source/target)
- **Build**: bash script (build.sh) compiling with javac
- **Game**: Starsector 0.98a-RC8
- **Dependencies**: 
  - Nexerelin (0.12.1d)
  - Ashlib (2.1.2)
  - LazyLib (3.0.0)
  - Starsector base jars (starfarer.api.jar, log4j, json.jar, lwjgl.jar)

## Project Structure
```
nexerelin-4x-mod/
├── src/nex4x/
│   ├── leaders/           (Phase 1+2: Personality, ReputationTier, Situation, LeaderProfile, LeaderRegistry, DialoguePool, DialogueSelector, DialogueProvider, StaticPoolDialogueProvider, DialogueSystem)
│   ├── debug/             (Nex4xDebugCommand)
│   ├── managers/          (Nex4xManager)
│   ├── ui/                (Various UI panels)
│   ├── agents/, ai/, agreements/, and 30+ other subsystems
├── data/
│   └── config/nex4x/      (configuration JSONs)
├── jars/                  (compiled output)
├── build.sh               (main build script)
├── mod_info.json
└── .git/
```

## Build Command
`cd workspace/nexerelin-4x-mod && ./build.sh`
Expected: "BUILD SUCCESSFUL"

## Key Classes (Phase 1+2 already committed)
- `Personality`: 8 archetypes + safeValueOf()
- `ReputationTier`: 5 tiers + fromRelation() + shift()
- `Situation`: 30 dialogue situations + jsonKey/fromJsonKey
- `LeaderProfile`: Serializable, resolve(), displayName(), portraitSprite(), titleString(), getPersonality, getTraits, getFactionId
- `LeaderRegistry`: getProfile(factionId), advanceDay, LeaderChangeEvent
- `DialogueSystem`: singleton, resolve(leader, situation, tier, ctx)
- `Nex4xDebugCommand`: printLeaders(), speakGreeting()

## Code Style
- Java 1.7 compatible (no lambdas, streams, etc.)
- Follows Starsector API conventions
- Package structure: nex4x.*
- Exception handling: minimal (trust internal APIs per CLAUDE.md)
