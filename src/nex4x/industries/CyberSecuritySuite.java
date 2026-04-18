package nex4x.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.Nex4xConstants;
import nex4x.agents.security.DetectionEngine;
import org.json.JSONObject;

public class CyberSecuritySuite extends BaseIndustry {

    @Override
    public void apply() { super.apply(true); }

    @Override
    public boolean canBeDisrupted() { return true; }

    @Override
    public float getPatherInterest() { return 2f; }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tip, Industry.IndustryTooltipMode mode) {
        float pad = 10f;
        float bonus = 15f;
        try {
            JSONObject cfg = Global.getSettings().loadJSON(Nex4xConstants.PATH_MARKET_SECURITY);
            bonus = (float) cfg.optDouble("cyberSecurityBonus", 15.0);
        } catch (Exception ignore) {}

        tip.addPara("Counter-espionage bonus: +%s", pad,
                Misc.getHighlightColor(), String.format("%.0f", bonus));

        if (market != null && !isDisrupted()) {
            float total = DetectionEngine.getMarketDetection(market);
            tip.addPara("Current market detection strength: %s", 3f,
                    Misc.getHighlightColor(), String.format("%.0f", total));
        }
        if (isDisrupted()) {
            tip.addPara("Disrupted — no bonus applied.", Misc.getNegativeHighlightColor(), 3f);
        }
    }
}
