package nex4x.declarations;

import java.awt.Color;

/**
 * Types of public diplomatic declarations (spec §2.9.4, §5.2.6).
 *
 * Unilateral declarations (no negotiation needed):
 *   DENOUNCE  — blocks negotiation, timed, generates CB
 *   RIVALRY   — blocks negotiation, permanent, stronger than denounce
 *
 * Negotiated declarations (placed on negotiation table, require acceptance):
 *   FRIENDSHIP             — mutual, +15 disposition, enables deeper deals
 *   GUARANTEE_INDEPENDENCE — one-way commitment to defend a weaker faction
 */
public enum DeclarationType {

    DENOUNCE(
            "Denounce", "Public condemnation. Blocks all negotiation until withdrawn.",
            true, true, 180, -10f,
            new Color(200, 80, 80)),

    RIVALRY(
            "Rivalry", "Permanent hostility declaration. Blocks all negotiation until withdrawn through diplomacy.",
            true, true, -1, -15f,
            new Color(180, 40, 40)),

    FRIENDSHIP(
            "Friendship", "Mutual declaration of amity. +15 disposition. Enables deeper diplomatic engagement.",
            false, false, 360, 15f,
            new Color(80, 180, 80)),

    GUARANTEE_INDEPENDENCE(
            "Guarantee Independence", "Unilateral commitment to defend this faction if attacked.",
            false, false, 360, 5f,
            new Color(80, 140, 200));

    public final String displayName;
    public final String description;
    /** True if this can be declared without the other faction's agreement. */
    public final boolean unilateral;
    /** True if this blocks all negotiation between the two factions. */
    public final boolean blocksNegotiation;
    /** Default duration in days. -1 = permanent until explicitly withdrawn. */
    public final float defaultDurationDays;
    /** Disposition modifier while active. */
    public final float dispositionModifier;
    public final Color color;

    DeclarationType(String displayName, String description,
                    boolean unilateral, boolean blocksNegotiation,
                    float defaultDurationDays, float dispositionModifier,
                    Color color) {
        this.displayName = displayName;
        this.description = description;
        this.unilateral = unilateral;
        this.blocksNegotiation = blocksNegotiation;
        this.defaultDurationDays = defaultDurationDays;
        this.dispositionModifier = dispositionModifier;
        this.color = color;
    }

    /** True if this is a hostile/negative declaration. */
    public boolean isHostile() {
        return this == DENOUNCE || this == RIVALRY;
    }

    /** True if this is a positive/cooperative declaration. */
    public boolean isPositive() {
        return this == FRIENDSHIP || this == GUARANTEE_INDEPENDENCE;
    }
}
