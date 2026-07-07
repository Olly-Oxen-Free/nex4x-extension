package nex4x.negotiation;

import nex4x.leaders.LeaderProfile;

/**
 * Balance = sum(valueTo(leader, proposer-item)) - sum(valueTo(leader, receiver-item))
 * Positive -> leans to receiver, they like it. Negative -> leans to proposer, leader dislikes.
 *
 * @deprecated Use ItemValuator.valueForLeader + DealEvaluator(LeaderProfile, proposerFactionId).
 *             See PRD-016. Deletion deferred to PRD-025.
 */
@Deprecated
public class BalanceCalculator {

    public static class Result {
        public final int balance;
        public final Verdict verdict;
        public Result(int b, Verdict v) { this.balance = b; this.verdict = v; }
    }

    public enum Verdict { INSULTING, COLD, FAIR, GENEROUS, EXTRAORDINARY }

    public static Result evaluate(DealProposal deal, LeaderProfile leader, int acceptanceThreshold) {
        int fromProposer = 0;
        int fromReceiver = 0;
        for (NegotiableItem i : deal.getProposerOffers()) {
            fromProposer += ItemValuator.valueForLeader(i, leader, deal.getProposer());
        }
        for (NegotiableItem i : deal.getReceiverOffers()) {
            fromReceiver += ItemValuator.valueForLeader(i, leader, deal.getProposer());
        }
        int balance = fromProposer - fromReceiver;
        Verdict v;
        if (Math.abs(balance) < 50) v = Verdict.FAIR;
        else if (balance > acceptanceThreshold * 1.5)  v = Verdict.EXTRAORDINARY;
        else if (balance > acceptanceThreshold)        v = Verdict.GENEROUS;
        else if (balance < -acceptanceThreshold)       v = Verdict.INSULTING;
        else if (balance < -acceptanceThreshold / 2)   v = Verdict.COLD;
        else                                           v = Verdict.FAIR;
        return new Result(balance, v);
    }

    public static String verdictLine(Verdict v) {
        switch (v) {
            case INSULTING:     return "Insulting.";
            case COLD:          return "A cold start.";
            case FAIR:          return "A fair start.";
            case GENEROUS:      return "Generous — this could warm me.";
            case EXTRAORDINARY: return "Extraordinary. I'd sign now.";
            default:            return "";
        }
    }
}
