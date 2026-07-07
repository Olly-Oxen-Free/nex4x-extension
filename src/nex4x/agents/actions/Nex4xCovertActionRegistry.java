package nex4x.agents.actions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.agents.AgentIntel;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Registers nex4x covert action defs with Nex's {@link CovertOpsManager} so the new
 * actions appear in the agent orders dialog and run through Nex's standard
 * scheduling / detection / resolution pipeline.
 *
 * Idempotent — safe to call multiple times. Logs a warning if Nex isn't loaded.
 */
public final class Nex4xCovertActionRegistry {

    private static final Logger log = Global.getLogger(Nex4xCovertActionRegistry.class);

    private Nex4xCovertActionRegistry() {}

    public static void register() {
        try {
            registerOne(ActionDefIds.DEEP_COVER, "Deep Cover",
                    "nex4x.agents.actions.covert.DeepCoverAction",
                    EnumSet.of(AgentIntel.Specialization.SABOTEUR, AgentIntel.Specialization.HYBRID),
                    /*success*/ 0.7f, /*detect*/ 0.10f, /*detectFail*/ 0.40f,
                    /*cost*/ 5000f, /*time*/ 30f, /*xp*/ 50,
                    /*alertInc*/ 1f, /*effect*/ new Pair<Float,Float>(20f, 30f),
                    /*useAlert*/ true, /*useIndSec*/ true, /*listInIntel*/ true);

            registerOne(ActionDefIds.DIPLOMAT_OFFICIAL, "Diplomatic Support",
                    "nex4x.agents.actions.diplomat.DiplomatOfficialSupportAction",
                    EnumSet.of(AgentIntel.Specialization.NEGOTIATOR, AgentIntel.Specialization.HYBRID),
                    0.85f, 0.0f, 0.05f,   // official action: low/no detection
                    8000f, 45f, 60,
                    0f, new Pair<Float,Float>(3f, 11f),
                    false, false, true);

            registerOne(ActionDefIds.DIPLOMAT_LEAK, "Unofficial Leak",
                    "nex4x.agents.actions.diplomat.DiplomatUnofficialLeakAction",
                    EnumSet.of(AgentIntel.Specialization.NEGOTIATOR, AgentIntel.Specialization.HYBRID),
                    0.55f, 0.30f, 0.65f,
                    6000f, 25f, 80,
                    2f, new Pair<Float,Float>(15f, 25f),
                    true, true, true);

            registerOne(ActionDefIds.GUERRILLA_BUILD_NETWORK, "Build Local Network",
                    "nex4x.agents.actions.guerrilla.BuildNetworkAction",
                    EnumSet.of(AgentIntel.Specialization.HYBRID, AgentIntel.Specialization.SABOTEUR),
                    0.65f, 0.20f, 0.45f,
                    7000f, 40f, 70,
                    1f, new Pair<Float,Float>(20f, 30f),
                    true, true, true);

            registerOne(ActionDefIds.GUERRILLA_FALSE_FLAG, "False-Flag Attack",
                    "nex4x.agents.actions.guerrilla.FalseFlagAttackAction",
                    EnumSet.of(AgentIntel.Specialization.HYBRID, AgentIntel.Specialization.SABOTEUR),
                    0.50f, 0.35f, 0.70f,
                    12000f, 35f, 120,
                    3f, new Pair<Float,Float>(40f, 60f),
                    true, true, true);

            registerOne(ActionDefIds.GUERRILLA_HIRE_MERCS, "Hire Mercenaries",
                    "nex4x.agents.actions.guerrilla.HireMercenariesAction",
                    EnumSet.of(AgentIntel.Specialization.HYBRID),
                    0.70f, 0.25f, 0.55f,
                    15000f, 30f, 90,
                    2f, new Pair<Float,Float>(20f, 30f),
                    true, true, true);

            registerOne(ActionDefIds.GUERRILLA_INCITE_RAID, "Incite Raid",
                    "nex4x.agents.actions.guerrilla.InciteRaidAction",
                    EnumSet.of(AgentIntel.Specialization.HYBRID, AgentIntel.Specialization.SABOTEUR),
                    0.60f, 0.25f, 0.55f,
                    9000f, 35f, 85,
                    2f, new Pair<Float,Float>(8f, 12f),
                    true, true, true);

            // Re-sort so nex4x defs land at their sortOrder position; Nex only sorts
            // during its own loadSettings, which has already run by the time we append.
            // CovertActionDef implements Comparable<CovertActionDef>.
            Collections.sort(CovertOpsManager.actionDefs);

            log.info("[Nex4x] Registered " + 7 + " covert action defs with Nex CovertOpsManager");
        } catch (NoClassDefFoundError ncdfe) {
            log.warn("[Nex4x] Nex CovertOpsManager not present — actions not registered: "
                    + ncdfe.getMessage());
        } catch (Throwable t) {
            log.warn("[Nex4x] Covert action registration failed: " + t.getMessage(), t);
        }
    }

    private static void registerOne(String id, String name, String className,
                                     Set<AgentIntel.Specialization> specs,
                                     float successChance, float detectionChance, float detectionChanceFail,
                                     float cost, float time, int xp,
                                     float alertLevelIncrease, Pair<Float, Float> effect,
                                     boolean useAlertLevel, boolean useIndustrySecurity,
                                     boolean listInIntel) {
        if (CovertOpsManager.actionDefsById.containsKey(id)) return; // idempotent

        CovertOpsManager.CovertActionDef def = new CovertOpsManager.CovertActionDef();
        def.id = id;
        def.name = name;
        def.nameForSub = name;
        def.className = className;
        def.successChance = successChance;
        def.detectionChance = detectionChance;
        def.detectionChanceFail = detectionChanceFail;
        def.injuryChanceMult = 1.0f;
        def.useAlertLevel = useAlertLevel;
        def.useIndustrySecurity = useIndustrySecurity;
        def.baseCost = cost;
        def.costScaling = true;
        def.time = time;
        def.xp = xp;
        def.alertLevelIncrease = alertLevelIncrease;
        def.effect = effect;
        def.repLossOnDetect = new Pair<Float, Float>(0.05f, 0.10f);
        def.specializations = new HashSet<AgentIntel.Specialization>(specs);
        def.listInIntel = listInIntel;
        def.sortOrder = 100f;

        CovertOpsManager.actionDefs.add(def);
        CovertOpsManager.actionDefsById.put(id, def);
    }
}
