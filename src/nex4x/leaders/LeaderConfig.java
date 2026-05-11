package nex4x.leaders;

import java.io.Serializable;

public class LeaderConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public String diplomaticVoiceRank = "commander";
    public String viceroyTitle = "Chamberlain";
    public String leaderTitle = "Leader";
    public boolean canSellAiCores = false;
    public boolean canSellWetwork = true;
    public boolean noteOnLuddicDenial = false;
}
