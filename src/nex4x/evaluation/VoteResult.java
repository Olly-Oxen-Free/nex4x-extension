package nex4x.evaluation;

import nex4x.data.TendencyId;

import java.util.EnumMap;
import java.util.Map;

/**
 * Result of an internal politics vote on a deal proposal.
 * Each tendency contributes a score based on its weight and how it evaluates the deal.
 * Positive total = approval, negative = rejection.
 */
public class VoteResult {

    private final EnumMap<TendencyId, Float> scores;
    private final float total;
    private final String summary;

    public VoteResult(EnumMap<TendencyId, Float> scores, String summary) {
        this.scores = new EnumMap<TendencyId, Float>(scores);
        float sum = 0;
        for (float v : scores.values()) sum += v;
        this.total = sum;
        this.summary = summary;
    }

    /** Score contributed by a specific tendency. */
    public float getScore(TendencyId tendency) {
        Float s = scores.get(tendency);
        return s != null ? s : 0;
    }

    /** Total vote score. Positive = accept, negative = reject. */
    public float getTotal() { return total; }

    /** Is the vote positive (accept deal)? */
    public boolean isApproval() { return total > 0; }

    /** Is the vote strongly positive (eager accept)? */
    public boolean isStrongApproval() { return total > 5; }

    /** Is the vote strongly negative (firm reject)? */
    public boolean isStrongRejection() { return total < -5; }

    /** Human-readable summary of the vote. */
    public String getSummary() { return summary; }

    /** Get the full vote breakdown for UI display. */
    public EnumMap<TendencyId, Float> getBreakdown() {
        return new EnumMap<TendencyId, Float>(scores);
    }

    /** Get the strongest supporting tendency. */
    public TendencyId getStrongestSupporter() {
        TendencyId best = null;
        float bestScore = 0;
        for (Map.Entry<TendencyId, Float> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** Get the strongest opposing tendency. */
    public TendencyId getStrongestOpposer() {
        TendencyId worst = null;
        float worstScore = 0;
        for (Map.Entry<TendencyId, Float> e : scores.entrySet()) {
            if (e.getValue() < worstScore) {
                worstScore = e.getValue();
                worst = e.getKey();
            }
        }
        return worst;
    }
}
