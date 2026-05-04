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
import nex4x.leaders.DialogueSystem;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.Personality;
import nex4x.leaders.ReputationTier;
import nex4x.leaders.Situation;
import nex4x.managers.Nex4xManager;
import nex4x.negotiation.*;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merged negotiation UI: Civ-style leader header + mood, bilateral pressure readout,
 * and the full DealPackage / DealEvaluator deal table (formerly NegotiationPopUpDialog).
 */
public class NegotiationPanel extends BasePopUpDialog {

    private static final Logger log = Global.getLogger(NegotiationPanel.class);

    /** Opens the negotiation popup with size derived from the current screen (reduces clipping). */
    public static void openScaled(String targetFactionId, boolean viceroyMode) {
        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();
        int w = (int) Math.min(1100f, Math.max(640f, sw * 0.7f));
        int h = (int) Math.min(780f, Math.max(560f, sh * 0.72f));
        BasePopUpDialog.popUpDialog(new NegotiationPanel(targetFactionId, viceroyMode), w, h);
    }

    // ── Button ID prefixes ────────────────────────────────────
    private static final String REMOVE_OFFER_PREFIX = "rmv_o_";
    private static final String REMOVE_REQUEST_PREFIX = "rmv_r_";
    private static final String ADD_OFFER_PREFIX = "add_o_";
    private static final String ADD_REQUEST_PREFIX = "add_r_";
    private static final String BTN_AUTO_NEGOTIATE = "auto_neg";
    private static final String BTN_DECLARE_WAR    = "btn_declare_war";
    private static final String BTN_DENOUNCE       = "btn_denounce";

    // ── State ─────────────────────────────────────────────────
    private final String targetFactionId;
    private final String playerFactionId;
    private final DealPackage deal;
    private final DealEvaluator evaluator;
    private final ItemValuator valuator;
    private final AgreementType currentTier;
    private final boolean atWar;
    private final boolean viceroyMode;
    private final boolean viaViceroyAgreement;

    private final SessionMood mood;
    private final LeaderProfile leader;

    private final DealProposal dealProposal;
    private final NegotiableItemCatalog catalog = new NegotiableItemCatalog();

    private boolean needsRefresh;

    private static final String CAT_TOGGLE_PREFIX = "cat_toggle_";
    private final Set<NegotiableItemType> expandedCategories = new HashSet<NegotiableItemType>();

    public NegotiationPanel(String targetFactionId) {
        this(targetFactionId, false);
    }

    public NegotiationPanel(String targetFactionId, boolean viceroyMode) {
        super(negotiateTitle(targetFactionId));
        this.targetFactionId = targetFactionId;
        this.playerFactionId = Global.getSector().getPlayerFaction().getId();
        this.viceroyMode = viceroyMode;
        this.viaViceroyAgreement = viceroyMode;

        this.deal = new DealPackage(playerFactionId, targetFactionId);
        this.dealProposal = new DealProposal(playerFactionId, targetFactionId);
        this.evaluator = new DealEvaluator();
        this.valuator = evaluator.getValuator();

        Nex4xManager mgr = Nex4xManager.getManager();
        this.currentTier = mgr != null
                ? mgr.getAgreementManager().getAllianceTier(playerFactionId, targetFactionId)
                : AgreementType.COLD_WAR;
        FactionAPI targetFac = Global.getSector().getFaction(targetFactionId);
        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);
        this.atWar = targetFac != null && playerFac != null && playerFac.isHostileTo(targetFac);

        this.leader = Nex4xManager.getOrCreateManager().getLeaderRegistry().getProfile(targetFactionId);
        this.mood = new SessionMood();

        setConfirmText("Send Proposal");

        // Expand first available category by default
        for (NegotiableItemType type : NegotiableItemType.values()) {
            if (viceroyMode && isViceroyCatalogTypeExcluded(type)) continue;
            if (type == NegotiableItemType.DECLARATIONS) continue;
            if (type.isAvailable(currentTier, atWar, false)) {
                expandedCategories.add(type);
                break;
            }
        }
    }

    /**
     * Counter-proposal constructor: opens the table pre-filled from an AI proposal.
     * The aiDeal has proposer=AI, target=player. We swap perspectives:
     * AI's offers (what AI gives us) → our requests (what we want from them).
     * AI's requests (what AI wants) → our offers (what we give them).
     */
    public NegotiationPanel(String targetFactionId, DealPackage aiDeal) {
        this(targetFactionId, false);
        for (NegotiableItem item : aiDeal.getOffers()) {
            deal.addRequest(item);
        }
        for (NegotiableItem item : aiDeal.getRequests()) {
            deal.addOffer(item);
        }
    }

    // ── Content rendering ─────────────────────────────────────

    @Override
    public void createUI(CustomPanelAPI panel) {
        createHeaader(panel); // Ashlib title bar — sets this.y
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        if (targetFaction == null) {
            log.error("[Nex4x] NegotiationPanel: no FactionAPI for " + targetFactionId);
            return;
        }

        float pw    = panel.getPosition().getWidth();
        float ph    = panel.getPosition().getHeight();
        float pad   = 10f;
        float colGap = 8f;
        float contentW = pw - pad * 2f;
        float curY  = y + pad;

        curY = buildLeaderHeader(panel, pad, curY, contentW, targetFaction);
        curY = buildDealColumns(panel, pad, curY, contentW, colGap, targetFaction);
        curY = buildBalanceBar(panel, pad, curY, contentW);
        buildCatalog(panel, pad, curY, contentW, ph - curY - 46f, targetFaction);

        createConfirmAndCancelSection(panel);
    }

    // ── Button dispatch ───────────────────────────────────────

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId == null) return;
        String id = buttonId.toString();

        if (id.startsWith(CAT_TOGGLE_PREFIX)) {
            String typeName = id.substring(CAT_TOGGLE_PREFIX.length());
            try {
                NegotiableItemType type = NegotiableItemType.valueOf(typeName);
                if (!expandedCategories.remove(type)) {
                    expandedCategories.add(type);
                }
            } catch (IllegalArgumentException ignored) {}
            needsRefresh = true;
            return;
        }

        if (id.startsWith(REMOVE_OFFER_PREFIX)) {
            int idx = parseIndex(id, REMOVE_OFFER_PREFIX);
            if (idx >= 0 && idx < deal.getOffers().size()) {
                String removedId = deal.getOffers().get(idx).getId();
                deal.removeOffer(idx);
                if (removedId != null) {
                    dealProposal.applyMutation(DealMutation.removeOffer(removedId), catalog);
                }
                log.info("[Nex4x] Removed offer at index " + idx);
            }
            needsRefresh = true;
            return;
        }

        if (id.startsWith(REMOVE_REQUEST_PREFIX)) {
            int idx = parseIndex(id, REMOVE_REQUEST_PREFIX);
            if (idx >= 0 && idx < deal.getRequests().size()) {
                String removedId = deal.getRequests().get(idx).getId();
                deal.removeRequest(idx);
                if (removedId != null) {
                    dealProposal.applyMutation(DealMutation.removeRequest(removedId), catalog);
                }
                log.info("[Nex4x] Removed request at index " + idx);
            }
            needsRefresh = true;
            return;
        }

        if (BTN_AUTO_NEGOTIATE.equals(id)) {
            runAutoNegotiate();
            mood.apply(SessionMood.Event.AUTO_BALANCE, leader.getPersonality());
            needsRefresh = true;
            return;
        }

        if (BTN_DECLARE_WAR.equals(id)) {
            Nex4xManager mgr = Nex4xManager.getOrCreateManager();
            mgr.getExecutor(playerFactionId).declareWarPlayer(targetFactionId);
            Global.getSector().getCampaignUI().addMessage(
                    "War declared on " + factionName(targetFactionId) + ".",
                    Misc.getNegativeHighlightColor());
            removeUI();
            return;
        }

        if (BTN_DENOUNCE.equals(id)) {
            Nex4xManager mgr = Nex4xManager.getOrCreateManager();
            mgr.getDeclarationManager().declareDenouncement(playerFactionId, targetFactionId);
            Global.getSector().getCampaignUI().addMessage(
                    "You publicly denounce " + factionName(targetFactionId) + ".",
                    Misc.getNegativeHighlightColor());
            removeUI();
            return;
        }

        if (id.startsWith(ADD_OFFER_PREFIX)) {
            String key = id.substring(ADD_OFFER_PREFIX.length());
            NegotiableItem item = createItemFromKey(key);
            if (item != null) {
                if (item.getId() == null) item.setId(key);
                deal.addOffer(item);
                dealProposal.applyMutation(
                        DealMutation.addOffer(item.getId(), (int) item.getAmount()), catalog);
                log.info("[Nex4x] Added offer: " + item.getDisplayLabel());
            }
            needsRefresh = true;
            return;
        }

        if (id.startsWith(ADD_REQUEST_PREFIX)) {
            String key = id.substring(ADD_REQUEST_PREFIX.length());
            NegotiableItem item = createItemFromKey(key);
            if (item != null) {
                if (item.getId() == null) item.setId(key);
                deal.addRequest(item);
                dealProposal.applyMutation(
                        DealMutation.addRequest(item.getId(), (int) item.getAmount()), catalog);
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
        if (targetFaction == null) {
            log.error("[Nex4x] applyConfirmScript: missing faction " + targetFactionId);
            return;
        }
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

    // ── Balance bar ───────────────────────────────────────────

    private float buildBalanceBar(CustomPanelAPI panel, float x, float y, float contentW) {
        float barH = 18f;
        float pad  = 6f;
        float innerW = contentW - pad * 2f;

        float rawBalance = deal.getBalance(valuator);
        float normalized = Math.max(-1f, Math.min(1f, rawBalance / 10000f));

        BalanceBarPlugin plugin = new BalanceBarPlugin(innerW, barH, normalized);
        CustomPanelAPI barPanel = Global.getSettings().createCustom(innerW, barH, plugin);
        plugin.attach(barPanel);
        panel.addComponent((com.fs.starfarer.api.ui.UIComponentAPI) barPanel).inTL(x + pad, y + 14f);

        // "Favors you" label — left
        TooltipMakerAPI leftLabel = panel.createUIElement(contentW / 2f, 14f, false);
        leftLabel.addPara("◄ Favors you", new Color(126, 200, 227, 255), 0f);
        panel.addUIElement(leftLabel).inTL(x, y);

        // "Favors them" label — right
        TooltipMakerAPI rightLabel = panel.createUIElement(contentW / 2f, 14f, false);
        LabelAPI rl = rightLabel.addPara("Favors them ►", new Color(200, 180, 126, 255), 0f);
        rl.setAlignment(com.fs.starfarer.api.ui.Alignment.RMID);
        panel.addUIElement(rightLabel).inTL(x + contentW / 2f, y);

        return y + 14f + barH + pad;
    }

    // ── Deal columns ──────────────────────────────────────────

    private float buildDealColumns(CustomPanelAPI panel, float x, float y,
                                   float contentW, float colGap, FactionAPI targetFaction) {
        float dealH = 130f;
        float colW  = (contentW - colGap) / 2f;
        float pad   = 6f;

        // ── YOUR OFFER ────────────────────────────────────────────
        CustomPanelAPI leftPanel = panel.createCustomPanel(colW, dealH, null);
        panel.addComponent((UIComponentAPI) leftPanel).inTL(x, y);
        TooltipMakerAPI leftTip = leftPanel.createUIElement(colW - pad * 2f, dealH - pad * 2f, false);

        leftTip.addSectionHeading("YOUR OFFER", Misc.getBasePlayerColor(),
                new Color(0, 30, 60, 255), Alignment.MID, 0f);

        java.util.List<NegotiableItem> offers = deal.getOffers();
        if (offers.isEmpty()) {
            leftTip.addPara("Nothing offered yet.", Misc.getGrayColor(), 4f);
        } else {
            for (int i = 0; i < offers.size(); i++) {
                NegotiableItem item = offers.get(i);
                float val = valuator.evaluate(item, targetFactionId);
                String label = item.getDisplayLabel()
                        + "  (" + String.format("%.0f", val) + ")";
                leftTip.addPara(label, 3f, Misc.getHighlightColor(), item.getDisplayLabel());
                leftTip.addButton("[✕]", REMOVE_OFFER_PREFIX + i,
                        Misc.getNegativeHighlightColor(), new Color(30, 10, 10, 255),
                        28f, 16f, 2f);
            }
        }
        leftPanel.addUIElement(leftTip).inTL(pad, pad);

        // ── YOUR REQUESTS ─────────────────────────────────────────
        CustomPanelAPI rightPanel = panel.createCustomPanel(colW, dealH, null);
        panel.addComponent((UIComponentAPI) rightPanel).inTL(x + colW + colGap, y);
        TooltipMakerAPI rightTip = rightPanel.createUIElement(colW - pad * 2f, dealH - pad * 2f, false);

        rightTip.addSectionHeading("YOUR REQUESTS", targetFaction.getBaseUIColor(),
                targetFaction.getDarkUIColor(), Alignment.MID, 0f);

        java.util.List<NegotiableItem> requests = deal.getRequests();
        if (requests.isEmpty()) {
            rightTip.addPara("Nothing requested yet.", Misc.getGrayColor(), 4f);
        } else {
            for (int i = 0; i < requests.size(); i++) {
                NegotiableItem item = requests.get(i);
                float val = valuator.evaluate(item, targetFactionId);
                String label = item.getDisplayLabel()
                        + "  (" + String.format("%.0f", val) + ")";
                rightTip.addPara(label, 3f, Misc.getHighlightColor(), item.getDisplayLabel());
                rightTip.addButton("[✕]", REMOVE_REQUEST_PREFIX + i,
                        Misc.getNegativeHighlightColor(), targetFaction.getDarkUIColor(),
                        28f, 16f, 2f);
            }
        }
        rightPanel.addUIElement(rightTip).inTL(pad, pad);

        return y + dealH + 6f;
    }

    // ── Balance rendering ─────────────────────────────────────

    // ── Item catalog ──────────────────────────────────────────

    private void buildCatalog(CustomPanelAPI panel, float x, float y,
                              float contentW, float catalogH, FactionAPI targetFaction) {
        float pad   = 6f;
        float btnW  = 80f;
        float btnH  = 18f;
        Color factionColor = targetFaction.getBaseUIColor();
        Color factionDark  = targetFaction.getDarkUIColor();
        Color playerColor  = Misc.getBasePlayerColor();
        Color playerDark   = Misc.getDarkPlayerColor();

        TooltipMakerAPI info = panel.createUIElement(contentW - pad, catalogH, true);
        panel.addUIElement(info).inTL(x, y);

        info.addSectionHeading("AVAILABLE ITEMS", factionColor, factionDark, Alignment.MID, 0f);

        boolean ceasefireOnTable = deal.hasCeasefire();

        for (NegotiableItemType type : NegotiableItemType.values()) {
            if (viceroyMode && isViceroyCatalogTypeExcluded(type)) continue;
            if (type == NegotiableItemType.DECLARATIONS) continue;
            boolean available = type.isAvailable(currentTier, atWar, ceasefireOnTable);
            boolean expanded  = expandedCategories.contains(type);
            String arrow = expanded ? "▼ " : "► ";
            Color headerColor = available ? Misc.getTextColor() : Misc.getGrayColor();

            info.addButton(arrow + type.displayName,
                    CAT_TOGGLE_PREFIX + type.name(),
                    headerColor, new Color(20, 20, 20, 255),
                    Alignment.LMID, CutStyle.ALL,
                    contentW - pad * 3f, 20f, pad);

            if (!available) {
                info.addPara("  " + getUnavailableReason(type), Misc.getGrayColor(), 2f);
                continue;
            }

            if (!expanded) continue;

            addCatalogItemsThreeZone(info, type, btnW, btnH, pad,
                    playerColor, playerDark, factionColor, factionDark);
        }

        // ── Declarations ──────────────────────────────────────
        info.addSectionHeading("DECLARATIONS", new Color(180, 60, 60, 255),
                new Color(40, 0, 0, 255), Alignment.MID, pad);
        info.addPara("Unilateral — cannot be refused.", Misc.getGrayColor(), 2f);
        info.addButton("Declare War", BTN_DECLARE_WAR,
                new Color(200, 80, 80, 255), new Color(50, 0, 0, 255),
                Alignment.MID, CutStyle.ALL, contentW - pad * 4f, 24f, pad);
        info.addButton("Denounce", BTN_DENOUNCE,
                new Color(200, 80, 80, 255), new Color(50, 0, 0, 255),
                Alignment.MID, CutStyle.ALL, contentW - pad * 4f, 24f, 4f);
    }

    private void addCatalogItemsThreeZone(TooltipMakerAPI info, NegotiableItemType type,
                                          float btnW, float btnH, float pad,
                                          Color playerColor, Color playerDark,
                                          Color factionColor, Color factionDark) {
        switch (type) {
            case CREDITS:
                addThreeZoneRow(info, "10,000 credits", "credits_10000",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                addThreeZoneRow(info, "50,000 credits", "credits_50000",
                        btnW, btnH, 2f, playerColor, playerDark, factionColor, factionDark);
                break;
            case TRIBUTE:
                addThreeZoneRow(info, "5,000 cr/cycle (90 days)", "tribute_5000",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                break;
            case AGREEMENTS: {
                AgreementType[] types = viceroyMode
                        ? new AgreementType[]{AgreementType.TRADE_AGREEMENT}
                        : getAvailableAgreementTypes();
                for (AgreementType at : types) {
                    addThreeZoneRow(info, at.displayName, "agree_" + at.name(),
                            btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                }
                break;
            }
            case PEACE_TERMS:
                addThreeZoneRow(info, "Ceasefire", "ceasefire",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                addThreeZoneRow(info, "Peace Treaty", "peace_treaty",
                        btnW, btnH, 2f, playerColor, playerDark, factionColor, factionDark);
                break;
            case WAR_DECLARATION:
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    if (faction.getId().equals(playerFactionId)) continue;
                    if (faction.getId().equals(targetFactionId)) continue;
                    if (faction.isNeutralFaction()) continue;
                    if (!hasMarkets(faction.getId())) continue;
                    addThreeZoneRow(info, "War on " + faction.getDisplayName(),
                            "war_" + faction.getId(),
                            btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                }
                break;
            case TERRITORY:
                for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (market.getFactionId().equals(playerFactionId)) {
                        addThreeZoneRow(info, market.getName() + " (yours, size " + market.getSize() + ")",
                                "territory_" + market.getId(),
                                btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                    } else if (market.getFactionId().equals(targetFactionId)) {
                        addThreeZoneRow(info, market.getName() + " (theirs, size " + market.getSize() + ")",
                                "territory_" + market.getId(),
                                btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                    }
                }
                break;
            case COMMODITIES:
                String[] commodities = {"supplies", "fuel", "metals", "rare_metals",
                        "organics", "food", "hand_weapons"};
                for (String c : commodities) {
                    addThreeZoneRow(info, "500 x " + c, "commodity_" + c,
                            btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                }
                break;
            case KNOWLEDGE:
                addThreeZoneRow(info, "Blueprint package", "knowledge_blueprints",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                break;
            case INTEL:
                addThreeZoneRow(info, "Map data", "intel_map",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                addThreeZoneRow(info, "Fleet intel", "intel_fleet",
                        btnW, btnH, 2f, playerColor, playerDark, factionColor, factionDark);
                break;
            case CONTRACTS:
                addThreeZoneRow(info, "Mercenary contract (180 days)", "contract_mercenary",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                break;
            case CONCESSIONS:
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    if (faction.getId().equals(playerFactionId)) continue;
                    if (faction.getId().equals(targetFactionId)) continue;
                    if (faction.isNeutralFaction()) continue;
                    if (!hasMarkets(faction.getId())) continue;
                    addThreeZoneRow(info, "Embargo " + faction.getDisplayName(),
                            "concession_embargo_" + faction.getId(),
                            btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                }
                break;
            case PRISONERS:
                addThreeZoneRow(info, "Prisoner exchange", "prisoner_exchange",
                        btnW, btnH, pad, playerColor, playerDark, factionColor, factionDark);
                break;
        }
    }

    private void addThreeZoneRow(TooltipMakerAPI info, String label, String itemKey,
                                 float btnW, float btnH, float topPad,
                                 Color playerColor, Color playerDark,
                                 Color factionColor, Color factionDark) {
        info.addButton("[<- Offer]", ADD_OFFER_PREFIX + itemKey,
                playerColor, playerDark, btnW, btnH, topPad);
        info.addPara("  " + label, Misc.getTextColor(), 2f);
        info.addButton("[Request ->]", ADD_REQUEST_PREFIX + itemKey,
                factionColor, factionDark, btnW, btnH, 2f);
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
        NegotiationDealExecutor.executeDeal(deal, mgr, viaViceroyAgreement);
    }

    private boolean isViceroyCatalogTypeExcluded(NegotiableItemType type) {
        return type == NegotiableItemType.WAR_DECLARATION
                || type == NegotiableItemType.PEACE_TERMS
                || type == NegotiableItemType.TRIBUTE
                || type == NegotiableItemType.TERRITORY
                || type == NegotiableItemType.DECLARATIONS
                || type == NegotiableItemType.PRISONERS;
    }

    private float buildLeaderHeader(CustomPanelAPI panel, float x, float y,
                                    float contentW, FactionAPI targetFaction) {
        float headerH = 90f;
        float colW    = contentW / 3f;
        float pad     = 6f;

        CustomPanelAPI headerPanel = panel.createCustomPanel(contentW, headerH, null);
        panel.addComponent((UIComponentAPI) headerPanel).inTL(x, y);

        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);

        // ── Left: player portrait + identity ─────────────────────
        CustomPanelAPI leftPanel = headerPanel.createCustomPanel(colW, headerH, null);
        headerPanel.addComponent((UIComponentAPI) leftPanel).inTL(0, 0);
        TooltipMakerAPI leftTip = leftPanel.createUIElement(colW - pad, headerH, false);

        LeaderProfile proposer = proposerLeader();
        leftTip.addImage(proposer.portraitSpriteForCampaignImage(), 48f, 48f, 0f);
        leftTip.addPara(proposer.displayName(), Misc.getBasePlayerColor(), pad);
        leftTip.addPara(factionName(playerFactionId), Misc.getTextColor(), 2f);
        leftTip.addPara(relationBadge(playerFac, targetFaction), Misc.getTextColor(), 2f);
        leftPanel.addUIElement(leftTip).inTL(pad, pad);

        // ── Center: dialogue + Auto-Balance button ────────────────
        CustomPanelAPI centerPanel = headerPanel.createCustomPanel(colW, headerH, null);
        headerPanel.addComponent((UIComponentAPI) centerPanel).inTL(colW, 0);
        TooltipMakerAPI centerTip = centerPanel.createUIElement(colW - pad * 2f, headerH, false);

        ReputationTier baseT = ReputationTier.fromRelation(
                targetFaction.getRelationship(playerFactionId));
        String line = resolveDialogue(leader, Situation.GREETING, mood.effectiveTier(baseT));
        centerTip.addPara("\"" + line + "\"", Misc.getGrayColor(), 0f);
        centerTip.addSpacer(8f);
        centerTip.addButton("⚖ Auto-Balance", BTN_AUTO_NEGOTIATE,
                Misc.getButtonTextColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, colW - pad * 4f, 22f, 4f);
        centerPanel.addUIElement(centerTip).inTL(pad, pad);

        // ── Right: leader portrait + identity ─────────────────────
        CustomPanelAPI rightPanel = headerPanel.createCustomPanel(colW, headerH, null);
        headerPanel.addComponent((UIComponentAPI) rightPanel).inTL(colW * 2f, 0);
        TooltipMakerAPI rightTip = rightPanel.createUIElement(colW - pad, headerH, false);

        rightTip.addImage(leader.portraitSpriteForCampaignImage(), 48f, 48f, 0f);
        rightTip.addPara(leader.displayName(), targetFaction.getBaseUIColor(), pad);
        rightTip.addPara(factionName(targetFactionId), Misc.getTextColor(), 2f);
        rightTip.addPara(relationBadge(targetFaction, playerFac), Misc.getTextColor(), 2f);
        rightTip.addPara("Mood: " + mood.getDelta(), Misc.getGrayColor(), 2f);
        java.util.List<String> traits = leader.getTraits();
        if (!traits.isEmpty()) {
            rightTip.addPara("Traits: " + joinTraits(traits), Misc.getGrayColor(), 2f);
        }
        rightPanel.addUIElement(rightTip).inTL(pad, pad);

        return y + headerH + 6f;
    }

    LeaderProfile proposerLeader() {
        if (Global.getSector().getPlayerFaction().getId().equals(playerFactionId)) {
            LeaderProfile p = new LeaderProfile(playerFactionId, Personality.PRAGMATIC);
            String name = Global.getSector().getPlayerPerson() != null
                    ? Global.getSector().getPlayerPerson().getNameString()
                    : "Commander";
            p.setSyntheticName(name);
            return p;
        }
        return Nex4xManager.getOrCreateManager().getLeaderRegistry().getProfile(playerFactionId);
    }

    String factionName(String factionId) {
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getDisplayName() : factionId;
    }

    String relationBadge(FactionAPI viewer, FactionAPI about) {
        if (viewer == null || about == null) return "-";
        float rel = viewer.getRelationship(about.getId());
        ReputationTier t = ReputationTier.fromRelation(rel);
        int displayed = Math.round(rel);
        return tierLabel(t) + " (" + (displayed >= 0 ? "+" : "") + displayed + ")";
    }

    String tierLabel(ReputationTier t) {
        switch (t) {
            case HOSTILE:     return "Hostile";
            case SUSPICIOUS:  return "Suspicious";
            case NEUTRAL:     return "Neutral";
            case FAVORABLE:   return "Favorable";
            case COOPERATIVE: return "Cooperative";
            default:          return "Unknown";
        }
    }

    String joinTraits(java.util.List<String> traits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < traits.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(traits.get(i));
        }
        return sb.toString();
    }

    java.util.Map<String, String> dialogueContext() {
        java.util.Map<String, String> ctx = new java.util.HashMap<String, String>();
        ctx.put("player", Global.getSector().getPlayerFaction().getDisplayName());
        ctx.put("leader", leader.displayName());
        ctx.put("faction", factionName(targetFactionId));
        return ctx;
    }

    String resolveDialogue(LeaderProfile profile, Situation situation, ReputationTier tier) {
        DialogueSystem sys = DialogueSystem.get();
        if (sys == null) return "...";
        return sys.resolve(profile, situation, tier, dialogueContext());
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

    private static String negotiateTitle(String targetFactionId) {
        FactionAPI f = Global.getSector().getFaction(targetFactionId);
        if (f != null) {
            return "Negotiate - " + f.getDisplayName();
        }
        return "Negotiate";
    }
}
