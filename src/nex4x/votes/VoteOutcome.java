package nex4x.votes;

import nex4x.data.TendencyId;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Result of an advisory vote, with per-tendency breakdown.
 * See master spec §5.2.8.
 */
public class VoteOutcome implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean passed;
    private final float totalFor;
    private final float totalAgainst;
    private final float playerVoteWeight;
    private final Map<TendencyId, Float> forBreakdown;
    private final Map<TendencyId, Float> againstBreakdown;

    public VoteOutcome(boolean passed, float totalFor, float totalAgainst,
                       float playerVoteWeight,
                       Map<TendencyId, Float> forBreakdown,
                       Map<TendencyId, Float> againstBreakdown) {
        this.passed = passed;
        this.totalFor = totalFor;
        this.totalAgainst = totalAgainst;
        this.playerVoteWeight = playerVoteWeight;
        this.forBreakdown = new EnumMap<TendencyId, Float>(forBreakdown);
        this.againstBreakdown = new EnumMap<TendencyId, Float>(againstBreakdown);
    }

    public boolean isPassed() { return passed; }
    public float getTotalFor() { return totalFor; }
    public float getTotalAgainst() { return totalAgainst; }
    public float getPlayerVoteWeight() { return playerVoteWeight; }
    public Map<TendencyId, Float> getForBreakdown() { return forBreakdown; }
    public Map<TendencyId, Float> getAgainstBreakdown() { return againstBreakdown; }

    public float getMargin() {
        return totalFor - totalAgainst;
    }

    public float getPercentFor() {
        float total = totalFor + totalAgainst;
        return total > 0 ? totalFor / total : 0.5f;
    }

    @Override
    public String toString() {
        return "Vote: " + (passed ? "PASSED" : "FAILED")
                + " (" + Math.round(totalFor) + " for / "
                + Math.round(totalAgainst) + " against)";
    }
}
