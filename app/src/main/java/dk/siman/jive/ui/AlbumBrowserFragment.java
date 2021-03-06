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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dk.siman.jive.R;
import dk.siman.jive.adater.AlbumListAdapter;
import dk.siman.jive.model.MusicProvider;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.MediaIDHelper;
import dk.siman.jive.utils.PermissionHelper;

import static dk.siman.jive.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowser} to connect to the {@link dk.siman.jive.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowser.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class AlbumBrowserFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(AlbumBrowserFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private AlbumListAdapter mAlbumListAdapter;
    private List<MediaBrowser.MediaItem> musicList = new ArrayList<>();
    private MusicProvider mMusicProvider;

    private String mMediaId;
    private String parentId;
    private MediaFragmentListener mMediaFragmentListener;
    private View mErrorView;
    private TextView mErrorMessage;

    private ListView listView;
    private View rootView;
    private Context mContext;

    private static final int REQUEST_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    private final BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogHelper.d(TAG, "onReceive");
            // We don't care about network changes while this fragment is not associated
            // with a media ID (for example, while it is being initialized)
            if (mMediaId != null) {
                if (mAlbumListAdapter != null) {
                    // Verify that all required storage permissions have been granted.
                    if ((ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED)
                            || (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED)) {
                        // Storage permissions have not been granted.
                        requestStoragePermissions();
                    } else {
                        // Storage permissions have been granted. Save wallpaper.
                        mAlbumListAdapter.notifyDataSetChanged();
                    }
                } else {
                    LogHelper.e(TAG, "onReceive, mAlbumListAdapter is null, recreating fragment");
                    recreateFragment();
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
            if (mAlbumListAdapter != null) {
                mAlbumListAdapter.notifyDataSetChanged();
            } else {
                LogHelper.e(TAG, "onMetadataChanged, mAlbumListAdapteris null, recreating fragment");
                //TODO: Workaround NPE on mAlbumListAdapter. Reason for NPE should be fixed instead // FIXME: 10-08-15
                recreateFragment();
            }
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);
            checkForUserVisibleErrors(false);
            if (mAlbumListAdapter != null) {
                mAlbumListAdapter.notifyDataSetChanged();
            } else {
                LogHelper.e(TAG, "onPlaybackStateChanged, mAlbumListAdapter is null, recreating fragment");
                //TODO: Workaround NPE on mAlbumListAdapter. Reason for NPE should be fixed instead // FIXME: 10-08-15
                recreateFragment();
            }
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                    try {
                        LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                                "  count=" + children.size());
                        checkForUserVisibleErrors(children.isEmpty());

                        musicList.clear();
                        for (MediaBrowser.MediaItem item : children) {
                            musicList.add(item);
                        }
                        mAlbumListAdapter.notifyDataSetChanged();
                    } catch (Throwable t) {
                        LogHelper.e(TAG, "Error on childrenloaded", t);
                    }
                }

                @Override
                public void onError(String id) {
                    LogHelper.e(TAG, "browse fragment subscription onError, id=" + id);
                    Toast.makeText(getActivity(), R.string.error_loading_media, Toast.LENGTH_LONG).show();
                    checkForUserVisibleErrors(true);
                }
            };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        LogHelper.d(TAG, "onAttach");
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "onCreate");

        mContext = getActivity().getApplicationContext();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");

        if (container == null) {
            LogHelper.e(TAG, "initListAdapter, container is null, recreating fragment");
            recreateFragment();
        }

        rootView = inflater.inflate(R.layout.album_fragment_list, container, false);
        listView = (ListView) rootView.findViewById(R.id.list_view);
        mAlbumListAdapter = new AlbumListAdapter(getActivity(), musicList, getParentId());

        listView.setAdapter(mAlbumListAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //checkForUserVisibleErrors(false);
                MediaBrowser.MediaItem item = mAlbumListAdapter.getItem(position);
                mMediaFragmentListener.onMediaItemSelected(item);
            }
        });


        mErrorView = rootView.findViewById(R.id.playback_error);
        mErrorMessage = (TextView) mErrorView.findViewById(R.id.error_message);

        return rootView;
    }

    @Override
    public void onStart() {
        LogHelper.d(TAG, "onStart");
        super.onStart();

        initOnConnected();

        // Verify that all required storage permissions have been granted.
        if ((ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            // Storage permissions have not been granted.
            requestStoragePermissions();
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.getActivity().registerReceiver(mConnectivityChangeReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStop() {
        LogHelper.d(TAG, "onStop");
        super.onStop();
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        if (getActivity().getMediaController() != null) {
            LogHelper.d(TAG, "unregisterCallback");
            getActivity().getMediaController().unregisterCallback(mMediaControllerCallback);
        }
        this.getActivity().unregisterReceiver(mConnectivityChangeReceiver);
    }

    @Override
    public void onResume() {
        LogHelper.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        LogHelper.d(TAG, "getMediaId");
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    private String getParentId() {
        LogHelper.d(TAG, "getParentId");
        mMediaId = getMediaId();
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }
        parentId = MediaIDHelper.getParentMediaID(mMediaId);
        return parentId;
    }

    private void recreateFragment() {
        LogHelper.d(TAG, "recreateFragment");

        String FRAGMENT_TAG = "jive_list_container";
        AlbumBrowserFragment fragment = new AlbumBrowserFragment();
        fragment.setMediaId(MEDIA_ID_MUSICS_BY_ALBUM);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
        transaction.addToBackStack(null);
        transaction.commitAllowingStateLoss();
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(AlbumBrowserFragment.ARG_MEDIA_ID, mediaId);
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
            LogHelper.d(TAG, "registerCallback");
            getActivity().getMediaController().registerCallback(mMediaControllerCallback);
        }
    }

    private void checkForUserVisibleErrors(boolean forceError) {
        boolean showError = forceError;

        // otherwise, if state is ERROR and metadata!=null, use playback state error message:
        MediaController controller = getActivity().getMediaController();
        if (controller != null
                && controller.getMetadata() != null
                && controller.getPlaybackState().getState() == PlaybackState.STATE_ERROR
                && controller.getPlaybackState().getErrorMessage() != null) {
            mErrorMessage.setText(controller.getPlaybackState().getErrorMessage());
            showError = true;
        } else if (forceError) {
            // Finally, if the caller requested to show error, show a generic message:
            mErrorMessage.setText(R.string.error_loading_media);
            showError = true;
        }

        if (mErrorView != null) {
            mErrorView.setVisibility(showError ? View.VISIBLE : View.GONE);
        }
        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
                " showError=", showError);

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

    /**
     * Requests the Storage permissions.
     * If the permission has been denied previously, a SnackBar will prompt the user to grant the
     * permission, otherwise it is requested directly.
     */
    private void requestStoragePermissions() {

        LogHelper.d(TAG, "requestStoragePermissions");

        if ((FragmentCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
                || (FragmentCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE))) {

            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example, if the request has been denied previously.

            // Display a SnackBar with an explanation and a button to trigger the request.
            Snackbar.make(rootView, R.string.permission_storage_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            FragmentCompat
                                    .requestPermissions(AlbumBrowserFragment.this, PERMISSIONS_STORAGE,
                                            REQUEST_STORAGE);
                        }
                    })
                    .show();
        } else {
            // Storage permissions have not been granted yet. Request them directly.
            FragmentCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_STORAGE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_STORAGE) {
            LogHelper.d(TAG, "onRequestPermissionsResult");
            if (PermissionHelper.verifyPermissions(grantResults)) {
                Snackbar.make(rootView, R.string.permision_available_storage,
                        Snackbar.LENGTH_LONG)
                        .show();

                initOnConnected();

            } else {
                Snackbar.make(rootView, R.string.permissions_not_granted,
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void initOnConnected() {
        LogHelper.d(TAG, "initOnConnected");
        // fetch browsing information to fill the listview:
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        mMusicProvider = new MusicProvider(getActivity().getContentResolver(), getActivity().getApplicationContext());

        LogHelper.d(TAG, "onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected();
        }
    }

}
