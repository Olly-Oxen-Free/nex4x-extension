package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.peace.PeaceConference;
import nex4x.peace.PeaceTerms;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class PeaceConferenceDialog extends BasePopUpDialog {
    private static final Logger log = Global.getLogger(PeaceConferenceDialog.class);
    private static PeaceConferenceDialog activeInstance;

    private static final String BTN_ACCEPT          = "pc_accept";
    private static final String BTN_REJECT          = "pc_reject";
    private static final String BTN_COUNTER         = "pc_counter";
    private static final String BTN_COUNTER_SUBMIT  = "pc_counter_submit";
    private static final String BTN_COUNTER_CANCEL  = "pc_counter_cancel";
    private static final String BTN_RM_PREFIX       = "pc_rm_";

    private final PeaceConference pc;

    /** True while the player is editing a counter-offer. */
    private boolean editingCounter = false;

    /**
     * Surviving term indices during counter-offer editing.
     * Starts as all indices present; remove-buttons subtract from it.
     */
    private List<Integer> survivingIndices = new ArrayList<Integer>();

    /** Null the singleton on game load so a stale handle can't lock out the dialog. */
    public static void resetActiveInstance() {
        activeInstance = null;
    }

    public static void openScaled(PeaceConference pc) {
        if (activeInstance != null) return;
        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();
        int w = (int) Math.min(700f, Math.max(420f, sw * 0.48f));
        int h = (int) Math.min(480f, Math.max(300f, sh * 0.50f));
        try {
            BasePopUpDialog.popUpDialog(new PeaceConferenceDialog(pc), w, h);
        } catch (Throwable t) {
            activeInstance = null;
            log.warn("[Nex4x] PeaceConferenceDialog.openScaled failed: " + t.getMessage(), t);
        }
    }

    public PeaceConferenceDialog(PeaceConference pc) {
        super("Peace Conference");
        activeInstance = this;
        this.pc = pc;
        setConfirmText("Close");
    }

    @Override
    public void removeUI() {
        activeInstance = null;
        super.removeUI();
    }

    @Override
    public void createUI(CustomPanelAPI panel) {
        float w = panel.getPosition().getWidth();
        float h = panel.getPosition().getHeight();
        TooltipMakerAPI tip = panel.createUIElement(w - 16f, h - 16f, false);

        FactionAPI attacker = Global.getSector().getFaction(pc.getAttackerId());
        FactionAPI defender = Global.getSector().getFaction(pc.getDefenderId());
        String aName = attacker != null ? attacker.getDisplayName() : pc.getAttackerId();
        String dName = defender != null ? defender.getDisplayName() : pc.getDefenderId();

        tip.addPara("Peace conference between " + aName + " and " + dName,
                Misc.getHighlightColor(), 0f);
        tip.addSpacer(10f);

        PeaceTerms terms = pc.getStatus() == PeaceConference.Status.COUNTER_OFFERED && pc.getCounterOffer() != null
                ? pc.getCounterOffer() : pc.getProposed();
        tip.addPara("Status: " + pc.getStatus(), 6f);
        tip.addPara("Terms: " + (terms != null && !terms.isEmpty()
                ? terms.getTerms().size() + " term(s)" : "(none)"), 6f);
        tip.addSpacer(12f);

        if (editingCounter) {
            renderCounterEditor(tip, terms, w);
        } else {
            renderMainButtons(tip, w);
        }

        panel.addUIElement(tip).inTL(8f, 8f);
    }

    private void renderMainButtons(TooltipMakerAPI tip, float w) {
        float btnW = (w - 32f) / 3f;
        tip.addButton("Accept Terms", BTN_ACCEPT, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
        tip.addButton("Reject Terms", BTN_REJECT, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
        tip.addButton("Counter Offer", BTN_COUNTER, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
    }

    private void renderCounterEditor(TooltipMakerAPI tip, PeaceTerms terms, float w) {
        tip.addPara("Counter-offer editor — remove unwanted terms:",
                Misc.getHighlightColor(), 0f);
        tip.addPara("(Note: term amounts carry over unchanged.)",
                Misc.getGrayColor(), 2f);
        tip.addSpacer(6f);

        if (terms == null || terms.isEmpty()) {
            tip.addPara("No terms to remove.", Misc.getGrayColor(), 0f);
        } else {
            List<PeaceTerms.Term> termList = terms.getTerms();
            for (int i = 0; i < termList.size(); i++) {
                PeaceTerms.Term t = termList.get(i);
                boolean kept = survivingIndices.contains(i);
                String label = termLabel(t) + (kept ? "  [keep]" : "  [removed]");
                String btnLabel = kept ? "Remove" : "Restore";
                float btnW = (w - 32f) / 2f;
                tip.addPara(label, kept ? Misc.getTextColor() : Misc.getGrayColor(), 4f);
                tip.addButton(btnLabel, BTN_RM_PREFIX + i, Misc.getButtonTextColor(),
                        Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 18f, 2f);
            }
        }
        tip.addSpacer(10f);

        float btnW = (w - 32f) / 2f;
        tip.addButton("Submit Counter", BTN_COUNTER_SUBMIT, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
        tip.addButton("Cancel", BTN_COUNTER_CANCEL, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 2f);
    }

    private static String termLabel(PeaceTerms.Term t) {
        if (t.payload != null && !t.payload.isEmpty()) {
            return t.type.name() + " (" + t.payload + ")";
        }
        if (t.amount > 0) {
            return t.type.name() + " (" + (int) t.amount + " credits)";
        }
        return t.type.name();
    }

    @Override
    public void buttonPressed(Object id) {
        if (BTN_ACCEPT.equals(id)) {
            pc.accept();
            removeUI();
            return;
        }
        if (BTN_REJECT.equals(id)) {
            pc.reject();
            removeUI();
            return;
        }
        if (BTN_COUNTER.equals(id)) {
            // Enter counter-editor mode; populate surviving indices with all term indices.
            editingCounter = true;
            survivingIndices.clear();
            PeaceTerms terms = pc.getProposed();
            if (terms != null) {
                for (int i = 0; i < terms.getTerms().size(); i++) {
                    survivingIndices.add(i);
                }
            }
            recreateUI();
            return;
        }
        if (BTN_COUNTER_CANCEL.equals(id)) {
            editingCounter = false;
            survivingIndices.clear();
            recreateUI();
            return;
        }
        if (BTN_COUNTER_SUBMIT.equals(id)) {
            submitCounterOffer();
            return;
        }
        // Remove / Restore buttons
        if (id instanceof String) {
            String sid = (String) id;
            if (sid.startsWith(BTN_RM_PREFIX)) {
                try {
                    int idx = Integer.parseInt(sid.substring(BTN_RM_PREFIX.length()));
                    if (survivingIndices.contains(idx)) {
                        survivingIndices.remove(Integer.valueOf(idx));
                    } else {
                        survivingIndices.add(idx);
                    }
                    recreateUI();
                } catch (NumberFormatException e) {
                    log.warn("[Nex4x] PeaceConferenceDialog: bad remove index in: " + sid);
                }
            }
        }
    }

    private void submitCounterOffer() {
        PeaceTerms source = pc.getProposed();
        PeaceTerms edited = new PeaceTerms();
        if (source != null) {
            List<PeaceTerms.Term> all = source.getTerms();
            for (int i = 0; i < all.size(); i++) {
                if (survivingIndices.contains(i)) {
                    edited.add(all.get(i));
                }
            }
        }
        pc.counterOffer(edited);
        editingCounter = false;
        survivingIndices.clear();
        removeUI();
    }

    /**
     * Rebuild the panel in place. Clears children and calls createUI again.
     * Ashlib BasePopUpDialog doesn't expose a first-class "recreate"; we dismiss and re-open.
     * For in-panel re-rendering we delegate to the panel refresh path used by other dialogs.
     */
    private void recreateUI() {
        // Re-open the dialog at the same size with updated state.
        // We must close the current instance first.
        PeaceConference savedPc = this.pc;
        boolean savedEditing = this.editingCounter;
        List<Integer> savedIndices = new ArrayList<Integer>(this.survivingIndices);

        activeInstance = null;
        super.removeUI();

        // Re-open preserving editor state.
        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();
        int w = (int) Math.min(700f, Math.max(420f, sw * 0.48f));
        int h = (int) Math.min(480f, Math.max(300f, sh * 0.50f));
        try {
            PeaceConferenceDialog next = new PeaceConferenceDialog(savedPc);
            next.editingCounter = savedEditing;
            next.survivingIndices = savedIndices;
            BasePopUpDialog.popUpDialog(next, w, h);
        } catch (Throwable t) {
            activeInstance = null;
            log.warn("[Nex4x] PeaceConferenceDialog.recreateUI failed: " + t.getMessage(), t);
        }
    }
}
