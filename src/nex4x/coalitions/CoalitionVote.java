package nex4x.coalitions;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** A single intra-coalition vote. */
public class CoalitionVote implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum VoteType {
        ADD_MEMBER, KICK_MEMBER, DISSOLVE, DECLARE_WAR, MAKE_PEACE
    }

    public static final float DEFAULT_TIMEOUT_DAYS = 14f;
    /** Minimum fraction of coalition members who must cast a decisive vote for the vote to be valid. */
    public static final float DEFAULT_QUORUM_FRACTION = 0.5f;

    private final VoteType type;
    private final String proposerFactionId;
    private final String targetFactionId;
    private final Map<String, Boolean> votes = new HashMap<String, Boolean>();
    private final float createdDay;
    private boolean resolved;
    private boolean passed;
    /** True if resolution failed because quorum wasn't reached. */
    private boolean failedQuorum;

    public CoalitionVote(VoteType type, String proposerFactionId, String targetFactionId) {
        this.type = type;
        this.proposerFactionId = proposerFactionId;
        this.targetFactionId = targetFactionId;
        this.createdDay = nex4x.util.Nex4xClock.currentAbsoluteDay();
    }

    public VoteType getType() { return type; }
    public String getProposerFactionId() { return proposerFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public float getCreatedDay() { return createdDay; }
    public boolean isResolved() { return resolved; }
    public boolean isPassed() { return passed; }
    public boolean isFailedQuorum() { return failedQuorum; }

    public void castVote(String factionId, boolean inFavor) { votes.put(factionId, inFavor); }

    /**
     * Resolve the vote.
     * @param blocLeaderId  faction id whose vote weighs double; null if no leader.
     * @param memberCount   total coalition members (denominator for quorum).
     * @return true if a decision was reached; false if quorum failed.
     */
    public boolean resolve(String blocLeaderId, int memberCount) {
        if (resolved) return true;
        if (votes.isEmpty()) {
            this.failedQuorum = true;
            this.passed = false;
            this.resolved = true;
            return false;
        }
        if (memberCount > 0
                && (float) votes.size() / (float) memberCount < DEFAULT_QUORUM_FRACTION) {
            this.failedQuorum = true;
            this.passed = false;
            this.resolved = true;
            return false;
        }
        float forWeight = 0f, againstWeight = 0f;
        for (Map.Entry<String, Boolean> e : votes.entrySet()) {
            float w = (blocLeaderId != null && e.getKey().equals(blocLeaderId)) ? 2f : 1f;
            if (e.getValue()) forWeight += w; else againstWeight += w;
        }
        this.passed = forWeight > againstWeight; // ties fail
        this.resolved = true;
        return true;
    }

    /** Backwards-compat: resolves without quorum enforcement. */
    public void resolve(String blocLeaderId) {
        resolve(blocLeaderId, 0);
    }

    /** True if vote has been open longer than DEFAULT_TIMEOUT_DAYS. */
    public boolean isExpired() {
        return !resolved
                && (nex4x.util.Nex4xClock.currentAbsoluteDay() - createdDay) > DEFAULT_TIMEOUT_DAYS;
    }

    public Map<String, Boolean> getVotes() { return votes; }
}
