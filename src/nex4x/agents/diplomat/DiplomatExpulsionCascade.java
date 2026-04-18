package nex4x.agents.diplomat;

import com.fs.starfarer.api.Global;
import nex4x.agents.AgentType;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;
import org.apache.log4j.Logger;

/**
 * When an actor faction's diplomat is exposed in victim-faction territory,
 * all of the actor's diplomats get a cascade suspicion flag against the victim.
 * Drops buildup by one level for each affected diplomat; forbids safe missions
 * for cascadeSuspicionDays.
 */
public class DiplomatExpulsionCascade {
    private static final Logger log = Global.getLogger(DiplomatExpulsionCascade.class);

    public static void apply(Nex4xAgentManager mgr, String actorFactionId,
                             String victimFactionId, float suspicionDays) {
        int affected = 0;
        for (Nex4xAgentData d : mgr.getAllData()) {
            if (d.getType() != AgentType.DIPLOMAT) continue;
            d.setCascadeSuspicion(victimFactionId, suspicionDays);
            d.getBuildupTracker().damageByOneLevel();
            affected++;
        }
        log.info("[Nex4x] Expulsion cascade: " + actorFactionId + " exposed in "
                + victimFactionId + " territory — " + affected + " diplomat(s) under suspicion");
    }
}
