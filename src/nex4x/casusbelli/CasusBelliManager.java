package nex4x.casusbelli;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agreements.Agreement;
import nex4x.agreements.AgreementManager;
import nex4x.agreements.AgreementType;
import nex4x.data.*;
import nex4x.declarations.Declaration;
import nex4x.declarations.DeclarationManager;
import nex4x.declarations.DeclarationType;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages Casus Belli — war justifications (spec §2.9.5).
 *
 * Two categories of CBs:
 * 1. Event-based (Treaty Violation, Retaliation, Coalition War) — stored persistently
 *    with expiry timers. Created by game events.
 * 2. Conditional (Territorial Claim, Ideological Conflict, Denouncement, Defense of Ally,
 *    Containment) — derived from current game state on query. Not stored.
 *
 * This split avoids stale data: conditional CBs always reflect the current truth.
 */
public class CasusBelliManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(CasusBelliManager.class);

    /** Containment threshold: target must have 2x the holder's military market count. */
    private static final float CONTAINMENT_POWER_RATIO = 2.0f;

    /** Stored event-based CBs only. Conditional CBs are generated on the fly. */
    private final List<CasusBelli> storedCBs = new ArrayList<CasusBelli>();

    // ── Event-based CB creation ─────────────────────────────────

    /**
     * Add an event-based CB (Treaty Violation, Retaliation, Coalition War).
     * Deduplicates: won't add if an identical active CB already exists.
     */
    public CasusBelli addCB(String holderFactionId, String targetFactionId,
                             CasusBelliType type, String details) {
        // Deduplicate
        for (CasusBelli cb : storedCBs) {
            if (cb.isActive()
                    && cb.getHolderFactionId().equals(holderFactionId)
                    && cb.getTargetFactionId().equals(targetFactionId)
                    && cb.getType() == type) {
                return cb; // already exists
            }
        }

        CasusBelli cb = new CasusBelli(holderFactionId, targetFactionId, type, details);
        storedCBs.add(cb);
        log.info("[Nex4x] CB created: " + holderFactionId + " has "
                + type.displayName + " against " + targetFactionId
                + (details != null ? " (" + details + ")" : ""));
        return cb;
    }

    // ── Queries ─────────────────────────────────────────────────

    /**
     * Get all active CBs (stored + conditional) that holder has against target.
     */
    public List<CasusBelli> getCBsAgainst(String holderFactionId, String targetFactionId) {
        List<CasusBelli> result = new ArrayList<CasusBelli>();

        // Stored event-based CBs
        for (CasusBelli cb : storedCBs) {
            if (cb.isActive()
                    && cb.getHolderFactionId().equals(holderFactionId)
                    && cb.getTargetFactionId().equals(targetFactionId)) {
                result.add(cb);
            }
        }

        // Conditional CBs — generated on the fly
        addConditionalCBs(result, holderFactionId, targetFactionId);

        return result;
    }

    /**
     * Check if holder has any valid CB against target.
     */
    public boolean hasAnyCB(String holderFactionId, String targetFactionId) {
        // Check stored first (fast)
        for (CasusBelli cb : storedCBs) {
            if (cb.isActive()
                    && cb.getHolderFactionId().equals(holderFactionId)
                    && cb.getTargetFactionId().equals(targetFactionId)) {
                return true;
            }
        }
        // Check conditionals
        return hasConditionalCB(holderFactionId, targetFactionId);
    }

    /**
     * Get the "best" (most politically acceptable) CB for display purposes.
     * Priority: Defense of Ally > Treaty Violation > Retaliation > Territorial >
     * Denouncement > Ideological > Containment > Coalition.
     */
    public CasusBelli getBestCB(String holderFactionId, String targetFactionId) {
        List<CasusBelli> all = getCBsAgainst(holderFactionId, targetFactionId);
        if (all.isEmpty()) return null;

        // Sort by priority
        CasusBelli best = all.get(0);
        for (int i = 1; i < all.size(); i++) {
            CasusBelli cb = all.get(i);
            if (cbPriority(cb.getType()) > cbPriority(best.getType())) {
                best = cb;
            }
        }
        return best;
    }

    private int cbPriority(CasusBelliType type) {
        switch (type) {
            case DEFENSE_OF_ALLY: return 8;
            case TREATY_VIOLATION: return 7;
            case RETALIATION: return 6;
            case TERRITORIAL_CLAIM: return 5;
            case DENOUNCEMENT: return 4;
            case IDEOLOGICAL_CONFLICT: return 3;
            case CONTAINMENT: return 2;
            case COALITION_WAR: return 1;
            default: return 0;
        }
    }

    /**
     * Get all stored CBs involving a faction (as holder or target).
     */
    public List<CasusBelli> getAllCBsFor(String factionId) {
        List<CasusBelli> result = new ArrayList<CasusBelli>();
        for (CasusBelli cb : storedCBs) {
            if (cb.isActive()
                    && (cb.getHolderFactionId().equals(factionId)
                    || cb.getTargetFactionId().equals(factionId))) {
                result.add(cb);
            }
        }
        return result;
    }

    // ── Conditional CB generation ───────────────────────────────

    private void addConditionalCBs(List<CasusBelli> result,
                                    String holderId, String targetId) {
        // Territorial Claim
        if (hasTerritorialClaim(holderId, targetId)) {
            result.add(new CasusBelli(holderId, targetId,
                    CasusBelliType.TERRITORIAL_CLAIM,
                    "Territorial belief conflicts with " + targetId + "'s holdings"));
        }

        // Ideological Conflict
        if (hasIdeologicalConflict(holderId, targetId)) {
            result.add(new CasusBelli(holderId, targetId,
                    CasusBelliType.IDEOLOGICAL_CONFLICT,
                    "Core beliefs in direct opposition"));
        }

        // Denouncement
        if (hasDenouncementCB(holderId, targetId)) {
            result.add(new CasusBelli(holderId, targetId,
                    CasusBelliType.DENOUNCEMENT,
                    "Active denouncement"));
        }

        // Defense of Ally
        if (hasDefenseOfAllyCB(holderId, targetId)) {
            result.add(new CasusBelli(holderId, targetId,
                    CasusBelliType.DEFENSE_OF_ALLY,
                    "Ally under attack"));
        }

        // Containment
        if (hasContainmentCB(holderId, targetId)) {
            result.add(new CasusBelli(holderId, targetId,
                    CasusBelliType.CONTAINMENT,
                    "Threatening power imbalance"));
        }
    }

    private boolean hasConditionalCB(String holderId, String targetId) {
        return hasTerritorialClaim(holderId, targetId)
                || hasIdeologicalConflict(holderId, targetId)
                || hasDenouncementCB(holderId, targetId)
                || hasDefenseOfAllyCB(holderId, targetId)
                || hasContainmentCB(holderId, targetId);
    }

    /**
     * Territorial Claim: holder has a TERRITORIAL belief and target owns markets.
     * (Simplified — full market-specific matching deferred to when territory beliefs
     * track specific market IDs.)
     */
    private boolean hasTerritorialClaim(String holderId, String targetId) {
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(holderId);
        if (beliefs == null) return false;

        FactionBeliefs.BeliefEntry territorial =
                beliefs.getStrongestInCategory(BeliefDef.Category.TERRITORIAL);
        if (territorial == null || territorial.strength < 2) return false;

        // Target must own at least one market
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (targetId.equals(m.getFactionId())) return true;
        }
        return false;
    }

    /**
     * Ideological Conflict: both factions have IDEOLOGICAL beliefs at strength 2+
     * that are opposed (different belief IDs in the same category).
     */
    private boolean hasIdeologicalConflict(String holderId, String targetId) {
        FactionBeliefs holderBeliefs = FactionBeliefsLoader.getBeliefs(holderId);
        FactionBeliefs targetBeliefs = FactionBeliefsLoader.getBeliefs(targetId);
        if (holderBeliefs == null || targetBeliefs == null) return false;

        List<FactionBeliefs.BeliefEntry> holderIdeology =
                holderBeliefs.getByCategory(BeliefDef.Category.IDEOLOGICAL);
        List<FactionBeliefs.BeliefEntry> targetIdeology =
                targetBeliefs.getByCategory(BeliefDef.Category.IDEOLOGICAL);

        for (FactionBeliefs.BeliefEntry h : holderIdeology) {
            if (h.strength < 2) continue;
            for (FactionBeliefs.BeliefEntry t : targetIdeology) {
                if (t.strength < 2) continue;
                // Different ideological beliefs = conflict
                if (!h.beliefId.equals(t.beliefId)) return true;
            }
        }
        return false;
    }

    /**
     * Denouncement CB: holder has an active Denounce against target AND the 90-day
     * CB-unlock period has elapsed. Only the declarer holds the CB.
     */
    private boolean hasDenouncementCB(String holderId, String targetId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;
        DeclarationManager declMgr = mgr.getDeclarationManager();
        Declaration d = declMgr.getDeclaration(holderId, targetId, DeclarationType.DENOUNCE);
        if (d == null) return false;
        if (!d.getDeclarerFactionId().equals(holderId)) return false;  // only declarer gets CB
        return d.isCbUnlocked();  // only after 90-day unlock
    }

    /**
     * Defense of Ally: holder has a Defensive Pact+ with a faction that is at war
     * with the target.
     */
    private boolean hasDefenseOfAllyCB(String holderId, String targetId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;
        AgreementManager agrMgr = mgr.getAgreementManager();

        // Find all factions holder has Defensive Pact+ with
        List<Agreement> agreements = agrMgr.getAgreementsFor(holderId);
        for (Agreement a : agreements) {
            if (!a.isActive()) continue;
            AgreementType type = a.getType();
            if (type.tier < AgreementType.DEFENSIVE_PACT.tier) continue;

            String allyId = a.getOtherFaction(holderId);
            if (allyId == null) continue;

            // Check if ally is at war with target
            FactionAPI ally = Global.getSector().getFaction(allyId);
            FactionAPI target = Global.getSector().getFaction(targetId);
            if (ally != null && target != null && ally.isHostileTo(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Containment: target has significantly more military markets than holder.
     */
    private boolean hasContainmentCB(String holderId, String targetId) {
        int holderMarkets = countMilitaryMarkets(holderId);
        int targetMarkets = countMilitaryMarkets(targetId);
        if (holderMarkets <= 0) return false;
        return (float) targetMarkets / holderMarkets >= CONTAINMENT_POWER_RATIO;
    }

    private int countMilitaryMarkets(String factionId) {
        int count = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) {
                count++;
            }
        }
        return count;
    }

    // ── Daily advance ───────────────────────────────────────────

    /** Prune expired/invalidated stored CBs. */
    public void advanceDay() {
        Iterator<CasusBelli> it = storedCBs.iterator();
        while (it.hasNext()) {
            CasusBelli cb = it.next();
            if (!cb.isActive()) {
                it.remove();
            }
        }
    }
}
