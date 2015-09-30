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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.support.v7.graphics.Palette;

import dk.siman.jive.ui.AlbumPlayerActivity;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.PaletteHelper;
import dk.siman.jive.utils.ResourceHelper;

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
public class MediaNotificationManager extends BroadcastReceiver {
    private static final String TAG = LogHelper.makeLogTag(MediaNotificationManager.class);

    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;

    private static final String ACTION_PAUSE = "dk.siman.jive.pause";
    private static final String ACTION_PLAY = "dk.siman.jive.play";
    private static final String ACTION_STOP = "dk.siman.jive.stop";
    private static final String ACTION_PREV = "dk.siman.jive.prev";
    private static final String ACTION_NEXT = "dk.siman.jive.next";
    private static final String ACTION_STOP_CASTING = "dk.siman.jive.stop_cast";

    private final MusicService mService;
    private MediaSession.Token mSessionToken;
    private MediaController mController;
    private MediaController.TransportControls mTransportControls;

    private PlaybackState mPlaybackState;
    private MediaMetadata mMetadata;

    private final NotificationManager mNotificationManager;

    private final PendingIntent mPauseIntent;
    private final PendingIntent mPlayIntent;
    private final PendingIntent mStopIntent;
    private final PendingIntent mPreviousIntent;
    private final PendingIntent mNextIntent;

    private final PendingIntent mStopCastIntent;

    private int mNotificationColor;
    private int vibrantColor;
    private int mutedColor;
    private int paletteSamples = 6;

    private Bitmap bitmap = null;
    private Bitmap defaultBitmap = null;

    private static boolean mStarted = false;

    public MediaNotificationManager(MusicService service) {
        mService = service;
        updateSessionToken();

        mNotificationManager = (NotificationManager) mService
                .getSystemService(Context.NOTIFICATION_SERVICE);

        String pkg = mService.getPackageName();
        mPauseIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPlayIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mStopIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_STOP).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPreviousIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mNextIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mStopCastIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_STOP_CASTING).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT);

        defaultBitmap = BitmapFactory.decodeResource(mService.getResources(),
                R.drawable.ic_default_art);

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        mNotificationManager.cancelAll();
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before {@link #stopNotification} is called.
     */
    public void startNotification() {
        if (!mStarted) {
            mMetadata = mController.getMetadata();
            mPlaybackState = mController.getPlaybackState();

            // The notification must be updated after setting started to true
            Notification notification = createNotification();
            if (notification != null) {
                mController.registerCallback(mCb);
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_NEXT);
                filter.addAction(ACTION_PAUSE);
                filter.addAction(ACTION_PLAY);
                filter.addAction(ACTION_STOP);
                filter.addAction(ACTION_PREV);
                filter.addAction(ACTION_STOP_CASTING);
                mService.registerReceiver(this, filter);

                mService.startForeground(NOTIFICATION_ID, notification);
                mStarted = true;
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    public void stopNotification() {
        LogHelper.d(TAG, "stopNotification");
        if (mStarted) {
            mStarted = false;
            mController.unregisterCallback(mCb);
            try {
                mNotificationManager.cancel(NOTIFICATION_ID);
                mService.unregisterReceiver(this);
            } catch (IllegalArgumentException ex) {
                // ignore if the receiver is not registered.
            }
            mService.stopForeground(true);
        }
    }

    public static String checkNotification() {
        if (!mStarted) {
            return "false";
        }
        return "true";
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        LogHelper.d(TAG, "Received intent with action " + action);
        switch (action) {
            case ACTION_PAUSE:
                mTransportControls.pause();
                break;
            case ACTION_PLAY:
                mTransportControls.play();
                break;
            case ACTION_STOP:
                mTransportControls.stop();
                stopNotification();
                break;
            case ACTION_NEXT:
                mTransportControls.skipToNext();
                break;
            case ACTION_PREV:
                mTransportControls.skipToPrevious();
                break;
            case ACTION_STOP_CASTING:
                Intent i = new Intent(context, MusicService.class);
                i.setAction(MusicService.ACTION_CMD);
                i.putExtra(MusicService.CMD_NAME, MusicService.CMD_STOP_CASTING);
                mService.startService(i);
                break;
            default:
                LogHelper.w(TAG, "Unknown intent ignored. Action=", action);
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see {@link android.media.session.MediaController.Callback#onSessionDestroyed()})
     */
    private void updateSessionToken() {
        MediaSession.Token freshToken = mService.getSessionToken();
        if (mSessionToken == null || !mSessionToken.equals(freshToken)) {
            if (mController != null) {
                mController.unregisterCallback(mCb);
            }
            mSessionToken = freshToken;
            mController = new MediaController(mService, mSessionToken);
            mTransportControls = mController.getTransportControls();
            if (mStarted) {
                mController.registerCallback(mCb);
            }
        }
    }

    private PendingIntent createContentIntent(MediaDescription description) {
        Intent openUI = new Intent(mService, AlbumPlayerActivity.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openUI.putExtra(AlbumPlayerActivity.EXTRA_START_FULLSCREEN, true);
        if (description != null) {
            openUI.putExtra(AlbumPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION, description);
        }
        return PendingIntent.getActivity(mService, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private final MediaController.Callback mCb = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            mPlaybackState = state;
            LogHelper.d(TAG, "Received new playback state", state);
            if (state != null && (state.getState() == PlaybackState.STATE_STOPPED ||
                    state.getState() == PlaybackState.STATE_NONE)) {
                stopNotification();
            } else {
                Notification notification = createNotification();
                if (notification != null) {
                    mNotificationManager.notify(NOTIFICATION_ID, notification);
                }
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            mMetadata = metadata;
            LogHelper.d(TAG, "Received new metadata ", metadata);
            Notification notification = createNotification();
            if (notification != null) {
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            LogHelper.d(TAG, "Session was destroyed, resetting to the new session token");
            updateSessionToken();
        }
    };

    private Notification createNotification() {
        LogHelper.d(TAG, "updateNotificationMetadata. mMetadata=" + mMetadata);
        if (mMetadata == null || mPlaybackState == null) {
            return null;
        }

        Notification.Builder notificationBuilder = new Notification.Builder(mService);

        MediaDescription description = mMetadata.getDescription();

        if (description.getIconUri() != null) {
            String artUrl = description.getIconUri().toString();
            bitmap = mService.getAlbumArt(Uri.parse(artUrl));
            generateNotificationColor(bitmap);
            if (bitmap == null) {
                bitmap = defaultBitmap;
            }
            bitmap = ArtHelper.scaleBitmap(bitmap);
        }

        notificationBuilder
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setCategory("CATEGORY_TRANSPORT")
                .setColor(mNotificationColor)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setDeleteIntent(mStopIntent)
                .setLargeIcon(bitmap);

        int previousButtonPosition = 0;
        int playPauseButtonPosition = 1;
        int nextButtonPosition = 2;

        if ((mPlaybackState.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
            if ((mPlaybackState.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                notificationBuilder.addAction(R.drawable.ic_skip_previous_white_24dp,
                        mService.getString(R.string.label_previous), mPreviousIntent);
                addPlayPauseAction(notificationBuilder);
                notificationBuilder.addAction(R.drawable.ic_skip_next_white_24dp,
                        mService.getString(R.string.label_next), mNextIntent)
                        .setStyle(new Notification.MediaStyle()
                                .setShowActionsInCompactView(
                                        new int[]{previousButtonPosition, playPauseButtonPosition, nextButtonPosition})  // show only play/pause in compact view
                                .setMediaSession(mSessionToken));

            } else {
                notificationBuilder
                        .addAction(R.drawable.ic_skip_previous_white_24dp,
                                mService.getString(R.string.label_previous), mPreviousIntent)
                        .setStyle(new Notification.MediaStyle()
                                .setShowActionsInCompactView(
                                        new int[]{previousButtonPosition, playPauseButtonPosition})  // show only play/pause in compact view
                                .setMediaSession(mSessionToken));
                addPlayPauseAction(notificationBuilder);
            }
        } else if ((mPlaybackState.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
            playPauseButtonPosition = 0;
            nextButtonPosition = 1;
            addPlayPauseAction(notificationBuilder);
            notificationBuilder
                    .addAction(R.drawable.ic_skip_next_white_24dp,
                            mService.getString(R.string.label_next), mNextIntent)
                    .setStyle(new Notification.MediaStyle()
                            .setShowActionsInCompactView(
                                    new int[]{playPauseButtonPosition, nextButtonPosition})  // show only play/pause in compact view
                            .setMediaSession(mSessionToken));
        }

        LogHelper.d(TAG, "setColor mNotificationColor =", notificationBuilder.setColor(mNotificationColor));

        if (mController != null && mController.getExtras() != null) {
            String castName = mController.getExtras().getString(MusicService.EXTRA_CONNECTED_CAST);
            if (castName != null) {
                String castInfo = mService.getResources()
                        .getString(R.string.casting_to_device, castName);
                notificationBuilder.setSubText(castInfo);
                notificationBuilder.addAction(R.drawable.ic_close_black_24dp,
                        mService.getString(R.string.stop_casting), mStopCastIntent);
            }
        }

        setNotificationPlaybackState(notificationBuilder);

        return notificationBuilder.build();
    }

    private void addPlayPauseAction(Notification.Builder builder) {
        LogHelper.d(TAG, "updatePlayPauseAction");
        String label;
        int icon;
        PendingIntent intent;
        if (mPlaybackState.getState() == PlaybackState.STATE_PLAYING) {
            label = mService.getString(R.string.label_pause);
            icon = R.drawable.jive_ic_pause_white_24dp;
            intent = mPauseIntent;
        } else {
            label = mService.getString(R.string.label_play);
            icon = R.drawable.jive_ic_play_arrow_white_24dp;
            intent = mPlayIntent;
        }
        builder.addAction(new Notification.Action(icon, label, intent));
    }

    private void setNotificationPlaybackState(Notification.Builder builder) {
        LogHelper.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + mPlaybackState);
        if (mPlaybackState == null || !mStarted) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. cancelling notification!");
            mService.stopForeground(true);
            return;
        }
        if (mPlaybackState.getState() == PlaybackState.STATE_PLAYING
                && mPlaybackState.getPosition() >= 0) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. updating playback position to ",
                    (System.currentTimeMillis() - mPlaybackState.getPosition()) / 1000, " seconds");
            builder
                    .setWhen(System.currentTimeMillis() - mPlaybackState.getPosition())
                    .setShowWhen(true)
                    .setUsesChronometer(true);
        } else {
            LogHelper.d(TAG, "updateNotificationPlaybackState. hiding playback position");
            builder
                    .setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false);
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(mPlaybackState.getState() == PlaybackState.STATE_PLAYING);
    }

    private void generateNotificationColor(Bitmap artBitmap) {
        LogHelper.d(TAG, "generateNotificationColor=", artBitmap);
        int p = PaletteHelper.generatePalette(artBitmap);

        if (p != 0) {
            mNotificationColor = p;
        } else {
            mNotificationColor = ResourceHelper.getThemeColor(mService,
                    android.R.attr.colorPrimary, Color.DKGRAY);
        }
    }
}
