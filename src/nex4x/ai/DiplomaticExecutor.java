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
import nex4x.coalitions.CoalitionGovernance;
import nex4x.coalitions.CoalitionVote;
import nex4x.contracts.ContractAuctionManager;
import nex4x.contracts.ContractType;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import nex4x.demands.Demand;
import nex4x.demands.DemandManager;
import nex4x.managers.Nex4xManager;
import nex4x.policies.PolicyManager;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 2026-07-07 audit: absolute day of this faction's last AI war declaration.
     * Persisted (non-transient primitive) so the 60-day declaration cooldown survives
     * save/load. -1 = never declared.
     */
    private float lastWarDeclarationDay = -1f;

    /**
     * PRD-015 (15d): Per-day double-fire guard for peace treaty proposals.
     * Cleared at the start of each advanceDay call. Prevents both Path A (Nex
     * StrategicAI, currently disabled) and Path B (DiplomaticExecutor) from
     * firing a peace treaty for the same target pair on the same day.
     */
    private transient Set<String> peaceProposedThisDay = new HashSet<String>();

    /**
     * PRD-022 (22b): Demand cooldown tracking. demandee factionId -> absolute day of last issued demand.
     * Transient: resets on load (safe — just opens a brief window for a demand to be reissued).
     */
    private transient Map<String, Float> demandCooldowns = new HashMap<String, Float>();

    // Config
    private static int MAX_ACTIONS_PER_DAY = 2;
    private static boolean WAR_PEACE_EXEMPT = true;
    // PRD-022 (22b): demand cooldown
    private static float DEMAND_COOLDOWN_DAYS = 30f;
    // PRD-022 (22h): default contract reserve price
    private static long DEFAULT_CONTRACT_RESERVE_PRICE = 5000L;

    // War decision config
    private static float WAR_CB_BONUS = 40f;
    private static float WAR_MILITARY_ADV_WEIGHT = 30f;
    private static float WAR_AGREEMENT_PENALTY = 50f;
    private static float WAR_ALLIANCE_DETERRENT = 25f;
    private static float WAR_DECLARE_THRESHOLD = 100f;
    private static float WAR_CONSIDER_THRESHOLD = 70f;
    private static float WAR_RANDOM_RANGE = 20f;
    // 2026-07-07 audit: per-faction cooldown between AI war declarations (days)
    private static float WAR_DECLARE_COOLDOWN_DAYS = 60f;

    // Peace decision config
    private static float PEACE_BASE = 30f;
    private static float PEACE_WEARINESS_WEIGHT = 0.005f;
    private static float PEACE_SCOPE_ACHIEVED_BONUS = 40f;
    private static float PEACE_SEEK_THRESHOLD = 80f;
    private static float PEACE_ACCEPT_THRESHOLD = 50f;
    // 2026-07-07 audit: war-state signals that unlock PEACE_SCOPE_ACHIEVED_BONUS
    private static float PEACE_GOAL_SPAWN_WEARINESS = 5000f;
    private static float PEACE_STALEMATE_DAYS = 60f;
    private static float PEACE_SCOPE_WARSCORE = 40f;

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
            WAR_DECLARE_COOLDOWN_DAYS = (float) war.optDouble("declareCooldownDays", 60);
        }
        JSONObject peace = config.optJSONObject("peaceDecision");
        if (peace != null) {
            PEACE_BASE = (float) peace.optDouble("base", 30);
            PEACE_WEARINESS_WEIGHT = (float) peace.optDouble("warWearinessWeight", 0.005);
            PEACE_SCOPE_ACHIEVED_BONUS = (float) peace.optDouble("scopeAchievedBonus", 40);
            PEACE_SEEK_THRESHOLD = (float) peace.optDouble("seekPeaceThreshold", 80);
            PEACE_ACCEPT_THRESHOLD = (float) peace.optDouble("acceptThreshold", 50);
            PEACE_GOAL_SPAWN_WEARINESS = (float) peace.optDouble("goalSpawnWeariness", 5000);
            PEACE_STALEMATE_DAYS = (float) peace.optDouble("stalemateDays", 60);
            PEACE_SCOPE_WARSCORE = (float) peace.optDouble("scopeWarScore", 40);
        }
        // PRD-022 (22b/22h): demand and contract config
        DEMAND_COOLDOWN_DAYS = (float) config.optDouble("demandCooldownDays", 30);
        DEFAULT_CONTRACT_RESERVE_PRICE = config.optLong("contractReservePrice", 5000L);
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
        // PRD-015 (15d): reset per-day peace guard
        if (peaceProposedThisDay == null) peaceProposedThisDay = new HashSet<String>();
        peaceProposedThisDay.clear();
        // PRD-022 (22b): ensure demandCooldowns is initialized after deserialization
        if (demandCooldowns == null) demandCooldowns = new HashMap<String, Float>();

        List<StrategicGoal> goals = goalMgr.getActiveGoals();
        Archetype archetype = grandStrategy.getArchetype(factionId);

        // PRD-022 (22d): evaluate incoming demands before war/peace (budget-exempt)
        evaluateIncomingDemands(archetype);

        // PRD-022 (22l): evaluate coalition votes (budget-exempt)
        evaluateCoalitionVotes(archetype);

        // War/peace decisions first (budget-exempt)
        evaluateWarDecisions(goals, archetype);
        evaluatePeaceDecisions(goals, archetype);

        // Then budgeted diplomatic actions
        for (StrategicGoal goal : goals) {
            if (actionBudgetRemaining <= 0) break;
            executeDiplomaticAction(goal, archetype);
        }

        // PRD-022 (22h): advance bidding on open contracts
        advanceBidding();
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
                // 2026-07-07 audit: gate declarations on cooldown + alliance/pact hard blocks.
                if (!canDeclareWar(goal.targetFactionId, mgr)) continue;
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
        return calculateDoctrineScore(isWarVote, factionId, target);
    }

    /** 2026-07-07 audit: doctrine score for an explicit faction (used for counterparty consent). */
    private float calculateDoctrineScore(boolean isWarVote, String forFactionId, String target) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(forFactionId);
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

        // 2026-07-07 audit: stamp the declaration day so the 60-day cooldown applies.
        lastWarDeclarationDay = nex4x.util.Nex4xClock.currentAbsoluteDay();
    }

    /**
     * 2026-07-07 audit: gate an AI war declaration against {@code targetId}.
     * <ul>
     *   <li>(a) 60-day per-faction declaration cooldown (persisted {@link #lastWarDeclarationDay}).</li>
     *   <li>(b) HARD block if we share a Nex alliance with the target.</li>
     *   <li>(b) HARD block if an active (non-expired) NAP or Defensive Pact exists with the target.</li>
     * </ul>
     * Only gates DECLARATIONS — ongoing war logic is untouched.
     */
    private boolean canDeclareWar(String targetId, Nex4xManager mgr) {
        // (a) declaration cooldown
        if (lastWarDeclarationDay >= 0f) {
            float since = nex4x.util.Nex4xClock.currentAbsoluteDay() - lastWarDeclarationDay;
            if (since < WAR_DECLARE_COOLDOWN_DAYS) {
                log.info("[Nex4x] " + factionId + " war on " + targetId
                        + " blocked: declaration cooldown (" + Math.round(since) + "/"
                        + Math.round(WAR_DECLARE_COOLDOWN_DAYS) + "d)");
                return false;
            }
        }

        // (b) alliance-mate hard block (mirror NexDiplomacyBridge alliance checks)
        try {
            exerelin.campaign.alliances.Alliance ours =
                    exerelin.campaign.AllianceManager.getFactionAlliance(factionId);
            exerelin.campaign.alliances.Alliance theirs =
                    exerelin.campaign.AllianceManager.getFactionAlliance(targetId);
            if (ours != null && ours == theirs) {
                log.info("[Nex4x] " + factionId + " war on " + targetId
                        + " blocked: shared alliance");
                return false;
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] canDeclareWar alliance check failed: " + t.getMessage());
        }

        // (b) active non-aggression / defensive agreement hard block
        AgreementManager aMgr = mgr.getAgreementManager();
        for (nex4x.agreements.Agreement a : aMgr.getAgreementsOfType(factionId, AgreementType.NAP)) {
            if (a.involves(targetId)) {
                log.info("[Nex4x] " + factionId + " war on " + targetId
                        + " blocked: active non-aggression pact");
                return false;
            }
        }
        for (nex4x.agreements.Agreement a : aMgr.getAgreementsOfType(factionId, AgreementType.DEFENSIVE_PACT)) {
            if (a.involves(targetId)) {
                log.info("[Nex4x] " + factionId + " war on " + targetId
                        + " blocked: active defensive pact");
                return false;
            }
        }

        return true;
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
                // 2026-07-07 audit: require two-sided consent. Compute the counterparty's
                // willingness toward us and require it > PEACE_ACCEPT_THRESHOLD before firing,
                // so the treaty only lands when the other faction would also accept.
                float theirWill = counterpartyPeaceWillingness(goal.targetFactionId);
                if (theirWill <= PEACE_ACCEPT_THRESHOLD) {
                    log.info("[Nex4x] " + factionId + " wants peace with " + goal.targetFactionId
                            + " (willingness=" + Math.round(peaceWill) + ") but counterparty declines"
                            + " (theirs=" + Math.round(theirWill) + " <= " + Math.round(PEACE_ACCEPT_THRESHOLD) + ")");
                    continue;
                }

                log.info("[Nex4x] " + factionId + " seeking peace with "
                        + goal.targetFactionId + " (willingness=" + Math.round(peaceWill)
                        + ", counterparty=" + Math.round(theirWill) + ")");

                // PRD-015 (15d): fire the real peace treaty effect.
                // Guard: don't fire twice for the same target on the same day.
                if (peaceProposedThisDay == null) peaceProposedThisDay = new HashSet<String>();
                if (!peaceProposedThisDay.contains(goal.targetFactionId)) {
                    peaceProposedThisDay.add(goal.targetFactionId);
                    FactionAPI us = Global.getSector().getFaction(factionId);
                    FactionAPI them = Global.getSector().getFaction(goal.targetFactionId);
                    if (us != null && them != null && us.isHostileTo(them)) {
                        // PRD-014 bridge is reliable; keep try/catch as defence-in-depth fallback.
                        try {
                            nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(us, them);
                        } catch (Exception e) {
                            log.warn("[Nex4x] Peace treaty bridge failed: " + e.getMessage());
                            if (us.isHostileTo(them)) {
                                us.setRelationship(goal.targetFactionId, 0f);
                                them.setRelationship(factionId, 0f);
                            }
                        }
                    }
                }
            }
        }
    }

    private float calculatePeaceWillingness(StrategicGoal goal, Archetype archetype) {
        String target = goal.targetFactionId;
        float willingness = PEACE_BASE;

        float weariness = getWarWeariness(factionId);
        willingness += weariness * PEACE_WEARINESS_WEIGHT;

        // 2026-07-07 audit: honour PEACE_SCOPE_ACHIEVED_BONUS. Without it, willingness only
        // crosses PEACE_SEEK_THRESHOLD (80) near ~9,000 weariness, while END_WAR spawns at
        // 5,000 — so peace almost never fired. Grant the bonus when the war's objectives are
        // achieved (clear war-score lead) or the war is a stalemate (long war + weariness past
        // the END_WAR spawn threshold). This lets peace realistically fire in the 5,000–10,000
        // weariness band. War age falls back to the END_WAR goal's own age when no WarGoal exists.
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            float warDays = Math.max(goal.getAgeDays(),
                    warDurationDaysBetween(factionId, target, mgr));
            if (isScopeAchievedOrStalemated(factionId, target, weariness, warDays, mgr)) {
                willingness += PEACE_SCOPE_ACHIEVED_BONUS;
            }
        }

        willingness += calculateDoctrineScore(false, factionId, target);
        willingness += (float)(Math.random() * 30) - 15;

        return willingness;
    }

    /**
     * 2026-07-07 audit: deterministic peace willingness of {@code them} toward us, used to
     * gate two-sided consent. No random jitter (this is a consent estimate, not a decision).
     */
    private float counterpartyPeaceWillingness(String them) {
        float willingness = PEACE_BASE;

        float weariness = getWarWeariness(them);
        willingness += weariness * PEACE_WEARINESS_WEIGHT;

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            float warDays = warDurationDaysBetween(them, factionId, mgr);
            if (isScopeAchievedOrStalemated(them, factionId, weariness, warDays, mgr)) {
                willingness += PEACE_SCOPE_ACHIEVED_BONUS;
            }
        }

        willingness += calculateDoctrineScore(false, them, factionId);
        return willingness;
    }

    /** War weariness for a faction, or 0 if Nex's DiplomacyManager is unavailable. */
    private float getWarWeariness(String forFactionId) {
        try {
            exerelin.campaign.DiplomacyManager dipMgr =
                    exerelin.campaign.DiplomacyManager.getManager();
            if (dipMgr != null) {
                return dipMgr.getWarWeariness(forFactionId, true);
            }
        } catch (Exception e) { /* Nex not available */ }
        return 0f;
    }

    /**
     * 2026-07-07 audit: true when {@code self}'s war objectives against {@code target} are
     * achieved (clear war-score lead) or the war is a drawn-out stalemate (age past
     * {@code PEACE_STALEMATE_DAYS} and weariness past the END_WAR spawn threshold).
     */
    private boolean isScopeAchievedOrStalemated(String self, String target, float weariness,
                                                float warDurationDays, Nex4xManager mgr) {
        // Scope achieved: a clear war-score lead means our war aims are effectively met.
        try {
            float score = mgr.getWarScoreTracker().getWarScore(self, target);
            if (score >= PEACE_SCOPE_WARSCORE) return true;
        } catch (Exception ignore) { /* war score unavailable */ }

        // Stalemate: sustained war plus weariness past the point that spawned the END_WAR goal.
        return warDurationDays > PEACE_STALEMATE_DAYS
                && weariness > PEACE_GOAL_SPAWN_WEARINESS;
    }

    /**
     * Best-effort elapsed days of the war between {@code a} and {@code b}, using the earliest
     * WarGoal declaredDay in either direction. Returns 0 when no WarGoal exists (AI wars may
     * be declared without one) — callers pair this with a goal-age fallback.
     */
    private float warDurationDaysBetween(String a, String b, Nex4xManager mgr) {
        float earliest = Float.MAX_VALUE;
        nex4x.wargoals.WarScoreTracker wst = mgr.getWarScoreTracker();
        for (nex4x.wargoals.WarGoal g : wst.getWarGoals(a, b)) {
            earliest = Math.min(earliest, g.getDeclaredDay());
        }
        for (nex4x.wargoals.WarGoal g : wst.getWarGoals(b, a)) {
            earliest = Math.min(earliest, g.getDeclaredDay());
        }
        if (earliest == Float.MAX_VALUE) return 0f;
        return nex4x.util.Nex4xClock.currentAbsoluteDay() - earliest;
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

            // PRD-022 (22c): wire PRESS_GRIEVANCE to demand issuance
            case PRESS_GRIEVANCE:
                if (goal.targetFactionId != null) {
                    issueAIDemand(goal, archetype);
                }
                break;

            // PRD-022 (22c/22h): wire EXPLOIT_WEAKNESS to demand + contract
            case EXPLOIT_WEAKNESS:
                if (goal.targetFactionId != null) {
                    issueAIDemand(goal, archetype);
                    postAIContract(goal, archetype);
                }
                break;

            default:
                break;
        }
    }

    /** PRD-022 (22c): Issue an AI demand based on goal type and archetype. */
    private void issueAIDemand(StrategicGoal goal, Archetype archetype) {
        String targetId = goal.targetFactionId;
        if (!canIssueDemand(targetId)) return;

        // Spam guard: skip if 2+ pending demands already active from us
        DemandManager demandMgr = DemandManager.getOrCreate();
        int pendingCount = 0;
        for (Demand d : demandMgr.getPending(factionId)) {
            if (d.getDemanderId().equals(factionId)) pendingCount++;
        }
        if (pendingCount >= 2) {
            log.info("[Nex4x] " + factionId + " demand spam guard: " + pendingCount
                    + " pending demands already active, skipping");
            return;
        }

        // Choose demand type based on goal type + archetype
        Demand.DemandType type;
        String payload = null;

        if (goal.type == GoalType.PRESS_GRIEVANCE) {
            if (archetype == Archetype.MILITARY_SUPREMACY
                    || archetype == Archetype.TERRITORIAL_EXPANSION) {
                type = Demand.DemandType.TRIBUTE_CREDITS;
                payload = "50000";
            } else if (archetype == Archetype.IDEOLOGICAL_CRUSADE) {
                type = Demand.DemandType.BREAK_ALLIANCE;
            } else {
                type = Demand.DemandType.TRIBUTE_CREDITS;
                payload = "50000";
            }
        } else {
            // EXPLOIT_WEAKNESS: prefer CEDE_MARKET if target has a market, else TRIBUTE_CREDITS
            com.fs.starfarer.api.campaign.econ.MarketAPI targetMarket =
                    nex4x.util.FactionMarketUtil.firstMarketOfFaction(targetId);
            if (targetMarket != null) {
                type = Demand.DemandType.CEDE_MARKET;
                payload = targetMarket.getId();
            } else {
                type = Demand.DemandType.TRIBUTE_CREDITS;
                payload = "50000";
            }
        }

        float pressureCost = 20f;
        float influenceCost = 15f;

        Demand result = demandMgr.issue(factionId, targetId, type, payload,
                pressureCost, influenceCost);
        if (result != null) {
            recordDemandCooldown(targetId);
            actionBudgetRemaining--;
            log.info("[Nex4x] AI demand issued: " + factionId + " -> " + targetId
                    + " type=" + type);
        }
    }

    /** PRD-022 (22h): Post a covert contract for EXPLOIT_WEAKNESS goals. */
    private void postAIContract(StrategicGoal goal, Archetype archetype) {
        String targetId = goal.targetFactionId;

        // Check pressure threshold
        float pressure = nex4x.pressure.PressureManager.getOrCreate()
                .getPressure(factionId, targetId);
        if (pressure <= 40f) return;

        // Check no existing open contract for this issuer-target pair
        ContractAuctionManager contractMgr = ContractAuctionManager.getOrCreate();
        for (nex4x.contracts.Contract c : contractMgr.getOpen()) {
            if (factionId.equals(c.getIssuerFactionId())
                    && targetId.equals(c.getTargetFactionId())) {
                return; // already have an open contract
            }
        }

        ContractType contractType = pickContractType(archetype);
        contractMgr.post(factionId, targetId, contractType, DEFAULT_CONTRACT_RESERVE_PRICE);
        log.info("[Nex4x] AI contract posted: " + factionId + " vs " + targetId
                + " type=" + contractType);
        // Contract posting is not budget-charged (it's a background action)
    }

    /** PRD-022 (22h): Pick a contract type based on archetype. */
    private ContractType pickContractType(Archetype archetype) {
        if (archetype == null) return ContractType.HARASS_FACTION;
        switch (archetype) {
            case MILITARY_SUPREMACY: return ContractType.RAID_FACTION;
            case TERRITORIAL_EXPANSION: return ContractType.BLOCKADE_MARKET;
            case IDEOLOGICAL_CRUSADE: return ContractType.ASSASSINATE_OFFICIAL;
            default: return ContractType.HARASS_FACTION;
        }
    }

    /** PRD-022 (22d): Evaluate all incoming demands directed at this faction. Budget-exempt. */
    private void evaluateIncomingDemands(Archetype archetype) {
        DemandManager demandMgr = DemandManager.getOrCreate();
        for (Demand d : demandMgr.getPending(factionId)) {
            // Only process demands targeted at us
            if (!d.getTargetId().equals(factionId)) continue;

            float acceptScore = 40f;

            // Resistance: military factions resist tribute
            if (archetype == Archetype.MILITARY_SUPREMACY
                    && d.getType() == Demand.DemandType.TRIBUTE_CREDITS) {
                acceptScore -= 30f;
            }

            // Compliance: federalist tendency
            TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
            if (profile != null) {
                float federalistTendency = profile.get(TendencyId.FEDERALISTS);
                if (federalistTendency > 0.5f) acceptScore += 15f;
            }

            // War weariness: already at war with demander → slight resistance
            FactionAPI us = Global.getSector().getFaction(factionId);
            FactionAPI demander = Global.getSector().getFaction(d.getDemanderId());
            if (us != null && demander != null && us.isHostileTo(demander)) {
                acceptScore -= 20f;
            }

            // Jitter
            acceptScore += (float)(Math.random() * 40) - 20f;

            if (acceptScore >= 60f) {
                demandMgr.accept(d);
            } else {
                demandMgr.reject(d);
            }
        }
    }

    /** PRD-022 (22l): Cast coalition votes for pending votes involving this faction. Budget-exempt. */
    private void evaluateCoalitionVotes(Archetype archetype) {
        CoalitionGovernance gov = CoalitionGovernance.getOrCreate();
        for (CoalitionVote vote : gov.getPendingVotes()) {
            if (vote.isResolved()) continue;
            // Skip if already voted
            if (vote.getVotes().containsKey(factionId)) continue;

            // Only vote if we're a coalition member of the proposer, or directly involved
            Nex4xManager mgr = Nex4xManager.getManager();
            if (mgr == null) continue;
            List<String> members = mgr.getAgreementManager()
                    .getCoalitionMembersFor(vote.getProposerFactionId());
            boolean isCoalitionMember = members.contains(factionId)
                    || factionId.equals(vote.getProposerFactionId())
                    || factionId.equals(vote.getTargetFactionId());
            if (!isCoalitionMember) continue;

            boolean inFavor = calculateVoteScore(vote, archetype);
            vote.castVote(factionId, inFavor);
            log.info("[Nex4x] " + factionId + " voted " + (inFavor ? "YEA" : "NAY")
                    + " on " + vote.getType() + " (target=" + vote.getTargetFactionId() + ")");
        }
    }

    /** Determine whether this faction would vote in favor of a coalition vote. */
    private boolean calculateVoteScore(CoalitionVote vote, Archetype archetype) {
        FactionAPI us = Global.getSector().getFaction(factionId);
        FactionAPI target = vote.getTargetFactionId() != null
                ? Global.getSector().getFaction(vote.getTargetFactionId()) : null;

        switch (vote.getType()) {
            case DECLARE_WAR: {
                // Favor if militaristic or already want war
                boolean militaristic = archetype == Archetype.MILITARY_SUPREMACY
                        || archetype == Archetype.TERRITORIAL_EXPANSION;
                float warDesireBonus = 0f;
                try {
                    exerelin.campaign.DiplomacyManager dipMgr =
                            exerelin.campaign.DiplomacyManager.getManager();
                    if (dipMgr != null) {
                        float weariness = dipMgr.getWarWeariness(factionId, true);
                        // low weariness = warlike; high weariness = war-weary
                        if (weariness < 1000f) warDesireBonus = 20f;
                    }
                } catch (Exception ignore) {}
                return militaristic || warDesireBonus > 0f;
            }
            case MAKE_PEACE: {
                // Favor if war-weary
                try {
                    exerelin.campaign.DiplomacyManager dipMgr =
                            exerelin.campaign.DiplomacyManager.getManager();
                    if (dipMgr != null) {
                        float weariness = dipMgr.getWarWeariness(factionId, true);
                        if (weariness > 5000f) return true;
                    }
                } catch (Exception ignore) {}
                return archetype == Archetype.DEFENSIVE_CONSOLIDATION
                        || archetype == Archetype.COALITION_BUILDER;
            }
            case ADD_MEMBER: {
                if (us == null || target == null) return false;
                return us.getRelationship(target.getId()) > 0.1f;
            }
            case KICK_MEMBER: {
                if (us == null || target == null) return true; // default favor kicking enemies
                return us.getRelationship(target.getId()) < -0.1f;
            }
            case DISSOLVE: {
                // Favor dissolution if coalition tensions are high
                List<String> members = new java.util.ArrayList<String>();
                Nex4xManager mgr = Nex4xManager.getManager();
                if (mgr != null) {
                    members = mgr.getAgreementManager()
                            .getCoalitionMembersFor(vote.getProposerFactionId());
                }
                CoalitionGovernance gov = CoalitionGovernance.getOrCreate();
                return gov.getAverageTension(members) > CoalitionGovernance.DISSOLUTION_TENSION_THRESHOLD;
            }
            default:
                return false;
        }
    }

    /** PRD-022 (22h): Advance bidding — bid on open contracts targeting hostile factions. */
    private void advanceBidding() {
        ContractAuctionManager contractMgr = ContractAuctionManager.getOrCreate();
        FactionAPI us = Global.getSector().getFaction(factionId);
        if (us == null) return;
        for (nex4x.contracts.Contract c : contractMgr.getOpen()) {
            FactionAPI target = Global.getSector().getFaction(c.getTargetFactionId());
            if (target == null) continue;
            if (!us.isHostileTo(target)) continue;
            // Don't bid on our own contracts
            if (factionId.equals(c.getIssuerFactionId())) continue;
            // Random bid in range 3000–8000
            long bid = 3000L + (long)(Math.random() * 5000L);
            contractMgr.bid(c, factionId, bid);
            log.info("[Nex4x] " + factionId + " bids " + bid
                    + " on contract " + c.getId() + " vs " + c.getTargetFactionId());
        }
    }

    // PRD-022 (22b): Demand cooldown helpers

    private boolean canIssueDemand(String targetId) {
        if (demandCooldowns == null) demandCooldowns = new HashMap<String, Float>();
        Float last = demandCooldowns.get(targetId);
        if (last == null) return true;
        return (nex4x.util.Nex4xClock.currentAbsoluteDay() - last) >= DEMAND_COOLDOWN_DAYS;
    }

    private void recordDemandCooldown(String targetId) {
        if (demandCooldowns == null) demandCooldowns = new HashMap<String, Float>();
        demandCooldowns.put(targetId, nex4x.util.Nex4xClock.currentAbsoluteDay());
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

            // PRD-015 (15e): actually create the agreement (previously log-only).
            // Return on failure so we don't decrement the budget for a no-op.
            try {
                aMgr.createAgreement(factionId, goal.targetFactionId, nextTier);
                if (nextTier == AgreementType.COALITION) {
                    // Ensure the Nex Alliance shadow is created/joined.
                    try {
                        nex4x.integration.NexDiplomacyBridge.ensureAlliance(
                                factionId, goal.targetFactionId);
                    } catch (Exception bridgeEx) {
                        log.warn("[Nex4x] ensureAlliance bridge failed: " + bridgeEx.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[Nex4x] createAgreement failed: " + e.getMessage());
                return; // don't decrement budget on failure
            }

            actionBudgetRemaining--;
        }
    }

    public String getFactionId() { return factionId; }
}
