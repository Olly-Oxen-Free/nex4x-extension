package nex4x.negotiation;

import nex4x.leaders.IntelTier;
import nex4x.leaders.LeaderProfile;

import java.util.List;

/**
 * Greedy solver -- adds catalog chips to proposer's offers until balance is
 * within a margin determined by intel tier. Credits first (divisible), then
 * per-month commodities, then one-time, then agreements (step-function).
 */
public class AutoBalanceSolver {

    /**
     * Compute balance using the leader-aware path (PRD-016 16h: inlined from BalanceCalculator).
     * Positive = leans to receiver (leader likes it). Negative = leans to proposer.
     */
    private static int computeBalance(DealProposal deal, LeaderProfile leader) {
        int fromProposer = 0;
        int fromReceiver = 0;
        for (NegotiableItem i : deal.getProposerOffers()) {
            fromProposer += ItemValuator.valueForLeader(i, leader, deal.getProposer());
        }
        for (NegotiableItem i : deal.getReceiverOffers()) {
            fromReceiver += ItemValuator.valueForLeader(i, leader, deal.getProposer());
        }
        return fromProposer - fromReceiver;
    }

    public static void solve(DealProposal deal, LeaderProfile leader,
                             NegotiableItemCatalog catalog, IntelTier tier,
                             int acceptanceThreshold) {
        int marginCredits = Math.max(50, Math.round(acceptanceThreshold * tier.marginFraction));
        // PRD-016 16h: inlined from BalanceCalculator.evaluate — BalanceCalculator is deprecated.
        int balance = computeBalance(deal, leader);

        // Needs to add value on proposer side (balance too negative <-> leans to proposer).
        // If balance is positive, we overshot -- do nothing in v5 (solver only adds, never removes).
        int addedIterations = 0;
        int maxIterations = 100;
        while (balance < -marginCredits && addedIterations < maxIterations) {
            List<String> candidateIds = catalog.getProposerAddableIds(deal);
            String pickedId = pickBestAdd(candidateIds, catalog, leader, deal, Math.abs(balance));
            if (pickedId == null) break;
            int qty = catalog.suggestedQty(pickedId, Math.abs(balance));
            if (!deal.applyMutation(DealMutation.addOffer(pickedId, qty), catalog)) break;
            balance = computeBalance(deal, leader);
            addedIterations++;
        }
    }

    static String pickBestAdd(List<String> ids, NegotiableItemCatalog catalog,
                              LeaderProfile leader, DealProposal deal, int targetCredits) {
        // Prefer credits-bearing divisible items first; fall back to smallest item whose
        // value >= targetCredits.
        String best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (String id : ids) {
            int val = catalog.estimatedUnitValue(id, leader, deal.getProposer());
            if (val <= 0) continue;
            int delta = Math.abs(val - targetCredits);
            if (delta < bestDelta) { bestDelta = delta; best = id; }
        }
        return best;
    }
}
