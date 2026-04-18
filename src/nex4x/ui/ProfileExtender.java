package nex4x.ui;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

/**
 * Deprecated stub — superseded by FactionBrowserIntel (v4).
 * Class retained for save-deserialization compatibility. Instances are
 * removed from the intel manager by Nex4xModPlugin.removeLegacyDossierIntels()
 * on game load.
 */
@Deprecated
public class ProfileExtender extends BaseIntelPlugin {

    @SuppressWarnings("unused")
    private String factionId;

    public ProfileExtender() {}

    public ProfileExtender(String factionId) {
        this.factionId = factionId;
    }

    @Override
    public boolean isHidden() { return true; }

    @Override
    public boolean isEnded() { return true; }
}
