package nex4x.contracts;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Second-price sealed-bid auction for covert contracts. */
public class ContractAuctionManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(ContractAuctionManager.class);

    public static final float BID_WINDOW_DAYS = 7f;

    private final List<Contract> contracts = new ArrayList<Contract>();
    private int nextId = 1;

    private static float now() {
        return nex4x.util.Nex4xClock.currentAbsoluteDay();
    }

    public Contract post(String issuerFactionId, String targetFactionId, ContractType type, long reservePrice) {
        float t = now();
        String id = "nex4x_contract_" + (nextId++);
        Contract c = new Contract(id, issuerFactionId, targetFactionId, type,
                reservePrice, t, t + BID_WINDOW_DAYS, t + BID_WINDOW_DAYS + type.defaultDurationDays);
        contracts.add(c);
        log.info("[Nex4x] Contract posted: " + id + " by " + issuerFactionId + " vs " + targetFactionId
                + " (" + type + ", reserve " + reservePrice + ")");
        return c;
    }

    public void bid(Contract c, String bidderId, long credits) {
        if (c.getStatus() != Contract.Status.OPEN) return;
        if (now() > c.getBidDeadlineDay()) return;
        c.addBid(new Bid(bidderId, credits, now()));
        log.info("[Nex4x] Bid: " + bidderId + " bids " + credits + " on " + c.getId());
    }

    /** Second-price: winner pays second-highest (or reserve if only one bid). */
    private void resolveAuction(Contract c) {
        List<Bid> bids = c.getBids();
        if (bids.isEmpty()) {
            c.setStatus(Contract.Status.EXPIRED);
            return;
        }
        Bid top = null, second = null;
        for (Bid b : bids) {
            if (top == null || b.getCredits() > top.getCredits()) {
                second = top;
                top = b;
            } else if (second == null || b.getCredits() > second.getCredits()) {
                second = b;
            }
        }
        if (top == null || top.getCredits() < c.getReservePrice()) {
            c.setStatus(Contract.Status.EXPIRED);
            return;
        }
        c.setWinningBid(top);
        c.setStatus(Contract.Status.AWARDED);
        long pay = second != null ? second.getCredits() : c.getReservePrice();
        log.info("[Nex4x] Contract " + c.getId() + " awarded to " + top.getBidderFactionId()
                + " (pays " + pay + " credits, bid " + top.getCredits() + ")");
    }

    public void complete(Contract c, boolean success) {
        if (c.getStatus() != Contract.Status.AWARDED) return;
        c.setStatus(success ? Contract.Status.COMPLETED : Contract.Status.FAILED);
        log.info("[Nex4x] Contract " + c.getId() + " " + (success ? "completed" : "failed"));
    }

    public List<Contract> getOpen() {
        List<Contract> out = new ArrayList<Contract>();
        for (Contract c : contracts) if (c.getStatus() == Contract.Status.OPEN) out.add(c);
        return out;
    }

    public List<Contract> getAwarded(String bidderId) {
        List<Contract> out = new ArrayList<Contract>();
        for (Contract c : contracts) {
            if (c.getStatus() == Contract.Status.AWARDED
                    && c.getWinningBid() != null
                    && c.getWinningBid().getBidderFactionId().equals(bidderId)) {
                out.add(c);
            }
        }
        return out;
    }

    public void advanceDay() {
        float t = now();
        Iterator<Contract> it = contracts.iterator();
        while (it.hasNext()) {
            Contract c = it.next();
            if (c.getStatus() == Contract.Status.OPEN && t >= c.getBidDeadlineDay()) {
                resolveAuction(c);
            }
            if (c.getStatus() == Contract.Status.AWARDED && t >= c.getExpiryDay()) {
                c.setStatus(Contract.Status.FAILED);
                log.info("[Nex4x] Contract " + c.getId() + " expired unfulfilled");
            }
            if ((c.getStatus() == Contract.Status.COMPLETED
                    || c.getStatus() == Contract.Status.FAILED
                    || c.getStatus() == Contract.Status.EXPIRED)
                    && t > c.getExpiryDay() + 60f) {
                it.remove();
            }
        }
    }

    public List<Contract> getAll() { return contracts; }

    public static ContractAuctionManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_CONTRACT_MANAGER);
        if (raw instanceof ContractAuctionManager) return (ContractAuctionManager) raw;
        return null;
    }

    public static ContractAuctionManager getOrCreate() {
        ContractAuctionManager mgr = get();
        if (mgr == null) {
            mgr = new ContractAuctionManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_CONTRACT_MANAGER, mgr);
        }
        return mgr;
    }
}
