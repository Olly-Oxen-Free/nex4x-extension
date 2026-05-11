package nex4x.pressure;

import com.fs.starfarer.api.Global;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import org.apache.log4j.Logger;

/**
 * Executes a force action: checks pressure threshold + influence cost, deducts both,
 * logs the result. Actual gameplay effect (agreement forced / peace forced / etc.)
 * is applied by higher-level systems consuming {@link Result}.
 */
public class ForceAction {
    private static final Logger log = Global.getLogger(ForceAction.class);

    public static class Result {
        public final boolean success;
        public final String reason;
        public final ForceActionType type;
        public Result(boolean success, String reason, ForceActionType type) {
            this.success = success; this.reason = reason; this.type = type;
        }
    }

    public static Result execute(String fromFaction, String toFaction, ForceActionType type) {
        PressureManager pm = PressureManager.getOrCreate();
        InfluenceManager im = InfluenceManager.getOrCreate();

        float pressure = pm.getPressure(fromFaction, toFaction);
        if (pressure < type.pressureThreshold) {
            return new Result(false, "Insufficient pressure (" + pressure + "/" + type.pressureThreshold + ")", type);
        }
        if (!im.canAfford(fromFaction, type.influenceCost)) {
            return new Result(false, "Insufficient influence (" + im.getBalance(fromFaction) + "/" + type.influenceCost + ")", type);
        }

        im.spend(fromFaction, type.influenceCost, InfluenceSource.FORCE_ACTION);
        pm.spend(fromFaction, toFaction, type.pressureThreshold);
        log.info("[Nex4x] Force action " + type + " executed: " + fromFaction + " -> " + toFaction);
        return new Result(true, "Force action succeeded", type);
    }
}
