package nex4x.agreements;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.casusbelli.CasusBelliType;
import nex4x.integration.NexDiplomacyBridge;
import nex4x.managers.Nex4xManager;
import nex4x.ui.AgreementExpiryIntel;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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

        if (type == AgreementType.COALITION) {
            try {
                // Collect all coalition members (existing + the two new parties) and sync.
                List<String> members = getCoalitionMembers(factionA, factionB);
                String coalitionId = buildCoalitionId(members);
                NexDiplomacyBridge.syncCoalitionToAlliance(coalitionId, members);
            } catch (Throwable t) {
                log.warn("[Nex4x] Coalition alliance sync (add): " + t.getMessage(), t);
            }
        }

        float floor = relationFloorFor(type);
        if (floor >= 0f) {
            try {
                nex4x.integration.NexDiplomacyBridge.enforceNonAggression(factionA, factionB, floor);
            } catch (Throwable t) {
                log.warn("[Nex4x] Relation floor bridge: " + t.getMessage());
            }
        }

        log.info("[Nex4x] Agreement created: " + type.displayName
                + " between " + factionA + " and " + factionB);
        return agreement;
    }

    /** Returns an immutable view of all agreements (active and inactive). Used by save migration. */
    public java.util.List<Agreement> getAllAgreements() {
        return Collections.unmodifiableList(agreements);
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
        com.fs.starfarer.api.campaign.FactionAPI fa = Global.getSector().getFaction(factionA);
        if (fa == null) return false;
        float rel = fa.getRelationship(factionB);
        if (!nex4x.util.Nex4xRelations.atLeastPct(rel, type.relationThreshold)) return false;

        // Check tier ladder
        if (type.isAllianceTrack()) {
            AgreementType currentTier = getAllianceTier(factionA, factionB);
            return type.canProposeFrom(currentTier);
        }

        // Parallel track — just need relations
        return true;
    }

    /**
     * Returns the set of all faction ids currently in a coalition that includes both
     * {@code factionA} and {@code factionB}. The result always contains at least both
     * arguments. Uses active COALITION agreements to discover transitive members.
     */
    public List<String> getCoalitionMembers(String factionA, String factionB) {
        Set<String> members = new LinkedHashSet<String>();
        members.add(factionA);
        members.add(factionB);
        // Expand: for each member already found, collect their active COALITION partners
        Set<String> toProcess = new LinkedHashSet<String>(members);
        while (!toProcess.isEmpty()) {
            String next = toProcess.iterator().next();
            toProcess.remove(next);
            for (Agreement a : agreements) {
                if (!a.isActive() || a.getType() != AgreementType.COALITION) continue;
                if (!a.involves(next)) continue;
                String partner = a.getOtherFaction(next);
                if (partner != null && members.add(partner)) {
                    toProcess.add(partner);
                }
            }
        }
        return new ArrayList<String>(members);
    }

    /** Derive a stable coalition id string from a sorted member list (for logging). */
    private static String buildCoalitionId(List<String> members) {
        List<String> sorted = new ArrayList<String>(members);
        java.util.Collections.sort(sorted);
        StringBuilder sb = new StringBuilder("coalition");
        for (String m : sorted) sb.append('_').append(m);
        return sb.toString();
    }

    /**
     * If the given agreement is a COALITION that just became inactive, remove the
     * departing faction from its Nex Alliance shadow if it has no remaining COALITION
     * agreements. Called after {@link Agreement#cancel()} or expiry pruning.
     */
    private void maybeSyncCoalitionLeave(Agreement agreement) {
        if (agreement.getType() != AgreementType.COALITION) return;
        String fidA = agreement.getFactionIdA();
        String fidB = agreement.getFactionIdB();
        // For each side: if they have no remaining active COALITION agreements, leave alliance.
        for (String fid : new String[]{fidA, fidB}) {
            boolean hasOtherCoalition = false;
            for (Agreement a : agreements) {
                if (a == agreement) continue;
                if (a.isActive() && a.getType() == AgreementType.COALITION && a.involves(fid)) {
                    hasOtherCoalition = true;
                    break;
                }
            }
            if (!hasOtherCoalition) {
                try {
                    Alliance alliance = AllianceManager.getFactionAlliance(fid);
                    if (alliance != null) {
                        AllianceManager.getManager().leaveAlliance(fid, alliance, false, false);
                        log.info("[Nex4x] Coalition leave: " + fid
                                + " left Nex alliance " + alliance.getName());
                    }
                } catch (Throwable t) {
                    log.warn("[Nex4x] Coalition alliance sync (remove) for " + fid
                            + ": " + t.getMessage(), t);
                }
            }
        }
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
        maybeSyncCoalitionLeave(agreement);

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

        // Re-apply relation floors to active alliance-track agreements (catches Nex daily flux)
        for (Agreement a : agreements) {
            if (!a.isActive()) continue;
            float floor = relationFloorFor(a.getType());
            if (floor >= 0f) {
                try {
                    nex4x.integration.NexDiplomacyBridge.enforceNonAggression(
                            a.getFactionIdA(), a.getFactionIdB(), floor);
                } catch (Throwable t) {
                    log.warn("[Nex4x] Daily relation floor: " + t.getMessage());
                }
            }
        }

        // Prune expired/cancelled agreements
        Iterator<Agreement> it = agreements.iterator();
        while (it.hasNext()) {
            Agreement a = it.next();
            if (!a.isActive()) {
                // Sync coalition leave before removal so we can still check remaining agreements
                maybeSyncCoalitionLeave(a);
                // Clean up warned key so future agreements can trigger fresh warnings
                String otherFid = a.getOtherFaction(playerFactionId);
                if (otherFid != null) {
                    warnedExpiries.remove(otherFid + "_" + a.getType().name());
                }
                it.remove();
            }
        }
    }

    private static float relationFloorFor(AgreementType type) {
        switch (type) {
            case NAP: return 0.10f;
            case DEFENSIVE_PACT: return 0.25f;
            case MILITARY_PARTNERSHIP: return 0.50f;
            case ECONOMIC_PARTNERSHIP: return 0.25f;
            default: return -1f;
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
