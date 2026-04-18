package nex4x.negotiation;

import nex4x.leaders.LeaderProfile;

import java.util.Collections;
import java.util.List;

/**
 * Stub catalog for Phase 8. Phase 11 (content scaffolding) will populate
 * real item-id to NegotiableItem construction. For now returns empty/defaults
 * so the mutation/solver code paths compile and are exercisable.
 */
public class NegotiableItemCatalog {

    /** Build a negotiable item from a catalog id. Returns null if unknown. */
    public NegotiableItem build(String itemId, int quantity) {
        if (itemId == null) return null;
        // Minimal built-in ids so the solver is non-trivial in smoke tests:
        if ("credits".equals(itemId)) {
            NegotiableItem item = NegotiableItem.credits(quantity);
            item.setId("credits");
            return item;
        }
        return null;
    }

    /** Ids the proposer could add to their offer column given current deal state. */
    public List<String> getProposerAddableIds(DealProposal deal) {
        return Collections.singletonList("credits");
    }

    /** How many units of itemId to add when trying to close targetCredits of gap. */
    public int suggestedQty(String itemId, int targetCredits) {
        if ("credits".equals(itemId)) return Math.max(1, targetCredits);
        return 1;
    }

    /** Rough per-unit value estimate for solver ranking. */
    public int estimatedUnitValue(String itemId, LeaderProfile leader, String proposerFactionId) {
        if ("credits".equals(itemId)) return 1;
        return 0;
    }
}
