package com.ccko.pikxplus.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ccko.pikxplus.R;

import java.util.HashSet;
import java.util.Set;
import androidx.lifecycle.ViewModelProvider;
import com.ccko.pikxplus.utils.SharedViewModel;

public class SearchFragment extends Fragment {

	private SharedViewModel sharedViewModel;

	public interface OnSearchFilterListener {
		void onFilterChanged(Filter filter);
	}

	public static class Filter {
		public final Set<String> types = new HashSet<>(); // "images","videos","gifs"
		public String query = "";
	}

	private OnSearchFilterListener listener;
	private ToggleButton btnImages, btnVideos, btnGifs;
	private EditText searchInput;
	private ImageButton btnClear;
	private Button btnReset;

	private final Handler handler = new Handler();
	private Runnable debounceRunnable;
	private static final long DEBOUNCE_MS = 300L;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_search, container, false);

		btnImages = v.findViewById(R.id.btnImages);
		btnVideos = v.findViewById(R.id.btnVideos);
		btnGifs = v.findViewById(R.id.btnGifs);
		searchInput = v.findViewById(R.id.searchInput);
		btnClear = v.findViewById(R.id.btnClearSearch);
		btnReset = v.findViewById(R.id.btnReset);

		sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

		// Wire toggles
		View.OnClickListener toggleListener = view -> emitFilterDebounced();
		btnImages.setOnClickListener(toggleListener);
		btnVideos.setOnClickListener(toggleListener);
		btnGifs.setOnClickListener(toggleListener);

		// Clear button
		btnClear.setOnClickListener(view -> {
			searchInput.setText("");
			emitFilterDebounced();
		});

		// Reset button: clear query and set Images selected (example default)
		btnReset.setOnClickListener(view -> {
			btnImages.setChecked(true);
			btnVideos.setChecked(false);
			btnGifs.setChecked(false);
			searchInput.setText("");
			emitFilterDebounced();
		});

		// Search input with debounce
		searchInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int st, int c, int a) {
			}

			@Override
			public void onTextChanged(CharSequence s, int st, int b, int c) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				emitFilterDebounced();
			}
		});

		// Emit initial filter on create
		emitFilterImmediate();

		return v;
	}

	private void emitFilterDebounced() {
		if (debounceRunnable != null)
			handler.removeCallbacks(debounceRunnable);
		debounceRunnable = this::emitFilterImmediate;
		handler.postDelayed(debounceRunnable, DEBOUNCE_MS);
	}

	private void emitFilterImmediate() {
		Filter f = new Filter();
		if (btnImages.isChecked())
			f.types.add("images");
		if (btnVideos.isChecked())
			f.types.add("videos");
		if (btnGifs.isChecked())
			f.types.add("gifs");
		f.query = searchInput.getText() != null ? searchInput.getText().toString().trim() : "";

		if (sharedViewModel != null) {
			sharedViewModel.setFilter(f);
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		listener = null;
		if (debounceRunnable != null)
			handler.removeCallbacks(debounceRunnable);
	}
}