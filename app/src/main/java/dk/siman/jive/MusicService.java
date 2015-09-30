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

package dk.siman.jive;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.audiofx.AudioEffect;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import android.support.v7.media.MediaRouter;
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dk.siman.jive.model.MusicProvider;
import dk.siman.jive.ui.NowPlayingActivity;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.CarHelper;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.MediaIDHelper;
import dk.siman.jive.utils.QueueHelper;
import dk.siman.jive.utils.WearHelper;

import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;
import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALPHABET;
import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST;
import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVORITE;
import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static dk.siman.jive.utils.MediaIDHelper.createBrowseCategoryMediaID;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 * To implement a MediaBrowserService, you need to:
 *
 * <ul>
 *
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 *      related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 *      {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 *      with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 *
 * <li> Set a callback on the
 *      {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 *      The callback will receive all the user's actions, like play, pause, etc;
 *
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 *      {@link android.media.MediaPlayer})
 *
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 *      {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 *      {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 *      {@link android.media.session.MediaSession#setQueue(java.util.List)})
 *
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 *      android.media.browse.MediaBrowserService
 *
 * </ul>
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 * <ul>
 *
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 *      with a &lt;automotiveApp&gt; root element. For a media app, this must include
 *      an &lt;uses name="media"/&gt; element as a child.
 *      For example, in AndroidManifest.xml:
 *          &lt;meta-data android:name="com.google.android.gms.car.application"
 *              android:resource="@xml/automotive_app_desc"/&gt;
 *      And in res/values/automotive_app_desc.xml:
 *          &lt;automotiveApp&gt;
 *              &lt;uses name="media"/&gt;
 *          &lt;/automotiveApp&gt;
 *
 * </ul>

 * @see <a href="README.md">README.md</a> for more details.
 *
 */
public class MusicService extends MediaBrowserService implements Playback.Callback {

    // Extra on MediaSession that contains the Cast device name currently connected to
    public static final String EXTRA_CONNECTED_CAST = "dk.siman.jive.CAST_NAME";
    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = "dk.siman.jive.ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that
    // should be executed (see {@link #onStartCommand})
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that
    // indicates that the music playback should be paused (see {@link #onStartCommand})
    public static final String CMD_PAUSE = "CMD_PAUSE";
    // A value of a CMD_NAME key that indicates that the music playback should switch
    // to local playback from cast playback.
    public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";

    public static int AUDIO_ID = 0;
    private static int mRepeatMode = 0;
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_ = 1;
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static MediaMetadata MEDIA_ID;

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);
    // Action to thumbs up a media item
    private static final String CUSTOM_ACTION_THUMBS_UP = "dk.siman.jive.THUMBS_UP";
    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    private static String cachedAlbum = null;

    // Music catalog manager
    private MusicProvider mMusicProvider;
    private MediaSession mSession;
    // "Now playing" queue:
    private List<MediaSession.QueueItem> mPlayingQueue;
    private int mCurrentIndexOnQueue;
    private MediaNotificationManager mMediaNotificationManager;

    // Indicates whether the service was started.
    private boolean mServiceStarted;
    private Bundle mSessionExtras;
    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private Playback mPlayback;
    private MediaRouter mMediaRouter;
    private PackageValidator mPackageValidator;
    private Context mContext;
    public static MusicService getService() {
        return new MusicService();
    }
    private boolean mIsConnectedToCar;
    private BroadcastReceiver mCarConnectionReceiver;

    /**
     * Consumer responsible for switching the Playback instances depending on whether
     * it is connected to a remote player.
     */
    private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

        @Override
        public void onApplicationConnected(ApplicationMetadata appMetadata, String sessionId,
                                           boolean wasLaunched) {
            LogHelper.d(TAG, "onApplicationConnected");
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras.putString(EXTRA_CONNECTED_CAST, mCastManager.getDeviceName());
            mSession.setExtras(mSessionExtras);
            // Now we can switch to CastPlayback
            Playback playback = new CastPlayback(mMusicProvider, getApplicationContext());
            mMediaRouter.setMediaSession(mSession);
            switchToPlayer(playback, true);
        }

        @Override
        public void onDisconnected() {
            LogHelper.d(TAG, "onDisconnected");
            mSessionExtras.remove(EXTRA_CONNECTED_CAST);
            mSession.setExtras(mSessionExtras);
            Playback playback = new LocalPlayback(MusicService.this, mMusicProvider, getApplicationContext());
            mMediaRouter.setMediaSession(null);
            switchToPlayer(playback, false);
        }
    };

    private VideoCastManager mCastManager;

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate");

        mContext = getApplicationContext();

        mPlayingQueue = new ArrayList<>();
        mMusicProvider = new MusicProvider(getContentResolver(), mContext);
        mPackageValidator = new PackageValidator(this);

        // Start a new MediaSession
        mSession = new MediaSession(this, "MusicService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(new MediaSessionCallback());
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mPlayback = new LocalPlayback(this, mMusicProvider, mContext);
        mPlayback.setState(PlaybackState.STATE_NONE);
        mPlayback.setCallback(this);
        mPlayback.start();

        Intent intent = new Intent(mContext, NowPlayingActivity.class);
        PendingIntent pi = PendingIntent.getActivity(mContext, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pi);

        mSessionExtras = new Bundle();
        CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
        WearHelper.setSlotReservationFlags(mSessionExtras, true, true);
        WearHelper.setUseBackgroundFromTheme(mSessionExtras, true);
        mSession.setExtras(mSessionExtras);

        updatePlaybackState(null);

        mMediaNotificationManager = new MediaNotificationManager(this);
        mCastManager = VideoCastManager.getInstance();
        mCastManager.addVideoCastConsumer(mCastConsumer);
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        IntentFilter filter = new IntentFilter(CarHelper.ACTION_MEDIA_STATUS);
        mCarConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS);
                mIsConnectedToCar = CarHelper.MEDIA_CONNECTED.equals(connectionEvent);
                LogHelper.i(TAG, "Connection event to Android Auto: ", connectionEvent,
                        " isConnectedToCar=", mIsConnectedToCar);
            }
        };
        registerReceiver(mCarConnectionReceiver, filter);
    }



    /**
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        LogHelper.d(TAG, "onStartCommand");
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    if (mPlayback != null && mPlayback.isPlaying()) {
                        handlePauseRequest();
                    }
                } else if (CMD_STOP_CASTING.equals(command)) {
                    mCastManager.disconnect();
                }
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        return START_NOT_STICKY;
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LogHelper.d(TAG, "onDestroy");
        unregisterReceiver(mCarConnectionReceiver);
        // Service is being killed, so make sure we release our resources
        handleStopRequest(null);

        try {
            NotificationManager mNotificationManager = (NotificationManager) this
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancelAll();
        } catch (Exception e) {
            LogHelper.d(TAG, "Exception removing notification: ", e);
        }

        stopEffect();

        mCastManager = VideoCastManager.getInstance();
        mCastManager.removeVideoCastConsumer(mCastConsumer);

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        // Always release the MediaSession to clean up resources
        // and notify associated MediaController(s).
        mSession.release();
    }

    private void startEffect() {
        LogHelper.d(TAG, "startEffect, AUDIO_ID: " + AUDIO_ID);

        final Intent intent = new Intent(
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        if (AUDIO_ID != 0) {
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AUDIO_ID);
        }
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getApplicationContext().getPackageName());
        sendBroadcast(intent);
    }

    private void stopEffect() {
        LogHelper.d(TAG, "stopEffect, AUDIO_ID: " + AUDIO_ID);
        final Intent intent = new Intent(
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (AUDIO_ID != 0) {
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AUDIO_ID);
        }
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getApplicationContext().getPackageName());
        sendBroadcast(intent);
    }

    public static int getRepeatMode() {
        LogHelper.d(TAG, "getRepeatMode: ", mRepeatMode);
        return mRepeatMode;
    }

    public void setRepeatMode(int mode) {
        LogHelper.d(TAG, "setRepeatMode: ", mode);
        mRepeatMode = mode;
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                "; clientUid=" + clientUid + " ; rootHints=", rootHints);
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return null. No further calls will
            // be made to other media browsing methods.
            LogHelper.w(TAG, "OnGetRoot: IGNORING request from untrusted package "
                    + clientPackageName);
            return null;
        }
        //noinspection StatementWithEmptyBody
        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library to show a different subset
            // when connected to the car, this is where you should handle it.
            // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
            // that should be different on cars, you should instead use the boolean flag
            // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
        }
        //noinspection StatementWithEmptyBody
        if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library for when browsing from a
            // Wear device, you should return a different MEDIA ROOT here, and then,
            // on onLoadChildren, handle it accordingly.
        }
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        if (!mMusicProvider.isInitialized()) {
            // Use result.detach to allow calling result.sendResult from another thread:
            result.detach();

            mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    if (success) {
                        loadChildrenImpl(parentMediaId, result);
                    } else {
                        updatePlaybackState(getString(R.string.error_no_metadata));
                        result.sendResult(Collections.<MediaItem>emptyList());
                    }
                }
            });

        } else {
            // If our music catalog is already loaded/cached, load them into result immediately
            loadChildrenImpl(parentMediaId, result);
        }
    }

    /**
     * Actual implementation of onLoadChildren that assumes that MusicProvider is already
     * initialized.
     */
    private void loadChildrenImpl(final String parentMediaId,
                                  final Result<List<MediaBrowser.MediaItem>> result) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);

        final List<MediaBrowser.MediaItem> mediaItems = new ArrayList<>();

        if (MEDIA_ID_ROOT.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ROOT");
            mediaItems.add(new MediaBrowser.MediaItem(
                    new MediaDescription.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_FAVORITE)
                            .setTitle(getString(R.string.browse_favorites))
                            .setIconUri(Uri.parse("android.resource://" +
                                    "dk.siman/drawable/ic_by_genre"))
                            .setSubtitle(getString(R.string.browse_favorite_subtitle))
                            .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
            ));

            mediaItems.add(new MediaBrowser.MediaItem(
                    new MediaDescription.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_GENRE)
                            .setTitle(getString(R.string.browse_genres))
                            .setIconUri(Uri.parse("android.resource://" +
                                    "dk.siman/drawable/ic_by_genre"))
                            .setSubtitle(getString(R.string.browse_genre_subtitle))
                            .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
            ));

            mediaItems.add(new MediaBrowser.MediaItem(
                    new MediaDescription.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_ARTIST)
                            .setTitle(getString(R.string.browse_artists))
                            .setIconUri(Uri.parse("android.resource://" +
                                    "dk.siman/drawable/ic_by_genre"))
                            .setSubtitle(getString(R.string.browse_artists_subtitle))
                            .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
            ));

            mediaItems.add(new MediaBrowser.MediaItem(
                    new MediaDescription.Builder()
                            .setMediaId(MEDIA_ID_MUSICS_BY_ALBUM)
                            .setTitle(getString(R.string.browse_albums))
                            .setIconUri(Uri.parse("android.resource://" +
                                    "dk.siman/drawable/ic_by_genre"))
                            .setSubtitle(getString(R.string.browse_album_subtitle))
                            .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
            ));

            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem lhs, MediaItem rhs) {
                    return lhs.toString().compareToIgnoreCase(rhs.toString());
                }
            });

        } else if (MEDIA_ID_MUSICS_BY_FAVORITE.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.FAVORITE");

            for (String mediaId : mMusicProvider.getFavorites()) {
                for (MediaMetadata track : mMusicProvider.getMusicsByFavorite(mediaId)) {
                    String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                            track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_FAVORITE, track.toString());
                    MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                            .build();
                    MediaBrowser.MediaItem bItem = new MediaBrowser.MediaItem(
                            trackCopy.getDescription(), MediaItem.FLAG_PLAYABLE);

                    mediaItems.add(bItem);
                }
            }

            if (mediaItems.size() == 0) {
                Toast.makeText(mContext, "No favorites songs", Toast.LENGTH_LONG).show();
                result.detach();
                return;
            } else {
                Collections.sort(mediaItems, new Comparator<MediaItem>() {
                    @Override
                    public int compare(MediaItem lhs, MediaItem rhs) {
                        return lhs.toString().compareToIgnoreCase(rhs.toString());
                    }
                });
            }

        } else if (MEDIA_ID_MUSICS_BY_GENRE.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.GENRES");
            for (String genre : mMusicProvider.getGenres()) {
                MediaBrowser.MediaItem item = new MediaBrowser.MediaItem(
                        new MediaDescription.Builder()
                                .setMediaId(createBrowseCategoryMediaID(MEDIA_ID_MUSICS_BY_GENRE, genre))
                                .setTitle(genre)
                                .setSubtitle(getString(R.string.browse_musics_by_genre_subtitle, genre))
                                .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
                );
                mediaItems.add(item);
            }

            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem lhs, MediaItem rhs) {
                    return lhs.toString().compareToIgnoreCase(rhs.toString());
                }
            });

        } else if (MEDIA_ID_MUSICS_BY_ARTIST.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ARTISTS");
            for (String artist : mMusicProvider.getArtists()) {
                MediaBrowser.MediaItem item = new MediaBrowser.MediaItem(
                        new MediaDescription.Builder()
                                .setMediaId(createBrowseCategoryMediaID(MEDIA_ID_MUSICS_BY_ARTIST, artist))
                                .setTitle(artist)
                                .setSubtitle(getString(R.string.browse_musics_by_artists_subtitle, artist))
                                .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
                );
                mediaItems.add(item);
            }

            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem lhs, MediaItem rhs) {
                    return lhs.toString().compareToIgnoreCase(rhs.toString());
                }
            });

        } else if (MEDIA_ID_MUSICS_BY_ALPHABET.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ALPHABET");
            for (String mediaId : mMusicProvider.getAllMusic()) {
                for (MediaMetadata track : mMusicProvider.getMusicsByAlphabet(mediaId)) {
                    String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                            track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_ALPHABET, track.toString());
                    MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                            .build();
                    MediaBrowser.MediaItem bItem = new MediaBrowser.MediaItem(
                            trackCopy.getDescription(), MediaItem.FLAG_PLAYABLE);

                    mediaItems.add(bItem);
                }
            }

            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem lhs, MediaItem rhs) {
                    return lhs.toString().compareToIgnoreCase(rhs.toString());
                }
            });

        } else if (MEDIA_ID_MUSICS_BY_ALBUM.equals(parentMediaId)) {
            LogHelper.d(TAG, "OnLoadChildren.ALBUMS");
            for (String albums : mMusicProvider.getAlbums()) {
                Uri iconUri = mMusicProvider.getAlbumArtUri(albums);
                MediaBrowser.MediaItem item = null;
                for (MediaMetadata track : mMusicProvider.getMusicsByAlbum(albums)) {

                    item = new MediaBrowser.MediaItem(
                            new MediaDescription.Builder()
                                    .setMediaId(createBrowseCategoryMediaID(MEDIA_ID_MUSICS_BY_ALBUM, albums))
                                    .setTitle(albums)
                                    .setIconUri(iconUri)
                                    .setSubtitle(getString(R.string.browse_musics_by_album_subtitle, track.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)))
                                    .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
                    );
                }
                mediaItems.add(item);
            }

            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem lhs, MediaItem rhs) {
                    return lhs.toString().compareToIgnoreCase(rhs.toString());
                }
            });

        } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {
            String genre = MediaIDHelper.getHierarchy(parentMediaId)[1];
            LogHelper.d(TAG, "OnLoadChildren.SONGS_BY_GENRE  genre=", genre);
            for (MediaMetadata track : mMusicProvider.getMusicsByGenre(genre)) {
                // Since mediaMetadata fields are immutable, we need to create a copy, so we
                // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
                // when we get a onPlayFromMusicID call, so we can create the proper queue based
                // on where the music was selected from (by artist, by genre, random, etc)
                String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                        track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_GENRE, genre);
                MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                        .build();
                MediaBrowser.MediaItem bItem = new MediaBrowser.MediaItem(
                        trackCopy.getDescription(), MediaItem.FLAG_PLAYABLE);
                mediaItems.add(bItem);
            }

        } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_ARTIST)) {
            String artist = MediaIDHelper.getHierarchy(parentMediaId)[1];
            LogHelper.d(TAG, "OnLoadChildren.SONGS_BY_ARTISTS  artist=", artist);
            for (MediaMetadata track : mMusicProvider.getMusicsByArtist(artist)) {
                // Since mediaMetadata fields are immutable, we need to create a copy, so we
                // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
                // when we get a onPlayFromMusicID call, so we can create the proper queue based
                // on where the music was selected from (by artist, by genre, random, etc)
                String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                        track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_ARTIST, artist);
                MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                        .build();
                MediaBrowser.MediaItem bItem = new MediaBrowser.MediaItem(
                        trackCopy.getDescription(), MediaItem.FLAG_PLAYABLE);
                mediaItems.add(bItem);
            }

        } else if (parentMediaId.startsWith(MEDIA_ID_MUSICS_BY_ALBUM)) {
            String album = MediaIDHelper.getHierarchy(parentMediaId)[1];
            LogHelper.d(TAG, "OnLoadChildren.SONGS_BY_ALBUM  album=", album);

            for (MediaMetadata track : mMusicProvider.getMusicsByAlbum(album)) {
                Long trackNo = track.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                // Since mediaMetadata fields are immutable, we need to create a copy, so we
                // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
                // when we get a onPlayFromMusicID call, so we can create the proper queue based
                // on where the music was selected from (by artist, by genre, random, etc)
                String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                        track.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_ALBUM, album);
                MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                        .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, trackNo)
                        .build();

                MediaBrowser.MediaItem bItem = new MediaBrowser.MediaItem(
                        trackCopy.getDescription(), MediaItem.FLAG_PLAYABLE);
                mediaItems.add(bItem);
            }

        } else {
            LogHelper.w(TAG, "Skipping unmatched parentMediaId: ", parentMediaId);
        }
        LogHelper.d(TAG, "OnLoadChildren sending ", mediaItems.size(),
                " results for ", parentMediaId);

        result.sendResult(mediaItems);
    }

    private final class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            LogHelper.d(TAG, "play");

            if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
                mPlayingQueue = QueueHelper.getRandomQueue(mMusicProvider);
                mSession.setQueue(mPlayingQueue);
                mSession.setQueueTitle(getString(R.string.random_queue_title));
                // start playing from the beginning of the queue
                mCurrentIndexOnQueue = 0;
            }

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                handlePlayRequest();
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId);

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the music Id:
                mCurrentIndexOnQueue = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId);
                // play the music
                handlePlayRequest();
            }
        }

        @Override
        public void onSeekTo(long position) {
            LogHelper.d(TAG, "onSeekTo:", position);
            mPlayback.seekTo((int) position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras);

            updateNotification(mediaId);

            // The mediaId used here is not the unique musicId. This one comes from the
            // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
            // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
            // so we can build the correct playing queue, based on where the track was
            // selected from.
            mPlayingQueue = QueueHelper.getPlayingQueue(mediaId, mMusicProvider);
            mSession.setQueue(mPlayingQueue);
            String queueTitle = getString(R.string.browse_musics_by_genre_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId));
            mSession.setQueueTitle(queueTitle);

            if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                // set the current index on queue from the media Id:
                mCurrentIndexOnQueue = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);

                if (mCurrentIndexOnQueue < 0) {
                    LogHelper.e(TAG, "playFromMediaId: media ID ", mediaId,
                            " could not be found on queue. Ignoring.");
                } else {
                    // play the music
                    handlePlayRequest();
                }
            }
        }

        @Override
        public void onPause() {
            LogHelper.d(TAG, "pause. current state=" + mPlayback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            LogHelper.d(TAG, "stop. current state=" + mPlayback.getState());
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            LogHelper.d(TAG, "skipToNext");
            if (mRepeatMode == REPEAT_CURRENT) {
                // Don't do anything
            } else if (mRepeatMode != REPEAT_CURRENT) {
                mCurrentIndexOnQueue++;
            }
            if (mPlayingQueue != null && mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                // This sample's behavior: skipping to next when in last song returns to the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                LogHelper.e(TAG, "skipToNext: cannot skip to next. next Index=" +
                        mCurrentIndexOnQueue + " queue length=" +
                        (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onSkipToPrevious() {
            LogHelper.d(TAG, "skipToPrevious");
            if (mRepeatMode == REPEAT_CURRENT) {
                // Don't do anything
            } else if (mRepeatMode != REPEAT_CURRENT) {
                mCurrentIndexOnQueue--;
            }
            if (mPlayingQueue != null && mCurrentIndexOnQueue < 0) {
                // This sample's behavior: skipping to previous when in first song restarts the
                // first song.
                mCurrentIndexOnQueue = 0;
            }
            if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                handlePlayRequest();
            } else {
                LogHelper.e(TAG, "skipToPrevious: cannot skip to previous. previous Index=" +
                        mCurrentIndexOnQueue + " queue length=" +
                        (mPlayingQueue == null ? "null" : mPlayingQueue.size()));
                handleStopRequest("Cannot skip");
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (CUSTOM_ACTION_THUMBS_UP.equals(action)) {
                LogHelper.i(TAG, "onCustomAction: favorite for current track");
                MediaMetadata track = getCurrentPlayingMusic();
                if (track != null) {
                    String musicId = track.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                    mMusicProvider.setFavorite(musicId, !mMusicProvider.isFavorite(musicId));
                }
                // playback state needs to be updated because the "Favorite" icon on the
                // custom action will change to reflect the new favorite state.
                updatePlaybackState(null);
            } else {
                LogHelper.e(TAG, "Unsupported action: ", action);
            }
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras);

            mPlayback.setState(PlaybackState.STATE_CONNECTING);

            // Voice searches may occur before the media catalog has been
            // prepared. We only handle the search after the musicProvider is ready.
            mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    mPlayingQueue = QueueHelper.getPlayingQueueFromSearch(query, extras,
                            mMusicProvider);

                    LogHelper.d(TAG, "playFromSearch  playqueue.length=" + mPlayingQueue.size());
                    mSession.setQueue(mPlayingQueue);

                    if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
                        // immediately start playing from the beginning of the search results
                        mCurrentIndexOnQueue = 0;

                        handlePlayRequest();
                    } else {
                        // if nothing was found, we need to warn the user and stop playing
                        handleStopRequest(getString(R.string.no_search_results));
                    }
                }
            });
        }
    }

    /**
     * Handle a request to play music
     */
    private void handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + mPlayback.getState());

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        if (!mServiceStarted) {
            LogHelper.v(TAG, "Starting service");
            // The MusicService needs to keep running even after the calling MediaBrowser
            // is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
            // need to play media.
            startService(new Intent(mContext, MusicService.class));
            mServiceStarted = true;
        }

        if (!mSession.isActive()) {
            mSession.setActive(true);
        }

        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            updateMetadata();
            mPlayback.play(mPlayingQueue.get(mCurrentIndexOnQueue));
        }

        startEffect();
    }

    /**
     * Handle a request to pause music
     */
    private void handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + mPlayback.getState());
        mPlayback.pause();
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
    }

    /**
     * Handle a request to stop music
     */
    private void handleStopRequest(String withError) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + mPlayback.getState() + " error=", withError);
        mPlayback.stop(true);

        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        updatePlaybackState(withError);

        // service is no longer necessary. Will be started again if needed.
        stopSelf();
        mServiceStarted = false;
    }

    public Bitmap getAlbumArt(Uri uri) {
        return ArtHelper.getAlbumArt(mContext, uri);
    }

    private void updateMetadata() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            LogHelper.e(TAG, "Can't retrieve current metadata.");
            updatePlaybackState(getResources().getString(R.string.error_no_metadata));
            return;
        }
        MediaSession.QueueItem queueItem = mPlayingQueue.get(mCurrentIndexOnQueue);
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                queueItem.getDescription().getMediaId());
        MediaMetadata track = mMusicProvider.getMusic(musicId);
        final String trackId = track.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (!musicId.equals(trackId)) {
            IllegalStateException e = new IllegalStateException("track ID should match musicId.");
            LogHelper.e(TAG, "track ID should match musicId.",
                    " musicId=", musicId, " trackId=", trackId,
                    " mediaId from queueItem=", queueItem.getDescription().getMediaId(),
                    " title from queueItem=", queueItem.getDescription().getTitle(),
                    " mediaId from track=", track.getDescription().getMediaId(),
                    " title from track=", track.getDescription().getTitle(),
                    e);
            throw e;
        }
        LogHelper.d(TAG, "Updating metadata for MusicID= " + musicId);
        mSession.setMetadata(track);

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        if (track.getDescription().getIconBitmap() == null &&
                track.getDescription().getIconUri() != null) {
            String albumString = track.getDescription().getIconUri().toString();
            Uri albumUri = Uri.parse(albumString);
            LogHelper.d(TAG, "Album art uri: " + albumUri);

            String curAlbum = track.getString(MediaMetadata.METADATA_KEY_ALBUM);
            if (curAlbum != null && cachedAlbum != null && curAlbum.equalsIgnoreCase(cachedAlbum)) {
                LogHelper.i(TAG, "Same album, don't change cover");
            } else {

                Bitmap bm = ArtHelper.getAlbumArt(mContext, albumUri);
                if (bm == null) {
                    bm = BitmapFactory.decodeResource(this.getResources(),
                            R.drawable.ic_default_art);
                }
                if (bm != null) {
                    Bitmap bitmap = ArtHelper.scaleBitmap(bm);
                    Bitmap icon = ArtHelper.scaleBitmapIcon(bm);
                    LogHelper.d(TAG, "album art icon of w=", icon.getWidth(),
                            " h=", icon.getHeight());

                    track = new MediaMetadata.Builder(track)
                            // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                            // example, on the lockscreen background when the media session is active.
                            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                                    // set small version of the album art in the DISPLAY_ICON. This is used on
                                    // the MediaDescription and thus it should be small to be serialized if
                                    // necessary..
                            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icon)
                            .build();
                }

                cachedAlbum = track.getString(MediaMetadata.METADATA_KEY_ALBUM);
            }

            mMusicProvider.updateMusic(trackId, track);

            // If we are still playing the same music
            String currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(
                    queueItem.getDescription().getMediaId());
            if (trackId.equals(currentPlayingId)) {
                mSession.setMetadata(track);
            }
        }
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    private void updatePlaybackState(String error) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + mPlayback.getState());
        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null && mPlayback.isConnected()) {
            position = mPlayback.getCurrentStreamPosition();
        }

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());

        setCustomAction(stateBuilder);
        int state = mPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error);
            state = PlaybackState.STATE_ERROR;
        }
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSession.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
            stateBuilder.setActiveQueueItemId(item.getQueueId());
        }

        mSession.setPlaybackState(stateBuilder.build());

        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED) {
            mMediaNotificationManager.startNotification();
        }
    }

    private void setCustomAction(PlaybackState.Builder stateBuilder) {
        MediaMetadata currentMusic = getCurrentPlayingMusic();
        if (currentMusic != null) {
            // Set appropriate "Favorite" icon on Custom action:
            String musicId = currentMusic.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            int favoriteIcon = R.drawable.ic_star_off;
            if (mMusicProvider.isFavorite(musicId)) {
                favoriteIcon = R.drawable.ic_star_on;
            }
            LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                    musicId, " current favorite=", mMusicProvider.isFavorite(musicId));
            Bundle customActionExtras = new Bundle();
            WearHelper.setShowCustomActionOnWear(customActionExtras, true);
            stateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
                    CUSTOM_ACTION_THUMBS_UP, getString(R.string.browse_favorites), favoriteIcon)
                    .setExtras(customActionExtras)
                    .build());
        }
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackState.ACTION_PLAY_FROM_SEARCH;
        if (mPlayingQueue == null || mPlayingQueue.isEmpty()) {
            return actions;
        }
        if (mPlayback.isPlaying()) {
            actions |= PlaybackState.ACTION_PAUSE;
        }
        if (mCurrentIndexOnQueue > 0) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mCurrentIndexOnQueue < mPlayingQueue.size() - 1) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        if (mRepeatMode != REPEAT_NONE) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;

        }
        return actions;
    }

    private MediaMetadata getCurrentPlayingMusic() {
        if (QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
            MediaSession.QueueItem item = mPlayingQueue.get(mCurrentIndexOnQueue);
            if (item != null) {
                LogHelper.d(TAG, "getCurrentPlayingMusic for musicId=",
                        item.getDescription().getMediaId());
                MEDIA_ID = mMusicProvider.getMusic(
                        MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()));
                return MEDIA_ID;
            }
        }
        return null;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        LogHelper.d(TAG, "onCompletion");
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mPlayingQueue != null && !mPlayingQueue.isEmpty()) {
            // In this sample, we restart the playing queue when it gets to the end:
            if (mRepeatMode != REPEAT_CURRENT) {
                mCurrentIndexOnQueue++;
                if (mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                    handleStopRequest(null);
                }
            }

            if (mRepeatMode == REPEAT_ALL) {
                if (mCurrentIndexOnQueue >= mPlayingQueue.size()) {
                    mCurrentIndexOnQueue = 0;
                }
            }

            handlePlayRequest();
        } else {
            // If there is nothing to play, we stop and release the resources:
            handleStopRequest(null);
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackState(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    @Override
    public void onMetadataChanged(String mediaId) {
        LogHelper.d(TAG, "onMetadataChanged", mediaId);
        List<MediaSession.QueueItem> queue = QueueHelper.getPlayingQueue(mediaId, mMusicProvider);
        int index = QueueHelper.getMusicIndexOnQueue(queue, mediaId);
        if (index > -1) {
            mCurrentIndexOnQueue = index;
            mPlayingQueue = queue;
            updateMetadata();
        }
    }

    /**
     * Helper to switch to a different Playback instance
     * @param playback switch to this playback
     */
    private void switchToPlayer(Playback playback, boolean resumePlaying) {
        if (playback == null) {
            throw new IllegalArgumentException("Playback cannot be null");
        }
        // suspend the current one.
        int oldState = mPlayback.getState();
        int pos = mPlayback.getCurrentStreamPosition();
        String currentMediaId = mPlayback.getCurrentMediaId();
        LogHelper.d(TAG, "Current position from " + playback + " is ", pos);
        mPlayback.stop(false);
        playback.setCallback(this);
        playback.setCurrentStreamPosition(pos < 0 ? 0 : pos);
        playback.setCurrentMediaId(currentMediaId);
        playback.start();
        // finally swap the instance
        mPlayback = playback;
        switch (oldState) {
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_PAUSED:
                mPlayback.pause();
                break;
            case PlaybackState.STATE_PLAYING:
                if (resumePlaying && QueueHelper.isIndexPlayable(mCurrentIndexOnQueue, mPlayingQueue)) {
                    mPlayback.play(mPlayingQueue.get(mCurrentIndexOnQueue));
                } else if (!resumePlaying) {
                    mPlayback.pause();
                } else {
                    mPlayback.stop(true);
                }
                break;
            case PlaybackState.STATE_NONE:
                break;
            default:
                LogHelper.d(TAG, "Default called. Old state is ", oldState);
        }
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mPlayback != null) {
                if (service.mPlayback.isPlaying()) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                if (MediaNotificationManager.checkNotification().equals("true")) {
                    LogHelper.d(TAG, "Notification is shown");
                    return;
                }
                LogHelper.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
                service.mServiceStarted = false;
            }
        }
    }

    private void updateNotification(String mediaId){
        LogHelper.d(TAG, "updateNotification");
        MediaMetadata track = getCurrentPlayingMusic();
        if (track != null) {
            String curAlbum = track.getString(MediaMetadata.METADATA_KEY_ALBUM);
            LogHelper.d(TAG, "current album=", curAlbum);
            String nextAlbum = getString(R.string.browse_musics_by_album_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId));
            LogHelper.d(TAG, "next album=", nextAlbum);
            if (!curAlbum.equals(nextAlbum)) {
                LogHelper.d(TAG, "current album not the same as next album");
                mMediaNotificationManager.stopNotification();
            }
        }
    }
}
