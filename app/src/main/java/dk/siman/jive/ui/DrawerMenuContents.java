/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.siman.jive.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dk.siman.jive.R;

class DrawerMenuContents {
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";

    private final ArrayList<Map<String, ?>> items;
    private final Class[] activities;

    public DrawerMenuContents(Context ctx) {
        activities = new Class[7];
        items = new ArrayList<>(7);

        activities[0] = AlbumPlayerActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_albums_title),
                R.drawable.ic_allmusic_black_24dp));

        activities[1] = ArtistPlayerActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_artists_title),
                R.drawable.ic_allmusic_black_24dp));

        activities[2] = FavoritesPlayerActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_favorites_title),
                R.drawable.ic_allmusic_black_24dp));

        activities[3] = GenresPlayerActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_genres_title),
                R.drawable.ic_allmusic_black_24dp));

        activities[4] = AllPlayerActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_allmusic_title),
                R.drawable.ic_allmusic_black_24dp));

        activities[5] = SettingsActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_settings_title),
                R.drawable.ic_setting_dark));

        activities[6] = AboutActivity.class;
        items.add(populateDrawerItem(ctx.getString(R.string.drawer_about_title),
                R.drawable.ic_about_outline_black_24dp));
    }

    public List<Map<String, ?>> getItems() {
        return items;
    }

    public Class getActivity(int position) {
        return activities[position];
    }

    public int getPosition(Class activityClass) {
        for (int i=0; i<activities.length; i++) {
            if (activities[i].equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, ?> populateDrawerItem(String title, int icon) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, title);
        item.put(FIELD_ICON, icon);
        return item;
    }
}
