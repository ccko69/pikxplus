package com.ccko.pikxplus.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ContentUris;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ccko.pikxplus.R;
import com.ccko.pikxplus.utils.ImageLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// glide
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

public class AlbumsFragment extends Fragment {

	private static final String TAG = "AlbumsFragment";
	private RecyclerView recyclerView;
	private AlbumsAdapter adapter;
	private List<Album> albumsList = new ArrayList<>();
	private OnAlbumSelectedListener listener;

	// Interface for communication with activity
	// Interface for communication with activity
	public interface OnAlbumSelectedListener {
		void onAlbumSelected(Album album);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		try {
			listener = (OnAlbumSelectedListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement OnAlbumSelectedListener");
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		// edge to edge
		//	requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
		//		| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

		View view = inflater.inflate(R.layout.fragment_albums, container, false);

		recyclerView = view.findViewById(R.id.recyclerViewAlbums);
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

		// Add divider between items
		//	DividerItemDecoration divider = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
		//	recyclerView.addItemDecoration(divider);

		adapter = new AlbumsAdapter();
		recyclerView.setAdapter(adapter);

		// hiding top toolbar
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity != null && activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().hide();
		}
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		loadAlbums();
	}

	public void loadAlbums() {
		new Thread(() -> {
			List<Album> loadedAlbums = loadAlbumsFromMediaStore();

			if (getActivity() != null) {
				getActivity().runOnUiThread(() -> {
					albumsList.clear();
					albumsList.addAll(loadedAlbums);
					adapter.notifyDataSetChanged();

					if (albumsList.isEmpty()) {
						Toast.makeText(getContext(), "No albums found", Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();
	}

	/**
		private List<Album> loadAlbumsFromMediaStore() {
			List<Album> loadedAlbums = new ArrayList<>();
			// Use LinkedHashMap to preserve the order of albums as they are found
			Map<String, Album> albumMap = new LinkedHashMap<>();
	
			// 1. Define the columns to query
			String[] projection = { MediaStore.Images.Media.BUCKET_ID, // The unique ID of the album/folder
					MediaStore.Images.Media.BUCKET_DISPLAY_NAME, // The display name of the album/folder
					MediaStore.Images.Media._ID, // The image ID (needed for thumbnail URI)
					MediaStore.Images.Media.RELATIVE_PATH // Required for Android 10+
			};
	
			// 2. Query the External MediaStore (storage)
			try (Cursor cursor = getContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					projection, null, // Selection: null (no filtering)
					null, // Selection Args: null
					MediaStore.Images.Media.DATE_TAKEN + " DESC" // Order by date to get recent photos first
			)) {
				if (cursor == null)
					return loadedAlbums;
	
				// 3. Cache column indices for performance
				int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
				int albumNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
				int imageIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
				int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
	
				// 4. Iterate over all photos and group them into albums
				while (cursor.moveToNext()) {
					String albumId = cursor.getString(albumIdColumn);
					String albumName = cursor.getString(albumNameColumn);
					long imageId = cursor.getLong(imageIdColumn);
					String relativePath = cursor.getString(relativePathColumn);
	
					// Build the URI for the photo (needed for the thumbnail)
					Uri thumbnailUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
	
					// Check if we already have an album entry for this bucket/folder
					Album album = albumMap.get(albumId);
					if (album == null) {
						// If it's a new album, create it
						album = new Album(albumId, albumName, 1, thumbnailUri, relativePath);
						albumMap.put(albumId, album);
					} else {
						// If it exists, just increment the count and update the thumbnail (to get the latest one)
						album.count++;
						album.thumbnailUri = thumbnailUri; // Keep the latest image as the cover
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed to load albums from MediaStore.", e);
				// Display a toast only if the app is active
				if (getContext() != null) {
					getActivity().runOnUiThread(() -> Toast
							.makeText(getContext(), "Error loading albums: " + e.getLocalizedMessage(), Toast.LENGTH_LONG)
							.show());
				}
			}
	
			// 5. Convert the map values into a list for the adapter
			loadedAlbums.addAll(albumMap.values());
			return loadedAlbums;
		}
	**/

	private List<Album> loadAlbumsFromMediaStore() {
		List<Album> loadedAlbums = new ArrayList<>();
		Map<String, Album> albumMap = new LinkedHashMap<>();

		String[] projection = { MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
				MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH };

		int totalCount = 0;
		Uri latestThumbnail = null;

		try (Cursor cursor = getContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				projection, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
			if (cursor == null || cursor.getCount() == 0)
				return loadedAlbums;

			totalCount = cursor.getCount();

			// Get most recent thumbnail (first row due to DESC sort)
			if (cursor.moveToFirst()) {
				long firstImageId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
				latestThumbnail = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						firstImageId);
			}

			// Reset for normal iteration
			cursor.moveToPosition(-1);

			int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
			int albumNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
			int imageIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);

			while (cursor.moveToNext()) {
				String albumId = cursor.getString(albumIdColumn);
				String albumName = cursor.getString(albumNameColumn);
				long imageId = cursor.getLong(imageIdColumn);
				String relativePath = cursor.getString(relativePathColumn);

				Uri thumbnailUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);

				Album album = albumMap.get(albumId);
				if (album == null) {
					album = new Album(albumId, albumName, 1, thumbnailUri, relativePath);
					albumMap.put(albumId, album);
				} else {
					album.count++;
					album.thumbnailUri = thumbnailUri; // Latest as cover
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to load albums from MediaStore.", e);
			if (getContext() != null) {
				getActivity().runOnUiThread(() -> Toast
						.makeText(getContext(), "Error loading albums: " + e.getLocalizedMessage(), Toast.LENGTH_LONG)
						.show());
			}
		}

		// Add custom "All Photos" first if there are images
		if (totalCount > 0 && latestThumbnail != null) {
			Album allPhotos = new Album("all_photos", "All Photos", totalCount, latestThumbnail, null);
			loadedAlbums.add(allPhotos);
		}

		// Add real albums after
		loadedAlbums.addAll(albumMap.values());

		return loadedAlbums;
	}

	// Album data class
	public static class Album {
		public String id;
		public String name;
		public int count;
		public Uri thumbnailUri;
		public String relativePath; // Make sure this exists

		public Album(String id, String name, int count, Uri thumbnailUri, String relativePath) {
			this.id = id;
			this.name = name;
			this.count = count;
			this.thumbnailUri = thumbnailUri;
			this.relativePath = relativePath;
		}
	}

	// RecyclerView Adapter
	private class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.AlbumViewHolder> {

		@NonNull
		@Override
		public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
			return new AlbumViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
			Album album = albumsList.get(position);
			holder.bind(album);
		}

		@Override
		public int getItemCount() {
			return albumsList.size();
		}

		// View Holder
		class AlbumViewHolder extends RecyclerView.ViewHolder {
			private ImageView thumbnail;
			private TextView title;
			private TextView count;
			private View container;

			public AlbumViewHolder(@NonNull View itemView) {
				super(itemView);
				thumbnail = itemView.findViewById(R.id.albumThumbnail);
				title = itemView.findViewById(R.id.albumTitle);
				count = itemView.findViewById(R.id.albumCount);
				container = itemView.findViewById(R.id.albumContainer);
			}

			public void bind(Album album) {
				title.setText(album.name);
				count.setText(String.valueOf(album.count));

				Glide.with(itemView.getContext()).load(album.thumbnailUri).override(384, 128) // 384x128 (~3x1) to fill the ro row
						.centerCrop().encodeQuality(60) // quality control (0-100, lower = smaller file)
						.into(thumbnail);

				container.setOnClickListener(v -> {
					if (listener != null) {
						listener.onAlbumSelected(album);
					}
				});
			}
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Reload albums if orientation changes (optional)
	}
}
