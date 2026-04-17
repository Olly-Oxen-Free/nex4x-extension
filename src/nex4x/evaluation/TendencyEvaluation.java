package nex4x.evaluation;

import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import nex4x.negotiation.DealPackage;
import nex4x.negotiation.NegotiableItem;
import nex4x.negotiation.NegotiableItemType;

import java.util.EnumMap;
import java.util.List;

/**
 * Evaluates a deal through the lens of each political tendency.
 * Each tendency scores the deal based on what it cares about.
 * The score is weighted by the tendency's strength in the faction profile.
 *
 * Spec §5.2.2 step 4: Internal vote.
 */
public class TendencyEvaluation {

    /**
     * Run the internal politics vote on a deal.
     * @param deal The deal being proposed (offers = what target receives, requests = what target gives)
     * @param factionId The evaluating faction (target of the deal)
     * @param dealBalance Pre-calculated deal balance from target's perspective
     * @param desperation Faction's desperation score (0-100)
     * @return VoteResult with per-tendency scores
     */
    public static VoteResult evaluate(DealPackage deal, String factionId,
                                      float dealBalance, float desperation) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) {
            // No profile — accept if balance positive
            EnumMap<TendencyId, Float> empty = new EnumMap<TendencyId, Float>(TendencyId.class);
            for (TendencyId t : TendencyId.values()) empty.put(t, 0f);
            return new VoteResult(empty, dealBalance >= 0 ? "No strong opinion." : "Deal seems unfavorable.");
        }

        EnumMap<TendencyId, Float> scores = new EnumMap<TendencyId, Float>(TendencyId.class);

        for (TendencyId tendency : TendencyId.values()) {
            float weight = profile.getWeight(tendency);

            // Apply desperation dynamic boost to Federalists
            if (tendency == TendencyId.FEDERALISTS) {
                weight += DesperationCalculator.getFederalistBoost(desperation);
            }

            float tendencyScore = scoreDeal(tendency, deal, dealBalance);
            scores.put(tendency, weight * tendencyScore);
        }

        String summary = buildSummary(scores, deal);
        return new VoteResult(scores, summary);
    }

    /**
     * Score a deal from a single tendency's perspective.
     * Returns raw score (positive = supports, negative = opposes).
     * Caller multiplies by tendency weight.
     */
    private static float scoreDeal(TendencyId tendency, DealPackage deal, float dealBalance) {
        float score = 0;

        // Base: all tendencies like positive balance
        score += dealBalance > 0 ? 0.5f : (dealBalance < 0 ? -0.5f : 0);

        List<NegotiableItem> received = deal.getOffers();   // what target receives
        List<NegotiableItem> given = deal.getRequests();     // what target gives up

        switch (tendency) {
            case MILITARISTS:
                score += scoreMilitarist(received, given);
                break;
            case FEDERALISTS:
                score += scoreFederalist(received, given);
                break;
            case ZEALOTS:
                score += scoreZealot(received, given, deal);
                break;
            case CORPORATISTS:
                score += scoreCorporatist(received, given, dealBalance);
                break;
            case INDUSTRIALISTS:
                score += scoreIndustrialist(received, given);
                break;
            case ECOLOGISTS:
                score += scoreEcologist(received, given);
                break;
        }

        return score;
    }

    private static float scoreMilitarist(List<NegotiableItem> received, List<NegotiableItem> given) {
        float s = 0;
        // Positive: gaining territory, military contracts
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.TERRITORY) s += 2f;
            if (item.getType() == NegotiableItemType.CONTRACTS) s += 1f;
            if (item.getType() == NegotiableItemType.KNOWLEDGE) s += 0.5f;
        }
        // Negative: giving up territory, signing peace when winning
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.TERRITORY) s -= 3f;
            if (item.getType() == NegotiableItemType.PEACE_TERMS) s -= 1f;
            if (item.getType() == NegotiableItemType.TRIBUTE) s -= 0.5f;
        }
        return s;
    }

    private static float scoreFederalist(List<NegotiableItem> received, List<NegotiableItem> given) {
        float s = 0;
        // Positive: agreements, peace terms
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.AGREEMENTS) s += 2f;
            if (item.getType() == NegotiableItemType.PEACE_TERMS) s += 1.5f;
        }
        // Positive: offering peace
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.PEACE_TERMS) s += 1f;
        }
        // Negative: war declarations, breaking agreements
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s -= 2f;
        }
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s -= 2f;
            if (item.getType() == NegotiableItemType.CONCESSIONS) s -= 1f;
        }
        return s;
    }

    private static float scoreZealot(List<NegotiableItem> received, List<NegotiableItem> given,
                                     DealPackage deal) {
        float s = 0;
        // War declarations are supported (especially against ideological enemies)
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s += 1.5f;
        }
        // Concessions against others
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.CONCESSIONS) s += 1f;
        }
        // Zealots dislike signing agreements (seen as weakness)
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.AGREEMENTS) s -= 1f;
        }
        // Zealots hate making concessions
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.CONCESSIONS) s -= 2f;
        }
        return s;
    }

    private static float scoreCorporatist(List<NegotiableItem> received, List<NegotiableItem> given,
                                          float dealBalance) {
        float s = 0;
        // Corporatists care about profit — balance is king
        if (dealBalance > 0) s += 1.5f;
        if (dealBalance > 5000) s += 1f;

        // Value economic items received
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.CREDITS) s += 1f;
            if (item.getType() == NegotiableItemType.TRIBUTE) s += 1.5f;
            if (item.getType() == NegotiableItemType.COMMODITIES) s += 0.5f;
            if (item.getType() == NegotiableItemType.KNOWLEDGE) s += 1f;
        }
        // Dislike unprofitable war commitments
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s -= 1.5f;
        }
        return s;
    }

    private static float scoreIndustrialist(List<NegotiableItem> received, List<NegotiableItem> given) {
        float s = 0;
        // Value commodity and territory gains
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.COMMODITIES) s += 1.5f;
            if (item.getType() == NegotiableItemType.TERRITORY) s += 1.5f;
            if (item.getType() == NegotiableItemType.CONTRACTS) s += 1f;
        }
        // Dislike losing productive territory
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.TERRITORY) s -= 2f;
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s -= 0.5f;
        }
        return s;
    }

    private static float scoreEcologist(List<NegotiableItem> received, List<NegotiableItem> given) {
        float s = 0;
        // Value peace and territory gains
        for (NegotiableItem item : received) {
            if (item.getType() == NegotiableItemType.TERRITORY) s += 1f;
            if (item.getType() == NegotiableItemType.PEACE_TERMS) s += 1.5f;
        }
        // Dislike war and destructive concessions
        for (NegotiableItem item : given) {
            if (item.getType() == NegotiableItemType.WAR_DECLARATION) s -= 1f;
            if (item.getType() == NegotiableItemType.TERRITORY) s -= 1f;
        }
        return s;
    }

    // ── Summary builder ────────────────────────────────────────

    private static String buildSummary(EnumMap<TendencyId, Float> scores, DealPackage deal) {
        TendencyId strongestFor = null;
        TendencyId strongestAgainst = null;
        float maxFor = 0, maxAgainst = 0;

        for (TendencyId t : TendencyId.values()) {
            Float s = scores.get(t);
            if (s == null) continue;
            if (s > maxFor) { maxFor = s; strongestFor = t; }
            if (s < maxAgainst) { maxAgainst = s; strongestAgainst = t; }
        }

        float total = 0;
        for (float v : scores.values()) total += v;

        StringBuilder sb = new StringBuilder();
        if (total > 5) {
            sb.append("Strong internal support.");
        } else if (total > 0) {
            sb.append("Marginal internal support.");
        } else if (total > -5) {
            sb.append("Internal opposition outweighs support.");
        } else {
            sb.append("Strong internal opposition.");
        }

        if (strongestFor != null && maxFor > 1) {
            sb.append(" ").append(strongestFor.displayName).append(" favor this deal.");
        }
        if (strongestAgainst != null && maxAgainst < -1) {
            sb.append(" ").append(strongestAgainst.displayName).append(" oppose it.");
        }

        return sb.toString();
    }
}
