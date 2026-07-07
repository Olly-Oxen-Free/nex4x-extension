package nex4x.ui;

import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import nex4x.data.Nex4xSettings;

/**
 * Ashlib tab for the faction browser on the <b>Outposts</b> (colony industry) screen.
 * {@link #getButtonToBePlacedNear()} must be a key Ashlib recognizes (see {@code Nex4xSettings#commandTabPlaceNearButton}).
 */
public class FactionsTabListener implements CommandTabListener {

    @Override
    public String getNameForTab() {
        return "Factions";
    }

    @Override
    public String getButtonToReplace() {
        return null;
    }

    @Override
    public String getButtonToBePlacedNear() {
        return Nex4xSettings.commandTabPlaceNearButton;
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltipCreatorForButton() {
        return null;
    }

    @Override
    public CommandUIPlugin createPlugin() {
        return new FactionsTabPlugin(
                Global.getSettings().getScreenWidth() - 80f,
                Global.getSettings().getScreenHeight() - 160f);
    }

    @Override
    public float getWidthOfButton() {
        return 80f;
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
        return 100;
    }

    @Override
    public boolean shouldButtonBeEnabled() {
        return true;
    }

    @Override
    public void performRefresh(ButtonAPI button) {
    }
}
