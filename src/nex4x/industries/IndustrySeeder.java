package nex4x.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds nex4x influence industries onto qualifying AI faction markets at new-game start.
 * Called from Nex4xModPlugin.onNewGameAfterEconomyLoad — NOT from onGameLoad (save-compat).
 */
public class IndustrySeeder {

    private static final Logger log = Global.getLogger(IndustrySeeder.class);

    public static void seedAll() {
        try {
            JSONObject cfg = Global.getSettings().loadJSON(Nex4xConstants.PATH_INDUSTRY_SEEDING);

            // Parse industry entries
            String embassyId = "nex4x_diplomatic_embassy";
            int embassyMinSize = 3;
            String bureauId = "nex4x_intelligence_bureau";
            int bureauMinSize = 4;

            JSONObject embassy = cfg.optJSONObject("diplomaticEmbassy");
            if (embassy != null) {
                embassyId = embassy.optString("industryId", embassyId);
                embassyMinSize = embassy.optInt("minMarketSize", embassyMinSize);
            }

            JSONObject bureau = cfg.optJSONObject("intelligenceBureau");
            if (bureau != null) {
                bureauId = bureau.optString("industryId", bureauId);
                bureauMinSize = bureau.optInt("minMarketSize", bureauMinSize);
            }

            // Parse exclusions
            Set<String> excludeFactions = new HashSet<String>();
            JSONArray excludeFactionArr = cfg.optJSONArray("excludeFactions");
            if (excludeFactionArr != null) {
                for (int i = 0; i < excludeFactionArr.length(); i++) {
                    excludeFactions.add(excludeFactionArr.getString(i));
                }
            }

            Set<String> excludeTags = new HashSet<String>();
            JSONArray excludeTagArr = cfg.optJSONArray("excludeMarketTags");
            if (excludeTagArr != null) {
                for (int i = 0; i < excludeTagArr.length(); i++) {
                    excludeTags.add(excludeTagArr.getString(i));
                }
            }

            // Seed
            int embassyCount = 0;
            int bureauCount = 0;
            int marketCount = 0;

            List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
            for (MarketAPI m : markets) {
                if (m == null || m.getFaction() == null) continue;
                if (m.getFaction().isPlayerFaction()) continue;
                if (excludeFactions.contains(m.getFactionId())) continue;

                // Check market tags
                if (!excludeTags.isEmpty()) {
                    Collection<String> tags = m.getTags();
                    boolean tagBlocked = false;
                    if (tags != null) {
                        for (String tag : tags) {
                            if (excludeTags.contains(tag)) {
                                tagBlocked = true;
                                break;
                            }
                        }
                    }
                    if (tagBlocked) continue;
                }

                boolean seededThisMarket = false;

                if (m.getSize() >= embassyMinSize && !m.hasIndustry(embassyId)) {
                    exerelin.campaign.ColonyManager.buildIndustry(m, embassyId, true);
                    embassyCount++;
                    seededThisMarket = true;
                }

                if (m.getSize() >= bureauMinSize && !m.hasIndustry(bureauId)) {
                    exerelin.campaign.ColonyManager.buildIndustry(m, bureauId, true);
                    bureauCount++;
                    seededThisMarket = true;
                }

                if (seededThisMarket) marketCount++;
            }

            log.info("[Nex4x] IndustrySeeder seeded " + embassyCount + " embassy, "
                    + bureauCount + " bureau across " + marketCount + " markets");

        } catch (Exception e) {
            log.error("[Nex4x] IndustrySeeder.seedAll failed: " + e.getMessage(), e);
        }
    }
}
