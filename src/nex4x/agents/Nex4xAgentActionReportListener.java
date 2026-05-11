package nex4x.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.AgentActionListener;
import nex4x.managers.Nex4xManager;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

/**
 * Hooks Nex {@link CovertActionIntel} resolution to Nex4x memory, pressure, and per-agent buildup.
 */
public class Nex4xAgentActionReportListener implements AgentActionListener {

    private static final Logger log = Global.getLogger(Nex4xAgentActionReportListener.class);

    @Override
    public void reportAgentAction(CovertActionIntel action) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || action == null) return;
        try {
            FactionAPI af = action.getAgentFaction();
            FactionAPI tf = action.getTargetFaction();
            if (af != null && tf != null) {
                mgr.getMemoryManager().createMemory(
                        "covert_operation",
                        af.getId(), tf.getId(), action.getActionName(false));
                PressureManager.getOrCreate().applyEvent(
                        af.getId(), tf.getId(), PressureSource.AGENT_ACTION, 15f);
            }
            AgentIntel ai = action.getAgent();
            if (ai != null) {
                PersonAPI person = ai.getAgent();
                if (person != null) {
                    // Read the canonical type from Nex; do not force COVERT.
                    AgentType type = AgentTypeMap.fromAgent(ai);
                    Nex4xAgentData data = Nex4xAgentManager.getOrCreate()
                            .getOrCreate(person.getId(), type);
                    // Keep type in sync with Nex if it changed.
                    if (data.getType() != type) data.setType(type);
                    if (data.getOwnerFactionId() == null && af != null) {
                        data.setOwnerFactionId(af.getId());
                    }
                    data.getBuildupTracker().addBoost(5f);
                }
            }
        } catch (Exception e) {
            log.warn("[Nex4x] reportAgentAction: " + e.getMessage());
        }
    }
}
