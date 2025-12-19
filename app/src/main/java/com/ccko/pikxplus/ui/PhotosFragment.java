package com.ccko.pikxplus.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.ContentUris;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.ccko.pikxplus.utils.ImageLoader;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import android.database.Cursor;

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

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import com.ccko.pikxplus.utils.SharedViewModel;
import java.math.BigInteger;

public class PhotosFragment extends Fragment {

	private static final String TAG = "PhotosFragment";
	private RecyclerView recyclerView;
	private PhotosAdapter adapter;
	private List<Photo> photosList = new ArrayList<>();
	private String albumId;
	private String albumName;
	private TextView albumTitle;
	private View invisBar;
	private FloatingActionButton sortButton;
	private FloatingActionButton viewModeButton;
	private int currentSpanCount = 3; // Default grid columns
	private boolean isGridView = true; // true = grid, false = list

	private SearchFragment.Filter currentFilter = null;
	private String folderName;

	// short memory keys (must match ViewerFragment)
	private static final String PREF_KEY_LAST_URI = "viewer_last_image";
	private static final String PREF_KEY_LAST_INDEX = "viewer_last_index";

	private static final String PREF_LAST_ALBUM_ID = "last_album_id";
	private static final String PREF_LAST_ALBUM_NAME = "last_album_name";
	private static final String PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path";

	// Sorting
	private enum SortMode {
		NEWEST_FIRST, OLDEST_FIRST, NAME_ASC, NAME_DESC
	}

	private SortMode currentSortMode = SortMode.NEWEST_FIRST; // default
	private static final String PREF_KEY_SORT_MODE = "photos_sort_mode";

	private SharedViewModel sharedViewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// edge to edge
		//	requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
		//		| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

		// hiding top toolbar
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity != null && activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().hide();
		}

		Bundle args = getArguments();
		if (args != null) {
			albumId = args.getString("album_id");
			albumName = args.getString("album_name");
			folderName = args.getString("folder_name"); // Add this

			// Add debug logging
			if (getActivity() != null) {
				android.util.Log.d("PhotosFragment", "Received album ID: " + albumId);
				android.util.Log.d("PhotosFragment", "Received album name: " + albumName);
			}
		} else {
			android.util.Log.d("PhotosFragment", "No arguments received");
		}

		// Restore saved sort mode if present
		try {
			if (MainActivity.prefs != null) {
				String saved = MainActivity.prefs.getString(PREF_KEY_SORT_MODE, null);
				if (saved != null) {
					try {
						currentSortMode = SortMode.valueOf(saved);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}

		// If no args were provided, try to restore last selected album from prefs
		if ((albumId == null || albumId.isEmpty()) && (albumName == null || albumName.isEmpty())) {
			try {
				if (MainActivity.prefs != null) {
					String savedId = MainActivity.prefs.getString(PREF_LAST_ALBUM_ID, null);
					String savedName = MainActivity.prefs.getString(PREF_LAST_ALBUM_NAME, null);
					String savedRel = MainActivity.prefs.getString(PREF_LAST_ALBUM_RELATIVE_PATH, null);

					if (savedId != null && !savedId.isEmpty()) {
						albumId = savedId;
					}
					if (savedName != null && !savedName.isEmpty()) {
						albumName = savedName;
					}
					if (savedRel != null && !savedRel.isEmpty()) {
						folderName = savedRel;
					}
				}
			} catch (Exception ignored) {
			}
		}
	}

	public void applyFilter(SearchFragment.Filter filter) {
		this.currentFilter = filter;
		loadAlbumPhotos(); // Reload photos with filter applied
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		// hide toolbar
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity != null && activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().hide();
		}

		View view = inflater.inflate(R.layout.fragment_photos, container, false);

		albumTitle = view.findViewById(R.id.albumTitle);
		invisBar = view.findViewById(R.id.invisBar);
		sortButton = view.findViewById(R.id.sortButton);
		viewModeButton = view.findViewById(R.id.viewModeButton);
		recyclerView = view.findViewById(R.id.recyclerViewPhotos);

		// Set up RecyclerView with GridLayoutManager
		GridLayoutManager layoutManager = new GridLayoutManager(getContext(), currentSpanCount);
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.setHasFixedSize(true);

		adapter = new PhotosAdapter();
		recyclerView.setAdapter(adapter);

		// Set up buttons
		sortButton.setOnClickListener(v -> showSortPopupAnchored(sortButton));
		viewModeButton.setOnClickListener(v -> toggleViewMode());

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
		sharedViewModel.getFilter().observe(getViewLifecycleOwner(), new Observer<SearchFragment.Filter>() {
			@Override
			public void onChanged(SearchFragment.Filter filter) {
				if (filter != null) {
					applyFilter(filter); // implement this method to run your query/update UI
				}
			}
		});

		if (albumName != null) {
			albumTitle.setText(albumName);
		}

		// Proceed if we have either an albumId or a folderName (RELATIVE_PATH)
		boolean hasAlbumId = albumId != null && !albumId.isEmpty();
		boolean hasFolderName = folderName != null && !folderName.isEmpty();

		if (hasAlbumId || hasFolderName) {
			loadAlbumPhotos();
		} else {
			Toast.makeText(getContext(), "No album selected", Toast.LENGTH_SHORT).show();
		}
	}

	public void loadAlbumPhotos() {
		new Thread(() -> {
			// loadPhotosFromMediaStore() returns a List<Photo>
			final List<Photo> result = loadPhotosFromMediaStore();

			// Post UI updates
			if (getActivity() != null) {
				getActivity().runOnUiThread(() -> {
					photosList.clear();
					photosList.addAll(result);
					applySort();

					if (photosList.isEmpty()) {
						Toast.makeText(getContext(), "No photos found in this album", Toast.LENGTH_SHORT).show();
					} else {
						// Scroll to last viewed image after RecyclerView has fully laid out
						// Use postDelayed to ensure adapter has finished binding views
						recyclerView.postDelayed(() -> {
							Log.d(TAG, "Attempting to scroll to last viewed image");
							scrollToLastViewedIfApplicable();
						}, 100); // Small delay to ensure layout is complete
					}
				});
			}
		}).start();
	}

	// update the album
	public void setAlbumData(String albumId, String albumName, String folderName) {
		this.albumId = albumId;
		this.albumName = albumName;
		this.folderName = folderName;

		// Update UI
		if (albumTitle != null) {
			albumTitle.setText(albumName);
		}

		// Reload photos with new album data
		loadAlbumPhotos();
	}

	// sort helper
	private void applySort() {
		if (photosList == null || photosList.isEmpty())
			return;

		switch (currentSortMode) {
		case NEWEST_FIRST:
			photosList.sort((a, b) -> Long.compare(b.dateModified, a.dateModified));
			break;
		case OLDEST_FIRST:
			photosList.sort((a, b) -> Long.compare(a.dateModified, b.dateModified));
			break;
		case NAME_ASC:
			photosList.sort(NATURAL_NAME_COMPARATOR);
			break;
		case NAME_DESC:
			photosList.sort(NATURAL_NAME_COMPARATOR.reversed());
			break;
		}

		adapter.notifyDataSetChanged();
	}

	private List<Photo> loadPhotosFromMediaStore() {
		List<Photo> result = new ArrayList<>();
		Context context = getContext();
		if (context == null) {
			Log.e(TAG, "Context is null");
			return result;
		}

		Log.d(TAG, "Loading photos for album: " + albumName + ", ID: " + albumId + ", Folder: " + folderName);

		if ((albumId == null || albumId.isEmpty()) && (folderName == null || folderName.isEmpty())) {
			Log.e(TAG, "No valid album identifier provided");
			return result;
		}

		String[] projection = new String[] { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
				MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.SIZE,
				MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH };

		Cursor cursor = null;
		try {
			String selection;
			String[] selectionArgs;

			// all photoas
			if (albumId != null && albumId.equals("all_photos")) {
				selection = null;
				selectionArgs = null;
				Log.d(TAG, "Loading ALL photos (no filter)");
			} else {

				// 1. Primary approach: Use BUCKET_DISPLAY_NAME (most reliable for Android 11)
				if (albumName != null && !albumName.isEmpty()) {
					selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
					selectionArgs = new String[] { albumName };
					Log.d(TAG, "Querying by album name: " + albumName);
				}
				// 2. Fallback to RELATIVE_PATH with LIKE pattern
				else if (folderName != null && !folderName.isEmpty()) {
					selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
					selectionArgs = new String[] { folderName };
					Log.d(TAG, "Querying by folder pattern: " + folderName);
				}
				// 3. Last resort: BUCKET_ID
				else {
					selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
					selectionArgs = new String[] { albumId };
					Log.d(TAG, "Querying by bucket ID: " + albumId);
				}
			}

			cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
					selection, selectionArgs, MediaStore.Images.Media.DATE_MODIFIED + " DESC");

			if (cursor == null || cursor.getCount() == 0) {
				Log.w(TAG, "First query returned no results");

				// Try broader query - get all images and filter manually
				if (cursor != null)
					cursor.close();
				cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
						null, null, MediaStore.Images.Media.DATE_MODIFIED + " DESC");

				if (cursor == null || cursor.getCount() == 0) {
					Log.e(TAG, "No images found in MediaStore");
					return result;
				}

				Log.d(TAG, "Falling back to manual filtering of " + cursor.getCount() + " images");
				return filterPhotosInMemory(cursor);
			}

			Log.d(TAG, "Query returned " + cursor.getCount() + " results");

			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);

			while (cursor.moveToNext()) {
				long imageId = cursor.getLong(idColumn);
				String name = cursor.getString(nameColumn);
				long dateModified = cursor.getLong(dateColumn);
				long size = cursor.getLong(sizeColumn);

				Uri imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
				result.add(new Photo(String.valueOf(imageId), name, dateModified, size, imageUri));
			}

		} catch (Exception e) {
			Log.e(TAG, "Error loading photos: " + e.getMessage(), e);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		// Apply search filter if present
		if (currentFilter != null && currentFilter.query != null && !currentFilter.query.isEmpty()) {
			List<Photo> filtered = new ArrayList<>();
			String query = currentFilter.query.toLowerCase().trim();

			for (Photo photo : result) {
				if (photo != null && photo.name != null) {
					if (photo.name.toLowerCase().contains(query)) {
						filtered.add(photo);
					}
				}
			}

			return filtered;
		}

		return result;
	}

	// helper method for manual filtering fallback
	private List<Photo> filterPhotosInMemory(Cursor cursor) {
		List<Photo> result = new ArrayList<>();
		try {
			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
			int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
			int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);

			while (cursor.moveToNext()) {
				String currentAlbumName = cursor.getString(bucketNameColumn);
				String relativePath = cursor.getString(relativePathColumn);

				boolean matchesAlbum = albumName != null && albumName.equals(currentAlbumName);
				boolean matchesFolder = folderName != null && relativePath != null && relativePath.equals(folderName);

				if (matchesAlbum || matchesFolder) {
					long imageId = cursor.getLong(idColumn);
					String name = cursor.getString(nameColumn);
					long dateModified = cursor.getLong(dateColumn);
					long size = cursor.getLong(sizeColumn);

					Uri imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
					result.add(new Photo(String.valueOf(imageId), name, dateModified, size, imageUri));
				}
			}

			Log.d(TAG, "Manual filtering found " + result.size() + " matching photos");

		} catch (Exception e) {
			Log.e(TAG, "Error during manual filtering: " + e.getMessage(), e);
		}
		return result;
	}

	// Replace the Name Sort Cases with a Natural Comparator
	private static final Comparator<Photo> NATURAL_NAME_COMPARATOR = (a, b) -> {
		if (a.name == null)
			return -1;
		if (b.name == null)
			return 1;

		String s1 = a.name;
		String s2 = b.name;

		int len1 = s1.length();
		int len2 = s2.length();
		int i = 0, j = 0;

		while (i < len1 && j < len2) {
			char c1 = s1.charAt(i);
			char c2 = s2.charAt(j);

			if (Character.isDigit(c1) && Character.isDigit(c2)) {
				// Skip leading zeros
				while (i < len1 && s1.charAt(i) == '0')
					i++;
				while (j < len2 && s2.charAt(j) == '0')
					j++;

				int numStart1 = i;
				int numStart2 = j;
				while (i < len1 && Character.isDigit(s1.charAt(i)))
					i++;
				while (j < len2 && Character.isDigit(s2.charAt(j)))
					j++;

				String numStr1 = s1.substring(numStart1, i);
				String numStr2 = s2.substring(numStart2, j);

				BigInteger num1 = numStr1.isEmpty() ? BigInteger.ZERO : new BigInteger(numStr1);
				BigInteger num2 = numStr2.isEmpty() ? BigInteger.ZERO : new BigInteger(numStr2);

				int cmp = num1.compareTo(num2);
				if (cmp != 0)
					return cmp;

				// Tiebreaker: longer digit sequence first (natural feel)
				if (numStr1.length() != numStr2.length()) {
					return Integer.compare(numStr2.length(), numStr1.length());
				}
			} else {
				int cmp = Character.toLowerCase(c1) - Character.toLowerCase(c2);
				if (cmp != 0)
					return cmp;
				i++;
				j++;
			}
		}

		return Integer.compare(len1, len2);
	};

	private void scrollToLastViewedIfApplicable() {
		if (recyclerView == null || photosList == null || photosList.isEmpty())
			return;

		// Check if prefs is available
		if (MainActivity.prefs == null) {
			Log.d(TAG, "SharedPreferences not available");
			return;
		}

		// Read saved short memory
		String savedUri = null;
		int savedIndex = -1;
		try {
			savedUri = MainActivity.prefs.getString(PREF_KEY_LAST_URI, null);
			savedIndex = MainActivity.prefs.getInt(PREF_KEY_LAST_INDEX, -1);
			Log.d(TAG, "Trying to restore scroll - URI: " + savedUri + ", Index: " + savedIndex);
		} catch (Exception e) {
			Log.e(TAG, "Error reading scroll position", e);
			return;
		}

		// Nothing to do
		// Nothing to do if both URI and index are invalid
		boolean hasValidUri = savedUri != null;
		boolean hasValidIndex = savedIndex >= 0 && savedIndex < photosList.size();

		if (!hasValidUri && !hasValidIndex) {
			Log.d(TAG, "No valid scroll position to restore");
			return;
		}

		// Prefer URI match (more robust across album changes)
		int matchPos = -1;
		if (savedUri != null) {
			for (int i = 0; i < photosList.size(); i++) {
				Photo p = photosList.get(i);
				if (p != null && p.uri != null && savedUri.equals(p.uri.toString())) {
					matchPos = i;
					break;
				}
			}
		}

		// If no URI match, optionally fall back to saved index if valid and within this list
		if (matchPos < 0 && savedIndex >= 0 && savedIndex < photosList.size()) {
			matchPos = savedIndex;
		}

		if (matchPos < 0)
			return; // still no match

		final int targetPos = matchPos;

		// Post to RecyclerView to ensure layout/adapter are ready
		recyclerView.post(() -> {
			RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
			if (lm instanceof GridLayoutManager) {
				// Try to bring the item into view; offset 0 places it at top of the grid
				((GridLayoutManager) lm).scrollToPositionWithOffset(targetPos, 0);
				Log.d(TAG, "Scrolled to position: " + targetPos);
			} else if (lm instanceof LinearLayoutManager) {
				((LinearLayoutManager) lm).scrollToPositionWithOffset(targetPos, 0);
				Log.d(TAG, "Scrolled to position: " + targetPos);
			} else {
				// fallback
				recyclerView.scrollToPosition(targetPos);
				Log.d(TAG, "Scrolled to position (fallback): " + targetPos);
			}

			// Clear the state after scroll animation completes (delay to let animation finish)
			recyclerView.postDelayed(() -> {
				if (MainActivity.prefs != null) {
					MainActivity.prefs.edit().remove(PREF_KEY_LAST_URI).remove(PREF_KEY_LAST_INDEX).apply();
					Log.d(TAG, "Cleared viewer state after scroll completed");
				}
			}, 500); // Wait 500ms for scroll animation to complete

			// Optional: highlight briefly (visual feedback)
			// View v = recyclerView.findViewHolderForAdapterPosition(targetPos) != null
			//     ? recyclerView.findViewHolderForAdapterPosition(targetPos).itemView : null;
			// if (v != null) {
			//     v.setBackgroundColor(Color.parseColor("#33FFFF00"));
			//     v.postDelayed(() -> v.setBackgroundColor(Color.TRANSPARENT), 700);
			// }
		});
	}

	private void showSortPopupAnchored(View anchor) {
		final String[] items = new String[] { "Newest → Oldest", "Oldest → Newest", "Name A → Z", "Name Z → A" };

		ListPopupWindow popup = new ListPopupWindow(requireContext());
		ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items);
		popup.setAdapter(adapter);
		popup.setAnchorView(anchor); // pass sortButton here
		popup.setModal(true);

		// Optional: set width in dp
		int widthDp = 200;
		int widthPx = (int) (widthDp * getResources().getDisplayMetrics().density);
		popup.setWidth(widthPx);

		popup.setOnItemClickListener((parent, view, position, id) -> {
			switch (position) {
			case 0:
				currentSortMode = SortMode.NEWEST_FIRST;
				break;
			case 1:
				currentSortMode = SortMode.OLDEST_FIRST;
				break;
			case 2:
				currentSortMode = SortMode.NAME_ASC;
				break;
			case 3:
				currentSortMode = SortMode.NAME_DESC;
				break;
			}
			// persist and apply
			try {
				if (MainActivity.prefs != null) {
					MainActivity.prefs.edit().putString(PREF_KEY_SORT_MODE, currentSortMode.name()).apply();
				}
			} catch (Exception ignored) {
			}

			applySort();
			popup.dismiss();
		});

		popup.show();
	}

	private void toggleViewMode() {
		isGridView = !isGridView;

		if (isGridView) {
			// Switch to grid view (3 columns)
			currentSpanCount = 3;
			viewModeButton.setImageResource(R.drawable.ic_grid);
		} else {
			// Switch to list view (1 column)
			currentSpanCount = 1;
			viewModeButton.setImageResource(R.drawable.ic_list);
		}

		// Update layout manager
		GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
		if (layoutManager != null) {
			layoutManager.setSpanCount(currentSpanCount);
		}

		// Update adapter to change item layout
		adapter.notifyDataSetChanged();
	}

	// Photo data class
	public static class Photo {
		public String id;
		public String name;
		public long dateModified;
		public long size;
		public Uri uri;

		public Photo(String id, String name, long dateModified, long size, Uri uri) {
			this.id = id;
			this.name = name;
			this.dateModified = dateModified;
			this.size = size;
			this.uri = uri;
		}
	}

	// RecyclerView Adapter
	private class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder> {

		@NonNull
		@Override
		public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			// Use different layout based on view mode
			int layoutId = isGridView ? R.layout.item_photo_grid : R.layout.item_photo_list;
			View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
			return new PhotoViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
			Photo photo = photosList.get(position);
			holder.bind(photo);
		}

		@Override
		public int getItemCount() {
			return photosList.size();
		}

		// View Holder
		class PhotoViewHolder extends RecyclerView.ViewHolder {
			private ImageView thumbnail;
			private TextView title;
			private TextView details;
			private View container;

			public PhotoViewHolder(@NonNull View itemView) {
				super(itemView);
				thumbnail = itemView.findViewById(R.id.photoThumbnail);
				title = itemView.findViewById(R.id.photoTitle);
				details = itemView.findViewById(R.id.photoDetails);
				container = itemView.findViewById(R.id.photoContainer); // This was missing!

				// Replaced the current container.setOnClickListener in PhotosFragment's PhotoViewHolder
				container.setOnClickListener(v -> {
					// Get the current position of this item in the adapter
					int position = getAdapterPosition();
					if (position == RecyclerView.NO_POSITION)
						return;

					// Create a list of all image URIs in the current album
					ArrayList<Uri> allImageUris = new ArrayList<>();
					for (Photo photo : photosList) {
						allImageUris.add(photo.uri);
					}

					// Create ViewerFragment with arguments
					ViewerFragment viewerFragment = new ViewerFragment();
					Bundle args = new Bundle();
					args.putParcelableArrayList("image_uris", allImageUris);
					args.putInt("current_index", position);
					args.putBoolean("user_picked", true); // Important: mark as user-selected
					viewerFragment.setArguments(args);

					// Open viewer using MainActivity's method
					if (getActivity() instanceof MainActivity) {
						((MainActivity) getActivity()).openViewer(viewerFragment);
					}
				});
			}

			// REPLACE the existing bind(Photo photo) method with this:
			public void bind(Photo photo) {
				if (isGridView) {
					// Grid view: show only thumbnail
					// FIX: Check if title/details exist before accessing them
					if (title != null)
						title.setVisibility(View.GONE);
					if (details != null)
						details.setVisibility(View.GONE);
				} else {
					// List view: show title and details
					// FIX: Check if title exists
					if (title != null) {
						title.setVisibility(View.VISIBLE);
						title.setText(photo.name);
					}
					// FIX: Check if details exists
					if (details != null) {
						details.setVisibility(View.VISIBLE);
						// Fix encoding issue in separator if needed, or just use " - "
						details.setText(formatDate(photo.dateModified) + " - " + formatSize(photo.size));
					}
				}

				// Use Glide for efficient image loading, caching, and downsampling.
				int targetSize = isGridView ? 120 : 200; // Reduced from 150/250

				Glide.with(itemView.getContext()).load(photo.uri).override(targetSize, targetSize).centerCrop()
						.encodeQuality(60) // Add quality control (70 is good for thumbnails)
						.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(thumbnail);
			}
		}
	}

	private String formatDate(long timestamp) {
		// Simple date formatting for phone IDE development
		java.util.Date date = new java.util.Date(timestamp * 1000);
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy");
		return sdf.format(date);
	}

	// Add this public method to PhotosFragment.java
	public void reloadPhotos() {
		if (albumId != null && !albumId.isEmpty()) {
			loadAlbumPhotos();
		}
	}

	private String formatSize(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(1024));
		String pre = ("KMGTPE").charAt(exp - 1) + "";
		return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Try to scroll when fragment becomes visible
		if (recyclerView != null && !photosList.isEmpty()) {
			recyclerView.postDelayed(() -> {
				Log.d(TAG, "onResume: Attempting scroll");
				scrollToLastViewedIfApplicable();
			}, 150);
		}
	}
}