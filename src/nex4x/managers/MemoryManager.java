package nex4x.managers;

import com.fs.starfarer.api.Global;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import nex4x.data.*;
import nex4x.memory.FactionMemory;
import nex4x.memory.FactionMemoryStore;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.*;

public class MemoryManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(MemoryManager.class);

    private final Map<String, FactionMemoryStore> stores = new HashMap<String, FactionMemoryStore>();
    private float lastAdvanceDay = 0;

    private String storeKey(String holder, String about) {
        return holder + "::" + about;
    }

    public FactionMemoryStore getStore(String holderFactionId, String aboutFactionId) {
        String key = storeKey(holderFactionId, aboutFactionId);
        FactionMemoryStore store = stores.get(key);
        if (store == null) {
            store = new FactionMemoryStore(holderFactionId, aboutFactionId);
            stores.put(key, store);
        }
        return store;
    }

    public void createMemory(String typeId, String sourceFaction, String targetFaction, String details) {
        MemoryTypeDef typeDef = MemoryTypeRegistry.get(typeId);
        if (typeDef == null) {
            log.warn("[Nex4x] Unknown memory type: " + typeId);
            return;
        }

        float beliefMult = calculateBeliefMultiplier(targetFaction, typeDef);
        float traitDecayMult = calculateTraitDecayModifier(targetFaction, typeId);

        float gameDays = Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getMonth() - 1) * 30f
                + (Global.getSector().getClock().getCycle() - 206) * 365f;

        FactionMemory memory = new FactionMemory(typeId, sourceFaction, targetFaction,
                beliefMult, traitDecayMult, gameDays, details);

        getStore(targetFaction, sourceFaction).addMemory(memory);

        log.info("[Nex4x] Memory created: " + targetFaction + " remembers " + typeId
                + " by " + sourceFaction + " (impact: " + memory.getCurrentImpact() + ")");
    }

    private float calculateBeliefMultiplier(String factionId, MemoryTypeDef typeDef) {
        if (typeDef.amplifyingCategories.isEmpty()) return 1f;

        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        float bestMult = 1f;

        for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
            BeliefDef def = BeliefRegistry.get(entry.beliefId);
            if (def == null) continue;

            if (typeDef.amplifyingCategories.contains(def.category.name())) {
                float mult = entry.getImpactMultiplier();
                if (mult > bestMult) bestMult = mult;
            }
        }

        return bestMult;
    }

    private float calculateTraitDecayModifier(String factionId, String memoryTypeId) {
        List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
        float mult = 1f;

        for (String trait : traits) {
            if (trait.equals(TraitIds.IRREDENTIST) && memoryTypeId.startsWith("territorial")) {
                return 0.001f;
            }
            if (trait.equals(TraitIds.STALWART)) {
                mult *= 0.67f;
            }
            if (trait.equals(TraitIds.TEMPERAMENTAL)) {
                mult *= 1.5f;
            }
            if (trait.equals(TraitIds.FOREVERWAR) && memoryTypeId.startsWith("military")) {
                return 0.001f;
            }
            if (trait.equals(TraitIds.HELPS_ALLIES)
                    && (memoryTypeId.equals("honored_pact") || memoryTypeId.equals("aid_given"))) {
                mult *= 0.5f;
            }
            if (trait.equals(TraitIds.DEVIOUS) && memoryTypeId.equals("espionage_discovered")) {
                mult *= 2f;
            }
            if (trait.equals(TraitIds.PREDATORY) && memoryTypeId.equals("convoy_raided")) {
                mult *= 2f;
            }
            if (trait.equals(TraitIds.SELFRIGHTEOUS)) {
                MemoryTypeDef def = MemoryTypeRegistry.get(memoryTypeId);
                if (def != null && def.amplifyingCategories.contains("IDEOLOGICAL")) {
                    mult *= 0.5f;
                }
            }
        }

        return mult;
    }

    public void advanceAllDecay(float daysPassed) {
        for (FactionMemoryStore store : stores.values()) {
            store.advanceDecay(daysPassed);
        }
    }

    public float getDispositionModifier(String holderFactionId, String aboutFactionId) {
        return getStore(holderFactionId, aboutFactionId).getTotalDispositionModifier();
    }
}
