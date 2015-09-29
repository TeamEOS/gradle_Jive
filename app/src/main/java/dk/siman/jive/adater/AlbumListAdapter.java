package dk.siman.jive.adater;

import android.app.Activity;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import dk.siman.jive.R;
import dk.siman.jive.utils.LogHelper;

public class AlbumListAdapter extends BaseAdapter {

	private static final String TAG = LogHelper.makeLogTag(AlbumListAdapter.class);
	private Activity activity;
	private LayoutInflater	inflater;
	public ImageManager imageManager;
	private String parentId;
	private ViewHolder holder;

	private List<MediaBrowser.MediaItem> musicItems;

	static class ViewHolder {
		public TextView title;
		public TextView description;
		public ImageView image;
	}

	public AlbumListAdapter(Activity activity, List<MediaBrowser.MediaItem> musicItems, String parentId) {
		this.activity = activity;
		this.musicItems = musicItems;
		this.inflater = LayoutInflater.from(activity);
		this.parentId = parentId;
		imageManager =
				new ImageManager(activity.getApplicationContext());
	}

	@Override
	public int getCount() {
		return musicItems.size();
	}

	@Override
	public MediaBrowser.MediaItem getItem(int location) {
		return musicItems.get(location);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_album, null);
			holder = new ViewHolder();
			holder.image = (ImageView) convertView.findViewById(R.id.thumbnail);
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.image.getLayoutParams();
			params.width = holder.image.getMaxWidth();
			params.height = holder.image.getMaxHeight();
			holder.image.setLayoutParams(params);
			holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			holder.title = (TextView) convertView.findViewById(R.id.title);
			holder.description = (TextView) convertView.findViewById(R.id.description);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		MediaBrowser.MediaItem media = getItem(position);
		Uri artUri = media.getDescription().getIconUri();
		holder.title.setText(media.getDescription().getTitle());
		holder.description.setText(media.getDescription().getSubtitle());

		if (artUri != null) {
			holder.image.setTag(artUri);
			imageManager.displayImage(artUri.toString(), parentId, activity, holder.image);
		} else {
			imageManager.displayImage("dummy", parentId, activity, holder.image);
		}

		return convertView;
	}
}
