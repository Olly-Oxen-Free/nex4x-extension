package nex4x.negotiation;

import nex4x.leaders.IntelTier;

/**
 * Adapts a BalanceCalculator.Result for display given an IntelTier.
 *
 * @deprecated Use ItemValuator.valueForLeader + DealEvaluator(LeaderProfile, proposerFactionId).
 *             See PRD-016. Deletion deferred to PRD-025.
 */
@Deprecated
public class BalanceSurface {

    public static class Surface {
        public final String qualitative;  // always present
        public final String numeric;      // empty when intel < GOOD
        public final float marginFraction;
        public final int estimate;
        public Surface(String q, String n, int est, float margin) {
            this.qualitative = q;
            this.numeric = n;
            this.estimate = est;
            this.marginFraction = margin;
        }
    }

    public static Surface surface(BalanceCalculator.Result r, IntelTier tier, int threshold) {
        String qual = BalanceCalculator.verdictLine(r.verdict);
        String numeric = "";
        if (tier == IntelTier.GOOD) {
            numeric = approximate(r.balance);
        } else if (tier == IntelTier.FULL) {
            int plusMinus = Math.max(1, Math.round(threshold * 0.02f));
            numeric = (r.balance >= 0 ? "+" : "") + r.balance + " +" + plusMinus + "/-" + plusMinus;
        }
        return new Surface(qual, numeric, r.balance, tier.marginFraction);
    }

    static String approximate(int balance) {
        // round to nearest 5
        int rounded = Math.round(balance / 5f) * 5;
        return "~" + (rounded >= 0 ? "+" : "") + rounded;
    }
}
