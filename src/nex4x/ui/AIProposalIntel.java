package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.diplomacy.TimedDiplomacyIntel;
import exerelin.campaign.ui.PopupDialogScript;
import exerelin.campaign.ui.PopupDialogScript.PopupDialog;
import nex4x.agreements.AgreementType;
import nex4x.managers.Nex4xManager;
import nex4x.negotiation.DealPackage;
import nex4x.negotiation.NegotiableItem;
import nex4x.negotiation.ItemValuator;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;
import java.util.Set;

/**
 * Intel notification for an AI-initiated deal proposal (spec §5.2.3).
 * Extends Nex's TimedDiplomacyIntel for the timed accept/reject lifecycle.
 * Implements PopupDialog for the initial campaign popup notification.
 *
 * Three actions:
 *   Accept  — execute the deal immediately
 *   Counter — open NegotiationPopUpDialog pre-filled with the AI's terms
 *   Reject  — decline, no consequences beyond lost opportunity
 */
public class AIProposalIntel extends TimedDiplomacyIntel implements PopupDialog {

    private static final Logger log = Global.getLogger(AIProposalIntel.class);

    public static final String BUTTON_COUNTER = "nex4x_counter";
    private static final Object DIALOG_OPT_ACCEPT = new Object();
    private static final Object DIALOG_OPT_COUNTER = new Object();
    private static final Object DIALOG_OPT_REJECT = new Object();
    private static final Object DIALOG_OPT_CLOSE = new Object();

    private final DealPackage aiDeal;

    /**
     * @param aiFactionId Faction making the proposal
     * @param aiDeal Deal from AI's perspective (proposer=AI, target=player)
     */
    public AIProposalIntel(String aiFactionId, DealPackage aiDeal) {
        super(MathUtils.getRandomNumberInRange(5, 7));
        this.factionId = aiFactionId;
        this.aiDeal = aiDeal;
    }

    public void init() {
        this.setImportant(true);
        Global.getSector().getIntelManager().addIntel(this);
        Global.getSector().addScript(this);
        Global.getSector().addScript(new PopupDialogScript(this));
        log.info("[Nex4x] AI proposal from " + factionId + " added to intel");
    }

    public DealPackage getAiDeal() {
        return aiDeal;
    }

    // ── TimedDiplomacyIntel lifecycle ─────────────────────────

    @Override
    protected void acceptImpl() {
        executeDeal();
        FactionAPI faction = getFactionForUIColors();
        Global.getSector().getCampaignUI().addMessage(
                faction.getDisplayName() + "'s proposal accepted.",
                Misc.getPositiveHighlightColor());
        Global.getSoundPlayer().playUISound("ui_rep_raise", 1, 1);
        log.info("[Nex4x] AI proposal from " + factionId + " accepted");
    }

    @Override
    protected void rejectImpl() {
        log.info("[Nex4x] AI proposal from " + factionId + " rejected");
    }

    @Override
    public void onExpire() {
        // Treat expiry as rejection
        setState(-1);
        log.info("[Nex4x] AI proposal from " + factionId + " expired");
    }

    // ── Deal execution ────────────────────────────────────────

    private void executeDeal() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        String playerFactionId = Global.getSector().getPlayerFaction().getId();

        // AI's offers = what AI gives to player
        for (NegotiableItem item : aiDeal.getOffers()) {
            executeItem(item, factionId, playerFactionId, mgr);
        }
        // AI's requests = what player gives to AI
        for (NegotiableItem item : aiDeal.getRequests()) {
            executeItem(item, playerFactionId, factionId, mgr);
        }
    }

    private void executeItem(NegotiableItem item, String giver, String receiver,
                              Nex4xManager mgr) {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        switch (item.getType()) {
            case AGREEMENTS:
                if (item.getAgreementType() != null) {
                    mgr.getAgreementManager().createAgreement(
                            playerFactionId, factionId, item.getAgreementType());
                }
                break;
            case PEACE_TERMS:
                if (item.isCeasefire()) {
                    try {
                        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);
                        FactionAPI targetFac = Global.getSector().getFaction(factionId);
                        exerelin.campaign.DiplomacyManager.createDiplomacyEventV2(
                                playerFac, targetFac, "ceasefire", null);
                    } catch (Exception e) {
                        Global.getSector().getFaction(playerFactionId)
                                .setRelationship(factionId, 0);
                    }
                }
                break;
            default:
                break;
        }
    }

    // ── Intel description (shown in intel tab) ────────────────

    /** Derive the appropriate Situation for the leader dialogue line from the deal's items. */
    private nex4x.leaders.Situation deriveProposalSituation() {
        // Check offers (what AI gives) for peace items
        for (nex4x.negotiation.NegotiableItem item : aiDeal.getOffers()) {
            if (item.getType() == nex4x.negotiation.NegotiableItemType.PEACE_TERMS) {
                return nex4x.leaders.Situation.PEACE_PROPOSED_BY_AI;
            }
        }
        // Check requests (what AI wants) for peace items
        for (nex4x.negotiation.NegotiableItem item : aiDeal.getRequests()) {
            if (item.getType() == nex4x.negotiation.NegotiableItemType.PEACE_TERMS) {
                return nex4x.leaders.Situation.PEACE_PROPOSED_BY_AI;
            }
        }
        // Check offers and requests for agreement types
        for (nex4x.negotiation.NegotiableItem item : aiDeal.getOffers()) {
            if (item.getType() == nex4x.negotiation.NegotiableItemType.AGREEMENTS
                    && item.getAgreementType() != null) {
                return agreementTypeToSituation(item.getAgreementType());
            }
        }
        for (nex4x.negotiation.NegotiableItem item : aiDeal.getRequests()) {
            if (item.getType() == nex4x.negotiation.NegotiableItemType.AGREEMENTS
                    && item.getAgreementType() != null) {
                return agreementTypeToSituation(item.getAgreementType());
            }
        }
        // Default: generic alliance proposal
        return nex4x.leaders.Situation.ALLIANCE_PROPOSED;
    }

    private nex4x.leaders.Situation agreementTypeToSituation(
            nex4x.agreements.AgreementType agType) {
        if (agType == nex4x.agreements.AgreementType.NAP) {
            return nex4x.leaders.Situation.NAP_PROPOSED;
        }
        if (agType == nex4x.agreements.AgreementType.TRADE_AGREEMENT) {
            return nex4x.leaders.Situation.TRADE_PACT_PROPOSED;
        }
        // DEFENSIVE_PACT, MILITARY_PARTNERSHIP, ECONOMIC_PARTNERSHIP, COALITION
        return nex4x.leaders.Situation.ALLIANCE_PROPOSED;
    }

    @Override
    public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        FactionAPI playerFaction = Global.getSector().getFaction(
                PlayerFactionStore.getPlayerFactionId());

        // v5 — leader portrait header
        nex4x.leaders.LeaderProfile leader = nex4x.managers.Nex4xManager
                .getOrCreateManager().getLeaderRegistry().getProfile(factionId);
        String sprite = leader.portraitSprite();
        if (sprite != null) {
            info.beginImageWithText(sprite, 72f);
            info.addPara(leader.displayName(), 4f);
            info.addPara(faction.getDisplayName(), 2f);
            info.addImageWithText(4f);
        } else {
            info.addPara(leader.displayName() + " — " + faction.getDisplayName(), opad);
        }
        // Derive situation from deal content
        nex4x.leaders.Situation sit = deriveProposalSituation();
        float rel = faction.getRelationship(playerFaction.getId());
        nex4x.leaders.ReputationTier tier = nex4x.leaders.ReputationTier.fromRelation(rel);
        java.util.Map<String,String> ctx = new java.util.HashMap<String,String>();
        ctx.put("player", playerFaction.getDisplayName());
        ctx.put("leader", leader.displayName());
        ctx.put("faction", faction.getDisplayName());
        String proposalLine = nex4x.leaders.DialogueSystem.get().resolve(leader, sit, tier, ctx);
        info.addPara("\"" + proposalLine + "\"", opad);

        info.addImages(width, 96, opad, opad, faction.getLogo(), playerFaction.getLogo());

        String factionName = faction.getDisplayNameWithArticle();
        info.addPara(Misc.ucFirst(factionName) + " has proposed a diplomatic deal.",
                opad, faction.getBaseUIColor(),
                faction.getDisplayNameWithArticleWithoutArticle());

        // Show deal summary
        info.addSectionHeading("THEIR OFFER", faction.getBaseUIColor(),
                faction.getDarkUIColor(), Alignment.MID, opad);

        List<NegotiableItem> offers = aiDeal.getOffers();
        if (offers.isEmpty()) {
            info.addPara("Nothing offered.", Misc.getGrayColor(), 5f);
        } else {
            for (NegotiableItem item : offers) {
                info.addPara("  " + item.getDisplayLabel(), 3f);
            }
        }

        info.addSectionHeading("THEIR DEMAND", faction.getBaseUIColor(),
                faction.getDarkUIColor(), Alignment.MID, opad);

        List<NegotiableItem> requests = aiDeal.getRequests();
        if (requests.isEmpty()) {
            info.addPara("Nothing demanded.", Misc.getGrayColor(), 5f);
        } else {
            for (NegotiableItem item : requests) {
                info.addPara("  " + item.getDisplayLabel(), 3f);
            }
        }
    }

    @Override
    public void createPendingDescription(TooltipMakerAPI info, float width, float opad) {
        Color h = Misc.getHighlightColor();
        Color base = getFactionForUIColors().getBaseUIColor();
        Color dark = getFactionForUIColors().getDarkUIColor();

        String days = Math.round(daysRemaining) + "";
        info.addPara("This proposal will expire in " + days + " days.", opad, h, days);

        // Accept
        info.addButton("Accept", BUTTON_ACCEPT, base, dark,
                (int) width, 20f, opad * 3f);
        // Counter
        info.addButton("Counter-Propose", BUTTON_COUNTER, base, dark,
                (int) width, 20f, opad);
        // Reject
        info.addButton("Reject", BUTTON_REJECT, base, dark,
                (int) width, 20f, opad);
    }

    @Override
    public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {
        String outcome;
        Color color;
        if (getState() == 1) {
            outcome = "Accepted";
            color = Misc.getPositiveHighlightColor();
        } else {
            outcome = "Rejected";
            color = Misc.getNegativeHighlightColor();
        }
        info.addPara("You " + outcome.toLowerCase() + " the proposal.", opad, color, outcome.toLowerCase());
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_COUNTER.equals(buttonId)) {
            // Open negotiation table pre-filled with AI's deal
            NegotiationPopUpDialog popup = new NegotiationPopUpDialog(factionId, aiDeal);
            BasePopUpDialog.popUpDialog(popup, 620, 560);
            // Treat as rejection of this specific proposal (player is now counter-proposing)
            reject();
            ui.updateUIForItem(this);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (BUTTON_COUNTER.equals(buttonId)) return false;
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    // ── PopupDialog interface (initial campaign popup) ────────

    @Override
    public void init(InteractionDialogAPI dialog) {
        FactionAPI faction = getFactionForUIColors();
        TextPanelAPI text = dialog.getTextPanel();

        text.addPara("Diplomatic Proposal", Misc.getHighlightColor());
        text.addPara(Misc.ucFirst(faction.getDisplayNameWithArticle())
                        + " has sent a diplomatic proposal.",
                faction.getBaseUIColor(),
                faction.getDisplayNameWithArticleWithoutArticle());

        // Brief summary
        List<NegotiableItem> offers = aiDeal.getOffers();
        List<NegotiableItem> requests = aiDeal.getRequests();
        if (!offers.isEmpty()) {
            text.addPara("They offer: " + offers.get(0).getDisplayLabel()
                    + (offers.size() > 1 ? " and " + (offers.size() - 1) + " more" : ""));
        }
        if (!requests.isEmpty()) {
            text.addPara("They request: " + requests.get(0).getDisplayLabel()
                    + (requests.size() > 1 ? " and " + (requests.size() - 1) + " more" : ""));
        }

        String days = Math.round(daysRemaining) + "";
        text.addPara("You have " + days + " days to respond.");
    }

    @Override
    public void populateOptions(OptionPanelAPI opts) {
        opts.addOption("Accept", DIALOG_OPT_ACCEPT);
        opts.addOption("Counter-Propose", DIALOG_OPT_COUNTER);
        opts.addOption("Reject", DIALOG_OPT_REJECT);
        opts.addOption("Close", DIALOG_OPT_CLOSE);
        opts.setShortcut(DIALOG_OPT_CLOSE, Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    @Override
    public void optionSelected(InteractionDialogAPI dialog, Object optionData) {
        if (optionData == DIALOG_OPT_ACCEPT) {
            accept();
            endAfterDelay();
        } else if (optionData == DIALOG_OPT_COUNTER) {
            NegotiationPopUpDialog popup = new NegotiationPopUpDialog(factionId, aiDeal);
            BasePopUpDialog.popUpDialog(popup, 620, 560);
            reject();
            endAfterDelay();
        } else if (optionData == DIALOG_OPT_REJECT) {
            setState(-1);
            endAfterDelay();
        }
        dialog.dismiss();
    }

    @Override
    public boolean shouldCancel() {
        return getState() != 0;
    }

    // ── Intel metadata ────────────────────────────────────────

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    protected String getName() {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        String base = "Proposal from " + faction.getDisplayName();
        if (getState() == 1) return base + " - Accepted";
        if (getState() == -1) return base + " - Rejected";
        return base;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_AGREEMENTS);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add(factionId);
        return tags;
    }

    @Override
    public String getIcon() {
        return getFactionForUIColors().getCrest();
    }

    @Override
    public String getSortString() {
        return "Diplomacy";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(factionId);
    }

    @Override
    public String getStrategicActionName() {
        return getName();
    }
}
