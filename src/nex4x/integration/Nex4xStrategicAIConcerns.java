package nex4x.integration;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * PRD-015 (15a): No-op stub. Registration of nex4x concern/action defs is
 * delegated entirely to strategicAIConfig.json (handled by Nex's
 * StrategicDefManager static initializer). The previous Java registration
 * used wrong ids (nex4x_goal_wrap, nex4x_propose_tiered_agreement) that
 * differed from the JSON ids, producing tagless orphan defs that could never
 * be selected by tag-intersection. register() is retained as a no-op so
 * existing call-sites (if any remain from old saves or external mods)
 * compile without error.
 */
public class Nex4xStrategicAIConcerns {
    private static final Logger log = Global.getLogger(Nex4xStrategicAIConcerns.class);

    /** No-op. JSON registration is the sole source of nex4x concern/action defs. */
    public static void register() {
        log.info("[Nex4x] StrategicAI concerns: registration delegated to strategicAIConfig.json");
    }
}
