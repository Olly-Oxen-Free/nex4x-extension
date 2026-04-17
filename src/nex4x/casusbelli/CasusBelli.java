package nex4x.casusbelli;

import com.fs.starfarer.api.Global;

import java.io.Serializable;

/**
 * A specific Casus Belli instance — one faction's justification for war against another.
 *
 * Event-based CBs (Treaty Violation, Retaliation, Coalition War) are created and stored
 * with a fixed expiry. Conditional CBs (Territorial, Ideological, Denouncement, Containment,
 * Defense of Ally) are generated on the fly by CasusBelliManager and not stored persistently.
 */
public class CasusBelli implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String holderFactionId;
    private final String targetFactionId;
    private final CasusBelliType type;
    private final float creationDay;
    private final float expiryDay;  // -1 = conditional (managed externally)
    private final String details;   // e.g., "Broke NAP", "Raided Jangala"
    private boolean active;

    public CasusBelli(String holderFactionId, String targetFactionId,
                       CasusBelliType type, String details) {
        this.holderFactionId = holderFactionId;
        this.targetFactionId = targetFactionId;
        this.type = type;
        this.creationDay = getCurrentDay();
        this.expiryDay = type.validityDays > 0
                ? this.creationDay + type.validityDays
                : -1;
        this.details = details;
        this.active = true;
    }

    public String getHolderFactionId() { return holderFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public CasusBelliType getType() { return type; }
    public float getCreationDay() { return creationDay; }
    public String getDetails() { return details; }

    public float getDaysRemaining() {
        if (expiryDay < 0) return -1;
        return expiryDay - getCurrentDay();
    }

    public boolean isExpired() {
        return expiryDay > 0 && getCurrentDay() >= expiryDay;
    }

    public boolean isActive() { return active && !isExpired(); }

    public void invalidate() { this.active = false; }

    /** Display string for UI. */
    public String getDisplayText() {
        String text = type.displayName;
        if (details != null && !details.isEmpty()) {
            text += ": " + details;
        }
        float remaining = getDaysRemaining();
        if (remaining > 0) {
            text += " (" + Math.round(remaining) + " days)";
        } else if (remaining < 0) {
            text += " (active)";
        }
        return text;
    }

    private static float getCurrentDay() {
        return Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getMonth() - 1) * 30f
                + (Global.getSector().getClock().getCycle() - 206) * 365f;
    }
}
