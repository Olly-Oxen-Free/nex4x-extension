package nex4x.ui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Loaded by a fresh URLClassLoader (not the mod/script classloader) so that
 * getDeclaredMethods() is not blocked by Starsector's sandbox.
 *
 * DO NOT reference any mod classes here — this class must be loadable
 * standalone via a classloader whose parent is the game classloader.
 */
public class ReflectionBridge {

    /**
     * Builds all MethodHandles needed for the Intel panel injection chain.
     * Returns: [getInstance, getCurrentState, getCore, getCurrentTab, getChildrenNonCopy_placeholder]
     * Index 4 is null — getChildrenNonCopy requires a panel instance and is populated later.
     */
    public static MethodHandle[] buildHandles() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Class<?> adClass = Class.forName("com.fs.state.AppDriver");
        Method getInstanceM = findMethod(adClass, "getInstance");
        MethodHandle getInstance = lookup.unreflect(getInstanceM);

        Object appDriver = getInstance.invoke();
        Method getCurrentStateM = findMethod(appDriver.getClass(), "getCurrentState");
        MethodHandle getCurrentState = lookup.unreflect(getCurrentStateM);

        Object state = getCurrentState.invoke(appDriver);
        Method getCoreM = findMethod(state.getClass(), "getCore");
        MethodHandle getCore = lookup.unreflect(getCoreM);

        Object coreUI = getCore.invoke(state);
        Method getCurrentTabM = findMethod(coreUI.getClass(), "getCurrentTab");
        MethodHandle getCurrentTab = lookup.unreflect(getCurrentTabM);

        return new MethodHandle[]{getInstance, getCurrentState, getCore, getCurrentTab};
    }

    /**
     * Builds the getChildrenNonCopy MethodHandle for a given panel instance.
     * Called separately since a panel instance is required to discover the class.
     */
    public static MethodHandle buildChildrenHandle(Object panel) throws Throwable {
        Method m = findMethod(panel.getClass(), "getChildrenNonCopy");
        if (m == null) throw new NoSuchMethodException("getChildrenNonCopy on " + panel.getClass());
        return MethodHandles.lookup().unreflect(m);
    }

    private static Method findMethod(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}
