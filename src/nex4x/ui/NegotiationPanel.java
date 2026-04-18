package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.DialogueSystem;
import nex4x.leaders.IntelTier;
import nex4x.leaders.IntelTierResolver;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.Personality;
import nex4x.leaders.ReputationTier;
import nex4x.leaders.Situation;
import nex4x.managers.Nex4xManager;
import nex4x.negotiation.DealProposal;
import nex4x.negotiation.NegotiableItemCatalog;
import nex4x.negotiation.SessionMood;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2-column Civ-style negotiation UI for v5 leader audience flow.
 * Uses DealProposal + DealMutation exclusively (not the legacy DealPackage).
 * All mutations route through deal.applyMutation(DealMutation, catalog).
 *
 * Opened via: BasePopUpDialog.popUpDialog(new NegotiationPanel(factionId), 620, 560)
 */
public class NegotiationPanel extends BasePopUpDialog {

    private static final Logger log = Global.getLogger(NegotiationPanel.class);

    // ── Button ID prefixes ────────────────────────────────────
    public static final String REMOVE_OFFER_PREFIX   = "np_rmv_o_";
    public static final String REMOVE_REQUEST_PREFIX = "np_rmv_r_";
    public static final String ADD_OFFER_PREFIX      = "np_add_o_";
    public static final String ADD_REQUEST_PREFIX    = "np_add_r_";
    public static final String BTN_AUTO_BALANCE      = "np_auto_balance";
    public static final String BTN_SPEAK_LEADER      = "np_speak_leader"; // reserved Phase 9+

    // ── State ─────────────────────────────────────────────────
    private final String targetFactionId;
    private final String playerFactionId;
    private final DealProposal deal;
    private final LeaderProfile leader;
    private final IntelTier intelTier;
    private final SessionMood mood;
    private final NegotiableItemCatalog catalog;
    private final int acceptanceThreshold;
    private final boolean atWar;

    private boolean needsRefresh;

    public NegotiationPanel(String targetFactionId) {
        super("Negotiate \u2014 " + Global.getSector().getFaction(targetFactionId).getDisplayName());
        this.targetFactionId = targetFactionId;
        this.playerFactionId = Global.getSector().getPlayerFaction().getId();

        this.deal = new DealProposal(playerFactionId, targetFactionId);
        this.leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(targetFactionId);
        this.intelTier = IntelTierResolver.resolve(targetFactionId);
        this.mood = new SessionMood();
        this.catalog = new NegotiableItemCatalog();
        this.acceptanceThreshold = 500;
        this.atWar = Global.getSector().getFaction(playerFactionId).isHostileTo(targetFactionId);

        setConfirmText("Send Proposal");
    }

    // ── Content rendering (Tasks 8.6-8.8 fill these) ─────────

    @Override
    public void createContentForDialog(TooltipMakerAPI info, float width) {
        renderHeader(info, width);
        renderBalanceBar(info, width);
        renderTwoColumns(info, width);
    }

    void renderHeader(TooltipMakerAPI info, float width) {
        LeaderProfile proposerProfile = proposerLeader();
        LeaderProfile receiverProfile = leader;

        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);
        FactionAPI targetFac = Global.getSector().getFaction(targetFactionId);

        // ── Proposer portrait (left) ──────────────────────────
        TooltipMakerAPI leftCol = info.beginImageWithText(proposerProfile.portraitSprite(), 160f);
        leftCol.addPara(proposerProfile.displayName(), 4f);
        leftCol.addPara(factionName(deal.getProposer()), 2f);
        leftCol.addPara(relationBadge(playerFac, targetFac), 2f);
        info.addImageWithText(4f);

        // ── Receiver dialogue line (center) ───────────────────
        ReputationTier baseT = ReputationTier.fromRelation(
                targetFac.getRelationship(playerFactionId));
        String line = resolveDialogue(receiverProfile, Situation.GREETING,
                mood.effectiveTier(baseT));
        info.addPara(receiverProfile.displayName() + ": \"" + line + "\"", 8f);

        // ── Receiver portrait (right, with mood + traits) ─────
        TooltipMakerAPI rightCol = info.beginImageWithText(receiverProfile.portraitSprite(), 160f);
        rightCol.addPara(receiverProfile.displayName(), 4f);
        rightCol.addPara(factionName(deal.getReceiver()), 2f);
        rightCol.addPara(relationBadge(targetFac, playerFac), 2f);
        rightCol.addPara("Mood: " + mood.getDelta(), 4f);
        List<String> traits = receiverProfile.getTraits();
        if (!traits.isEmpty()) {
            rightCol.addPara("Traits: " + joinTraits(traits), 2f);
        }
        info.addImageWithText(4f);

        // ── Action row (auto-balance + confirm/cancel from Ashlib) ─
        float btnW = (width - 60f) / 2f;
        info.addSpacer(8f);
        info.addButton("Auto-Balance", BTN_AUTO_BALANCE,
                targetFac.getBaseUIColor(), targetFac.getDarkUIColor(),
                btnW, 28f, 4f);
    }

    // ── Helpers used by renderHeader ──────────────────────────

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
        float rel = viewer.getRelationship(about.getId());
        ReputationTier t = ReputationTier.fromRelation(rel);
        int displayed = Math.round(rel * 100f);
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

    String joinTraits(List<String> traits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < traits.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(traits.get(i));
        }
        return sb.toString();
    }

    Map<String, String> dialogueContext() {
        Map<String, String> ctx = new HashMap<String, String>();
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

    void renderBalanceBar(TooltipMakerAPI info, float width) {
        nex4x.negotiation.BalanceCalculator.Result r =
                nex4x.negotiation.BalanceCalculator.evaluate(deal, leader, acceptanceThreshold);
        nex4x.negotiation.BalanceSurface.Surface s =
                nex4x.negotiation.BalanceSurface.surface(r, intelTier, acceptanceThreshold);

        FactionAPI targetFac = Global.getSector().getFaction(targetFactionId);
        Color factionColor = targetFac.getBaseUIColor();
        Color darkColor    = targetFac.getDarkUIColor();

        info.addSectionHeading("BALANCE", factionColor, darkColor, Alignment.MID, 4f);

        // Qualitative verdict label — always shown
        Color verdictColor = verdictColor(r.verdict);
        info.addPara(s.qualitative, verdictColor, 2f);

        // Numeric readout — only when intel is GOOD or FULL
        if (!s.numeric.isEmpty()) {
            info.addPara(s.numeric, Misc.getHighlightColor(), 2f);
        }

        // ASCII balance meter: [.....<.....|.....*.....]  -500 ... 0 ... +500
        String meter = renderMeter(r.balance, acceptanceThreshold);
        info.addPara(meter, Misc.getGrayColor(), 4f);
    }

    private static Color verdictColor(nex4x.negotiation.BalanceCalculator.Verdict v) {
        switch (v) {
            case EXTRAORDINARY: return Misc.getPositiveHighlightColor();
            case GENEROUS:      return Misc.getPositiveHighlightColor();
            case FAIR:          return Misc.getHighlightColor();
            case COLD:          return Misc.getNegativeHighlightColor();
            case INSULTING:     return Misc.getNegativeHighlightColor();
            default:            return Misc.getTextColor();
        }
    }

    static String renderMeter(int balance, int threshold) {
        // 21-slot ASCII track: [.....<.....|.....*.....] -T ... 0 ... +T
        int slots = 21;
        int mid = slots / 2;   // 10 = centre
        int pos;
        if (threshold == 0) {
            pos = mid;
        } else {
            pos = mid + Math.round((float) balance / threshold * mid);
            if (pos < 0)       pos = 0;
            if (pos >= slots)  pos = slots - 1;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slots; i++) {
            if (i == mid && i == pos) sb.append('X');   // balanced AND at centre
            else if (i == mid)        sb.append('|');   // centre tick
            else if (i == pos)        sb.append('*');   // current balance marker
            else                      sb.append('.');
        }
        sb.append("]  ").append(-threshold).append("...0...+").append(threshold);
        return sb.toString();
    }

    void renderTwoColumns(TooltipMakerAPI info, float width) {
        float pad    = 6f;
        float colW   = (width - pad * 3f) / 2f;
        float colH   = 260f;

        // Create a CustomPanel to hold the two side-by-side scrollable columns.
        CustomPanelAPI cols = Global.getSettings().createCustom(width, colH, null);

        // ── Left column: proposer's offers + catalog ──────────
        TooltipMakerAPI leftCol = cols.createUIElement(colW, colH, true);
        FactionAPI playerFac = Global.getSector().getFaction(playerFactionId);
        leftCol.addSectionHeading("YOUR OFFER",
                playerFac.getBaseUIColor(), playerFac.getDarkUIColor(),
                Alignment.MID, 0f);
        renderOnTable(leftCol, true);
        leftCol.addSectionHeading("ADD TO OFFER",
                playerFac.getBaseUIColor(), playerFac.getDarkUIColor(),
                Alignment.MID, 8f);
        renderCatalog(leftCol, true);
        cols.addUIElement(leftCol).inTL(0f, 0f);

        // ── Right column: receiver's requests + catalog ───────
        TooltipMakerAPI rightCol = cols.createUIElement(colW, colH, true);
        FactionAPI targetFac = Global.getSector().getFaction(targetFactionId);
        rightCol.addSectionHeading("THEIR OFFER",
                targetFac.getBaseUIColor(), targetFac.getDarkUIColor(),
                Alignment.MID, 0f);
        renderOnTable(rightCol, false);
        rightCol.addSectionHeading("ADD TO REQUEST",
                targetFac.getBaseUIColor(), targetFac.getDarkUIColor(),
                Alignment.MID, 8f);
        renderCatalog(rightCol, false);
        cols.addUIElement(rightCol).rightOfTop(leftCol, pad);

        info.addCustom(cols, 8f);
    }

    private void renderOnTable(TooltipMakerAPI col, boolean proposerSide) {
        List<nex4x.negotiation.NegotiableItem> items = proposerSide
                ? deal.getProposerOffers()
                : deal.getReceiverOffers();
        String prefix = proposerSide ? REMOVE_OFFER_PREFIX : REMOVE_REQUEST_PREFIX;

        if (items.isEmpty()) {
            col.addPara("(nothing yet)", Misc.getGrayColor(), 2f);
        } else {
            for (nex4x.negotiation.NegotiableItem item : items) {
                String label = item.getDisplayLabel() + "  x" + item.getAmount();
                col.addPara("• " + label, 2f);
                col.addButton("Remove", prefix + item.getId(),
                        Misc.getNegativeHighlightColor(), Misc.getDarkPlayerColor(),
                        80f, 20f, 2f);
            }
        }
    }

    private void renderCatalog(TooltipMakerAPI col, boolean proposerSide) {
        String addPrefix = proposerSide ? ADD_OFFER_PREFIX : ADD_REQUEST_PREFIX;
        List<String> ids = catalog.getAvailableIds(atWar);
        if (ids.isEmpty()) {
            col.addPara("(no items available)", Misc.getGrayColor(), 2f);
            return;
        }
        for (String id : ids) {
            boolean locked = deal.getLockedChips().contains(id);
            String label = catalog.getDisplayName(id) + (locked ? " [locked]" : "");
            Color btnBase = locked ? Misc.getGrayColor() : Misc.getButtonTextColor();
            Color btnDark = Misc.getDarkPlayerColor();
            col.addButton(label, addPrefix + id, btnBase, btnDark, 200f, 22f, 2f);
        }
    }

    // ── Button dispatch ───────────────────────────────────────

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId == null) return;
        String id = buttonId.toString();

        if (BTN_AUTO_BALANCE.equals(id)) {
            nex4x.negotiation.AutoBalanceSolver.solve(
                    deal, leader, catalog, intelTier, acceptanceThreshold);
            mood.apply(SessionMood.Event.AUTO_BALANCE, leader.getPersonality());
            needsRefresh = true;
            return;
        }

        if (BTN_SPEAK_LEADER.equals(id)) {
            // Reserved for Starlogue x Nex4x joint spec — disabled in v5
            return;
        }

        if (id.startsWith(REMOVE_OFFER_PREFIX)) {
            String itemId = id.substring(REMOVE_OFFER_PREFIX.length());
            deal.applyMutation(nex4x.negotiation.DealMutation.removeOffer(itemId), catalog);
            log.info("[Nex4x] Removed offer: " + itemId);
            needsRefresh = true;
            return;
        }

        if (id.startsWith(REMOVE_REQUEST_PREFIX)) {
            String itemId = id.substring(REMOVE_REQUEST_PREFIX.length());
            deal.applyMutation(nex4x.negotiation.DealMutation.removeRequest(itemId), catalog);
            log.info("[Nex4x] Removed request: " + itemId);
            needsRefresh = true;
            return;
        }

        if (id.startsWith(ADD_OFFER_PREFIX)) {
            String itemId = id.substring(ADD_OFFER_PREFIX.length());
            int qty = catalog.suggestedQty(itemId, 1);
            boolean applied = deal.applyMutation(
                    nex4x.negotiation.DealMutation.addOffer(itemId, qty), catalog);
            if (applied) {
                log.info("[Nex4x] Added offer: " + itemId + " x" + qty);
                if (isAggressiveDemand(itemId)) {
                    mood.apply(SessionMood.Event.AGGRESSIVE_DEMAND, leader.getPersonality());
                }
            }
            needsRefresh = true;
            return;
        }

        if (id.startsWith(ADD_REQUEST_PREFIX)) {
            String itemId = id.substring(ADD_REQUEST_PREFIX.length());
            int qty = catalog.suggestedQty(itemId, 1);
            boolean applied = deal.applyMutation(
                    nex4x.negotiation.DealMutation.addRequest(itemId, qty), catalog);
            if (applied) {
                log.info("[Nex4x] Added request: " + itemId + " x" + qty);
                if (isConcession(itemId)) {
                    mood.apply(SessionMood.Event.CONCESSION, leader.getPersonality());
                }
            }
            needsRefresh = true;
            return;
        }
    }

    private boolean isAggressiveDemand(String itemId) {
        return itemId.contains("tribute") || itemId.contains("reparations");
    }

    private boolean isConcession(String itemId) {
        return itemId.contains("gift") || itemId.contains("waive");
    }

    // ── Confirm: Send Proposal ────────────────────────────────

    @Override
    public void applyConfirmScript() {
        nex4x.negotiation.BalanceCalculator.Result r =
                nex4x.negotiation.BalanceCalculator.evaluate(deal, leader, acceptanceThreshold);
        float moodDelta = acceptanceThreshold * mood.thresholdDeltaPct();
        boolean accepted = (r.balance + Math.round(moodDelta)) >= acceptanceThreshold;

        Situation responseType = accepted
                ? Situation.NEGOTIATION_ACCEPT
                : Situation.NEGOTIATION_REJECT;
        ReputationTier effectiveTier = mood.effectiveTier(baseTier());
        String line = resolveDialogue(leader, responseType, effectiveTier);

        Global.getSector().getCampaignUI().addMessage(
                leader.displayName() + ": \"" + line + "\"",
                accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());

        if (accepted) {
            executeAcceptedDeal();
            log.info("[Nex4x] Deal accepted by " + targetFactionId);
        } else {
            log.info("[Nex4x] Deal rejected by " + targetFactionId);
        }
    }

    private ReputationTier baseTier() {
        float rel = Global.getSector().getFaction(targetFactionId)
                .getRelationship(playerFactionId);
        return ReputationTier.fromRelation(rel);
    }

    private void executeAcceptedDeal() {
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getOrCreateManager();
        for (nex4x.negotiation.NegotiableItem item : deal.getProposerOffers()) {
            applyItemEffect(item, deal.getProposer(), deal.getReceiver(), mgr);
        }
        for (nex4x.negotiation.NegotiableItem item : deal.getReceiverOffers()) {
            applyItemEffect(item, deal.getReceiver(), deal.getProposer(), mgr);
        }
        log.info("[Nex4x] Deal executed: " + deal.getProposer() + " -> " + deal.getReceiver());
    }

    private void applyItemEffect(nex4x.negotiation.NegotiableItem item, String fromFaction, String toFaction,
                                 nex4x.managers.Nex4xManager mgr) {
        switch (item.getType()) {
            case AGREEMENTS:
                mgr.getAgreementManager().createAgreement(fromFaction, toFaction, item.getAgreementType());
                break;
            case WAR_DECLARATION:
                mgr.getExecutor(fromFaction).declareWarPlayer(toFaction);
                break;
            case CREDITS:
            case PEACE_TERMS:
            default:
                log.info("[Nex4x] Deal item placeholder: " + item.getType() + " (" + fromFaction + " -> " + toFaction + ")");
                break;
        }
    }

    // ── Refresh ───────────────────────────────────────────────

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (needsRefresh) {
            needsRefresh = false;
            removeUI();
            createUI(panelToInfluence);
        }
    }

    // ── Accessors ─────────────────────────────────────────────

    public DealProposal getDeal()           { return deal; }
    public LeaderProfile getLeader()        { return leader; }
    public IntelTier getIntelTier()         { return intelTier; }
    public SessionMood getMood()            { return mood; }
    public int getAcceptanceThreshold()     { return acceptanceThreshold; }
}
