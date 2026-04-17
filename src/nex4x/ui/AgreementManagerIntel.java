package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.agreements.Agreement;
import nex4x.agreements.AgreementManager;
import nex4x.agreements.AgreementType;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Standalone "Your Agreements" intel item (spec SS5.2.5).
 * Always present in the intel tab. Shows all active player agreements
 * grouped by urgency: Expiring Soon (<30d), Active, Permanent.
 * Each row has [Renew] and [Cancel] buttons.
 */
public class AgreementManagerIntel extends BaseIntelPlugin {

    private static final Logger log = Global.getLogger(AgreementManagerIntel.class);

    private static final String RENEW_PREFIX = "nex4x_agr_renew_";
    private static final String CANCEL_PREFIX = "nex4x_agr_cancel_";
    private static final String UPGRADE_PREFIX = "nex4x_agr_upgrade_";

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

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) {
            info.addPara("Nex4x manager not initialized.", opad);
            panel.addUIElement(info).inTL(0, 0);
            return;
        }

        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        AgreementManager agr = mgr.getAgreementManager();
        List<Agreement> all = agr.getAgreementsFor(playerFactionId);

        if (all.isEmpty()) {
            info.addPara("No active agreements. Use the Negotiation Table to propose deals.",
                    Misc.getGrayColor(), opad);
            panel.addUIElement(info).inTL(0, 0);
            return;
        }

        // Sort by days remaining (soonest first; permanent = last)
        Collections.sort(all, new Comparator<Agreement>() {
            public int compare(Agreement a, Agreement b) {
                float ra = a.getDaysRemaining();
                float rb = b.getDaysRemaining();
                // Permanent (-1) sorts to end
                if (ra < 0 && rb < 0) return 0;
                if (ra < 0) return 1;
                if (rb < 0) return -1;
                return Float.compare(ra, rb);
            }
        });

        // Partition into groups
        List<Agreement> expiringSoon = new ArrayList<Agreement>();
        List<Agreement> active = new ArrayList<Agreement>();
        List<Agreement> permanent = new ArrayList<Agreement>();

        for (Agreement a : all) {
            float rem = a.getDaysRemaining();
            if (rem < 0) {
                permanent.add(a);
            } else if (rem <= 30) {
                expiringSoon.add(a);
            } else {
                active.add(a);
            }
        }

        if (!expiringSoon.isEmpty()) {
            info.addSectionHeading("EXPIRING SOON",
                    Misc.getNegativeHighlightColor(), new Color(80, 30, 30),
                    Alignment.LMID, opad);
            for (Agreement a : expiringSoon) {
                addAgreementRow(info, a, playerFactionId, width, opad);
            }
        }

        if (!active.isEmpty()) {
            info.addSectionHeading("ACTIVE",
                    Misc.getHighlightColor(), new Color(60, 60, 30),
                    Alignment.LMID, opad);
            for (Agreement a : active) {
                addAgreementRow(info, a, playerFactionId, width, opad);
            }
        }

        if (!permanent.isEmpty()) {
            info.addSectionHeading("PERMANENT",
                    Misc.getPositiveHighlightColor(), new Color(30, 60, 30),
                    Alignment.LMID, opad);
            for (Agreement a : permanent) {
                addAgreementRow(info, a, playerFactionId, width, opad);
            }
        }

        panel.addUIElement(info).inTL(0, 0);
    }

    private void addAgreementRow(TooltipMakerAPI info, Agreement agreement,
                                  String playerFactionId, float width, float opad) {
        AgreementType type = agreement.getType();
        String otherFactionId = agreement.getOtherFaction(playerFactionId);
        FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
        String factionName = otherFaction != null ? otherFaction.getDisplayName() : otherFactionId;
        Color factionColor = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getTextColor();

        // Build display text
        String daysText;
        float remaining = agreement.getDaysRemaining();
        if (remaining < 0) {
            daysText = "permanent";
        } else {
            daysText = Math.round(remaining) + " days";
        }

        String tierText = "";
        if (type.isAllianceTrack() && type.tier > 0) {
            tierText = " [Tier " + type.tier + " of 4]";
        }

        // Main line: "Defensive Pact w/ Persean League    47 days  [Tier 2 of 4]"
        String mainText = type.displayName + " w/ " + factionName + "    " + daysText + tierText;
        LabelAPI label = info.addPara(mainText, 5f);
        label.setHighlight(type.displayName, factionName, daysText);
        label.setHighlightColors(type.color, factionColor,
                remaining >= 0 && remaining <= 30 ? Misc.getNegativeHighlightColor()
                        : Misc.getHighlightColor());

        // Cancel consequence preview
        String cancelConsequence = getCancelConsequence(type);
        if (cancelConsequence != null) {
            info.addPara("  Cancel consequence: " + cancelConsequence,
                    Misc.getGrayColor(), 2f);
        }

        // Upgrade hint
        if (type.isAllianceTrack()) {
            AgreementType nextTier = type.getNextAllianceTier();
            if (nextTier != null) {
                float rel = Global.getSector().getFaction(playerFactionId)
                        .getRelationship(otherFactionId);
                float needed = nextTier.relationThreshold / 100f;
                if (rel >= needed) {
                    info.addPara("  Upgrade available: " + nextTier.displayName,
                            Misc.getPositiveHighlightColor(), 2f);
                } else {
                    String relStr = String.format("%.0f", needed * 100);
                    info.addPara("  Next tier: " + nextTier.displayName
                                    + " (requires " + relStr + " relations)",
                            Misc.getGrayColor(), 2f);
                }
            }
        }

        // Buttons — use unique IDs keyed by faction+type
        String key = otherFactionId + "_" + type.name();
        Color base = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getBasePlayerColor();
        Color dark = otherFaction != null ? otherFaction.getDarkUIColor() : Misc.getDarkPlayerColor();

        if (remaining >= 0) {
            // Renew button
            addGenericButton(info, 3f, "Renew", RENEW_PREFIX + key);
        }
        // Cancel button
        addGenericButton(info, 3f, "Cancel", CANCEL_PREFIX + key);
    }

    private String getCancelConsequence(AgreementType type) {
        switch (type) {
            case NAP:
                return "-15 disposition, Oathbreaker badge risk";
            case DEFENSIVE_PACT:
                return "-20 disposition, Oathbreaker badge, Unreliable memory";
            case MILITARY_PARTNERSHIP:
            case ECONOMIC_PARTNERSHIP:
                return "-25 disposition, Oathbreaker badge, severe trust damage";
            case COALITION:
                return "-30 disposition, Oathbreaker badge, coalition-wide penalties";
            case TRADE_AGREEMENT:
                return "-8 disposition, Trade Betrayal memory";
            default:
                return null;
        }
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!(buttonId instanceof String)) return;
        String id = (String) buttonId;

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;
        String playerFactionId = Global.getSector().getPlayerFaction().getId();

        if (id.startsWith(RENEW_PREFIX)) {
            String key = id.substring(RENEW_PREFIX.length());
            Agreement agreement = findAgreementByKey(key, playerFactionId, mgr);
            if (agreement != null) {
                agreement.renew();
                String otherFid = agreement.getOtherFaction(playerFactionId);
                FactionAPI otherFaction = Global.getSector().getFaction(otherFid);
                Global.getSector().getCampaignUI().addMessage(
                        agreement.getType().displayName + " with "
                                + otherFaction.getDisplayName() + " renewed.",
                        Misc.getPositiveHighlightColor());
                log.info("[Nex4x] Renewed " + agreement.getType().displayName
                        + " with " + otherFid);
            }
        } else if (id.startsWith(CANCEL_PREFIX)) {
            String key = id.substring(CANCEL_PREFIX.length());
            Agreement agreement = findAgreementByKey(key, playerFactionId, mgr);
            if (agreement != null) {
                String otherFid = agreement.getOtherFaction(playerFactionId);
                FactionAPI otherFaction = Global.getSector().getFaction(otherFid);
                mgr.getAgreementManager().cancelWithConsequences(agreement, playerFactionId);
                Global.getSector().getCampaignUI().addMessage(
                        agreement.getType().displayName + " with "
                                + otherFaction.getDisplayName() + " cancelled.",
                        Misc.getNegativeHighlightColor());
                log.info("[Nex4x] Cancelled " + agreement.getType().displayName
                        + " with " + otherFid);
            }
        }

        ui.updateUIForItem(this);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;
            // Cancel requires confirmation
            return id.startsWith(CANCEL_PREFIX);
        }
        return false;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;
            if (id.startsWith(CANCEL_PREFIX)) {
                String key = id.substring(CANCEL_PREFIX.length());
                String[] parts = splitKey(key);
                if (parts != null) {
                    try {
                        AgreementType type = AgreementType.valueOf(parts[1]);
                        String consequence = getCancelConsequence(type);
                        prompt.addPara("Cancel this agreement?", 0f);
                        if (consequence != null) {
                            prompt.addPara("Consequence: " + consequence,
                                    Misc.getNegativeHighlightColor(), 5f);
                        }
                    } catch (IllegalArgumentException e) {
                        prompt.addPara("Cancel this agreement?", 0f);
                    }
                }
            }
        }
    }

    /**
     * Find agreement by encoded key "factionId_AGREEMENT_TYPE".
     */
    private Agreement findAgreementByKey(String key, String playerFactionId,
                                          Nex4xManager mgr) {
        String[] parts = splitKey(key);
        if (parts == null) return null;

        String otherFactionId = parts[0];
        AgreementType type;
        try {
            type = AgreementType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        List<Agreement> agreements = mgr.getAgreementManager()
                .getAgreementsOfType(playerFactionId, type);
        for (Agreement a : agreements) {
            if (a.involves(otherFactionId)) return a;
        }
        return null;
    }

    /**
     * Split "factionId_AGREEMENT_TYPE" into [factionId, typeName].
     * Handles faction IDs containing underscores by finding the last
     * segment that matches an AgreementType.
     */
    private String[] splitKey(String key) {
        // Try each underscore position from the right
        for (int i = key.length() - 1; i >= 0; i--) {
            if (key.charAt(i) == '_') {
                String typePart = key.substring(i + 1);
                try {
                    AgreementType.valueOf(typePart);
                    return new String[]{key.substring(0, i), typePart};
                } catch (IllegalArgumentException e) {
                    // Not a valid type, try next underscore
                }
            }
        }
        return null;
    }

    // ── Intel metadata ────────────────────────────────────────

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
