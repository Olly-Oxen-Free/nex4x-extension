package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderProfile;

import java.util.Set;

public class LeaderChangeIntel extends BaseIntelPlugin {

    private static final long serialVersionUID = 1L;

    private final String factionId;
    private final String newLeaderName;
    private final String newLeaderPortrait; // may be null
    private final String title;

    public LeaderChangeIntel(String factionId, LeaderProfile newProfile) {
        this.factionId = factionId;
        this.newLeaderName = newProfile.displayName();
        this.newLeaderPortrait = newProfile.portraitSprite();
        this.title = newProfile.titleString();
    }

    @Override public boolean hasSmallDescription() { return true; }
    @Override public boolean hasLargeDescription() { return false; }

    @Override
    public String getIcon() {
        if (newLeaderPortrait != null) return newLeaderPortrait;
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getCrest() : null;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        if (newLeaderPortrait != null) {
            info.beginImageWithText(newLeaderPortrait, 72f);
            info.addPara(newLeaderName, 4f);
            FactionAPI f = Global.getSector().getFaction(factionId);
            String fName = f != null ? f.getDisplayName() : factionId;
            info.addPara("New " + title + " of " + fName, 2f);
            info.addImageWithText(4f);
        } else {
            FactionAPI f = Global.getSector().getFaction(factionId);
            String fName = f != null ? f.getDisplayName() : factionId;
            info.addPara(newLeaderName + " — New " + title + " of " + fName, 4f);
        }
    }

    @Override
    public String getName() {
        FactionAPI f = Global.getSector().getFaction(factionId);
        String fName = f != null ? f.getDisplayName() : factionId;
        return "New " + title + ": " + newLeaderName + " (" + fName + ")";
    }

    @Override
    public String getSmallDescriptionTitle() { return getName(); }

    @Override
    public String getSortString() { return "leader_change_" + factionId; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_AGREEMENTS);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add(factionId);
        return tags;
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f : Global.getSector().getPlayerFaction();
    }

    @Override public boolean isEnding() { return false; }
    @Override public boolean isEnded() { return false; }
}
