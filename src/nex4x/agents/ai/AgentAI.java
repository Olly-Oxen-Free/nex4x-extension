package nex4x.agents.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.agents.AgentType;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import org.apache.log4j.Logger;

/**
 * Picks preferred agent types per faction based on tendency profile:
 * Militarists/Zealots -> GUERRILLA, Corporatists/Industrialists -> COVERT,
 * Federalists -> DIPLOMAT. Used when factions auto-train new agents.
 */
public class AgentAI {
    private static final Logger log = Global.getLogger(AgentAI.class);

    public static AgentType preferredType(String factionId) {
        TendencyProfile tp = TendencyProfileLoader.getProfile(factionId);
        if (tp == null) return AgentType.COVERT;

        float diplomat = tp.get(TendencyId.FEDERALISTS) + 0.5f * tp.get(TendencyId.ECOLOGISTS);
        float guerrilla = tp.get(TendencyId.MILITARISTS) + tp.get(TendencyId.ZEALOTS);
        float covert = tp.get(TendencyId.CORPORATISTS) + tp.get(TendencyId.INDUSTRIALISTS);

        if (diplomat >= guerrilla && diplomat >= covert) return AgentType.DIPLOMAT;
        if (guerrilla >= covert) return AgentType.GUERRILLA;
        return AgentType.COVERT;
    }

    public static void advanceFaction(FactionAPI faction) {
        if (faction == null || faction.isNeutralFaction() || faction.isPlayerFaction()) return;
        // Placeholder for per-faction AI routine — wired by Nex4xAgentManager daily sweep.
    }
}
