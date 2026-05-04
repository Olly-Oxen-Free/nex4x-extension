package nex4x.agreements;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.casusbelli.CasusBelliType;
import nex4x.managers.Nex4xManager;
import nex4x.ui.AgreementExpiryIntel;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Manages all active diplomatic agreements. Persisted in save via Nex4xManager.
 */
public class AgreementManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(AgreementManager.class);
    private static final float EXPIRY_WARNING_DAYS = 15f;

    private final List<Agreement> agreements = new ArrayList<Agreement>();
    /** Track which agreements have already had expiry warnings fired (by factionPair_type key). */
    private final Set<String> warnedExpiries = new HashSet<String>();

    /** Create and register a new agreement. Cancels any same-track agreement between the pair. */
    public Agreement createAgreement(String factionA, String factionB, AgreementType type) {
        return createAgreement(factionA, factionB, type, false);
    }

    /** @param viaViceroy when true, marks the agreement as negotiated via a viceroy proxy. */
    public Agreement createAgreement(String factionA, String factionB, AgreementType type,
                                     boolean viaViceroy) {
        // If alliance track, cancel existing alliance-track agreement between this pair
        if (type.isAllianceTrack()) {
            Agreement existing = getAllianceAgreement(factionA, factionB);
            if (existing != null) {
                existing.cancel();
                log.info("[Nex4x] Cancelled " + existing.getType().displayName
                        + " between " + factionA + " and " + factionB
                        + " (replaced by " + type.displayName + ")");
            }
        }

        // If trade track, cancel existing trade agreement
        if (type == AgreementType.TRADE_AGREEMENT) {
            Agreement existing = getTradeAgreement(factionA, factionB);
            if (existing != null) {
                existing.cancel();
            }
        }

        Agreement agreement = new Agreement(factionA, factionB, type, viaViceroy);
        agreements.add(agreement);

        log.info("[Nex4x] Agreement created: " + type.displayName
                + " between " + factionA + " and " + factionB);
        return agreement;
    }

    /** Get the current alliance-track agreement between two factions, or null (= Cold War). */
    public Agreement getAllianceAgreement(String factionA, String factionB) {
        for (Agreement a : agreements) {
            if (a.isActive() && a.getType().isAllianceTrack()
                    && a.involves(factionA) && a.involves(factionB)) {
                return a;
            }
        }
        return null;
    }

    /** Get the effective alliance tier between two factions. */
    public AgreementType getAllianceTier(String factionA, String factionB) {
        Agreement a = getAllianceAgreement(factionA, factionB);
        return a != null ? a.getType() : AgreementType.COLD_WAR;
    }

    /** Get trade agreement between two factions, or null. */
    public Agreement getTradeAgreement(String factionA, String factionB) {
        for (Agreement a : agreements) {
            if (a.isActive() && a.getType() == AgreementType.TRADE_AGREEMENT
                    && a.involves(factionA) && a.involves(factionB)) {
                return a;
            }
        }
        return null;
    }

    /** Get all active agreements involving a faction. */
    public List<Agreement> getAgreementsFor(String factionId) {
        List<Agreement> result = new ArrayList<Agreement>();
        for (Agreement a : agreements) {
            if (a.isActive() && a.involves(factionId)) {
                result.add(a);
            }
        }
        return result;
    }

    /** Get all active agreements of a specific type involving a faction. */
    public List<Agreement> getAgreementsOfType(String factionId, AgreementType type) {
        List<Agreement> result = new ArrayList<Agreement>();
        for (Agreement a : agreements) {
            if (a.isActive() && a.getType() == type && a.involves(factionId)) {
                result.add(a);
            }
        }
        return result;
    }

    /** Check if proposing this agreement type is valid between two factions. */
    public boolean canPropose(String factionA, String factionB, AgreementType type) {
        // Check relation threshold
        float rel = Global.getSector().getFaction(factionA)
                .getRelationship(factionB);
        if (rel < type.relationThreshold) return false;

        // Check tier ladder
        if (type.isAllianceTrack()) {
            AgreementType currentTier = getAllianceTier(factionA, factionB);
            return type.canProposeFrom(currentTier);
        }

        // Parallel track — just need relations
        return true;
    }

    /**
     * Cancel an agreement and generate diplomatic consequences:
     * - Treaty Violation CB for the other faction
     * - (Future: Oathbreaker badge, memory events)
     * @param canceller The faction initiating the cancellation
     */
    public void cancelWithConsequences(Agreement agreement, String canceller) {
        if (agreement == null || !agreement.isActive()) return;

        String otherFaction = agreement.getOtherFaction(canceller);
        agreement.cancel();

        // Generate Treaty Violation CB for the aggrieved party
        if (otherFaction != null) {
            Nex4xManager mgr = Nex4xManager.getManager();
            if (mgr != null) {
                CasusBelliManager cbMgr = mgr.getCasusBelliManager();
                cbMgr.addCB(otherFaction, canceller,
                        CasusBelliType.TREATY_VIOLATION,
                        "Broke " + agreement.getType().displayName);
            }
        }

        log.info("[Nex4x] " + canceller + " cancelled " + agreement.getType().displayName
                + " with " + otherFaction + " (consequences applied)");
    }

    /** Advance: fire expiry warnings, then prune expired/cancelled agreements. */
    public void advanceDay() {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();

        // Fire expiry warnings for player agreements approaching expiration
        for (Agreement a : agreements) {
            if (!a.isActive()) continue;
            if (!a.involves(playerFactionId)) continue;

            float remaining = a.getDaysRemaining();
            if (remaining < 0) continue; // permanent
            if (remaining > EXPIRY_WARNING_DAYS) continue;

            String otherFid = a.getOtherFaction(playerFactionId);
            String warnKey = otherFid + "_" + a.getType().name();
            if (warnedExpiries.contains(warnKey)) continue;

            // Check if there's already an active expiry intel for this agreement
            boolean alreadyWarned = false;
            for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                    .getIntel(AgreementExpiryIntel.class)) {
                AgreementExpiryIntel ei = (AgreementExpiryIntel) intel;
                if (ei.getOtherFactionId().equals(otherFid)
                        && ei.getAgreementType() == a.getType()) {
                    alreadyWarned = true;
                    break;
                }
            }

            if (!alreadyWarned) {
                AgreementExpiryIntel warning = new AgreementExpiryIntel(
                        playerFactionId, otherFid, a.getType());
                Global.getSector().getIntelManager().addIntel(warning);
                warnedExpiries.add(warnKey);
                log.info("[Nex4x] Expiry warning: " + a.getType().displayName
                        + " with " + otherFid + " (" + Math.round(remaining) + " days)");
            }
        }

        // Prune expired/cancelled agreements
        Iterator<Agreement> it = agreements.iterator();
        while (it.hasNext()) {
            Agreement a = it.next();
            if (!a.isActive()) {
                // Clean up warned key so future agreements can trigger fresh warnings
                String otherFid = a.getOtherFaction(playerFactionId);
                if (otherFid != null) {
                    warnedExpiries.remove(otherFid + "_" + a.getType().name());
                }
                it.remove();
            }
        }
    }

    /** Get all agreements about to expire within N days. */
    public List<Agreement> getExpiringWithin(float days) {
        List<Agreement> result = new ArrayList<Agreement>();
        for (Agreement a : agreements) {
            if (!a.isActive()) continue;
            float remaining = a.getDaysRemaining();
            if (remaining > 0 && remaining <= days) {
                result.add(a);
            }
        }
        return result;
    }

    public int getActiveCount() {
        int count = 0;
        for (Agreement a : agreements) {
            if (a.isActive()) count++;
        }
        return count;
    }
}
