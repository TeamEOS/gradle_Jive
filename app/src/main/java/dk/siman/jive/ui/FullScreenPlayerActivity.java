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

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dk.siman.jive.MusicService;
import dk.siman.jive.R;
import dk.siman.jive.model.MusicProvider;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.PrefUtils;
import dk.siman.jive.utils.SwipeHelper;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * A full screen player that shows the current playing music with a background image
 * depicting the album art. The activity also has controls to seek/pause/play the audio.
 */
public class FullScreenPlayerActivity extends ActionBarCastActivity {
    private static final String TAG = LogHelper.makeLogTag(FullScreenPlayerActivity.class);
    private static final long PROGRESS_UPDATE_INTERNAL = 1000;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;

    private ImageView mSkipPrev;
    private ImageView mSkipNext;
    private ImageView mPlayPause;
    private ImageView mFavorite;
    private ImageView mRepeat;
    private TextView mStart;
    private TextView mEnd;
    private SeekBar mSeekbar;
    private TextView mLine1;
    private TextView mLine2;
    private TextView mLine3;
    private ProgressBar mLoading;
    private View mControllers;
    private Drawable mPauseDrawable;
    private Drawable mPlayDrawable;
    private Drawable mFavoriteOnDrawable;
    private Drawable mFavoriteOffDrawable;
    private Drawable mRepeatAllDrawable;
    private Drawable mRepeatOneDrawable;
    private ImageSwitcher mBackgroundImageSwitcher;
    private Animation inLeft;
    private Animation inRight;
    private Animation outLeft;
    private Animation outRight;

    private final Handler mHandler = new Handler();
    private MediaBrowser mMediaBrowser;

    private MusicService mService;
    private MusicProvider mMusicProvider;
    private String musicId;

    private Context mContext;

    private final Runnable mUpdateProgressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };

    private final ScheduledExecutorService mExecutorService =
        Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> mScheduleFuture;
    private PlaybackState mLastPlaybackState;

    private final MediaController.Callback mCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            LogHelper.d(TAG, "onPlaybackstate changed", state);
            updatePlaybackState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (metadata != null) {
                updateMediaDescription(metadata.getDescription());
                updateDuration(metadata);
            }
        }
    };

    private final MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
        @Override
        public void onConnected() {
            LogHelper.d(TAG, "onConnected");

            MediaSession.Token token = mMediaBrowser.getSessionToken();
            connectToSession(token);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);
        initializeToolbar();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        mContext = getApplicationContext();

        mBackgroundImageSwitcher = (ImageSwitcher) findViewById(R.id.background_image_switcher);

        mBackgroundImageSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            public View makeView() {
                ImageView myView = new ImageView(getApplicationContext());
                myView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                myView.setLayoutParams(new ImageSwitcher.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT));
                return myView;
            }
        });

        inLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_left);
        outRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_right);
        inRight = AnimationUtils.loadAnimation(this, R.anim.slide_in_right);
        outLeft = AnimationUtils.loadAnimation(this, R.anim.slide_out_left);

        mPauseDrawable = getDrawable(R.drawable.jive_ic_pause);
        mPlayDrawable = getDrawable(R.drawable.jive_ic_play);
        mPlayPause = (ImageView) findViewById(R.id.imageView1);
        mSkipNext = (ImageView) findViewById(R.id.next);
        mSkipPrev = (ImageView) findViewById(R.id.prev);

        mFavorite = (ImageView) findViewById(R.id.favoriteImageView);
        mRepeat = (ImageView) findViewById(R.id.repeatImageView);

        mFavoriteOnDrawable = getDrawable(R.drawable.jive_ic_thumb_up);
        mFavoriteOffDrawable = getDrawable(R.drawable.jive_ic_thumb_up_outline);
        mRepeatAllDrawable = getDrawable(R.drawable.jive_ic_loop_64dp);
        mRepeatOneDrawable = getDrawable(R.drawable.jive_ic_loop_one_64dp);

        mStart = (TextView) findViewById(R.id.startText);
        mEnd = (TextView) findViewById(R.id.endText);
        mSeekbar = (SeekBar) findViewById(R.id.seekBar1);
        mLine1 = (TextView) findViewById(R.id.line1);
        mLine2 = (TextView) findViewById(R.id.line2);
        mLine3 = (TextView) findViewById(R.id.line3);
        mLoading = (ProgressBar) findViewById(R.id.progressBar1);
        mControllers = findViewById(R.id.controllers);
        View mFullPlayerMainView = findViewById(R.id.full_player_main_view);

        mFullPlayerMainView.setOnTouchListener(new SwipeHelper(mContext) {
            public void onSwipeRight() {
                mBackgroundImageSwitcher.setInAnimation(inLeft);
                mBackgroundImageSwitcher.setOutAnimation(outRight);

                if (PrefUtils.isInvertSwipe(mContext)) {
                    LogHelper.d(TAG, "inverted onSwipeRight");
                    MediaController.TransportControls controls =
                            getMediaController().getTransportControls();
                    controls.skipToPrevious();
                } else {
                    LogHelper.d(TAG, "onSwipeRight");
                    MediaController.TransportControls controls =
                            getMediaController().getTransportControls();
                    controls.skipToNext();
                }
            }
            public void onSwipeLeft() {
                mBackgroundImageSwitcher.setInAnimation(inRight);
                mBackgroundImageSwitcher.setOutAnimation(outLeft);

                if (PrefUtils.isInvertSwipe(mContext)) {
                    LogHelper.d(TAG, "inverted onSwipeLeft");
                    MediaController.TransportControls controls =
                            getMediaController().getTransportControls();
                    controls.skipToNext();
                } else {
                    LogHelper.d(TAG, "onSwipeLeft");
                    MediaController.TransportControls controls =
                            getMediaController().getTransportControls();
                    controls.skipToPrevious();
                }
            }
            public void onSwipeUp() {
                LogHelper.d(TAG, "onSwipeUp");

            }
            public void onSwipeDown() {
                LogHelper.d(TAG, "onSwipeDown");
                dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            }
        });

        mService = MusicService.getService();
        mMusicProvider = new MusicProvider(getContentResolver(), mContext);

        mSkipNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBackgroundImageSwitcher.setInAnimation(inLeft);
                mBackgroundImageSwitcher.setOutAnimation(outRight);

                MediaController.TransportControls controls =
                    getMediaController().getTransportControls();
                controls.skipToNext();
            }
        });

        mSkipPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBackgroundImageSwitcher.setInAnimation(inRight);
                mBackgroundImageSwitcher.setOutAnimation(outLeft);

                MediaController.TransportControls controls =
                    getMediaController().getTransportControls();
                controls.skipToPrevious();
            }
        });

        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackState state = getMediaController().getPlaybackState();
                if (state != null) {
                    MediaController.TransportControls controls =
                            getMediaController().getTransportControls();
                    switch (state.getState()) {
                        case PlaybackState.STATE_PLAYING: // fall through
                        case PlaybackState.STATE_BUFFERING:
                            controls.pause();
                            stopSeekbarUpdate();
                            break;
                        case PlaybackState.STATE_PAUSED:
                        case PlaybackState.STATE_STOPPED:
                            controls.play();
                            scheduleSeekbarUpdate();
                            break;
                        default:
                            LogHelper.d(TAG, "onClick with state ", state.getState());
                    }
                }
            }
        });

        mFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateFavoriteState(true);
            }
        });

        updateRepeatState(false);
        mRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateRepeatState(true);
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mStart.setText(Utils.formatMillis(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekbarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                getMediaController().getTransportControls().seekTo(seekBar.getProgress());
                scheduleSeekbarUpdate();
            }
        });

        // Only update from the intent if we are not recreating from a config change:
        if (savedInstanceState == null) {
            updateFromParams(getIntent());
        }

        mMediaBrowser = new MediaBrowser(this,
            new ComponentName(this, MusicService.class), mConnectionCallback, null);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If user hit menu key, we don't want to try and open the menu drawer
        return keyCode != KeyEvent.KEYCODE_MENU && super.onKeyDown(keyCode, event);
    }

    private void updateRepeatState(boolean change) {

        int mRepeatMode = MusicService.getRepeatMode();
        LogHelper.d(TAG, "updateRepeatState mode: ", mRepeatMode);

        if (change) {
            if (mRepeatMode == MusicService.REPEAT_NONE) {
                mRepeat.setImageDrawable(mRepeatOneDrawable);
                mRepeat.setAlpha(1.0f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_CURRENT);
                }
            } else if (mRepeatMode == MusicService.REPEAT_CURRENT) {
                mRepeat.setImageDrawable(mRepeatAllDrawable);
                mRepeat.setAlpha(1.0f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_ALL);
                }
            } else if (mRepeatMode == MusicService.REPEAT_ALL) {
                mRepeat.setImageDrawable(mRepeatOneDrawable);
                mRepeat.setAlpha(0.4f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_NONE);
                }
            }

        } else {
            if (mRepeatMode == MusicService.REPEAT_NONE) {
                mRepeat.setImageDrawable(mRepeatOneDrawable);
                mRepeat.setAlpha(0.4f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_NONE);
                }
            } else if (mRepeatMode == MusicService.REPEAT_CURRENT) {
                mRepeat.setImageDrawable(mRepeatOneDrawable);
                mRepeat.setAlpha(1.0f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_CURRENT);
                }
            } else if (mRepeatMode == MusicService.REPEAT_ALL) {
                mRepeat.setImageDrawable(mRepeatAllDrawable);
                mRepeat.setAlpha(1.0f);
                if (mService != null) {
                    mService.setRepeatMode(MusicService.REPEAT_ALL);
                }
            }
        }
    }

    private void connectToSession(MediaSession.Token token) {
        MediaController mediaController = new MediaController(FullScreenPlayerActivity.this, token);
        if (mediaController.getMetadata() == null) {
            finish();
            return;
        }
        setMediaController(mediaController);
        mediaController.registerCallback(mCallback);
        PlaybackState state = mediaController.getPlaybackState();
        updatePlaybackState(state);
        MediaMetadata metadata = mediaController.getMetadata();
        if (metadata != null) {
            updateMediaDescription(metadata.getDescription());
            updateDuration(metadata);
        }
        updateProgress();
        if (state != null && (state.getState() == PlaybackState.STATE_PLAYING ||
                state.getState() == PlaybackState.STATE_BUFFERING)) {
            scheduleSeekbarUpdate();
        }
    }

    private void updateFromParams(Intent intent) {
        if (intent != null) {
            MediaDescription description = intent.getParcelableExtra(
                    AlbumPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION);
            if (description != null) {
                updateMediaDescription(description);
            }
        }
    }

    private void scheduleSeekbarUpdate() {
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            mHandler.post(mUpdateProgressTask);
                        }
                    }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
        }
    }

    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMediaBrowser != null) {
            mMediaBrowser.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaBrowser != null) {
            mMediaBrowser.disconnect();
        }
        if (getMediaController() != null) {
            getMediaController().unregisterCallback(mCallback);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSeekbarUpdate();
        mExecutorService.shutdown();
    }

    private void updateMediaDescription(MediaDescription description) {
        if (description == null) {
            return;
        }
        LogHelper.d(TAG, "updateMediaDescription called ");
        mLine1.setText(description.getTitle());
        mLine2.setText(description.getSubtitle());

        Uri artUrl = description.getIconUri();
        Bitmap bitmap = ArtHelper.getAlbumArt(mContext, artUrl);
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.ic_default_art);
        }

        updateFavoriteState(false);
        Drawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
        mBackgroundImageSwitcher.setImageDrawable(bitmapDrawable);
    }

    private void updateDuration(MediaMetadata metadata) {
        if (metadata == null) {
            return;
        }
        LogHelper.d(TAG, "updateDuration called ");
        int duration = (int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        mSeekbar.setMax(duration);
        mEnd.setText(Utils.formatMillis(duration));
    }

    private void updateFavoriteState(boolean change) {

        MediaMetadata metadata = MusicService.MEDIA_ID;
        Boolean isCurFavorite = false;
        Boolean isFavorite = false;

        if (metadata != null) {
            musicId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            isCurFavorite = mMusicProvider.isFavorite(musicId);
            if (change) {
                isFavorite = !isCurFavorite;
            }
        }

        if (musicId != null) {
            if (isFavorite && !isCurFavorite) {
                mFavorite.setImageDrawable(mFavoriteOnDrawable);
                if (change)
                    mMusicProvider.setFavorite(musicId, true);
                //LogHelper.i(TAG, "updateFavoriteState, true");
            } else if (!isFavorite && isCurFavorite) {
                if (change) {
                    mMusicProvider.setFavorite(musicId, false);
                    mFavorite.setImageDrawable(mFavoriteOffDrawable);
                } else {
                    mFavorite.setImageDrawable(mFavoriteOnDrawable);
                }
                //LogHelper.i(TAG, "updateFavoriteState, false");
            } else {
                mFavorite.setImageDrawable(mFavoriteOffDrawable);
                if (change)
                    mMusicProvider.setFavorite(musicId, false);
                //LogHelper.i(TAG, "set to false");
            }
        }
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) {
            return;
        }
        mLastPlaybackState = state;
        String castName = getMediaController()
                .getExtras().getString(MusicService.EXTRA_CONNECTED_CAST);
        String line3Text = "";
        if (castName != null) {
            line3Text = getResources()
                    .getString(R.string.casting_to_device, castName);
        }
        mLine3.setText(line3Text);

        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPauseDrawable);
                mFavorite.setVisibility(VISIBLE);
                mRepeat.setVisibility(VISIBLE);
                mControllers.setVisibility(VISIBLE);
                scheduleSeekbarUpdate();
                break;
            case PlaybackState.STATE_PAUSED:
                mControllers.setVisibility(VISIBLE);
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                mFavorite.setVisibility(VISIBLE);
                mRepeat.setVisibility(VISIBLE);
                stopSeekbarUpdate();
                break;
            case PlaybackState.STATE_NONE:
            case PlaybackState.STATE_STOPPED:
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                mFavorite.setVisibility(VISIBLE);
                mRepeat.setVisibility(VISIBLE);
                stopSeekbarUpdate();
                break;
            case PlaybackState.STATE_BUFFERING:
                mPlayPause.setVisibility(INVISIBLE);
                mLoading.setVisibility(VISIBLE);
                mFavorite.setVisibility(VISIBLE);
                mRepeat.setVisibility(VISIBLE);
                mLine3.setText(R.string.loading);
                stopSeekbarUpdate();
                break;
            default:
                LogHelper.d(TAG, "Unhandled state ", state.getState());
        }

        mSkipNext.setVisibility((state.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) == 0
            ? INVISIBLE : VISIBLE );
        mSkipPrev.setVisibility((state.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0
            ? INVISIBLE : VISIBLE );
    }

    private void updateProgress() {
        if (mLastPlaybackState == null) {
            return;
        }
        long currentPosition = mLastPlaybackState.getPosition();
        if (mLastPlaybackState.getState() != PlaybackState.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaController.
            long timeDelta = SystemClock.elapsedRealtime() -
                    mLastPlaybackState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * mLastPlaybackState.getPlaybackSpeed();
        }
        mSeekbar.setProgress((int) currentPosition);
    }
}
