package nex4x.ui;

import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import exerelin.campaign.diplomacy.DiplomacyTraits;

import java.util.List;

/**
 * The "Politics" tab content. Shows the player faction's internal politics:
 * tendency bars, traits, and (in v1+) recent votes.
 *
 * For v0: static display of the player's own faction politics.
 */
public class PoliticsTabPlugin extends CommandUIPlugin {

    public PoliticsTabPlugin(float width, float height) {
        super(width, height);
    }

    @Override
    public void createUI() {
        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();

        TooltipMakerAPI info = mainPanel.createUIElement(width - 20, height - 20, true);

        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        String factionId = playerFaction.getId();

        // Header
        info.addSectionHeading("INTERNAL POLITICS — " + playerFaction.getDisplayName().toUpperCase(),
                playerFaction.getBaseUIColor(), playerFaction.getDarkUIColor(), Alignment.MID, 10f);

        // Tendency bars
        info.addSectionHeading("POLITICAL TENDENCIES", Alignment.MID, 15f);

        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);

        for (TendencyId t : TendencyId.values()) {
            float weight = profile.getWeight(t);

            int barFilled = Math.round(weight * 3);
            int barEmpty = 30 - barFilled;
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barFilled; i++) bar.append("\u2588");
            for (int i = 0; i < barEmpty; i++) bar.append("\u2591");

            String weightStr = String.format("%.1f", weight);
            String text = t.displayName + "  " + bar.toString() + "  " + weightStr;

            LabelAPI label = info.addPara(text, 2f);
            label.setHighlight(t.displayName, weightStr);
            label.setHighlightColors(t.color, Misc.getHighlightColor());
        }

        info.addPara("Total: " + String.format("%.1f", profile.getTotal()) + " / 10.0",
                Misc.getGrayColor(), 10f);

        // Traits section
        info.addSectionHeading("FACTION TRAITS", Alignment.MID, 15f);

        List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
        if (traits.isEmpty()) {
            info.addPara("No diplomacy traits.", 5f);
        } else {
            for (String traitId : traits) {
                DiplomacyTraits.TraitDef def = DiplomacyTraits.getTrait(traitId);
                if (def == null) continue;

                LabelAPI label = info.addPara("[" + def.name + "] " + def.desc, 3f);
                label.setHighlight(def.name);
                label.setHighlightColor(def.color);
            }
        }

        // Placeholder for future sections
        info.addSectionHeading("RECENT VOTES", Alignment.MID, 15f);
        info.addPara("No internal votes recorded yet. (Voting system coming in v1)",
                Misc.getGrayColor(), 5f);

        mainPanel.addUIElement(info).inTL(10, 10);
    }
}
