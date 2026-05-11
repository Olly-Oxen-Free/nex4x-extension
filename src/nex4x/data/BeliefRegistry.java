package nex4x.data;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class BeliefRegistry {

    private static final Logger log = Global.getLogger(BeliefRegistry.class);
    private static final Map<String, BeliefDef> beliefs = new HashMap<String, BeliefDef>();

    public static void load() throws Exception {
        beliefs.clear();

        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "id", Nex4xConstants.PATH_BELIEFS_REGISTRY, Nex4xConstants.MOD_ID);

        for (int i = 0; i < csv.length(); i++) {
            JSONObject row = csv.getJSONObject(i);
            String id = row.getString("id");
            String name = row.getString("name");
            String category = row.getString("category");
            String desc = row.optString("description", "");

            beliefs.put(id, new BeliefDef(id, name, category, desc));
        }

        log.info("[Nex4x] Loaded " + beliefs.size() + " belief definitions");
    }

    public static BeliefDef get(String id) {
        return beliefs.get(id);
    }

    public static Map<String, BeliefDef> getAll() {
        return beliefs;
    }
}
