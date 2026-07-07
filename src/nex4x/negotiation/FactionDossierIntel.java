package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.agreements.Agreement;
import nex4x.influence.InfluenceManager;
import nex4x.managers.Nex4xManager;
import nex4x.util.FactionPowerRankings;

import java.awt.Color;
import java.util.List;
import java.util.Set;

/**
 * Intel-journal dossier produced when the player receives an INTEL item in a negotiation.
 * Reports the subject faction's real, current data: relations table, active agreements,
 * influence balance and composite power ranking. "Deep" intel additionally lists the full
 * relations table; "basic" trims it to notable (allied/hostile) relations.
 *
 * Extends BaseIntelPlugin (no abstract lifecycle) — mirrors ViceroyIntelItem.
 */
public class FactionDossierIntel extends BaseIntelPlugin {
    private static final long serialVersionUID = 1L;

    private final String subjectFactionId;
    private final String tier; // "basic" | "deep"

    public FactionDossierIntel(String subjectFactionId, String tier) {
        this.subjectFactionId = subjectFactionId;
        this.tier = tier == null ? "basic" : tier;
    }

    public String getSubjectFactionId() { return subjectFactionId; }
    public String getTier() { return tier; }

    private boolean isDeep() { return "deep".equalsIgnoreCase(tier); }

    @Override
    protected String getName() {
        FactionAPI f = Global.getSector().getFaction(subjectFactionId);
        String fn = f != null ? f.getDisplayName() : subjectFactionId;
        return "Intelligence Dossier: " + fn + (isDeep() ? " (Deep)" : "");
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        FactionAPI subject = Global.getSector().getFaction(subjectFactionId);
        if (subject == null) {
            info.addPara("Subject faction no longer exists.", Misc.getGrayColor(), 0f);
            return;
        }
        Color base = subject.getBaseUIColor();
        Color dark = subject.getDarkUIColor();
        Color hl = Misc.getHighlightColor();

        info.addPara("Acquired intelligence on " + subject.getDisplayName() + ".", 0f);

        // ── Power ranking + influence ─────────────────────────────
        info.addSectionHeading("STANDING", base, dark, Alignment.MID, 10f);
        try {
            FactionPowerRankings.rebuild();
            info.addPara("Power ranking: " + FactionPowerRankings.getRankLabel(subjectFactionId), 4f);
        } catch (Throwable t) {
            info.addPara("Power ranking: unavailable", Misc.getGrayColor(), 4f);
        }
        try {
            InfluenceManager infl = InfluenceManager.get();
            if (infl != null) {
                info.addPara("Influence treasury: " + Math.round(infl.getBalance(subjectFactionId)),
                        4f, hl, "" + Math.round(infl.getBalance(subjectFactionId)));
            }
        } catch (Throwable ignore) {}

        // ── Active agreements ─────────────────────────────────────
        info.addSectionHeading("ACTIVE AGREEMENTS", base, dark, Alignment.MID, 10f);
        Nex4xManager mgr = Nex4xManager.getManager();
        int shown = 0;
        if (mgr != null) {
            List<Agreement> ags = mgr.getAgreementManager().getAgreementsFor(subjectFactionId);
            for (Agreement a : ags) {
                String other = a.getOtherFaction(subjectFactionId);
                info.addPara("  " + a.getType().displayName + " with " + factionName(other), 3f);
                shown++;
            }
        }
        if (shown == 0) {
            info.addPara("  No active agreements.", Misc.getGrayColor(), 3f);
        }

        // ── Relations table ───────────────────────────────────────
        info.addSectionHeading("RELATIONS", base, dark, Alignment.MID, 10f);
        int rows = 0;
        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (other.isNeutralFaction()) continue;
            if (other.getId().equals(subjectFactionId)) continue;
            if (other.getId().equals("derelict") || other.getId().equals("nex_derelict")) continue;
            float rel = subject.getRelationship(other.getId());
            boolean notable = subject.isHostileTo(other) || rel >= 0.25f;
            if (!isDeep() && !notable) continue;
            int pct = nex4x.util.Nex4xRelations.toPercentInt(rel);
            Color c = rel < -0.01f ? Misc.getNegativeHighlightColor()
                    : (rel > 0.01f ? Misc.getPositiveHighlightColor() : Misc.getGrayColor());
            info.addPara("  " + other.getDisplayName() + ": " + (pct >= 0 ? "+" : "") + pct,
                    c, 2f);
            rows++;
        }
        if (rows == 0) {
            info.addPara("  No notable relations.", Misc.getGrayColor(), 2f);
        }
    }

    private static String factionName(String factionId) {
        if (factionId == null) return "?";
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getDisplayName() : factionId;
    }

    @Override
    public String getIcon() {
        FactionAPI f = Global.getSector().getFaction(subjectFactionId);
        return f != null ? f.getCrest() : super.getIcon();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add("Nex4x");
        tags.add("Diplomacy");
        return tags;
    }

    @Override
    public String getSortString() {
        return "Intelligence Dossier";
    }
}
