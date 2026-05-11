package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.util.Set;

/**
 * Standalone "Your Agreements" intel item (spec SS5.2.5).
 * Always present in the intel tab. Shows all active player agreements
 * grouped by urgency: Expiring Soon (&lt;30d), Active, Permanent.
 * Each row has [Renew] and [Cancel] buttons.
 */
public class AgreementManagerIntel extends BaseIntelPlugin {

    @Override
    public boolean hasSmallDescription() { return false; }
    @Override
    public boolean hasLargeDescription() { return true; }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f;

        TooltipMakerAPI info = panel.createUIElement(width, height, true);

        info.addSectionHeading("YOUR AGREEMENTS",
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, opad);

        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        AgreementIntelEmbed.render(info, width, opad, playerFactionId, null);

        panel.addUIElement(info).inTL(0, 0);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        AgreementIntelEmbed.handleButton(buttonId, ui, this);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return AgreementIntelEmbed.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        AgreementIntelEmbed.createConfirmationPrompt(buttonId, prompt);
    }

    @Override
    public String getIcon() {
        return Global.getSector().getPlayerFaction().getCrest();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_AGREEMENTS);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        return tags;
    }

    @Override
    public String getName() {
        return "Your Agreements";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public String getSortString() {
        return "Agreements";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getPlayerFaction();
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    /** Always present — never ends. */
    @Override
    public boolean isEnding() {
        return false;
    }

    @Override
    public boolean isEnded() {
        return false;
    }
}
