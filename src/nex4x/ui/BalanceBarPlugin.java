package nex4x.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Center-origin balance bar. balance in [-1, 1]:
 *   +1 = entirely favors player (full blue left fill)
 *   -1 = entirely favors target (full gold right fill)
 *    0 = balanced (no fill, center divider only)
 */
public class BalanceBarPlugin implements CustomUIPanelPlugin {

    private final float w;
    private final float h;
    private float balance;
    private CustomPanelAPI panel;

    public BalanceBarPlugin(float w, float h, float balance) {
        this.w = w;
        this.h = h;
        this.balance = Math.max(-1f, Math.min(1f, balance));
    }

    public void setBalance(float balance) {
        this.balance = Math.max(-1f, Math.min(1f, balance));
    }

    public void attach(CustomPanelAPI panel) {
        this.panel = panel;
    }

    @Override
    public void renderBelow(float amount) {
        if (panel == null) return;
        PositionAPI pos = panel.getPosition();
        float x = pos.getX();
        float y = pos.getY();
        float cx = x + w / 2f;

        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Background track
        GL11.glColor4f(0.1f, 0.1f, 0.1f, 0.85f);
        drawRect(x, y, w, h);

        if (balance > 0.01f) {
            // Favors player — blue fill leftward from center
            GL11.glColor4f(0.17f, 0.42f, 0.62f, 0.85f);
            float fillW = (w / 2f) * balance;
            drawRect(cx - fillW, y, fillW, h);
        } else if (balance < -0.01f) {
            // Favors target — gold fill rightward from center
            GL11.glColor4f(0.62f, 0.42f, 0.17f, 0.85f);
            float fillW = (w / 2f) * (-balance);
            drawRect(cx, y, fillW, h);
        }

        // Center divider
        GL11.glColor4f(0.55f, 0.55f, 0.55f, 1.0f);
        drawRect(cx - 1f, y, 2f, h);

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();
    }

    private void drawRect(float rx, float ry, float rw, float rh) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(rx,      ry);
        GL11.glVertex2f(rx + rw, ry);
        GL11.glVertex2f(rx + rw, ry + rh);
        GL11.glVertex2f(rx,      ry + rh);
        GL11.glEnd();
    }

    @Override public void positionChanged(PositionAPI pos) {}
    @Override public void render(float amount) {}
    @Override public void advance(float amount) {}
    @Override public void processInput(List<InputEventAPI> events) {}
    @Override public void buttonPressed(Object id) {}
}
