package nex4x.contracts;

import java.io.Serializable;

/** Single bid on an auctioned contract. */
public class Bid implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String bidderFactionId;
    private final long credits;
    private final float day;

    public Bid(String bidderFactionId, long credits, float day) {
        this.bidderFactionId = bidderFactionId;
        this.credits = credits;
        this.day = day;
    }

    public String getBidderFactionId() { return bidderFactionId; }
    public long getCredits() { return credits; }
    public float getDay() { return day; }
}
