package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import nex4x.leaders.IntelTier;
import nex4x.leaders.IntelTierResolver;
import nex4x.leaders.LeaderProfile;
import nex4x.managers.Nex4xManager;
import nex4x.negotiation.DealProposal;
import nex4x.negotiation.NegotiableItemCatalog;
import nex4x.negotiation.SessionMood;
import org.apache.log4j.Logger;

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
        // Task 8.6
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
