package nex4x.peace;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * Negotiation session resolving an active war. Tracks participants and proposed terms.
 * Concrete UI + resolution flow is wired via NegotiationTableDialog in war-state mode.
 */
public class PeaceConference implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(PeaceConference.class);

    private final String attackerId;
    private final String defenderId;
    private PeaceTerms proposed = new PeaceTerms();
    private boolean concluded;
    private boolean accepted;

    public PeaceConference(String attackerId, String defenderId) {
        this.attackerId = attackerId;
        this.defenderId = defenderId;
    }

    public String getAttackerId() { return attackerId; }
    public String getDefenderId() { return defenderId; }
    public PeaceTerms getProposed() { return proposed; }
    public void setProposed(PeaceTerms terms) { this.proposed = terms; }
    public boolean isConcluded() { return concluded; }
    public boolean isAccepted() { return accepted; }

    public void conclude(boolean accepted) {
        this.concluded = true;
        this.accepted = accepted;
        log.info("[Nex4x] PeaceConference " + attackerId + " vs " + defenderId
                + " concluded; accepted=" + accepted);
    }
}
