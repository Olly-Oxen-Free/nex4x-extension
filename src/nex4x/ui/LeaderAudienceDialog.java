package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import exerelin.campaign.PlayerFactionStore;
import nex4x.leaders.*;
import nex4x.managers.Nex4xManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Full leader audience — opens the 2-column Civ negotiation panel on "Negotiate".
 * v5 skeleton: menu only; negotiation invocation populated in Phase 8.
 */
@SuppressWarnings("rawtypes")
public class LeaderAudienceDialog implements InteractionDialogPlugin {

    public static final String OPT_NEGOTIATE = "lead_negotiate";
    public static final String OPT_DECLARE_FRIENDSHIP = "lead_friendship";
    public static final String OPT_DENOUNCE = "lead_denounce";
    public static final String OPT_DECLARE_WAR = "lead_war";
    public static final String OPT_PROPOSE_PEACE = "lead_peace";
    public static final String OPT_DEPART = "lead_done";

    private final MarketAPI market;
    private InteractionDialogAPI dialog;
    private TextPanelAPI text;

    public LeaderAudienceDialog(MarketAPI market) {
        this.market = market;
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(market.getFactionId());
        float rel = Global.getSector().getPlayerFaction().getRelationship(market.getFactionId());
        ReputationTier tier = ReputationTier.fromRelation(rel);
        Map<String, String> ctx = new HashMap<String, String>();
        ctx.put("player", Global.getSector().getPlayerFaction().getDisplayName());
        ctx.put("leader", leader.displayName());
        ctx.put("faction", Global.getSector().getFaction(market.getFactionId()).getDisplayName());
        String greeting = DialogueSystem.get().resolve(leader, Situation.GREETING, tier, ctx);
        text.addPara(leader.displayName() + ": \"" + greeting + "\"");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Negotiate terms", OPT_NEGOTIATE);
        dialog.getOptionPanel().addOption("Declare public friendship", OPT_DECLARE_FRIENDSHIP);
        dialog.getOptionPanel().addOption("Denounce this faction", OPT_DENOUNCE);
        dialog.getOptionPanel().addOption("Declare war", OPT_DECLARE_WAR);
        dialog.getOptionPanel().addOption("Propose peace", OPT_PROPOSE_PEACE);
        dialog.getOptionPanel().addOption("Take your leave", OPT_DEPART);
    }

    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        String id = optionData.toString();
        if (OPT_DEPART.equals(id)) { dialog.dismiss(); return; }
        if (OPT_NEGOTIATE.equals(id)) {
            BasePopUpDialog.popUpDialog(new NegotiationPanel(market.getFactionId(), false), 620, 560);
            return;
        }
        String fid = market.getFactionId();
        String playerFid = PlayerFactionStore.getPlayerFactionId();
        Nex4xManager mgr = Nex4xManager.getOrCreateManager();
        if (OPT_DECLARE_FRIENDSHIP.equals(id)) {
            mgr.getDeclarationManager().declareFriendship(playerFid, fid);
            text.addPara("You declare public friendship with "
                    + Global.getSector().getFaction(fid).getDisplayName() + ".");
            return;
        }
        if (OPT_DENOUNCE.equals(id)) {
            mgr.getDeclarationManager().declareDenouncement(playerFid, fid);
            text.addPara("You publicly denounce "
                    + Global.getSector().getFaction(fid).getDisplayName() + ".");
            return;
        }
        if (OPT_DECLARE_WAR.equals(id)) {
            mgr.getExecutor(playerFid).declareWarPlayer(fid);
            text.addPara("War is declared.");
            return;
        }
        if (OPT_PROPOSE_PEACE.equals(id)) {
            mgr.getExecutor(playerFid).requestPeacePlayer(fid);
            text.addPara("A peace overture has been sent.");
            return;
        }
    }

    public void optionMousedOver(String optionText, Object optionData) {}
    public void advance(float amount) {}
    public void backFromEngagement(EngagementResultAPI r) {}
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
}
