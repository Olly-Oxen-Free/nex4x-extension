package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
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
        if (OPT_NEGOTIATE.equals(id)) { text.addPara("[Negotiation panel — Phase 8]"); return; }
        if (OPT_DECLARE_FRIENDSHIP.equals(id)) { text.addPara("[Friendship declaration — Phase 4]"); return; }
        if (OPT_DENOUNCE.equals(id)) { text.addPara("[Denouncement — Phase 4]"); return; }
        if (OPT_DECLARE_WAR.equals(id)) { text.addPara("[War declaration — Phase 10]"); return; }
        if (OPT_PROPOSE_PEACE.equals(id)) { text.addPara("[Peace proposal — Phase 10]"); return; }
    }

    public void optionMousedOver(String optionText, Object optionData) {}
    public void advance(float amount) {}
    public void backFromEngagement(EngagementResultAPI r) {}
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
}
