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
            case TRIBUTE: {
                mgr.getMemoryManager().createMemory("tribute_obligation", giver, receiver,
                        String.format("%.0f cr/cycle for %.0f days", item.getAmount(), item.getDurationDays()));
                PressureManager.getOrCreate().applyEvent(giver, receiver, PressureSource.ECONOMIC_EXPORT, 15f);
                int perMonth = (int) item.getAmount();
                float durationDays = item.getDurationDays();
                if (perMonth > 0 && durationDays > 0 && Global.getSector() != null) {
                    TributePaymentScript script =
                            new TributePaymentScript(giver, receiver, perMonth, durationDays);
                    Global.getSector().addScript(script);
                    log.info("[Nex4x] Tribute scheduled: " + giver + " -> " + receiver
                            + " " + perMonth + " cr/cycle for " + (int) durationDays + "d");
                }
                break;
            }
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
                handleKnowledge(item, giver, receiver, playerId, mgr);
                break;
            case INTEL:
                handleIntel(item, giver, receiver, playerId, mgr);
                PressureManager.getOrCreate().applyEvent(giver, receiver, PressureSource.EVENT, 10f);
                break;
            case CONTRACTS:
                // No negotiation-level contract system exists: mercenary/arms/security contracts are
                // owned by ContractAuctionManager, a separate campaign flow with its own auction UI
                // and lifecycle. CONTRACTS is intentionally kept out of the negotiable catalog
                // (NegotiableItemCatalog.idsForType returns empty for it). This case only fires for
                // legacy/AI-authored deals; record a memory so the exchange is not silently lost.
                mgr.getMemoryManager().createMemory("contract", giver, receiver,
                        item.getTargetId() + " (" + (int) item.getDurationDays() + "d)");
                break;
            case CONCESSIONS:
                handleConcession(item, giver, receiver, playerId);
                break;
            case PRISONERS:
                handlePrisoners(item, giver, receiver, playerId, mgr);
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
        // All concession sub-types apply the relation nudge (giver worsens ties with the third
        // party as a favour to the receiver), scaled by sub-type severity.
        float nudge = concessionNudge(item.getSecondaryId());
        try {
            float cur = g.getRelationship(third);
            g.setRelationship(third, cur - nudge);
            log.info("[Nex4x] Concession '" + item.getSecondaryId() + "': " + giver
                    + " -" + nudge + " toward " + third + " (for " + receiver + ")");
        } catch (Exception e) {
            log.warn("[Nex4x] Concession relation tweak failed: " + e.getMessage());
        }
    }

    /** Relation penalty the conceding faction takes toward the third party, by sub-type. */
    private static float concessionNudge(String subType) {
        if ("sever".equals(subType)) return 0.10f;
        if ("denounce".equals(subType)) return 0.06f;
        return 0.05f; // embargo (default)
    }

    // ── Knowledge (blueprint transfer) ─────────────────────────

    private static void handleKnowledge(NegotiableItem item, String giver, String receiver,
                                        String playerId, Nex4xManager mgr) {
        FactionAPI giverFaction    = Global.getSector().getFaction(giver);
        FactionAPI receiverFaction = Global.getSector().getFaction(receiver);
        if (giverFaction == null || receiverFaction == null) {
            log.warn("[Nex4x] Knowledge deal: null faction(s) giver=" + giver + " receiver=" + receiver);
            return;
        }
        boolean playerIsReceiver = playerId.equals(receiver);
        String[] pick = pickTransferableSpec(giverFaction, receiverFaction);
        if (pick == null) {
            // Nothing transferable — refund the receiving party so nobody pays for nothing.
            float refund = new ItemValuator().getBaseValue(item);
            log.info("[Nex4x] Knowledge deal: no transferable spec from " + giver + " to "
                    + receiver + "; refunding base value " + (int) refund);
            if (playerIsReceiver && refund > 0) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().add((int) refund);
                AddRemoveCommodity.addCreditsGainText((int) refund, null);
            }
            mgr.getMemoryManager().createMemory("tech_transfer", giver, receiver, "no-op refund");
            return;
        }
        String kind = pick[0];
        String id   = pick[1];
        try {
            if ("ship".equals(kind))        receiverFaction.addKnownShip(id, playerIsReceiver);
            else if ("weapon".equals(kind)) receiverFaction.addKnownWeapon(id, playerIsReceiver);
            else if ("fighter".equals(kind)) receiverFaction.addKnownFighter(id, playerIsReceiver);
            log.info("[Nex4x] Knowledge transfer: " + kind + " '" + id + "' " + giver
                    + " -> " + receiver);
            if (playerIsReceiver) {
                Global.getSector().getCampaignUI().addMessage(
                        "Acquired blueprint: " + id + " from " + giverFaction.getDisplayName(),
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor());
            }
        } catch (Throwable tr) {
            log.warn("[Nex4x] Knowledge addKnown failed: " + tr.getMessage());
        }
        mgr.getMemoryManager().createMemory("tech_transfer", giver, receiver, kind + ":" + id);
    }

    /**
     * Picks a learnable, not-yet-known spec the {@code from} faction knows but {@code to} does not.
     * Prefers mid-value ships (never capitals), then weapons, then fighters. Returns
     * {@code {kind, id}} or {@code null} if nothing is transferable.
     */
    private static String[] pickTransferableSpec(FactionAPI from, FactionAPI to) {
        // Ships — exclude capitals, prefer cruiser → destroyer → frigate, mid-value within bucket.
        java.util.List<String> cruisers   = new java.util.ArrayList<String>();
        java.util.List<String> destroyers = new java.util.ArrayList<String>();
        java.util.List<String> frigates   = new java.util.ArrayList<String>();
        java.util.Set<String> toShips = to.getKnownShips();
        for (String hullId : from.getKnownShips()) {
            if (toShips.contains(hullId)) continue;
            try {
                com.fs.starfarer.api.combat.ShipHullSpecAPI spec =
                        Global.getSettings().getHullSpec(hullId);
                if (spec == null) continue;
                com.fs.starfarer.api.combat.ShipAPI.HullSize sz = spec.getHullSize();
                if (sz == com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER) cruisers.add(hullId);
                else if (sz == com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER) destroyers.add(hullId);
                else if (sz == com.fs.starfarer.api.combat.ShipAPI.HullSize.FRIGATE) frigates.add(hullId);
                // CAPITAL_SHIP / FIGHTER / DEFAULT intentionally skipped
            } catch (Throwable ignore) {}
        }
        String ship = median(cruisers);
        if (ship == null) ship = median(destroyers);
        if (ship == null) ship = median(frigates);
        if (ship != null) return new String[]{"ship", ship};

        // Weapons — mid-value.
        java.util.List<String> weapons = new java.util.ArrayList<String>();
        java.util.Set<String> toWeapons = to.getKnownWeapons();
        for (String wid : from.getKnownWeapons()) {
            if (!toWeapons.contains(wid)) weapons.add(wid);
        }
        String weapon = median(weapons);
        if (weapon != null) return new String[]{"weapon", weapon};

        // Fighters — any.
        java.util.List<String> fighters = new java.util.ArrayList<String>();
        java.util.Set<String> toFighters = to.getKnownFighters();
        for (String fid : from.getKnownFighters()) {
            if (!toFighters.contains(fid)) fighters.add(fid);
        }
        String fighter = median(fighters);
        if (fighter != null) return new String[]{"fighter", fighter};

        return null;
    }

    /** Deterministic mid-of-list pick (approximates a mid-value choice). */
    private static String median(java.util.List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        java.util.Collections.sort(ids);
        return ids.get(ids.size() / 2);
    }

    // ── Intel (real dossier) ───────────────────────────────────

    private static void handleIntel(NegotiableItem item, String giver, String receiver,
                                    String playerId, Nex4xManager mgr) {
        String subType = item.getTargetId() != null ? item.getTargetId() : "basic";
        if (playerId.equals(receiver)) {
            try {
                FactionDossierIntel intel = new FactionDossierIntel(giver, subType);
                intel.setImportant(false);
                Global.getSector().getIntelManager().addIntel(intel);
                Global.getSector().getCampaignUI().addMessage(
                        "Received an intelligence dossier on "
                                + Global.getSector().getFaction(giver).getDisplayName() + ".",
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor());
            } catch (Throwable t) {
                log.warn("[Nex4x] Intel dossier creation failed: " + t.getMessage());
            }
        }
        mgr.getMemoryManager().createMemory("intel_exchange", giver, receiver, subType);
    }

    // ── Prisoners (crew transfer) ──────────────────────────────

    private static final int DEFAULT_PRISONER_CREW = 50;

    private static void handlePrisoners(NegotiableItem item, String giver, String receiver,
                                        String playerId, Nex4xManager mgr) {
        int crew = item.getAmount() > 0 ? (int) item.getAmount() : DEFAULT_PRISONER_CREW;
        com.fs.starfarer.api.campaign.CargoAPI cargo =
                Global.getSector().getPlayerFleet().getCargo();
        if (playerId.equals(receiver)) {
            cargo.addCrew(crew);
            Global.getSector().getCampaignUI().addMessage(
                    "Received " + crew + " freed crew from prisoner exchange.",
                    com.fs.starfarer.api.util.Misc.getPositiveHighlightColor());
        } else if (playerId.equals(giver)) {
            int avail = cargo.getCrew();
            int give = Math.min(crew, avail);
            if (give > 0) cargo.removeCrew(give);
            Global.getSector().getCampaignUI().addMessage(
                    "Handed over " + give + " prisoners.",
                    com.fs.starfarer.api.util.Misc.getNegativeHighlightColor());
        } else {
            log.info("[Nex4x] NPC prisoner exchange (memory only): " + giver + " -> " + receiver);
        }
        mgr.getMemoryManager().createMemory("prisoner_exchange", giver, receiver,
                item.getTargetId() != null ? item.getTargetId() : "exchange");
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
