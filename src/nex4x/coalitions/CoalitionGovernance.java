package nex4x.coalitions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Coalition governance: member management, tension tracking, dissolution. */
public class CoalitionGovernance implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(CoalitionGovernance.class);

    public static final float TENSION_DECAY_PER_DAY = 0.2f;
    public static final float DISSOLUTION_TENSION_THRESHOLD = 70f;

    private final List<CoalitionTension> tensions = new ArrayList<CoalitionTension>();
    private final List<CoalitionVote> pendingVotes = new ArrayList<CoalitionVote>();

    /** Read-only lookup; returns null if no tension entry exists. Does not mutate state. */
    public CoalitionTension findTension(String factionA, String factionB) {
        for (CoalitionTension t : tensions) {
            if ((t.getFactionA().equals(factionA) && t.getFactionB().equals(factionB))
                    || (t.getFactionA().equals(factionB) && t.getFactionB().equals(factionA))) {
                return t;
            }
        }
        return null;
    }

    /** Returns existing tension entry or creates one. Mutates state. Use only on write paths. */
    public CoalitionTension getOrCreateTension(String factionA, String factionB) {
        CoalitionTension existing = findTension(factionA, factionB);
        if (existing != null) return existing;
        CoalitionTension t = new CoalitionTension(factionA, factionB);
        tensions.add(t);
        return t;
    }

    /** @deprecated use {@link #findTension} (read-only) or {@link #getOrCreateTension} (write). */
    @Deprecated
    public CoalitionTension getTension(String factionA, String factionB) {
        return getOrCreateTension(factionA, factionB);
    }

    public void addTension(String factionA, String factionB, float amount, String reason) {
        getOrCreateTension(factionA, factionB).addTension(amount);
        log.info("[Nex4x] Coalition tension: " + factionA + " / " + factionB + " +" + amount + " (" + reason + ")");
    }

    public float getAverageTension(List<String> memberIds) {
        if (memberIds.size() < 2) return 0f;
        float total = 0f;
        int pairs = 0;
        for (int i = 0; i < memberIds.size(); i++) {
            for (int j = i + 1; j < memberIds.size(); j++) {
                CoalitionTension t = findTension(memberIds.get(i), memberIds.get(j));
                if (t != null) total += t.getTension();
                pairs++;
            }
        }
        return pairs > 0 ? total / pairs : 0f;
    }

    public String getBlocLeader(List<String> memberIds) {
        String leader = null;
        int maxMarkets = 0;
        for (String fid : memberIds) {
            int markets = 0;
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (fid.equals(m.getFactionId()) && !m.isHidden()) markets++;
            }
            if (markets > maxMarkets) {
                maxMarkets = markets;
                leader = fid;
            }
        }
        return leader;
    }

    public CoalitionVote proposeVote(CoalitionVote.VoteType type, String proposerId, String targetId) {
        CoalitionVote vote = new CoalitionVote(type, proposerId, targetId);
        pendingVotes.add(vote);
        return vote;
    }

    public List<CoalitionVote> getPendingVotes() { return pendingVotes; }
    public List<CoalitionTension> getAllTensions() { return tensions; }

    public void advanceDay() {
        for (CoalitionTension t : tensions) t.decay(TENSION_DECAY_PER_DAY);
        Iterator<CoalitionVote> it = pendingVotes.iterator();
        while (it.hasNext()) if (it.next().isResolved()) it.remove();
    }

    public boolean shouldDissolve(List<String> memberIds) {
        return getAverageTension(memberIds) >= DISSOLUTION_TENSION_THRESHOLD;
    }

    public static CoalitionGovernance get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_COALITION_GOV);
        if (raw instanceof CoalitionGovernance) return (CoalitionGovernance) raw;
        return null;
    }

    public static CoalitionGovernance getOrCreate() {
        CoalitionGovernance mgr = get();
        if (mgr == null) {
            mgr = new CoalitionGovernance();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_COALITION_GOV, mgr);
        }
        return mgr;
    }
}
