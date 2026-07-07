package nex4x.agents.wetwork;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;

/**
 * Terminal report delivered to the player when a wetwork contract resolves.
 *
 * Three narrative outcomes:
 *   - success && !discovered : clean kill, target market destabilised, player not implicated.
 *   - !success && discovered : the job went wrong and the player was exposed (rep hit applied elsewhere).
 *   - success && discovered  : (not currently produced) reserved for future partial outcomes.
 *
 * Persisted (Serializable via BaseIntelPlugin); all fields are snapshot strings/booleans so no
 * live lookups are required at render time.
 */
public class WetworkResultIntel extends BaseIntelPlugin {

    private final String targetFactionId;
    private final String marketFactionId;
    private final String marketName;
    private final boolean success;
    private final boolean discovered;

    public WetworkResultIntel(String targetFactionId, String marketFactionId,
                              String marketName, boolean success, boolean discovered) {
        this.targetFactionId = targetFactionId;
        this.marketFactionId = marketFactionId;
        this.marketName = marketName;
        this.success = success;
        this.discovered = discovered;
    }

    @Override
    public boolean hasSmallDescription() { return true; }
    @Override
    public boolean hasLargeDescription() { return false; }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float opad) {
        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        FactionAPI player = Global.getSector().getPlayerFaction();
        String targetName = target != null ? target.getDisplayName() : targetFactionId;

        if (player != null && target != null) {
            info.addImages(width, 48, opad, opad, player.getLogo(), target.getLogo());
        }

        String where = marketName != null ? marketName : ("a holding of " + targetName);

        if (success) {
            info.addPara("Your contract against " + targetName + " has been carried out. "
                    + "The target was eliminated at " + where + "; the aftermath has thrown local "
                    + "administration into disarray.", opad,
                    new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()},
                    targetName, where);
            info.addPara("Local stability will remain depressed for some weeks.",
                    Misc.getGrayColor(), 5f);
        } else {
            info.addPara("The contract against " + targetName + " failed. The operative was "
                    + "intercepted before the job could be completed.", opad,
                    new Color[]{Misc.getNegativeHighlightColor()}, targetName);
        }

        if (discovered) {
            info.addPara("Worse: your involvement was uncovered. " + targetName
                    + " holds you responsible, and relations have soured accordingly.", opad,
                    new Color[]{Misc.getNegativeHighlightColor()}, targetName);
        } else if (success) {
            info.addPara("There is no trail leading back to you.", Misc.getGrayColor(), 5f);
        }
    }

    @Override
    public String getName() {
        if (discovered) return "Wetwork Contract — Exposed";
        return success ? "Wetwork Contract — Fulfilled" : "Wetwork Contract — Failed";
    }

    @Override
    public String getSmallDescriptionTitle() { return getName(); }

    @Override
    public String getIcon() {
        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        return target != null ? target.getCrest() : null;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Covert");
        tags.add("Nex4x");
        return tags;
    }

    @Override
    public String getSortString() { return "Wetwork"; }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        return target != null ? target : Global.getSector().getPlayerFaction();
    }
}
