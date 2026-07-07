package nex4x.negotiation;

import nex4x.leaders.LeaderProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Catalog of negotiable item ids. Builds {@link NegotiableItem} instances and exposes the
 * subset addable in current diplomatic state. Item categories align with NegotiableItem
 * factory methods; specific commodities/blueprints are looked up in the engine.
 */
public class NegotiableItemCatalog {

    public static final String ID_CREDITS         = "credits";
    public static final String ID_TRIBUTE         = "tribute";
    public static final String ID_CEASEFIRE       = "ceasefire";
    public static final String ID_PEACE_TREATY    = "peace_treaty";
    public static final String ID_WAR_REPARATIONS = "war_reparations";
    public static final String ID_INTEL_BASIC     = "intel_basic";
    public static final String ID_INTEL_DEEP      = "intel_deep";
    public static final String ID_PRISONER        = "prisoner_exchange";
    public static final String ID_KNOWLEDGE       = "knowledge_share";
    /** Prefixed ids resolved at build time. */
    public static final String PREFIX_COMMODITY  = "commodity:";   // commodity:<commodityId>
    public static final String PREFIX_AGREEMENT  = "agreement:";   // agreement:<AgreementType>
    public static final String PREFIX_DECLARATION = "declaration:";// declaration:<DeclarationType>
    public static final String PREFIX_TERRITORY  = "territory:";   // territory:<marketId>

    public NegotiableItem build(String itemId, int quantity) {
        if (itemId == null) return null;
        NegotiableItem item = null;
        if (ID_CREDITS.equals(itemId)) {
            item = NegotiableItem.credits(quantity);
        } else if (ID_TRIBUTE.equals(itemId)) {
            item = NegotiableItem.tribute(quantity, 360f);
        } else if (ID_CEASEFIRE.equals(itemId)) {
            item = NegotiableItem.ceasefire();
        } else if (ID_PEACE_TREATY.equals(itemId)) {
            item = NegotiableItem.peaceTreaty();
        } else if (ID_WAR_REPARATIONS.equals(itemId)) {
            item = NegotiableItem.warReparations(quantity);
        } else if (ID_INTEL_BASIC.equals(itemId)) {
            item = NegotiableItem.intel("basic");
        } else if (ID_INTEL_DEEP.equals(itemId)) {
            item = NegotiableItem.intel("deep");
        } else if (itemId.startsWith(PREFIX_COMMODITY)) {
            item = NegotiableItem.commodity(itemId.substring(PREFIX_COMMODITY.length()), quantity);
        } else if (itemId.startsWith(PREFIX_TERRITORY)) {
            item = NegotiableItem.territory(itemId.substring(PREFIX_TERRITORY.length()));
        } else if (itemId.startsWith(PREFIX_AGREEMENT)) {
            try {
                item = NegotiableItem.agreement(
                        nex4x.agreements.AgreementType.valueOf(
                                itemId.substring(PREFIX_AGREEMENT.length())));
            } catch (IllegalArgumentException ignore) { /* unknown agreement type */ }
        } else if (itemId.startsWith(PREFIX_DECLARATION)) {
            try {
                item = NegotiableItem.declaration(
                        nex4x.declarations.DeclarationType.valueOf(
                                itemId.substring(PREFIX_DECLARATION.length())));
            } catch (IllegalArgumentException ignore) { /* unknown declaration type */ }
        } else if (ID_KNOWLEDGE.equals(itemId)) {
            item = NegotiableItem.knowledge("generic");
        } else if (ID_PRISONER.equals(itemId)) {
            item = NegotiableItem.prisoner("exchange");
        }
        if (item != null) item.setId(itemId);
        return item;
    }

    public List<String> getProposerAddableIds(DealProposal deal) {
        // Permissive default; concrete UIs filter further by faction state.
        return new ArrayList<String>(Arrays.asList(
                ID_CREDITS, ID_TRIBUTE, ID_CEASEFIRE, ID_PEACE_TREATY,
                ID_WAR_REPARATIONS, ID_INTEL_BASIC, ID_PRISONER));
    }

    public int suggestedQty(String itemId, int targetCredits) {
        if (ID_CREDITS.equals(itemId) || ID_WAR_REPARATIONS.equals(itemId)) {
            return Math.max(1, targetCredits);
        }
        if (ID_TRIBUTE.equals(itemId)) {
            return Math.max(1000, targetCredits / 12);  // monthly tribute spread over a cycle
        }
        return 1;
    }

    public int estimatedUnitValue(String itemId, LeaderProfile leader, String proposerFactionId) {
        if (itemId == null) return 0;
        if (ID_CREDITS.equals(itemId) || ID_WAR_REPARATIONS.equals(itemId)) return 1;
        if (ID_TRIBUTE.equals(itemId)) return 12;       // 1cr/cycle ≈ 12cr value annualized
        // PRD-016 16g: align with ItemValuator/BaseValueTable to eliminate split-table divergence.
        if (ID_INTEL_BASIC.equals(itemId))  return BaseValueTable.INTEL;
        if (ID_INTEL_DEEP.equals(itemId))   return BaseValueTable.INTEL * 5;
        if (ID_PRISONER.equals(itemId))     return 3000;   // no BaseValueTable field; keep literal
        if (ID_KNOWLEDGE.equals(itemId))    return BaseValueTable.STAR_CHART * BaseValueTable.BLUEPRINT_MULT;
        if (ID_CEASEFIRE.equals(itemId))    return BaseValueTable.CEASEFIRE;
        if (ID_PEACE_TREATY.equals(itemId)) return BaseValueTable.PEACE_TREATY;
        if (itemId.startsWith(PREFIX_COMMODITY)) {
            try {
                String c = itemId.substring(PREFIX_COMMODITY.length());
                com.fs.starfarer.api.campaign.econ.CommoditySpecAPI spec =
                        com.fs.starfarer.api.Global.getSettings().getCommoditySpec(c);
                if (spec != null) return Math.max(1, (int) spec.getBasePrice());
            } catch (Throwable ignore) {}
        }
        return 0;
    }

    public List<String> getAvailableIds(boolean atWar) {
        List<String> out = new ArrayList<String>();
        out.add(ID_CREDITS);
        out.add(ID_INTEL_BASIC);
        out.add(ID_INTEL_DEEP);
        out.add(ID_KNOWLEDGE);
        out.add(ID_PRISONER);
        if (atWar) {
            out.add(ID_CEASEFIRE);
            out.add(ID_PEACE_TREATY);
            out.add(ID_WAR_REPARATIONS);
            out.add(ID_TRIBUTE);
        } else {
            // Peacetime additions: tribute is still allowed (vassalage), reparations is not.
            out.add(ID_TRIBUTE);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the catalog IDs that belong to a given {@link NegotiableItemType}.
     *
     * <p>For {@code TERRITORY} the list is populated from the live sector economy, capped at 5
     * markets owned by {@code targetFactionId}. For {@code AGREEMENTS} only alliance-track
     * {@link nex4x.agreements.AgreementType} values are included.
     *
     * @param type           the category to resolve; {@code null} returns an empty list
     * @param atWar          whether the player is currently at war with the target faction
     * @param targetFactionId faction ID used to filter territory markets; may be {@code null}
     * @return an unmodifiable list of catalog IDs for the given type
     */
    public java.util.List<String> idsForType(NegotiableItemType type, boolean atWar, String targetFactionId) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (type == null) return java.util.Collections.unmodifiableList(out);
        switch (type) {
            case CREDITS:
                out.add(ID_CREDITS);
                break;
            case TRIBUTE:
                out.add(ID_TRIBUTE);
                break;
            case COMMODITIES:
                for (String c : new String[]{"supplies", "machinery", "fuel", "food",
                        "heavy_machinery", "organs", "drugs", "hand_weapons"}) {
                    out.add(PREFIX_COMMODITY + c);
                }
                break;
            case TERRITORY:
                if (targetFactionId != null && com.fs.starfarer.api.Global.getSector() != null) {
                    int count = 0;
                    for (com.fs.starfarer.api.campaign.econ.MarketAPI m
                            : com.fs.starfarer.api.Global.getSector().getEconomy().getMarketsCopy()) {
                        if (m.isHidden()) continue;
                        if (!targetFactionId.equals(m.getFactionId())) continue;
                        out.add(PREFIX_TERRITORY + m.getId());
                        count++;
                        if (count >= 5) break;
                    }
                }
                break;
            case AGREEMENTS:
                for (nex4x.agreements.AgreementType at : nex4x.agreements.AgreementType.values()) {
                    if (at.isAllianceTrack()) {
                        out.add(PREFIX_AGREEMENT + at.name());
                    }
                }
                break;
            case PEACE_TERMS:
                out.add(ID_CEASEFIRE);
                out.add(ID_PEACE_TREATY);
                out.add(ID_WAR_REPARATIONS);
                break;
            case KNOWLEDGE:
                out.add(ID_KNOWLEDGE);
                break;
            case INTEL:
                out.add(ID_INTEL_BASIC);
                out.add(ID_INTEL_DEEP);
                break;
            case PRISONERS:
                out.add(ID_PRISONER);
                break;
            case DECLARATIONS:
                for (nex4x.declarations.DeclarationType dt : nex4x.declarations.DeclarationType.values()) {
                    out.add(PREFIX_DECLARATION + dt.name());
                }
                break;
            case WAR_DECLARATION:
            case CONTRACTS:
            case CONCESSIONS:
            default:
                break; // deferred: require additional picker context
        }
        return java.util.Collections.unmodifiableList(out);
    }

    public String getDisplayName(String itemId) {
        if (itemId == null) return "";
        if (ID_CREDITS.equals(itemId)) return "Credits";
        if (ID_TRIBUTE.equals(itemId)) return "Tribute (per cycle)";
        if (ID_CEASEFIRE.equals(itemId)) return "Ceasefire";
        if (ID_PEACE_TREATY.equals(itemId)) return "Peace Treaty";
        if (ID_WAR_REPARATIONS.equals(itemId)) return "War Reparations";
        if (ID_INTEL_BASIC.equals(itemId)) return "Intel: Basic";
        if (ID_INTEL_DEEP.equals(itemId)) return "Intel: Deep";
        if (ID_PRISONER.equals(itemId)) return "Prisoner Exchange";
        if (ID_KNOWLEDGE.equals(itemId)) return "Technology Share";
        if (itemId.startsWith(PREFIX_COMMODITY)) {
            return "Commodity: " + itemId.substring(PREFIX_COMMODITY.length());
        }
        if (itemId.startsWith(PREFIX_AGREEMENT)) {
            return "Agreement: " + itemId.substring(PREFIX_AGREEMENT.length());
        }
        if (itemId.startsWith(PREFIX_DECLARATION)) {
            return "Declaration: " + itemId.substring(PREFIX_DECLARATION.length());
        }
        if (itemId.startsWith(PREFIX_TERRITORY)) {
            return "Territory: " + itemId.substring(PREFIX_TERRITORY.length());
        }
        return itemId;
    }
}
