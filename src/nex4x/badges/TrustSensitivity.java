package nex4x.badges;

import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;

import java.util.List;

/**
 * Determines how much a faction cares about trust/badges based on their traits.
 */
public class TrustSensitivity {

    public enum Level {
        NONE(0f),       // devious, predatory, anarchist — don't care
        LOW(0.5f),      // neutralist, lowprofile
        MEDIUM(1f),     // default
        MEDIUM_HIGH(1.5f),  // diplomatic, pacifist
        HIGH(2f);       // law_and_order, helps_allies

        public final float multiplier;
        Level(float multiplier) { this.multiplier = multiplier; }
    }

    public static Level getSensitivity(String factionId) {
        List<String> traits = DiplomacyTraits.getFactionTraits(factionId);

        // Check for high sensitivity
        if (traits.contains(TraitIds.LAW_AND_ORDER) || traits.contains(TraitIds.HELPS_ALLIES)) {
            return Level.HIGH;
        }

        // Check for none
        if (traits.contains(TraitIds.DEVIOUS) || traits.contains(TraitIds.PREDATORY)
            || traits.contains(TraitIds.ANARCHIST)) {
            return Level.NONE;
        }

        // Check for medium-high
        if (traits.contains("diplomatic") || traits.contains(TraitIds.PACIFIST)) {
            return Level.MEDIUM_HIGH;
        }

        // Check for low
        if (traits.contains(TraitIds.NEUTRALIST) || traits.contains(TraitIds.LOWPROFILE)) {
            return Level.LOW;
        }

        return Level.MEDIUM;
    }
}
