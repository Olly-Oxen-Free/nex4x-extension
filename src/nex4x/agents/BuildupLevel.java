package nex4x.agents;

/**
 * Buildup levels 0-3. Used by all three agent types.
 * Level 0 = Fresh, Level 3 = maximum (Deep Cover / Entrenched / Esteemed).
 */
public class BuildupLevel {
    public static final int FRESH = 0;
    public static final int LEVEL_1 = 1;
    public static final int LEVEL_2 = 2;
    public static final int LEVEL_3 = 3;
    public static final int MAX_LEVEL = 3;

    /** Buildup points needed to reach each level. */
    public static final float[] THRESHOLDS = { 0f, 30f, 90f, 180f };

    public static int getLevelForPoints(float points) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (points >= THRESHOLDS[i]) return i;
        }
        return FRESH;
    }
}
