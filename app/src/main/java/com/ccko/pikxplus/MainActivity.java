package com.ccko.pikxplus;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.ccko.pikxplus.ui.AlbumsFragment;
import com.ccko.pikxplus.ui.PhotosFragment;
import com.ccko.pikxplus.ui.ViewerFragment;
import com.ccko.pikxplus.ui.AlbumsFragment.Album;
import com.ccko.pikxplus.ui.GestureOverlayView;
import com.ccko.pikxplus.adapters.MainFragmentAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity
		implements AlbumsFragment.OnAlbumSelectedListener, ViewerFragment.OnImageDeletedListener {

	private static final int STORAGE_PERMISSION_CODE = 1001;
	private BottomNavigationView bottomNavigationView;
	private androidx.viewpager2.widget.ViewPager2 viewPager;
	private MainFragmentAdapter pagerAdapter;
	private boolean isViewerActive = false;

	// NEW: Gesture overlay
	private GestureOverlayView gestureOverlay;

	public static SharedPreferences prefs;

	private static final String PREF_LAST_ALBUM_ID = "last_album_id";
	private static final String PREF_LAST_ALBUM_NAME = "last_album_name";
	private static final String PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// Enable edge-to-edge
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		// Initialize views FIRST
		bottomNavigationView = findViewById(R.id.bottomNavigation);
		viewPager = findViewById(R.id.viewPager);
		gestureOverlay = findViewById(R.id.gestureOverlay);

		// Correct root view
		View root = findViewById(android.R.id.content);

		// Apply insets AFTER views exist
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

			// Bottom nav respects bottom inset
			bottomNavigationView.setPadding(bottomNavigationView.getPaddingLeft(), bottomNavigationView.getPaddingTop(),
					bottomNavigationView.getPaddingRight(), systemBars.bottom);

			// ViewPager respects top + bottom
			viewPager.setPadding(0, systemBars.top, 0, 0);

			return WindowInsetsCompat.CONSUMED;
		});

		checkStoragePermission();
		prefs = getSharedPreferences("pikxplus_prefs", MODE_PRIVATE);

		// Initialize gesture overlay

		gestureOverlay.setDeadzones(0.10f, 0.10f); // 10% top and bottom

		// Setup ViewPager2

		pagerAdapter = new MainFragmentAdapter(this);
		viewPager.setAdapter(pagerAdapter);
		viewPager.setUserInputEnabled(true);
		viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, false);

		// Setup bottom navigation

		bottomNavigationView.setSelectedItemId(R.id.nav_albums);

		setupViewPagerWithBottomNav();
	}

	private void checkStoragePermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(this).setTitle("Storage Permission Needed")
						.setMessage("This app needs access to your photos to display them in your gallery.")
						.setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
								new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, STORAGE_PERMISSION_CODE))
						.setNegativeButton("Cancel", (dialog, which) -> finish()).create().show();
			} else {
				ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
						STORAGE_PERMISSION_CODE);
			}
		}
	}

	private void setupViewPagerWithBottomNav() {
		viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);

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
				viewPager.setCurrentItem(position, true);
			}
			return true;
		});
	}

	@Override
	public void onAlbumSelected(AlbumsFragment.Album album) {
		try {
			prefs.edit().putString(PREF_LAST_ALBUM_ID, album.id).putString(PREF_LAST_ALBUM_NAME, album.name)
					.putString(PREF_LAST_ALBUM_RELATIVE_PATH, album.relativePath == null ? "" : album.relativePath)
					.apply();
		} catch (Exception ignored) {
		}

		Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
		if (fragment instanceof PhotosFragment) {
			((PhotosFragment) fragment).setAlbumData(album.id, album.name, album.relativePath);
		}

		viewPager.setCurrentItem(MainFragmentAdapter.POSITION_PHOTOS, true);
	}

	// Method to open ViewerFragment (overlay mode) - NOW CONTROLS GESTURE ROUTING
	public void openViewer(ViewerFragment viewerFragment) {
		isViewerActive = true;
		setViewerMode(true);

		// Disable ViewPager swiping
		viewPager.setUserInputEnabled(false);

		// Show fragmentContainer and hide ViewPager
		findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);
		viewPager.setVisibility(View.GONE);

		// Open ViewerFragment in overlay container
		getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, viewerFragment)
				.addToBackStack(null).commit();

		// NEW: Switch gesture overlay to viewer mode
		// ViewerFragment will register its gesture handler in onResume()
	}

	// Method to close ViewerFragment - RESTORES GESTURE ROUTING
	public void closeViewer() {
		isViewerActive = false;
		setViewerMode(false);

		// Re-enable ViewPager swiping
		viewPager.setUserInputEnabled(true);

		// Hide fragmentContainer and show ViewPager
		findViewById(R.id.fragmentContainer).setVisibility(View.GONE);
		viewPager.setVisibility(View.VISIBLE);

		// NEW: Clear gesture delegate (return to ViewPager control)
		if (gestureOverlay != null) {
			gestureOverlay.clearDelegate();
		}
	}

	// NEW: Public method for fragments to register gesture handlers
	public void setGestureDelegate(GestureOverlayView.GestureDelegate delegate) {
		if (gestureOverlay != null) {
			gestureOverlay.setGestureDelegate(delegate);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
			@NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == STORAGE_PERMISSION_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
				if (currentFragment instanceof AlbumsFragment) {
					((AlbumsFragment) currentFragment).loadAlbums();
				}
			} else {
				Toast.makeText(this, "Storage permission is required to view photos", Toast.LENGTH_LONG).show();

				if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
						Manifest.permission.READ_EXTERNAL_STORAGE)) {
					Toast.makeText(this, "Please grant permission in Settings to use this app", Toast.LENGTH_LONG)
							.show();
					finish();
				} else {
					new Handler(Looper.getMainLooper())
							.postDelayed(() -> ActivityCompat.requestPermissions(MainActivity.this,
									new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
									STORAGE_PERMISSION_CODE), 1000);
				}
			}
		}
	}

	private void animateViewerOverlay(boolean show) {
		View overlay = findViewById(R.id.fragmentContainer);

		if (show) {
			overlay.setAlpha(0f);
			overlay.setVisibility(View.VISIBLE);
			overlay.animate().alpha(1f).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator())
					.start();
		} else {
			overlay.animate().alpha(0f).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator())
					.withEndAction(() -> overlay.setVisibility(View.GONE)).start();
		}
	}

	private void animateBottomNav(boolean show) {
		float target = show ? 0 : bottomNavigationView.getHeight();

		bottomNavigationView.animate().translationY(target).setDuration(500)
				.setInterpolator(new AccelerateDecelerateInterpolator()).withStartAction(() -> {
					if (show)
						bottomNavigationView.setVisibility(View.VISIBLE);
				}).withEndAction(() -> {
					if (!show)
						bottomNavigationView.setVisibility(View.GONE);
				}).start();
	}

	public void setViewerModeEnabled(boolean enabled) {
		setViewerMode(enabled);
	}

	// hide/show system bar and toolbars
	private void setViewerMode(boolean enabled) {
		Window window = getWindow();
		WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());

		if (enabled) {
			// --- Immersive mode ---
			WindowCompat.setDecorFitsSystemWindows(window, false);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				WindowManager.LayoutParams lp = window.getAttributes();
				lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
				window.setAttributes(lp);
			}

			// Transparent bars for immersive feel
			window.setStatusBarColor(Color.TRANSPARENT);
			window.setNavigationBarColor(Color.TRANSPARENT);

			if (controller != null) {
				// Hide both bars
				controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
				// Option 1: Immersive swipe behavior
				controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}

			animateBottomNav(false);
			animateViewerOverlay(true);

		} else {
			// --- Normal mode: give bars back ---
			WindowCompat.setDecorFitsSystemWindows(window, true);

			// Option 1: Accessing the library colors directly via full package name
			int primaryDark = ContextCompat.getColor(this,
					com.google.android.material.R.color.design_default_color_primary_dark);
			int primary = ContextCompat.getColor(this,
					com.google.android.material.R.color.design_default_color_primary);

			window.setStatusBarColor(primaryDark);
			window.setNavigationBarColor(primary);

			if (controller != null) {
				controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
			}

			animateBottomNav(true);
			animateViewerOverlay(false);
		}
	}

	public void onImageDeleted(int deletedIndex) {
		Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
		if (fragment instanceof PhotosFragment) {
			((PhotosFragment) fragment).loadAlbumPhotos();
		}
	}

	@Override
	public void onBackPressed() {
		if (isViewerActive) {
			closeViewer();
			getSupportFragmentManager().popBackStack();
		} else {
			int currentPosition = viewPager.getCurrentItem();

			if (currentPosition == MainFragmentAdapter.POSITION_PHOTOS) {
				viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
			} else if (currentPosition == MainFragmentAdapter.POSITION_ALBUMS
					|| currentPosition == MainFragmentAdapter.POSITION_STORAGE) {
				super.onBackPressed();
			} else {
				viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
			}
		}
	}
}