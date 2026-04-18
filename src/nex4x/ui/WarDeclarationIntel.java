package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.DialogueSystem;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.ReputationTier;
import nex4x.leaders.Situation;
import nex4x.managers.Nex4xManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WarDeclarationIntel extends BaseIntelPlugin {

    private static final long serialVersionUID = 1L;

    private final String declarerFactionId;
    private final String targetFactionId;
    private final String line;

    public WarDeclarationIntel(String declarer, String target, boolean byAi) {
        this.declarerFactionId = declarer;
        this.targetFactionId = target;
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(declarer);
        float rel = Global.getSector().getFaction(declarer).getRelationship(target);
        ReputationTier tier = ReputationTier.fromRelation(rel);
        Map<String,String> ctx = new HashMap<String,String>();
        ctx.put("player", Global.getSector().getPlayerFaction().getDisplayName());
        ctx.put("leader", leader.displayName());
        ctx.put("faction", Global.getSector().getFaction(declarer).getDisplayName());
        this.line = DialogueSystem.get().resolve(leader,
                byAi ? Situation.WAR_DECLARED_BY_AI : Situation.WAR_DECLARED_BY_PLAYER, tier, ctx);
    }

    @Override public boolean hasSmallDescription() { return true; }
    @Override public boolean hasLargeDescription() { return false; }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(declarerFactionId);
        String sprite = leader.portraitSprite();
        if (sprite != null) {
            info.beginImageWithText(sprite, 72f);
            info.addPara(leader.displayName(), 4f);
            info.addPara("Faction: " + Global.getSector().getFaction(declarerFactionId).getDisplayName(), 2f);
            info.addImageWithText(4f);
        } else {
            info.addPara(leader.displayName() + " — " +
                    Global.getSector().getFaction(declarerFactionId).getDisplayName(), 4f);
        }
        info.addPara("\"" + line + "\"", 8f);
        info.addPara("Declaration of war against " +
                Global.getSector().getFaction(targetFactionId).getDisplayName(), 8f);
    }

    @Override
    public String getIcon() {
        LeaderProfile leader = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(declarerFactionId);
        String sprite = leader.portraitSprite();
        if (sprite != null) return sprite;
        FactionAPI f = Global.getSector().getFaction(declarerFactionId);
        return f != null ? f.getCrest() : null;
    }

    @Override
    public String getName() {
        FactionAPI f = Global.getSector().getFaction(declarerFactionId);
        FactionAPI t = Global.getSector().getFaction(targetFactionId);
        String fn = f != null ? f.getDisplayName() : declarerFactionId;
        String tn = t != null ? t.getDisplayName() : targetFactionId;
        return fn + " declares war on " + tn;
    }

    @Override
    public String getSmallDescriptionTitle() { return getName(); }

    @Override
    public String getSortString() { return "war_" + declarerFactionId + "_" + targetFactionId; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_AGREEMENTS);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add(declarerFactionId);
        return tags;
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI f = Global.getSector().getFaction(declarerFactionId);
        return f != null ? f : Global.getSector().getPlayerFaction();
    }

    @Override
    public boolean isEnding() { return false; }
    @Override
    public boolean isEnded() { return false; }
}
