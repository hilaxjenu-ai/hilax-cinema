package com.hilax.cinema;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
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
    private PlayerView playerView;
    private ListView lvItems;
    private TextView tvTitle;

    private boolean isShowingCategories = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTitle = findViewById(R.id.tvTitle);
        playerView = findViewById(R.id.playerView);
        lvItems = findViewById(R.id.lvItems);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        loadCategories();
    }

    private void loadCategories() {
        isShowingCategories = true;
        runOnUiThread(() -> tvTitle.setText("HILAX CINEMA - CATEGORIES"));

        fetchJson(BASE_URL + "/api/categories", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                parseItems(json, titles, urls);

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            MainActivity.this,
                            android.R.layout.simple_list_item_1,
                            titles
                    );
                    lvItems.setAdapter(adapter);
                    lvItems.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < urls.size()) {
                            loadBrowse(titles.get(position), urls.get(position));
                        }
                    });
                });
            }

            @Override
            public void onError(String message) {
                showToast("Categories error: " + message);
            }
        });
    }

    private void loadBrowse(String categoryTitle, String pageUrl) {
        isShowingCategories = false;
        runOnUiThread(() -> tvTitle.setText("HILAX CINEMA - " + categoryTitle));

        String encoded = URLEncoder.encode(pageUrl == null ? "" : pageUrl, StandardCharsets.UTF_8);
        fetchJson(BASE_URL + "/api/browse?url=" + encoded, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                List<String> titles = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                parseItems(json, titles, urls);

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            MainActivity.this,
                            android.R.layout.simple_list_item_1,
                            titles
                    );
                    lvItems.setAdapter(adapter);
                    lvItems.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < urls.size()) {
                            resolveAndPlay(titles.get(position), urls.get(position));
                        }
                    });
                });
            }

            @Override
            public void onError(String message) {
                showToast("Browse error: " + message);
            }
        });
    }

    private void resolveAndPlay(String itemTitle, String itemUrl) {
        showToast("Loading: " + itemTitle);
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
                    showToast("Stream URL not found in response");
                    return;
                }

                if (streamUrl.startsWith("/")) {
                    streamUrl = BASE_URL + streamUrl;
                }

                final String finalUrl = streamUrl;
                runOnUiThread(() -> {
                    tvTitle.setText("PLAYING: " + itemTitle);
                    playStream(finalUrl);
                });
            }

            @Override
            public void onError(String message) {
                showToast("Resolve error: " + message);
            }
        });
    }

    private void playStream(String streamUrl) {
        if (player == null) return;
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
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
        if (!isShowingCategories) {
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
