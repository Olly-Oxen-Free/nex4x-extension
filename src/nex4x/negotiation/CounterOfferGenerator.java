package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import nex4x.evaluation.DealEvaluator;
import nex4x.evaluation.DesperationCalculator;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Generates counter-offers when the AI rejects a deal (spec §5.2.2).
 * The counter modifies the rejected deal to bring it closer to the AI's acceptable terms.
 *
 * Strategy varies by NegotiationStyle:
 * - Anchoring: reduces demands toward real target value over rounds
 * - Cooperative: splits the difference, adds small sweeteners
 * - Erratic: random adjustments
 * - Rigid: never counters
 */
public class CounterOfferGenerator {

    private static final Logger log = Global.getLogger(CounterOfferGenerator.class);

    private final ItemValuator valuator;
    private final Random random;

    public CounterOfferGenerator(ItemValuator valuator) {
        this.valuator = valuator;
        this.random = new Random();
    }

    /**
     * Attempt to generate a counter-offer for a rejected deal.
     * @param originalDeal The deal that was rejected
     * @param currentRound Current negotiation round (1-based)
     * @return A modified DealPackage, or null if the AI won't counter
     */
    public DealPackage generateCounter(DealPackage originalDeal, int currentRound) {
        String targetId = originalDeal.getTargetFactionId();
        NegotiationStyle style = NegotiationStyle.deriveFromProfile(targetId);

        // Rigid factions never counter
        if (!style.willCounterOffer()) return null;

        // Check round limit
        if (currentRound >= style.maxRounds) return null;

        // Roll for counter-offer chance
        float chance = style.getCounterOfferChance();
        float desperation = DesperationCalculator.calculate(targetId);
        // Desperation increases willingness to counter (want to make a deal)
        chance += desperation * 0.003f; // +30% at desperation 100
        chance = Math.min(chance, 0.95f);

        if (random.nextFloat() > chance) return null;

        // Generate the counter based on style
        switch (style) {
            case ANCHORING:
                return anchoringCounter(originalDeal, currentRound, style);
            case COOPERATIVE:
                return cooperativeCounter(originalDeal);
            case ERRATIC:
                return erraticCounter(originalDeal);
            default:
                return null;
        }
    }

    /**
     * Anchoring counter: move demands toward real target.
     * Round 1 reject: reduce ask by ~25%. Round 2: reduce by another ~15%.
     */
    private DealPackage anchoringCounter(DealPackage original, int round, NegotiationStyle style) {
        String targetId = original.getTargetFactionId();
        String proposerId = original.getProposerFactionId();

        DealPackage counter = new DealPackage(proposerId, targetId);

        // Keep all offers (what proposer gives) — don't touch what we'd receive
        for (NegotiableItem item : original.getOffers()) {
            counter.addOffer(item);
        }

        // Reduce requests: scale down monetary items toward real value
        float reductionFactor = round == 1 ? 0.75f : 0.85f;
        for (NegotiableItem item : original.getRequests()) {
            NegotiableItem adjusted = adjustItemAmount(item, reductionFactor);
            if (adjusted != null) {
                counter.addRequest(adjusted);
            } else {
                counter.addRequest(item);
            }
        }

        // If balance is still negative, add a small credit sweetener from AI
        float balance = counter.getBalance(valuator);
        if (balance < 0) {
            float sweetener = Math.abs(balance) * 0.3f;
            counter.addOffer(NegotiableItem.credits(sweetener));
        }

        return counter;
    }

    /**
     * Cooperative counter: split the difference, try to find middle ground.
     */
    private DealPackage cooperativeCounter(DealPackage original) {
        String targetId = original.getTargetFactionId();
        String proposerId = original.getProposerFactionId();

        DealPackage counter = new DealPackage(proposerId, targetId);

        // Copy offers, add a small sweetener
        for (NegotiableItem item : original.getOffers()) {
            counter.addOffer(item);
        }

        // Scale down requests by ~15% (split the difference)
        for (NegotiableItem item : original.getRequests()) {
            NegotiableItem adjusted = adjustItemAmount(item, 0.85f);
            if (adjusted != null) {
                counter.addRequest(adjusted);
            } else {
                counter.addRequest(item);
            }
        }

        // Cooperative factions offer a small credit bonus to show goodwill
        float balance = counter.getBalance(valuator);
        if (balance < -500) {
            float sweetener = Math.abs(balance) * 0.5f;
            counter.addOffer(NegotiableItem.credits(sweetener));
        }

        return counter;
    }

    /**
     * Erratic counter: random adjustments to both sides.
     */
    private DealPackage erraticCounter(DealPackage original) {
        String targetId = original.getTargetFactionId();
        String proposerId = original.getProposerFactionId();

        DealPackage counter = new DealPackage(proposerId, targetId);

        // Randomly adjust offers
        for (NegotiableItem item : original.getOffers()) {
            float factor = 0.8f + random.nextFloat() * 0.4f; // 0.8 to 1.2
            NegotiableItem adjusted = adjustItemAmount(item, factor);
            counter.addOffer(adjusted != null ? adjusted : item);
        }

        // Randomly adjust requests
        for (NegotiableItem item : original.getRequests()) {
            float factor = 0.7f + random.nextFloat() * 0.6f; // 0.7 to 1.3
            NegotiableItem adjusted = adjustItemAmount(item, factor);
            counter.addRequest(adjusted != null ? adjusted : item);
        }

        return counter;
    }

    /**
     * Create a new item with adjusted amount. Returns null if item type can't be scaled.
     */
    private NegotiableItem adjustItemAmount(NegotiableItem item, float factor) {
        switch (item.getType()) {
            case CREDITS:
                return NegotiableItem.credits(item.getAmount() * factor);
            case TRIBUTE:
                return NegotiableItem.tribute(item.getAmount() * factor, item.getDurationDays());
            case COMMODITIES:
                int newQty = Math.max(1, Math.round(item.getAmount() * factor));
                return NegotiableItem.commodity(item.getTargetId(), newQty);
            case PEACE_TERMS:
                if ("reparations".equals(item.getSecondaryId())) {
                    return NegotiableItem.warReparations(item.getAmount() * factor);
                }
                return null;
            default:
                // Non-numeric items (territory, agreements, etc.) can't be scaled
                return null;
        }
    }
}
