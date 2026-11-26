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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://hilax.vinokaran.my.id:5000";

    private ExoPlayer player;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private TextView tvHeader;
    private TextView tvStatus;
    private EditText etSearch;
    private LinearLayout gridContainer;

    private boolean isShowingCategories = true;
    private String currentSectionTitle = "CATEGORIES";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0B0C"));
        root.setPadding(24, 24, 24, 24);

        // Top Bar: HILAX CINEMA + Back/Home button
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, 16);

        tvHeader = new TextView(this);
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

        // Search Bar
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

        // Player Container (HIDDEN until video is played)
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

        // Section Title + Status
        tvStatus = new TextView(this);
        tvStatus.setText("CATEGORIES");
        tvStatus.setTextColor(Color.parseColor("#E5E5E5"));
        tvStatus.setTextSize(15f);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setPadding(0, 0, 0, 12);
        root.addView(tvStatus);

        // Scrollable Netflix-style 2-Column Card Grid
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
        currentSectionTitle = "CATEGORIES";
        tvStatus.setText("LOADING CATEGORIES...");

        fetchJson(BASE_URL + "/api/categories", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                parseItems(json, titles, urls);

                runOnUiThread(() -> {
                    tvStatus.setText("CATEGORIES (" + titles.size() + ")");
                    renderGrid(titles, urls, true);
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
        isShowingCategories = false;
        currentSectionTitle = title;
        tvStatus.setText("LOADING " + title.toUpperCase() + "...");

        String encoded = URLEncoder.encode(pageUrl == null ? "" : pageUrl, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/browse?url=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                parseItems(json, titles, urls);

                runOnUiThread(() -> {
                    tvStatus.setText(title.toUpperCase() + " (" + titles.size() + ")");
                    renderGrid(titles, urls, false);
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
        currentSectionTitle = "SEARCH: " + query;
        tvStatus.setText("SEARCHING: " + query + "...");

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/search?q=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                parseItems(json, titles, urls);

                runOnUiThread(() -> {
                    tvStatus.setText("RESULTS FOR \"" + query + "\" (" + titles.size() + ")");
                    renderGrid(titles, urls, false);
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

    private void renderGrid(List<String> titles, List<String> urls, boolean isCategory) {
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
                    View card = createNetflixCard(itemTitle, isCategory);
                    card.setLayoutParams(cardParams);
                    card.setOnClickListener(v -> {
                        if (isCategory) {
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

    private View createNetflixCard(String title, boolean isCategory) {
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
        badge.setText(isCategory ? "COLLECTION" : "▶ PLAY");
        badge.setTextColor(Color.parseColor("#E50914"));
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(0, 0, 0, 8);
        card.addView(badge);

        TextView tvItem = new TextView(this);
        tvItem.setText(title);
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

    private void parseItems(JSONObject json, List<String> titles, List<String> urls) {
        JSONArray items = json.optJSONArray("items");
        if (items == null && json.has("data") && json.optJSONObject("data") != null) {
            items = json.optJSONObject("data").optJSONArray("items");
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.optJSONObject(i);
                if (obj != null) {
                    String title = obj.optString("title", obj.optString("name", "Item " + (i + 1)));
                    String url = obj.optString("url", obj.optString("link", ""));
                    titles.add(title);
                    urls.add(url);
                }
            }
        }
    }

    private void fetchJson(String endpoint, JsonCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                callback.onSuccess(json);
            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Network error");
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
