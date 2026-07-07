package com.pixelspore.grefsenveien;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

final class TourLiveFetcher {

    private static final String TAG = "GrefsenveienApp";
    private static final String LIVE_URL = "https://letour.grense.land/json/live-api.json";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    interface Callback {
        void onDataReady(@NonNull TourLiveData data);
        void onError();
    }

    private TourLiveFetcher() {}

    static void fetch(@NonNull Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                TourLiveData data = loadData();
                main.post(() -> callback.onDataReady(data));
            } catch (Exception e) {
                Log.e(TAG, "Tour live fetch failed", e);
                main.post(callback::onError);
            }
        }, "TourLiveFetch").start();
    }

    private static TourLiveData loadData() throws Exception {
        JSONObject root = new JSONObject(fetchJson());
        JSONObject meta = root.optJSONObject("meta");
        int stage = meta != null ? meta.optInt("stage", 0) : 0;
        int year = meta != null ? meta.optInt("year", 0) : 0;
        boolean simulated = meta != null && meta.optBoolean("simulated", false);

        JSONObject progress = root.optJSONObject("stageProgress");
        int kmLeft = progress != null ? progress.optInt("kmLeft", 0) : 0;
        int progressPct = progress != null ? progress.optInt("progressPct", 0) : 0;
        String avgSpeed = progress != null ? progress.optString("avgSpeedKmh", "") : "";
        String estFinish = progress != null ? progress.optString("estimatedFinishLocal", "") : "";

        return new TourLiveData(stage, year, simulated, kmLeft, progressPct, avgSpeed, estFinish,
                parseVirtualStandings(root.optJSONArray("virtualStandings")),
                parseJerseys(root.optJSONArray("jerseys")),
                parseTrackGroups(root),
                parseTrackGaps(root),
                parseFieldGroups(root),
                parseMissingTelemetry(root.optJSONArray("missingTelemetry")));
    }

    private static List<TourLiveVirtualRider> parseVirtualStandings(@Nullable JSONArray array) {
        List<TourLiveVirtualRider> riders = new ArrayList<>();
        if (array == null) {
            return riders;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            riders.add(new TourLiveVirtualRider(
                    row.optInt("virtualRank", 0),
                    row.optString("rider", ""),
                    row.optBoolean("isNorwegian", false),
                    row.optString("teamCode", ""),
                    row.optString("natCode", ""),
                    row.optString("yesterdayGap", ""),
                    row.optString("virtualGap", ""),
                    row.optInt("rankChange", 0),
                    row.optBoolean("telemetryEstimated", false)));
        }
        return riders;
    }

    private static List<TourLiveJersey> parseJerseys(@Nullable JSONArray array) {
        List<TourLiveJersey> jerseys = new ArrayList<>();
        if (array == null) {
            return jerseys;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            jerseys.add(new TourLiveJersey(
                    row.optString("code", ""),
                    row.optString("title", ""),
                    row.optString("rider", ""),
                    row.optBoolean("isNorwegian", false),
                    row.optInt("groupNumber", 0),
                    row.optBoolean("telemetryEstimated", false)));
        }
        return jerseys;
    }

    private static List<TourLiveTrackGroup> parseTrackGroups(JSONObject root) {
        List<TourLiveTrackGroup> groups = new ArrayList<>();
        JSONObject field = root.optJSONObject("field");
        if (field == null) {
            return groups;
        }
        JSONObject track = field.optJSONObject("track");
        JSONArray array = track != null ? track.optJSONArray("groups") : null;
        if (array == null) {
            return groups;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            groups.add(new TourLiveTrackGroup(
                    row.optInt("number", 0),
                    (float) row.optDouble("positionPct", 0.0)));
        }
        return groups;
    }

    private static List<TourLiveTrackGap> parseTrackGaps(JSONObject root) {
        List<TourLiveTrackGap> gaps = new ArrayList<>();
        JSONObject field = root.optJSONObject("field");
        if (field == null) {
            return gaps;
        }
        JSONObject track = field.optJSONObject("track");
        JSONArray array = track != null ? track.optJSONArray("gapsBetweenGroups") : null;
        if (array == null) {
            return gaps;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            gaps.add(new TourLiveTrackGap(
                    (float) row.optDouble("midPct", 0.0),
                    row.optString("gapText", "")));
        }
        return gaps;
    }

    private static List<TourLiveFieldGroup> parseFieldGroups(JSONObject root) {
        List<TourLiveFieldGroup> groups = new ArrayList<>();
        JSONObject field = root.optJSONObject("field");
        JSONArray array = field != null ? field.optJSONArray("groups") : null;
        if (array == null) {
            return groups;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            List<String> jerseyCodes = new ArrayList<>();
            JSONArray jerseyArray = row.optJSONArray("jerseyCodes");
            if (jerseyArray != null) {
                for (int j = 0; j < jerseyArray.length(); j++) {
                    jerseyCodes.add(jerseyArray.optString(j, ""));
                }
            }
            List<TourLiveDisplayName> names = new ArrayList<>();
            JSONArray nameArray = row.optJSONArray("displayNames");
            if (nameArray != null) {
                for (int j = 0; j < nameArray.length(); j++) {
                    JSONObject nameRow = nameArray.optJSONObject(j);
                    if (nameRow == null) {
                        continue;
                    }
                    names.add(new TourLiveDisplayName(
                            nameRow.optString("rider", ""),
                            nameRow.optBoolean("isNorwegian", false),
                            nameRow.optString("teamCode", ""),
                            nameRow.optString("natCode", ""),
                            nameRow.optInt("gcRank", 0)));
                }
            }
            String gapToPrev = row.optString("gapToPreviousGroupText", "");
            groups.add(new TourLiveFieldGroup(
                    row.optInt("number", 0),
                    row.optString("gapText", ""),
                    gapToPrev.isEmpty() ? null : gapToPrev,
                    row.optInt("riderCount", 0),
                    jerseyCodes,
                    names));
        }
        return groups;
    }

    private static List<TourLiveMissingRider> parseMissingTelemetry(@Nullable JSONArray array) {
        List<TourLiveMissingRider> riders = new ArrayList<>();
        if (array == null) {
            return riders;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) {
                continue;
            }
            int gcRank = row.has("gcRank") && !row.isNull("gcRank")
                    ? row.optInt("gcRank", 0) : -1;
            riders.add(new TourLiveMissingRider(
                    row.optString("rider", ""),
                    row.optBoolean("isNorwegian", false),
                    row.optString("teamCode", ""),
                    row.optString("natCode", ""),
                    gcRank,
                    row.optString("yesterdayGap", "")));
        }
        return riders;
    }

    private static String fetchJson() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(LIVE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Tour live HTTP " + connection.getResponseCode());
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
