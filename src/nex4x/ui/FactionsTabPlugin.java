package nex4x.ui;

import ashlib.data.plugins.coreui.CommandUIPlugin;

/**
 * Command UI tab hosting {@link FactionBrowserPanelModel}.
 */
public class FactionsTabPlugin extends CommandUIPlugin {

    private final FactionBrowserPanelModel browser = new FactionBrowserPanelModel();

    public FactionsTabPlugin(float width, float height) {
        super(width, height);
    }

    @Override
    public void createUI() {
        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();
        browser.render(mainPanel, width, height, 10f, 10f);
    }

    @Override
    public void buttonPressed(Object buttonId) {
        browser.handleButton(buttonId, null, null, new Runnable() {
            public void run() {
                clearUI(true);
                createUI();
            }
        });
    }
}
