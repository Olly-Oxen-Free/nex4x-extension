package nex4x.negotiation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Two-sided deal state. All mutations go through applyMutation() so the joint-spec
 * LLM tool-call path can drive the same transitions as the UI.
 */
public class DealProposal {
    private final String proposerFactionId;
    private final String receiverFactionId;

    private final List<NegotiableItem> proposerOffers = new ArrayList<NegotiableItem>();
    private final List<NegotiableItem> receiverOffers = new ArrayList<NegotiableItem>();
    private final Set<String> lockedChips = new HashSet<String>();

    public DealProposal(String proposerFactionId, String receiverFactionId) {
        this.proposerFactionId = proposerFactionId;
        this.receiverFactionId = receiverFactionId;
    }

    public String getProposer() { return proposerFactionId; }
    public String getReceiver() { return receiverFactionId; }
    public List<NegotiableItem> getProposerOffers() { return proposerOffers; }
    public List<NegotiableItem> getReceiverOffers() { return receiverOffers; }
    public Set<String> getLockedChips() { return lockedChips; }

    public boolean applyMutation(DealMutation m, NegotiableItemCatalog catalog) {
        if (m == null) return false;
        switch (m.op) {
            case ADD_OFFER: {
                if (lockedChips.contains(m.itemId)) return false;
                NegotiableItem item = catalog.build(m.itemId, m.quantity);
                if (item == null) return false;
                proposerOffers.add(item);
                return true;
            }
            case REMOVE_OFFER: {
                return removeById(proposerOffers, m.itemId);
            }
            case ADD_REQUEST: {
                if (lockedChips.contains(m.itemId)) return false;
                NegotiableItem item = catalog.build(m.itemId, m.quantity);
                if (item == null) return false;
                receiverOffers.add(item);
                return true;
            }
            case REMOVE_REQUEST: {
                return removeById(receiverOffers, m.itemId);
            }
            case LOCK_CHIP:    lockedChips.add(m.itemId); return true;
            case UNLOCK_CHIP:  lockedChips.remove(m.itemId); return true;
            case CLEAR_ALL:
                proposerOffers.clear();
                receiverOffers.clear();
                lockedChips.clear();
                return true;
        }
        return false;
    }

    static boolean removeById(List<NegotiableItem> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) { list.remove(i); return true; }
        }
        return false;
    }
}
