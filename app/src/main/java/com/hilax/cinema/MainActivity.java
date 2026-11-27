package com.hilax.cinema;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://hilax.vinokaran.my.id:5000";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private ExoPlayer player;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private TextView tvStatus;
    private EditText etSearch;
    private LinearLayout gridContainer;

    private boolean isShowingCategories = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0B0C"));
        root.setPadding(24, 24, 24, 24);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, 16);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("HILAX CINEMA");
        tvHeader.setTextColor(Color.parseColor("#E50914"));
        tvHeader.setTextSize(22f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        topBar.addView(tvHeader, headerParams);

        Button btnHome = new Button(this);
        btnHome.setText("HOME");
        btnHome.setTextColor(Color.WHITE);
        btnHome.setTextSize(12f);
        btnHome.setBackgroundColor(Color.parseColor("#1F1F23"));
        btnHome.setOnClickListener(v -> {
            hidePlayer();
            loadCategories();
        });
        topBar.addView(btnHome);
        root.addView(topBar);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(0, 0, 0, 16);

        etSearch = new EditText(this);
        etSearch.setHint("Search movies, series...");
        etSearch.setHintTextColor(Color.parseColor("#888888"));
        etSearch.setTextColor(Color.WHITE);
        etSearch.setBackgroundColor(Color.parseColor("#18181B"));
        etSearch.setPadding(20, 16, 20, 16);
        etSearch.setSingleLine(true);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        searchParams.setMargins(0, 0, 12, 0);
        searchRow.addView(etSearch, searchParams);

        Button btnSearch = new Button(this);
        btnSearch.setText("SEARCH");
        btnSearch.setTextColor(Color.WHITE);
        btnSearch.setBackgroundColor(Color.parseColor("#E50914"));
        btnSearch.setOnClickListener(v -> {
            String q = etSearch.getText().toString().trim();
            if (!TextUtils.isEmpty(q)) {
                hidePlayer();
                loadSearch(q);
            } else {
                Toast.makeText(this, "Enter search text", Toast.LENGTH_SHORT).show();
            }
        });
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams playerBoxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 460
        );
        playerBoxParams.setMargins(0, 0, 0, 16);
        root.addView(playerContainer, playerBoxParams);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        Button btnClosePlayer = new Button(this);
        btnClosePlayer.setText("✕ CLOSE");
        btnClosePlayer.setTextColor(Color.WHITE);
        btnClosePlayer.setTextSize(11f);
        btnClosePlayer.setBackgroundColor(Color.parseColor("#B3000000"));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(8, 8, 8, 8);
        playerContainer.addView(btnClosePlayer, closeParams);
        btnClosePlayer.setOnClickListener(v -> hidePlayer());

        tvStatus = new TextView(this);
        tvStatus.setText("CATEGORIES");
        tvStatus.setTextColor(Color.parseColor("#E5E5E5"));
        tvStatus.setTextSize(15f);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setPadding(0, 0, 0, 12);
        root.addView(tvStatus);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0B0B0C"));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(scrollView, scrollParams);

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(gridContainer);

        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        loadCategories();
    }

    private void hidePlayer() {
        if (player != null) {
            player.pause();
        }
        playerContainer.setVisibility(View.GONE);
    }

    private void loadCategories() {
        isShowingCategories = true;
        tvStatus.setText("LOADING CATEGORIES...");

        fetchJson(BASE_URL + "/api/categories", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                List<Boolean> isPageList = new ArrayList<>();
                parseBrowseOrCategories(json, titles, urls, isPageList, true);

                runOnUiThread(() -> {
                    tvStatus.setText("CATEGORIES (" + titles.size() + ")");
                    renderGrid(titles, urls, isPageList, true);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("CATEGORIES");
                    Toast.makeText(MainActivity.this, "API error: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadBrowse(String title, String pageUrl) {
        if (pageUrl == null || !pageUrl.startsWith("http")) {
            runOnUiThread(() -> {
                isShowingCategories = false;
                tvStatus.setText(title.toUpperCase());
                renderGrid(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
                Toast.makeText(MainActivity.this, "Special section: " + title, Toast.LENGTH_SHORT).show();
            });
            return;
        }

        isShowingCategories = false;
        runOnUiThread(() -> tvStatus.setText("LOADING " + title.toUpperCase() + "..."));

        String encoded = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/browse?url=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                List<Boolean> isPageList = new ArrayList<>();
                parseBrowseOrCategories(json, titles, urls, isPageList, false);

                runOnUiThread(() -> {
                    tvStatus.setText(title.toUpperCase() + " (" + titles.size() + ")");
                    renderGrid(titles, urls, isPageList, false);
                    if (titles.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No items found in " + title, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText(title.toUpperCase());
                    Toast.makeText(MainActivity.this, "Browse error: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadSearch(String query) {
        isShowingCategories = false;
        runOnUiThread(() -> tvStatus.setText("SEARCHING: " + query + "..."));

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/search?q=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                List<Boolean> isPageList = new ArrayList<>();
                parseBrowseOrCategories(json, titles, urls, isPageList, false);

                runOnUiThread(() -> {
                    tvStatus.setText("RESULTS FOR \"" + query + "\" (" + titles.size() + ")");
                    renderGrid(titles, urls, isPageList, false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("SEARCH: " + query);
                    Toast.makeText(MainActivity.this, "Search error: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderGrid(List<String> titles, List<String> urls, List<Boolean> isPageList, boolean isCategory) {
        gridContainer.removeAllViews();

        for (int i = 0; i < titles.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, 14);
            row.setLayoutParams(rowParams);

            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        0, 220, 1f
                );
                if (col == 0) {
                    cardParams.setMargins(0, 0, 7, 0);
                } else {
                    cardParams.setMargins(7, 0, 0, 0);
                }

                if (idx < titles.size()) {
                    String itemTitle = titles.get(idx);
                    String itemUrl = urls.get(idx);
                    boolean isPage = idx < isPageList.size() && isPageList.get(idx);
                    View card = createNetflixCard(itemTitle, isCategory, isPage);
                    card.setLayoutParams(cardParams);
                    card.setOnClickListener(v -> {
                        if (isCategory || isPage) {
                            loadBrowse(itemTitle, itemUrl);
                        } else {
                            resolveAndPlay(itemTitle, itemUrl);
                        }
                    });
                    row.addView(card);
                } else {
                    View spacer = new View(this);
                    spacer.setLayoutParams(cardParams);
                    row.addView(spacer);
                }
            }

            gridContainer.addView(row);
        }
    }

    private View createNetflixCard(String title, boolean isCategory, boolean isPage) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(18, 18, 18, 18);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#16161A"));
        bg.setCornerRadius(14f);
        bg.setStroke(2, Color.parseColor("#2A2A32"));
        card.setBackground(bg);

        TextView badge = new TextView(this);
        if (isCategory) {
            badge.setText("COLLECTION");
        } else if (isPage) {
            badge.setText("NEXT PAGE");
        } else {
            badge.setText("▶ PLAY");
        }
        badge.setTextColor(Color.parseColor("#E50914"));
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(0, 0, 0, 8);
        card.addView(badge);

        TextView tvItem = new TextView(this);
        tvItem.setText(isPage ? "Page " + title : title);
        tvItem.setTextColor(Color.WHITE);
        tvItem.setTextSize(15f);
        tvItem.setTypeface(Typeface.DEFAULT_BOLD);
        tvItem.setMaxLines(3);
        tvItem.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(tvItem);

        return card;
    }

    private void resolveAndPlay(String itemTitle, String itemUrl) {
        Toast.makeText(this, "Resolving: " + itemTitle, Toast.LENGTH_SHORT).show();
        String encoded = URLEncoder.encode(itemUrl == null ? "" : itemUrl, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/resolve?url=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                String streamUrl = json.optString("stream_url", "");
                if (streamUrl.isEmpty()) {
                    streamUrl = json.optString("url", "");
                }
                if (streamUrl.isEmpty() && json.has("data") && json.optJSONObject("data") != null) {
                    JSONObject data = json.optJSONObject("data");
                    streamUrl = data.optString("stream_url", data.optString("url", ""));
                }

                if (streamUrl.isEmpty()) {
                    showToast("Stream link not found");
                    return;
                }

                if (streamUrl.startsWith("/")) {
                    streamUrl = BASE_URL + streamUrl;
                }

                final String finalUrl = streamUrl;
                runOnUiThread(() -> {
                    playerContainer.setVisibility(View.VISIBLE);
                    tvStatus.setText("NOW PLAYING: " + itemTitle);
                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(finalUrl));
                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play();
                });
            }

            @Override
            public void onError(String message) {
                showToast("Resolve error: " + message);
            }
        });
    }

    private void parseBrowseOrCategories(JSONObject json, List<String> titles, List<String> urls, List<Boolean> isPageList, boolean categoryMode) {
        JSONArray entries = json.optJSONArray("entries");
        if (entries == null) {
            entries = json.optJSONArray("items");
        }
        if (entries == null) {
            entries = json.optJSONArray("categories");
        }
        if (entries == null) {
            entries = json.optJSONArray("results");
        }
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject obj = entries.optJSONObject(i);
                if (obj != null) {
                    String title = obj.optString("title", obj.optString("name", "Item " + (i + 1)));
                    String url = obj.optString("url", obj.optString("link", ""));
                    titles.add(title);
                    urls.add(url);
                    isPageList.add(false);
                }
            }
        }

        if (!categoryMode) {
            JSONArray pages = json.optJSONArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject p = pages.optJSONObject(i);
                    if (p != null) {
                        String pageTitle = p.optString("title", "");
                        String pageUrl = p.optString("url", "");
                        if (!pageTitle.isEmpty() && !pageUrl.isEmpty()) {
                            titles.add(pageTitle);
                            urls.add(pageUrl);
                            isPageList.add(true);
                        }
                    }
                }
            }
        }
    }

    private void fetchJson(String endpoint, JsonCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/json")
                        .header("Connection", "close")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String raw = response.body() != null ? response.body().string().trim() : "";
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP " + response.code());
                        return;
                    }
                    if (raw.isEmpty()) {
                        callback.onError("Empty response body");
                        return;
                    }
                    if (raw.startsWith("[")) {
                        JSONObject wrapped = new JSONObject();
                        wrapped.put("entries", new JSONArray(raw));
                        callback.onSuccess(wrapped);
                    } else {
                        callback.onSuccess(new JSONObject(raw));
                    }
                }
            } catch (Exception e) {
                String errName = e.getClass().getSimpleName();
                String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown";
                callback.onError(errName + " - " + errMsg);
            }
        });
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onBackPressed() {
        if (playerContainer != null && playerContainer.getVisibility() == View.VISIBLE) {
            hidePlayer();
        } else if (!isShowingCategories) {
            loadCategories();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private interface JsonCallback {
        void onSuccess(JSONObject json);
        void onError(String message);
    }
}
