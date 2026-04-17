package nex4x.data;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class MemoryTypeRegistry {

    private static final Logger log = Global.getLogger(MemoryTypeRegistry.class);
    private static final Map<String, MemoryTypeDef> types = new HashMap<String, MemoryTypeDef>();

    public static void load() throws Exception {
        types.clear();

        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "id", Nex4xConstants.PATH_MEMORY_TYPES, Nex4xConstants.MOD_ID);

        for (int i = 0; i < csv.length(); i++) {
            JSONObject row = csv.getJSONObject(i);
            String id = row.getString("id");
            String name = row.getString("name");
            float impact = (float) row.getDouble("base_impact");
            float decay = (float) row.getDouble("base_decay_days");
            String catStr = row.optString("amplifying_categories", "");

            Set<String> cats = new HashSet<String>();
            if (!catStr.isEmpty()) {
                for (String s : catStr.split("\\|")) {
                    cats.add(s.trim().toUpperCase());
                }
            }

            types.put(id, new MemoryTypeDef(id, name, impact, decay, cats));
        }

        log.info("[Nex4x] Loaded " + types.size() + " memory type definitions");
    }

    public static MemoryTypeDef get(String id) { return types.get(id); }
    public static Map<String, MemoryTypeDef> getAll() { return types; }
}
