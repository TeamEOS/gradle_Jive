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

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import dk.siman.jive.MusicService;
import dk.siman.jive.R;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.PrefUtils;
import dk.siman.jive.utils.SwipeHelper;


/**
 * A class that shows the Media Queue to the user.
 */
public class PlaybackControlsFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(PlaybackControlsFragment.class);

    private ImageButton mPlayPause;
    private ImageButton mFavorite;
    private ImageButton mRepeat;
    private TextView mTitle;
    private TextView mSubtitle;
    private TextView mExtraInfo;
    private ImageView mAlbumArt;
    private String mArtUrl;
    private Context mContext;
    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaController.Callback mCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) {
                return;
            }
            LogHelper.d(TAG, "Received playback state change to state ", state.getState());
            PlaybackControlsFragment.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata state change to mediaId=",
                    metadata.getDescription().getMediaId(),
                    " song=", metadata.getDescription().getTitle());
            PlaybackControlsFragment.this.onMetadataChanged(metadata);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_playback_controls, container, false);
        mContext = getActivity().getApplication().getApplicationContext();

        mPlayPause = (ImageButton) rootView.findViewById(R.id.play_pause);
        mPlayPause.setEnabled(true);
        mPlayPause.setOnClickListener(mPlayButtonListener);

        mTitle = (TextView) rootView.findViewById(R.id.title);
        mSubtitle = (TextView) rootView.findViewById(R.id.artist);
        mExtraInfo = (TextView) rootView.findViewById(R.id.extra_info);
        mAlbumArt = (ImageView) rootView.findViewById(R.id.album_art);

        rootView.setOnTouchListener(new SwipeHelper(mContext) {
            public void onSwipeRight() {
                if (PrefUtils.isInvertSwipe(mContext)) {
                    LogHelper.d(TAG, "onSwipeLeft");
                    MediaController controller = getActivity().getMediaController();
                    controller.getTransportControls().skipToPrevious();
                } else {
                    LogHelper.d(TAG, "onSwipeRight");
                    MediaController controller = getActivity().getMediaController();
                    controller.getTransportControls().skipToNext();
                }
            }
            public void onSwipeLeft() {
                if (PrefUtils.isInvertSwipe(mContext)) {
                    LogHelper.d(TAG, "onSwipeRight");
                    MediaController controller = getActivity().getMediaController();
                    controller.getTransportControls().skipToNext();
                } else {
                    LogHelper.d(TAG, "onSwipeLeft");
                    MediaController controller = getActivity().getMediaController();
                    controller.getTransportControls().skipToPrevious();
                }
            }
            public void onSwipeUp() {
                LogHelper.d(TAG, "onSwipeUp");
                Intent intent = new Intent(getActivity(), FullScreenPlayerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                MediaMetadata metadata = getActivity().getMediaController().getMetadata();
                if (metadata != null) {
                    intent.putExtra(AlbumPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
                            metadata.getDescription());
                }
                startActivity(intent);
            }
            public void onSwipeDown() {
                LogHelper.d(TAG, "onSwipeDown");
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        LogHelper.d(TAG, "fragment.onStart");
        if (getActivity().getMediaController() != null) {
            onConnected();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LogHelper.d(TAG, "fragment.onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        LogHelper.d(TAG, "fragment.onStop");
        if (getActivity().getMediaController() != null) {
            getActivity().getMediaController().unregisterCallback(mCallback);
        }
    }

    public void onConnected() {
        MediaController controller = getActivity().getMediaController();
        LogHelper.d(TAG, "onConnected, mediaController==null? ", controller == null);
        if (controller != null) {
            onMetadataChanged(controller.getMetadata());
            onPlaybackStateChanged(controller.getPlaybackState());
            LogHelper.d(TAG, "registerCallback");
            controller.registerCallback(mCallback);
        }
    }

    private void onMetadataChanged(MediaMetadata metadata) {
        LogHelper.d(TAG, "onMetadataChanged ", metadata);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onMetadataChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (metadata == null) {
            return;
        }

        mTitle.setText(metadata.getDescription().getTitle());
        mSubtitle.setText(metadata.getDescription().getSubtitle());
        String artUrl = metadata.getDescription().getIconUri().toString();

        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mArtUrl = artUrl;
            Bitmap bitmap = ArtHelper.getAlbumArt(mContext, Uri.parse(artUrl));
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(this.getResources(),
                        R.drawable.ic_default_art);
            }
            if (bitmap != null) {
                Bitmap icon = ArtHelper.scaleBitmapIcon(bitmap);
                LogHelper.d(TAG, "album art icon of w=", icon.getWidth(),
                        " h=", icon.getHeight());
                mAlbumArt.setImageBitmap(icon);
            }
        }
    }

    private void setExtraInfo(String extraInfo) {
        if (extraInfo == null) {
            mExtraInfo.setVisibility(View.GONE);
        } else {
            mExtraInfo.setText(extraInfo);
            mExtraInfo.setVisibility(View.VISIBLE);
        }
    }

    private void onPlaybackStateChanged(PlaybackState state) {
        LogHelper.d(TAG, "onPlaybackStateChanged ", state);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onPlaybackStateChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (state == null) {
            return;
        }
        boolean enablePlay = false;
        switch (state.getState()) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
                enablePlay = true;
                break;
            case PlaybackState.STATE_ERROR:
                LogHelper.e(TAG, "error playbackstate: ", state.getErrorMessage());
                Toast.makeText(getActivity(), state.getErrorMessage(), Toast.LENGTH_LONG).show();
                break;
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(
                    getActivity().getDrawable(R.drawable.ic_play_arrow_black_36dp));
        } else {
            mPlayPause.setImageDrawable(getActivity().getDrawable(R.drawable.ic_pause_black_36dp));
        }

        MediaController controller = getActivity().getMediaController();
        String extraInfo = null;
        if (controller != null && controller.getExtras() != null) {
            String castName = controller.getExtras().getString(MusicService.EXTRA_CONNECTED_CAST);
            if (castName != null) {
                extraInfo = getResources().getString(R.string.casting_to_device, castName);
            }
        }
        setExtraInfo(extraInfo);
    }

    private final View.OnClickListener mPlayButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            PlaybackState stateObj = getActivity().getMediaController().getPlaybackState();
            final int state = stateObj == null ?
                    PlaybackState.STATE_NONE : stateObj.getState();
            LogHelper.d(TAG, "Button pressed, in state " + state);
            switch (v.getId()) {
                case R.id.play_pause:
                    LogHelper.d(TAG, "Play button pressed, in state " + state);
                    if (state == PlaybackState.STATE_PAUSED ||
                            state == PlaybackState.STATE_STOPPED ||
                            state == PlaybackState.STATE_NONE) {
                        playMedia();
                    } else if (state == PlaybackState.STATE_PLAYING ||
                            state == PlaybackState.STATE_BUFFERING ||
                            state == PlaybackState.STATE_CONNECTING) {
                        pauseMedia();
                    }
                    break;
            }
        }
    };

    private void playMedia() {
        MediaController controller = getActivity().getMediaController();
        if (controller != null) {
            controller.getTransportControls().play();
        }
    }

    private void pauseMedia() {
        MediaController controller = getActivity().getMediaController();
        if (controller != null) {
            controller.getTransportControls().pause();
        }
    }
}
