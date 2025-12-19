package com.ccko.pikxplus;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.ccko.pikxplus.ui.AlbumsFragment;
import com.ccko.pikxplus.ui.PhotosFragment;
import com.ccko.pikxplus.ui.SearchFragment;
import com.ccko.pikxplus.ui.ViewerFragment;
import com.ccko.pikxplus.ui.StorageFragment;
import com.ccko.pikxplus.ui.AlbumsFragment.Album;
import com.ccko.pikxplus.adapters.MainFragmentAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

//public class MainActivity extends AppCompatActivity implements AlbumsFragment.OnAlbumSelectedListener {
public class MainActivity extends AppCompatActivity
		implements AlbumsFragment.OnAlbumSelectedListener, ViewerFragment.OnImageDeletedListener {

	private static final int STORAGE_PERMISSION_CODE = 1001;
	private BottomNavigationView bottomNavigationView;
	private androidx.viewpager2.widget.ViewPager2 viewPager;
	private MainFragmentAdapter pagerAdapter;
	private boolean isViewerActive = false;

	// SharedPreferences
	public static SharedPreferences prefs;

	// in MainActivity (top)
	private static final String PREF_LAST_ALBUM_ID = "last_album_id";
	private static final String PREF_LAST_ALBUM_NAME = "last_album_name";
	private static final String PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Request storage permission (for personal app on Android 11)
		checkStoragePermission();

		prefs = getSharedPreferences("pikxplus_prefs", MODE_PRIVATE);

		// Setup ViewPager2
		viewPager = findViewById(R.id.viewPager);
		pagerAdapter = new MainFragmentAdapter(this);
		viewPager.setAdapter(pagerAdapter);

		// Enable user swipe input
		viewPager.setUserInputEnabled(true);

		// Add edge-only swipe with sensitivity control
		//	setupEdgeSwipe();

		// Set default page (Albums)
		viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, false);

		// Setup bottom navigation
		bottomNavigationView = findViewById(R.id.bottomNavigation);
		bottomNavigationView.setSelectedItemId(R.id.nav_albums);

		// Sync ViewPager with BottomNav
		setupViewPagerWithBottomNav();
	}

	private void checkStoragePermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				// Show explanation dialog
				new AlertDialog.Builder(this).setTitle("Storage Permission Needed")
						.setMessage("This app needs access to your photos to display them in your gallery.")
						.setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
								new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, STORAGE_PERMISSION_CODE))
						.setNegativeButton("Cancel", (dialog, which) -> finish()).create().show();
			} else {
				// No explanation needed, request permission directly
				ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
						STORAGE_PERMISSION_CODE);
			}
		}
	}

	private void setupViewPagerWithBottomNav() {
		// Listen to ViewPager page changes and update BottomNav
		viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);

				// Update bottom nav selection without triggering listener
				switch (position) {
				case MainFragmentAdapter.POSITION_STORAGE:
					bottomNavigationView.setSelectedItemId(R.id.nav_storage);
					break;
				case MainFragmentAdapter.POSITION_ALBUMS:
					bottomNavigationView.setSelectedItemId(R.id.nav_albums);
					break;
				case MainFragmentAdapter.POSITION_PHOTOS:
					bottomNavigationView.setSelectedItemId(R.id.nav_photos);
					break;
				case MainFragmentAdapter.POSITION_SEARCH:
					bottomNavigationView.setSelectedItemId(R.id.nav_search);
					break;
				case MainFragmentAdapter.POSITION_MORE:
					bottomNavigationView.setSelectedItemId(R.id.nav_more);
					break;
				}
			}
		});

		// Listen to BottomNav clicks and update ViewPager
		bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
			int position = -1;

			switch (item.getItemId()) {
			case R.id.nav_storage:
				position = MainFragmentAdapter.POSITION_STORAGE;
				break;
			case R.id.nav_albums:
				position = MainFragmentAdapter.POSITION_ALBUMS;
				break;
			case R.id.nav_photos:
				position = MainFragmentAdapter.POSITION_PHOTOS;
				break;
			case R.id.nav_search:
				position = MainFragmentAdapter.POSITION_SEARCH;
				break;
			case R.id.nav_more:
				Toast.makeText(MainActivity.this, "There is Nothing More!", Toast.LENGTH_SHORT).show();
				return true;
			}

			if (position != -1) {
				viewPager.setCurrentItem(position, true); // true = smooth scroll
			}
			return true;
		});
	}

	/**
		private void setupEdgeSwipe() {
			// Get the RecyclerView inside ViewPager2 (ViewPager2's child is a RecyclerView)
			View recyclerView = viewPager.getChildAt(0);
			if (recyclerView == null)
				return;
	
			recyclerView.setOnTouchListener(new View.OnTouchListener() {
				private float startX = 0f;
				private boolean isEdgeSwipe = false;
	
				// 0.20 = 20% of screen width from each edge
				private final float EDGE_THRESHOLD = 0.15f;
	
				@override
				public boolean onTouch(View v, MotionEvent event) {
					int screenWidth = v.getWidth();
					float edgeSize = screenWidth * EDGE_THRESHOLD;
					int action = event.getActionMasked();
	
					switch (action) {
					case MotionEvent.ACTION_DOWN:
						startX = event.getX();
						// If touch started at the edge, allow parent (ViewPager) to intercept
						isEdgeSwipe = (startX < edgeSize) || (startX > screenWidth - edgeSize);
						// If it's NOT an edge swipe, tell parent NOT to intercept so RecyclerView handles gestures
						v.getParent().requestDisallowInterceptTouchEvent(!isEdgeSwipe);
						// Return false so the RecyclerView still receives the DOWN event and can start gesture detection
						return false;
	
					case MotionEvent.ACTION_MOVE:
						// While moving, keep the same interception decision made on ACTION_DOWN.
						// If it's not an edge swipe, keep parent disallowed from intercepting.
						v.getParent().requestDisallowInterceptTouchEvent(!isEdgeSwipe);
						// Let RecyclerView handle the move events (return false)
						return false;
	
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						// Restore default: allow parent to intercept future gestures
						v.getParent().requestDisallowInterceptTouchEvent(false);
						isEdgeSwipe = false;
						return false;
	
					default:
						return false;
					}
				}
			});
		}
	
		
			// swipe Sensitivity
			private void setupEdgeSwipe() {
				// Get the RecyclerView inside ViewPager2
				View recyclerView = viewPager.getChildAt(0);
				if (recyclerView != null) {
					recyclerView.setOnTouchListener(new View.OnTouchListener() {
						private float startX = 0f;
						private boolean isEdgeSwipe = false;
		
						// Adjust this value: 0.15 = 15% of screen width from each edge
						// Try values between 0.10 (10%) and 0.25 (25%)
						private final float EDGE_THRESHOLD = 0.20f;
		
						@Override
						public boolean onTouch(View v, android.view.MotionEvent event) {
							int screenWidth = v.getWidth();
							float edgeSize = screenWidth * EDGE_THRESHOLD;
		
							switch (event.getAction()) {
							case android.view.MotionEvent.ACTION_DOWN:
								startX = event.getX();
		
								// Check if touch started at edge
								if (startX < edgeSize || startX > screenWidth - edgeSize) {
									isEdgeSwipe = true;
									return false; // Let ViewPager handle it
								} else {
									isEdgeSwipe = false;
									return true; // Block the swipe
								}
		
							case android.view.MotionEvent.ACTION_MOVE:
								if (!isEdgeSwipe) {
									return true; // Block non-edge swipes
								}
								break;
		
							case android.view.MotionEvent.ACTION_UP:
							case android.view.MotionEvent.ACTION_CANCEL:
								isEdgeSwipe = false;
								break;
							}
		
							return false; // Let ViewPager handle edge swipes
						}
					});
				}
			}
		
				
			@Override
			public void onAlbumSelected(AlbumsFragment.Album album) {
				// Save last selected album to prefs
				try {
					prefs.edit().putString(PREF_LAST_ALBUM_ID, album.id).putString(PREF_LAST_ALBUM_NAME, album.name)
							.putString(PREF_LAST_ALBUM_RELATIVE_PATH, album.relativePath == null ? "" : album.relativePath)
							.apply();
				} catch (Exception ignored) {
				}
			
				PhotosFragment photosFragment = new PhotosFragment();
				Bundle args = new Bundle();
			
				// Always use the album name for querying - most reliable approach
				args.putString("album_id", album.id);
				args.putString("album_name", album.name);
			
				if (album.relativePath != null) {
					args.putString("folder_name", album.relativePath);
				}
			
				photosFragment.setArguments(args);
				getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, photosFragment)
						.addToBackStack(null).setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit();
			
				// Update bottom navigation selection
				bottomNavigationView.setOnNavigationItemSelectedListener(null);
				bottomNavigationView.setSelectedItemId(R.id.nav_photos);
				bottomNavigationView.setOnNavigationItemSelectedListener(navListener);
			}
			**/

	@Override
	public void onAlbumSelected(AlbumsFragment.Album album) {
		// Save last selected album to prefs
		try {
			prefs.edit().putString(PREF_LAST_ALBUM_ID, album.id).putString(PREF_LAST_ALBUM_NAME, album.name)
					.putString(PREF_LAST_ALBUM_RELATIVE_PATH, album.relativePath == null ? "" : album.relativePath)
					.apply();
		} catch (Exception ignored) {
		}

		// Get the PhotosFragment from ViewPager and update it
		Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
		if (fragment instanceof PhotosFragment) {
			((PhotosFragment) fragment).setAlbumData(album.id, album.name, album.relativePath);
		}

		// Navigate to Photos tab in ViewPager
		viewPager.setCurrentItem(MainFragmentAdapter.POSITION_PHOTOS, true);
	}

	// Method to open ViewerFragment (overlay mode)
	public void openViewer(ViewerFragment viewerFragment) {
		isViewerActive = true;

		// Disable ViewPager swiping
		viewPager.setUserInputEnabled(false);

		// Show fragmentContainer and hide ViewPager
		findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);
		viewPager.setVisibility(View.GONE);

		// Open ViewerFragment in overlay container
		getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, viewerFragment)
				.addToBackStack(null).commit();
	}

	// Method to close ViewerFragment
	public void closeViewer() {
		isViewerActive = false;

		// Re-enable ViewPager swiping
		viewPager.setUserInputEnabled(true);

		// Hide fragmentContainer and show ViewPager
		findViewById(R.id.fragmentContainer).setVisibility(View.GONE);
		viewPager.setVisibility(View.VISIBLE);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
			@NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == STORAGE_PERMISSION_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission granted - reload albums
				Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
				if (currentFragment instanceof AlbumsFragment) {
					((AlbumsFragment) currentFragment).loadAlbums();
				}
			} else {
				// Permission denied
				Toast.makeText(this, "Storage permission is required to view photos", Toast.LENGTH_LONG).show();

				// For personal app, we can try to request again or finish
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
						Manifest.permission.READ_EXTERNAL_STORAGE)) {
					// User selected "Don't ask again"
					Toast.makeText(this, "Please grant permission in Settings to use this app", Toast.LENGTH_LONG)
							.show();
					finish();
				} else {
					// Try requesting again after a short delay
					new Handler(Looper.getMainLooper())
							.postDelayed(() -> ActivityCompat.requestPermissions(MainActivity.this,
									new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
									STORAGE_PERMISSION_CODE), 1000);
				}
			}
		}
	}

	public void onImageDeleted(int deletedIndex) {
		// Get PhotosFragment from ViewPager and refresh it
		Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
		if (fragment instanceof PhotosFragment) {
			((PhotosFragment) fragment).loadAlbumPhotos();
		}
	}

	@Override
	public void onBackPressed() {
		if (isViewerActive) {
			// Close viewer and return to ViewPager
			closeViewer();
			getSupportFragmentManager().popBackStack();
		} else {
			// Check current ViewPager position
			int currentPosition = viewPager.getCurrentItem();

			// If on Photos tab, go back to Albums
			if (currentPosition == MainFragmentAdapter.POSITION_PHOTOS) {
				viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
			}
			// If on Albums tab (or any other main tab), exit app
			else if (currentPosition == MainFragmentAdapter.POSITION_ALBUMS
					|| currentPosition == MainFragmentAdapter.POSITION_STORAGE) {
				super.onBackPressed();
			}
			// For other tabs (Search, More), go back to Albums first
			else {
				viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
			}
		}
	}
}