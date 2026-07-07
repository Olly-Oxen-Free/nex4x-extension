package nex4x.evaluation;

import com.fs.starfarer.api.Global;
import nex4x.data.FactionBeliefs;
import nex4x.data.FactionBeliefsLoader;
import nex4x.data.BeliefDef;
import nex4x.managers.Nex4xManager;
import nex4x.policies.PolicyManager;
import nex4x.negotiation.DealPackage;
import nex4x.negotiation.ItemValuator;
import nex4x.negotiation.NegotiableItem;
import nex4x.negotiation.NegotiableItemType;
import org.apache.log4j.Logger;

/**
 * Orchestrates the full AI evaluation pipeline for a deal proposal (spec §5.2.2).
 *
 * Pipeline:
 * 1. Deal balance check — subjective item valuations via ItemValuator
 * 2. Belief filter — hard-block strength-3 belief items if deal isn't overwhelming
 * 3. Memory modifier — disposition shifts the acceptance threshold
 * 4. Internal vote — tendency-weighted scoring via TendencyEvaluation
 * 5. Final decision — combine all signals
 */
public class DealEvaluator {

    private static final Logger log = Global.getLogger(DealEvaluator.class);

    private final ItemValuator valuator;

    /** PRD-016: leader/proposer for the personality/goal/relation/scarcity pipeline. Null = legacy. */
    private final nex4x.leaders.LeaderProfile targetLeader;
    private final String proposerFactionId;

    /** Legacy no-arg constructor — personality/goal/scarcity disabled. Logs a warning. */
    public DealEvaluator() {
        this.valuator = new ItemValuator();
        this.targetLeader = null;
        this.proposerFactionId = null;
        log.warn("[Nex4x] DealEvaluator constructed without leader — personality/goal/scarcity disabled");
    }

    public DealEvaluator(ItemValuator valuator) {
        this.valuator = valuator;
        this.targetLeader = null;
        this.proposerFactionId = null;
    }

    /** PRD-016 leader-aware constructor — routes evaluate() through ItemValuator.valueForLeader. */
    public DealEvaluator(nex4x.leaders.LeaderProfile targetLeader, String proposerFactionId) {
        this.valuator = new ItemValuator();
        this.targetLeader = targetLeader;
        this.proposerFactionId = proposerFactionId;
    }

    /**
     * Full evaluation of a deal from the target faction's perspective.
     * @param deal The proposed deal
     * @return EvaluationResult with accept/reject decision and reasoning
     */
    public EvaluationResult evaluate(DealPackage deal) {
        String targetId = deal.getTargetFactionId();
        String proposerId = deal.getProposerFactionId();

        // Step 1: Deal balance (subjective per-item values)
        // PRD-016: use leader-aware balance when a leader is wired (personality/goal/scarcity).
        float balance = targetLeader != null
                ? deal.getLeaderBalance(targetLeader, proposerFactionId)
                : deal.getBalance(valuator);

        // Step 2: Belief hard-block check
        String beliefBlock = checkBeliefHardBlocks(deal, targetId);
        if (beliefBlock != null) {
            return EvaluationResult.reject(balance, null,
                    "Would never agree to this. " + beliefBlock);
        }

        // Step 3: Memory modifier — disposition shifts threshold
        float dispositionMod = getDispositionModifier(targetId, proposerId);
        float adjustedBalance = balance + dispositionMod;

        // Active policies shift negotiation tolerance (negative negotiation_fatigue = more lenient).
        try {
            float fatigueMod = PolicyManager.getOrCreate().getPolicyModifier(targetId, "negotiation_fatigue");
            adjustedBalance -= fatigueMod * 400f;
        } catch (Exception ignore) { }

        // Step 4: Desperation
        float desperation = DesperationCalculator.calculate(targetId);

        // Step 5: Internal vote
        VoteResult vote = TendencyEvaluation.evaluate(deal, targetId, adjustedBalance, desperation);

        // Step 6: Final decision
        return makeFinalDecision(adjustedBalance, vote, desperation, deal);
    }

    /**
     * Quick balance check for UI display (no full evaluation).
     * PRD-016: uses leader-aware path when leader is wired.
     * @return Raw deal balance from target's perspective
     */
    public float quickBalance(DealPackage deal) {
        return targetLeader != null
                ? deal.getLeaderBalance(targetLeader, proposerFactionId)
                : deal.getBalance(valuator);
    }

    /** Get the ItemValuator for external use (e.g., UI tooltips). */
    public ItemValuator getValuator() { return valuator; }

    // ── Step 2: Belief hard-blocks ─────────────────────────────

    /**
     * Check if any requested items are hard-blocked by strength-3 beliefs.
     * Returns null if no blocks, or a reason string if blocked.
     */
    private String checkBeliefHardBlocks(DealPackage deal, String factionId) {
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        if (beliefs == null) return null;

        for (NegotiableItem item : deal.getRequests()) {
            // Territory touching territorial beliefs at strength 3
            if (item.getType() == NegotiableItemType.TERRITORY) {
                FactionBeliefs.BeliefEntry strongest =
                        beliefs.getStrongestInCategory(BeliefDef.Category.TERRITORIAL);
                if (strongest != null && strongest.strength >= 3) {
                    BeliefDef def = nex4x.data.BeliefRegistry.get(strongest.beliefId);
                    String beliefName = def != null ? def.name : strongest.beliefId;
                    return "Core belief prevents ceding territory: " + beliefName;
                }
            }

            // Agreements that violate ideological beliefs at strength 3
            if (item.getType() == NegotiableItemType.AGREEMENTS) {
                FactionBeliefs.BeliefEntry strongest =
                        beliefs.getStrongestInCategory(BeliefDef.Category.IDEOLOGICAL);
                if (strongest != null && strongest.strength >= 3) {
                    // Ideological hard-block only applies to certain agreement types
                    // (e.g., Luddic Path won't sign anything with AI users)
                    // Full context-dependent check deferred to Nex integration
                }
            }
        }
        return null;
    }

    // ── Step 3: Memory disposition modifier ────────────────────

    /**
     * Get disposition modifier from memories.
     * Per spec: each -10 disposition from memories = +10% price increase on requested items,
     * meaning the effective balance shifts against the deal.
     * Positive disposition = balance shifts in favor.
     */
    private float getDispositionModifier(String targetId, String proposerId) {
        try {
            Nex4xManager mgr = Nex4xManager.getManager();
            if (mgr == null) return 0;
            float disposition = mgr.getMemoryManager().getDispositionModifier(targetId, proposerId);
            // Each 10 points of disposition = 10% effective balance shift
            // Positive disposition = favorable shift
            return disposition * 100f; // disposition is typically -1 to 1, scale to credit value range
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Step 6: Final decision ─────────────────────────────────

    private EvaluationResult makeFinalDecision(float balance, VoteResult vote,
                                                float desperation, DealPackage deal) {
        float voteTotal = vote.getTotal();

        // Desperation lowers the acceptance bar
        float desperationLeniency = desperation * 50f; // at 100 desperation, accept deals 5000 credits underwater

        float combinedScore = balance + (voteTotal * 200f) + desperationLeniency;

        if (combinedScore > 2000) {
            return EvaluationResult.accept(balance, vote,
                    vote.isStrongApproval()
                            ? "Eager to accept this deal."
                            : "Finds these terms acceptable.");
        } else if (combinedScore > 0) {
            return EvaluationResult.accept(balance, vote,
                    "Reluctantly accepts these terms.");
        } else if (combinedScore > -2000) {
            return EvaluationResult.reject(balance, vote,
                    vote.isStrongRejection()
                            ? "Strong internal opposition blocks this deal."
                            : "Considers the terms unfavorable.");
        } else {
            return EvaluationResult.reject(balance, vote,
                    "Would not consider this deal under any circumstances.");
        }
    }

    // ── Result container ───────────────────────────────────────

    public static class EvaluationResult {
        public final boolean accepted;
        public final float dealBalance;
        public final VoteResult vote;
        public final String reason;

        private EvaluationResult(boolean accepted, float dealBalance, VoteResult vote, String reason) {
            this.accepted = accepted;
            this.dealBalance = dealBalance;
            this.vote = vote;
            this.reason = reason;
        }

        public static EvaluationResult accept(float balance, VoteResult vote, String reason) {
            return new EvaluationResult(true, balance, vote, reason);
        }

        public static EvaluationResult reject(float balance, VoteResult vote, String reason) {
            return new EvaluationResult(false, balance, vote, reason);
        }
    }
}
