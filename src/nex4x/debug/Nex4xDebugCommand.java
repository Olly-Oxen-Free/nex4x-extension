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
}
