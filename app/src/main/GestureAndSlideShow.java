package com.ccko.pikxplus.features;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.core.view.GestureDetectorCompat;

import com.ccko.pikxplus.features.GestureAndSlideShow.SlideShow.Callback;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;

/**
 * Centralized gesture and slideshow helper.
 *
 * Usage:
 *   GestureAndSlideShow gss = new GestureAndSlideShow(context, hostCallback);
 *   imageView.setOnTouchListener((v, e) -> gss.onTouch(v, e));
 *
 * Host implements HostCallback to receive UI actions (next/prev image, toggle UI, etc).
 */
public class GestureAndSlideShow {

	// Public fields kept with the same names so existing code can be adapted easily
	public GestureDetectorCompat gestureDetector;
	public ScaleGestureDetector scaleGestureDetector;

	private volatile View lastTouchView;

	public Handler uiHandler;
	public Runnable hideUiRunnable;
	private SlideShow slideShow;

	// gesture state flags (volatile for cross-thread visibility)
	public volatile boolean isSwiping = false;
	public volatile boolean isScaling = false;
	public volatile boolean isPanning = false;
	public volatile boolean isDoubleTapping = false;

	// slideshow running flag
	public volatile boolean isSlideShowRunning = false;
	private final AtomicBoolean active = new AtomicBoolean(false);
	private final WeakReference<Activity> activityRef;
	private final HostCallback host;
	private final Context ctx;

	public interface HostCallback {
		//	void onSingleTap();

		void onDoubleTap(float x, float y);

		void onLongPress();

		void onPan(float distanceX, float distanceY); // called during scroll when fragment should pan

		void onScale(float scaleFactor, float focusX, float focusY); // continuous scale updates

		void onScaleEnd(); // scale finished (snap back if needed)

		void onRequestClose(); // vertical swipe-to-close

		void onNextImageRequested(); // horizontal swipe -> next

		void onPreviousImageRequested(); // horizontal swipe -> previous 

		void onSlideShowStopped();

		void onSlideShowNext();
	}

	public GestureAndSlideShow(Context context, HostCallback hostCallback, @Nullable Activity activity) {
		this.ctx = context;
		this.host = hostCallback;
		this.activityRef = activity != null ? new WeakReference<>(activity) : null;

		uiHandler = new Handler(Looper.getMainLooper());
		gestureDetector = new GestureDetectorCompat(context, new GestureListener());
		scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

		// SlideShow instance (pass the hosting Activity)
		// inside your Fragment (onViewCreated or similar)
		slideShow = new SlideShow(new SlideShow.Callback() {
			@Override
			public void onNextImage() {
				uiHandler.post(() -> {
					if (host != null)
						host.onSlideShowNext();
				});
			}

			@Override
			public void onSlideShowStopped() {
				uiHandler.post(() -> {
					isSlideShowRunning = false;
					if (host != null)
						host.onSlideShowStopped();
				});
			}
		}, activity); // requireActivity() is valid here
	}

	/**
	 * Entry point for touch events. Call this from your ImageView's OnTouchListener.
	 * Returns true if event was consumed.
	 */
	public boolean onTouch(View v, MotionEvent event) {

		lastTouchView = v; // remember for listeners

		// Manage longpress enabling/disabling if needed (host may want to control)
		if (event.getPointerCount() > 1) {
			gestureDetector.setIsLongpressEnabled(false);
		} else {
			gestureDetector.setIsLongpressEnabled(true);
		}

		boolean scaleHandled = scaleGestureDetector.onTouchEvent(event);
		boolean gestureHandled = gestureDetector.onTouchEvent(event);

		// If neither handled, return false so other listeners (hotspots) can get ACTION_UP
		return scaleHandled || gestureHandled;
	}

	// Expose simple control methods for slideshow
	public void startSlideShow(long intervalMs) {
		if (slideShow != null && !slideShow.isActive()) {
			slideShow.start(intervalMs);
			isSlideShowRunning = true;
		}
	}

	public void stopSlideShow() {
		if (slideShow != null && slideShow.isActive()) {
			slideShow.stop();
			isSlideShowRunning = false;
		}
	}

	public boolean isSlideShowActive() {
		return slideShow != null && slideShow.isActive();
	}

	// GestureListener: copy your existing GestureDetector logic here (onFling, onScroll, onDoubleTap, etc.)
	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		private static final int SWIPE_THRESHOLD = 100;
		private static final int SWIPE_VELOCITY_THRESHOLD = 100;
		private static final float TOP_IGNORE_RATIO = 0.15f;

		/**
				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					if (host != null)
						host.onSingleTap();
					return true;
				}
		**/
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			isDoubleTapping = true;
			uiHandler.postDelayed(() -> isDoubleTapping = false, 300);
			if (host != null)
				host.onDoubleTap(e.getX(), e.getY());
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e) {
			// delegate long press to host (fragment will toggle UI and stop slideshow)
			if (host != null)
				host.onLongPress();
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			isPanning = true;
			uiHandler.postDelayed(() -> isPanning = false, 50);
			// delegate pan distances to host; host decides whether to apply panning (only when zoomed)
			if (host != null)
				host.onPan(distanceX, distanceY);
			return true; // consumed by helper; host will decide whether to act
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				// Ignore while scaling or multi-touch
				//if (isScaling || isPanning)
				//	return false;
				if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
					return false;

				// Make thresholds static: fixed pixel distance and velocity
				final int effectiveThreshold = 109; // static swipe distance in pixels
				final int velocityThreshold = 800; // static velocity threshold (px/sec)

				float startX = e1.getX();
				float startY = e1.getY();
				float endX = e2.getX();
				float endY = e2.getY();

				float diffX = endX - startX;
				float diffY = endY - startY;

				// Ignore gestures that start in the top area (optional) - use screen height
				float topIgnoreLimit = ctx.getResources().getDisplayMetrics().heightPixels * TOP_IGNORE_RATIO;
				if (startY <= topIgnoreLimit) {
					return false;
				}

				// Vertical swipe-to-close (downwards)
				if (Math.abs(diffY) > Math.abs(diffX)) {
					// require downward motion (top -> bottom) and a meaningful distance
					if (diffY > effectiveThreshold && Math.abs(velocityY) > velocityThreshold) {
						// If the image is zoomed in, do not close — let panning/scale handle it
						// The host/fragment should expose scaleFactor or helper can keep it in sync
						if (host != null && host instanceof GestureAndSlideShow.HostCallback) {
							// ask host whether closing is allowed (optional callback)
							// If you don't have such callback, check a shared flag (e.g., isScaling/isPanning)
						}
						isSwiping = true;
						uiHandler.postDelayed(() -> isSwiping = false, 300);
						if (host != null)
							host.onRequestClose();
						return true;
					}
				}

				// Horizontal navigation (left/right)
				if (Math.abs(diffX) > Math.abs(diffY)) {
					if (Math.abs(diffX) > effectiveThreshold && Math.abs(velocityX) > velocityThreshold) {
						isSwiping = true;
						uiHandler.postDelayed(() -> isSwiping = false, 300);

						if (diffX > 0) {
							if (host != null)
								host.onPreviousImageRequested();
						} else {
							if (host != null)
								host.onNextImageRequested();
						}
						return true;
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			return false;
		}
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			isScaling = true;
			return true;
		}

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			// delegate continuous scale updates to host
			if (host != null)
				host.onScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
			return true;
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector) {
			isScaling = false;
			if (host != null)
				host.onScaleEnd();
		}
	}

	// SlideShow class (kept internal). If you already have a SlideShow class, copy it here.	
	// added my SlideShow.java Class here as you said.
	public static class SlideShow {
		public interface Callback {
			void onNextImage();

			void onSlideShowStopped();
		}

		private final Callback cb;
		private final Handler handler = new Handler(Looper.getMainLooper());
		private final AtomicBoolean active = new AtomicBoolean(false);
		private long intervalMs = 3000L;
		private final WeakReference<Activity> activityRef;

		private final Runnable tick = new Runnable() {
			@Override
			public void run() {
				if (!active.get())
					return;
				if (cb != null)
					cb.onNextImage();
				handler.postDelayed(this, intervalMs);
			}
		};

		public SlideShow(Callback callback, @Nullable Activity activity) {
			this.cb = callback;
			this.activityRef = activity != null ? new WeakReference<>(activity) : null;
		}

		public void start(long ms) {
			this.intervalMs = Math.max(100L, ms);

			// set keep-screen-on on the activity window if available
			if (activityRef != null) {
				Activity a = activityRef.get();
				if (a != null) {
					a.runOnUiThread(() -> a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
				}
			}

			if (active.compareAndSet(false, true)) {
				handler.postDelayed(tick, this.intervalMs);
			}
		}

		public void stop() {
			if (active.compareAndSet(true, false)) {
				handler.removeCallbacks(tick);

				// clear keep-screen-on on the activity window if available
				if (activityRef != null) {
					Activity a = activityRef.get();
					if (a != null) {
						a.runOnUiThread(() -> a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
					}
				}

				if (cb != null)
					cb.onSlideShowStopped();
			}
		}

		public boolean isActive() {
			return active.get();
		}

		public static void fadeTransition(View imageView, Context context, Runnable loadNextImage) {
			Animation fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
			fadeOut.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					loadNextImage.run();
					Animation fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
					imageView.startAnimation(fadeIn);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}
			});
			imageView.startAnimation(fadeOut);
		}
	}
}
