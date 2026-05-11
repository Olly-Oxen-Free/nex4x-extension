package nex4x.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * Runs work on the next campaign frame so dialogs can open after intel / popups tear down.
 */
public final class Nex4xDeferredUi {

    private Nex4xDeferredUi() {}

    public static void runNextFrame(final Runnable action) {
        if (action == null) return;
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done;

            public boolean isDone() {
                return done;
            }

            public boolean runWhilePaused() {
                return false;
            }

            public void advance(float amount) {
                if (done) return;
                done = true;
                try {
                    action.run();
                } catch (Exception e) {
                    Global.getLogger(Nex4xDeferredUi.class).warn("Deferred UI action failed: " + e.getMessage(), e);
                }
            }
        });
    }
}
