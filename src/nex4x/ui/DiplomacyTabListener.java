package nex4x.ui;

import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
/**
 * Registers the "Diplomacy" tab on the Outposts (colony industry) screen via Ashlib CommandTabTracker.
 * Placed near the same anchor button as FactionsTabListener so it sits adjacent to Factions.
 */
public class DiplomacyTabListener implements CommandTabListener {

    @Override
    public String getNameForTab() {
        return "Diplomacy";
    }

    @Override
    public String getButtonToReplace() {
        return null;
    }

    @Override
    public String getButtonToBePlacedNear() {
        return nex4x.data.Nex4xSettings.commandTabPlaceNearButton;
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltipCreatorForButton() {
        return null;
    }

    @Override
    public CommandUIPlugin createPlugin() {
        return new DiplomacyTabPlugin(
                Global.getSettings().getScreenWidth() - 80f,
                Global.getSettings().getScreenHeight() - 160f);
    }

    @Override
    public float getWidthOfButton() {
        return 90f;
    }

    @Override
    public int getKeyBind() {
        return 0;
    }

    @Override
    public void performRecalculations(UIComponentAPI component) {
    }

    @Override
    public int getOrder() {
        return 110;
    }

    @Override
    public boolean shouldButtonBeEnabled() {
        return true;
    }

    @Override
    public void performRefresh(ButtonAPI button) {
    }
}
