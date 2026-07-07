package nex4x.demands;

import java.io.Serializable;

/** Diplomatic demand from one faction to another. */
public class Demand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum DemandType {
        TRIBUTE_CREDITS,
        CEDE_MARKET,
        BREAK_ALLIANCE,
        END_WAR,
        OPEN_MARKETS,
        RELEASE_VASSAL,
        /** Instructs target to cease hostilities with a named third party. Payload = third-party faction id. */
        FORCE_NEUTRALITY
    }

    public enum DemandStatus {
        PENDING, ACCEPTED, REJECTED, EXPIRED
    }

    private final String demanderId;
    private final String targetId;
    private final DemandType type;
    private final String payload;
    private final float createdDay;
    private final float expiryDay;
    private final float pressureCost;
    private final float influenceCost;
    private DemandStatus status = DemandStatus.PENDING;

    public Demand(String demanderId, String targetId, DemandType type, String payload,
                  float createdDay, float expiryDay, float pressureCost, float influenceCost) {
        this.demanderId = demanderId;
        this.targetId = targetId;
        this.type = type;
        this.payload = payload;
        this.createdDay = createdDay;
        this.expiryDay = expiryDay;
        this.pressureCost = pressureCost;
        this.influenceCost = influenceCost;
    }

    public String getDemanderId() { return demanderId; }
    public String getTargetId() { return targetId; }
    public DemandType getType() { return type; }
    public String getPayload() { return payload; }
    public float getCreatedDay() { return createdDay; }
    public float getExpiryDay() { return expiryDay; }
    public float getPressureCost() { return pressureCost; }
    public float getInfluenceCost() { return influenceCost; }
    public DemandStatus getStatus() { return status; }
    public void setStatus(DemandStatus status) { this.status = status; }
}
