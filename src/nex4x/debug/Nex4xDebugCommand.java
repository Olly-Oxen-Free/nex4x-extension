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
}
