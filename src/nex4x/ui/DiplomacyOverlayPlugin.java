package nex4x.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/**
 * Plugin for the Diplomacy overlay panel injected into the Intel screen.
 *
 * The overlay covers the FULL Intel panel (including tab bar) so that processInput
 * receives tab-bar click events. Only the content area (below the 30px tab bar) is
 * rendered black. Tab button clicks are detected by screen-position hit-testing;
 * events are NOT consumed so vanilla buttons still function.
 */
public class DiplomacyOverlayPlugin implements CustomUIPanelPlugin {

    private static final Color BACKGROUND    = new Color(0, 0, 0, 255);
    private static final float TAB_BAR_H     = 30f;

    private DiplomacyTabOverlayModel model;
    private PositionAPI              position;
    private Runnable                 refreshCallback;

    // Tab button hit-testing — set by IntelDiplomacyTabInjector once resolved.
    private UIComponentAPI       diplomacyBtnComp = null;
    private List<UIComponentAPI> otherBtnComps    = Collections.emptyList();
    private Runnable             showCallback     = null;
    private Runnable             hideCallback     = null;

    public void setModel(DiplomacyTabOverlayModel model)     { this.model = model; }
    public void setRefreshCallback(Runnable r)               { this.refreshCallback = r; }

    public void setTabButtons(UIComponentAPI dipBtn,
                              List<UIComponentAPI> others,
                              Runnable show,
                              Runnable hide) {
        this.diplomacyBtnComp = dipBtn;
        this.otherBtnComps    = others;
        this.showCallback     = show;
        this.hideCallback     = hide;
    }

    @Override public void positionChanged(PositionAPI pos) { this.position = pos; }

    @Override
    public void renderBelow(float alphaMult) {
        if (position == null) return;
        float x = position.getX();
        float y = position.getY();      // screen bottom of the overlay
        float w = position.getWidth();
        float h = position.getHeight();

        // Draw black only for the content area; leave the top TAB_BAR_H transparent.
        float contentTop = y + h - TAB_BAR_H;

        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(
                BACKGROUND.getRed()   / 255f,
                BACKGROUND.getGreen() / 255f,
                BACKGROUND.getBlue()  / 255f,
                BACKGROUND.getAlpha() / 255f * alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, contentTop);
        GL11.glVertex2f(x,     contentTop);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();
    }

    @Override public void render(float alphaMult) {}
    @Override public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (diplomacyBtnComp == null) return;
        for (InputEventAPI e : events) {
            if (!e.isLMBDownEvent()) continue;
            float mx = e.getX(), my = e.getY();
            if (hits(mx, my, diplomacyBtnComp)) {
                if (showCallback != null) showCallback.run();
            } else {
                for (UIComponentAPI btn : otherBtnComps) {
                    if (hits(mx, my, btn)) {
                        if (hideCallback != null) hideCallback.run();
                        break;
                    }
                }
            }
        }
    }

    private static boolean hits(float mx, float my, UIComponentAPI comp) {
        PositionAPI p = comp.getPosition();
        if (p == null) return false;
        return mx >= p.getX() && mx <= p.getX() + p.getWidth()
            && my >= p.getY() && my <= p.getY() + p.getHeight();
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (model != null) model.handleButton(buttonId, refreshCallback);
    }
}
