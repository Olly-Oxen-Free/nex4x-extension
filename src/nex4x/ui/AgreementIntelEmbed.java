package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
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

/**
 * Shared "your agreements" block for {@link AgreementManagerIntel} and the Faction Browser
 * Diplomacy tab. Button IDs match the standalone intel so handlers stay compatible.
 */
public final class AgreementIntelEmbed {

    private static final Logger log = Global.getLogger(AgreementIntelEmbed.class);

    public static final String RENEW_PREFIX = "nex4x_agr_renew_";
    public static final String CANCEL_PREFIX = "nex4x_agr_cancel_";

    private AgreementIntelEmbed() {}

    /**
     * @param filterOtherFactionId if non-null, only agreements involving this faction (viewer is player)
     */
    public static void render(TooltipMakerAPI info, float width, float opad,
                              String playerFactionId, String filterOtherFactionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) {
            info.addPara("Nex4x manager not initialized.", opad);
            return;
        }

        AgreementManager agr = mgr.getAgreementManager();
        List<Agreement> all = agr.getAgreementsFor(playerFactionId);
        if (filterOtherFactionId != null) {
            List<Agreement> filtered = new ArrayList<Agreement>();
            for (Agreement a : all) {
                if (a.involves(filterOtherFactionId)) filtered.add(a);
            }
            all = filtered;
        }

        if (all.isEmpty()) {
            info.addPara("No active agreements"
                            + (filterOtherFactionId != null ? " with this faction." : ".")
                            + " Use Negotiation from the Overview tab to propose deals.",
                    Misc.getGrayColor(), opad);
            return;
        }

        Collections.sort(all, new Comparator<Agreement>() {
            public int compare(Agreement a, Agreement b) {
                float ra = a.getDaysRemaining();
                float rb = b.getDaysRemaining();
                if (ra < 0 && rb < 0) return 0;
                if (ra < 0) return 1;
                if (rb < 0) return -1;
                return Float.compare(ra, rb);
            }
        });

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
    }

    private static void addAgreementRow(TooltipMakerAPI info, Agreement agreement,
                                        String playerFactionId, float width, float opad) {
        AgreementType type = agreement.getType();
        String otherFactionId = agreement.getOtherFaction(playerFactionId);
        FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
        String factionName = otherFaction != null ? otherFaction.getDisplayName() : otherFactionId;
        Color factionColor = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getTextColor();

        float remaining = agreement.getDaysRemaining();
        String daysText = remaining < 0 ? "permanent" : Math.round(remaining) + " days";

        String tierText = "";
        if (type.isAllianceTrack() && type.tier > 0) {
            tierText = " [Tier " + type.tier + " of 4]";
        }

        String mainText = type.displayName + " w/ " + factionName + "    " + daysText + tierText;
        LabelAPI label = info.addPara(mainText, 5f);
        label.setHighlight(type.displayName, factionName, daysText);
        label.setHighlightColors(type.color, factionColor,
                remaining >= 0 && remaining <= 30 ? Misc.getNegativeHighlightColor()
                        : Misc.getHighlightColor());

        String cancelConsequence = getCancelConsequence(type);
        if (cancelConsequence != null) {
            info.addPara("  Cancel consequence: " + cancelConsequence,
                    Misc.getGrayColor(), 2f);
        }

        if (type.isAllianceTrack()) {
            AgreementType nextTier = type.getNextAllianceTier();
            if (nextTier != null) {
                float rel = Global.getSector().getFaction(playerFactionId)
                        .getRelationship(otherFactionId);
                float needed = nextTier.relationThreshold;
                if (rel >= needed) {
                    info.addPara("  Upgrade available: " + nextTier.displayName,
                            Misc.getPositiveHighlightColor(), 2f);
                } else {
                    String relStr = String.format("%.0f", needed);
                    info.addPara("  Next tier: " + nextTier.displayName
                                    + " (requires " + relStr + " relations)",
                            Misc.getGrayColor(), 2f);
                }
            }
        }

        String key = otherFactionId + "_" + type.name();
        Color base = otherFaction != null ? otherFaction.getBaseUIColor() : Misc.getBasePlayerColor();
        Color dark = otherFaction != null ? otherFaction.getDarkUIColor() : Misc.getDarkPlayerColor();

        if (remaining >= 0) {
            info.addButton("Renew", RENEW_PREFIX + key, base, dark, 72f, 20f, 3f);
        }
        info.addButton("Cancel", CANCEL_PREFIX + key, base, dark, 72f, 20f, 3f);
    }

    private static String getCancelConsequence(AgreementType type) {
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

    /** @return true if this was an agreement button and was processed */
    public static boolean handleButton(Object buttonId, IntelUIAPI ui, IntelInfoPlugin refreshTarget) {
        if (!(buttonId instanceof String)) return false;
        String id = (String) buttonId;
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;
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
                                + (otherFaction != null ? otherFaction.getDisplayName() : otherFid)
                                + " renewed.",
                        Misc.getPositiveHighlightColor());
                log.info("[Nex4x] Renewed " + agreement.getType().displayName + " with " + otherFid);
            }
            if (ui != null && refreshTarget != null) {
                ui.updateUIForItem(refreshTarget);
            }
            return true;
        }
        if (id.startsWith(CANCEL_PREFIX)) {
            String key = id.substring(CANCEL_PREFIX.length());
            Agreement agreement = findAgreementByKey(key, playerFactionId, mgr);
            if (agreement != null) {
                String otherFid = agreement.getOtherFaction(playerFactionId);
                FactionAPI otherFaction = Global.getSector().getFaction(otherFid);
                mgr.getAgreementManager().cancelWithConsequences(agreement, playerFactionId);
                Global.getSector().getCampaignUI().addMessage(
                        agreement.getType().displayName + " with "
                                + (otherFaction != null ? otherFaction.getDisplayName() : otherFid)
                                + " cancelled.",
                        Misc.getNegativeHighlightColor());
                log.info("[Nex4x] Cancelled " + agreement.getType().displayName + " with " + otherFid);
            }
            if (ui != null && refreshTarget != null) {
                ui.updateUIForItem(refreshTarget);
            }
            return true;
        }
        return false;
    }

    public static boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId instanceof String) {
            return ((String) buttonId).startsWith(CANCEL_PREFIX);
        }
        return false;
    }

    public static void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (!(buttonId instanceof String)) return;
        String id = (String) buttonId;
        if (!id.startsWith(CANCEL_PREFIX)) return;
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

    private static Agreement findAgreementByKey(String key, String playerFactionId, Nex4xManager mgr) {
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

    private static String[] splitKey(String key) {
        for (int i = key.length() - 1; i >= 0; i--) {
            if (key.charAt(i) == '_') {
                String typePart = key.substring(i + 1);
                try {
                    AgreementType.valueOf(typePart);
                    return new String[]{key.substring(0, i), typePart};
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        return null;
    }
}
