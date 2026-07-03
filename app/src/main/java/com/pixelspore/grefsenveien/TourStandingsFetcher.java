package com.pixelspore.grefsenveien;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class TourStandingsFetcher {

    private static final String TAG = "GrefsenveienApp";
    private static final String STANDINGS_URL = "https://letour.grense.land/json/standings.json";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    interface Callback {
        void onDataReady(@NonNull TourStandingsData data);
        void onError();
    }

    private TourStandingsFetcher() {}

    static void fetch(@NonNull Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                TourStandingsData data = loadData();
                main.post(() -> callback.onDataReady(data));
            } catch (Exception e) {
                Log.e(TAG, "Tour standings fetch failed", e);
                main.post(callback::onError);
            }
        }, "TourStandingsFetch").start();
    }

    private static TourStandingsData loadData() throws Exception {
        String body = fetchJson();
        JSONObject root = new JSONObject(body);
        List<TourStandingsRider> standings = parseRiders(root.optJSONArray("standings"));
        if (standings.isEmpty()) {
            standings = parseRiders(root.optJSONArray("top20"));
        }
        if (standings.isEmpty()) {
            throw new IllegalStateException("No standings in response");
        }
        List<TourStandingsRider> norwegians = parseRiders(root.optJSONArray("norwegians"));
        JSONObject meta = root.optJSONObject("meta");
        int stage = meta != null ? meta.optInt("stage", 0) : 0;
        int year = meta != null ? meta.optInt("year", 0) : 0;
        return new TourStandingsData(standings, norwegians, stage, year);
    }

    private static List<TourStandingsRider> parseRiders(JSONArray array) {
        List<TourStandingsRider> riders = new ArrayList<>();
        if (array == null) {
            return riders;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            riders.add(new TourStandingsRider(
                    row.optInt("rank", 0),
                    row.optString("rider", ""),
                    row.optString("bib", ""),
                    row.optString("team", ""),
                    row.optString("nationality", ""),
                    row.optString("time", ""),
                    row.optString("gap", "")));
        }
        return riders;
    }

    private static String fetchJson() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(STANDINGS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Tour standings HTTP " + connection.getResponseCode());
            }

            InputStream stream = connection.getInputStream();
            String encoding = connection.getContentEncoding();
            if (encoding != null && encoding.contains("gzip")) {
                stream = new GZIPInputStream(stream);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    body.append(buffer, 0, read);
                }
            }
            return body.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
