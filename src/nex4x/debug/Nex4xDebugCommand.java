package nex4x.debug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.LeaderRegistry;
import nex4x.managers.Nex4xManager;

/** runcode entry points for manual smoke testing from the Console Commands mod. */
public class Nex4xDebugCommand {

    /** runcode nex4x.debug.Nex4xDebugCommand.printLeaders() */
    public static String printLeaders() {
        StringBuilder sb = new StringBuilder();
        LeaderRegistry reg = Nex4xManager.getOrCreateManager().getLeaderRegistry();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction() || f.isPlayerFaction()) continue;
            LeaderProfile p = reg.getProfile(f.getId());
            sb.append(f.getId())
              .append(" → ").append(p.displayName())
              .append(" [").append(p.getPersonality()).append("]")
              .append("  title=").append(p.titleString())
              .append("\n");
        }
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.speakGreeting("hegemony"); */
    public static String speakGreeting(String factionId) {
        nex4x.leaders.LeaderProfile p = nex4x.managers.Nex4xManager
                .getOrCreateManager().getLeaderRegistry().getProfile(factionId);
        com.fs.starfarer.api.campaign.FactionAPI player =
                com.fs.starfarer.api.Global.getSector().getPlayerFaction();
        float rel = player.getRelationship(factionId);
        nex4x.leaders.ReputationTier tier = nex4x.leaders.ReputationTier.fromRelation(rel);
        java.util.Map<String,String> ctx = new java.util.HashMap<String,String>();
        ctx.put("player", player.getDisplayName());
        ctx.put("leader", p.displayName());
        ctx.put("faction", com.fs.starfarer.api.Global.getSector().getFaction(factionId).getDisplayName());
        String line = nex4x.leaders.DialogueSystem.get().resolve(
                p, nex4x.leaders.Situation.GREETING, tier, ctx);
        String msg = "[" + factionId + "] " + p.displayName() + " [" + p.getPersonality() + ", " + tier + "]: " + line;
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(msg);
        return msg;
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openViceroy("hegemony"); */
    public static String openViceroy(String factionId) {
        com.fs.starfarer.api.campaign.econ.MarketAPI m = firstMarketOfFaction(factionId);
        if (m == null) return "No market found for " + factionId;
        com.fs.starfarer.api.Global.getSector().getCampaignUI().showInteractionDialog(
                new nex4x.ui.ViceroyDialog(m), m.getPrimaryEntity());
        return "Opened viceroy dialog at " + m.getName();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openLeader("hegemony"); */
    public static String openLeader(String factionId) {
        nex4x.leaders.LeaderAccessGate.Gate g = nex4x.leaders.LeaderAccessGate.resolve(factionId);
        if (g == nex4x.leaders.LeaderAccessGate.Gate.NONE) {
            return "Leader audience is gated. No path open. (rapport/commission/own-colony/other-means)";
        }
        nex4x.ui.NegotiationPanel.openScaled(factionId, false);
        return "Opened negotiation panel for " + factionId + " via gate " + g;
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.declareFriendship("hegemony"); */
    public static String declareFriendship(String target) {
        nex4x.declarations.Declaration d = nex4x.managers.Nex4xManager.getOrCreateManager()
                .getDeclarationManager()
                .declareFriendship(com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), target);
        return "Friendship declared with " + target + " (expires day " + d.getExpiryDay() + ")";
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.denounce("pirates"); */
    public static String denounce(String target) {
        nex4x.declarations.Declaration d = nex4x.managers.Nex4xManager.getOrCreateManager()
                .getDeclarationManager()
                .declareDenouncement(com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), target);
        return "Denounced " + target + " (expires day " + d.getExpiryDay() + ", CB unlocks at 3 mo)";
    }

    static com.fs.starfarer.api.campaign.econ.MarketAPI firstMarketOfFaction(String factionId) {
        return nex4x.util.FactionMarketUtil.firstMarketOfFaction(factionId);
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.smokeTest(); */
    public static String smokeTest() {
        StringBuilder sb = new StringBuilder("[Nex4x v5 smoke test]\n");
        // 1. Every tier-1 faction has a profile
        String[] t1 = {"hegemony", "tritachyon", "sindrian_diktat", "luddic_church",
                       "luddic_path", "persean", "independent", "pirates", "remnants"};
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getOrCreateManager();
        for (String fid : t1) {
            nex4x.leaders.LeaderProfile p = mgr.getLeaderRegistry().getProfile(fid);
            sb.append("  ").append(fid).append(" -> ").append(p.displayName())
              .append(" [").append(p.getPersonality()).append("]\n");
        }
        // 2. Dialogue system resolves without error
        nex4x.leaders.LeaderProfile heg = mgr.getLeaderRegistry().getProfile("hegemony");
        java.util.Map<String,String> ctx = new java.util.HashMap<String,String>();
        ctx.put("player", "Commander");
        ctx.put("leader", heg.displayName());
        ctx.put("faction", "Hegemony");
        String line = nex4x.leaders.DialogueSystem.get().resolve(
                heg, nex4x.leaders.Situation.GREETING, nex4x.leaders.ReputationTier.NEUTRAL, ctx);
        sb.append("  dialogue: ").append(line).append("\n");
        // 3. Declaration config loaded
        sb.append("  friendship.durationDays = ")
          .append(nex4x.declarations.DeclarationConfig.get(
                  nex4x.declarations.DeclarationType.FRIENDSHIP).durationDays).append("\n");
        // 4. Valuator + Balance calc compile-path sanity
        nex4x.negotiation.DealProposal deal = new nex4x.negotiation.DealProposal(
                com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), "hegemony");
        nex4x.negotiation.BalanceCalculator.Result r =
                nex4x.negotiation.BalanceCalculator.evaluate(deal, heg, 500);
        sb.append("  empty-deal balance = ").append(r.balance)
          .append(" verdict=").append(r.verdict).append("\n");
        // 5. Intel tier
        sb.append("  hegemony intel tier = ")
          .append(nex4x.leaders.IntelTierResolver.resolve("hegemony")).append("\n");

        sb.append("[OK]");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }
}
