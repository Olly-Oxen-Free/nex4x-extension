package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.MutableValue;
import nex4x.agreements.Agreement;
import nex4x.agreements.AgreementManager;
import nex4x.influence.InfluenceManager;
import nex4x.managers.Nex4xManager;
import nex4x.util.FactionPowerRankings;
import nex4x.util.Nex4xRelations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles player purchase of intelligence tips from a viceroy.
 * Task 21b: real credit deduction + intel item grant.
 */
public class IntelPurchaseHandler {

    public enum IntelTier {
        LOCATION_TIP("System Location Tip", 500),
        FACTION_GOALS("Faction Strategic Goals", 2500),
        BOUNTY_TARGETS("Active Bounty Roster", 1000);

        public final String displayName;
        public final int price;

        IntelTier(String displayName, int price) {
            this.displayName = displayName;
            this.price = price;
        }
    }

    // Legacy price constants for smoke-test compatibility.
    public static final int PRICE_LOCATION_TIP    = IntelTier.LOCATION_TIP.price;
    public static final int PRICE_FACTION_GOALS   = IntelTier.FACTION_GOALS.price;
    public static final int PRICE_BOUNTY_TARGETS  = IntelTier.BOUNTY_TARGETS.price;

    /** Called from ViceroyDialog main menu to print the offering text. */
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("Intel on offer from " + market.getFaction().getDisplayName() + ":");
        text.addPara(" • " + IntelTier.LOCATION_TIP.displayName
                + " — " + IntelTier.LOCATION_TIP.price + " credits");
        text.addPara(" • " + IntelTier.FACTION_GOALS.displayName
                + " — " + IntelTier.FACTION_GOALS.price + " credits");
        text.addPara(" • " + IntelTier.BOUNTY_TARGETS.displayName
                + " — " + IntelTier.BOUNTY_TARGETS.price + " credits");
    }

    /**
     * Deducts credits and grants an intel-journal entry.
     * Called from ViceroyDialog after the player selects a tier.
     */
    public static void purchase(InteractionDialogAPI dialog, MarketAPI market, IntelTier tier) {
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;
        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();

        if (credits.get() < tier.price) {
            if (text != null) {
                text.addPara("Insufficient credits. " + tier.displayName
                        + " costs " + tier.price + " credits.");
            }
            return;
        }

        credits.subtract(tier.price);
        AddRemoveCommodity.addCreditsLossText(tier.price, text);

        ViceroyIntelItem item = buildIntelItem(tier, market.getFactionId());
        Global.getSector().getIntelManager().addIntel(item, false, text);

        if (text != null) {
            text.addPara("Purchased: " + tier.displayName
                    + ". Intel added to your journal.");
        }
    }

    // ── Snapshot construction ──────────────────────────────────────────────

    /** Number of top-ranked factions to report relations against. */
    private static final int MAX_MAJORS = 8;

    /**
     * Builds a {@link ViceroyIntelItem} with a snapshot of the target faction's diplomatic
     * position captured now. Full payload (agreements/influence/rank) only for FACTION_GOALS.
     */
    private static ViceroyIntelItem buildIntelItem(IntelTier tier, String targetFactionId) {
        boolean full = (tier == IntelTier.FACTION_GOALS);

        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        List<String> majors = majorFactionIds();

        List<String> relationLines = new ArrayList<String>();
        List<String> warLines = new ArrayList<String>();
        if (target != null) {
            for (String otherId : majors) {
                if (otherId.equals(targetFactionId)) continue;
                FactionAPI other = Global.getSector().getFaction(otherId);
                if (other == null) continue;
                float rawRel = target.getRelationship(otherId);
                int pct = Nex4xRelations.toPercentInt(rawRel);
                String rl = Nex4xRelations.repLevel(rawRel).getDisplayName();
                relationLines.add(other.getDisplayName() + ": " + pct + " (" + rl + ")");
                if (target.isHostileTo(other)) {
                    warLines.add("At war with " + other.getDisplayName());
                }
            }
        }

        List<String> agreementLines = null;
        String influenceStr = null;
        String rankStr = null;
        if (full) {
            agreementLines = new ArrayList<String>();
            try {
                Nex4xManager mgr = Nex4xManager.getManager();
                if (mgr != null) {
                    AgreementManager am = mgr.getAgreementManager();
                    for (Agreement a : am.getAgreementsFor(targetFactionId)) {
                        String otherFid = a.getOtherFaction(targetFactionId);
                        FactionAPI other = otherFid != null
                                ? Global.getSector().getFaction(otherFid) : null;
                        String otherName = other != null ? other.getDisplayName()
                                : (otherFid != null ? otherFid : "?");
                        agreementLines.add(a.getType().displayName + " with " + otherName);
                    }
                }
            } catch (Throwable t) {
                Global.getLogger(IntelPurchaseHandler.class)
                        .warn("[Nex4x] Intel snapshot: agreements unavailable: " + t.getMessage());
            }

            try {
                InfluenceManager im = InfluenceManager.get();
                if (im != null) {
                    influenceStr = Math.round(im.getBalance(targetFactionId)) + " influence";
                }
            } catch (Throwable t) {
                Global.getLogger(IntelPurchaseHandler.class)
                        .warn("[Nex4x] Intel snapshot: influence unavailable: " + t.getMessage());
            }

            try {
                FactionPowerRankings.rebuild();
                rankStr = FactionPowerRankings.getRankLabel(targetFactionId);
            } catch (Throwable t) {
                Global.getLogger(IntelPurchaseHandler.class)
                        .warn("[Nex4x] Intel snapshot: power rank unavailable: " + t.getMessage());
            }
        }

        return new ViceroyIntelItem(tier, targetFactionId,
                relationLines, warLines, agreementLines, influenceStr, rankStr);
    }

    /** Top {@link #MAX_MAJORS} market-owning factions (plus the player) by composite power. */
    private static List<String> majorFactionIds() {
        Set<String> withMarkets = new LinkedHashSet<String>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m == null || m.getFaction() == null) continue;
            FactionAPI f = m.getFaction();
            if (f.isNeutralFaction()) continue;
            withMarkets.add(f.getId());
        }

        try {
            FactionPowerRankings.rebuild();
        } catch (Throwable t) {
            // Fall through — unranked factions sort as 0 and simply come last.
        }

        List<String> ids = new ArrayList<String>(withMarkets);
        Collections.sort(ids, new Comparator<String>() {
            public int compare(String a, String b) {
                return Float.compare(power(b), power(a));
            }
            private float power(String fid) {
                return FactionPowerRankings.economicScore(fid)
                        + FactionPowerRankings.militaryScore(fid)
                        + FactionPowerRankings.expansionScore(fid);
            }
        });

        List<String> majors = new ArrayList<String>();
        for (String id : ids) {
            majors.add(id);
            if (majors.size() >= MAX_MAJORS) break;
        }

        // Always include the player faction's standing even if it is not a top power.
        String playerId = Global.getSector().getPlayerFaction().getId();
        if (!majors.contains(playerId)) majors.add(playerId);
        return majors;
    }
}
