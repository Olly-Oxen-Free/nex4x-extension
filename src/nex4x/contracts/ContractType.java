package nex4x.contracts;

/** Kinds of AI-auction contracts. */
public enum ContractType {
    HARASS_FACTION("Harass Faction", 14),
    BLOCKADE_MARKET("Blockade Market", 30),
    ESCORT_CONVOY("Escort Convoy", 14),
    RAID_FACTION("Raid Faction", 30),
    ASSASSINATE_OFFICIAL("Assassinate Official", 30),
    SABOTAGE_INDUSTRY("Sabotage Industry", 21);

    public final String displayName;
    public final int defaultDurationDays;

    ContractType(String displayName, int defaultDurationDays) {
        this.displayName = displayName;
        this.defaultDurationDays = defaultDurationDays;
    }
}
