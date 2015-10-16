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

package dk.siman.jive.model;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;

import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dk.siman.jive.provider.MusicDB;
import dk.siman.jive.provider.MusicDBDataSource;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;

/**
 * Utility class to get a list of MusicTrack's
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    // Categorized caches for music track data:
    private ConcurrentMap<String, MutableMediaMetadata> mMusicListById;
    private ConcurrentMap<String, List<MediaMetadata>> mMusicListByFavorite;
    private ConcurrentMap<String, List<MediaMetadata>> mMusicListByGenre;
    private ConcurrentMap<String, List<MediaMetadata>> mMusicListByArtist;
    private ConcurrentMap<String, List<MediaMetadata>> mMusicListByAlbum;
    private ConcurrentMap<String, List<MediaMetadata>> mMusicListByAlphabet;

    private final MusicDBDataSource datasource;

    private final ContentResolver mContentResolver;
    private final Context mContext;

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }

    public MusicProvider(ContentResolver cr, Context cn) {
        mMusicListByFavorite = new ConcurrentHashMap<>();
        mMusicListByGenre = new ConcurrentHashMap<>();
        mMusicListByArtist = new ConcurrentHashMap<>();
        mMusicListByAlbum = new ConcurrentHashMap<>();
        mMusicListByAlphabet = new ConcurrentHashMap<>();
        mMusicListById = new ConcurrentHashMap<>();
        mContentResolver = cr;
        mContext = cn;
        datasource = new MusicDBDataSource(mContext);
    }

    /**
     * Get an iterator over the list of favorites
     *
     * @return genres
     */
    public Iterable<String> getFavorites() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        buildListsByFavorites();
        return mMusicListByFavorite.keySet();
    }

    /**
     * Get an iterator over the list of genres
     *
     * @return genres
     */
    public Iterable<String> getGenres() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.keySet();
    }

    /**
     * Get an iterator over the list of artist
     *
     * @return artists
     */
    public Iterable<String> getArtists() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByArtist.keySet();
    }

    /**
     * Get an iterator over the list of albums
     *
     * @return genres
     */
    public Iterable<String> getAlbums() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByAlbum.keySet();
    }

    /**
     * Get an iterator over the list of music
     *
     * @return genres
     */
    public Iterable<String> getAllMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByAlphabet.keySet();
    }

    public Uri getAlbumArtUri(String mediaid) {
        for (MediaMetadata track : getMusicsByAlbum(mediaid)) {
            Uri bitmap = track.getDescription().getIconUri();

            if (ArtHelper.getAlbumArt(mContext,bitmap) != null) {
                return bitmap;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Get music tracks of favorites
     *
     */
    public Iterable<MediaMetadata> getMusicsByFavorite(String mediaid) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByFavorite.containsKey(mediaid)) {
            return Collections.emptyList();
        }
        return mMusicListByFavorite.get(mediaid);
    }

    /**
     * Get music tracks of the given genre
     *
     */
    public Iterable<MediaMetadata> getMusicsByGenre(String genre) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre.containsKey(genre)) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.get(genre);
    }

    /**
     * Get music tracks of the given artist
     *
     */
    public Iterable<MediaMetadata> getMusicsByArtist(String artist) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByArtist.containsKey(artist)) {
            return Collections.emptyList();
        }
        return mMusicListByArtist.get(artist);
    }

    /**
     * Get music tracks of the given album
     *
     */
    public Iterable<MediaMetadata> getMusicsByAlbum(String album) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByAlbum.containsKey(album)) {
            return Collections.emptyList();
        }
        return mMusicListByAlbum.get(album);
    }

    /**
     * Get music tracks of the given album
     *
     */
    public Iterable<MediaMetadata> getMusicsByAlphabet(String album) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByAlphabet.containsKey(album)) {
            return Collections.emptyList();
        }
        return mMusicListByAlphabet.get(album);
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public Iterable<MediaMetadata> searchMusicBySongTitle(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_TITLE, query);
    }

    public Iterable<MediaMetadata> searchMusicByAlbum(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_ALBUM, query);
    }

    public Iterable<MediaMetadata> searchMusicByArtist(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_ARTIST, query);
    }

    private Iterable<MediaMetadata> searchMusic(String metadataField, String query) {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        ArrayList<MediaMetadata> result = new ArrayList<>();
        query = query.toLowerCase(Locale.US);
        for (MutableMediaMetadata track : mMusicListById.values()) {
            if (track.metadata.getString(metadataField).toLowerCase(Locale.US)
                    .contains(query)) {
                result.add(track.metadata);
            }
        }
        return result;
    }


    /**
     * Return the MediaMetadata for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    public MediaMetadata getMusic(String musicId) {
        return mMusicListById.containsKey(musicId) ? mMusicListById.get(musicId).metadata : null;
    }

    public synchronized void updateMusic(String musicId, MediaMetadata metadata) {
        MutableMediaMetadata track = mMusicListById.get(musicId);
        if (track == null) {
            return;
        }

        String oldGenre = track.metadata.getString(MediaMetadata.METADATA_KEY_GENRE);
        String newGenre = metadata.getString(MediaMetadata.METADATA_KEY_GENRE);

        String oldAlbum = track.metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);

        String oldArtist = track.metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        track.metadata = metadata;

        // if genre has changed, we need to rebuild the list by genre
        if (!oldGenre.equals(newGenre)) {
            buildListsByGenre();
        }

        if (!oldAlbum.equals(newAlbum)) {
            buildListByAlbum();
        }

        if (!oldArtist.equals(newArtist)) {
            buildListsByArtist();
        }

        buildListsByFavorites();
        buildListByAlphabet();
    }

    public void setFavorite(String musicId, boolean favorite) {
        if (favorite) {
            datasource.open();
            datasource.addMusic(null, null, null, null, null, null, musicId, favorite);
            datasource.close();
        } else {
            datasource.deleteFavorite(musicId);
        }

        buildListsByFavorites();
    }

    public boolean isFavorite(String musicId) {
        boolean isFav = false;
        String isFavMusicId = datasource.getFavorite(musicId);

        if (isFavMusicId != null)
            isFav = true;

        return isFav;
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    public void retrieveMediaAsync(final Callback callback) {
        LogHelper.d(TAG, "retrieveMediaAsync called");
        if (mCurrentState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            callback.onMusicCatalogReady(true);
            return;
        }

        // Verify that all required storage permissions have been granted.
        if ((ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            // Storage permissions have not been granted.
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        new AsyncTask<Void, Void, State>() {
            @Override
            protected State doInBackground(Void... params) {
                retrieveMedia();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onMusicCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

    private synchronized void buildListsByFavorites() {
        ConcurrentMap<String, List<MediaMetadata>> newMusicListByFavorites = new ConcurrentHashMap<>();

        for (MusicDB musicDB : datasource.getAllFavorites()) {

            for (MutableMediaMetadata m : mMusicListById.values()) {
                String MediaId = musicDB.getMediaId();
                String trackId = m.metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                if (trackId.equals(MediaId)) {
                    List<MediaMetadata> list = newMusicListByFavorites.get(trackId);
                    if (list == null) {
                        list = new ArrayList<>();
                        newMusicListByFavorites.put(MediaId, list);
                    }
                    list.add(m.metadata);
                }
            }
        }
        mMusicListByFavorite = newMusicListByFavorites;
    }

    private synchronized void buildListsByGenre() {
        ConcurrentMap<String, List<MediaMetadata>> newMusicListByGenre = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String genre = m.metadata.getString(MediaMetadata.METADATA_KEY_GENRE);
            List<MediaMetadata> list = newMusicListByGenre.get(genre);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByGenre.put(genre, list);
            }
            list.add(m.metadata);
        }
        mMusicListByGenre = newMusicListByGenre;
    }

    private synchronized void buildListsByArtist() {
        ConcurrentMap<String, List<MediaMetadata>> newMusicListByArtist = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String artist = m.metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            List<MediaMetadata> list = newMusicListByArtist.get(artist);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByArtist.put(artist, list);
            }
            list.add(m.metadata);
        }
        mMusicListByArtist = newMusicListByArtist;
    }

    //TODO: sort by filename if track number does not exist
    private synchronized void buildListByAlbum() {
        ConcurrentMap<String, List<MediaMetadata>> newMusicListByAlbum = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String album = m.metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            List<MediaMetadata> list = newMusicListByAlbum.get(album);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByAlbum.put(album, list);
            }
            list.add(m.metadata);
            Collections.sort(list, new Comparator<MediaMetadata>() {
                @Override
                public int compare(MediaMetadata lhs, MediaMetadata rhs) {
                    Long no = lhs.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                    Long no2 = rhs.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                    return no.compareTo(no2);
                }
            });
        }
        mMusicListByAlbum = newMusicListByAlbum;
    }

    private synchronized void buildListByAlphabet() {
        ConcurrentMap<String, List<MediaMetadata>> newMusicListByAlbum = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String music = m.metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            List<MediaMetadata> list = newMusicListByAlbum.get(music);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByAlbum.put(music, list);
            }
            list.add(m.metadata);

            Collections.sort(list, Ordering.usingToString());
        }
        mMusicListByAlphabet = newMusicListByAlbum;
    }

    private synchronized void retrieveMedia() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING;

                Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                Uri genreUri = android.provider.MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;

                HashMap<String, String> genreIdToGenreNameMap = new HashMap<>();
                HashMap<String, String> songIdToGenreIdMap = new HashMap<>();

                LogHelper.i(TAG, "Querying media...");

                String GENRE_ID      = MediaStore.Audio.Genres._ID;
                String GENRE_NAME    = MediaStore.Audio.Genres.NAME;
                String SONG_ID       = android.provider.MediaStore.Audio.Media._ID;
                String SONG_TITLE    = android.provider.MediaStore.Audio.Media.TITLE;
                String SONG_ARTIST   = android.provider.MediaStore.Audio.Media.ARTIST;
                String SONG_ALBUM    = android.provider.MediaStore.Audio.Media.ALBUM;
                String SONG_ALBUM_ID = android.provider.MediaStore.Audio.Media.ALBUM_ID;
                String SONG_YEAR     = android.provider.MediaStore.Audio.Media.YEAR;
                String SONG_TRACK_NO = android.provider.MediaStore.Audio.Media.TRACK;
                String SONG_FILEPATH = android.provider.MediaStore.Audio.Media.DATA;
                String SONG_DURATION = android.provider.MediaStore.Audio.Media.DURATION;

                String[] genreColumns = {
                        GENRE_ID,
                        GENRE_NAME
                };

                Cursor cursor = mContentResolver.query(genreUri, genreColumns, null, null, null);
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    genreIdToGenreNameMap.put(cursor.getString(0), cursor.getString(1));
                }
                cursor.close();

                for (String genreID : genreIdToGenreNameMap.keySet()) {
                    Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external",
                            Long.parseLong(genreID));
                    cursor = mContentResolver.query(uri, new String[] { SONG_ID }, null, null, null);
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long currentSongID = cursor.getLong(cursor.getColumnIndex(SONG_ID));
                        songIdToGenreIdMap.put(Long.toString(currentSongID), genreID);
                    }
                    cursor.close();
                }
                String[] columns = {
                        SONG_ID,
                        SONG_TITLE,
                        SONG_ARTIST,
                        SONG_ALBUM,
                        SONG_ALBUM_ID,
                        SONG_YEAR,
                        SONG_TRACK_NO,
                        SONG_FILEPATH,
                        SONG_DURATION
                };

                final String musicsOnly = MediaStore.Audio.Media.IS_MUSIC + "=1";
                cursor = mContentResolver.query(musicUri, columns, musicsOnly, null, SONG_TRACK_NO);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        // Creating a song from the values on the row
                        Music music = new Music(cursor.getInt(cursor.getColumnIndex(SONG_ID)),
                                cursor.getString(cursor.getColumnIndex(SONG_FILEPATH)));

                        music.setSongId     (cursor.getString(cursor.getColumnIndex(SONG_ID)));
                        music.setTitle(cursor.getString(cursor.getColumnIndex(SONG_TITLE)));
                        music.setArtist(cursor.getString(cursor.getColumnIndex(SONG_ARTIST)));
                        music.setAlbum(cursor.getString(cursor.getColumnIndex(SONG_ALBUM)));
                        music.setAlbumId(cursor.getLong(cursor.getColumnIndex(SONG_ALBUM_ID)));
                        music.setAlbumArtist(cursor.getString(cursor.getColumnIndex(SONG_ARTIST)));
                        music.setYear       (cursor.getInt   (cursor.getColumnIndex(SONG_YEAR)));
                        music.setTrackNumber(cursor.getInt   (cursor.getColumnIndex(SONG_TRACK_NO)));
                        music.setDuration   (cursor.getInt   (cursor.getColumnIndex(SONG_DURATION)));

                        // Using the previously created genre maps
                        // to fill the current song genre.
                        String currentGenreID   = songIdToGenreIdMap.get(Long.toString(music.getId()));
                        String currentGenreName = genreIdToGenreNameMap.get(currentGenreID);
                        music.setGenre(currentGenreName);

                        MediaMetadata item = buildFromList(music);
                        String musicId = item.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                        mMusicListById.put(musicId, new MutableMediaMetadata(musicId, item));
                    }
                    while (cursor.moveToNext());
                }
                else {
                    LogHelper.e(TAG, "Could not retrieve music list");
                }
                if (cursor != null) {
                    cursor.close();
                }

                buildListsByGenre();
                buildListsByArtist();
                buildListByAlbum();
                buildListsByFavorites();
                buildListByAlphabet();

                LogHelper.i(TAG, "Done querying media. MusicProvider is ready.");
                mCurrentState = State.INITIALIZED;
            }
        } catch (Exception e) {
            LogHelper.e(TAG, e, "Could not retrieve music list");
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED;
            }
        }
    }

    private MediaMetadata buildFromList(Music music) {
        long albumId = music.getAlbumId();
        final Uri ART_CONTENT_URI = Uri.parse("content://media/external/audio/albumart");
        Uri albumArtUri = ContentUris.withAppendedId(ART_CONTENT_URI, albumId);

        String genre = music.getGenre();
        if (genre == null)
            genre = "Other";

        return new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, music.getTrackNumber())
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, music.getSongId())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, music.getAlbum())
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, music.getAlbumArtist())
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, music.getAlbumArtist())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, music.getArtist())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, music.getDuration())
                .putString(MediaMetadata.METADATA_KEY_GENRE, genre)
                .putString(MediaMetadata.METADATA_KEY_TITLE, music.getTitle())
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, albumArtUri.toString())
                .build();
    }
}
