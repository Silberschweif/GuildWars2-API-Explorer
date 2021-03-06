package de.goddchen.android.gw2.api.async;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import de.goddchen.android.gw2.api.Application;
import de.goddchen.android.gw2.api.data.Continent;
import de.goddchen.android.gw2.api.data.Floor;
import de.goddchen.android.gw2.api.data.Map;
import de.goddchen.android.gw2.api.data.POI;
import de.goddchen.android.gw2.api.data.Region;
import de.goddchen.android.gw2.api.data.SkillChallenge;
import de.goddchen.android.gw2.api.data.Task;

/**
 * Created by Goddchen on 21.06.13.
 */
public class FloorLoader extends FixedAsyncTaskLoader<Floor> {

    private Continent mContinent;

    private long mFloorId;

    public FloorLoader(Context context, Continent continent, long floorId) {
        super(context);
        mContinent = continent;
        mFloorId = floorId;
    }

    @Override
    public Floor loadInBackground() {
        try {
            Floor floor = Application.getDatabaseHelper().getFloorDao().queryForFirst(
                    Application.getDatabaseHelper().getFloorDao().queryBuilder().where().eq
                            ("continent_id", mContinent.id).and().eq("floor_id",
                            mFloorId).prepare());
            if (floor == null) {
                floor = new Floor();
                floor.floor_id = mFloorId;
                floor.continent_id = mContinent.id;
                Application.getDatabaseHelper().getFloorDao().create(floor);
                floor = Application.getDatabaseHelper().getFloorDao().queryForId(floor.id);
            }
            HttpsURLConnection connection =
                    (HttpsURLConnection) new URL("https://api.guildwars2.com/v1/map_floor.json?"
                            + "&continent_id=" + mContinent.id + "&floor=" + mFloorId
                            + "&lang=" + Locale.getDefault().getLanguage())
                            .openConnection();
            Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT).create();
            JSONObject jsonResponse =
                    new JSONObject(IOUtils.toString(connection.getInputStream()));
            Iterator<String> regionKeys = jsonResponse.getJSONObject("regions").keys();
            while (regionKeys.hasNext()) {
                String regionKey = regionKeys.next();
                JSONObject jsonRegion = jsonResponse.getJSONObject("regions")
                        .getJSONObject(regionKey);
                Region region = gson.fromJson(jsonRegion.toString(), Region.class);
                region.id = Long.parseLong(regionKey);
                region.label_coord_x = jsonRegion.getJSONArray("label_coord").getInt(0);
                region.label_coord_y = jsonRegion.getJSONArray("label_coord").getInt(1);
                if (!Application.getDatabaseHelper().getRegionDao().idExists(region.id)) {
                    floor.regions.add(region);
                } else {
                    region.floor = floor;
                    Application.getDatabaseHelper().getRegionDao().update(region);
                }
                region = Application.getDatabaseHelper().getRegionDao().queryForId(region.id);
                Iterator<String> mapKeys = jsonRegion.getJSONObject("maps").keys();
                while (mapKeys.hasNext()) {
                    String mapKey = mapKeys.next();
                    JSONObject jsonMap = jsonRegion.getJSONObject("maps").getJSONObject(mapKey);
                    Map map = gson.fromJson(jsonMap.toString(), Map.class);
                    map.id = Long.parseLong(mapKey);
                    map.continent_rect_x1 = jsonMap.getJSONArray("continent_rect").getJSONArray
                            (0).getInt(0);
                    map.continent_rect_y1 = jsonMap.getJSONArray("continent_rect").getJSONArray
                            (0).getInt(1);
                    map.continent_rect_x2 = jsonMap.getJSONArray("continent_rect").getJSONArray
                            (1).getInt(0);
                    map.continent_rect_y2 = jsonMap.getJSONArray("continent_rect").getJSONArray
                            (1).getInt(1);
                    map.map_rect_x1 = jsonMap.getJSONArray("map_rect").getJSONArray(0).getInt(0);
                    map.map_rect_y1 = jsonMap.getJSONArray("map_rect").getJSONArray(0).getInt(1);
                    map.map_rect_x2 = jsonMap.getJSONArray("map_rect").getJSONArray(1).getInt(0);
                    map.map_rect_y2 = jsonMap.getJSONArray("map_rect").getJSONArray(1).getInt(1);
                    if (Application.getDatabaseHelper().getMapDao().idExists(map.id)) {
                        map.region = region;
                        region.maps.update(map);
                    } else {
                        region.maps.add(map);
                    }
                    map = Application.getDatabaseHelper().getMapDao().queryForId(map.id);

                    parsePOIs(gson, map, jsonMap);
                    parseTasks(gson, map, jsonMap);
                    parseSkillChallenges(gson, map, jsonMap);
                    parseSectors(gson, map, jsonMap);
                }
            }
            return floor;
        } catch (Exception e) {
            Log.e(Application.Constants.LOG_TAG, "Error getting floor info", e);
            return null;
        }
    }

    private void parsePOIs(Gson gson, Map map, JSONObject mapJson) throws Exception {
        List<POI> pois = gson.fromJson(mapJson.getJSONArray("points_of_interest").toString(),
                new TypeToken<List<POI>>() {
                }.getType());
        for (POI poi : pois) {
            poi.map = map;
            poi.coord_x = poi.coord[0];
            poi.coord_y = poi.coord[1];
            if (Application.getDatabaseHelper().getPoiDao().idExists(poi.poi_id)) {
                map.pois.update(poi);
            } else {
                map.pois.add(poi);
            }
        }
    }

    private void parseSectors(Gson gson, Map map, JSONObject jsonMap) throws Exception {

    }

    private void parseTasks(Gson gson, Map map, JSONObject mapJson) throws Exception {
        List<Task> tasks = gson.fromJson(mapJson.getJSONArray("tasks").toString(),
                new TypeToken<List<Task>>() {
                }.getType());
        for (Task task : tasks) {
            task.map = map;
            task.coord_x = task.coord[0];
            task.coord_y = task.coord[1];
            if (Application.getDatabaseHelper().getTaskDao().idExists(task.task_id)) {
                map.tasks.update(task);
            } else {
                map.tasks.add(task);
            }
        }
    }

    private void parseSkillChallenges(Gson gson, Map map, JSONObject jsonMap) throws Exception {
        List<SkillChallenge> skillChallenges = gson.fromJson(jsonMap.getJSONArray
                ("skill_challenges").toString(),
                new TypeToken<List<SkillChallenge>>() {
                }.getType());
        for (SkillChallenge skillChallenge : skillChallenges) {
            skillChallenge.map = map;
            skillChallenge.coord_x = skillChallenge.coord[0];
            skillChallenge.coord_y = skillChallenge.coord[1];
            if (Application.getDatabaseHelper().getSkillChallengeDao().countOf(
                    Application.getDatabaseHelper().getSkillChallengeDao().queryBuilder()
                            .setCountOf(true)
                            .where().eq("map_id", map.id).and().eq("coord_x",
                            skillChallenge.coord_x).and().eq("coord_y",
                            skillChallenge.coord_y).prepare()) > 0) {
                map.skill_challenges.update(skillChallenge);
            } else {
                map.skill_challenges.add(skillChallenge);
            }
        }
    }
}
