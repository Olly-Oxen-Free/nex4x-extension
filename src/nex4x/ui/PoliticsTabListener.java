package nex4x.ui;

import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

/**
 * Registers the "Politics" tab in the core UI via Ashlib's listener system.
 * Discovered by CommandTabTracker via ListenerManagerAPI.getListeners(CommandTabListener.class).
 */
public class PoliticsTabListener implements CommandTabListener {

    @Override
    public String getNameForTab() {
        return "Politics";
    }

    @Override
    public String getButtonToReplace() {
        return null;  // don't replace an existing button
    }

    @Override
    public String getButtonToBePlacedNear() {
        return "Fleet";  // place near the Fleet tab
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltipCreatorForButton() {
        return null;
    }

    @Override
    public CommandUIPlugin createPlugin() {
        return new PoliticsTabPlugin(
                Global.getSettings().getScreenWidth() - 80,
                Global.getSettings().getScreenHeight() - 160);
    }

    @Override
    public float getWidthOfButton() {
        return 80f;
    }

    @Override
    public int getKeyBind() {
        return 0;  // no keybind
    }

    @Override
    public void performRecalculations(UIComponentAPI component) {
        // no-op for v0
    }

    @Override
    public int getOrder() {
        return 100;  // after other tabs
    }

    @Override
    public boolean shouldButtonBeEnabled() {
        return true;
    }

    @Override
    public void performRefresh(ButtonAPI button) {
        // no-op for v0
    }
}
