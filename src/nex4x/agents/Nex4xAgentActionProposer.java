package nex4x.agents;

import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;

/**
 * Phase 4.2 placeholder: future hook to queue {@code CovertActionIntel} through Nex's
 * {@code CovertOpsManager} using Nex4x buildup / synergy signals. Nex still owns execution;
 * {@link Nex4xAgentActionReportListener} consumes outcomes today.
 */
public final class Nex4xAgentActionProposer {

    private static final Logger log = Global.getLogger(Nex4xAgentActionProposer.class);

    private Nex4xAgentActionProposer() {}

    /** Reserved for daily AI-driven agent task proposals. */
    public static void tick() {
        // Intentionally empty — avoids duplicating CovertOpsManager action selection until
        // buildup thresholds and Nex action constructors are fully mapped.
    }
}
