package nex4x.negotiation;

/** Declarative mutation applied to a DealProposal. Both UI and future LLM route through this. */
public class DealMutation {

    public enum Op { ADD_OFFER, REMOVE_OFFER, ADD_REQUEST, REMOVE_REQUEST,
                     LOCK_CHIP, UNLOCK_CHIP, CLEAR_ALL }

    public final Op op;
    public final String itemId;
    public final int quantity;
    public final String reason;

    public DealMutation(Op op, String itemId, int quantity, String reason) {
        this.op = op;
        this.itemId = itemId;
        this.quantity = quantity;
        this.reason = reason;
    }

    public static DealMutation addOffer(String itemId, int qty) {
        return new DealMutation(Op.ADD_OFFER, itemId, qty, null);
    }

    public static DealMutation removeOffer(String itemId) {
        return new DealMutation(Op.REMOVE_OFFER, itemId, 1, null);
    }

    public static DealMutation addRequest(String itemId, int qty) {
        return new DealMutation(Op.ADD_REQUEST, itemId, qty, null);
    }

    public static DealMutation removeRequest(String itemId) {
        return new DealMutation(Op.REMOVE_REQUEST, itemId, 1, null);
    }

    public static DealMutation lock(String itemId, String reason) {
        return new DealMutation(Op.LOCK_CHIP, itemId, 0, reason);
    }

    public static DealMutation unlock(String itemId) {
        return new DealMutation(Op.UNLOCK_CHIP, itemId, 0, null);
    }
}
