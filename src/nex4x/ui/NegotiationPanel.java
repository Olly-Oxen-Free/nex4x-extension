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
        // Task 8.7
    }

    void renderTwoColumns(TooltipMakerAPI info, float width) {
        // Task 8.8
    }

    // ── Button dispatch (Task 8.9) ────────────────────────────

    @Override
    public void buttonPressed(Object buttonId) {
        // Task 8.9
    }

    // ── Confirm hook ──────────────────────────────────────────

    @Override
    public void applyConfirmScript() {
        // Task 8.9
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
