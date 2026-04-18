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

    private final VoteType type;
    private final String proposerFactionId;
    private final String targetFactionId;
    private final Map<String, Boolean> votes = new HashMap<String, Boolean>();
    private boolean resolved;
    private boolean passed;

    public CoalitionVote(VoteType type, String proposerFactionId, String targetFactionId) {
        this.type = type;
        this.proposerFactionId = proposerFactionId;
        this.targetFactionId = targetFactionId;
    }

    public VoteType getType() { return type; }
    public String getProposerFactionId() { return proposerFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public boolean isResolved() { return resolved; }
    public boolean isPassed() { return passed; }

    public void castVote(String factionId, boolean inFavor) { votes.put(factionId, inFavor); }

    public void resolve(String blocLeaderId) {
        float forWeight = 0f, againstWeight = 0f;
        for (Map.Entry<String, Boolean> e : votes.entrySet()) {
            float w = e.getKey().equals(blocLeaderId) ? 2f : 1f;
            if (e.getValue()) forWeight += w; else againstWeight += w;
        }
        this.passed = forWeight > againstWeight;
        this.resolved = true;
    }

    public Map<String, Boolean> getVotes() { return votes; }
}
