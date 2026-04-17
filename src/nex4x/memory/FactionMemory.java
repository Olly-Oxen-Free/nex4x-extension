package nex4x.memory;

import nex4x.data.MemoryTypeDef;
import nex4x.data.MemoryTypeRegistry;

import java.io.Serializable;

public class FactionMemory implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String typeId;
    private final String sourceFactionId;
    private final String targetFactionId;
    private float currentImpact;
    private final float maxImpact;
    private final float decayRatePerDay;
    private final float timestamp;
    private String details;

    public FactionMemory(String typeId, String sourceFactionId, String targetFactionId,
                         float beliefMultiplier, float traitDecayMult, float gameDays, String details) {
        this.typeId = typeId;
        this.sourceFactionId = sourceFactionId;
        this.targetFactionId = targetFactionId;
        this.timestamp = gameDays;
        this.details = details;

        MemoryTypeDef def = MemoryTypeRegistry.get(typeId);
        if (def == null) {
            this.maxImpact = 0;
            this.currentImpact = 0;
            this.decayRatePerDay = 0;
            return;
        }

        this.maxImpact = def.baseImpact * beliefMultiplier;
        this.currentImpact = maxImpact;

        float effectiveDecayDays = def.baseDecayDays * traitDecayMult;
        if (effectiveDecayDays <= 0) effectiveDecayDays = 1;
        this.decayRatePerDay = Math.abs(maxImpact) / effectiveDecayDays;
    }

    public boolean advanceDecay(float days) {
        if (currentImpact > 0) {
            currentImpact = Math.max(0, currentImpact - decayRatePerDay * days);
        } else if (currentImpact < 0) {
            currentImpact = Math.min(0, currentImpact + decayRatePerDay * days);
        }
        return Math.abs(currentImpact) < 0.01f;
    }

    public String getTypeId() { return typeId; }
    public String getSourceFactionId() { return sourceFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public float getCurrentImpact() { return currentImpact; }
    public float getMaxImpact() { return maxImpact; }
    public float getTimestamp() { return timestamp; }
    public String getDetails() { return details; }

    public float getStrengthFraction() {
        if (Math.abs(maxImpact) < 0.01f) return 0;
        return Math.abs(currentImpact) / Math.abs(maxImpact);
    }
}
