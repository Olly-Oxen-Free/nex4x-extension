package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;

import java.util.Set;

/**
 * Intel entry for the faction browser (dossier, relations, diplomacy agreements).
 */
public class FactionBrowserIntel extends BaseIntelPlugin {

    private final FactionBrowserPanelModel browser = new FactionBrowserPanelModel();

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        browser.render(panel, width, height, 0f, 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (AgreementIntelEmbed.handleButton(buttonId, ui, this)) return;
        browser.handleButton(buttonId, ui, this, null);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (AgreementIntelEmbed.doesButtonHaveConfirmDialog(buttonId)) return true;
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (AgreementIntelEmbed.doesButtonHaveConfirmDialog(buttonId)) {
            AgreementIntelEmbed.createConfirmationPrompt(buttonId, prompt);
        } else {
            super.createConfirmationPrompt(buttonId, prompt);
        }
    }

    @Override
    public String getName() {
        return "Faction Browser";
    }

    @Override
    public String getSortString() {
        return "AAA_FactionBrowser";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add("Factions");
        return tags;
    }

    @Override
    public String getIcon() {
        FactionAPI player = Global.getSector().getPlayerFaction();
        return player != null ? player.getCrest() : null;
    }

    @Override
    public boolean isHidden() {
        return false;
    }
}
