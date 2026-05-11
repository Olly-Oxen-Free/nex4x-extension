package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;
import rolflectionlib.util.RolfLectionUtil;
import wfg.native_ui.ui.Attachments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection bridge using WrapUI's RolfLectionLib + Attachments.
 */
public final class IntelReflectionUtil {

    private static final Logger log = Global.getLogger(IntelReflectionUtil.class);

    // Per-class cache for getChildrenNonCopy and getText method objects.
    private static final Map<Class<?>, Object> childrenMethodCache = new HashMap<Class<?>, Object>();
    private static final Map<Class<?>, Object> getTextMethodCache   = new HashMap<Class<?>, Object>();
    private static final Map<Class<?>, Object> setTextMethodCache   = new HashMap<Class<?>, Object>();
    private static final Map<Class<?>, Object> isCheckedMethodCache     = new HashMap<Class<?>, Object>();
    private static final Map<Class<?>, Object> isHighlightedMethodCache = new HashMap<Class<?>, Object>();

    private IntelReflectionUtil() {}

    public static void init() {
        // no-op — methods cached lazily on first panel encounter
    }

    public static UIPanelAPI getCurrentTab() {
        return Attachments.getCurrentTab();
    }

    /** Returns the CoreUI panel — parent of all sub-tab content and tab bars. */
    public static UIPanelAPI getCoreUI() {
        return Attachments.getCoreUI();
    }

    // ── getChildrenNonCopy ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static List<Object> getChildrenNonCopy(Object panel) {
        if (panel == null) return null;
        try {
            Class<?> cls = panel.getClass();
            Object method = childrenMethodCache.get(cls);
            if (method == null) {
                method = RolfLectionUtil.getMethod("getChildrenNonCopy", cls);
                if (method == null) return null;
                childrenMethodCache.put(cls, method);
            }
            Object result = RolfLectionUtil.invokeMethodDirectly(method, panel);
            return (result instanceof List) ? (List<Object>) result : null;
        } catch (Throwable e) {
            return null;
        }
    }

    // ── getText / setText / isChecked via reflection ───────────────────────────

    public static String getTextSafe(Object obj) {
        if (obj == null) return null;
        try {
            Class<?> cls = obj.getClass();
            Object method = getTextMethodCache.get(cls);
            if (method == null) {
                method = RolfLectionUtil.getMethod("getText", cls);
                if (method == null) { getTextMethodCache.put(cls, Boolean.FALSE); return null; }
                getTextMethodCache.put(cls, method);
            }
            if (method instanceof Boolean) return null; // sentinel: no getText
            Object result = RolfLectionUtil.invokeMethodDirectly(method, obj);
            return (result instanceof String) ? (String) result : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public static void setTextSafe(Object obj, String text) {
        if (obj == null) return;
        try {
            Class<?> cls = obj.getClass();
            Object method = setTextMethodCache.get(cls);
            if (method == null) {
                method = RolfLectionUtil.getMethod("setText", cls);
                if (method == null) { setTextMethodCache.put(cls, Boolean.FALSE); return; }
                setTextMethodCache.put(cls, method);
            }
            if (method instanceof Boolean) return;
            RolfLectionUtil.invokeMethodDirectly(method, obj, text);
        } catch (Throwable e) {
            log.warn("[Nex4x] setTextSafe: " + e.getMessage());
        }
    }

    public static boolean isHighlightedSafe(Object obj) {
        if (obj == null) return false;
        try {
            Class<?> cls = obj.getClass();
            Object method = isHighlightedMethodCache.get(cls);
            if (method == null) {
                method = RolfLectionUtil.getMethod("isHighlighted", cls);
                if (method == null) { isHighlightedMethodCache.put(cls, Boolean.FALSE); return false; }
                isHighlightedMethodCache.put(cls, method);
            }
            if (method instanceof Boolean) return false;
            Object result = RolfLectionUtil.invokeMethodDirectly(method, obj);
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isCheckedSafe(Object obj) {
        if (obj == null) return false;
        try {
            Class<?> cls = obj.getClass();
            Object method = isCheckedMethodCache.get(cls);
            if (method == null) {
                method = RolfLectionUtil.getMethod("isChecked", cls);
                if (method == null) { isCheckedMethodCache.put(cls, Boolean.FALSE); return false; }
                isCheckedMethodCache.put(cls, method);
            }
            if (method instanceof Boolean) return false;
            Object result = RolfLectionUtil.invokeMethodDirectly(method, obj);
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            return false;
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Recursively searches ALL children (not just UIPanelAPI) for a child
     * whose getText() equals the given text. Returns the raw Object.
     */
    public static Object findChildByText(Object root, String text) {
        List<Object> children = getChildrenNonCopy(root);
        if (children == null) return null;
        for (Object child : children) {
            String t = getTextSafe(child);
            if (text.equals(t)) return child;
            Object found = findChildByText(child, text);
            if (found != null) return found;
        }
        return null;
    }

    /** Backwards-compat wrapper — returns ButtonAPI if child is one, else null. */
    public static ButtonAPI findButtonByText(UIPanelAPI root, String text) {
        Object found = findChildByText(root, text);
        return (found instanceof ButtonAPI) ? (ButtonAPI) found : null;
    }

    // ── Hierarchy logging ─────────────────────────────────────────────────────

    public static void logPanelHierarchy(Object panel, String indent) {
        List<Object> children = getChildrenNonCopy(panel);
        if (children == null) { log.info(indent + "[null children]"); return; }
        for (Object child : children) {
            String cls   = child.getClass().getSimpleName();
            String txt   = getTextSafe(child);
            String extra = (txt != null) ? " text='" + txt + "'" : "";
            log.info(indent + cls + extra);
            if (indent.length() < 20) {
                logPanelHierarchy(child, indent + "  ");
            }
        }
    }

    /** Calls getFactionPanel() on the Intel panel (com.fs.starfarer.campaign.comms.F). */
    public static Object getFactionPanel(Object intelPanel) {
        if (intelPanel == null) return null;
        try {
            Object method = RolfLectionUtil.getMethod("getFactionPanel", intelPanel.getClass());
            if (method == null) return null;
            return RolfLectionUtil.invokeMethodDirectly(method, intelPanel);
        } catch (Throwable e) {
            log.warn("[Nex4x] getFactionPanel: " + e.getMessage());
            return null;
        }
    }

    /** Calls getFactionsButton() on the Intel panel (com.fs.starfarer.campaign.comms.F). */
    public static Object getFactionsButton(Object intelPanel) {
        if (intelPanel == null) return null;
        try {
            Object method = RolfLectionUtil.getMethod("getFactionsButton", intelPanel.getClass());
            if (method == null) return null;
            return RolfLectionUtil.invokeMethodDirectly(method, intelPanel);
        } catch (Throwable e) {
            log.warn("[Nex4x] getFactionsButton: " + e.getMessage());
            return null;
        }
    }

    /** No-op — kept for call-site compatibility. */
    public static void ensureCoreHandles() {}
}
