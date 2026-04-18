package nex4x.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.Nex4xConstants;
import org.json.JSONObject;

public class IntelligenceBureau extends BaseIndustry {

    @Override
    public void apply() { super.apply(true); }

    @Override
    public boolean canBeDisrupted() { return true; }

    @Override
    public float getPatherInterest() { return 1f; }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tip, Industry.IndustryTooltipMode mode) {
        float pad = 10f;
        int influence = 3;
        float assist = 8f;
        try {
            JSONObject inf = Global.getSettings().loadJSON(Nex4xConstants.PATH_INFLUENCE_SOURCES);
            influence = inf.getJSONObject("buildingIncome").optInt(Nex4xConstants.BUILDING_INTELLIGENCE_BUREAU, 3);
            JSONObject sec = Global.getSettings().loadJSON(Nex4xConstants.PATH_MARKET_SECURITY);
            assist = (float) sec.optDouble("intelBureauBonus", 8.0);
        } catch (Exception ignore) {}

        tip.addPara("Influence income: +%s per cycle", pad,
                Misc.getHighlightColor(), String.valueOf(influence));
        tip.addPara("Counter-espionage patrol assist: +%s", 3f,
                Misc.getHighlightColor(), String.format("%.0f", assist));

        if (isDisrupted()) {
            tip.addPara("Disrupted — no effects applied.", Misc.getNegativeHighlightColor(), 3f);
        }
    }
}
