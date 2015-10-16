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

import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;

import dk.siman.jive.R;
import dk.siman.jive.utils.LogHelper;

import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class AlbumPlayerActivity extends BaseActivity
        implements AlbumBrowserFragment.MediaFragmentListener, AlbumBrowserDetailedFragment.MediaFragmentListener {

    private static final String TAG = LogHelper.makeLogTag(AlbumPlayerActivity.class);
    private static final String SAVED_MEDIA_ID="dk.siman.jive.MEDIA_ID";
    private static final String FRAGMENT_TAG = "jive_list_container";
    private static final String FRAGMENT_TAG_DETAILED = "jive_list_detailed_container";

    public static final String EXTRA_START_FULLSCREEN =
            "dk.siman.jive.EXTRA_START_FULLSCREEN";

    /**
     * Optionally used with {@link #EXTRA_START_FULLSCREEN} to carry a MediaDescription to
     * the {@link FullScreenPlayerActivity}, speeding up the screen rendering
     * while the {@link android.media.session.MediaController} is connecting.
     */
    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION =
            "dk.siman.jive.CURRENT_MEDIA_DESCRIPTION";

    private Bundle mVoiceSearchParams;

    private Boolean detailedView = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.album_activity_player);

        initializeToolbar();
        initializeFromParams(savedInstanceState, getIntent());

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(getIntent());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMediaItemSelected(MediaBrowser.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            getMediaController().getTransportControls().playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
            if (item.getMediaId() == null) {
                navigateToBrowser(item.getMediaId());
            } else {
                navigateToBrowserDetailed(item.getMediaId());
            }
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent);
        initializeFromParams(null, intent);
        startFullScreenActivityIfNeeded(intent);
    }

    private void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            Intent fullScreenIntent = new Intent(this, FullScreenPlayerActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
                            intent.getParcelableExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION));
            startActivity(fullScreenIntent);
        }
    }

    private void initializeFromParams(Bundle savedInstanceState, Intent intent) {
        String mediaId = null;
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
                && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams.getString(SearchManager.QUERY));
        } else {
            if (savedInstanceState != null) {
                // If there is a saved media ID, use it
                mediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
            }
        }
        if (mediaId == null) {
            detailedView = false;
            navigateToBrowser(MEDIA_ID_MUSICS_BY_ALBUM);
        } else {
            detailedView = true;
            navigateToBrowserDetailed(mediaId);
        }
    }

    private void navigateToBrowserDetailed(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowserDetailed, mediaId=" + mediaId);

        if (mediaId == null) {
            mediaId = MEDIA_ID_MUSICS_BY_ALBUM;
        }

        AlbumBrowserDetailedFragment fragment = getBrowseDetailedFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new AlbumBrowserDetailedFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG_DETAILED);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);

        if (mediaId == null) {
            mediaId = MEDIA_ID_MUSICS_BY_ALBUM;
        }

        AlbumBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new AlbumBrowserFragment();
            fragment.setMediaId(mediaId);

            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (!mediaId.equals(MEDIA_ID_MUSICS_BY_ALBUM)) {
                transaction.addToBackStack(null);
            }

            transaction.commit();
        }
    }

    private String getMediaId() {
        AlbumBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
        return fragment.getMediaId();
    }

    private AlbumBrowserFragment getBrowseFragment() {
        return (AlbumBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    private AlbumBrowserDetailedFragment getBrowseDetailedFragment() {
        return (AlbumBrowserDetailedFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG_DETAILED);
    }

    @Override
    protected void onMediaControllerConnected() {
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            getMediaController().getTransportControls().playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
        if (!detailedView) {
            LogHelper.d(TAG, "1 detailedView=" , detailedView);
            getBrowseFragment().onConnected();
        } else {
            LogHelper.d(TAG, "2 detailedView=" , detailedView);
            getBrowseDetailedFragment().onConnected();
        }
    }
}
