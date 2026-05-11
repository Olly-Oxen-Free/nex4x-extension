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

public class PeaceConferenceDialog extends BasePopUpDialog {
    private static final Logger log = Global.getLogger(PeaceConferenceDialog.class);
    private static PeaceConferenceDialog activeInstance;

    private static final String BTN_ACCEPT  = "pc_accept";
    private static final String BTN_REJECT  = "pc_reject";
    private static final String BTN_COUNTER = "pc_counter";

    private final PeaceConference pc;

    public static void openScaled(PeaceConference pc) {
        if (activeInstance != null) return;
        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();
        int w = (int) Math.min(640f, Math.max(420f, sw * 0.45f));
        int h = (int) Math.min(420f, Math.max(280f, sh * 0.45f));
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
        tip.addPara("Terms: " + (terms != null && !terms.isEmpty() ? terms.getTerms().size() + " term(s)" : "(none)"), 6f);
        tip.addSpacer(12f);

        float btnW = (w - 32f) / 3f;
        tip.addButton("Accept Terms", BTN_ACCEPT, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
        tip.addButton("Reject Terms", BTN_REJECT, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);
        tip.addButton("Counter Offer", BTN_COUNTER, Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, btnW, 22f, 4f);

        panel.addUIElement(tip).inTL(8f, 8f);
    }

    @Override
    public void buttonPressed(Object id) {
        if (BTN_ACCEPT.equals(id)) { pc.accept(); removeUI(); }
        else if (BTN_REJECT.equals(id)) { pc.reject(); removeUI(); }
        else if (BTN_COUNTER.equals(id)) {
            PeaceTerms simpler = pc.getProposed();
            // v1 counter: same terms — placeholder until full editor exists.
            pc.counterOffer(simpler);
            removeUI();
        }
    }
}
