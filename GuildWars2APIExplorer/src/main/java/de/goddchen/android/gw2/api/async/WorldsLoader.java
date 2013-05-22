package de.goddchen.android.gw2.api.async;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.goddchen.android.gw2.api.Application;
import de.goddchen.android.gw2.api.data.World;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

/**
 * Created by Goddchen on 22.05.13.
 */
public class WorldsLoader extends FixedAsyncTaskLoader<List<World>> {
    public WorldsLoader(Context context) {
        super(context);
    }

    @Override
    public List<World> loadInBackground() {
        try {
            HttpsURLConnection connection =
                    (HttpsURLConnection) new URL("https://api.guildwars2.com/v1/world_names.json").openConnection();
            List<World> worlds = new Gson().fromJson(new InputStreamReader(connection.getInputStream()),
                    new TypeToken<List<World>>() {
                    }.getType());
            for (World world : worlds) {
                Application.getDatabaseHelper().getWorldDao().delete(world);
                Application.getDatabaseHelper().getWorldDao().create(world);
            }
            return worlds;
        } catch (Exception e) {
            Log.e(Application.Constants.LOG_TAG, "Error loading worlds", e);
            return null;
        }
    }
}