package nex4x.vassals;

/** Three tiers of vassalization, from loose to total control. */
public enum VassalTier {
    TRIBUTARY("Tributary", 0.10f, 0.8f, false, false,
            "Pays 10% income. High autonomy. Own diplomacy."),
    VASSAL("Vassal", 0.25f, 0.5f, true, false,
            "Pays 25% income. Joins overlord's wars. Limited diplomacy."),
    PUPPET("Puppet", 0.40f, 0.2f, true, true,
            "Pays 40% income. Overlord controls diplomacy. Minimal autonomy.");

    public final String displayName;
    public final float incomeShare;
    public final float autonomy;
    public final boolean joinsWars;
    public final boolean controlledDiplomacy;
    public final String description;

    VassalTier(String displayName, float incomeShare, float autonomy,
               boolean joinsWars, boolean controlledDiplomacy, String description) {
        this.displayName = displayName;
        this.incomeShare = incomeShare;
        this.autonomy = autonomy;
        this.joinsWars = joinsWars;
        this.controlledDiplomacy = controlledDiplomacy;
        this.description = description;
    }
}
