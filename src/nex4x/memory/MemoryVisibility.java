package nex4x.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;

public enum MemoryVisibility {
    MINIMAL(0),
    BASIC(1),
    STANDARD(2),
    DEEP(3),
    FULL(4);

    public final int level;

    MemoryVisibility(int level) {
        this.level = level;
    }

    public static MemoryVisibility getAccessLevel(String viewerFactionId, String targetFactionId) {
        if (viewerFactionId.equals(targetFactionId)) return FULL;

        FactionAPI viewer = Global.getSector().getFaction(viewerFactionId);
        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        if (viewer == null || target == null) return MINIMAL;

        RepLevel rep = viewer.getRelationshipLevel(target);

        if (rep.isAtBest(RepLevel.HOSTILE)) return MINIMAL;
        if (rep.isAtBest(RepLevel.SUSPICIOUS)) return MINIMAL;
        if (rep.isAtBest(RepLevel.NEUTRAL)) return BASIC;
        if (rep.isAtBest(RepLevel.FAVORABLE)) return STANDARD;
        if (rep.isAtBest(RepLevel.FRIENDLY)) return DEEP;
        return FULL;
    }

    public boolean canSeePublicBeliefs() { return level >= BASIC.level; }
    public boolean canSeeSecretBeliefs() { return level >= DEEP.level; }
    public boolean canSeeMemories() { return level >= BASIC.level; }
    public boolean canSeeMemoryDetails() { return level >= STANDARD.level; }
    public boolean canSeeTendencies() { return level >= STANDARD.level; }
    public boolean canSeeVotes() { return level >= DEEP.level; }

    /** Strategic AI tab: abstracted summary only (no numeric I/U/P). */
    public boolean canSeeStrategicBasic() { return level >= BASIC.level; }

    /** Strategic AI tab: full goal list with numeric priorities. */
    public boolean canSeeStrategicDetailed() { return level >= STANDARD.level; }

    /** Commitment ledger / archetype trend lines on Strategic tab. */
    public boolean canSeeStrategicDeep() { return level >= DEEP.level; }
}
