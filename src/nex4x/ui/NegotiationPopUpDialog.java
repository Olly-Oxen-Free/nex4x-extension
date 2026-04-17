package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.agreements.AgreementType;
import nex4x.data.Nex4xSettings;
import nex4x.evaluation.DealEvaluator;
import nex4x.managers.Nex4xManager;
import nex4x.negotiation.*;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.List;

/**
 * The Negotiation Table popup — an Ashlib BasePopUpDialog that renders the
 * deal-building UI (spec §5.2.2). Replaces the broken vanilla
 * CustomDialogDelegate approach with a fully refreshable popup.
 *
 * Layout (single scrollable content):
 *   Section 1: Your Offer — items you're giving, with [x] remove buttons
 *   Section 2: Their Demand — items you want, with [x] remove buttons
 *   Section 3: Balance readout + AI assessment text
 *   Section 4: Item catalog — all 12 categories with [← Offer] [Request →] buttons
 *   Footer: [Auto-Negotiate] button, then [Send Proposal] / [Cancel] from Ashlib
 *
 * Opened via: BasePopUpDialog.popUpDialog(new NegotiationPopUpDialog(factionId), 620, 560)
 */
public class NegotiationPopUpDialog extends BasePopUpDialog {

    private static final Logger log = Global.getLogger(NegotiationPopUpDialog.class);

    // ── Button ID prefixes ────────────────────────────────────
    private static final String REMOVE_OFFER_PREFIX = "rmv_o_";
    private static final String REMOVE_REQUEST_PREFIX = "rmv_r_";
    private static final String ADD_OFFER_PREFIX = "add_o_";
    private static final String ADD_REQUEST_PREFIX = "add_r_";
    private static final String BTN_AUTO_NEGOTIATE = "auto_neg";

    // ── State ─────────────────────────────────────────────────
    private final String targetFactionId;
    private final String playerFactionId;
    private final DealPackage deal;
    private final DealEvaluator evaluator;
    private final ItemValuator valuator;
    private final AgreementType currentTier;
    private final boolean atWar;

    private boolean needsRefresh;

    public NegotiationPopUpDialog(String targetFactionId) {
        super("Negotiate \u2014 " + Global.getSector().getFaction(targetFactionId).getDisplayName());
        this.targetFactionId = targetFactionId;
        this.playerFactionId = Global.getSector().getPlayerFaction().getId();

        this.deal = new DealPackage(playerFactionId, targetFactionId);
        this.evaluator = new DealEvaluator();
        this.valuator = evaluator.getValuator();

        Nex4xManager mgr = Nex4xManager.getManager();
        this.currentTier = mgr != null
                ? mgr.getAgreementManager().getAllianceTier(playerFactionId, targetFactionId)
                : AgreementType.COLD_WAR;
        this.atWar = Global.getSector().getFaction(playerFactionId).isHostileTo(targetFactionId);

        setConfirmText("Send Proposal");
    }

    /**
     * Counter-proposal constructor: opens the table pre-filled from an AI proposal.
     * The aiDeal has proposer=AI, target=player. We swap perspectives:
     * AI's offers (what AI gives us) → our requests (what we want from them).
     * AI's requests (what AI wants) → our offers (what we give them).
     */
    public NegotiationPopUpDialog(String targetFactionId, DealPackage aiDeal) {
        this(targetFactionId);
        for (NegotiableItem item : aiDeal.getOffers()) {
            deal.addRequest(item);
        }
        for (NegotiableItem item : aiDeal.getRequests()) {
            deal.addOffer(item);
        }
    }

    // ── Content rendering ─────────────────────────────────────

    @Override
    public void createContentForDialog(TooltipMakerAPI info, float width) {
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        Color factionColor = targetFaction.getBaseUIColor();
        Color darkColor = targetFaction.getDarkUIColor();
        float pad = 10f;
        float sPad = 3f;

        // ── Your Offer ────────────────────────────────────────
        info.addSectionHeading("YOUR OFFER", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, 0);

        List<NegotiableItem> offers = deal.getOffers();
        if (offers.isEmpty()) {
            info.addPara("No items offered.", Misc.getGrayColor(), pad);
        } else {
            for (int i = 0; i < offers.size(); i++) {
                NegotiableItem item = offers.get(i);
                float val = valuator.evaluate(item, targetFactionId);
                String label = item.getDisplayLabel();
                String valStr = String.format("%.0f", val);
                info.addPara(label + "  (" + valStr + ")", sPad,
                        Misc.getHighlightColor(), label);
                info.addButton("[x]", REMOVE_OFFER_PREFIX + i,
                        Misc.getNegativeHighlightColor(), Misc.getDarkPlayerColor(),
                        30, 16, 2f);
            }
        }

        // ── Their Demand ──────────────────────────────────────
        info.addSectionHeading("THEIR DEMAND", factionColor, darkColor, Alignment.MID, pad);

        List<NegotiableItem> requests = deal.getRequests();
        if (requests.isEmpty()) {
            info.addPara("No items requested.", Misc.getGrayColor(), pad);
        } else {
            for (int i = 0; i < requests.size(); i++) {
                NegotiableItem item = requests.get(i);
                float val = valuator.evaluate(item, targetFactionId);
                String label = item.getDisplayLabel();
                String valStr = String.format("%.0f", val);
                info.addPara(label + "  (" + valStr + ")", sPad,
                        Misc.getHighlightColor(), label);
                info.addButton("[x]", REMOVE_REQUEST_PREFIX + i,
                        Misc.getNegativeHighlightColor(), darkColor,
                        30, 16, 2f);
            }
        }

        // ── Balance + Assessment ──────────────────────────────
        info.addSpacer(pad);
        renderBalanceSection(info);

        // ── Item Catalog ──────────────────────────────────────
        info.addSectionHeading("AVAILABLE ITEMS", factionColor, darkColor, Alignment.MID, pad);

        boolean ceasefireOnTable = deal.hasCeasefire();
        for (NegotiableItemType type : NegotiableItemType.values()) {
            boolean available = type.isAvailable(currentTier, atWar, ceasefireOnTable);
            Color labelColor = available ? Misc.getTextColor() : Misc.getGrayColor();

            info.addPara(type.displayName, labelColor, pad);

            if (!available) {
                info.addPara("  " + getUnavailableReason(type), Misc.getGrayColor(), 2f);
                continue;
            }

            addCatalogItems(info, type, sPad);
        }

        // Auto-Negotiate
        info.addSpacer(pad);
        info.addButton("Auto-Negotiate", BTN_AUTO_NEGOTIATE,
                factionColor, darkColor, Alignment.MID, CutStyle.ALL,
                width - pad * 4, 24, pad);
    }

    // ── Button dispatch ───────────────────────────────────────

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId == null) return;
        String id = buttonId.toString();

        if (id.startsWith(REMOVE_OFFER_PREFIX)) {
            int idx = parseIndex(id, REMOVE_OFFER_PREFIX);
            if (idx >= 0) {
                deal.removeOffer(idx);
                log.info("[Nex4x] Removed offer at index " + idx);
            }
            needsRefresh = true;
            return;
        }

        if (id.startsWith(REMOVE_REQUEST_PREFIX)) {
            int idx = parseIndex(id, REMOVE_REQUEST_PREFIX);
            if (idx >= 0) {
                deal.removeRequest(idx);
                log.info("[Nex4x] Removed request at index " + idx);
            }
            needsRefresh = true;
            return;
        }

        if (BTN_AUTO_NEGOTIATE.equals(id)) {
            runAutoNegotiate();
            needsRefresh = true;
            return;
        }

        if (id.startsWith(ADD_OFFER_PREFIX)) {
            String key = id.substring(ADD_OFFER_PREFIX.length());
            NegotiableItem item = createItemFromKey(key);
            if (item != null) {
                deal.addOffer(item);
                log.info("[Nex4x] Added offer: " + item.getDisplayLabel());
            }
            needsRefresh = true;
            return;
        }

        if (id.startsWith(ADD_REQUEST_PREFIX)) {
            String key = id.substring(ADD_REQUEST_PREFIX.length());
            NegotiableItem item = createItemFromKey(key);
            if (item != null) {
                deal.addRequest(item);
                log.info("[Nex4x] Added request: " + item.getDisplayLabel());
            }
            needsRefresh = true;
            return;
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (needsRefresh) {
            needsRefresh = false;
            removeUI();
            createUI(panelToInfluence);
        }
    }

    // ── Confirm: send proposal ────────────────────────────────

    @Override
    public void applyConfirmScript() {
        if (deal.isEmpty()) return;

        DealEvaluator.EvaluationResult result = evaluator.evaluate(deal);
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        String factionName = targetFaction.getDisplayName();

        if (result.accepted) {
            executeDeal();
            Global.getSector().getCampaignUI().addMessage(
                    factionName + " accepted the deal. " + result.reason,
                    Misc.getPositiveHighlightColor());
            log.info("[Nex4x] Deal accepted by " + targetFactionId + ": " + result.reason);
        } else {
            String msg = factionName + " rejected the deal: " + result.reason;
            if (result.vote != null) {
                msg += " " + result.vote.getSummary();
            }
            Global.getSector().getCampaignUI().addMessage(msg,
                    Misc.getNegativeHighlightColor());
            log.info("[Nex4x] Deal rejected by " + targetFactionId + ": " + result.reason);
        }
    }

    // ── Balance rendering ─────────────────────────────────────

    private void renderBalanceSection(TooltipMakerAPI info) {
        float balance = deal.getBalance(valuator);
        String balanceStr = String.format("Balance: %+.0f", balance);
        Color balanceColor;
        if (balance > 1000) {
            balanceColor = Misc.getPositiveHighlightColor();
        } else if (balance > -1000) {
            balanceColor = Misc.getHighlightColor();
        } else {
            balanceColor = Misc.getNegativeHighlightColor();
        }

        LabelAPI label = info.addPara(balanceStr, 0);
        label.setHighlight(balanceStr);
        label.setHighlightColor(balanceColor);

        if (deal.isEmpty()) {
            info.addPara("Add items to both sides to see an assessment.",
                    Misc.getGrayColor(), 3f);
        } else {
            info.addPara(getAssessmentText(balance), 3f);
        }
    }

    private String getAssessmentText(float balance) {
        boolean alwaysVisible = "always_visible".equals(Nex4xSettings.negotiationAssessmentMode);

        if (!alwaysVisible) {
            return "Your agents have insufficient insight into their decision-making.";
        }

        if (balance > 5000) {
            return "They would eagerly accept these terms.";
        } else if (balance > 1000) {
            return "They would likely accept.";
        } else if (balance > -1000) {
            return "The deal is borderline \u2014 could go either way.";
        } else if (balance > -5000) {
            return "They would likely reject. The terms are unfavorable to them.";
        } else {
            return "They would firmly reject. This is far below what they'd accept.";
        }
    }

    // ── Item catalog ──────────────────────────────────────────

    private void addCatalogItems(TooltipMakerAPI catalog, NegotiableItemType type, float pad) {
        float btnWidth = 70f;
        float btnHeight = 18f;

        switch (type) {
            case CREDITS:
                addDualButtons(catalog, "10,000 credits",
                        "credits_10000", btnWidth, btnHeight, pad);
                addDualButtons(catalog, "50,000 credits",
                        "credits_50000", btnWidth, btnHeight, pad);
                break;

            case TRIBUTE:
                addDualButtons(catalog, "5,000 cr/cycle (90 days)",
                        "tribute_5000", btnWidth, btnHeight, pad);
                break;

            case AGREEMENTS:
                for (AgreementType at : getAvailableAgreementTypes()) {
                    addDualButtons(catalog, at.displayName,
                            "agree_" + at.name(), btnWidth, btnHeight, pad);
                }
                break;

            case PEACE_TERMS:
                addDualButtons(catalog, "Ceasefire",
                        "ceasefire", btnWidth, btnHeight, pad);
                addDualButtons(catalog, "Peace Treaty",
                        "peace_treaty", btnWidth, btnHeight, pad);
                break;

            case WAR_DECLARATION:
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    if (faction.getId().equals(playerFactionId)) continue;
                    if (faction.getId().equals(targetFactionId)) continue;
                    if (faction.isNeutralFaction()) continue;
                    if (!hasMarkets(faction.getId())) continue;
                    addDualButtons(catalog, "War on " + faction.getDisplayName(),
                            "war_" + faction.getId(), btnWidth, btnHeight, pad);
                }
                break;

            case TERRITORY:
                for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (market.getFactionId().equals(playerFactionId)) {
                        catalog.addPara("  " + market.getName()
                                + " (yours, size " + market.getSize() + ")", pad);
                        catalog.addButton("[\u2190 Offer]",
                                ADD_OFFER_PREFIX + "territory_" + market.getId(),
                                Misc.getButtonTextColor(), Misc.getDarkPlayerColor(),
                                btnWidth, btnHeight, 2f);
                    } else if (market.getFactionId().equals(targetFactionId)) {
                        catalog.addPara("  " + market.getName()
                                + " (theirs, size " + market.getSize() + ")", pad);
                        catalog.addButton("[Request \u2192]",
                                ADD_REQUEST_PREFIX + "territory_" + market.getId(),
                                Misc.getButtonTextColor(), Misc.getDarkPlayerColor(),
                                btnWidth, btnHeight, 2f);
                    }
                }
                break;

            case COMMODITIES:
                String[] commodities = {"supplies", "fuel", "metals", "rare_metals",
                        "organics", "food", "hand_weapons"};
                for (String c : commodities) {
                    addDualButtons(catalog, "500 \u00d7 " + c,
                            "commodity_" + c, btnWidth, btnHeight, pad);
                }
                break;

            case KNOWLEDGE:
                addDualButtons(catalog, "Blueprint package",
                        "knowledge_blueprints", btnWidth, btnHeight, pad);
                break;

            case INTEL:
                addDualButtons(catalog, "Map data",
                        "intel_map", btnWidth, btnHeight, pad);
                addDualButtons(catalog, "Fleet intel",
                        "intel_fleet", btnWidth, btnHeight, pad);
                break;

            case CONTRACTS:
                addDualButtons(catalog, "Mercenary contract (180 days)",
                        "contract_mercenary", btnWidth, btnHeight, pad);
                break;

            case CONCESSIONS:
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    if (faction.getId().equals(playerFactionId)) continue;
                    if (faction.getId().equals(targetFactionId)) continue;
                    if (faction.isNeutralFaction()) continue;
                    if (!hasMarkets(faction.getId())) continue;
                    addDualButtons(catalog, "Embargo " + faction.getDisplayName(),
                            "concession_embargo_" + faction.getId(),
                            btnWidth, btnHeight, pad);
                }
                break;

            case PRISONERS:
                addDualButtons(catalog, "Prisoner exchange",
                        "prisoner_exchange", btnWidth, btnHeight, pad);
                break;
        }
    }

    /**
     * Adds a labeled row with [← Offer] and [Request →] buttons.
     * The itemKey is shared — prefixed with ADD_OFFER_ or ADD_REQUEST_ as button IDs.
     */
    private void addDualButtons(TooltipMakerAPI tooltip, String label,
                                String itemKey, float btnWidth, float btnHeight, float pad) {
        Color baseColor = Misc.getButtonTextColor();
        Color darkColor = Misc.getDarkPlayerColor();

        tooltip.addPara("  " + label, pad);
        tooltip.addButton("[\u2190 Offer]", ADD_OFFER_PREFIX + itemKey,
                baseColor, darkColor, btnWidth, btnHeight, 2f);
        tooltip.addButton("[Request \u2192]", ADD_REQUEST_PREFIX + itemKey,
                baseColor, darkColor, btnWidth, btnHeight, 2f);
    }

    // ── Item creation from button key ─────────────────────────

    private NegotiableItem createItemFromKey(String key) {
        if (key.startsWith("credits_")) {
            float amount = parseFloat(key.substring("credits_".length()));
            return NegotiableItem.credits(amount);
        }

        if (key.startsWith("tribute_")) {
            float amount = parseFloat(key.substring("tribute_".length()));
            return NegotiableItem.tribute(amount, 90);
        }

        if ("ceasefire".equals(key)) return NegotiableItem.ceasefire();
        if ("peace_treaty".equals(key)) return NegotiableItem.peaceTreaty();

        if (key.startsWith("agree_")) {
            String typeName = key.substring("agree_".length());
            try {
                AgreementType type = AgreementType.valueOf(typeName);
                return NegotiableItem.agreement(type);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        if (key.startsWith("war_")) {
            return NegotiableItem.warDeclaration(key.substring("war_".length()));
        }

        if (key.startsWith("territory_")) {
            return NegotiableItem.territory(key.substring("territory_".length()));
        }

        if (key.startsWith("commodity_")) {
            return NegotiableItem.commodity(key.substring("commodity_".length()), 500);
        }

        if (key.startsWith("knowledge_")) {
            return NegotiableItem.knowledge(key.substring("knowledge_".length()));
        }

        if (key.startsWith("intel_")) {
            return NegotiableItem.intel(key.substring("intel_".length()));
        }

        if (key.startsWith("contract_")) {
            return NegotiableItem.contract(key.substring("contract_".length()), 180);
        }

        if (key.startsWith("concession_embargo_")) {
            String factionId = key.substring("concession_embargo_".length());
            return NegotiableItem.concession(factionId, "embargo");
        }

        if ("prisoner_exchange".equals(key)) {
            return NegotiableItem.prisoner("exchange");
        }

        log.warn("[Nex4x] Unknown item key: " + key);
        return null;
    }

    // ── Auto-Negotiate ────────────────────────────────────────

    private void runAutoNegotiate() {
        AutoNegotiator auto = new AutoNegotiator(evaluator);

        if (!deal.getRequests().isEmpty() && deal.getOffers().isEmpty()) {
            AutoNegotiator.AutoNegotiateResult result =
                    auto.suggestCounterForRequests(deal.getRequests(),
                            deal.getProposerFactionId(), deal.getTargetFactionId());
            if (result.possible && result.deal != null) {
                deal.clearOffers();
                for (NegotiableItem item : result.deal.getOffers()) {
                    deal.addOffer(item);
                }
            }
        } else if (!deal.getOffers().isEmpty() && deal.getRequests().isEmpty()) {
            AutoNegotiator.AutoNegotiateResult result =
                    auto.suggestCounterForOffers(deal.getOffers(),
                            deal.getProposerFactionId(), deal.getTargetFactionId());
            if (result.possible && result.deal != null) {
                deal.clearRequests();
                for (NegotiableItem item : result.deal.getRequests()) {
                    deal.addRequest(item);
                }
            }
        }
    }

    // ── Agreement helpers ─────────────────────────────────────

    private AgreementType[] getAvailableAgreementTypes() {
        AgreementType next = currentTier.getNextAllianceTier();
        if (next != null) {
            if (currentTier == AgreementType.DEFENSIVE_PACT) {
                return new AgreementType[]{
                        AgreementType.MILITARY_PARTNERSHIP,
                        AgreementType.ECONOMIC_PARTNERSHIP,
                        AgreementType.TRADE_AGREEMENT
                };
            }
            return new AgreementType[]{next, AgreementType.TRADE_AGREEMENT};
        }
        return new AgreementType[]{AgreementType.TRADE_AGREEMENT};
    }

    private String getUnavailableReason(NegotiableItemType type) {
        if (atWar && type != NegotiableItemType.PEACE_TERMS) {
            if (!deal.hasCeasefire()) {
                return "Ceasefire must be on the table first.";
            }
        }
        if (type.minAllianceTier > currentTier.tier) {
            return "Requires " + getMinTierName(type.minAllianceTier) + " or higher.";
        }
        if (type == NegotiableItemType.PEACE_TERMS && !atWar) {
            return "Not at war.";
        }
        return "Not available.";
    }

    private String getMinTierName(int tier) {
        switch (tier) {
            case 1: return "Non-Aggression Pact";
            case 2: return "Defensive Pact";
            case 3: return "Military Partnership";
            default: return "higher tier agreement";
        }
    }

    // ── Deal execution ────────────────────────────────────────

    private void executeDeal() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        for (NegotiableItem item : deal.getOffers()) {
            executeItem(item, playerFactionId, targetFactionId, mgr);
        }
        for (NegotiableItem item : deal.getRequests()) {
            executeItem(item, targetFactionId, playerFactionId, mgr);
        }
    }

    private void executeItem(NegotiableItem item, String giver, String receiver,
                              Nex4xManager mgr) {
        switch (item.getType()) {
            case AGREEMENTS:
                if (item.getAgreementType() != null) {
                    mgr.getAgreementManager().createAgreement(
                            playerFactionId, targetFactionId, item.getAgreementType());
                }
                break;
            case PEACE_TERMS:
                if (item.isCeasefire()) {
                    try {
                        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);
                        FactionAPI targetFac = Global.getSector().getFaction(targetFactionId);
                        exerelin.campaign.DiplomacyManager.createDiplomacyEventV2(
                                playerFac, targetFac, "ceasefire", null);
                    } catch (Exception e) {
                        Global.getSector().getFaction(playerFactionId)
                                .setRelationship(targetFactionId, 0);
                    }
                }
                break;
            default:
                break;
        }
    }

    // ── Utility ───────────────────────────────────────────────

    private boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return true;
        }
        return false;
    }

    private int parseIndex(String id, String prefix) {
        try {
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
