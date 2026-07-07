package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class BaseValueTable {
    private static final Logger log = Global.getLogger(BaseValueTable.class);

    public static int NON_AGGRESSION_PACT  = 3000;
    public static int ALLIANCE             = 8000;
    public static int TRADE_PACT           = 4000;
    public static int SEEK_PEACE           = 5000;
    public static int FRIENDSHIP_DECL      = 2500;
    public static int DENOUNCEMENT_DECL    = 1500;
    public static int INTEL                = 500;
    public static int STAR_CHART           = 750;
    public static int BLUEPRINT_MULT       = 10;
    public static int CEASEFIRE            = 5000;
    public static int PEACE_TREATY         = 8000;

    public static void load() {
        try {
            JSONObject o = Global.getSettings().loadJSON("data/config/nex4x/valuation/base_values.json");
            JSONObject ag = o.optJSONObject("agreements");
            if (ag != null) {
                NON_AGGRESSION_PACT = ag.optInt("non_aggression_pact", NON_AGGRESSION_PACT);
                ALLIANCE            = ag.optInt("alliance", ALLIANCE);
                TRADE_PACT          = ag.optInt("trade_pact", TRADE_PACT);
                SEEK_PEACE          = ag.optInt("seek_peace", SEEK_PEACE);
            }
            JSONObject dc = o.optJSONObject("declarations");
            if (dc != null) {
                FRIENDSHIP_DECL     = dc.optInt("friendship", FRIENDSHIP_DECL);
                DENOUNCEMENT_DECL   = dc.optInt("denouncement", DENOUNCEMENT_DECL);
            }
            JSONObject ot = o.optJSONObject("one_time");
            if (ot != null) {
                INTEL               = ot.optInt("intel", INTEL);
                STAR_CHART          = ot.optInt("star_chart", STAR_CHART);
                BLUEPRINT_MULT      = ot.optInt("blueprint_multiplier", BLUEPRINT_MULT);
            }
            CEASEFIRE    = o.optInt("agreements_ceasefire", CEASEFIRE);
            PEACE_TREATY = o.optInt("agreements_peace_treaty", PEACE_TREATY);
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load base_values.json: " + e.getMessage());
        }
    }
}
