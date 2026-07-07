package nex4x.politics;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.data.TendencyId;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Event-driven tendency shifts per faction. Modifiers accumulate and decay daily.
 */
public class DynamicModifierManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(DynamicModifierManager.class);

    private final Map<String, List<PoliticalModifier>> factionModifiers =
            new HashMap<String, List<PoliticalModifier>>();

    public void fireEvent(String factionId, ModifierEventType eventType, String details) {
        fireEvent(factionId, eventType, eventType.baseAmount, details);
    }

    public void fireEvent(String factionId, ModifierEventType eventType,
                          float amount, String details) {
        List<PoliticalModifier> mods = getModifiers(factionId);
        float day = nex4x.util.Nex4xClock.currentAbsoluteDay();

        PoliticalModifier mod = new PoliticalModifier(eventType, amount, day, details);
        mods.add(mod);

        log.info("[Nex4x] Dynamic modifier: " + factionId + " "
                + eventType.targetTendency + " +" + amount + " (" + details + ")");
    }

    public float getTotalShift(String factionId, TendencyId tendency) {
        float total = 0f;
        for (PoliticalModifier mod : getModifiers(factionId)) {
            if (mod.getTargetTendency() == tendency && !mod.isExpired()) {
                total += mod.getAmount();
            }
        }
        return total;
    }

    public List<PoliticalModifier> getActiveModifiers(String factionId) {
        List<PoliticalModifier> result = new ArrayList<PoliticalModifier>();
        for (PoliticalModifier mod : getModifiers(factionId)) {
            if (!mod.isExpired()) result.add(mod);
        }
        return result;
    }

    public void advanceDay() {
        for (Map.Entry<String, List<PoliticalModifier>> entry : factionModifiers.entrySet()) {
            Iterator<PoliticalModifier> it = entry.getValue().iterator();
            while (it.hasNext()) {
                if (it.next().decay()) it.remove();
            }
        }
    }

    private List<PoliticalModifier> getModifiers(String factionId) {
        List<PoliticalModifier> mods = factionModifiers.get(factionId);
        if (mods == null) {
            mods = new ArrayList<PoliticalModifier>();
            factionModifiers.put(factionId, mods);
        }
        return mods;
    }

    public static DynamicModifierManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_DYNAMIC_MODIFIERS);
        if (raw instanceof DynamicModifierManager) return (DynamicModifierManager) raw;
        return null;
    }

    public static DynamicModifierManager getOrCreate() {
        DynamicModifierManager mgr = get();
        if (mgr == null) {
            mgr = new DynamicModifierManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_DYNAMIC_MODIFIERS, mgr);
        }
        return mgr;
    }
}
