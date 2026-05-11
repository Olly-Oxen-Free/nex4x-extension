# Research Findings — Unify Relation Scale

## Source
Derived from `audit/AUDIT_REPORT.md` (THEME-A), 2026-05-10.

## Problem
`com.fs.starfarer.api.campaign.FactionAPI.getRelationship(String)` returns a float in `[-1.0, +1.0]`. Multiple call sites in nex4x compare this raw value against thresholds expressed in `[-100, +100]` — primarily `10/20/40/50/60/80` and `-50/-70`.

Result: every faction classifies into the most-permissive bucket (HOSTILE for `ReputationTier`, gold "neutral" for intel relColor, alliance never proposable).

## Affected sites (verified via Edit + audit transcript)
- `src/nex4x/leaders/ReputationTier.java:18` — thresholds `-50/-25/10/50` (P0). Called from 8 sites.
- `src/nex4x/ui/NegotiationPanel.java:748,797,798` — passes raw `getRelationship` to `ReputationTier`; `relationBadge` uses `Math.round(rel)` and shows "+0" for everyone.
- `src/nex4x/ui/intel/WarDeclarationIntel.java:39`, `AIProposalIntel.java:174`, `BadgeReactionIntel.java:52`
- `src/nex4x/evaluation/Valuator.java:40`
- `src/nex4x/debug/Nex4xDebugCommand.java:36`
- `src/nex4x/ui/DiplomacyIntel.java:200` — `relColor`/`repLabel` thresholds wrong scale.
- `src/nex4x/ui/DiplomacyTabPlugin.java:134` — same.
- `src/nex4x/ui/CoreUITabInjectorListener.java:179` — same (likely dead, also has overlay leak).
- `src/nex4x/agreements/AgreementType.java:14` — `relationThreshold` 10..60 vs raw rel — alliance proposals never pass `canPropose`.
- `src/nex4x/agreements/AgreementManager.java:119-121` — caller of above; also has unguarded null on `getFaction`.
- `src/nex4x/leaders/LeaderAccessGate.java:18` — uses raw rel against threshold; convention drift vs `data/config/.../declarationConfig.json` which uses 0..100.

## Sites already using correct convention (do not break)
- `src/nex4x/ui/DiplomacyTabOverlayModel.java:248` — uses `0.5f / -0.5f` correctly.
- `src/nex4x/ui/DiplomacyTabOverlayModel.java:421` — same.
- `src/nex4x/declarations/DeclarationConfig.java` — JSON-loaded thresholds expressed in 0..100, divided by 100 at use.

## API verification
```
$ javap -classpath ~/Games/Starsector/starfarer.api.jar com.fs.starfarer.api.campaign.FactionAPI | grep -i relationship
  public abstract float getRelationship(java.lang.String);
  public abstract com.fs.starfarer.api.campaign.RepLevel getRelationshipLevel(java.lang.String);
  public abstract void adjustRelationship(java.lang.String, float);
  ...
```
RepLevel buckets (from API): VENGEFUL(-0.75), HOSTILE(-0.5), INHOSPITABLE(-0.25), SUSPICIOUS(-0.1), NEUTRAL(0), FAVORABLE(0.1), WELCOMING(0.25), FRIENDLY(0.5), COOPERATIVE(0.75) — all in -1..1.

## Recommended approach
1. Introduce `src/nex4x/util/Nex4xRelations.java` with two surface APIs:
   - `static float toPercent(float rel)` — multiplies by 100, returns -100..100.
   - `static int toPercentInt(float rel)` — rounds for display.
   - `static boolean atLeastPct(float rel, float pct)` — semantic compare.
   - `static RepLevel fromRel(float rel)` — wraps `RepLevel.getLevelFor` for convenience.
2. Migrate every flagged callsite. Two strategies — pick per file:
   - **Display**: `int pct = Nex4xRelations.toPercentInt(rel)` then format.
   - **Threshold compare**: replace numeric literals with `Nex4xRelations.atLeastPct(rel, 50f)`.
3. Centralize `ReputationTier` to take percent input (callers convert) — single source of truth.
4. JSON-loaded configs (`DeclarationConfig`) keep 0..100 convention; raw API rel is converted at boundary.
5. Add a smoke test via `Nex4xDebugCommand`: print `(rel, pct, tier)` for each pair of factions.

## Out of scope (separate PRDs)
- Bilateral `adjustRelationship` 2× delta in DeclarationManager (PRD-F)
- Overlay leak / class shadow in `ui/` (PRD-E)
- `Nex4xDebugCommand:36` itself only changes via callsite migration

## Risks
- Breaking convention silently in any missed callsite → compounds the existing bug.
- JSON data authors may have written thresholds against the wrong scale; verify `data/config/nex4x/*` after migration.
- Save compatibility: no persistent fields touch raw rel; safe.
