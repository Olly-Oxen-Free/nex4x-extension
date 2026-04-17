package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.managers.Nex4xManager;

import java.awt.Color;
import java.util.*;

/**
 * CustomDialogDelegate for player faction doctrine creation.
 * Player allocates 10-point budget across 6 tendencies + picks 0-5 diplomacy traits.
 * Fires once at faction formation.
 *
 * v0 limitation: dialog is read-only (shows auto-derived profile from traits).
 * Interactive allocation via CustomUIPanelPlugin is a v1 polish item.
 */
public class DoctrineSetupDialog implements CustomDialogDelegate {

    private static final float BUDGET = 10f;
    private static final int MAX_TRAITS = 5;

    private final EnumMap<TendencyId, Float> allocations = new EnumMap<TendencyId, Float>(TendencyId.class);
    private final List<String> selectedTraits = new ArrayList<String>();

    public DoctrineSetupDialog() {
        for (TendencyId t : TendencyId.values()) {
            allocations.put(t, 0f);
        }
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        float width = panel.getPosition().getWidth();
        float height = panel.getPosition().getHeight();
        float pad = 10f;

        TooltipMakerAPI info = panel.createUIElement(width - 20, height - 20, true);

        info.addSectionHeading("FACTION DOCTRINE", Alignment.MID, pad);
        info.addPara("Allocate 10 points across 6 political tendencies. This defines your faction's " +
                "internal politics — which voices are loudest when decisions are made.", pad);
        info.addPara("Each tendency influences how your faction evaluates diplomatic proposals, " +
                "trade deals, and war declarations.", Misc.getGrayColor(), 3f);

        // Tendency allocation section
        info.addSectionHeading("POLITICAL TENDENCIES (10 points)", Alignment.MID, 15f);

        for (TendencyId t : TendencyId.values()) {
            float val = allocations.get(t);
            String text = String.format("%-15s  %.1f / 10", t.displayName, val);
            LabelAPI label = info.addPara(text, 3f);
            label.setHighlight(t.displayName);
            label.setHighlightColor(t.color);
            info.addPara("  " + t.description + "  (opposes: " + t.getOpposite().displayName + ")",
                    Misc.getGrayColor(), 1f);
        }

        float remaining = BUDGET - getTotal();
        Color remainColor = remaining < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        String remStr = String.format("%.1f", remaining);
        LabelAPI remLabel = info.addPara("Remaining: " + remStr + " points", 10f);
        remLabel.setHighlight(remStr);
        remLabel.setHighlightColor(remainColor);

        // Trait selection section
        info.addSectionHeading("DIPLOMACY TRAITS (pick up to " + MAX_TRAITS + ")", Alignment.MID, 15f);
        info.addPara("Traits define how other factions perceive you and how your faction handles " +
                "specific diplomatic situations.", 5f);

        List<DiplomacyTraits.TraitDef> allTraits = DiplomacyTraits.getTraits();
        for (DiplomacyTraits.TraitDef def : allTraits) {
            if (def.noRandom) continue;
            boolean selected = selectedTraits.contains(def.id);
            String prefix = selected ? "[X] " : "[ ] ";
            LabelAPI label = info.addPara(prefix + def.name + " — " + def.desc, 2f);
            label.setHighlight(def.name);
            label.setHighlightColor(selected ? Misc.getHighlightColor() : Misc.getGrayColor());
        }

        info.addPara("Selected: " + selectedTraits.size() + " / " + MAX_TRAITS,
                Misc.getGrayColor(), 10f);

        // Preview section
        info.addSectionHeading("PREVIEW", Alignment.MID, 15f);
        TendencyId dominant = getDominant();
        if (dominant != null) {
            info.addPara("Dominant tendency: " + dominant.displayName, 5f);
        }
        info.addPara("Other factions will perceive your faction based on these choices. " +
                "Diplomatic proposals, trade deals, and alliance opportunities will be " +
                "filtered through your doctrine.", Misc.getGrayColor(), 3f);

        panel.addUIElement(info).inTL(10, 10);
    }

    @Override
    public boolean hasCancelButton() { return false; }

    @Override
    public String getConfirmText() { return "Establish Doctrine"; }
    @Override
    public String getCancelText() { return null; }

    @Override
    public void customDialogConfirm() {
        // Validate: must total ~10 points
        float total = getTotal();
        if (Math.abs(total - BUDGET) > 0.5f) {
            // Normalize to 10 for v0 (interactive editing is v1)
            float scale = BUDGET / Math.max(total, 0.1f);
            for (TendencyId t : TendencyId.values()) {
                allocations.put(t, allocations.get(t) * scale);
            }
        }

        TendencyProfile profile = new TendencyProfile(allocations);
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            mgr.setPlayerDoctrine(profile);
        }
    }

    @Override
    public void customDialogCancel() {}

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() { return null; }

    private float getTotal() {
        float sum = 0;
        for (float v : allocations.values()) sum += v;
        return sum;
    }

    private TendencyId getDominant() {
        TendencyId best = null;
        float bestVal = 0;
        for (Map.Entry<TendencyId, Float> e : allocations.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** For v1 interactive UI — set a tendency allocation via buttons. */
    public void setAllocation(TendencyId tendency, float value) {
        allocations.put(tendency, Math.max(0, Math.min(BUDGET, value)));
    }

    /** For v1 interactive UI — toggle a trait selection. */
    public void toggleTrait(String traitId) {
        if (selectedTraits.contains(traitId)) {
            selectedTraits.remove(traitId);
        } else if (selectedTraits.size() < MAX_TRAITS) {
            selectedTraits.add(traitId);
        }
    }
}
