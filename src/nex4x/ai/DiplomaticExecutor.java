package nex4x.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.agreements.AgreementManager;
import nex4x.agreements.AgreementType;
import nex4x.ai.archetype.Archetype;
import nex4x.ai.archetype.GrandStrategyManager;
import nex4x.ai.goals.FeasibilityChecker;
import nex4x.ai.goals.GoalType;
import nex4x.ai.goals.StrategicGoal;
import nex4x.ai.posture.DiplomaticPosture;
import nex4x.casusbelli.CasusBelli;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import nex4x.managers.Nex4xManager;
import nex4x.policies.PolicyManager;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;

/**
 * Daily diplomatic action execution. Fully replaces DiplomacyBrain's war/peace logic.
 * Reads goal list, scores candidate actions, executes within action budget.
 * War/peace decisions exempt from budget.
 * See AI spec §4.
 */
public class DiplomaticExecutor implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(DiplomaticExecutor.class);

    private final String factionId;
    private int actionBudgetRemaining;

    // Config
    private static int MAX_ACTIONS_PER_DAY = 2;
    private static boolean WAR_PEACE_EXEMPT = true;

    // War decision config
    private static float WAR_CB_BONUS = 40f;
    private static float WAR_MILITARY_ADV_WEIGHT = 30f;
    private static float WAR_AGREEMENT_PENALTY = 50f;
    private static float WAR_ALLIANCE_DETERRENT = 25f;
    private static float WAR_DECLARE_THRESHOLD = 100f;
    private static float WAR_CONSIDER_THRESHOLD = 70f;
    private static float WAR_RANDOM_RANGE = 20f;

    // Peace decision config
    private static float PEACE_BASE = 30f;
    private static float PEACE_WEARINESS_WEIGHT = 0.005f;
    private static float PEACE_SCOPE_ACHIEVED_BONUS = 40f;
    private static float PEACE_SEEK_THRESHOLD = 80f;
    private static float PEACE_ACCEPT_THRESHOLD = 50f;

    public static void loadConfig(JSONObject config) {
        JSONObject budget = config.optJSONObject("actionBudget");
        if (budget != null) {
            MAX_ACTIONS_PER_DAY = budget.optInt("maxPerDay", 2);
            WAR_PEACE_EXEMPT = budget.optBoolean("warPeaceExempt", true);
        }
        JSONObject war = config.optJSONObject("warDecision");
        if (war != null) {
            WAR_CB_BONUS = (float) war.optDouble("cbBonus", 40);
            WAR_MILITARY_ADV_WEIGHT = (float) war.optDouble("militaryAdvantageWeight", 30);
            WAR_AGREEMENT_PENALTY = (float) war.optDouble("agreementPenaltyWeight", 50);
            WAR_ALLIANCE_DETERRENT = (float) war.optDouble("allianceDeterrentWeight", 25);
            WAR_DECLARE_THRESHOLD = (float) war.optDouble("declareThreshold", 100);
            WAR_CONSIDER_THRESHOLD = (float) war.optDouble("considerThreshold", 70);
            WAR_RANDOM_RANGE = (float) war.optDouble("randomRange", 20);
        }
        JSONObject peace = config.optJSONObject("peaceDecision");
        if (peace != null) {
            PEACE_BASE = (float) peace.optDouble("base", 30);
            PEACE_WEARINESS_WEIGHT = (float) peace.optDouble("warWearinessWeight", 0.005);
            PEACE_SCOPE_ACHIEVED_BONUS = (float) peace.optDouble("scopeAchievedBonus", 40);
            PEACE_SEEK_THRESHOLD = (float) peace.optDouble("seekPeaceThreshold", 80);
            PEACE_ACCEPT_THRESHOLD = (float) peace.optDouble("acceptThreshold", 50);
        }
    }

    public DiplomaticExecutor(String factionId) {
        this.factionId = factionId;
        this.actionBudgetRemaining = MAX_ACTIONS_PER_DAY;
    }

    /**
     * Daily execution. Called after StrategicGoalManager updates.
     */
    public void advanceDay(StrategicGoalManager goalMgr, GrandStrategyManager grandStrategy) {
        actionBudgetRemaining = MAX_ACTIONS_PER_DAY;

        List<StrategicGoal> goals = goalMgr.getActiveGoals();
        Archetype archetype = grandStrategy.getArchetype(factionId);

        // War/peace decisions first (budget-exempt)
        evaluateWarDecisions(goals, archetype);
        evaluatePeaceDecisions(goals, archetype);

        // Then budgeted diplomatic actions
        for (StrategicGoal goal : goals) {
            if (actionBudgetRemaining <= 0) break;
            executeDiplomaticAction(goal, archetype);
        }
    }

    // War Decision (AI spec §4.2)

    private void evaluateWarDecisions(List<StrategicGoal> goals, Archetype archetype) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        for (StrategicGoal goal : goals) {
            if (goal.targetFactionId == null) continue;
            if (goal.getPosture() != DiplomaticPosture.HOSTILE) continue;

            FactionAPI us = Global.getSector().getFaction(factionId);
            FactionAPI them = Global.getSector().getFaction(goal.targetFactionId);
            if (us == null || them == null) continue;
            if (us.isHostileTo(them)) continue;

            float warDesire = calculateWarDesire(goal, archetype, mgr);

            if (warDesire > WAR_DECLARE_THRESHOLD) {
                declareWar(goal.targetFactionId, goal, mgr);
            } else if (warDesire > WAR_CONSIDER_THRESHOLD) {
                log.info("[Nex4x] " + factionId + " CONSIDERING war on "
                        + goal.targetFactionId + " (desire=" + Math.round(warDesire) + ")");
            }
        }
    }

    private float calculateWarDesire(StrategicGoal goal, Archetype archetype, Nex4xManager mgr) {
        String target = goal.targetFactionId;

        float desire = goal.getEffectivePriority();

        List<CasusBelli> cbs = mgr.getCasusBelliManager().getCBsAgainst(factionId, target);
        if (!cbs.isEmpty()) desire += WAR_CB_BONUS;

        float ourStr = FeasibilityChecker.check(goal, factionId);
        desire += ourStr * WAR_MILITARY_ADV_WEIGHT;

        desire += calculateDoctrineScore(true, target);

        AgreementType tier = mgr.getAgreementManager().getAllianceTier(factionId, target);
        if (tier.tier > 0) desire -= tier.tier * WAR_AGREEMENT_PENALTY;

        List<nex4x.agreements.Agreement> targetPacts =
                mgr.getAgreementManager().getAgreementsOfType(target, AgreementType.DEFENSIVE_PACT);
        desire -= targetPacts.size() * WAR_ALLIANCE_DETERRENT;

        desire += (Math.random() * WAR_RANDOM_RANGE * 2) - WAR_RANDOM_RANGE;

        try {
            desire += PolicyManager.getOrCreate().getPolicyModifier(factionId, "war_desire");
        } catch (Exception ignore) { }

        return desire;
    }

    private float calculateDoctrineScore(boolean isWarVote, String target) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return 0;

        float score = 0;
        score += profile.get(TendencyId.MILITARISTS) * (isWarVote ? 8 : -4);
        score += profile.get(TendencyId.FEDERALISTS) * (isWarVote ? -7 : 5);
        return score;
    }

    private void declareWar(String targetFactionId, StrategicGoal goal, Nex4xManager mgr) {
        log.info("[Nex4x] " + factionId + " DECLARES WAR on " + targetFactionId
                + " (goal: " + goal.type.displayName + ")");

        try {
            FactionAPI us = Global.getSector().getFaction(factionId);
            FactionAPI them = Global.getSector().getFaction(targetFactionId);
            nex4x.casusbelli.CasusBelli cb = mgr.getCasusBelliManager()
                    .getActiveCasusBelliFor(factionId, targetFactionId);
            if (cb != null) {
                nex4x.integration.NexDiplomacyBridge.fireJustifiedWar(us, them, cb.getType().name());
            } else {
                exerelin.campaign.DiplomacyManager.createDiplomacyEvent(us, them, "declare_war", null);
            }
        } catch (Exception e) {
            log.error("[Nex4x] Failed to declare war: " + e.getMessage());
        }
        try {
            Global.getSector().getIntelManager()
                    .addIntel(new nex4x.ui.WarDeclarationIntel(factionId, targetFactionId, /*byAi=*/ true));
        } catch (Exception e) {
            log.error("[Nex4x] Failed to emit WarDeclarationIntel: " + e.getMessage());
        }
    }

    /** Player-driven war declaration path (no StrategicGoal context). */
    public void declareWarPlayer(String targetFactionId) {
        log.info("[Nex4x] " + factionId + " DECLARES WAR on " + targetFactionId + " (player-initiated)");

        try {
            exerelin.campaign.DiplomacyManager.createDiplomacyEvent(
                    Global.getSector().getFaction(factionId),
                    Global.getSector().getFaction(targetFactionId),
                    "declare_war", null);
        } catch (Exception e) {
            log.error("[Nex4x] Failed to declare war: " + e.getMessage());
        }
        try {
            Global.getSector().getIntelManager()
                    .addIntel(new nex4x.ui.WarDeclarationIntel(factionId, targetFactionId, /*byAi=*/ false));
        } catch (Exception e) {
            log.error("[Nex4x] Failed to emit WarDeclarationIntel: " + e.getMessage());
        }
    }

    /** Player seeks peace / ceasefire with target (no StrategicGoal context). */
    public void requestPeacePlayer(String targetFactionId) {
        FactionAPI us = Global.getSector().getFaction(factionId);
        FactionAPI them = Global.getSector().getFaction(targetFactionId);
        if (us == null || them == null) return;
        log.info("[Nex4x] " + factionId + " proposes peace with " + targetFactionId + " (player-initiated)");
        try {
            exerelin.campaign.DiplomacyManager.createDiplomacyEventV2(us, them, "ceasefire", null);
        } catch (Exception e) {
            log.warn("[Nex4x] ceasefire event failed, softening relations: " + e.getMessage());
        }
        try {
            if (us.isHostileTo(them)) {
                us.setRelationship(targetFactionId, 0f);
                them.setRelationship(factionId, 0f);
            }
        } catch (Exception e) {
            log.error("[Nex4x] requestPeacePlayer relation step failed: " + e.getMessage());
        }
    }

    // Peace Decision (AI spec §4.3)

    private void evaluatePeaceDecisions(List<StrategicGoal> goals, Archetype archetype) {
        for (StrategicGoal goal : goals) {
            if (goal.type != GoalType.END_WAR) continue;
            if (goal.targetFactionId == null) continue;

            float peaceWill = calculatePeaceWillingness(goal, archetype);

            if (peaceWill > PEACE_SEEK_THRESHOLD) {
                log.info("[Nex4x] " + factionId + " seeking peace with "
                        + goal.targetFactionId + " (willingness=" + Math.round(peaceWill) + ")");
            }
        }
    }

    private float calculatePeaceWillingness(StrategicGoal goal, Archetype archetype) {
        float willingness = PEACE_BASE;

        try {
            exerelin.campaign.DiplomacyManager dipMgr =
                    exerelin.campaign.DiplomacyManager.getManager();
            if (dipMgr != null) {
                willingness += dipMgr.getWarWeariness(factionId, true) * PEACE_WEARINESS_WEIGHT;
            }
        } catch (Exception e) { /* Nex not available */ }

        willingness += calculateDoctrineScore(false, goal.targetFactionId);
        willingness += (float)(Math.random() * 30) - 15;

        return willingness;
    }

    // Budgeted diplomatic actions

    private void executeDiplomaticAction(StrategicGoal goal, Archetype archetype) {
        switch (goal.type) {
            case BUILD_ALLIANCE:
            case SECURE_AGREEMENT:
            case IMPROVE_RELATIONS:
                if (goal.targetFactionId != null) {
                    proposeAgreementIfViable(goal);
                }
                break;
            default:
                break;
        }
    }

    private void proposeAgreementIfViable(StrategicGoal goal) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        AgreementManager aMgr = mgr.getAgreementManager();
        AgreementType currentTier = aMgr.getAllianceTier(factionId, goal.targetFactionId);

        AgreementType nextTier = currentTier.getNextAllianceTier();
        if (nextTier != null && aMgr.canPropose(factionId, goal.targetFactionId, nextTier)) {
            log.info("[Nex4x] " + factionId + " proposing " + nextTier.displayName
                    + " to " + goal.targetFactionId);
            actionBudgetRemaining--;
        }
    }

    public String getFactionId() { return factionId; }
}
