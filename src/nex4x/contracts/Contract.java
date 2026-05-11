package nex4x.contracts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A single auctionable contract. */
public class Contract implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { OPEN, AWARDED, COMPLETED, FAILED, EXPIRED }

    private final String id;
    private final String issuerFactionId;
    private final String targetFactionId;
    private final ContractType type;
    private final long reservePrice;
    private final float postedDay;
    private final float bidDeadlineDay;
    private final float expiryDay;
    private final List<Bid> bids = new ArrayList<Bid>();
    private Status status = Status.OPEN;
    private Bid winningBid;

    public Contract(String id, String issuerFactionId, String targetFactionId, ContractType type,
                    long reservePrice, float postedDay, float bidDeadlineDay, float expiryDay) {
        this.id = id;
        this.issuerFactionId = issuerFactionId;
        this.targetFactionId = targetFactionId;
        this.type = type;
        this.reservePrice = reservePrice;
        this.postedDay = postedDay;
        this.bidDeadlineDay = bidDeadlineDay;
        this.expiryDay = expiryDay;
    }

    public String getId() { return id; }
    public String getIssuerFactionId() { return issuerFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public ContractType getType() { return type; }
    public long getReservePrice() { return reservePrice; }
    public float getPostedDay() { return postedDay; }
    public float getBidDeadlineDay() { return bidDeadlineDay; }
    public float getExpiryDay() { return expiryDay; }
    public List<Bid> getBids() { return bids; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Bid getWinningBid() { return winningBid; }
    public void setWinningBid(Bid b) { this.winningBid = b; }

    public void addBid(Bid b) { bids.add(b); }
}
