package nex4x.casusbelli;

import java.awt.Color;

/**
 * The 8 types of Casus Belli — justifications for war (spec §2.9.5).
 *
 * Some CBs have fixed durations (e.g., Treaty Violation = 360d).
 * Others are conditional — they exist as long as the underlying condition holds
 * (territorial belief, ideological conflict, active denouncement, power threshold).
 * Conditional CBs are re-evaluated on the daily tick rather than stored with an expiry.
 */
public enum CasusBelliType {

    TERRITORIAL_CLAIM(
            "Territorial Claim",
            "Faction holds a territorial belief for a market owned by the target.",
            -1, true,
            new Color(200, 150, 50)),

    TREATY_VIOLATION(
            "Treaty Violation",
            "Target broke a NAP, pact, or agreement.",
            360, false,
            new Color(200, 50, 50)),

    RETALIATION(
            "Retaliation",
            "Target attacked your convoy, raided your market, or conducted espionage.",
            180, false,
            new Color(200, 100, 50)),

    DEFENSE_OF_ALLY(
            "Defense of Ally",
            "Target attacked a faction you have a Defensive Pact or higher with.",
            -1, true,
            new Color(50, 150, 255)),

    IDEOLOGICAL_CONFLICT(
            "Ideological Conflict",
            "Target's public beliefs directly oppose your core ideological beliefs.",
            -1, true,
            new Color(180, 50, 180)),

    DENOUNCEMENT(
            "Denouncement",
            "You have an active Denounce declaration against the target.",
            -1, true,
            new Color(200, 80, 80)),

    COALITION_WAR(
            "Coalition War",
            "Your coalition voted to declare war on the target.",
            30, false,
            new Color(255, 200, 50)),

    CONTAINMENT(
            "Containment",
            "Target's military and economic power exceeds a threatening threshold.",
            -1, true,
            new Color(150, 100, 50));

    public final String displayName;
    public final String description;
    /**
     * Validity duration in days. -1 = conditional (persists while underlying
     * condition is true; re-evaluated on daily tick).
     */
    public final float validityDays;
    /**
     * True if this CB type is derived from game state rather than events.
     * Conditional CBs are regenerated each tick rather than stored persistently.
     */
    public final boolean conditional;
    public final Color color;

    CasusBelliType(String displayName, String description,
                   float validityDays, boolean conditional, Color color) {
        this.displayName = displayName;
        this.description = description;
        this.validityDays = validityDays;
        this.conditional = conditional;
        this.color = color;
    }
}
