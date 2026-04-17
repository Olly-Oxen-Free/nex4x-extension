package nex4x.badges;

import java.awt.Color;

public enum BadgeType {
    OATHBREAKER("Oathbreaker", "Broke a NAP or Defensive Pact", new Color(200, 50, 50), 360),
    WARMONGER("Warmonger", "Declared 3+ wars within 360 days", new Color(150, 30, 30), 360),
    RELIABLE_PARTNER("Reliable Partner", "Honored a Defensive Pact call-to-arms", new Color(50, 200, 50), -1),
    AGGRESSOR("Aggressor", "Attacked without valid justification", new Color(200, 100, 50), 360),
    BETRAYER("Betrayer", "Bought out a mercenary contract from under another faction", new Color(180, 50, 180), 360),
    PEACEMAKER("Peacemaker", "Negotiated 3+ peace conferences", new Color(100, 200, 255), -1);

    public final String displayName;
    public final String description;
    public final Color color;
    public final float decayDays;  // -1 = permanent until counter-action

    BadgeType(String displayName, String description, Color color, float decayDays) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.decayDays = decayDays;
    }
}
