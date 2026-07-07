package nex4x.peace;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.SectorManager;
import nex4x.negotiation.NegotiationDealExecutor;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * Negotiation session resolving an active war. Tracks participants and proposed terms.
 * Concrete UI + resolution flow is wired via NegotiationTableDialog in war-state mode.
 */
public class PeaceConference implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(PeaceConference.class);

    /** Lifecycle: PROPOSED → COUNTER_OFFERED ↔ PROPOSED → ACCEPTED|REJECTED|EXPIRED. */
    public enum Status { PROPOSED, COUNTER_OFFERED, ACCEPTED, REJECTED, EXPIRED }

    private final String attackerId;
    private final String defenderId;
    private PeaceTerms proposed = new PeaceTerms();
    private PeaceTerms counterOffer;
    private Status status = Status.PROPOSED;

    public PeaceConference(String attackerId, String defenderId) {
        this.attackerId = attackerId;
        this.defenderId = defenderId;
    }

    public String getAttackerId() { return attackerId; }
    public String getDefenderId() { return defenderId; }
    public PeaceTerms getProposed() { return proposed; }
    public void setProposed(PeaceTerms terms) { this.proposed = terms; }
    public PeaceTerms getCounterOffer() { return counterOffer; }
    public Status getStatus() { return status; }

    /** True once the conference has reached a terminal state. */
    public boolean isConcluded() {
        return status == Status.ACCEPTED || status == Status.REJECTED || status == Status.EXPIRED;
    }
    /** True if the most-recent terms were accepted. Backwards-compat with old isAccepted() callers. */
    public boolean isAccepted() { return status == Status.ACCEPTED; }

    /** Defender returns a counter-offer; conference re-enters negotiation. */
    public void counterOffer(PeaceTerms terms) {
        if (isConcluded()) return;
        this.counterOffer = terms;
        this.status = Status.COUNTER_OFFERED;
        log.info("[Nex4x] PeaceConference " + attackerId + " vs " + defenderId + " counter-offered");
    }

    /** Either side accepts the current proposal/counter-offer. */
    public void accept() {
        if (isConcluded()) return;
        if (status == Status.COUNTER_OFFERED && counterOffer != null) {
            this.proposed = counterOffer;
        }
        this.status = Status.ACCEPTED;
        log.info("[Nex4x] PeaceConference " + attackerId + " vs " + defenderId + " ACCEPTED");

        // Apply each ratified term.
        // Loser = attacker, winner = defender (standard conference convention).
        if (proposed != null) {
            for (PeaceTerms.Term term : proposed.getTerms()) {
                applyTerm(term);
            }
        }

        // Fire peace event via Nexerelin bridge.
        try {
            FactionAPI fa = Global.getSector().getFaction(attackerId);
            FactionAPI fb = Global.getSector().getFaction(defenderId);
            nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(fa, fb);
        } catch (Throwable t) {
            log.warn("[Nex4x] PeaceConference.accept fire peace: " + t.getMessage());
        }
    }

    /**
     * Applies a single ratified term.
     * Loser = attacker, winner = defender.
     */
    private void applyTerm(PeaceTerms.Term term) {
        FactionAPI loserFaction  = Global.getSector().getFaction(attackerId);
        FactionAPI winnerFaction = Global.getSector().getFaction(defenderId);

        switch (term.type) {
            case TERRITORY_CEDE: {
                MarketAPI m = Global.getSector().getEconomy().getMarket(term.payload);
                if (m == null) {
                    log.warn("[Nex4x] TERRITORY_CEDE: market not found: " + term.payload);
                    break;
                }
                if (!attackerId.equals(m.getFactionId())) {
                    log.warn("[Nex4x] TERRITORY_CEDE: market " + m.getId()
                            + " not owned by attacker " + attackerId + " — skipping.");
                    break;
                }
                if (loserFaction == null || winnerFaction == null) {
                    log.warn("[Nex4x] TERRITORY_CEDE: null faction(s)");
                    break;
                }
                try {
                    SectorManager.transferMarket(m, loserFaction, winnerFaction,
                            false, false, null, 0f);
                    log.info("[Nex4x] TERRITORY_CEDE applied: " + m.getId()
                            + " (" + attackerId + " -> " + defenderId + ")");
                } catch (Throwable t) {
                    log.error("[Nex4x] SectorManager.transferMarket failed: " + t.getMessage(), t);
                }
                break;
            }
            case REPARATIONS:
            case WAR_REPARATIONS: {
                // Attacker pays defender.
                NegotiationDealExecutor.transferCreditsStatic(
                        attackerId, defenderId, (long) term.amount);
                log.info("[Nex4x] " + term.type + " applied: "
                        + attackerId + " -> " + defenderId + " (" + (long) term.amount + " cr)");
                break;
            }
            case CEASEFIRE:
            case WHITE_PEACE:
            case TRADE_RESUMPTION:
            case NAP:
                // Already handled by firePeaceTreaty above; log acknowledgement only.
                log.info("[Nex4x] Term acknowledged (handled by peace treaty): " + term.type);
                break;
            default:
                log.info("[Nex4x] Peace term not explicitly handled: " + term.type);
                break;
        }
    }

    public void reject() {
        if (isConcluded()) return;
        this.status = Status.REJECTED;
        log.info("[Nex4x] PeaceConference " + attackerId + " vs " + defenderId + " REJECTED");
    }

    public void expire() {
        if (isConcluded()) return;
        this.status = Status.EXPIRED;
        log.info("[Nex4x] PeaceConference " + attackerId + " vs " + defenderId + " EXPIRED");
    }

    /** Backwards-compat: old single-boolean conclude. */
    public void conclude(boolean accepted) {
        if (accepted) accept(); else reject();
    }
}
