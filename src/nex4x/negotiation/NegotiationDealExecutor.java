package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.MutableValue;
import exerelin.campaign.SectorManager;
import nex4x.agreements.AgreementType;
import nex4x.declarations.DeclarationManager;
import nex4x.declarations.DeclarationType;
import nex4x.managers.Nex4xManager;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

/**
 * Executes a {@link DealPackage} after acceptance. Shared by {@link nex4x.ui.NegotiationPanel}
 * and {@link nex4x.ui.AIProposalIntel}.
 */
public final class NegotiationDealExecutor {

    private static final Logger log = Global.getLogger(NegotiationDealExecutor.class);

    private NegotiationDealExecutor() {}

    public static void executeDeal(DealPackage deal, Nex4xManager mgr, boolean viaViceroy) {
        if (mgr == null || deal == null || deal.isEmpty()) return;

        String playerId = Global.getSector().getPlayerFaction().getId();
        for (NegotiableItem item : deal.getOffers()) {
            executeItem(item, deal.getProposerFactionId(), deal.getTargetFactionId(), mgr,
                    playerId, viaViceroy);
        }
        for (NegotiableItem item : deal.getRequests()) {
            executeItem(item, deal.getTargetFactionId(), deal.getProposerFactionId(), mgr,
                    playerId, viaViceroy);
        }
    }

    private static void executeItem(NegotiableItem item, String giver, String receiver,
                                    Nex4xManager mgr, String playerId, boolean viaViceroy) {
        switch (item.getType()) {
            case AGREEMENTS:
                if (item.getAgreementType() != null) {
                    String other = playerId.equals(giver) ? receiver : giver;
                    mgr.getAgreementManager().createAgreement(
                            playerId, other, item.getAgreementType(), viaViceroy);
                }
                break;
            case PEACE_TERMS:
                handlePeaceTerms(item, giver, receiver, playerId);
                break;
            case WAR_DECLARATION:
                if (item.getTargetId() != null) {
                    mgr.getExecutor(giver).declareWarPlayer(item.getTargetId());
                }
                break;
            case CREDITS:
                transferCredits(giver, receiver, playerId, (long) item.getAmount());
                break;
            case TRIBUTE:
                mgr.getMemoryManager().createMemory("tribute_obligation", giver, receiver,
                        String.format("%.0f cr/cycle for %.0f days", item.getAmount(), item.getDurationDays()));
                PressureManager.getOrCreate().applyEvent(giver, receiver, PressureSource.ECONOMIC_EXPORT, 15f);
                break;
            case COMMODITIES:
                transferCommodity(giver, receiver, playerId, item.getTargetId(), (int) item.getAmount());
                break;
            case TERRITORY:
                if (item.getTargetId() != null) {
                    MarketAPI m = Global.getSector().getEconomy().getMarket(item.getTargetId());
                    if (m == null) {
                        log.warn("[Nex4x] Territory deal: market not found: " + item.getTargetId());
                        break;
                    }
                    FactionAPI giverFaction    = Global.getSector().getFaction(giver);
                    FactionAPI receiverFaction = Global.getSector().getFaction(receiver);
                    if (giverFaction == null || receiverFaction == null) {
                        log.warn("[Nex4x] Territory deal: null faction(s) giver=" + giver
                                + " receiver=" + receiver);
                        break;
                    }
                    if (!giver.equals(m.getFactionId())) {
                        log.warn("[Nex4x] Territory deal: market " + m.getId()
                                + " not owned by giver " + giver + " — skipping transfer.");
                        break;
                    }
                    try {
                        SectorManager.transferMarket(
                                m, giverFaction, receiverFaction, false, false, null, 0f);
                        log.info("[Nex4x] Territory transferred: " + m.getId()
                                + " (" + giver + " -> " + receiver + ")");
                    } catch (Throwable t) {
                        log.error("[Nex4x] SectorManager.transferMarket failed: "
                                + t.getMessage(), t);
                    }
                }
                break;
            case KNOWLEDGE:
                mgr.getMemoryManager().createMemory("tech_transfer", giver, receiver,
                        item.getTargetId() != null ? item.getTargetId() : "blueprints");
                break;
            case INTEL:
                mgr.getMemoryManager().createMemory("intel_exchange", giver, receiver,
                        item.getTargetId() != null ? item.getTargetId() : "intel");
                PressureManager.getOrCreate().applyEvent(giver, receiver, PressureSource.EVENT, 10f);
                break;
            case CONTRACTS:
                mgr.getMemoryManager().createMemory("contract", giver, receiver,
                        item.getTargetId() + " (" + (int) item.getDurationDays() + "d)");
                break;
            case CONCESSIONS:
                handleConcession(item, giver, receiver, playerId);
                break;
            case PRISONERS:
                mgr.getMemoryManager().createMemory("prisoner_exchange", giver, receiver,
                        item.getTargetId() != null ? item.getTargetId() : "exchange");
                break;
            case DECLARATIONS:
                handleDeclaration(item, giver, receiver, mgr);
                break;
            default:
                log.warn("[Nex4x] Unhandled negotiable type: " + item.getType());
        }
    }

    private static void handlePeaceTerms(NegotiableItem item, String giver, String receiver,
                                         String playerId) {
        FactionAPI g = Global.getSector().getFaction(giver);
        FactionAPI r = Global.getSector().getFaction(receiver);
        if (g == null || r == null) return;

        if (item.isCeasefire()) {
            try {
                exerelin.campaign.DiplomacyManager.createDiplomacyEventV2(g, r, "ceasefire", null);
            } catch (Exception e) {
                try {
                    g.setRelationship(receiver, 0f);
                    r.setRelationship(giver, 0f);
                } catch (Exception e2) {
                    log.error("[Nex4x] Ceasefire failed: " + e.getMessage());
                }
            }
            return;
        }
        if ("peace_treaty".equals(item.getSecondaryId())) {
            try {
                exerelin.campaign.DiplomacyManager.createDiplomacyEventV2(g, r, "ceasefire", null);
            } catch (Exception ignore) { }
            try {
                if (g.isHostileTo(r)) {
                    g.setRelationship(receiver, 0.1f);
                    r.setRelationship(giver, 0.1f);
                }
            } catch (Exception e) {
                log.error("[Nex4x] Peace treaty relationship step failed: " + e.getMessage());
            }
            return;
        }
        if ("reparations".equals(item.getSecondaryId())) {
            transferCredits(giver, receiver, playerId, (long) item.getAmount());
        }
    }

    /**
     * Package-visible static accessor for peace-term credit transfers.
     * Delegates to the main transferCredits path using the player faction id.
     */
    public static void transferCreditsStatic(String giver, String receiver, long amount) {
        String playerId = Global.getSector().getPlayerFaction().getId();
        transferCredits(giver, receiver, playerId, amount);
    }

    private static void transferCredits(String giver, String receiver, String playerId, long amount) {
        if (amount <= 0) return;
        MutableValue creds = Global.getSector().getPlayerFleet().getCargo().getCredits();
        if (playerId.equals(giver)) {
            creds.subtract((int) Math.min(amount, creds.get()));
            AddRemoveCommodity.addCreditsLossText((int) amount, null);
        } else if (playerId.equals(receiver)) {
            creds.add((int) Math.min(amount, Integer.MAX_VALUE));
            AddRemoveCommodity.addCreditsGainText((int) amount, null);
        } else {
            log.info("[Nex4x] NPC credit transfer skipped: " + giver + " -> " + receiver + " (" + amount + ")");
        }
    }

    private static void transferCommodity(String giver, String receiver, String playerId,
                                          String commodityId, int qty) {
        if (commodityId == null || qty == 0) return;
        if (playerId.equals(giver)) {
            Global.getSector().getPlayerFleet().getCargo().removeCommodity(commodityId, qty);
        } else if (playerId.equals(receiver)) {
            Global.getSector().getPlayerFleet().getCargo().addCommodity(commodityId, qty);
        } else {
            log.info("[Nex4x] NPC commodity transfer skipped: " + commodityId + " x" + qty);
        }
    }

    private static void handleConcession(NegotiableItem item, String giver, String receiver,
                                         String playerId) {
        String third = item.getTargetId();
        if (third == null) return;
        FactionAPI t = Global.getSector().getFaction(third);
        FactionAPI g = Global.getSector().getFaction(giver);
        if (t == null || g == null) return;
        if ("embargo".equals(item.getSecondaryId())) {
            try {
                float cur = g.getRelationship(third);
                g.setRelationship(third, cur - 0.05f);
            } catch (Exception e) {
                log.warn("[Nex4x] Concession embargo tweak failed: " + e.getMessage());
            }
        }
    }

    private static void handleDeclaration(NegotiableItem item, String giver, String receiver,
                                          Nex4xManager mgr) {
        DeclarationManager dm = mgr.getDeclarationManager();
        if (item.getDeclarationType() == null) return;
        if (item.isWithdrawal()) {
            dm.withdraw(giver, receiver, item.getDeclarationType());
        } else {
            dm.declare(giver, receiver, item.getDeclarationType());
        }
    }
}
