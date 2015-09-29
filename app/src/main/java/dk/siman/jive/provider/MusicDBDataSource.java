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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import dk.siman.jive.utils.LogHelper;

public class MusicDBDataSource {

    private static final String TAG = LogHelper.makeLogTag(MusicDBDataSource.class);

    // Database fields
    private SQLiteDatabase database;
    private final MySQLiteHelper dbHelper;
    private final String[] allColumns = {
            MySQLiteHelper._ID,
            MySQLiteHelper.ARTIST,
            MySQLiteHelper.TITLE,
            MySQLiteHelper.ALBUM,
            MySQLiteHelper.ALBUM_ID,
            MySQLiteHelper.DURATION,
            MySQLiteHelper.GENRE_NAME,
            MySQLiteHelper.MEDIA_ID,
            MySQLiteHelper.IS_FAVORITE};

    public MusicDBDataSource(Context context) {
        dbHelper = MySQLiteHelper.getInstance(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }


    public MusicDB addMusic(String artist, String title, String album, Long albumId, Long duration, String genreName, String mediaId, Boolean isFavorite) {
        ContentValues values = new ContentValues();

        if (artist != null)
            values.put(MySQLiteHelper.ARTIST, artist);
        if (title != null)
            values.put(MySQLiteHelper.TITLE, title);
        if (album != null)
            values.put(MySQLiteHelper.ALBUM, album);
        if (albumId != null)
            values.put(MySQLiteHelper.ALBUM_ID, albumId);
        if (duration != null)
            values.put(MySQLiteHelper.DURATION, duration);
        if (genreName != null)
            values.put(MySQLiteHelper.GENRE_NAME, genreName);
        if (mediaId != null)
            values.put(MySQLiteHelper.MEDIA_ID, mediaId);
        if (isFavorite != null)
            values.put(MySQLiteHelper.IS_FAVORITE, isFavorite);

        if (values != null) {
            long insertId = database.insert(MySQLiteHelper.TABLE_MUSICS, null, values);
            Cursor cursor = database.query(
                    MySQLiteHelper.TABLE_MUSICS,
                    allColumns,
                    MySQLiteHelper._ID + " = " + insertId,
                    null,
                    null,
                    null,
                    null);

            cursor.moveToFirst();
            MusicDB newComment = cursorToComment(cursor);
            cursor.close();
            return newComment;
        }
        return null;
    }

    // Deleting single contact
    public void deleteFavorite(String mediaID) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        db.delete(MySQLiteHelper.TABLE_MUSICS,
                MySQLiteHelper.MEDIA_ID + " = ?",
                new String[] { String.valueOf(mediaID) });

        db.close();
    }


    // Getting single favorite
    public String getFavorite(String mediaID) {
        String isFavorite;

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(MySQLiteHelper.TABLE_MUSICS,
                new String[] { MySQLiteHelper._ID, MySQLiteHelper.MEDIA_ID, MySQLiteHelper.IS_FAVORITE },
                MySQLiteHelper.MEDIA_ID + "=?",
                new String[] { String.valueOf(mediaID) },
                null, null, null, null);

        if (cursor == null) {
            // Query failed...
            LogHelper.e(TAG, "Failed to retrieve music: cursor is null :-(");
            return null;
        }
        if (!cursor.moveToFirst()) {
            // Nothing to query. There is no music on the device. How boring.
            //LogHelper.e(TAG, "Failed to move cursor to first row (no query results).");
            return null;
        }

        do {
            isFavorite = cursor.getString(1);
        } while (cursor.moveToNext());

        cursor.close();

        // return contact
        return isFavorite;
    }

    // Getting All favorites
    public List<MusicDB> getAllFavorites() {
        List<MusicDB> favoriteList = new ArrayList<>();
        // Select All Query
        String selectQuery = "SELECT  * FROM " + MySQLiteHelper.TABLE_MUSICS;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                MusicDB music = new MusicDB();
                music.setMediaId(cursor.getString(1));
                // Adding favorite to list
                favoriteList.add(music);
            } while (cursor.moveToNext());
        }

        cursor.close();
        // return favorite list
        return favoriteList;
    }

    public MusicDB updateMusic(String artist, String title, String album, Long albumId, Long duration, String genreName, String mediaId) {
        ContentValues values = new ContentValues();

        values.put(MySQLiteHelper.ARTIST, artist);
        values.put(MySQLiteHelper.TITLE, title);
        values.put(MySQLiteHelper.ALBUM, album);
        values.put(MySQLiteHelper.ALBUM_ID, albumId);
        values.put(MySQLiteHelper.DURATION, duration);
        values.put(MySQLiteHelper.GENRE_NAME, genreName);
        values.put(MySQLiteHelper.MEDIA_ID, mediaId);

        String selection = MySQLiteHelper.MEDIA_ID + "=" + mediaId;

        Cursor c = database.rawQuery("SELECT * FROM " + MySQLiteHelper.TABLE_MUSICS+ " WHERE " + MySQLiteHelper.MEDIA_ID + "= '" + mediaId + "'", null);

        boolean exists = c.moveToFirst();
        if(!exists) {
            LogHelper.d(TAG, "exists = " + exists);
            database.insert(MySQLiteHelper.TABLE_MUSICS, null, values);
        } else {
            LogHelper.d(TAG, "exists = " + exists);
            database.update(MySQLiteHelper.TABLE_MUSICS, values, selection, null);
        }

        c.close();
        return null;
    }

    private MusicDB cursorToComment(Cursor cursor) {
      MusicDB comment = new MusicDB();
    comment.setId(cursor.getInt(0));
    comment.setComment(cursor.getString(1));
    return comment;
  }
} 
