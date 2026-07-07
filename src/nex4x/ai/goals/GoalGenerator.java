package nex4x.ai.goals;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.ai.posture.DiplomaticPosture;
import nex4x.casusbelli.CasusBelli;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.data.BeliefDef;
import nex4x.data.BeliefRegistry;
import nex4x.data.FactionBeliefs;
import nex4x.data.FactionBeliefsLoader;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans game state and generates strategic goals for a faction.
 * Pipeline: beliefs -> grievances -> pressure -> agreements -> military -> opportunities.
 * See AI spec §2.5.
 */
public class GoalGenerator {

    private static final Logger log = Global.getLogger(GoalGenerator.class);

    /**
     * Generate all candidate goals for a faction.
     * Called daily by StrategicGoalManager.
     */
    public static List<StrategicGoal> generateGoals(String factionId) {
        List<StrategicGoal> goals = new ArrayList<StrategicGoal>();
        long currentTs = nex4x.util.Nex4xClock.now();
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return goals;

        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) return goals;

        generateBeliefGoals(factionId, goals, currentTs);
        generateGrievanceGoals(factionId, mgr.getCasusBelliManager(), goals, currentTs);
        generateMaintenanceGoals(factionId, mgr, goals, currentTs);
        generateSecurityGoals(factionId, goals, currentTs);
        generateOpportunisticGoals(factionId, goals, currentTs);
        generateDiplomacyGoals(factionId, goals, currentTs);

        // PRD-015 (15b): Post-pass — derive and set posture for all goals that have a target.
        // Must run after all generators so grievance goals exist for parentGoalId wiring.
        applyPosture(factionId, mgr.getCasusBelliManager(), goals);

        return goals;
    }

    private static void generateBeliefGoals(String factionId, List<StrategicGoal> goals,
                                             long currentTs) {
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        if (beliefs == null) return;

        for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
            BeliefDef def = BeliefRegistry.get(entry.beliefId);
            if (def == null) continue;

            if (def.category == BeliefDef.Category.TERRITORIAL && entry.strength >= 2) {
                goals.add(new StrategicGoal(GoalType.CLAIM_TERRITORY, null, null, currentTs));
            }
            if (def.category == BeliefDef.Category.IDEOLOGICAL && entry.strength >= 3) {
                goals.add(new StrategicGoal(GoalType.SPREAD_IDEOLOGY, null, null, currentTs));
            }
            if (def.category == BeliefDef.Category.ECONOMIC && entry.strength >= 2) {
                goals.add(new StrategicGoal(GoalType.ECONOMIC_DOMINANCE, null, null, currentTs));
            }
        }
    }

    private static void generateGrievanceGoals(String factionId, CasusBelliManager cbMgr,
                                                List<StrategicGoal> goals, long currentTs) {
        List<CasusBelli> cbs = cbMgr.getAllCBsFor(factionId);
        for (CasusBelli cb : cbs) {
            // Only create a grievance goal when this faction is the CB holder
            if (!cb.getHolderFactionId().equals(factionId)) continue;

            StrategicGoal goal = new StrategicGoal(GoalType.PRESS_GRIEVANCE,
                    cb.getTargetFactionId(), null, currentTs);

            // PRD-015 (15b Fix B): set obstacle severity from CB validity.
            // Conditional CBs (validityDays == -1) are persistent grievances → higher severity.
            // Timed CBs decay → moderate severity.
            float obstacleSeverity = cb.getType().validityDays < 0 ? 60f : 40f;
            goal.setObstacle(new Obstacle(ObstacleType.NO_CASUS_BELLI,
                    cb.getTargetFactionId(), obstacleSeverity, cb.getType().displayName));

            goals.add(goal);
        }
    }

    private static void generateMaintenanceGoals(String factionId, Nex4xManager mgr,
                                                  List<StrategicGoal> goals, long currentTs) {
        for (nex4x.agreements.Agreement a : mgr.getAgreementManager().getAgreementsFor(factionId)) {
            float remaining = a.getDaysRemaining();
            if (remaining > 0 && remaining <= 30) {
                goals.add(new StrategicGoal(GoalType.RENEW_AGREEMENT,
                        a.getOtherFaction(factionId), null, currentTs));
            }
        }
    }

    private static void generateSecurityGoals(String factionId, List<StrategicGoal> goals,
                                               long currentTs) {
        FactionAPI faction = Global.getSector().getFaction(factionId);

        try {
            exerelin.campaign.DiplomacyManager dipMgr =
                    exerelin.campaign.DiplomacyManager.getManager();
            if (dipMgr != null) {
                float weariness = dipMgr.getWarWeariness(factionId, true);
                if (weariness > 5000) {
                    for (FactionAPI other : Global.getSector().getAllFactions()) {
                        if (faction.isHostileTo(other) && !other.isNeutralFaction()) {
                            goals.add(new StrategicGoal(GoalType.END_WAR,
                                    other.getId(), null, currentTs));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Nex not available
        }

        goals.add(new StrategicGoal(GoalType.DEFEND_TERRITORY, null, null, currentTs));
    }

    private static void generateOpportunisticGoals(String factionId, List<StrategicGoal> goals,
                                                    long currentTs) {
        FactionAPI faction = Global.getSector().getFaction(factionId);

        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (other == faction || other.isNeutralFaction()) continue;
            if (other.getId().equals(factionId)) continue;

            int warCount = 0;
            for (FactionAPI third : Global.getSector().getAllFactions()) {
                if (other.isHostileTo(third) && !third.isNeutralFaction()) warCount++;
            }

            if (warCount >= 2 && !faction.isHostileTo(other)) {
                goals.add(new StrategicGoal(GoalType.EXPLOIT_WEAKNESS,
                        other.getId(), null, currentTs));
            }
        }
    }

    private static void generateDiplomacyGoals(String factionId, List<StrategicGoal> goals,
                                                long currentTs) {
        FactionAPI faction = Global.getSector().getFaction(factionId);

        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (other == faction || other.isNeutralFaction()) continue;
            if (faction.isHostileTo(other)) continue;

            float rel = faction.getRelationship(other.getId());
            if (rel > 0.2f) {
                boolean sharedEnemy = false;
                for (FactionAPI third : Global.getSector().getAllFactions()) {
                    if (faction.isHostileTo(third) && other.isHostileTo(third)) {
                        sharedEnemy = true;
                        break;
                    }
                }
                if (sharedEnemy) {
                    goals.add(new StrategicGoal(GoalType.BUILD_ALLIANCE,
                            other.getId(), null, currentTs));
                }
            }
        }
    }

    // ── PRD-015 (15b) ─────────────────────────────────────────────────────────

    /**
     * Post-pass: derive and set posture for all goals with a non-null targetFactionId,
     * then wire parentGoalId for END_WAR goals that correspond to a PRESS_GRIEVANCE goal.
     */
    private static void applyPosture(String factionId, CasusBelliManager cbMgr,
                                     List<StrategicGoal> goals) {
        // Build a lookup of grievance goals by targetFactionId for parentGoalId wiring
        java.util.Map<String, StrategicGoal> grievanceByTarget =
                new java.util.HashMap<String, StrategicGoal>();
        for (StrategicGoal g : goals) {
            if (g.type == GoalType.PRESS_GRIEVANCE && g.targetFactionId != null) {
                grievanceByTarget.put(g.targetFactionId, g);
            }
        }

        for (StrategicGoal goal : goals) {
            if (goal.targetFactionId == null) continue;

            // Fix A: derive and set posture
            goal.setPosture(derivePosture(goal.type, goal.targetFactionId, factionId, cbMgr));

            // Fix C: wire parentGoalId for END_WAR → PRESS_GRIEVANCE
            if (goal.type == GoalType.END_WAR) {
                StrategicGoal grievance = grievanceByTarget.get(goal.targetFactionId);
                if (grievance != null) {
                    goal.setParentGoalId(grievance.getKey());
                }
            }
        }
    }

    /**
     * Derive the appropriate DiplomaticPosture for a goal given goal type, target, and game state.
     * PRD-015 (15b Fix A).
     */
    private static DiplomaticPosture derivePosture(GoalType type, String targetFactionId,
                                                    String myFactionId,
                                                    CasusBelliManager cbMgr) {
        switch (type) {
            case PRESS_GRIEVANCE:
            case EXPLOIT_WEAKNESS:
            case CLAIM_TERRITORY: {
                // AGGRESSION-category goals: escalate to HOSTILE if rel < -0.3 or CB exists
                FactionAPI us = Global.getSector().getFaction(myFactionId);
                if (us != null) {
                    float rel = us.getRelationship(targetFactionId);
                    boolean hasCB = cbMgr.hasAnyCB(myFactionId, targetFactionId);
                    if (rel < -0.3f || hasCB) return DiplomaticPosture.HOSTILE;
                    if (rel < 0f) return DiplomaticPosture.PRESSURING;
                }
                return DiplomaticPosture.NEGOTIATING;
            }

            case END_WAR:
                return DiplomaticPosture.NEGOTIATING; // conciliatory intent

            case BUILD_ALLIANCE:
            case SECURE_AGREEMENT:
            case IMPROVE_RELATIONS:
            case RENEW_AGREEMENT:
                return DiplomaticPosture.NEGOTIATING;

            case DEFEND_TERRITORY:
            case COUNTER_PRESSURE:
            case CONTAIN_RIVAL:
            case SEEK_PROTECTION:
                return DiplomaticPosture.DEFENSIVE;

            default:
                return DiplomaticPosture.NEGOTIATING;
        }
    }
}
