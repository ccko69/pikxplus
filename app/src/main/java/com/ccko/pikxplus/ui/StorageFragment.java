package com.ccko.pikxplus.ui;

import android.os.Build;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowCompat;
import com.ccko.pikxplus.R;
import androidx.fragment.app.Fragment;
import com.ccko.pikxplus.features.OpticalIllusionView;

public class StorageFragment extends Fragment {

	private OpticalIllusionView illusionView;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		//  modern edge-to-edge API
		Window window = requireActivity().getWindow();
		WindowCompat.setDecorFitsSystemWindows(window, false);

		// 2) Allow layout into the display cutout (API 28+)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			window.setAttributes(lp);
		}

		// 3) Make status/navigation bars transparent (optional)
		window.setStatusBarColor(Color.TRANSPARENT);
		window.setNavigationBarColor(Color.TRANSPARENT);

		// 4) Hide bars in an immersive way (use compat controller)
		WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
		insetsController.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
		insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

		// 5) Fallback for older devices (optional)
		window.getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
						| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
						| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

		// If you already have a layout, add the view into a container there.
		// For simplicity, create the view and return it as the fragment root.
		illusionView = new OpticalIllusionView(requireContext());
		// Optionally set layout params if adding to an existing layout
		illusionView.setLayoutParams(
				new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return illusionView;
	}

	private void restoreSystemBars() {
		Window window = requireActivity().getWindow();

		// 1. Tell the controller to show the bars again
		WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());

		// Show both status and navigation bars
		insetsController.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());

		// Reset behavior to default (non-transient)
		insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH);

		// 2. Reset DecorFitsSystemWindows (Optional: set to true if you want the app 
		// to stop drawing behind the bars and go back to a standard layout)
		WindowCompat.setDecorFitsSystemWindows(window, true);

		// 3. Fallback: Clear the legacy SystemUiVisibility flags
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Clearing flags returns them to the default system state
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		}

		// 4. (Optional) Reset the colors if you don't want them transparent anymore
		window.setStatusBarColor(ContextCompat.getColor(getContext(), android.R.color.black));
		window.setNavigationBarColor(Color.BLACK);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (illusionView != null)
			illusionView.start();

		// edge to edge
		requireActivity().getWindow().getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		//requireActivity().getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
		//requireActivity().getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

		// hiding top toolbar
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity != null && activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().hide();

		}
		// hiding bottom nav bar
		requireActivity().findViewById(R.id.bottomNavigation).setVisibility(View.GONE);

	}

	@Override
	public void onPause() {
		super.onPause();
		restoreSystemBars();
		// stop the illusion if you haven't already
		if (illusionView != null)
			illusionView.stop();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		restoreSystemBars();

	}
}