package nex4x.negotiation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete deal proposal between two factions.
 * Contains items offered by the proposer and items requested from the target.
 * Balance is always evaluated from the target's perspective.
 */
public class DealPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String proposerFactionId;
    private final String targetFactionId;
    private final List<NegotiableItem> offers;   // proposer gives these
    private final List<NegotiableItem> requests;  // proposer wants these

    public DealPackage(String proposerFactionId, String targetFactionId) {
        this.proposerFactionId = proposerFactionId;
        this.targetFactionId = targetFactionId;
        this.offers = new ArrayList<NegotiableItem>();
        this.requests = new ArrayList<NegotiableItem>();
    }

    // ── Mutation ────────────────────────────────────────────────

    public void addOffer(NegotiableItem item) {
        offers.add(item);
    }

    public void addRequest(NegotiableItem item) {
        requests.add(item);
    }

    public void removeOffer(int index) {
        if (index >= 0 && index < offers.size()) offers.remove(index);
    }

    public void removeRequest(int index) {
        if (index >= 0 && index < requests.size()) requests.remove(index);
    }

    public void clearOffers() { offers.clear(); }
    public void clearRequests() { requests.clear(); }

    // ── Queries ────────────────────────────────────────────────

    public String getProposerFactionId() { return proposerFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public List<NegotiableItem> getOffers() { return Collections.unmodifiableList(offers); }
    public List<NegotiableItem> getRequests() { return Collections.unmodifiableList(requests); }
    public boolean isEmpty() { return offers.isEmpty() && requests.isEmpty(); }

    /** True if a ceasefire item appears anywhere in the deal (either column). */
    public boolean hasCeasefire() {
        for (NegotiableItem item : offers) {
            if (item.isCeasefire()) return true;
        }
        for (NegotiableItem item : requests) {
            if (item.isCeasefire()) return true;
        }
        return false;
    }

    /** True if the deal contains any item of the given type (either column). */
    public boolean hasItemType(NegotiableItemType type) {
        for (NegotiableItem item : offers) {
            if (item.getType() == type) return true;
        }
        for (NegotiableItem item : requests) {
            if (item.getType() == type) return true;
        }
        return false;
    }

    /**
     * Calculate deal balance from the target faction's perspective using per-item valuations.
     * Positive = favorable to target (they receive more value than they give up).
     * This is the raw balance before memory/desperation/vote modifiers.
     */
    public float getBalance(ItemValuator valuator) {
        float receivedValue = 0;
        for (NegotiableItem item : offers) {
            receivedValue += valuator.evaluate(item, targetFactionId);
        }
        float givenValue = 0;
        for (NegotiableItem item : requests) {
            givenValue += valuator.evaluate(item, targetFactionId);
        }
        return receivedValue - givenValue;
    }

    /**
     * PRD-016: Leader-aware balance using ItemValuator.valueForLeader.
     * Personality, goal alignment, relation, and scarcity all apply.
     * Positive = favorable to target (leader's faction).
     */
    public float getLeaderBalance(nex4x.leaders.LeaderProfile targetLeader,
                                  String proposerFactionId) {
        float receivedValue = 0f;
        for (NegotiableItem item : offers) {
            receivedValue += ItemValuator.valueForLeader(item, targetLeader, proposerFactionId);
        }
        float givenValue = 0f;
        for (NegotiableItem item : requests) {
            givenValue += ItemValuator.valueForLeader(item, targetLeader, proposerFactionId);
        }
        return receivedValue - givenValue;
    }

    /**
     * Get the total value of all offered items from the target's perspective.
     */
    public float getOfferedValue(ItemValuator valuator) {
        float total = 0;
        for (NegotiableItem item : offers) {
            total += valuator.evaluate(item, targetFactionId);
        }
        return total;
    }

    /**
     * Get the total value of all requested items from the target's perspective.
     */
    public float getRequestedValue(ItemValuator valuator) {
        float total = 0;
        for (NegotiableItem item : requests) {
            total += valuator.evaluate(item, targetFactionId);
        }
        return total;
    }
}
