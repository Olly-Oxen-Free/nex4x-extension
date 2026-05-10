package nex4x.integration;

import com.fs.starfarer.api.Global;
import exerelin.campaign.ai.StrategicDefManager;
import org.apache.log4j.Logger;

/**
 * Registers nex4x-specific StrategicAI concerns/actions with Nex's StrategicDefManager
 * so they participate in the existing AI module system. We add concerns/actions; we do
 * NOT replace Nex's concern flow.
 */
public class Nex4xStrategicAIConcerns {
    private static final Logger log = Global.getLogger(Nex4xStrategicAIConcerns.class);

    private static final String CONCERN_BELIEF = "nex4x_belief_alignment";
    private static final String CONCERN_GOAL_WRAP = "nex4x_goal_wrap";
    private static final String ACTION_PROPOSE_TIERED = "nex4x_propose_tiered_agreement";

    public static void register() {
        try {
            registerConcern(CONCERN_BELIEF,
                    "Belief Alignment",
                    "Track ideological clashes with neighbours.",
                    "nex4x.strategic.concern.BeliefAlignmentConcern",
                    StrategicDefManager.ModuleType.DIPLOMATIC);
            registerConcern(CONCERN_GOAL_WRAP,
                    "Strategic Goal",
                    "Active goal from the nex4x strategic goal manager.",
                    "nex4x.strategic.concern.GoalWrapConcern",
                    StrategicDefManager.ModuleType.EXECUTIVE);

            registerAction(ACTION_PROPOSE_TIERED,
                    "Propose Tiered Agreement",
                    "nex4x.strategic.action.ProposeTieredAgreementAction",
                    1.0f, 30f);

            log.info("[Nex4x] Registered " + 2 + " concerns + 1 action with Nex StrategicDefManager");
        } catch (NoClassDefFoundError ncdfe) {
            log.warn("[Nex4x] Nex StrategicDefManager not present — concerns not registered: "
                    + ncdfe.getMessage());
        } catch (Throwable t) {
            log.warn("[Nex4x] Failed to register strategic concerns/actions: " + t.getMessage(), t);
        }
    }

    private static void registerConcern(String id, String name, String desc, String classPath,
                                         StrategicDefManager.ModuleType module) {
        if (StrategicDefManager.CONCERN_DEFS_BY_ID.containsKey(id)) return;
        StrategicDefManager.StrategicConcernDef def = new StrategicDefManager.StrategicConcernDef(id);
        def.name = name;
        def.desc = desc;
        def.classPath = classPath;
        def.module = module;
        def.enabled = true;
        StrategicDefManager.CONCERN_DEFS_BY_ID.put(id, def);
    }

    private static void registerAction(String id, String name, String classPath,
                                        float chance, float cooldown) {
        if (StrategicDefManager.ACTION_DEFS_BY_ID.containsKey(id)) return;
        StrategicDefManager.StrategicActionDef def = new StrategicDefManager.StrategicActionDef(id);
        def.name = name;
        def.classPath = classPath;
        def.chance = chance;
        def.cooldown = cooldown;
        def.enabled = true;
        StrategicDefManager.ACTION_DEFS_BY_ID.put(id, def);
    }
}
