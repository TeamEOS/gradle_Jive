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

import android.content.ContentUris;
import android.content.Context;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import dk.siman.jive.httpd.CastFileServer;
import dk.siman.jive.model.MusicProvider;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.MediaIDHelper;
import dk.siman.jive.utils.UriUtil;

import static android.media.session.MediaSession.QueueItem;

/**
 * An implementation of Playback that talks to Cast.
 */
public class CastPlayback implements Playback {

    private static final String TAG = LogHelper.makeLogTag(CastPlayback.class);

    private static final String MIME_TYPE_AUDIO_MPEG = "audio/mpeg";
    private static final String ITEM_ID = "itemId";

    private static String imagePath;
    private static String mediaPath;
    private CastFileServer mFileServer;
    private final Context mContext;
    private final MusicProvider mMusicProvider;
    private final WifiManager.WifiLock mWifiLock;
    private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

        @Override
        public void onVolumeChanged(double volume, boolean isMute) {
            try {
                LogHelper.d(TAG, "onVolumeChanged: ", mCastManager.getVolume());
            } catch (TransientNetworkDisconnectionException e) {
                e.printStackTrace();
            } catch (NoConnectionException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onRemoteMediaPlayerMetadataUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerMetadataUpdated");
            updateMetadata();
        }

        @Override
        public void onRemoteMediaPlayerStatusUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated");
            updatePlaybackState();
        }
    };

    /** The current PlaybackState*/
    private int mState;
    /** Callback for making completion/error calls on */
    private Callback mCallback;
    private VideoCastManager mCastManager;
    private volatile int mCurrentPosition;
    private volatile String mCurrentMediaId;

    public CastPlayback(MusicProvider musicProvider, Context context) {
        mContext = context;
        this.mMusicProvider = musicProvider;
        this.mWifiLock = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "Jive_lock");
    }

    @Override
    public void start() {
        mCastManager = VideoCastManager.getInstance();
        mCastManager.addVideoCastConsumer(mCastConsumer);
        startFileServer();
        // Since we are casting media from the device, we want to hold a
        // Wifi lock, which prevents the Wifi radio from going to
        // sleep while the song is playing.
        mWifiLock.acquire();
    }

    @Override
    public void stop(boolean notifyListeners) {
        mCastManager.removeVideoCastConsumer(mCastConsumer);
        mState = PlaybackState.STATE_STOPPED;
        if (notifyListeners && mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
        stopFileServer();
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    @Override
    public void setState(int state) {
        this.mState = state;
    }

    @Override
    public int getCurrentStreamPosition() {
        if (!mCastManager.isConnected()) {
            return mCurrentPosition;
        }
        try {
            return (int)mCastManager.getCurrentMediaPosition();
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception getting media position");
        }
        return -1;
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.mCurrentPosition = pos;
    }

    @Override
    public void play(QueueItem item) {
        try {
            loadMedia(item.getDescription().getMediaId(), true);
            mState = PlaybackState.STATE_BUFFERING;
            if (mCallback != null) {
                mCallback.onPlaybackStatusChanged(mState);
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, "Exception loading media ", e, null);
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void pause() {
        try {
            if (mCastManager.isRemoteMediaLoaded()) {
                mCastManager.pause();
                mCurrentPosition = (int) mCastManager.getCurrentMediaPosition();
            } else {
                loadMedia(mCurrentMediaId, false);
            }
        } catch ( CastException | TransientNetworkDisconnectionException
                | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void seekTo(int position) {
        if (mCurrentMediaId == null) {
            if (mCallback != null) {
                mCallback.onError("seekTo cannot be calling in the absence of mediaId.");
            }
            return;
        }
        try {
            if (mCastManager.isRemoteMediaLoaded()) {
                mCastManager.seek(position);
                mCurrentPosition = position;
            } else {
                mCurrentPosition = position;
                loadMedia(mCurrentMediaId, false);
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    @Override
    public boolean isConnected() {
        return mCastManager.isConnected();
    }

    @Override
    public boolean isPlaying() {
        try {
            return mCastManager.isConnected() && mCastManager.isRemoteMediaPlaying();
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception calling isRemoteMoviePlaying");
        }
        return false;
    }

    @Override
    public int getState() {
        return mState;
    }

    private void startFileServer() {
        if (mFileServer != null) {
            LogHelper.e(TAG, "FileServer already running");
            return;
        }
        mFileServer = new CastFileServer();
        //String BASE_URL = mFileServer.getLocalIpAddress() + ":" + mFileServer.getServerPort();
        try {
            mFileServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogHelper.d(TAG, "FileServer started");
    }

    private void stopFileServer() {
        if (mFileServer == null) {
            LogHelper.e(TAG, "FileServer is not running");
            return;
        }
        try {
            mFileServer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogHelper.d(TAG, "FileServer stopped");
    }

    private void loadMedia(String mediaId, boolean autoPlay) throws
        TransientNetworkDisconnectionException, NoConnectionException {
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
        android.media.MediaMetadata track = mMusicProvider.getMusic(musicId);
        if (!TextUtils.equals(mediaId, mCurrentMediaId)) {
            mCurrentMediaId = mediaId;
            mCurrentPosition = 0;
        }

        if (mFileServer == null) {
            LogHelper.e(TAG, "FileServer not running");
            return;
        }

        String id = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
        Uri curUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.valueOf(id));
        mediaPath = UriUtil.getImagePath(mContext, curUri);
        String URL = mFileServer.generateUri(mediaPath);
        LogHelper.d(TAG, "FileServer: Media URL: " + URL);

        String albumString = track.getDescription().getIconUri().toString();
        Uri albumUri = Uri.parse(albumString);
        imagePath = UriUtil.getImagePath(mContext, albumUri);
        String ImageURl = mFileServer.generateUri(imagePath);
        LogHelper.d(TAG, "FileServer: Image URL: " + ImageURl);

        MediaInfo media = toCastMediaMetadata(track, mediaId, URL, ImageURl);
        mCastManager.loadMedia(media, autoPlay, mCurrentPosition);
    }

    public static String getMediaPath () {
        return mediaPath;
    }

    public static String getImagePath () {
        return imagePath;
    }


    /**
     * Helper method to convert a {@link android.media.MediaMetadata} to a
     * {@link com.google.android.gms.cast.MediaInfo} used for sending media to the receiver app.
     *
     * @param track {@link com.google.android.gms.cast.MediaMetadata}
     * @param mediaId custom data specifies the local mediaId used by the player.
     * @return mediaInfo {@link com.google.android.gms.cast.MediaInfo}
     */
    private static MediaInfo toCastMediaMetadata(android.media.MediaMetadata track,
                                                 String mediaId, String URL, String ImageURL) {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                track.getDescription().getTitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                track.getDescription().getSubtitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                track.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                track.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM));

        WebImage image = new WebImage(
                new Uri.Builder().encodedPath(ImageURL).build());

        // First image is used by the receiver for showing the audio album art.
        mediaMetadata.addImage(image);
        // Second image is used by Cast Companion Library on the full screen activity that is shown
        // when the cast dialog is clicked.
        mediaMetadata.addImage(image);

        String id = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
        Uri curUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.valueOf(id));

        LogHelper.d(TAG, "toCastMediaMetadata, Music URI = " + curUri);
        LogHelper.d(TAG, "toCastMediaMetadata, URL = " + URL);
        LogHelper.d(TAG, "toCastMediaMetadata, ImageURL = " + ImageURL);

        return new MediaInfo.Builder(URL)
                .setContentType(MIME_TYPE_AUDIO_MPEG)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
    }

    private void updateMetadata() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            MediaInfo mediaInfo = mCastManager.getRemoteMediaInformation();
            if (mediaInfo == null) {
                return;
            }
            JSONObject customData = mediaInfo.getCustomData();

            if (customData != null && customData.has(ITEM_ID)) {
                String remoteMediaId = customData.getString(ITEM_ID);
                if (!TextUtils.equals(mCurrentMediaId, remoteMediaId)) {
                    mCurrentMediaId = remoteMediaId;
                    if (mCallback != null) {
                        mCallback.onMetadataChanged(remoteMediaId);
                    }
                    mCurrentPosition = getCurrentStreamPosition();
                }
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException | JSONException e) {
            LogHelper.e(TAG, e, "Exception processing update metadata");
        }

    }

    private void updatePlaybackState() {
        int status = mCastManager.getPlaybackStatus();
        int idleReason = mCastManager.getIdleReason();

        LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated ", status);

        // Convert the remote playback states to media playback states.
        switch (status) {
            case MediaStatus.PLAYER_STATE_IDLE:
                if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    if (mCallback != null) {
                        mCallback.onCompletion();
                    }
                }
                break;
            case MediaStatus.PLAYER_STATE_BUFFERING:
                mState = PlaybackState.STATE_BUFFERING;
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PLAYING:
                mState = PlaybackState.STATE_PLAYING;
                updateMetadata();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                mState = PlaybackState.STATE_PAUSED;
                updateMetadata();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            default: // case unknown
                LogHelper.d(TAG, "State default : ", status);
                break;
        }
    }
}
