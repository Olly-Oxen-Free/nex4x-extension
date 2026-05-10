package nex4x.badges;

import java.awt.Color;

public enum BadgeType {
    OATHBREAKER("Oathbreaker", "Broke a NAP or Defensive Pact", new Color(200, 50, 50), 360, false),
    WARMONGER("Warmonger", "Declared 3+ wars within 360 days", new Color(150, 30, 30), 360, false),
    RELIABLE_PARTNER("Reliable Partner", "Honored a Defensive Pact call-to-arms", new Color(50, 200, 50), -1, true),
    AGGRESSOR("Aggressor", "Attacked without valid justification", new Color(200, 100, 50), 360, false),
    BETRAYER("Betrayer", "Bought out a mercenary contract from under another faction", new Color(180, 50, 180), 360, false),
    PEACEMAKER("Peacemaker", "Negotiated 3+ peace conferences", new Color(100, 200, 255), -1, true);

    public final String displayName;
    public final String description;
    public final Color color;
    public final float decayDays;  // -1 = permanent until counter-action
    /** True if earning this badge is good for the holder; false for negative-rep badges. */
    public final boolean positive;

    BadgeType(String displayName, String description, Color color, float decayDays, boolean positive) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.decayDays = decayDays;
        this.positive = positive;
    }
}
