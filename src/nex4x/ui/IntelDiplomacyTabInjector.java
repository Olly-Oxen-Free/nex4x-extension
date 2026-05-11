package nex4x.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Two modes: RETRY (polls for Factions button) and MONITOR (z-order-based tab detection).
 *
 * Visibility strategy:
 *   The Intel panel uses z-order, not opacity, to show/hide sub-tab content — all panels stay
 *   at opacity 1.0. When a tab is selected, vanilla calls bringComponentToTop(contentPanel),
 *   making it the last child (highest z-order) in the Intel panel's children list.
 *
 *   We poll the children list each frame: if the Diplomacy content panel is the last
 *   non-button, non-overlay child, Diplomacy is active → show overlay. Otherwise hide it.
 *
 *   Button children are identified by having non-empty text (via reflection).
 *   Content panels have no text. Our overlay is excluded by reference equality.
 */
public class IntelDiplomacyTabInjector implements EveryFrameScript {

    private static final Logger log = Global.getLogger(IntelDiplomacyTabInjector.class);

    // ── MONITOR mode ──────────────────────────────────────────────────────────
    private final Object          diplomacyBtn;
    private final UIPanelAPI      intelPanel;
    private final CustomPanelAPI  overlayPanel;

    private boolean          resolved        = false;
    private int              resolveAttempts = 0;
    private static final int MAX_RESOLVE     = 180;

    // The Diplomacy sub-tab's content panel — resolved once, then polled each frame.
    private Object diplomacyContentPanel = null;
    // Cached previous active state to avoid redundant opacity/z-order calls.
    private boolean wasActive = false;

    // ── RETRY mode ────────────────────────────────────────────────────────────
    private final boolean retryMode;
    private boolean retryDone = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    IntelDiplomacyTabInjector(Object diplomacyBtn, UIPanelAPI intelPanel,
                               CustomPanelAPI overlayPanel) {
        this.retryMode    = false;
        this.diplomacyBtn = diplomacyBtn;
        this.intelPanel   = intelPanel;
        this.overlayPanel = overlayPanel;
    }

    IntelDiplomacyTabInjector() {
        this.retryMode    = true;
        this.diplomacyBtn = null;
        this.intelPanel   = null;
        this.overlayPanel = null;
    }

    // ── EveryFrameScript ──────────────────────────────────────────────────────

    @Override
    public boolean isDone() {
        if (Global.getCurrentState() != GameState.CAMPAIGN) return true;
        if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.INTEL) return true;
        if (retryMode) return retryDone;
        return false;
    }

    @Override public boolean runWhilePaused() { return true; }

    @Override
    public void advance(float amount) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) return;
        if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.INTEL) return;
        if (retryMode) { advanceRetry(); } else { advanceMonitor(); }
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    private void advanceRetry() {
        if (retryDone) return;
        UIPanelAPI panel = IntelReflectionUtil.getCurrentTab();
        if (panel == null) return;
        Object factionsBtn = IntelReflectionUtil.findChildByText(panel, "Factions [3]");
        if (factionsBtn == null) {
            log.info("[Nex4x] retry: Factions [3] not found, dumping hierarchy");
            IntelReflectionUtil.logPanelHierarchy(panel, "  ");
            retryDone = true;
            return;
        }
        CoreUITabInjectorListener.injectWithPanelAndButton(panel, factionsBtn);
        retryDone = true;
    }

    // ── Monitor ───────────────────────────────────────────────────────────────

    private void advanceMonitor() {
        if (!resolved) {
            resolveContentPanel();
            return;
        }
        pollTabActive();
    }

    /**
     * Resolve the Diplomacy (Factions) content panel via getFactionPanel() reflection
     * on the Intel panel (com.fs.starfarer.campaign.comms.F).
     */
    private void resolveContentPanel() {
        if (resolveAttempts++ >= MAX_RESOLVE) { resolved = true; return; }

        Object factionPanel = IntelReflectionUtil.getFactionPanel(intelPanel);
        if (factionPanel == null) return;

        diplomacyContentPanel = factionPanel;
        resolved = true;
        log.info("[Nex4x] Diplomacy content panel resolved via getFactionPanel(): "
                + factionPanel.getClass().getSimpleName());
    }

    /**
     * Each frame, find the last content panel (highest z-order, excluding our overlay).
     * If it is the Diplomacy content panel → show overlay. Otherwise hide.
     *
     * Vanilla's tab-switch calls bringComponentToTop(contentPanel), which moves that
     * panel to the end of the children list — making it the "active" sub-tab.
     */
    private int diagFrame = 0;

    private void pollTabActive() {
        // Primary signal: button highlight state. Vanilla comms.F.new(z) calls
        // n.highlight() on the active tab button and n.unhighlight() on the rest.
        boolean isActive = IntelReflectionUtil.isHighlightedSafe(diplomacyBtn);

        if ((++diagFrame % 120) == 0) {
            String btnCls = (diplomacyBtn != null) ? diplomacyBtn.getClass().getSimpleName() : "null";
            log.info("[Nex4x] poll: highlighted=" + isActive + " btn=" + btnCls);
        }

        if (isActive == wasActive) return;
        wasActive = isActive;

        log.info("[Nex4x] Diplomacy tab active=" + isActive);

        if (isActive) {
            overlayPanel.setOpacity(1f);
            intelPanel.bringComponentToTop(overlayPanel);
        } else {
            overlayPanel.setOpacity(0.001f);
        }
    }
}
