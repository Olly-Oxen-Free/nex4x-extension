package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import nex4x.agreements.AgreementType;
import nex4x.evaluation.DealEvaluator;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implements the "Suggest Counter" / auto-negotiate feature (spec §5.2.2).
 * Player puts desired items on one side → AI fills the other with minimum acceptable terms.
 *
 * Also handles AI-initiated proposal generation: the AI constructs deals it wants to propose
 * to the player based on its internal politics priorities.
 */
public class AutoNegotiator {

    private static final Logger log = Global.getLogger(AutoNegotiator.class);

    private final DealEvaluator evaluator;

    public AutoNegotiator(DealEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Given items the player wants, suggest what the AI would need in return.
     * Returns a complete DealPackage with both sides filled, or null if impossible.
     *
     * @param playerRequests Items the player wants from the AI faction
     * @param proposerId Player's faction ID
     * @param targetId AI faction ID
     * @return Balanced DealPackage, or null if the AI would never agree
     */
    public AutoNegotiateResult suggestCounterForRequests(
            List<NegotiableItem> playerRequests, String proposerId, String targetId) {

        ItemValuator valuator = evaluator.getValuator();

        // Calculate total value of what the player wants
        float requestedTotal = 0;
        for (NegotiableItem item : playerRequests) {
            requestedTotal += valuator.evaluate(item, targetId);
        }

        // Check for impossibles (strength-3 belief blocks)
        for (NegotiableItem item : playerRequests) {
            if (isImpossibleRequest(item, targetId, valuator)) {
                return AutoNegotiateResult.impossible(
                        "Would not part with " + item.getDisplayLabel() + " under any terms.");
            }
        }

        // AI wants at least 110% of what it's giving up (small surplus for AI)
        float targetValue = requestedTotal * 1.1f;

        // Apply negotiation style inflation
        NegotiationStyle style = NegotiationStyle.deriveFromProfile(targetId);
        targetValue *= style.openingAskMultiplier;

        // Build a package of what the AI would ask in return
        DealPackage deal = new DealPackage(proposerId, targetId);
        for (NegotiableItem item : playerRequests) {
            deal.addRequest(item);
        }

        // Try to fill offers with credits first (simplest)
        deal.addOffer(NegotiableItem.credits(targetValue));

        return AutoNegotiateResult.success(deal);
    }

    /**
     * Given items the player offers, suggest what the AI would give in return.
     * @param playerOffers Items the player is offering to the AI faction
     * @param proposerId Player's faction ID
     * @param targetId AI faction ID
     * @return Balanced DealPackage, or null if the AI doesn't want what's offered
     */
    public AutoNegotiateResult suggestCounterForOffers(
            List<NegotiableItem> playerOffers, String proposerId, String targetId) {

        ItemValuator valuator = evaluator.getValuator();

        // Calculate total value of what the player offers
        float offeredTotal = 0;
        for (NegotiableItem item : playerOffers) {
            offeredTotal += valuator.evaluate(item, targetId);
        }

        if (offeredTotal <= 0) {
            return AutoNegotiateResult.impossible("Nothing of value offered.");
        }

        // AI will give up items worth ~90% of what it receives (it keeps a surplus)
        float willGive = offeredTotal * 0.9f;

        // Build package — AI offers credits equivalent
        DealPackage deal = new DealPackage(proposerId, targetId);
        for (NegotiableItem item : playerOffers) {
            deal.addOffer(item);
        }
        deal.addRequest(NegotiableItem.credits(willGive));

        return AutoNegotiateResult.success(deal);
    }

    /**
     * Generate an AI-initiated proposal to the player.
     * The AI picks items from categories its internal politics favor.
     * @param aiFactionId The faction making the proposal
     * @param playerFactionId The player faction
     * @param currentTier Current alliance tier between the factions
     * @return A DealPackage the AI wants to propose, or null if no deal is worth proposing
     */
    public DealPackage generateAIProposal(String aiFactionId, String playerFactionId,
                                          AgreementType currentTier) {
        NegotiationStyle style = NegotiationStyle.deriveFromProfile(aiFactionId);

        // Check what the AI most wants based on tendency priorities
        // For v1a, the simplest valuable proposal: offer what AI has, ask for what it wants
        // Full AI proposal logic depends on wiring to Nex market/fleet APIs (v1b+)

        // Default: propose an agreement upgrade if available
        AgreementType nextTier = currentTier.getNextAllianceTier();
        if (nextTier != null) {
            float rel = Global.getSector().getFaction(aiFactionId)
                    .getRelationship(playerFactionId);
            if (rel >= nextTier.relationThreshold / 100f) {
                DealPackage deal = new DealPackage(aiFactionId, playerFactionId);
                deal.addOffer(NegotiableItem.agreement(nextTier));
                deal.addRequest(NegotiableItem.agreement(nextTier));
                return deal;
            }
        }

        return null;
    }

    private boolean isImpossibleRequest(NegotiableItem item, String factionId, ItemValuator valuator) {
        // Items with extremely high belief multiplier are effectively untradeable
        float value = valuator.evaluate(item, factionId);
        float baseValue = valuator.getBaseValue(item);
        if (baseValue <= 0) return false;
        // If beliefs inflate the value by 2.5x+, it's a hard block
        return (value / baseValue) >= 2.4f;
    }

    // ── Result container ───────────────────────────────────────

    public static class AutoNegotiateResult {
        public final boolean possible;
        public final DealPackage deal;
        public final String impossibleReason;

        private AutoNegotiateResult(boolean possible, DealPackage deal, String impossibleReason) {
            this.possible = possible;
            this.deal = deal;
            this.impossibleReason = impossibleReason;
        }

        public static AutoNegotiateResult success(DealPackage deal) {
            return new AutoNegotiateResult(true, deal, null);
        }

        public static AutoNegotiateResult impossible(String reason) {
            return new AutoNegotiateResult(false, null, reason);
        }
    }
}
