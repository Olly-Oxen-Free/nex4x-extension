---
title: Player UI Surface
theme: Player UI Surface
source_files:
  - src/nex4x/ui/
  - src/nex4x/debug/
  - src/nex4x/util/Nex4xRelations.java
  - src/nex4x/util/Nex4xClock.java
---

# Player UI Surface

What the player actually sees + the runcode entrypoints used for
in-game smoke testing.

## NegotiationPanel

`nex4x.ui.NegotiationPanel` extends Ashlib `BasePopUpDialog`. Singleton-
guarded (try/catch around `popUpDialog` clears `activeInstance` on
throw). Opens via `openScaled(targetFactionId, viceroyMode)`.

Layout:
- Header: leader portrait + dialogue line + ⚖ Auto-Balance button
- Center: balance bar (`BalanceBarPlugin` with center-origin GL11 fill)
- Catalog: per-`NegotiableItemType` accordion. Each expanded category
  shows concrete add-buttons rendered from
  `NegotiableItemCatalog.idsForType(type, atWar, targetFid)`.
- Deal table: two columns (YOUR OFFER / YOUR REQUESTS)
- Declarations section

Refresh pattern: `advance()` checks `needsRefresh`; clears children of
`panelToInfluence` directly (no `removeUI` which would detach parent),
then re-calls `createUI`. Mirrors `CoreUITabInjectorListener.refresh`.

## PeaceConferenceDialog

`nex4x.ui.PeaceConferenceDialog` is the minimal dialog for war
resolution. Shows attacker/defender names, status, current terms,
and 3 buttons:
- Accept Terms → `pc.accept()` + fires Nex peace_treaty via bridge
- Reject Terms → `pc.reject()`
- Counter Offer → `pc.counterOffer(currentTerms)` (v1 placeholder; full
  term editor deferred)

Singleton-guarded same as NegotiationPanel.

## Diplomacy intel overlay

`DiplomacyTabOverlayModel` + `DiplomacyOverlayPlugin` render a custom
overlay on top of the Intel tab when the Diplomacy section is selected.
`CoreUITabInjectorListener` listens for `INTEL` tab opens and injects
the overlay (with stale-overlay removal to prevent stacking on
repeated open/close).

`DiplomacyIntel` uses `IntelSortTier.TIER_1` for top-of-list placement
(not the old "AAA_..." sort-string hack).

## Per-event intel

- `WarDeclarationIntel` — leader portrait + dialogue line on war declaration
- `AIProposalIntel` — leader portrait + situation line on AI deal proposals
- `BadgeReactionIntel` — leader reaction when a badge is earned
- `LeaderChangeIntel` — fired on `POST_FACTION_LEADER` reassignment

All consume `LeaderRegistry` + `DialogueSystem` + `ReputationTier` to
generate flavor.

## Util helpers

`Nex4xRelations` is the single point of conversion between Nex's
raw -1..1 relation and the percent (-100..100) scale used in nex4x
config + UI labels. See [[time-and-relations]].

`Nex4xClock` provides `now()` (long timestamp), `daysSince(ts)`,
`currentAbsoluteDay()`, and `isLegacyDayValue(v)` for save migration.

## Runcode smoke commands

All in `nex4x.debug.Nex4xDebugCommand`:

| Command | Tests |
|---|---|
| `printLeaders()` | Leader registry populated |
| `speakGreeting(fid)` | DialogueSystem + ReputationTier integration |
| `openLeader(fid)` | LeaderAccessGate + NegotiationPanel |
| `openPeaceConference(a, d)` | PeaceConferenceDialog |
| `proposeMediation(m, a, b, inf)` | MediationManager.propose |
| `declareFriendship(target)` / `denounce(target)` | Declarations + Nex events |
| `smokeTest()` | v5 leader-dialogue compile-path |
| `auditRels()` | Relation scale (Nex4xRelations) |
| `auditClock()` | Time-unit migration |
| `auditInit()` | Sub-manager init state |
| `auditAgents()` | Companion data + CovertOpsManager def count |
| `auditDiplomacy()` | DiplomacyIntel + coalition shadow coverage |
| `auditNexSync()` | CB coverage + alliance shadow + floor compliance |
| `auditTribute()` | Markets with TributeCondition |
| `auditUI()` | Catalog id count per NegotiableItemType |

## Related

- [[diplomacy-core]] — what UI surfaces
- [[nex-integration-layer]] — `auditNexSync` validates bridge state
- [[agents-and-covert-ops]] — `auditAgents` validates Nex registry
