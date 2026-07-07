package nex4x.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.campaign.intel.agents.AgentIntel;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Daily sweep that ensures every live Nex {@link AgentIntel} has a nex4x companion record
 * with a populated owner faction id — BEFORE passive-influence drip and expulsion cascades
 * run, both of which skip null-owner agents.
 *
 * <p>Previously the owner id was only set in {@code Nex4xAgentActionReportListener} after an
 * agent completed a Nex-reported covert action, so diplomats that had never acted produced
 * zero passive influence and were immune to cascades. This sweep closes that gap.
 *
 * <p>Live agents are enumerated via the IntelManager (Nex's {@code AgentIntel} has no public
 * static accessor for its instances).
 */
public final class AgentOwnershipSweep {

    private static final Logger log = Global.getLogger(AgentOwnershipSweep.class);

    private AgentOwnershipSweep() {}

    public static void sweepAndWire(Nex4xAgentManager mgr) {
        if (mgr == null) return;
        int ensured = 0, ownersSet = 0, pruned = 0;
        try {
            List<IntelInfoPlugin> agents = Global.getSector().getIntelManager()
                    .getIntel(AgentIntel.class);
            if (agents == null) return;
            for (IntelInfoPlugin plugin : agents) {
                if (!(plugin instanceof AgentIntel)) continue;
                AgentIntel ai = (AgentIntel) plugin;
                PersonAPI person = ai.getAgent();
                if (person == null) continue;
                String personId = person.getId();

                if (ai.isDeadOrDismissed()) {
                    if (mgr.get(personId) != null) {
                        mgr.remove(personId);
                        pruned++;
                        log.debug("[Nex4x] Pruned dead agent: " + personId);
                    }
                    continue;
                }

                AgentType type = AgentTypeMap.fromAgent(ai);
                Nex4xAgentData data = mgr.getOrCreate(personId, type);
                ensured++;

                if (data.getOwnerFactionId() == null) {
                    FactionAPI f = ai.updateAgentDisplayedFaction();
                    if (f != null) {
                        data.setOwnerFactionId(f.getId());
                        ownersSet++;
                    }
                }
                if (data.getType() != type) {
                    data.setType(type);
                }
            }
            log.info("[Nex4x] AgentOwnershipSweep: " + ensured + " records ensured, "
                    + ownersSet + " owner ids set, " + pruned + " pruned");
        } catch (Throwable t) {
            log.warn("[Nex4x] AgentOwnershipSweep failed: " + t.getMessage(), t);
        }
    }
}
