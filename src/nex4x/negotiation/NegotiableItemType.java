package nex4x.negotiation;

import nex4x.agreements.AgreementType;

/**
 * The 12 categories of items that can appear on the negotiation table.
 * State gating controls which categories are available based on diplomatic state.
 */
public enum NegotiableItemType {
    CREDITS("Credits", "Lump sum payment", 0),
    TRIBUTE("Tribute", "Credits per cycle (timed)", 0),
    COMMODITIES("Commodities", "Trade goods at market value", 0),
    TERRITORY("Territory", "Markets and worlds", 1),
    AGREEMENTS("Agreements", "Diplomatic agreements", 0),
    WAR_DECLARATION("War Declaration", "Declare war on a faction", 0),
    PEACE_TERMS("Peace Terms", "Ceasefire, peace treaty, reparations", -1),
    KNOWLEDGE("Knowledge", "Blueprints and tech sharing", 1),
    INTEL("Intel", "Intelligence and map data", 0),
    CONTRACTS("Contracts", "Mercenary, security, arms contracts", 1),
    CONCESSIONS("Concessions", "Third-party diplomatic actions", 0),
    PRISONERS("Prisoners", "Captured officers and administrators", 0),
    DECLARATIONS("Declarations", "Public diplomatic declarations", 0);

    public final String displayName;
    public final String description;
    /**
     * Minimum alliance tier to access this category during peacetime.
     * -1 = war-only (PEACE_TERMS). 0 = Cold War and above. 1 = NAP and above.
     */
    public final int minAllianceTier;

    NegotiableItemType(String displayName, String description, int minAllianceTier) {
        this.displayName = displayName;
        this.description = description;
        this.minAllianceTier = minAllianceTier;
    }

    /**
     * Check if this item type is available given the current diplomatic state.
     * At war: only PEACE_TERMS until a ceasefire is on the table, then everything unlocks.
     * At peace: gated by alliance tier.
     */
    public boolean isAvailable(AgreementType currentTier, boolean atWar, boolean ceasefireOnTable) {
        if (atWar) {
            if (this == PEACE_TERMS) return true;
            return ceasefireOnTable;
        }
        // Not at war: peace terms unavailable
        if (this == PEACE_TERMS) return false;
        // Check tier
        if (minAllianceTier < 0) return true;
        return currentTier.tier >= minAllianceTier;
    }

    /** True if this is an economic/trade item type. */
    public boolean isEconomic() {
        return this == CREDITS || this == TRIBUTE || this == COMMODITIES
                || this == CONTRACTS;
    }

    /** True if this is a military/war item type. */
    public boolean isMilitary() {
        return this == WAR_DECLARATION || this == TERRITORY || this == CONTRACTS;
    }

    /** True if this is a diplomatic/relationship item type. */
    public boolean isDiplomatic() {
        return this == AGREEMENTS || this == PEACE_TERMS || this == CONCESSIONS
                || this == DECLARATIONS;
    }
}
