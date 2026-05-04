package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderProfile;
import nex4x.managers.Nex4xManager;

import java.util.Set;

public class LeaderChangeIntel extends BaseIntelPlugin {

    private static final long serialVersionUID = 1L;

    /** Safety net until Phase 1 replaces this with "end when the next leader swap happens". */
    private static final float MAX_LIFETIME_DAYS = 90f;

    private final String factionId;
    private final String newLeaderName;
    private final String newLeaderPortrait; // may be null
    private final String title;
    private final long createdTimestamp;
    /** Leader person id when this intel was created; intel ends when the seat changes again. */
    private final String anchorLeaderPersonId;

    public LeaderChangeIntel(String factionId, LeaderProfile newProfile) {
        this.factionId = factionId;
        this.newLeaderName = newProfile.displayName();
        this.newLeaderPortrait = newProfile.portraitSprite();
        this.title = newProfile.titleString();
        this.createdTimestamp = Global.getSector().getClock().getTimestamp();
        this.anchorLeaderPersonId = newProfile.getPersonApiId();
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

    private float elapsedDays() {
        return Global.getSector().getClock().getElapsedDaysSince(createdTimestamp);
    }

    private boolean leaderSeatChangedAgain() {
        String now = Nex4xManager.getOrCreateManager().getLeaderRegistry()
                .getProfile(factionId).getPersonApiId();
        if (anchorLeaderPersonId == null) return now != null;
        return now == null || !anchorLeaderPersonId.equals(now);
    }

    @Override public boolean isEnding() {
        return leaderSeatChangedAgain() || elapsedDays() >= MAX_LIFETIME_DAYS - 5f;
    }
    @Override public boolean isEnded() {
        return leaderSeatChangedAgain() || elapsedDays() >= MAX_LIFETIME_DAYS;
    }
}
