# Tech Tree — Radial Design Spec

**Date:** 2026-04-25
**Status:** Draft (mockup committed)
**Companion:** [`2026-04-25-tech-tree-radial-mockup.html`](2026-04-25-tech-tree-radial-mockup.html)
**Origin:** Reworked from the admin radial mockup iterations (v1–v4). Pillars repurposed from Nex4x faction-philosophy (Militarist/Industrialist/Zealot/Ecologist) into a Civ-style tech-tree tri-pillar (Industry/Science/Culture).

---

## Overview

Single radial tree with four pillars positioned at the cardinals. Player starts at the center (`Foundation Tech`) and radiates outward through 4 rings of skills toward 1 of 8 capstones at the edge. Inspired by Endless Space radial structure with Civilization-style policy / government slots and tech-tree categorization.

**Why radial (not branching / Civ-grid):**
- Single visual frame; player sees all options and trade-offs at once.
- Geographic positioning encodes synergy: adjacent pillars share corner capstones; opposite pillars compete.
- Capstone choice forces commitment but allows partial cross-pillar investment.

**Why four pillars (Mil/Ind/Sci/Cul):**
- Maps to classic 4X categories: Force, Production, Knowledge, Influence.
- Military pillar covers fleet/weapons/doctrine R&D distinct from Culture's civic/garrison stability.
- Each pillar adjacent to 2 others; corner-capstones bridge each adjacency for 4 hybrids total.

---

## Geometry

- **Center:** `Foundation Tech` base node (free, gates everything).
- **4 concentric skill rings:** R1 (r=90), R2 (r=170), R3 (r=250), R4 (r=330).
- **R4 outer sub-ring:** r=370 (R4 cluster govts + R4 corner hybrid skills).
- **Capstone edge:** r=415.
- **Pillars:** Military (N, deg 0), Industry (E, deg 90), Science (S, deg 180), Culture (W, deg 270). Each occupies a 90° cone (cardinal ±45°).
- **Sub-paths:** Each pillar's cone is bisected by its cardinal axis into two 45° sub-paths.

---

## Pillars and Sub-Paths

### Military (North, deg 315–45)
- **NW half (deg 315–360) — Strategy / Doctrine:** fleet command, ECM/EW, intel, battle nets, officer doctrine. Synergizes with Culture-Stability via `Defense Compact` corner capstone.
- **NE half (deg 0–45) — Hardware / Weapons R&D:** weapon blueprints, hullmod labs, armor research, capital-ship forge tech. Synergizes with Industry-Mercantilism via `Arms Trade` corner capstone.

### Industry (East, deg 45–135)
- **NE half (deg 45–90) — Mercantilism:** trade routes, banking, currency, soft economic dominance. Builds toward credit-rich playstyle and the `Galactic Market` capstone.
- **SE half (deg 90–135) — Production / Extraction:** mining, refining, heavy industry, megastructures. Builds toward output-rich playstyle and the `Industrial Empire` capstone.

### Science (South, deg 135–225)
- **SE half (deg 135–180) — Terraforming:** atmospheric engineering, climate tuning, hazard reduction, planet-forming. Synergizes with Industry-Production via `Geo-Forge` corner capstone.
- **SW half (deg 180–225) — Ecological Production:** algae farms, hydroponics, bio-foundries, organic exports. Synergizes with Culture-Diplomacy via `Verdant Republic` corner capstone.

### Culture (West, deg 225–315)
- **SW half (deg 225–270) — Cultural / Diplomacy:** embassies, soft power, sector concert, treaties. Synergizes with Science-Ecological via `Verdant Republic`.
- **NW half (deg 270–315) — Stability / Civic Defense:** garrisons, marines, walls, civic order. Synergizes with Military-Strategy via `Defense Compact` corner capstone.

---

## Skill Density

Each pillar's cone contains:

| Ring | Skills per cone | Per sub-path |
|------|----------------|--------------|
| R1   | 2              | 1 each       |
| R2   | 4              | 2 each       |
| R3   | 6              | 3 each       |
| R4   | 8              | 4 each       |

**Total skills:** 80 (4 cones × 20 skills each).

Skills are distributed evenly within each cone via sub-arc bisection: for `N` skills in a 90° cone, each skill occupies a sub-arc of `90/N` degrees, placed at the sub-arc center. This gives clean visual spacing that scales with density.

---

## Government Nodes (Squares)

Governments are slot-based — the player keeps **max 2 active** at any time, swappable at a market dock for an SP cost (or free with a designated capital colony). Inactive governments don't apply.

### R2 Cardinal Govts (4 total — foundational forms)
| Position | Name              | Effect (sketch)                                                                              |
|----------|-------------------|----------------------------------------------------------------------------------------------|
| N (Mil)  | General Staff     | Officer XP 2x; fleet doctrine +1 point; combat win restores +1 morale empire-wide.            |
| E (Ind)  | Corporate Charter | +20% export income; own-faction tariffs at 50%; passive stock income.                         |
| S (Sci)  | Technocracy       | AI-core admin slots count 1.5x effective; tech-heavy industries +1 tier; Pather rate doubled. |
| W (Cul)  | Civic Republic    | +2 stab empire-wide; diplomatic actions +25%; garrison upkeep -25%.                           |

### R4 Advanced Cluster Govts (12 total — 3 per cardinal cluster)
Each pillar gets a left-flavor / center-neutral / right-flavor triplet, biased toward the pillar's two sub-paths.

**Military cluster:** War College (Strategy flavor) · Junta (center) · Arms Industry (Hardware flavor)
**Industry cluster:** Trade League (Mercantilism flavor) · Megacorp (center) · Industrial State (Production flavor)
**Science cluster:** Terra Authority (Terraforming flavor) · Technate (center) · Biocracy (Ecological flavor)
**Culture cluster:** Diplomatic League (Diplomacy flavor) · Cultural Hegemon (center) · Stratocracy (Stability flavor)

R4 govts sit on a sub-ring at r=370 (outside R4 skills) so they don't collide visually.

---

## Hybrid Skills (R4 Corners — 4 total)
Circles, not squares. Sit at quadrant boundaries on R4 outer sub-ring. They gate the corner capstones and bridge adjacent pillars.

| Corner | Position    | Name              | Bridges                                     |
|--------|-------------|-------------------|---------------------------------------------|
| NE     | deg 45      | Arms Market       | Military-Hardware × Industry-Mercantilism.  |
| SE     | deg 135     | Geo-Engineer      | Industry-Production × Science-Terraforming. |
| SW     | deg 225     | Verdant Federation| Science-Ecological × Culture-Diplomacy.     |
| NW     | deg 315     | Defense Compact   | Culture-Stability × Military-Strategy.      |

---

## Capstones (8 — Diamonds)

4 pure-pillar capstones at cardinals + 4 corner hybrid capstones (one bridging each adjacent pillar pair).

### Cardinal Capstones
| Position | Capstone            | Identity                                                                                                                        |
|----------|---------------------|---------------------------------------------------------------------------------------------------------------------------------|
| N        | Naval Hegemony      | Fleet doctrine +3 empire-wide; HI colonies produce free combat capital ship/cycle; war-decl auto-intel; flagship cmd range +50%. |
| E        | Industrial Empire   | +1 industry slot per colony; megastructures unlocked; HI ships at +1 quality without AI cores.                                   |
| S        | Tech Singularity    | Research Lab industry (passive blueprint discovery); Garden-class terraforming; Singularity Drive system-wide industry bonus.    |
| W        | Cultural Hegemony   | Lingua franca: NPC factions adopt your laws/tech (passive influence); Sector Council seat; pirates can't recruit your population. |

### Corner Capstones
| Position | Capstone           | Identity                                                                                                          |
|----------|--------------------|-------------------------------------------------------------------------------------------------------------------|
| NE       | Arms Trade Empire  | Sell military blueprints + ship hulls to ALL factions (incl. hostiles); +50% credits from arms; +10% tension w/ majors. |
| SE       | Geo-Forge Combine  | Exploit-industries (Mining/Refining/HI) ALSO terraform; Mantle-tier industries unlocked.                          |
| SW       | Verdant Republic   | Garden Worlds = diplomatic capitals (+5 rep with all majors per Garden); Bio-Sphere Protection sector treaty.     |
| NW       | Defense Compact    | Bastion-class colonies + treaty web w/ up to 3 NPC factions auto-trigger reinforcements when raided; defensive war only. |

### Capstone Gating
- **Cardinal capstones:** require 2 R4 skills closest to the cardinal axis in the same pillar.
- **Corner capstones:** require their R4 hybrid skill + 1 R4 skill from each adjacent pillar (true cross-pillar synergy gate, applies uniformly across all 4 corners now that Military fills the N cone).

Picking a capstone **locks all other capstones permanently**. Cross-pillar partial investment is encouraged before commitment; capstone is the endgame doctrine commitment.

---

## Linking / Progression

- **Base → R1:** every R1 skill is reachable directly from the base node.
- **Ri → Ri+1:** each Ri skill connects to its 2 nearest Ri+1 skills in the same pillar (radial fan-out).
- **R2 govt → R1+R2 skills:** R2 cardinal govts anchor inward to R1 skills in their pillar and outward to nearest R2 skills.
- **R4 govt → R4 skills:** R4 cluster govts connect to nearest 2 R4 skills in their pillar.
- **R4 hybrid → R4 skills:** R4 hybrids connect to nearest R4 skill in each adjacent pillar.
- **R4 → capstone:** see "Capstone Gating" above.

---

## Open Questions

1. **Military pillar overlap with Culture-NW:** Culture-NW is now "Stability/Civic Defense" (garrisons, ground forces, civic order). Military pillar handles all fleet/weapons/doctrine R&D. Acceptable separation, or merge into one Mil-leaning pillar?
2. **SP economy:** how many SP available across a campaign? Need budget so a typical run reaches 1 capstone but only ~30–50% of skill nodes.
3. **Govt swap cost:** SP per swap, free at capital, or limited reroll currency?
4. **Capstone exclusivity strength:** hard lock, or soft "−5 SP per other capstone attempted"?
5. **Sub-path interaction:** can a player invest only in Mercantilism (skip Production) and still reach Industry capstone? Probably yes, since both sub-paths feed R4. Worth confirming.
6. **Corner capstone identities:** all 4 corners are now true hybrids (Arms Trade, Geo-Forge, Verdant Republic, Defense Compact). Validate that the bridges feel thematically right — especially Defense Compact (Mil-Strategy × Cul-Stability) which could feel redundant if both pillars already lean defensive.
7. **R4 govt cluster overlap:** govts at deg ±12° from cardinal sit close to R4 skills at deg ±5.625° and ±16.875°. Visual sub-ring at r=370 keeps them separated but they may still feel crowded. Consider: 2 govts per cluster instead of 3?
8. **Detailed flavor pass:** mockup uses placeholder one-liners for ~50 of 60 skills. Spec needs full skill text per node before implementation.

---

## Future Work

- Full skill flavor pass for all 80 skill nodes + 12 R4 govts (4 R2 govts done)
- 8 capstone full effect specs (lock-out rules, mod-mod interactions w/ TASC, IndEvo, Nex)
- UI/UX wireframe (hover tooltips, locked/owned/pickable state, pre-pick path preview)
- Save format for govt slots + capstone selection (Nex-compatible if cross-faction?)
- Story/prereq hooks (does this tree gate behind reading planet-survey reports? in-game research projects?)
