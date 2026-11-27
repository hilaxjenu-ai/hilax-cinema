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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
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
            .readTimeout(25, TimeUnit.SECONDS)
            .build();

    private ExoPlayer player;
    private LinearLayout playerSection;
    private PlayerView playerView;
    private Button btnSpeed;
    private TextView tvStatus;
    private EditText etSearch;
    private LinearLayout gridContainer;

    private boolean isShowingCategories = true;
    private float currentSpeed = 1.0f;

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
        topBar.setPadding(0, 0, 0, 14);

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
        searchRow.setPadding(0, 0, 0, 14);

        etSearch = new EditText(this);
        etSearch.setHint("Search movies, web series (2 sites)...");
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
                Toast.makeText(this, "Enter movie or web series name", Toast.LENGTH_SHORT).show();
            }
        });
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        playerSection = new LinearLayout(this);
        playerSection.setOrientation(LinearLayout.VERTICAL);
        playerSection.setBackgroundColor(Color.parseColor("#111115"));
        playerSection.setPadding(10, 10, 10, 10);
        playerSection.setVisibility(View.GONE);
        LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        secParams.setMargins(0, 0, 0, 14);
        root.addView(playerSection, secParams);

        FrameLayout playerBox = new FrameLayout(this);
        playerBox.setBackgroundColor(Color.BLACK);
        playerSection.addView(playerBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 460
        ));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerBox.addView(playerView, new FrameLayout.LayoutParams(
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
        playerBox.addView(btnClosePlayer, closeParams);
        btnClosePlayer.setOnClickListener(v -> hidePlayer());

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setPadding(0, 10, 0, 2);

        Button btnRewind = new Button(this);
        btnRewind.setText("⏪ -10s");
        btnRewind.setTextColor(Color.WHITE);
        btnRewind.setTextSize(11f);
        btnRewind.setBackgroundColor(Color.parseColor("#23232A"));
        LinearLayout.LayoutParams ctrlParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        ctrlParams.setMargins(0, 0, 8, 0);
        controlsRow.addView(btnRewind, ctrlParams);
        btnRewind.setOnClickListener(v -> {
            if (player != null) {
                long target = Math.max(0L, player.getCurrentPosition() - 10000L);
                player.seekTo(target);
            }
        });

        Button btnForward = new Button(this);
        btnForward.setText("+10s ⏩");
        btnForward.setTextColor(Color.WHITE);
        btnForward.setTextSize(11f);
        btnForward.setBackgroundColor(Color.parseColor("#23232A"));
        LinearLayout.LayoutParams fwdParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        fwdParams.setMargins(0, 0, 8, 0);
        controlsRow.addView(btnForward, fwdParams);
        btnForward.setOnClickListener(v -> {
            if (player != null) {
                long dur = player.getDuration();
                long nextPos = player.getCurrentPosition() + 10000L;
                if (dur > 0 && nextPos > dur) {
                    nextPos = dur;
                }
                player.seekTo(nextPos);
            }
        });

        btnSpeed = new Button(this);
        btnSpeed.setText("1.0x");
        btnSpeed.setTextColor(Color.parseColor("#E50914"));
        btnSpeed.setTextSize(11f);
        btnSpeed.setTypeface(Typeface.DEFAULT_BOLD);
        btnSpeed.setBackgroundColor(Color.parseColor("#23232A"));
        controlsRow.addView(btnSpeed, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        btnSpeed.setOnClickListener(v -> {
            if (player == null) return;
            if (currentSpeed == 1.0f) {
                currentSpeed = 1.25f;
            } else if (currentSpeed == 1.25f) {
                currentSpeed = 1.5f;
            } else if (currentSpeed == 1.5f) {
                currentSpeed = 2.0f;
            } else {
                currentSpeed = 1.0f;
            }
            player.setPlaybackSpeed(currentSpeed);
            btnSpeed.setText(currentSpeed + "x");
        });

        playerSection.addView(controlsRow);
        root.addView(playerSection);

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
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                showToast("Player error: " + error.getErrorCodeName());
            }
        });

        loadCategories();
    }

    private void hidePlayer() {
        if (player != null) {
            player.pause();
        }
        playerSection.setVisibility(View.GONE);
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
                extractAllItemsRecursive(json, titles, urls, isPageList, false);

                runOnUiThread(() -> {
                    tvStatus.setText("CATEGORIES (" + titles.size() + ")");
                    renderGrid(titles, urls, isPageList, true);
                    if (titles.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No categories returned", Toast.LENGTH_SHORT).show();
                    }
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
                extractAllItemsRecursive(json, titles, urls, isPageList, true);

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
        runOnUiThread(() -> tvStatus.setText("SEARCHING ALL SITES: " + query + "..."));

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url1 = BASE_URL + "/api/search?q=" + encoded + "&all=1";
        String url2 = BASE_URL + "/api/search?query=" + encoded;

        fetchJson(url1, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                List<Boolean> isPageList = new ArrayList<>();
                extractAllItemsRecursive(json, titles, urls, isPageList, false);

                if (!titles.isEmpty()) {
                    runOnUiThread(() -> {
                        tvStatus.setText("SEARCH \"" + query + "\" (" + titles.size() + ")");
                        renderGrid(titles, urls, isPageList, false);
                    });
                    return;
                }

                fetchJson(url2, new JsonCallback() {
                    @Override
                    public void onSuccess(JSONObject json2) {
                        List<String> t2 = new ArrayList<>();
                        List<String> u2 = new ArrayList<>();
                        List<Boolean> p2 = new ArrayList<>();
                        extractAllItemsRecursive(json2, t2, u2, p2, false);

                        runOnUiThread(() -> {
                            tvStatus.setText("SEARCH \"" + query + "\" (" + t2.size() + ")");
                            renderGrid(t2, u2, p2, false);
                            if (t2.isEmpty()) {
                                Toast.makeText(MainActivity.this, "No movies or web series found", Toast.LENGTH_SHORT).show();
                            }
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

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("SEARCH: " + query);
                    Toast.makeText(MainActivity.this, "Search error: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void extractAllItemsRecursive(
            Object node,
            List<String> titles,
            List<String> urls,
            List<Boolean> isPageList,
            boolean includePages
    ) {
        if (node == null) return;

        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                extractAllItemsRecursive(arr.opt(i), titles, urls, isPageList, includePages);
            }
            return;
        }

        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            boolean hasTitle = obj.has("title") || obj.has("name");
            boolean hasUrl = obj.has("url") || obj.has("link") || obj.has("stream_url");
            boolean isPagination = obj.optBoolean("is_pagination", false);

            if (hasTitle && hasUrl) {
                if (!isPagination || includePages) {
                    String title = obj.optString("title", obj.optString("name", "Item"));
                    String url = obj.optString("url", obj.optString("link", obj.optString("stream_url", "")));
                    if (!title.isEmpty()) {
                        titles.add(title);
                        urls.add(url);
                        isPageList.add(isPagination);
                    }
                }
            }

            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object val = obj.opt(k);
                if (val instanceof JSONArray || val instanceof JSONObject) {
                    extractAllItemsRecursive(val, titles, urls, isPageList, includePages);
                }
            }
        }
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
                    playerSection.setVisibility(View.VISIBLE);
                    tvStatus.setText("NOW PLAYING: " + itemTitle);
                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(finalUrl));
                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.setPlaybackSpeed(currentSpeed);
                    player.play();
                });
            }

            @Override
            public void onError(String message) {
                if (message != null && message.contains("404") && itemUrl != null && itemUrl.startsWith("http")) {
                    loadBrowse(itemTitle, itemUrl);
                } else {
                    showToast("Resolve error: " + message);
                }
            }
        });
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
        if (playerSection != null && playerSection.getVisibility() == View.VISIBLE) {
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
