package nex4x.negotiation;

import nex4x.agreements.AgreementType;
import nex4x.declarations.DeclarationType;

import java.io.Serializable;

/**
 * A single item on the negotiation table. Carries type-specific data via named fields.
 * Created through factory methods to ensure correct field usage per type.
 */
public class NegotiableItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final NegotiableItemType type;
    private float amount;                // credits, tribute per cycle, commodity quantity, reparations
    private String targetId;             // marketId, factionId, commodityId, blueprintId, personId
    private String secondaryId;          // concession sub-type, peace term sub-type, intel sub-type
    private float durationDays;          // tribute duration, contract duration
    private AgreementType agreementType; // for AGREEMENTS items only
    private DeclarationType declarationType; // for DECLARATIONS items only
    private boolean isWithdrawal;            // true = withdraw existing declaration

    private NegotiableItem(NegotiableItemType type) {
        this.type = type;
    }

    // ── Factory methods ────────────────────────────────────────

    public static NegotiableItem credits(float amount) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.CREDITS);
        item.amount = amount;
        return item;
    }

    public static NegotiableItem tribute(float perCycle, float durationDays) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.TRIBUTE);
        item.amount = perCycle;
        item.durationDays = durationDays;
        return item;
    }

    public static NegotiableItem commodity(String commodityId, int quantity) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.COMMODITIES);
        item.targetId = commodityId;
        item.amount = quantity;
        return item;
    }

    public static NegotiableItem territory(String marketId) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.TERRITORY);
        item.targetId = marketId;
        return item;
    }

    public static NegotiableItem agreement(AgreementType type) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.AGREEMENTS);
        item.agreementType = type;
        return item;
    }

    public static NegotiableItem warDeclaration(String targetFactionId) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.WAR_DECLARATION);
        item.targetId = targetFactionId;
        return item;
    }

    public static NegotiableItem ceasefire() {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.PEACE_TERMS);
        item.secondaryId = "ceasefire";
        return item;
    }

    public static NegotiableItem peaceTreaty() {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.PEACE_TERMS);
        item.secondaryId = "peace_treaty";
        return item;
    }

    public static NegotiableItem warReparations(float amount) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.PEACE_TERMS);
        item.secondaryId = "reparations";
        item.amount = amount;
        return item;
    }

    public static NegotiableItem knowledge(String blueprintId) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.KNOWLEDGE);
        item.targetId = blueprintId;
        return item;
    }

    public static NegotiableItem intel(String intelType) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.INTEL);
        item.targetId = intelType;
        return item;
    }

    public static NegotiableItem contract(String contractType, float durationDays) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.CONTRACTS);
        item.targetId = contractType;
        item.durationDays = durationDays;
        return item;
    }

    public static NegotiableItem concession(String targetFactionId, String concessionType) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.CONCESSIONS);
        item.targetId = targetFactionId;
        item.secondaryId = concessionType;
        return item;
    }

    public static NegotiableItem prisoner(String personId) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.PRISONERS);
        item.targetId = personId;
        return item;
    }

    public static NegotiableItem declaration(DeclarationType type) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.DECLARATIONS);
        item.declarationType = type;
        item.isWithdrawal = false;
        return item;
    }

    public static NegotiableItem withdrawDeclaration(DeclarationType type) {
        NegotiableItem item = new NegotiableItem(NegotiableItemType.DECLARATIONS);
        item.declarationType = type;
        item.isWithdrawal = true;
        return item;
    }

    // ── Getters ────────────────────────────────────────────────

    public NegotiableItemType getType() { return type; }
    public float getAmount() { return amount; }
    public String getTargetId() { return targetId; }
    public String getSecondaryId() { return secondaryId; }
    public float getDurationDays() { return durationDays; }
    public AgreementType getAgreementType() { return agreementType; }
    public DeclarationType getDeclarationType() { return declarationType; }
    public boolean isWithdrawal() { return isWithdrawal; }

    public boolean isCeasefire() {
        return type == NegotiableItemType.PEACE_TERMS && "ceasefire".equals(secondaryId);
    }

    /** Short display label for UI. */
    public String getDisplayLabel() {
        switch (type) {
            case CREDITS:
                return String.format("%.0f credits", amount);
            case TRIBUTE:
                return String.format("%.0f cr/cycle (%d days)", amount, (int) durationDays);
            case COMMODITIES:
                return String.format("%d x %s", (int) amount, targetId);
            case TERRITORY:
                return targetId;
            case AGREEMENTS:
                return agreementType != null ? agreementType.displayName : "Agreement";
            case WAR_DECLARATION:
                return "Declare war on " + targetId;
            case PEACE_TERMS:
                return formatPeaceTerm();
            case KNOWLEDGE:
                return targetId;
            case INTEL:
                return "Intel: " + targetId;
            case CONTRACTS:
                return targetId + " contract";
            case CONCESSIONS:
                return secondaryId + " vs " + targetId;
            case PRISONERS:
                return "Prisoner: " + targetId;
            case DECLARATIONS:
                return formatDeclaration();
            default:
                return type.displayName;
        }
    }

    private String formatDeclaration() {
        if (declarationType == null) return "Declaration";
        String prefix = isWithdrawal ? "Withdraw " : "Declare ";
        return prefix + declarationType.displayName;
    }

    private String formatPeaceTerm() {
        if ("ceasefire".equals(secondaryId)) return "Ceasefire";
        if ("peace_treaty".equals(secondaryId)) return "Peace Treaty";
        if ("reparations".equals(secondaryId)) return String.format("Reparations (%.0f cr)", amount);
        return "Peace Terms";
    }
}
