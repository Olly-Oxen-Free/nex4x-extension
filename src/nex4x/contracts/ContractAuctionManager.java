package nex4x.contracts;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Second-price sealed-bid auction for covert contracts. */
public class ContractAuctionManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(ContractAuctionManager.class);

    public static final float BID_WINDOW_DAYS = 7f;
    /** Rate at which contract payment (in credits) is converted to influence for AI factions. */
    public static final float CONTRACT_INFLUENCE_RATE = 0.1f;

    /** Mapping of ContractType to the best-match covert action def id for dispatch. */
    private static final Map<ContractType, String> CONTRACT_TYPE_TO_ACTION_DEF =
            new HashMap<ContractType, String>();
    static {
        // DESIGN: AI factions cannot hold credits; contract payoff uses influence transfer.
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.HARASS_FACTION,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_HIRE_MERCS);
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.RAID_FACTION,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_INCITE_RAID);
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.BLOCKADE_MARKET,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_HIRE_MERCS);
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.SABOTAGE_INDUSTRY,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_FALSE_FLAG);
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.ASSASSINATE_OFFICIAL,
                nex4x.agents.actions.ActionDefIds.DIPLOMAT_LEAK); // best-effort — no exact match
        CONTRACT_TYPE_TO_ACTION_DEF.put(ContractType.ESCORT_CONVOY,
                null); // no covert action mapping for defensive contracts
    }

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

    /**
     * Compute the second-price payment for an awarded contract.
     * Winner pays second-highest bid (or reserve price if only one bid).
     */
    private long computeSecondPrice(Contract c) {
        List<Bid> bids = c.getBids();
        Bid top = null, second = null;
        for (Bid b : bids) {
            if (top == null || b.getCredits() > top.getCredits()) {
                second = top;
                top = b;
            } else if (second == null || b.getCredits() > second.getCredits()) {
                second = b;
            }
        }
        return second != null ? second.getCredits() : c.getReservePrice();
    }

    public void complete(Contract c, boolean success) {
        if (c.getStatus() != Contract.Status.AWARDED) return;
        c.setStatus(success ? Contract.Status.COMPLETED : Contract.Status.FAILED);
        log.info("[Nex4x] Contract " + c.getId() + " " + (success ? "completed" : "failed"));
        if (!success) return;

        // Determine payment amount (second-price or reserve)
        long pay = (c.getWinningBid() != null && c.getWinningBid().getCredits() > 0)
                ? computeSecondPrice(c) : c.getReservePrice();

        String winnerId = c.getWinningBid() != null
                ? c.getWinningBid().getBidderFactionId() : null;
        if (winnerId == null) {
            log.warn("[Nex4x] Contract " + c.getId() + " complete: no winning bid — skipping payoff");
            return;
        }

        boolean playerWon = winnerId.equals(
                Global.getSector().getPlayerFaction().getId());
        if (playerWon) {
            // DESIGN: player fleet can receive literal credits.
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(pay);
            log.info("[Nex4x] Contract " + c.getId() + " paid " + pay
                    + " credits to player fleet");
        } else {
            // DESIGN: AI factions cannot hold credits — model as influence.
            float influence = (float)(pay * CONTRACT_INFLUENCE_RATE);
            InfluenceManager.getOrCreate().addLump(winnerId, influence,
                    InfluenceSource.CONTRACT_PAYOFF);
            log.info("[Nex4x] Contract " + c.getId() + " — influence +"
                    + influence + " to " + winnerId + " (credits=" + pay + ")");
        }

        // Dispatch covert action (best-effort; falls back gracefully)
        try {
            dispatchContractAction(c, winnerId);
        } catch (Throwable t) {
            log.warn("[Nex4x] Contract covert dispatch failed: " + t.getMessage());
        }
    }

    /** Attempt to dispatch a covert action matching the contract type via Nex CovertOpsManager. */
    private void dispatchContractAction(Contract c, String executorFactionId) {
        String actionDefId = CONTRACT_TYPE_TO_ACTION_DEF.get(c.getType());
        if (actionDefId == null) {
            log.info("[Nex4x] Contract " + c.getId()
                    + " — no action def mapping for " + c.getType() + "; skipping dispatch");
            return;
        }
        try {
            // Look up an agent owned by executorFactionId
            nex4x.agents.Nex4xAgentManager agentMgr = nex4x.agents.Nex4xAgentManager.get();
            if (agentMgr == null) {
                log.info("[Nex4x] Contract " + c.getId()
                        + " — Nex4xAgentManager unavailable; skipping dispatch");
                return;
            }
            exerelin.campaign.intel.agents.AgentIntel agent = null;
            java.util.List<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin> agents =
                    Global.getSector().getIntelManager().getIntel(
                            exerelin.campaign.intel.agents.AgentIntel.class);
            if (agents != null) {
                for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin p : agents) {
                    if (!(p instanceof exerelin.campaign.intel.agents.AgentIntel)) continue;
                    exerelin.campaign.intel.agents.AgentIntel ai =
                            (exerelin.campaign.intel.agents.AgentIntel) p;
                    if (ai.isDeadOrDismissed()) continue;
                    if (ai.getAgent() == null) continue;
                    nex4x.agents.Nex4xAgentData data = agentMgr.get(ai.getAgent().getId());
                    if (data != null && executorFactionId.equals(data.getOwnerFactionId())) {
                        agent = ai;
                        break;
                    }
                }
            }
            if (agent == null) {
                log.info("[Nex4x] Contract " + c.getId()
                        + " — no executor agent available for " + executorFactionId
                        + "; skipping dispatch");
                return;
            }
            // Verify action def exists in CovertOpsManager
            boolean defExists = exerelin.campaign.CovertOpsManager.actionDefsById
                    .containsKey(actionDefId);
            if (!defExists) {
                log.info("[Nex4x] Contract " + c.getId()
                        + " — action def not found: " + actionDefId + "; skipping dispatch");
                return;
            }
            // NOTE: Nex AgentIntel/PersonAPI does not expose a public setTarget/setAction API.
            // The agent is confirmed present; log the dispatch intent.
            // TODO: wire actual action launch when Nex exposes a programmatic queue method.
            log.info("[Nex4x] Contract " + c.getId() + " — agent of " + executorFactionId
                    + " designated for action " + actionDefId + " vs " + c.getTargetFactionId()
                    + " (programmatic launch deferred — no public Nex API)");
            // DESIGN: influence is still transferred even without programmatic dispatch.
            // The contract commitment is fulfilled; covert execution is best-effort.
        } catch (Throwable t) {
            log.warn("[Nex4x] Contract " + c.getId()
                    + " covert dispatch failed (Nex unavailable?): " + t.getMessage());
        }
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
