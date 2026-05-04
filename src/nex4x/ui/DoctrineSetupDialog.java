package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Player doctrine setup for custom player factions: tendency budget + trait picks.
 */
public class DoctrineSetupDialog implements CustomDialogDelegate {

    private static final Logger log = Global.getLogger(DoctrineSetupDialog.class);

    private static final float BUDGET = 10f;
    private static final float STEP = 0.5f;
    private static final int MAX_TRAITS = 5;
    private static final int MIN_TRAITS = 1;

    final EnumMap<TendencyId, Float> allocations = new EnumMap<TendencyId, Float>(TendencyId.class);
    final List<String> selectedTraits = new ArrayList<String>();

    public DoctrineSetupDialog() {
        float start = BUDGET / TendencyId.values().length;
        for (TendencyId t : TendencyId.values()) {
            allocations.put(t, start);
        }
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        float w = panel.getPosition().getWidth();
        float h = panel.getPosition().getHeight();
        DoctrinePanelPlugin plug = new DoctrinePanelPlugin(this);
        CustomPanelAPI inner = panel.createCustomPanel(w, h, plug);
        plug.bind(inner);
        panel.addComponent((UIComponentAPI) inner).inTL(0, 0);
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Establish Doctrine";
    }

    @Override
    public String getCancelText() {
        return null;
    }

    @Override
    public void customDialogConfirm() {
        float total = getTotal();
        if (Math.abs(total - BUDGET) > 0.01f) {
            float scale = BUDGET / Math.max(total, 0.1f);
            for (TendencyId t : TendencyId.values()) {
                allocations.put(t, roundStep(allocations.get(t) * scale));
            }
        }
        if (selectedTraits.size() < MIN_TRAITS) {
            for (DiplomacyTraits.TraitDef def : DiplomacyTraits.getTraits()) {
                if (def.noRandom) continue;
                selectedTraits.add(def.id);
                if (selectedTraits.size() >= MIN_TRAITS) break;
            }
        }

        TendencyProfile profile = new TendencyProfile(new EnumMap<TendencyId, Float>(allocations));
        Nex4xManager mgr = Nex4xManager.getOrCreateManager();
        mgr.setPlayerDoctrine(profile);
        mgr.setPlayerDoctrineTraitIds(new ArrayList<String>(selectedTraits));
        try {
            TendencyProfileLoader.registerProfile(Global.getSector().getPlayerFaction().getId(), profile);
        } catch (Exception e) {
            log.warn("[Nex4x] registerProfile(player): " + e.getMessage());
        }
        log.info("[Nex4x] Player doctrine established (" + selectedTraits.size() + " traits)");
    }

    @Override
    public void customDialogCancel() {
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }

    float getTotal() {
        float sum = 0f;
        for (float v : allocations.values()) sum += v;
        return sum;
    }

    static float roundStep(float v) {
        return Math.round(v / STEP) * STEP;
    }

    private static final class DoctrinePanelPlugin extends BaseCustomUIPanelPlugin {
        private final DoctrineSetupDialog dlg;
        private CustomPanelAPI panel;
        private TooltipMakerAPI ui;

        DoctrinePanelPlugin(DoctrineSetupDialog dlg) {
            this.dlg = dlg;
        }

        void bind(CustomPanelAPI p) {
            this.panel = p;
            rebuild();
        }

        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!(buttonId instanceof String)) return;
            String id = (String) buttonId;
            if (id.startsWith("T+_")) {
                TendencyId t = TendencyId.valueOf(id.substring(3));
                float v = dlg.allocations.get(t);
                if (dlg.getTotal() >= BUDGET - 0.01f) return;
                dlg.allocations.put(t, DoctrineSetupDialog.roundStep(v + STEP));
            } else if (id.startsWith("T-_")) {
                TendencyId t = TendencyId.valueOf(id.substring(3));
                float v = dlg.allocations.get(t);
                dlg.allocations.put(t, Math.max(0f, DoctrineSetupDialog.roundStep(v - STEP)));
            } else if (id.startsWith("TR_")) {
                String traitId = id.substring(3);
                if (dlg.selectedTraits.contains(traitId)) {
                    dlg.selectedTraits.remove(traitId);
                } else if (dlg.selectedTraits.size() < MAX_TRAITS) {
                    dlg.selectedTraits.add(traitId);
                }
            }
            rebuild();
        }

        private void rebuild() {
            if (panel == null) return;
            if (ui != null) {
                panel.removeComponent((UIComponentAPI) ui);
                ui = null;
            }
            float w = Math.max(200f, panel.getPosition().getWidth() - 10f);
            float h = Math.max(300f, panel.getPosition().getHeight() - 10f);
            ui = panel.createUIElement(w, h, true);

            ui.addSectionHeading("POLITICAL TENDENCIES (total 10)", Alignment.MID, 8f);
            for (TendencyId t : TendencyId.values()) {
                float val = dlg.allocations.get(t);
                LabelAPI row = ui.addPara(String.format("%s  %.1f", t.displayName, val), t.color, 6f);
                row.setHighlight(t.displayName);
                row.setHighlightColor(t.color);
                ui.addButton("-", "T-_" + t.name(), 36, 20, 2f);
                ui.addButton("+", "T+_" + t.name(), 36, 20, 2f);
                ui.addPara("  " + t.description, Misc.getGrayColor(), 4f);
            }
            float remaining = BUDGET - dlg.getTotal();
            Color rc = remaining < -0.01f ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
            LabelAPI rem = ui.addPara("Remaining: " + String.format("%.1f", remaining), 10f);
            rem.setHighlight(String.format("%.1f", remaining));
            rem.setHighlightColor(rc);

            ui.addSectionHeading("DIPLOMACY TRAITS (" + dlg.selectedTraits.size() + "/" + MAX_TRAITS + ")",
                    Alignment.MID, 12f);
            for (DiplomacyTraits.TraitDef def : DiplomacyTraits.getTraits()) {
                if (def.noRandom) continue;
                boolean on = dlg.selectedTraits.contains(def.id);
                ui.addButton((on ? "[x] " : "[ ] ") + def.name, "TR_" + def.id,
                        420f, 18f, 3f);
                ui.addPara("   " + def.desc, Misc.getGrayColor(), 2f);
            }

            panel.addUIElement(ui).inTL(5, 5);
            panel.updateUIElementSizeAndMakeItProcessInput(ui);
        }
    }
}
