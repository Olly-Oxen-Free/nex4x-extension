package nex4x.integration;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Registration hook for nex4x-specific StrategicAI concerns/actions.
 * Stub: future tasks will register concerns via Nex's StrategicDefManager.
 */
public class Nex4xStrategicAIConcerns {
    private static final Logger log = Global.getLogger(Nex4xStrategicAIConcerns.class);

    public static void register() {
        log.info("[Nex4x] Nex4xStrategicAIConcerns.register() — stub (no concerns registered yet)");
    }
}
