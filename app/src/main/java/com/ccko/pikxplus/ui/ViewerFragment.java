package com.ccko.pikxplus.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;
import android.widget.Button;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.view.inputmethod.InputMethodManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.Glide;
import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.google.android.material.textfield.TextInputEditText;
import com.ccko.pikxplus.utils.ImageLoader;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import android.os.Build;
import androidx.annotation.RequiresApi;

import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import androidx.fragment.app.FragmentActivity;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import com.ccko.pikxplus.features.GestureAndSlideShow;
import com.ccko.pikxplus.features.GestureAndSlideShow;

public class ViewerFragment extends Fragment {

	private static final String TAG = "ViewerFragment";
	private ImageView imageView;
	private TextView imageIndexText;
	private TextView imageNameText;
	private View topBar;
	private View bottomBar;
	private ImageButton backButton;
	private ImageButton deleteButton;
	private ImageButton infoButton;
	private ImageButton menuButton;
	private View leftHotspot;
	private View rightHotspot;
	private View rootView;

	private boolean isUiVisible = true;

	// Image navigation
	private List<Uri> imageUris;
	private int currentIndex = 0;
	private float scaleFactor = 1.0f;
	private Matrix imageMatrix = new Matrix();

	private GestureAndSlideShow.ImageViewerGestureHandler gestureHandler;
	private boolean isSlideShowRunning = false;

	private float fitScale = 1.0f; // Base fit scale (computed per image)
	private float minTempScale = 0.7f; // Allow temporary zoom-out to this during gesture
	// Callback interface

	// short memory
	private static final String PREF_KEY_LAST_URI = "viewer_last_image";
	private static final String PREF_KEY_LAST_INDEX = "viewer_last_index";
	private static final String STATE_KEY_URI = "state_last_image_uri";
	private static final String STATE_KEY_INDEX = "state_last_image_index";
	private boolean userPickedImage = false; // set true when user explicitly selects a thumbnail

	public interface OnImageDeletedListener {
		void onImageDeleted(int deletedIndex);

	}

	private OnImageDeletedListener deleteListener;

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		try {
			deleteListener = (OnImageDeletedListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement OnImageDeletedListener");
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		rootView = inflater.inflate(R.layout.fragment_viewer, container, false);
		// Initialize views
		imageView = rootView.findViewById(R.id.imageView);
		imageIndexText = rootView.findViewById(R.id.imageIndex);
		imageNameText = rootView.findViewById(R.id.imageName);
		topBar = rootView.findViewById(R.id.topBar);
		bottomBar = rootView.findViewById(R.id.bottomBar);
		backButton = rootView.findViewById(R.id.backButton);
		deleteButton = rootView.findViewById(R.id.deleteButton);
		infoButton = rootView.findViewById(R.id.infoButton);
		menuButton = rootView.findViewById(R.id.menuButton);

		// gesture and slideshow wiring
		// CHANGED: Initialize the new gesture handler
		gestureHandler = new GestureAndSlideShow.ImageViewerGestureHandler(requireContext(),
				new GestureAndSlideShow.ImageViewerGestureHandler.HostCallback() {
					@Override
					public void onDoubleTap(float x, float y) {
						imageView.post(() -> {
							if (scaleFactor > 1.1f) {
								Matrix target = computeInitialMatrix(imageView);
								clampMatrixForMatrix(target);
								animateToMatrix(target, 1.0f);
							} else {
								Matrix targetMatrix = new Matrix(imageMatrix);
								float zoom = 2f;
								targetMatrix.postScale(zoom, zoom, x, y);
								float targetScale = scaleFactor * zoom;
								clampMatrixForMatrix(targetMatrix);
								animateToMatrix(targetMatrix, targetScale);
							}
						});
					}

					@Override
					public void onSlideShowNext() {
						showNextImageWithFade();
					}

					@Override
					public void onSlideShowStopped() {
						showUiElements();
						isSlideShowRunning = false;
					}

					@Override
					public void onLongPress() {
						toggleUiVisibility();
						if (isSlideShowRunning) {
							stopSlideShow();
						}
					}

					@Override
					public void onPan(float distanceX, float distanceY) {
						imageView.post(() -> {
							if (scaleFactor > 1.01f) {
								imageMatrix.postTranslate(-distanceX, -distanceY);
								clampMatrix();
								imageView.setImageMatrix(imageMatrix);
							}
						});
					}

					@Override
					public void onScale(float scaleDelta, float focusX, float focusY) {
						imageView.post(() -> {
							float newScale = scaleFactor * scaleDelta;
							newScale = Math.max(minTempScale, Math.min(newScale, 30.0f));
							if (newScale != scaleFactor) {
								float appliedDelta = newScale / scaleFactor;
								scaleFactor = newScale;
								imageMatrix.postScale(appliedDelta, appliedDelta, focusX, focusY);
								clampMatrix();
								imageView.setImageMatrix(imageMatrix);
							}
						});
					}

					@Override
					public void onScaleEnd() {
						imageView.post(() -> {
							if (scaleFactor < 1.0f) {
								animateScaleTo(1.0f);
							}
						});
					}

					@Override
					public void onRequestClose() {
						imageView.post(() -> {
							final float ZOOM_CLOSE_THRESHOLD = 1.02f;
							boolean currentlyScalingOrPanning = gestureHandler != null
									&& (gestureHandler.isScaling || gestureHandler.isPanning);
							boolean zoomedIn = scaleFactor > ZOOM_CLOSE_THRESHOLD;

							if (currentlyScalingOrPanning || zoomedIn) {
								imageView.animate().translationY(0).setDuration(180).start();
								return;
							}

							imageView.animate().translationY(imageView.getHeight()).setDuration(250)
									.withEndAction(() -> {
										if (getActivity() != null)
											getActivity().onBackPressed();
									}).start();
						});
					}

					@Override
					public void onNextImageRequested() {
						imageView.post(() -> {
							if (imageUris == null || imageUris.isEmpty())
								return;
							if (currentIndex < imageUris.size() - 1) {
								imageView.animate().translationX(-imageView.getWidth()).setDuration(300)
										.withEndAction(() -> {
											loadImageAtIndex(currentIndex + 1);
											imageView.setTranslationX(imageView.getWidth());
											imageView.animate().translationX(0).setDuration(300).start();
										}).start();
							}
						});
					}

					@Override
					public void onPreviousImageRequested() {
						imageView.post(() -> {
							if (imageUris == null || imageUris.isEmpty())
								return;
							if (currentIndex > 0) {
								imageView.animate().translationX(imageView.getWidth()).setDuration(250)
										.withEndAction(() -> {
											loadImageAtIndex(currentIndex - 1);
											imageView.setTranslationX(-imageView.getWidth());
											imageView.animate().translationX(0).setDuration(250).start();
										}).start();
							}
						});
					}
				}, requireActivity());

		// Setup listeners
		backButton.setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				getActivity().onBackPressed();
			}
		});
		deleteButton.setOnClickListener(v -> confirmDeleteImage());
		infoButton.setOnClickListener(v -> showImageInfo());
		//rootView.setOnClickListener(v -> toggleUiVisibility());

		// auto-hide UI onCreate
		hideUiElements();
		return rootView;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// bind only the hotspots here (other views already bound in onCreateView)
		leftHotspot = view.findViewById(R.id.leftHotspot);
		rightHotspot = view.findViewById(R.id.rightHotspot);

		// in Fragment.onViewCreated or in the custom view constructor
		View root = requireActivity().findViewById(android.R.id.content); // or your fragment root
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
			// If you truly want to ignore all system insets, return CONSUMED so no padding is applied
			return WindowInsetsCompat.CONSUMED;
		});
		ViewCompat.requestApplyInsets(root);

		// Hotspot listeners (lowest priority: check hotspotAllowed())
		// CHANGED: Hotspot listeners now check gesture state from new handler
		leftHotspot.setOnTouchListener((v, event) -> {
			if (!hotspotAllowed())
				return false;
			if (event.getAction() == MotionEvent.ACTION_UP) {
				v.performClick();
				if (currentIndex > 0)
					loadImageAtIndex(currentIndex - 1);
				return true;
			}
			return false;
		});

		rightHotspot.setOnTouchListener((v, event) -> {
			if (!hotspotAllowed())
				return false;
			if (event.getAction() == MotionEvent.ACTION_UP) {
				v.performClick();
				if (currentIndex < imageUris.size() - 1)
					loadImageAtIndex(currentIndex + 1);
				return true;
			}
			return false;
		});

		// Menu button for slideshow
		menuButton.setOnClickListener(v -> {
			PopupMenu menu = new PopupMenu(requireContext(), menuButton);
			if (!gestureHandler.isSlideShowActive()) {
				menu.getMenu().add("Slideshow  »");
			} else {
				menu.getMenu().add("Slideshow  ∎");
			}
			menu.setOnMenuItemClickListener(item -> {
				if (item.getTitle().equals("Slideshow  »")) {
					askForSlideShowInterval();
				} else {
					stopSlideShow();
				}
				return true;
			});
			menu.show();
		});

		// ========
		// calling the viewer: read args, allow optional "user_picked" flag
		Bundle args = getArguments();
		if (args != null) {
			imageUris = args.getParcelableArrayList("image_uris");
			int incomingIndex = args.getInt("current_index", 0);

			// optional: caller can set this to true when opening from a thumbnail
			userPickedImage = args.getBoolean("user_picked", false);

			if (imageUris == null || imageUris.isEmpty()) {
				Toast.makeText(getContext(), "No images to display", Toast.LENGTH_SHORT).show();
				requireActivity().onBackPressed();
				return;
			}

			// clamp incomingIndex
			currentIndex = Math.max(0, Math.min(incomingIndex, imageUris.size() - 1));
			// Try to restore short memory (savedInstanceState first, then prefs)
			restoreViewerStateIfNeeded(savedInstanceState);
			// Load the initial image now that currentIndex is decided
			loadImageAtIndex(currentIndex);
		} else {
			Toast.makeText(getContext(), "No images to display", Toast.LENGTH_SHORT).show();
			requireActivity().onBackPressed();
		}
	}

	// helpers for short memory
	private void saveViewerState(@Nullable Uri imageUri, int index) {
		if (imageUri == null) {
			Log.d(TAG, "Cannot save state - URI is null");
			return;
		}
		if (MainActivity.prefs == null) {
			Log.e(TAG, "Cannot save state - SharedPreferences is null!");
			return;
		}
		try {
			MainActivity.prefs.edit().putString(PREF_KEY_LAST_URI, imageUri.toString())
					.putInt(PREF_KEY_LAST_INDEX, index).apply();
			Log.d(TAG, "Saved viewer state - URI: " + imageUri + ", Index: " + index);
		} catch (Exception e) {
			Log.e(TAG, "Error saving viewer state", e);
		}
	}

	private void clearViewerState() {
		try {
			MainActivity.prefs.edit().remove(PREF_KEY_LAST_URI).remove(PREF_KEY_LAST_INDEX).apply();
		} catch (Exception ignored) {
		}
	}

	private void restoreViewerStateIfNeeded(@Nullable Bundle savedInstanceState) {
		// 1) If user explicitly picked an image in this session, do not override
		if (imageUris == null || imageUris.isEmpty())
			return; // defensive guard
		if (userPickedImage)
			return;

		// 2) Try savedInstanceState first (fast rotation restore)
		if (savedInstanceState != null) {
			String uriStr = savedInstanceState.getString(STATE_KEY_URI, null);
			int idx = savedInstanceState.getInt(STATE_KEY_INDEX, -1);
			if (uriStr != null && idx >= 0 && idx < imageUris.size()) {
				// try to match URI in current list
				for (int i = 0; i < imageUris.size(); i++) {
					Uri u = imageUris.get(i);
					if (u != null && u.toString().equals(uriStr)) {
						currentIndex = i;
						return;
					}
				}
				// fallback to index if valid
				if (idx >= 0 && idx < imageUris.size()) {
					currentIndex = idx;
					return;
				}
			}
		}

		// 3) Then try SharedPreferences (survives process death)
		String savedUri = MainActivity.prefs.getString(PREF_KEY_LAST_URI, null);
		int savedIndex = MainActivity.prefs.getInt(PREF_KEY_LAST_INDEX, -1);

		if (savedUri != null) {
			int matchIndex = -1;
			for (int i = 0; i < imageUris.size(); i++) {
				Uri u = imageUris.get(i);
				if (u != null && u.toString().equals(savedUri)) {
					matchIndex = i;
					break;
				}
			}
			if (matchIndex >= 0) {
				currentIndex = matchIndex;
				return;
			}
		}

		// 4) fallback to saved index if valid, else keep default 0
		if (savedIndex >= 0 && savedIndex < imageUris.size()) {
			currentIndex = savedIndex;
		} else {
			currentIndex = Math.max(0, Math.min(currentIndex, imageUris.size() - 1));
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (imageUris != null && currentIndex >= 0 && currentIndex < imageUris.size()) {
			Uri u = imageUris.get(currentIndex);
			if (u != null) {
				outState.putString(STATE_KEY_URI, u.toString());
				outState.putInt(STATE_KEY_INDEX, currentIndex);
			}
		}
	}// short memory end

	private boolean hotspotAllowed() {
		if (gestureHandler != null) {
			return !gestureHandler.isSwiping && !gestureHandler.isScaling && !gestureHandler.isPanning
					&& !gestureHandler.isDoubleTapping && scaleFactor <= 1.01f;
		}
		return scaleFactor <= 1.01f;
	}

	// animation handler for slideshow
	private void showNextImageWithFade() {
		if (currentIndex < imageUris.size() - 1) {
			currentIndex++;
		} else {
			currentIndex = 0;
		}

		// Optional fade animation (can be adjusted, androidx.material)
		Animation fadeOut = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_out);
		Animation fadeIn = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in);

		imageView.startAnimation(fadeOut);
		fadeOut.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				loadImageAtIndex(currentIndex);
				imageView.startAnimation(fadeIn);
			}
		});
	}

	private void askForSlideShowInterval() {
		// 1. Setup the EditText (code remains the same)
		final EditText input = new EditText(requireContext());
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		int lastSec = MainActivity.prefs.getInt("slideshow_delay", 5);
		input.setText(String.valueOf(lastSec));
		input.setTextColor(Color.WHITE);
		input.setGravity(Gravity.CENTER_HORIZONTAL);
		input.setPadding(32, 16, 32, 16);
		input.setBackgroundColor(Color.parseColor("#333333"));

		// --- Create the Title entirely in Java (NO XML required) ---
		TextView customTitleTextView = new TextView(requireContext());
		customTitleTextView.setText("Second per Slide");
		customTitleTextView.setTextColor(Color.WHITE);
		customTitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
		customTitleTextView.setPadding(32, 16, 32, 16);
		customTitleTextView.setTextSize(20);

		// 2. Build the AlertDialog (code remains the same)
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext()).setCustomTitle(customTitleTextView)
				.setView(input).setPositiveButton("▶", (d, i) -> {
					String txt = input.getText().toString().trim();
					int sec = txt.isEmpty() ? lastSec : Integer.parseInt(txt);
					MainActivity.prefs.edit().putInt("slideshow_delay", sec).apply();
					startSlideShow(sec);
				}).setNegativeButton("X", null);

		// 3. Show the dialog
		AlertDialog dialog = builder.show();

		// 4. Custom Styling & Sizing AFTER the dialog is shown

		// --- A. Style the Dialog Window Background ---
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2C2C2C")));

			// --- B. Adjust the Dialog Size (The new code for sizing) ---

			// 1. Get the current display metrics (screen size info)
			DisplayMetrics displayMetrics = new DisplayMetrics();
			// Use the activity context to get the display metrics
			requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

			int screenWidth = displayMetrics.widthPixels;

			// Calculate the target width (e.g., 50% of the screen width)
			int targetWidth = (int) (screenWidth * 0.5);

			// 2. Apply the new width to the dialog window layout parameters
			WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
			layoutParams.copyFrom(window.getAttributes());
			layoutParams.width = targetWidth;
			layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT; // Keep height natural

			window.setAttributes(layoutParams);
		}

		// --- C. Style the Buttons (code remains the same) ---
		Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

		if (positiveButton != null && negativeButton != null) {
			int accentColor = Color.parseColor("#4CAF50");
			int cancelColor = Color.parseColor("#F44336");
			positiveButton.setTextColor(accentColor);
			negativeButton.setTextColor(cancelColor);
		}

		// 5. Handle keyboard focus (code remains the same)
		input.selectAll();
		input.requestFocus();
		input.post(() -> {
			InputMethodManager imm = (InputMethodManager) requireContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
			}
		});
	}

	// CHANGED: Update slideshow methods to use new handler
	private void startSlideShow(int sec) {
		isSlideShowRunning = true;
		hideUiElements();
		long intervalMs = Math.max(500, sec * 1000L);
		gestureHandler.startSlideShow(intervalMs);
	}

	private void stopSlideShow() {
		isSlideShowRunning = false;
		gestureHandler.stopSlideShow();
	}

	// main image loader. (need to make a var to hold showing image's id/address for when activity gets reset.)
	private void loadImageAtIndex(int index) {
		if (imageUris == null || index < 0 || index >= imageUris.size())
			return;

		Uri imageUri = imageUris.get(index);
		currentIndex = index;
		saveViewerState(imageUri, index); // persist short memory

		// Reset zoom
		scaleFactor = 1.0f;

		// Clear previous drawable safely
		if (imageView.getDrawable() != null) {
			imageView.getDrawable().setCallback(null);
			imageView.setImageDrawable(null);
		}

		// Ensure matrix mode is ready before loading (keeps MATRIX active)
		imageView.setScaleType(ImageView.ScaleType.MATRIX);
		imageMatrix = new Matrix(); // start with identity while we load

		// Background load: use Glide to fetch cached file, then use ImageLoader / ImageDecoder for display
		new Thread(() -> {
			WeakReference<ImageView> imageViewRef = new WeakReference<>(imageView);
			WeakReference<TextView> nameTextViewRef = new WeakReference<>(imageNameText);
			WeakReference<FragmentActivity> activityRef = new WeakReference<>((FragmentActivity) getActivity());
			com.bumptech.glide.request.FutureTarget<File> future = null;
			try {
				Context context = getContext();
				if (context == null)
					return;

				// 1) Ask Glide for a local File (this will use cache if available):
				future = Glide.with(context).asFile().load(imageUri).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
						.submit();

				// Wait for file (timeout optional). This uses cached file if present or downloads to cache.
				File file = null;
				try {
					file = future.get(5, TimeUnit.SECONDS);
				} catch (Exception ex) {
					// fallback: null file -> we'll try direct stream from content resolver
					file = null;
				}

				// 2) Decide how to load the image: prefer file when available
				Context ctx = context;
				boolean loaded = false;
				Drawable drawable = null;
				if (file != null && file.exists()) {
					// If file is present, use ImageDecoder (API >= 28) or Bitmap decode via your ImageLoader
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						try {
							ImageDecoder.Source src = ImageDecoder.createSource(file);
							drawable = ImageDecoder.decodeDrawable(src);
						} catch (Throwable t) {
							// fallback to ImageLoader or stream
							drawable = null;
						}
					}
					if (drawable == null) {
						// Let your ImageLoader decode the bitmap from file path (keep existing decoder usage)
						Bitmap bm = ImageLoader.decodeSampledBitmapFromUri(ctx, Uri.fromFile(file), 0, 0);
						if (bm != null) {
							drawable = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);
						}
					}
				} else {
					// No file from Glide: detect via MIME and use stream (this covers Animated WebP & GIF)
					String mimeType = ctx.getContentResolver().getType(imageUri);
					boolean isGif = mimeType != null && mimeType.equals("image/gif");
					boolean isWebp = mimeType != null && mimeType.contains("webp");
					boolean isAnimatedWebp = false;
					if (isWebp) {
						try (InputStream is = ctx.getContentResolver().openInputStream(imageUri)) {
							byte[] header = new byte[12];
							if (is != null) {
								is.read(header);
								isAnimatedWebp = isAnimatedWebp(header);
							}
						} catch (Exception ignored) {
						}
					}
					if (isGif || isAnimatedWebp) {
						// Use framework decoder from InputStream (supports AnimatedImageDrawable where available)
						try (InputStream is = ctx.getContentResolver().openInputStream(imageUri)) {
							if (is != null) {
								drawable = Drawable.createFromStream(is, null);
							}
						} catch (Exception e) {
							Log.e(TAG, "stream animated load failed", e);
						}
					} else {
						// static image fallback via ImageLoader
						Bitmap bm = ImageLoader.decodeSampledBitmapFromUri(ctx, imageUri, 0, 0);
						if (bm != null)
							drawable = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);
					}
				}

				// --- Log before runOnUiThread: indicate whether background produced a drawable
				Log.d(TAG,
						"loadImageAtIndex: prepared drawable present=" + (drawable != null) + " for uri=" + imageUri);

				final Drawable finalDrawable = drawable;
				final FragmentActivity activity = activityRef.get();
				if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
					activity.runOnUiThread(() -> {
						// --- Log inside UI Runnable: show drawable presence before applying
						Log.d(TAG, "UI Runnable: finalDrawable != null -> " + (finalDrawable != null));

						ImageView imgView = imageViewRef.get();
						TextView nameView = nameTextViewRef.get();
						if (imgView == null || nameView == null) {
							if (finalDrawable instanceof android.graphics.drawable.BitmapDrawable) {
								// nothing special
							}
							return;
						}

						if (finalDrawable != null) {
							imgView.setImageDrawable(finalDrawable);
							// If drawable animatable (AnimatedImageDrawable or GIF), start it
							if (finalDrawable instanceof Animatable) {
								try {
									((Animatable) finalDrawable).start();
								} catch (Throwable ignored) {
								}
							}
						} else {
							imgView.setImageResource(R.drawable.ic_broken_image);
							// --- Log forced-resource test result: we had to use fallback resource
							Log.d(TAG, "loadImageAtIndex: forced resource used (ic_broken_image) for uri=" + imageUri);
						}

						// Apply computed initial fit matrix now that drawable is set
						imageMatrix = computeInitialMatrix(imgView);
						imgView.setImageMatrix(imageMatrix);
						imgView.setScaleType(ImageView.ScaleType.MATRIX);

						// update UI
						updateIndexDisplay();
						String name = getImageNameFromUri(ctx, imageUri);
						if (name != null)
							nameView.setText(name);
					});
				} else {
					// cleanup if activity gone
					if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
						Bitmap b = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
						if (b != null && !b.isRecycled())
							b.recycle();
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Error loading image at index " + index, e);
				FragmentActivity activity = activityRef.get();
				if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
					activity.runOnUiThread(() -> {
						ImageView imgView = imageViewRef.get();
						if (imgView != null)
							imgView.setImageResource(R.drawable.ic_broken_image);
					});
				}
			} finally {
				// Make sure to clear Glide future (free resources)
				if (future != null) {
					try {
						Glide.with(this).clear(future);
					} catch (Exception ignored) {
					}
				}
			}

			// Optional: kick off preload of nearby files (non-blocking)
			preloadNearbyImages();
		}).start();
	}

	private void preloadNearbyImages() {
		if (imageUris == null || imageUris.isEmpty())
			return;
		int start = Math.max(0, currentIndex - 2);
		int end = Math.min(imageUris.size() - 1, currentIndex + 4);
		for (int i = start; i <= end; i++) {
			if (i == currentIndex)
				continue;
			Uri u = imageUris.get(i);
			// this warms the disk cache with a File; fast and cheap
			Glide.with(this).asFile().load(u).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).preload();
		}
	}

	// Helper method to detect animated webp files
	private boolean isAnimatedWebp(byte[] header) {
		try {
			// Check RIFF header
			if (header.length < 12 || header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
					|| header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
				return false;
			}

			// For complete detection we would need to parse chunks, but this is a good heuristic
			// Animated webp files typically have VP8X chunk with animation flag
			return true; // Simplified for now - most webp files on Android 11 can be animated
		} catch (Exception e) {
			return false;
		}
	}

	// helper for zoom out animation snap back
	private void animateScaleTo(float targetScale) {
		final Matrix startMatrix = new Matrix(imageMatrix); // Snapshot current
		final Matrix endMatrix = computeInitialMatrix(imageView); // Target fit (centered)

		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f); // Progress 0-1
		animator.setDuration(500);
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		animator.addUpdateListener(animation -> {
			float progress = (float) animation.getAnimatedValue();

			// Interpolate matrix values
			float[] startValues = new float[9];
			float[] endValues = new float[9];
			float[] interpolated = new float[9];
			startMatrix.getValues(startValues);
			endMatrix.getValues(endValues);
			for (int i = 0; i < 9; i++) {
				interpolated[i] = startValues[i] + progress * (endValues[i] - startValues[i]);
			}

			imageMatrix.setValues(interpolated);
			imageView.setImageMatrix(imageMatrix);

			// Update scaleFactor for consistency (lerp from current to 1.0f)
			scaleFactor = scaleFactor + progress * (targetScale - scaleFactor);
		});
		animator.start();
	}

	// helper for double tap animation zoom in and out
	private void animateToMatrix(Matrix targetMatrix, float targetScale) {
		final Matrix startMatrix = new Matrix(imageMatrix); // Snapshot current

		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(500);
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		animator.addUpdateListener(animation -> {
			float progress = (float) animation.getAnimatedValue();

			// Interpolate matrix
			float[] startValues = new float[9];
			float[] endValues = new float[9];
			float[] interpolated = new float[9];
			startMatrix.getValues(startValues);
			targetMatrix.getValues(endValues);
			for (int i = 0; i < 9; i++) {
				interpolated[i] = startValues[i] + progress * (endValues[i] - startValues[i]);
			}

			imageMatrix.setValues(interpolated);
			imageView.setImageMatrix(imageMatrix);

			// Interpolate scaleFactor
			scaleFactor = scaleFactor + progress * (targetScale - scaleFactor);
		});
		animator.start();
	}

	private Matrix computeInitialMatrix(ImageView imgView) {
		Drawable drawable = imgView.getDrawable();
		if (drawable == null) {
			return new Matrix();
		}

		int drawWidth = drawable.getIntrinsicWidth();
		int drawHeight = drawable.getIntrinsicHeight();
		int viewWidth = imgView.getWidth();
		int viewHeight = imgView.getHeight();

		if (drawWidth <= 0 || drawHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
			return new Matrix();
		}

		float scale = Math.min((float) viewWidth / drawWidth, (float) viewHeight / drawHeight);
		Matrix matrix = new Matrix();
		matrix.setScale(scale, scale);

		float dx = (viewWidth - drawWidth * scale) / 2f;
		float dy = (viewHeight - drawHeight * scale) / 2f;
		matrix.postTranslate(dx, dy);
		fitScale = scale; // Store the base fit scale
		return matrix;
	}

	private void clampMatrix() {
		Drawable drawable = imageView.getDrawable();
		if (drawable == null)
			return;

		float[] values = new float[9];
		imageMatrix.getValues(values);
		float scaleX = values[Matrix.MSCALE_X];
		float transX = values[Matrix.MTRANS_X];
		float transY = values[Matrix.MTRANS_Y];

		int drawWidth = drawable.getIntrinsicWidth();
		int drawHeight = drawable.getIntrinsicHeight();
		int viewWidth = imageView.getWidth();
		int viewHeight = imageView.getHeight();

		// Scaled dimensions
		float scaledWidth = drawWidth * scaleX;
		float scaledHeight = drawHeight * scaleX; // Assume uniform scale

		// Clamp X: Don't pan left if left edge is visible, etc.
		if (scaledWidth > viewWidth) {
			// Can pan horizontally
			float minTransX = viewWidth - scaledWidth;
			float maxTransX = 0f;
			transX = Math.max(minTransX, Math.min(transX, maxTransX));
		} else { // Center if smaller
			transX = (viewWidth - scaledWidth) / 2f;
		}

		// Clamp Y (same logic)
		if (scaledHeight > viewHeight) {
			float minTransY = viewHeight - scaledHeight;
			float maxTransY = 0f;
			transY = Math.max(minTransY, Math.min(transY, maxTransY));
		} else {
			transY = (viewHeight - scaledHeight) / 2f;
		}

		// Apply clamped values
		values[Matrix.MTRANS_X] = transX;
		values[Matrix.MTRANS_Y] = transY;
		imageMatrix.setValues(values);
	}

	// helper for double tap clamp
	private RectF getDisplayRect(Matrix matrix) {
		Drawable d = imageView.getDrawable();
		if (d == null)
			return null;

		RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
		matrix.mapRect(rect);
		return rect;
	}

	private void clampMatrixForMatrix(Matrix m) {
		RectF r = getDisplayRect(m);
		if (r == null)
			return;

		int viewW = imageView.getWidth();
		int viewH = imageView.getHeight();
		if (viewW == 0 || viewH == 0)
			return;

		float deltaX = 0f;
		float deltaY = 0f;

		// Horizontal clamp / center
		if (r.width() <= viewW) {
			// center horizontally
			deltaX = (viewW - r.width()) * 0.5f - r.left;
		} else {
			if (r.left > 0) {
				deltaX = -r.left;
			} else if (r.right < viewW) {
				deltaX = viewW - r.right;
			}
		}

		// Vertical clamp / center
		if (r.height() <= viewH) {
			// center vertically
			deltaY = (viewH - r.height()) * 0.5f - r.top;
		} else {
			if (r.top > 0) {
				deltaY = -r.top;
			} else if (r.bottom < viewH) {
				deltaY = viewH - r.bottom;
			}
		}

		if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
			m.postTranslate(deltaX, deltaY);
		}
	}

	private String getImageNameFromUri(Context context, Uri uri) {
		try {
			String[] projection = { MediaStore.Images.Media.DISPLAY_NAME };
			try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
					null)) {
				if (cursor != null && cursor.moveToFirst()) {
					int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
					return cursor.getString(nameIndex);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting image name", e);
		}
		return "Unknown";
	}

	private void updateIndexDisplay() {
		if (imageUris != null) {
			imageIndexText.setText((currentIndex + 1) + " / " + imageUris.size());
		}
	}

	//=======
	// Ui Visibility
	private void toggleUiVisibility() {
		if (isSlideShowRunning)
			return; // lock UI hidden

		if (isUiVisible) {
			hideUiElements();
		} else {
			showUiElements();
		}
	}

	private void hideUiElements() {
		// Animate out
		topBar.animate().translationY(-topBar.getHeight()).setInterpolator(new AccelerateDecelerateInterpolator())
				.setDuration(400).start();
		bottomBar.animate().translationY(bottomBar.getHeight()).setInterpolator(new AccelerateDecelerateInterpolator())
				.setDuration(400).start();

		topBar.setVisibility(View.INVISIBLE);
		bottomBar.setVisibility(View.INVISIBLE);
		isUiVisible = false;

	}

	private void showUiElements() {
		// Animate in
		topBar.animate().translationY(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(300)
				.start();
		bottomBar.animate().translationY(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(300)
				.start();

		topBar.setVisibility(View.VISIBLE);
		bottomBar.setVisibility(View.VISIBLE);
		isUiVisible = true;

	}

	private void confirmDeleteImage() {
		// For personal app, simple confirmation
		Toast.makeText(getContext(), "Image deleted", Toast.LENGTH_SHORT).show();
		deleteCurrentImage();
		// clearTempMemory();
	}

	private void deleteCurrentImage() {
		if (imageUris == null || currentIndex < 0 || currentIndex >= imageUris.size())
			return;

		Uri imageUri = imageUris.get(currentIndex);
		boolean deleted = deleteImageFromStorage(getContext(), imageUri);

		if (deleted) {
			imageUris.remove(currentIndex);
			// 	clearTempMemory();
			if (deleteListener != null) {
				deleteListener.onImageDeleted(currentIndex);
			}

			if (imageUris.isEmpty()) {
				requireActivity().onBackPressed();
			} else {
				// Adjust index if needed
				if (currentIndex >= imageUris.size()) {
					currentIndex = imageUris.size() - 1;
				}
				loadImageAtIndex(currentIndex);
				updateIndexDisplay();
			}
		} else {
			Toast.makeText(getContext(), "Failed to delete image", Toast.LENGTH_SHORT).show();
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.Q)
	private boolean deleteImageFromStorage(Context context, Uri imageUri) {
		try {
			ContentResolver resolver = context.getContentResolver();
			ContentValues values = new ContentValues();
			values.put(MediaStore.Images.Media.IS_PENDING, 0);
			resolver.update(imageUri, values, null, null);

			return resolver.delete(imageUri, null, null) > 0;
		} catch (Exception e) {
			Log.e(TAG, "Error deleting image", e);
			return false;
		}
	}

	private void showImageInfo() {
		if (imageUris == null || currentIndex < 0 || currentIndex >= imageUris.size())
			return;

		Uri imageUri = imageUris.get(currentIndex);
		StringBuilder info = new StringBuilder();

		try {
			String[] projection = { MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
					MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.WIDTH,
					MediaStore.Images.Media.HEIGHT };

			try (android.database.Cursor cursor = getContext().getContentResolver().query(imageUri, projection, null,
					null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
					int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
					int dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
					int widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
					int heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);

					String name = cursor.getString(nameIndex);
					long size = cursor.getLong(sizeIndex);
					long dateModified = cursor.getLong(dateIndex);
					int width = cursor.getInt(widthIndex);
					int height = cursor.getInt(heightIndex);

					info.append("Name: ").append(name).append("\n");
					info.append("Size: ").append(formatSize(size)).append("\n");
					info.append("Dimensions: ").append(width).append("x").append(height).append("\n");
					info.append("Modified: ").append(formatDate(dateModified));
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting image info", e);
			info.append("Error loading image info");
		}

		// Show info in Toast for now (simple implementation for phone IDE)
		Toast.makeText(getContext(), info.toString(), Toast.LENGTH_LONG).show();
	}

	private String formatDate(long timestamp) {
		java.util.Date date = new java.util.Date(timestamp * 1000);
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
		return sdf.format(date);
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

		// NEW: Register this fragment's gesture handler with MainActivity
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).setGestureDelegate(gestureHandler);
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Reset to fit on rotation
		if (imageView != null && imageView.getDrawable() != null) {
			imageMatrix = computeInitialMatrix(imageView);
			imageView.setImageMatrix(imageMatrix);
			scaleFactor = 1.0f;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		stopSlideShow();

		// Save viewer state
		if (imageUris != null && currentIndex >= 0 && currentIndex < imageUris.size()) {
			Uri currentUri = imageUris.get(currentIndex);
			saveViewerState(currentUri, currentIndex);
		}

		// NEW: Unregister gesture handler
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).setGestureDelegate(null);
		}

	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		// CHANGED: Cleanup new gesture handler
		if (gestureHandler != null) {
			gestureHandler.cleanup();
			gestureHandler = null;
		}

		// Clear prefs only when the user is actually closing the activity/fragment,
		// not on configuration changes (rotation) or normal fragment replacement.

		if (imageView != null) {
			if (imageView.getDrawable() != null) {
				imageView.getDrawable().setCallback(null);
				imageView.setImageDrawable(null);
			}
			Glide.with(this).clear(imageView);
			imageView = null;
		}

	}
}