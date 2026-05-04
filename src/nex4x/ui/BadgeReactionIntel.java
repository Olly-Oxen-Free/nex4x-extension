package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.badges.BadgeType;
import nex4x.leaders.DialogueSystem;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.ReputationTier;
import nex4x.leaders.Situation;
import nex4x.managers.Nex4xManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BadgeReactionIntel extends BaseIntelPlugin {

    private static final long serialVersionUID = 1L;

    /** Safety net — hard ceiling on intel lifetime. */
    private static final float MAX_LIFETIME_DAYS = 90f;
    private static final int VISIBILITY_PROGRESS_THRESHOLD = 1;

    private final String factionId;
    private final BadgeType badgeType;
    private final String badgeDisplayName;
    private final boolean positive;
    private final String line;
    private final long createdTimestamp;

    public BadgeReactionIntel(String factionId, BadgeType badge) {
        this.factionId = factionId;
        this.badgeType = badge;
        this.badgeDisplayName = badge.displayName;
        this.positive = isPositiveBadge(badge);
        this.createdTimestamp = Global.getSector().getClock().getTimestamp();
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(factionId);
        float rel = Global.getSector().getFaction(factionId)
                .getRelationship(Global.getSector().getPlayerFaction().getId());
        Map<String,String> ctx = new HashMap<String,String>();
        ctx.put("player", Global.getSector().getPlayerFaction().getDisplayName());
        ctx.put("leader", leader.displayName());
        ctx.put("faction", Global.getSector().getFaction(factionId).getDisplayName());
        this.line = DialogueSystem.get().resolve(leader,
                positive ? Situation.BADGE_EARNED_POSITIVE : Situation.BADGE_EARNED_NEGATIVE,
                ReputationTier.fromRelation(rel), ctx);
    }

    /** Hardcoded polarity. TODO(v5.1): faction-specific badge polarity */
    private static boolean isPositiveBadge(BadgeType badge) {
        return badge == BadgeType.PEACEMAKER || badge == BadgeType.RELIABLE_PARTNER;
    }

    @Override public boolean hasSmallDescription() { return true; }
    @Override public boolean hasLargeDescription() { return false; }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(factionId);
        String sprite = leader.portraitSprite();
        if (sprite != null) {
            info.beginImageWithText(sprite, 64f);
            info.addPara(leader.displayName(), 4f);
            info.addPara(Global.getSector().getFaction(factionId).getDisplayName(), 2f);
            info.addImageWithText(4f);
        } else {
            info.addPara(leader.displayName() + " — " +
                    Global.getSector().getFaction(factionId).getDisplayName(), 4f);
        }
        info.addPara("On \"" + badgeDisplayName + "\": \"" + line + "\"", 8f);
    }

    @Override
    public String getIcon() {
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(factionId);
        String sprite = leader.portraitSprite();
        if (sprite != null) return sprite;
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getCrest() : null;
    }

    @Override
    public String getName() {
        FactionAPI f = Global.getSector().getFaction(factionId);
        String fn = f != null ? f.getDisplayName() : factionId;
        return fn + ": " + badgeDisplayName + (positive ? "" : " (negative)");
    }

    @Override
    public String getSmallDescriptionTitle() { return getName(); }

    @Override
    public String getSortString() { return "badge_" + factionId + "_" + badgeDisplayName; }

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

    private boolean badgeBelowVisibility() {
        Nex4xManager m = Nex4xManager.getManager();
        if (m == null) return false;
        nex4x.badges.FactionBadges fb = m.getBadgeManager().getBadges(factionId);
        return fb.getProgress(badgeType) < VISIBILITY_PROGRESS_THRESHOLD
                && !fb.hasBadge(badgeType);
    }

    @Override public boolean isEnding() {
        return badgeBelowVisibility() || elapsedDays() >= MAX_LIFETIME_DAYS - 5f;
    }
    @Override public boolean isEnded() {
        return badgeBelowVisibility() || elapsedDays() >= MAX_LIFETIME_DAYS;
    }
}
