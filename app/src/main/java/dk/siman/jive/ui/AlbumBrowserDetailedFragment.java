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

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dk.siman.jive.R;
import dk.siman.jive.model.MusicProvider;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.MediaIDHelper;
import dk.siman.jive.utils.NetworkHelper;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowser} to connect to the {@link dk.siman.jive.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowser.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class AlbumBrowserDetailedFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(AlbumBrowserDetailedFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private MusicProvider mMusicProvider;
    private BrowseAdapter mBrowserAdapter;

    private String mMediaId;
    private MediaFragmentListener mMediaFragmentListener;

    private ListView listView;
    private ImageView backgroundImage;
    private int lastTopValue = 0;

    private final BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {
        private boolean oldOnline = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            // We don't care about network changes while this fragment is not associated
            // with a media ID (for example, while it is being initialized)
            if (mMediaId != null) {
                boolean isOnline = NetworkHelper.isOnline(context);
                if (isOnline != oldOnline) {
                    oldOnline = isOnline;
                    if (isOnline) {
                        mBrowserAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata change to media ",
                    metadata.getDescription().getMediaId());
            mBrowserAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                    try {
                        LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                                "  count=" + children.size());

                        mBrowserAdapter.clear();
                        for (MediaBrowser.MediaItem item : children) {
                            mBrowserAdapter.add(item);
                        }
                        mBrowserAdapter.notifyDataSetChanged();
                    } catch (Throwable t) {
                        LogHelper.e(TAG, "Error on childrenloaded", t);
                    }
                }

                @Override
                public void onError(String id) {
                    LogHelper.e(TAG, "browse fragment subscription onError, id=" + id);
                    Toast.makeText(getActivity(), R.string.error_loading_media, Toast.LENGTH_LONG).show();
                }
            };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");

        View rootView = inflater.inflate(R.layout.album_fragment_list_details, container, false);
        listView = (ListView) rootView.findViewById(R.id.list_view);
        mBrowserAdapter = new BrowseAdapter(getActivity());

        listView.setAdapter(mBrowserAdapter);

        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.list_album_details_header, listView, false);
        listView.addHeaderView(header, null, false);
        backgroundImage = (ImageView) header.findViewById(R.id.albumart);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaBrowser.MediaItem item = mBrowserAdapter.getItem(position - 1);
                mMediaFragmentListener.onMediaItemSelected(item);
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                Rect rect = new Rect();
                backgroundImage.getLocalVisibleRect(rect);
                if (lastTopValue != rect.top) {
                    lastTopValue = rect.top;
                    backgroundImage.setY((float) (rect.top / 2.0));
                }
            }
        });


        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        // fetch browsing information to fill the listview:
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        mMusicProvider = new MusicProvider(getActivity().getContentResolver(), getActivity().getApplicationContext());

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected();
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.getActivity().registerReceiver(mConnectivityChangeReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        if (getActivity().getMediaController() != null) {
            getActivity().getMediaController().unregisterCallback(mMediaControllerCallback);
        }
        this.getActivity().unregisterReceiver(mConnectivityChangeReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        LogHelper.d(TAG, "onResume");

        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser.isConnected()) {
            onConnected();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(AlbumBrowserDetailedFragment.ARG_MEDIA_ID, mediaId);
        setArguments(args);
    }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    public void onConnected() {
        LogHelper.d(TAG, "onConnected");
        if (isDetached()) {
            return;
        }
        mMediaId = getMediaId();
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }
        updateTitle();

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener.getMediaBrowser().unsubscribe(mMediaId);

        mMediaFragmentListener.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);

        // Add MediaController callback so we can redraw the list when metadata changes:
        if (getActivity().getMediaController() != null) {
            getActivity().getMediaController().registerCallback(mMediaControllerCallback);
        }
    }

    private void updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT.equals(mMediaId)) {
            mMediaFragmentListener.setToolbarTitle(null);
            return;
        }

        final String parentId = MediaIDHelper.getParentMediaID(mMediaId);

        // MediaBrowser doesn't provide metadata for a given mediaID, only for its children. Since
        // the mediaId contains the item's hierarchy, we know the item's parent mediaId and we can
        // fetch and iterate over it and find the proper MediaItem, from which we get the title,
        // This is temporary - a better solution (a method to get a mediaItem by its mediaID)
        // is being worked out in the platform and should be available soon.
        LogHelper.d(TAG, "on updateTitle: mediaId=", mMediaId, " parentID=", parentId);
        if (parentId != null) {
            MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
            LogHelper.d(TAG, "on updateTitle: mediaBrowser is ",
                    mediaBrowser==null?"null":("not null, connected="+mediaBrowser.isConnected()));
            if (mediaBrowser != null && mediaBrowser.isConnected()) {
                // Unsubscribing is required to guarantee that we will get the initial values.
                // Otherwise, if there is another callback subscribed to this mediaID, mediaBrowser
                // will only call this callback when the media content change.
                mediaBrowser.unsubscribe(parentId);
                mediaBrowser.subscribe(parentId, new MediaBrowser.SubscriptionCallback() {
                    @Override
                    public void onChildrenLoaded(String parentId,
                                                 List<MediaBrowser.MediaItem> children) {
                        LogHelper.d(TAG, "Got ", children.size(), " children for ", parentId,
                                ". Looking for ", mMediaId);

                        for (MediaBrowser.MediaItem item: children) {
                            if (item.getMediaId().equals(mMediaId)) {
                                if (mMediaFragmentListener != null) {
                                    mMediaFragmentListener.setToolbarTitle(item.getDescription().getTitle());

                                    Uri artUri = item.getDescription().getIconUri();
                                    Bitmap bitmap = ArtHelper.getAlbumArt(getActivity().getApplicationContext(), artUri);
                                    if (bitmap == null) {
                                        bitmap = BitmapFactory.decodeResource(getActivity().getResources(),
                                                R.drawable.ic_default_art);
                                    }

                                    backgroundImage.setImageBitmap(bitmap);
                                }
                                return;
                            }
                        }
                        mMediaFragmentListener.getMediaBrowser().unsubscribe(parentId);
                    }

                    @Override
                    public void onError(String id) {
                        super.onError(id);
                        LogHelper.d(TAG, "subscribe error: id=", id);
                    }
                });
            }
        }
    }

    // An adapter for showing the list of browsed MediaItem's
    private static class BrowseAdapter extends ArrayAdapter<MediaBrowser.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowser.MediaItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowser.MediaItem item = getItem(position);
            int state = MediaItemViewHolder.STATE_NONE;
            if (item.isPlayable()) {
                state = MediaItemViewHolder.STATE_PLAYABLE;
                MediaController controller = ((Activity) getContext()).getMediaController();
                if (controller != null && controller.getMetadata() != null) {
                    String currentPlaying = controller.getMetadata().getDescription().getMediaId();
                    String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                            item.getDescription().getMediaId());
                    if (currentPlaying != null && currentPlaying.equals(musicId)) {
                        if (controller.getPlaybackState().getState() ==
                                PlaybackState.STATE_PLAYING) {
                            state = MediaItemViewHolder.STATE_PLAYING;
                        } else if (controller.getPlaybackState().getState() !=
                                PlaybackState.STATE_ERROR) {
                            state = MediaItemViewHolder.STATE_PAUSED;
                        }
                    }
                }
            }
            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                    item.getDescription(), state);
        }
    }

    public interface MediaFragmentListener extends MediaBrowserProvider {
        void onMediaItemSelected(MediaBrowser.MediaItem item);
        void setToolbarTitle(CharSequence title);
    }

}
