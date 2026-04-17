package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agreements.AgreementType;
import nex4x.data.*;
import nex4x.declarations.DeclarationType;
import org.apache.log4j.Logger;

/**
 * Calculates subjective value of negotiable items from a specific faction's perspective.
 * Applies: baseValue x beliefMultiplier x tendencyMultiplier.
 *
 * Deal-level modifiers (memory disposition, desperation, internal vote) are applied
 * by DealEvaluator — this class handles per-item valuation only.
 */
public class ItemValuator {

    private static final Logger log = Global.getLogger(ItemValuator.class);

    // ── Tendency modifier table ────────────────────────────────
    // [TendencyId ordinal][NegotiableItemType ordinal] → modifier
    // Applied as: 1 + sum(weight_fraction × modifier) across all tendencies
    private static final float[][] TENDENCY_MODS = buildTendencyTable();

    private static float[][] buildTendencyTable() {
        int T = TendencyId.values().length;
        int I = NegotiableItemType.values().length;
        float[][] t = new float[T][I];

        int MIL = TendencyId.MILITARISTS.ordinal();
        t[MIL][NegotiableItemType.WAR_DECLARATION.ordinal()] = 0.3f;
        t[MIL][NegotiableItemType.TERRITORY.ordinal()] = 0.2f;
        t[MIL][NegotiableItemType.CONTRACTS.ordinal()] = 0.2f;
        t[MIL][NegotiableItemType.PEACE_TERMS.ordinal()] = -0.2f;
        t[MIL][NegotiableItemType.TRIBUTE.ordinal()] = -0.15f;

        // Militarists somewhat value hostile declarations
        t[MIL][NegotiableItemType.DECLARATIONS.ordinal()] = 0.1f;

        int FED = TendencyId.FEDERALISTS.ordinal();
        t[FED][NegotiableItemType.AGREEMENTS.ordinal()] = 0.3f;
        // Federalists strongly value positive declarations like Friendship
        t[FED][NegotiableItemType.DECLARATIONS.ordinal()] = 0.2f;
        t[FED][NegotiableItemType.PEACE_TERMS.ordinal()] = 0.25f;
        t[FED][NegotiableItemType.WAR_DECLARATION.ordinal()] = -0.2f;
        t[FED][NegotiableItemType.CONCESSIONS.ordinal()] = -0.15f;

        int ZEA = TendencyId.ZEALOTS.ordinal();
        t[ZEA][NegotiableItemType.WAR_DECLARATION.ordinal()] = 0.2f;
        t[ZEA][NegotiableItemType.CONCESSIONS.ordinal()] = 0.15f;
        t[ZEA][NegotiableItemType.AGREEMENTS.ordinal()] = -0.1f;

        int COR = TendencyId.CORPORATISTS.ordinal();
        t[COR][NegotiableItemType.CREDITS.ordinal()] = 0.25f;
        t[COR][NegotiableItemType.TRIBUTE.ordinal()] = 0.2f;
        t[COR][NegotiableItemType.COMMODITIES.ordinal()] = 0.2f;
        t[COR][NegotiableItemType.KNOWLEDGE.ordinal()] = 0.15f;
        t[COR][NegotiableItemType.AGREEMENTS.ordinal()] = 0.1f;
        t[COR][NegotiableItemType.WAR_DECLARATION.ordinal()] = -0.15f;

        int IND = TendencyId.INDUSTRIALISTS.ordinal();
        t[IND][NegotiableItemType.COMMODITIES.ordinal()] = 0.25f;
        t[IND][NegotiableItemType.TERRITORY.ordinal()] = 0.2f;
        t[IND][NegotiableItemType.CONTRACTS.ordinal()] = 0.15f;
        t[IND][NegotiableItemType.WAR_DECLARATION.ordinal()] = -0.1f;

        int ECO = TendencyId.ECOLOGISTS.ordinal();
        t[ECO][NegotiableItemType.TERRITORY.ordinal()] = 0.15f;
        t[ECO][NegotiableItemType.PEACE_TERMS.ordinal()] = 0.2f;
        t[ECO][NegotiableItemType.CONCESSIONS.ordinal()] = -0.1f;

        return t;
    }

    // ── Base value constants ───────────────────────────────────

    private static final float CEASEFIRE_BASE = 5000f;
    private static final float PEACE_TREATY_BASE = 8000f;
    private static final float INTEL_BASE = 2000f;
    private static final float PRISONER_BASE = 3000f;
    private static final float CONTRACT_BASE_PER_DAY = 20f;

    // Declaration base values
    private static final float FRIENDSHIP_BASE = 3500f;
    private static final float GUARANTEE_BASE = 5000f;
    private static final float WITHDRAW_DENOUNCE_BASE = 2000f;
    private static final float WITHDRAW_RIVALRY_BASE = 3000f;
    private static final float DENOUNCE_BASE = 2500f;
    private static final float RIVALRY_BASE = 4000f;

    // Agreement base values by tier
    private static final float NAP_BASE = 3000f;
    private static final float DEFENSIVE_PACT_BASE = 6000f;
    private static final float PARTNERSHIP_BASE = 10000f;
    private static final float COALITION_BASE = 20000f;
    private static final float TRADE_AGREEMENT_BASE = 4000f;

    // Territory value: market size^2 × this constant
    private static final float TERRITORY_SIZE_MULT = 2000f;

    // ── Public API ─────────────────────────────────────────────

    /**
     * Calculate subjective value of an item for a faction.
     * @param item The negotiable item
     * @param factionId The faction evaluating this item
     * @return Subjective value (positive; higher = more valuable to this faction)
     */
    public float evaluate(NegotiableItem item, String factionId) {
        float base = getBaseValue(item);
        float beliefMult = getBeliefMultiplier(item, factionId);
        float tendencyMult = getTendencyMultiplier(item, factionId);
        return base * beliefMult * tendencyMult;
    }

    // ── Base value calculation ─────────────────────────────────

    public float getBaseValue(NegotiableItem item) {
        switch (item.getType()) {
            case CREDITS:
                return item.getAmount();

            case TRIBUTE:
                // Net present value: per-cycle amount × cycles remaining
                return item.getAmount() * (item.getDurationDays() / 30f);

            case COMMODITIES:
                return getCommodityValue(item.getTargetId(), (int) item.getAmount());

            case TERRITORY:
                return getTerritoryValue(item.getTargetId());

            case AGREEMENTS:
                return getAgreementValue(item.getAgreementType());

            case WAR_DECLARATION:
                return getWarDeclarationValue(item.getTargetId());

            case PEACE_TERMS:
                return getPeaceTermValue(item);

            case KNOWLEDGE:
                return getKnowledgeValue(item.getTargetId());

            case INTEL:
                return INTEL_BASE;

            case CONTRACTS:
                return CONTRACT_BASE_PER_DAY * item.getDurationDays();

            case CONCESSIONS:
                return getConcessionValue(item);

            case PRISONERS:
                return PRISONER_BASE;

            case DECLARATIONS:
                return getDeclarationValue(item);

            default:
                return 1000f;
        }
    }

    private float getCommodityValue(String commodityId, int quantity) {
        if (commodityId == null) return 0;
        try {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
            if (spec != null) {
                return spec.getBasePrice() * quantity;
            }
        } catch (Exception e) {
            log.warn("[Nex4x] Could not get commodity spec for: " + commodityId);
        }
        return 100f * quantity; // fallback
    }

    private float getTerritoryValue(String marketId) {
        if (marketId == null) return 5000f;
        try {
            MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
            if (market != null) {
                int size = market.getSize();
                float industryCount = market.getIndustries().size();
                float stability = market.getStabilityValue();
                // size^2 base + industry bonus + stability bonus
                return (size * size * TERRITORY_SIZE_MULT)
                        + (industryCount * 1000f)
                        + (stability * 200f);
            }
        } catch (Exception e) {
            log.warn("[Nex4x] Could not evaluate market: " + marketId);
        }
        return 5000f; // fallback
    }

    private float getAgreementValue(AgreementType type) {
        if (type == null) return 3000f;
        switch (type) {
            case NAP: return NAP_BASE;
            case DEFENSIVE_PACT: return DEFENSIVE_PACT_BASE;
            case MILITARY_PARTNERSHIP: return PARTNERSHIP_BASE;
            case ECONOMIC_PARTNERSHIP: return PARTNERSHIP_BASE;
            case COALITION: return COALITION_BASE;
            case TRADE_AGREEMENT: return TRADE_AGREEMENT_BASE;
            default: return 3000f;
        }
    }

    private float getWarDeclarationValue(String targetFactionId) {
        // War is expensive. Base 15000, scaled by target faction's strength.
        // Full strength calculation deferred to Nexerelin integration —
        // for now, use a flat high value.
        return 15000f;
    }

    private float getPeaceTermValue(NegotiableItem item) {
        String subType = item.getSecondaryId();
        if ("ceasefire".equals(subType)) return CEASEFIRE_BASE;
        if ("peace_treaty".equals(subType)) return PEACE_TREATY_BASE;
        if ("reparations".equals(subType)) return item.getAmount();
        return CEASEFIRE_BASE;
    }

    private float getKnowledgeValue(String blueprintId) {
        // Blueprint rarity and type affect value.
        // Full evaluation deferred to game data integration.
        return 5000f;
    }

    private float getDeclarationValue(NegotiableItem item) {
        DeclarationType dt = item.getDeclarationType();
        if (dt == null) return 2000f;

        if (item.isWithdrawal()) {
            switch (dt) {
                case DENOUNCE: return WITHDRAW_DENOUNCE_BASE;
                case RIVALRY: return WITHDRAW_RIVALRY_BASE;
                // Withdrawing positive declarations = negative value (loss)
                case FRIENDSHIP: return FRIENDSHIP_BASE;
                case GUARANTEE_INDEPENDENCE: return GUARANTEE_BASE;
                default: return 2000f;
            }
        }

        switch (dt) {
            case DENOUNCE: return DENOUNCE_BASE;
            case RIVALRY: return RIVALRY_BASE;
            case FRIENDSHIP: return FRIENDSHIP_BASE;
            case GUARANTEE_INDEPENDENCE: return GUARANTEE_BASE;
            default: return 2000f;
        }
    }

    private float getConcessionValue(NegotiableItem item) {
        // Value = cost to the conceding faction of performing the concession.
        // Context-dependent; use moderate base for now.
        return 4000f;
    }

    // ── Belief multiplier ──────────────────────────────────────

    /**
     * Items touching a faction's core beliefs get multiplied values.
     * Strength 3 beliefs make items nearly untradeable (2.5x multiplier).
     */
    protected float getBeliefMultiplier(NegotiableItem item, String factionId) {
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        if (beliefs == null) return 1f;

        // Territory: check if any territorial beliefs apply to this market
        if (item.getType() == NegotiableItemType.TERRITORY) {
            FactionBeliefs.BeliefEntry strongest =
                    beliefs.getStrongestInCategory(BeliefDef.Category.TERRITORIAL);
            if (strongest != null) {
                return strongest.getImpactMultiplier();
            }
        }

        // Agreements and declarations: check political beliefs
        if (item.getType() == NegotiableItemType.AGREEMENTS
                || item.getType() == NegotiableItemType.DECLARATIONS) {
            FactionBeliefs.BeliefEntry strongest =
                    beliefs.getStrongestInCategory(BeliefDef.Category.POLITICAL);
            if (strongest != null && strongest.strength >= 2) {
                return strongest.getImpactMultiplier();
            }
        }

        // War declarations: check military beliefs
        if (item.getType() == NegotiableItemType.WAR_DECLARATION) {
            FactionBeliefs.BeliefEntry strongest =
                    beliefs.getStrongestInCategory(BeliefDef.Category.MILITARY);
            if (strongest != null) {
                return strongest.getImpactMultiplier();
            }
        }

        // Commodities/trade: check economic beliefs
        if (item.getType().isEconomic()) {
            FactionBeliefs.BeliefEntry strongest =
                    beliefs.getStrongestInCategory(BeliefDef.Category.ECONOMIC);
            if (strongest != null && strongest.strength >= 2) {
                return strongest.getImpactMultiplier();
            }
        }

        // Concessions involving ideology: check ideological beliefs
        if (item.getType() == NegotiableItemType.CONCESSIONS) {
            FactionBeliefs.BeliefEntry strongest =
                    beliefs.getStrongestInCategory(BeliefDef.Category.IDEOLOGICAL);
            if (strongest != null && strongest.strength >= 2) {
                return strongest.getImpactMultiplier();
            }
        }

        return 1f;
    }

    // ── Tendency multiplier ────────────────────────────────────

    /**
     * Faction political tendencies shift how much they value each item category.
     * Corporatists value credits higher, Militarists value territory higher, etc.
     * Returns multiplier centered on 1.0 (range ~0.7 to ~1.3).
     */
    protected float getTendencyMultiplier(NegotiableItem item, String factionId) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return 1f;

        float total = profile.getTotal();
        if (total <= 0) return 1f;

        int itemOrd = item.getType().ordinal();
        float modifier = 0f;

        for (TendencyId t : TendencyId.values()) {
            float weightFraction = profile.getWeight(t) / total;
            modifier += weightFraction * TENDENCY_MODS[t.ordinal()][itemOrd];
        }

        return 1f + modifier;
    }
}
