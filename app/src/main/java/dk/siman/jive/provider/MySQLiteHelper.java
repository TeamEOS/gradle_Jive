/*
 * Copyright (c) 2015. Anders Nielsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package dk.siman.jive.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MySQLiteHelper extends SQLiteOpenHelper {

    public static final String TABLE_MUSICS = "music";
    public static final String _ID = "_id";
    public static final String MEDIA_ID = "media_id";
    public static final String ARTIST = "artist";
    public static final String TITLE = "title";
    public static final String ALBUM = "album";
    public static final String ALBUM_ID = "album_id";
    public static final String DURATION = "duration";
    private static final String MUSIC_PATH = "music_path";
    public static final String GENRE_NAME = "genre_name";
    public static final String IS_FAVORITE = "is_favorite";

    private static final String DATABASE_NAME = "musicdb.db";
    private static final int DATABASE_VERSION = 1;
    private static MySQLiteHelper mInstance = null;

    private static final String DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE_MUSICS
            + " (  "
            + _ID + " INTEGER primary key autoincrement,"
            + MEDIA_ID + " INTEGER, "
            + ARTIST + " TEXT, "
            + TITLE + " TEXT, "
            + ALBUM + " TEXT, "
            + ALBUM_ID + " TEXT, "
            + DURATION + " TEXT, "
            + MUSIC_PATH + " TEXT, "
            + GENRE_NAME + " TEXT, "
            + IS_FAVORITE + " TEXT)";

    private MySQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static MySQLiteHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MySQLiteHelper(context.getApplicationContext());
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

}
