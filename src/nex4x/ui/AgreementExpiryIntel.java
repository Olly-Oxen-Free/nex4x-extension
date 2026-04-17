package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.agreements.Agreement;
import nex4x.agreements.AgreementType;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.Set;

/**
 * Transient intel notification: "[Agreement] with [Faction] expires in N days."
 * Fired 15 days before expiry by AgreementManager. Auto-ends when the agreement
 * is renewed, cancelled, or expires.
 */
public class AgreementExpiryIntel extends BaseIntelPlugin {

    private static final Logger log = Global.getLogger(AgreementExpiryIntel.class);

    private static final String BUTTON_RENEW = "nex4x_expiry_renew";

    private final String playerFactionId;
    private final String otherFactionId;
    private final AgreementType type;
    private boolean ended;

    public AgreementExpiryIntel(String playerFactionId, String otherFactionId,
                                 AgreementType type) {
        this.playerFactionId = playerFactionId;
        this.otherFactionId = otherFactionId;
        this.type = type;
        this.ended = false;
    }

    public String getOtherFactionId() { return otherFactionId; }
    public AgreementType getAgreementType() { return type; }

    @Override
    public boolean hasSmallDescription() { return true; }
    @Override
    public boolean hasLargeDescription() { return false; }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float opad) {
        FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
        FactionAPI playerFaction = Global.getSector().getFaction(playerFactionId);
        Color factionColor = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getTextColor();
        String factionName = otherFaction != null ? otherFaction.getDisplayName() : otherFactionId;

        info.addImages(width, 48, opad, opad,
                playerFaction.getLogo(), otherFaction != null ? otherFaction.getLogo() : null);

        // Find the live agreement to get current remaining days
        Agreement agreement = findAgreement();
        if (agreement == null || !agreement.isActive()) {
            info.addPara("This agreement is no longer active.", opad);
            ended = true;
            return;
        }

        float remaining = agreement.getDaysRemaining();
        String daysStr = Math.round(remaining) + "";

        info.addPara("Your " + type.displayName + " with " + factionName
                        + " expires in " + daysStr + " days.",
                opad, new Color[]{type.color, factionColor, Misc.getNegativeHighlightColor()},
                type.displayName, factionName, daysStr + " days");

        info.addPara("Renew to maintain the agreement, or let it lapse.",
                Misc.getGrayColor(), 5f);

        // Renew button
        Color base = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getBasePlayerColor();
        Color dark = otherFaction != null ? otherFaction.getDarkUIColor() : Misc.getDarkPlayerColor();
        info.addButton("Renew", BUTTON_RENEW, base, dark,
                (int) width, 20f, opad);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_RENEW.equals(buttonId)) {
            Agreement agreement = findAgreement();
            if (agreement != null && agreement.isActive()) {
                agreement.renew();
                FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
                Global.getSector().getCampaignUI().addMessage(
                        type.displayName + " with " + otherFaction.getDisplayName() + " renewed.",
                        Misc.getPositiveHighlightColor());
                Global.getSoundPlayer().playUISound("ui_rep_raise", 1, 1);
                log.info("[Nex4x] Renewed " + type.displayName + " with " + otherFactionId
                        + " via expiry warning");
            }
            ended = true;
            ui.updateUIForItem(this);
        }
    }

    private Agreement findAgreement() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return null;

        for (Agreement a : mgr.getAgreementManager().getAgreementsOfType(playerFactionId, type)) {
            if (a.involves(otherFactionId) && a.isActive()) {
                return a;
            }
        }
        return null;
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Override
    public boolean isEnding() {
        if (ended) return true;
        // Also end if agreement no longer exists or was renewed
        Agreement agreement = findAgreement();
        if (agreement == null || !agreement.isActive()) return true;
        // End if renewed (no longer expiring soon)
        return agreement.getDaysRemaining() > 20;
    }

    @Override
    public boolean isEnded() {
        return isEnding();
    }

    // ── Intel metadata ────────────────────────────────────────

    @Override
    public String getIcon() {
        FactionAPI faction = Global.getSector().getFaction(otherFactionId);
        return faction != null ? faction.getCrest() : null;
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
        FactionAPI faction = Global.getSector().getFaction(otherFactionId);
        String fName = faction != null ? faction.getDisplayName() : otherFactionId;
        return type.displayName + " with " + fName + " — Expiring";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public String getSortString() {
        return "Agreement Expiry";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI faction = Global.getSector().getFaction(otherFactionId);
        return faction != null ? faction : Global.getSector().getPlayerFaction();
    }
}
