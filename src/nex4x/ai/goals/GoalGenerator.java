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
        float currentDay = Global.getSector().getClock().getTimestamp();
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return goals;

        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) return goals;

        generateBeliefGoals(factionId, goals, currentDay);
        generateGrievanceGoals(factionId, mgr.getCasusBelliManager(), goals, currentDay);
        generateMaintenanceGoals(factionId, mgr, goals, currentDay);
        generateSecurityGoals(factionId, goals, currentDay);
        generateOpportunisticGoals(factionId, goals, currentDay);
        generateDiplomacyGoals(factionId, goals, currentDay);

        return goals;
    }

    private static void generateBeliefGoals(String factionId, List<StrategicGoal> goals,
                                             float currentDay) {
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        if (beliefs == null) return;

        for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
            BeliefDef def = BeliefRegistry.get(entry.beliefId);
            if (def == null) continue;

            if (def.category == BeliefDef.Category.TERRITORIAL && entry.strength >= 2) {
                goals.add(new StrategicGoal(GoalType.CLAIM_TERRITORY, null, null, currentDay));
            }
            if (def.category == BeliefDef.Category.IDEOLOGICAL && entry.strength >= 3) {
                goals.add(new StrategicGoal(GoalType.SPREAD_IDEOLOGY, null, null, currentDay));
            }
            if (def.category == BeliefDef.Category.ECONOMIC && entry.strength >= 2) {
                goals.add(new StrategicGoal(GoalType.ECONOMIC_DOMINANCE, null, null, currentDay));
            }
        }
    }

    private static void generateGrievanceGoals(String factionId, CasusBelliManager cbMgr,
                                                List<StrategicGoal> goals, float currentDay) {
        List<CasusBelli> cbs = cbMgr.getAllCBsFor(factionId);
        for (CasusBelli cb : cbs) {
            goals.add(new StrategicGoal(GoalType.PRESS_GRIEVANCE,
                    cb.getTargetFactionId(), null, currentDay));
        }
    }

    private static void generateMaintenanceGoals(String factionId, Nex4xManager mgr,
                                                  List<StrategicGoal> goals, float currentDay) {
        for (nex4x.agreements.Agreement a : mgr.getAgreementManager().getAgreementsFor(factionId)) {
            float remaining = a.getDaysRemaining();
            if (remaining > 0 && remaining <= 30) {
                goals.add(new StrategicGoal(GoalType.RENEW_AGREEMENT,
                        a.getOtherFaction(factionId), null, currentDay));
            }
        }
    }

    private static void generateSecurityGoals(String factionId, List<StrategicGoal> goals,
                                               float currentDay) {
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
                                    other.getId(), null, currentDay));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Nex not available
        }

        goals.add(new StrategicGoal(GoalType.DEFEND_TERRITORY, null, null, currentDay));
    }

    private static void generateOpportunisticGoals(String factionId, List<StrategicGoal> goals,
                                                    float currentDay) {
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
                        other.getId(), null, currentDay));
            }
        }
    }

    private static void generateDiplomacyGoals(String factionId, List<StrategicGoal> goals,
                                                float currentDay) {
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
                            other.getId(), null, currentDay));
                }
            }
        }
    }
}
