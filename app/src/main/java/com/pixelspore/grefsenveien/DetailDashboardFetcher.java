package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class DetailDashboardFetcher {

    private static final String TAG = "GrefsenveienApp";

    private static final int HA_CONNECT_TIMEOUT_MS = 15_000;
    private static final int HA_READ_TIMEOUT_MS = 15_000;
    private static final int HA_LONG_READ_TIMEOUT_MS = 60_000;
    private static final int HA_MAX_ATTEMPTS = 3;
    private static final int HA_RETRY_DELAY_MS = 1_500;

    private static final String RAIN_WEEKLY_ENTITY_ID = "sensor.vaerstasjon_weekly_rain";
    private static final String RAIN_DAILY_ENTITY_ID = "sensor.vaerstasjon_daily_rain";
    private static final String TEMP_ENTITY_ID = "sensor.vaerstasjon_temp";
    private static final String WEATHER_CAMERA_BATTERY_ENTITY = "sensor.tak_battery";
    private static final int RAIN_WEEKS = 12;
    private static final int RAIN_HISTORY_DAYS = 91;
    private static final int TEMP_MINMAX_DAYS = 60;

    interface Callback {
        void onDataReady(@NonNull DetailDashboardData data);
        void onError();
    }

    private DetailDashboardFetcher() {}

    static void fetch(@NonNull Context context, @NonNull Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                DetailDashboardData data = loadAll();
                fetchCameraImages(data);
                main.post(() -> callback.onDataReady(data));
            } catch (Exception e) {
                Log.e(TAG, "DetailDashboard fetch failed", e);
                main.post(callback::onError);
            }
        }, "DetailDashboardFetch").start();
    }

    private static DetailDashboardData loadAll() {
        DetailDashboardData data = new DetailDashboardData();
        long now = System.currentTimeMillis();
        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        Calendar dayCal = Calendar.getInstance();
        dayCal.setTimeInMillis(now);
        dayCal.set(Calendar.HOUR_OF_DAY, 0);
        dayCal.set(Calendar.MINUTE, 0);
        dayCal.set(Calendar.SECOND, 0);
        dayCal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = dayCal.getTimeInMillis();
        long yesterdayMidnight = todayMidnight - 24L * 3600_000;
        long twoDaysAgoMidnight = yesterdayMidnight - 24L * 3600_000;

        List<float[]> todayTempPoints = new ArrayList<>();
        List<float[]> yesterdayTempPoints = new ArrayList<>();
        List<float[]> twoDaysAgoTempPoints = new ArrayList<>();
        List<float[]> sixtyDayAvgTempPoints = new ArrayList<>();
        float[] rainByWeek = new float[RAIN_WEEKS];

        // 1. Temperature history (72h)
        try {
            String tempUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(now - 72L * 3600_000))
                    + "?filter_entity_id=sensor.vaerstasjon_temp"
                    + "&end_time=" + isoFmt.format(new Date(now));
            String json = fetchHaJsonWithRetry("Temperature history", tempUrl,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (json != null) {
                JSONArray outer = new JSONArray(json);
                if (outer.length() > 0) {
                    JSONArray states = outer.getJSONArray(0);
                    float lastTodayVal = 0f;
                    boolean hasTodayVal = false;
                    for (int i = 0; i < states.length(); i++) {
                        JSONObject obj = states.getJSONObject(i);
                        try {
                            float temp = Float.parseFloat(obj.getString("state"));
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            if (ts >= todayMidnight) {
                                float hourOfDay = (ts - todayMidnight) / 3_600_000f;
                                if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                    todayTempPoints.add(new float[]{hourOfDay, temp});
                                    lastTodayVal = temp;
                                    hasTodayVal = true;
                                }
                            } else if (ts >= yesterdayMidnight) {
                                float hourOfDay = (ts - yesterdayMidnight) / 3_600_000f;
                                if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                    yesterdayTempPoints.add(new float[]{hourOfDay, temp});
                                }
                            } else if (ts >= twoDaysAgoMidnight) {
                                float hourOfDay = (ts - twoDaysAgoMidnight) / 3_600_000f;
                                if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                    twoDaysAgoTempPoints.add(new float[]{hourOfDay, temp});
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    if (hasTodayVal) {
                        float currentHour = (now - todayMidnight) / 3_600_000f;
                        todayTempPoints.add(new float[]{currentHour, lastTodayVal});
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse temperature history", e);
        }

        // 1b. 60-day average temperature curve
        long sixtyDaysAgo = now - (long) TEMP_MINMAX_DAYS * 24 * 3600_000;
        try {
            String avgTempUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(sixtyDaysAgo))
                    + "?filter_entity_id=" + TEMP_ENTITY_ID
                    + "&end_time=" + isoFmt.format(new Date(now));
            String avgJson = fetchHaJsonWithRetry("Temperature 60d average", avgTempUrl,
                    HA_CONNECT_TIMEOUT_MS, HA_LONG_READ_TIMEOUT_MS);
            if (avgJson != null) {
                JSONArray outer = new JSONArray(avgJson);
                if (outer.length() > 0) {
                    sixtyDayAvgTempPoints = computeSixtyDayHourlyAverage(
                            outer.getJSONArray(0), sixtyDaysAgo);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse 60-day temperature average", e);
        }

        // 1c. Daily min/max (60 days)
        try {
            List<DetailDashboardData.DailyTempRange> tempDays =
                    fetchTempDailyMinMaxFromStatistics(sixtyDaysAgo, now, TEMP_MINMAX_DAYS, isoFmt);
            if (tempDays == null || !hasAnyTempDayData(tempDays)) {
                String temp60Url = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(sixtyDaysAgo))
                        + "?filter_entity_id=" + TEMP_ENTITY_ID
                        + "&end_time=" + isoFmt.format(new Date(now));
                String temp60Json = fetchHaJsonWithRetry("Temperature 60d min/max", temp60Url,
                        HA_CONNECT_TIMEOUT_MS, HA_LONG_READ_TIMEOUT_MS);
                if (temp60Json != null) {
                    JSONArray outer60 = new JSONArray(temp60Json);
                    if (outer60.length() > 0) {
                        tempDays = computeDailyTempMinMax(
                                outer60.getJSONArray(0), sixtyDaysAgo, now, TEMP_MINMAX_DAYS);
                    }
                }
            }
            if (tempDays != null) {
                data.tempMinMax60d = tempDays;
                data.temp60dPeriodMin = Float.NaN;
                data.temp60dPeriodMax = Float.NaN;
                for (DetailDashboardData.DailyTempRange day : tempDays) {
                    if (!day.hasData) continue;
                    if (Float.isNaN(data.temp60dPeriodMin) || day.min < data.temp60dPeriodMin) {
                        data.temp60dPeriodMin = day.min;
                    }
                    if (Float.isNaN(data.temp60dPeriodMax) || day.max > data.temp60dPeriodMax) {
                        data.temp60dPeriodMax = day.max;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse 60-day temperature min/max", e);
        }

        // 2. Rain 12 weeks
        try {
            long rainStartMs = now - (long) RAIN_HISTORY_DAYS * 24 * 3600_000;
            TreeMap<Long, Float> rainPerWeek = fetchRainWeeklyStatistics(
                    rainStartMs, now, RAIN_WEEKLY_ENTITY_ID, "max");
            if (rainPerWeek == null || rainPerWeek.isEmpty()) {
                rainPerWeek = fetchRainWeeklyStatistics(
                        rainStartMs, now, RAIN_DAILY_ENTITY_ID, "change");
            }
            if (rainPerWeek == null || rainPerWeek.isEmpty()) {
                SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                dayFmt.setTimeZone(java.util.TimeZone.getDefault());
                String rainUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(rainStartMs))
                        + "?filter_entity_id=" + RAIN_DAILY_ENTITY_ID
                        + "&end_time=" + isoFmt.format(new Date(now));
                String json = fetchHaJsonWithRetry("Rain history", rainUrl,
                        HA_CONNECT_TIMEOUT_MS, HA_LONG_READ_TIMEOUT_MS);
                if (json != null) {
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        TreeMap<String, Float> maxPerDay = parseRainDailyMaxFromHistory(
                                outer.getJSONArray(0), dayFmt);
                        rainPerWeek = aggregateRainDailyToWeeks(maxPerDay, dayFmt);
                    }
                }
            }
            if (rainPerWeek != null && !rainPerWeek.isEmpty()) {
                applyRainWeeklyToChart(rainPerWeek, now, rainByWeek);
            }
            data.todayRainMm = fetchSensorState(RAIN_DAILY_ENTITY_ID, 0f);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse rain data", e);
        }

        // 2b. Lightning 7d
        List<DetailDashboardData.LightningEvent> lightningEvents = new ArrayList<>();
        long sevenDaysAgo = now - 7L * 24 * 3600_000;
        try {
            String lightningUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(sevenDaysAgo))
                    + "?filter_entity_id=sensor.vaerstasjon_lightning_strike_distance"
                    + "&end_time=" + isoFmt.format(new Date(now));
            String lightningJson = fetchHaJsonWithRetry("Lightning history", lightningUrl,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (lightningJson != null) {
                JSONArray outer = new JSONArray(lightningJson);
                if (outer.length() > 0) {
                    JSONArray states = outer.getJSONArray(0);
                    float prevDist = -1f;
                    long prevTs = 0L;
                    for (int i = 0; i < states.length(); i++) {
                        JSONObject obj = states.getJSONObject(i);
                        try {
                            String stateStr = obj.getString("state");
                            if ("unavailable".equals(stateStr) || "unknown".equals(stateStr)) continue;
                            float dist = Float.parseFloat(stateStr);
                            if (dist <= 0f) continue;
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            if (ts < sevenDaysAgo) continue;
                            if (prevDist < 0f || Math.abs(dist - prevDist) > 0.01f || ts - prevTs > 120_000L) {
                                lightningEvents.add(new DetailDashboardData.LightningEvent(ts, dist));
                            }
                            prevDist = dist;
                            prevTs = ts;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse lightning history", e);
        }
        lightningEvents.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        float lastLightningDist = fetchSensorState("sensor.vaerstasjon_lightning_strike_distance", -1f);
        if (lastLightningDist <= 0f && !lightningEvents.isEmpty()) {
            lastLightningDist = lightningEvents.get(lightningEvents.size() - 1).distanceKm;
        }
        float nearestLightningDist = -1f;
        for (DetailDashboardData.LightningEvent event : lightningEvents) {
            if (nearestLightningDist < 0f || event.distanceKm < nearestLightningDist) {
                nearestLightningDist = event.distanceKm;
            }
        }
        data.lightningEvents7d = lightningEvents;
        data.lastLightningDistanceKm = lastLightningDist;
        data.nearestLightningDistanceKm = nearestLightningDist;
        data.lightningCount7d = lightningEvents.size();
        data.lightningWindowStartMs = sevenDaysAgo;
        data.lightningWindowEndMs = now;

        // 3. Hourly rain rate
        try {
            String hourlyRainUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(now - 24L * 3600_000))
                    + "?filter_entity_id=sensor.vaerstasjon_hourly_rain_rate"
                    + "&end_time=" + isoFmt.format(new Date(now));
            String json = fetchHaJsonWithRetry("Hourly rain rate", hourlyRainUrl,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (json != null) {
                JSONArray outer = new JSONArray(json);
                if (outer.length() > 0) {
                    JSONArray states = outer.getJSONArray(0);
                    for (int i = 0; i < states.length(); i++) {
                        JSONObject obj = states.getJSONObject(i);
                        try {
                            float rate = Float.parseFloat(obj.getString("state"));
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            int hourIndex = (int) ((now - ts) / 3_600_000L);
                            if (hourIndex >= 0 && hourIndex < 24) {
                                data.hourlyRain[hourIndex] = Math.max(data.hourlyRain[hourIndex], rate);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch hourly_rain_rate", e);
        }

        // 4. Soil humidity
        try {
            String soilUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(now - 3L * 24 * 3600_000))
                    + "?filter_entity_id=sensor.vaerstasjon_soil_humidity_1"
                    + "&end_time=" + isoFmt.format(new Date(now));
            String json = fetchHaJsonWithRetry("Soil humidity", soilUrl,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (json != null) {
                JSONArray outer = new JSONArray(json);
                if (outer.length() > 0) {
                    JSONArray states = outer.getJSONArray(0);
                    List<float[]> soilPoints = new ArrayList<>();
                    float lastVal = 0f;
                    boolean hasVal = false;
                    for (int i = 0; i < states.length(); i++) {
                        JSONObject obj = states.getJSONObject(i);
                        try {
                            float humidity = Float.parseFloat(obj.getString("state"));
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            float hoursAgo = (now - ts) / 3_600_000f;
                            if (hoursAgo >= 0 && hoursAgo <= 72) {
                                soilPoints.add(new float[]{hoursAgo, humidity});
                                lastVal = humidity;
                                hasVal = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    if (hasVal) {
                        soilPoints.add(new float[]{0f, lastVal});
                    }
                    data.soilPoints = soilPoints;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch soil_humidity", e);
        }

        // Process temperature points
        float curTemp = 15f;
        if (todayTempPoints.isEmpty()) {
            todayTempPoints.add(new float[]{0f, 15f});
        } else {
            todayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
            curTemp = todayTempPoints.get(todayTempPoints.size() - 1)[1];
        }
        if (!yesterdayTempPoints.isEmpty()) {
            yesterdayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
        }
        if (!twoDaysAgoTempPoints.isEmpty()) {
            twoDaysAgoTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
        }

        float[] todayTempMinMax = computeTodayTempMinMax(todayTempPoints);
        todayTempPoints = bucketTempPointsHourly(todayTempPoints);
        yesterdayTempPoints = bucketTempPointsHourly(yesterdayTempPoints);
        twoDaysAgoTempPoints = bucketTempPointsHourly(twoDaysAgoTempPoints);
        float currentHour = (now - todayMidnight) / 3_600_000f;

        if (!todayTempPoints.isEmpty()) {
            float lastVal = todayTempPoints.get(todayTempPoints.size() - 1)[1];
            if (todayTempPoints.get(todayTempPoints.size() - 1)[0] >= currentHour) {
                todayTempPoints.remove(todayTempPoints.size() - 1);
            }
            todayTempPoints.add(new float[]{currentHour, lastVal});
            curTemp = lastVal;
        }
        if (!yesterdayTempPoints.isEmpty()) {
            float[] last = yesterdayTempPoints.get(yesterdayTempPoints.size() - 1);
            if (last[0] < 24f) {
                yesterdayTempPoints.add(new float[]{24f, last[1]});
            }
        }
        if (!twoDaysAgoTempPoints.isEmpty()) {
            float[] last = twoDaysAgoTempPoints.get(twoDaysAgoTempPoints.size() - 1);
            if (last[0] < 24f) {
                twoDaysAgoTempPoints.add(new float[]{24f, last[1]});
            }
        }
        if (!sixtyDayAvgTempPoints.isEmpty()) {
            sixtyDayAvgTempPoints = alignAverageCurveToNow(sixtyDayAvgTempPoints, currentHour, curTemp);
        }

        data.todayTempPoints = todayTempPoints;
        data.yesterdayTempPoints = yesterdayTempPoints;
        data.twoDaysAgoTempPoints = twoDaysAgoTempPoints;
        data.sixtyDayAvgTempPoints = sixtyDayAvgTempPoints;
        data.todayTempMin = todayTempMinMax[0];
        data.todayTempMax = todayTempMinMax[1];
        data.currentOutdoorTemp = curTemp;
        System.arraycopy(rainByWeek, 0, data.rainByWeek, 0, rainByWeek.length);

        // Room temperatures
        data.valJonatan = fetchSensorState("sensor.jonatan_temperatur_temperature", curTemp + 0.0f);
        data.valLoftsgang = fetchSensorState("sensor.loftsgang_temperatur_temperature", curTemp + 2.2f);
        data.valKontor = fetchSensorState("sensor.kontor_temperatur_temperature", curTemp + 2.8f);
        data.valBad = fetchSensorState("sensor.stort_bad_temperatur_temperatur", curTemp + 0.8f);
        data.valVinterhage = fetchSensorState("sensor.vaerstasjon_inside_temp", curTemp + 6.7f);
        data.valKjokken = fetchSensorState("sensor.kjokken_temperatur_temperature", curTemp - 0.1f);
        data.valLiteBad = fetchSensorState("sensor.lite_bad_temperatur_temperature", curTemp - 0.1f);
        data.valMats = fetchSensorState("sensor.mats_temperatur_temperature", curTemp - 1.2f);
        data.valStue = fetchSensorState("sensor.stue_temperatur_temperature", curTemp + 1.8f);
        data.valGang3 = fetchSensorState("sensor.innergang_temperatur_temperature_2", curTemp + 0.2f);
        data.valSoverom = fetchSensorState("sensor.soverom_temperatur_temperature", curTemp - 1.1f);
        data.valVaskerom = fetchSensorState("sensor.vaskerom_temperatur_temperature", curTemp + 0.4f);
        data.valHumidity = fetchSensorState("sensor.vaerstasjon_humidity", 45f);
        data.valSolarRad = fetchSensorState("sensor.vaerstasjon_solar_rad", 0f);
        data.valRainRate = fetchSensorState("sensor.vaerstasjon_hourly_rain_rate", 0f);
        data.sunAzimuth = fetchSensorState("sensor.sun_solar_azimuth", 180f);
        data.sunElevation = fetchSensorState("sensor.sun_solar_elevation", 0f);
        data.sunNextRisingMs = parseHaDatetime(fetchSensorStateString("sensor.sun_next_rising", ""));
        data.sunNextSettingMs = parseHaDatetime(fetchSensorStateString("sensor.sun_next_setting", ""));
        data.valSolarEnergy24h = computeRollingSolarEnergy24h(now, isoFmt);

        // Motion histories
        data.valStueMotionTime = Math.max(
                fetchMotionHistory("binary_sensor.stue_ved_vindu_bevegelsessensor_occupancy", now, isoFmt),
                fetchMotionHistory("binary_sensor.stue_innerst_bevegelsessensor_occupancy", now, isoFmt));
        data.valLoftsgangMotionTime = fetchMotionHistory("binary_sensor.loftsgang_bevegelsessensor_occupancy", now, isoFmt);
        data.valGang4MotionTime = fetchMotionHistory("binary_sensor.inngang_bevegelsessensor_motion_detection", now, isoFmt);
        data.valJonatanMotionTime = fetchMotionHistory("binary_sensor.jonatan_bevegelsessensor_occupancy", now, isoFmt);
        data.valBadMotionTime = fetchMotionHistory("binary_sensor.stort_bad_bevegelsessensor_occupancy", now, isoFmt);
        data.valVaskeromMotionTime = fetchMotionHistory("binary_sensor.vaskerom_bevegelsessensor_occupancy", now, isoFmt);

        // Weather battery
        updateWeatherBattery(data);

        return data;
    }

    // -------------------------------------------------------------------------
    // Camera images
    // -------------------------------------------------------------------------

    private static void fetchCameraImages(DetailDashboardData data) {
        data.weatherBitmap = fetchImage(BuildConfig.WEATHER_CAMERA_URL);
        data.weatherTimestamp = fetchImageTimestamp(BuildConfig.WEATHER_CAMERA_URL);
        data.yardBitmap = fetchImage(BuildConfig.S3_IMAGE_URL);
        data.yardTimestamp = fetchImageTimestamp(BuildConfig.S3_IMAGE_URL);
        data.mailboxBitmap = fetchImage(BuildConfig.S3_MAILBOX_IMAGE_URL);
        data.mailboxTimestamp = fetchImageTimestamp(BuildConfig.S3_MAILBOX_IMAGE_URL);
    }

    @Nullable
    private static Bitmap fetchImage(String imageUrl) {
        try {
            String urlWithTs = imageUrl + (imageUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
            URL url = new URL(urlWithTs);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                InputStream input = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(input);
                conn.disconnect();
                return bmp;
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch image: " + imageUrl, e);
        }
        return null;
    }

    @NonNull
    private static String fetchImageTimestamp(String imageUrl) {
        try {
            String urlWithTs = imageUrl + (imageUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
            URL url = new URL(urlWithTs);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setUseCaches(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String lastMod = conn.getHeaderField("Last-Modified");
                String dateHdr = conn.getHeaderField("Date");
                conn.disconnect();
                String dateHeader = lastMod != null ? lastMod : dateHdr;
                if (dateHeader != null) {
                    try {
                        SimpleDateFormat httpFormat = new SimpleDateFormat(
                                "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                        Date parsed = httpFormat.parse(dateHeader);
                        if (parsed != null) {
                            return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",
                                    Locale.getDefault()).format(parsed);
                        }
                    } catch (Exception ignored) {}
                    return dateHeader;
                }
            } else {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch image timestamp: " + imageUrl, e);
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // HA HTTP helpers
    // -------------------------------------------------------------------------

    @Nullable
    private static String fetchHaJsonWithRetry(@NonNull String label, @NonNull String url,
            int connectTimeoutMs, int readTimeoutMs) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= HA_MAX_ATTEMPTS; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                conn.connect();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    return json;
                }
                lastError = new java.io.IOException("HTTP " + responseCode);
            } catch (java.net.SocketTimeoutException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (attempt < HA_MAX_ATTEMPTS) {
                try { Thread.sleep(HA_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            Log.e(TAG, label + " gave up after " + HA_MAX_ATTEMPTS + " attempts", lastError);
        }
        return null;
    }

    @Nullable
    private static String fetchHaServiceResponseJson(@NonNull String label, @NonNull String domain,
            @NonNull String service, @NonNull JSONObject body,
            int connectTimeoutMs, int readTimeoutMs) {
        String urlStr = BuildConfig.HA_BASE_URL + "/api/services/" + domain + "/" + service + "?return_response";
        Exception lastError = null;
        for (int attempt = 1; attempt <= HA_MAX_ATTEMPTS; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                conn.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(payload);
                conn.connect();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
                lastError = new java.io.IOException("HTTP " + responseCode);
            } catch (Exception e) {
                lastError = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (attempt < HA_MAX_ATTEMPTS) {
                try { Thread.sleep(HA_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            Log.e(TAG, label + " gave up after " + HA_MAX_ATTEMPTS + " attempts", lastError);
        }
        return null;
    }

    private static float fetchSensorState(String entityId, float fallbackValue) {
        try {
            String urlStr = BuildConfig.HA_BASE_URL + "/api/states/" + entityId;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();
                JSONObject obj = new JSONObject(json);
                return Float.parseFloat(obj.getString("state"));
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch state for " + entityId, e);
        }
        return fallbackValue;
    }

    @NonNull
    private static String fetchSensorStateString(String entityId, String fallbackValue) {
        try {
            String urlStr = BuildConfig.HA_BASE_URL + "/api/states/" + entityId;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();
                JSONObject obj = new JSONObject(json);
                return obj.getString("state");
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch state for " + entityId, e);
        }
        return fallbackValue;
    }

    // -------------------------------------------------------------------------
    // Timestamp / date parsing
    // -------------------------------------------------------------------------

    private static long parseIsoTimestamp(String iso) {
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(s);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static long parseHaDatetime(String iso) {
        if (iso == null || iso.isEmpty() || "unavailable".equals(iso) || "unknown".equals(iso)) {
            return 0L;
        }
        try {
            SimpleDateFormat sdf;
            if (iso.endsWith("Z")) {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            } else if (iso.length() > 19 && (iso.charAt(19) == '+' || iso.charAt(19) == '-')) {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            }
            Date d = sdf.parse(iso);
            return d != null ? d.getTime() : 0L;
        } catch (Exception e) {
            return parseIsoTimestamp(iso);
        }
    }

    // -------------------------------------------------------------------------
    // Rain helpers
    // -------------------------------------------------------------------------

    private static long getWeekMondayMillis(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
        return cal.getTimeInMillis();
    }

    @Nullable
    private static TreeMap<Long, Float> fetchRainWeeklyStatistics(long startMs, long endMs,
            @NonNull String entityId, @NonNull String... statTypes) {
        try {
            SimpleDateFormat haTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            haTimeFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            JSONObject statsBody = new JSONObject();
            statsBody.put("start_time", haTimeFmt.format(new Date(startMs)));
            statsBody.put("end_time", haTimeFmt.format(new Date(endMs)));
            JSONArray statIds = new JSONArray();
            statIds.put(entityId);
            statsBody.put("statistic_ids", statIds);
            statsBody.put("period", "week");
            JSONArray types = new JSONArray();
            for (String statType : statTypes) types.put(statType);
            statsBody.put("types", types);

            String statsJson = fetchHaServiceResponseJson("Rain statistics (" + entityId + ")",
                    "recorder", "get_statistics", statsBody,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (statsJson == null) return null;

            TreeMap<Long, Float> rainPerWeek = new TreeMap<>();
            JSONObject root = new JSONObject(statsJson);
            JSONObject serviceResponse = root.getJSONObject("service_response");
            JSONObject statistics = serviceResponse.getJSONObject("statistics");
            if (!statistics.has(entityId)) return null;
            JSONArray periods = statistics.getJSONArray(entityId);
            for (int i = 0; i < periods.length(); i++) {
                JSONObject row = periods.getJSONObject(i);
                float rain = readRainStatValue(row, statTypes);
                if (Float.isNaN(rain)) continue;
                long weekMonday = getWeekMondayMillis(parseHaDatetime(row.getString("start")));
                rainPerWeek.put(weekMonday, rain);
            }
            return rainPerWeek.isEmpty() ? null : rainPerWeek;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch rain statistics for " + entityId, e);
            return null;
        }
    }

    private static float readRainStatValue(@NonNull JSONObject row, @NonNull String[] statTypes)
            throws JSONException {
        for (String statType : statTypes) {
            if (row.has(statType) && !row.isNull(statType)) {
                return Math.max(0f, (float) row.getDouble(statType));
            }
        }
        return Float.NaN;
    }

    @NonNull
    private static TreeMap<String, Float> parseRainDailyMaxFromHistory(@NonNull JSONArray states,
            @NonNull SimpleDateFormat dayFmt) {
        TreeMap<String, Float> maxPerDay = new TreeMap<>();
        for (int i = 0; i < states.length(); i++) {
            try {
                JSONObject obj = states.getJSONObject(i);
                float rain = Float.parseFloat(obj.getString("state"));
                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                String dayKey = dayFmt.format(new Date(ts));
                Float cur = maxPerDay.get(dayKey);
                if (cur == null || rain > cur) {
                    maxPerDay.put(dayKey, rain);
                }
            } catch (Exception ignored) {}
        }
        return maxPerDay;
    }

    @NonNull
    private static TreeMap<Long, Float> aggregateRainDailyToWeeks(@NonNull TreeMap<String, Float> maxPerDay,
            @NonNull SimpleDateFormat dayFmt) {
        TreeMap<Long, Float> rainPerWeek = new TreeMap<>();
        for (Map.Entry<String, Float> entry : maxPerDay.entrySet()) {
            try {
                Date day = dayFmt.parse(entry.getKey());
                if (day == null) continue;
                long weekMonday = getWeekMondayMillis(day.getTime());
                float prev = rainPerWeek.containsKey(weekMonday) ? rainPerWeek.get(weekMonday) : 0f;
                rainPerWeek.put(weekMonday, prev + entry.getValue());
            } catch (Exception ignored) {}
        }
        return rainPerWeek;
    }

    private static void applyRainWeeklyToChart(@NonNull TreeMap<Long, Float> rainPerWeek, long now,
            @NonNull float[] rainByWeek) {
        Calendar weekCal = Calendar.getInstance();
        weekCal.setTimeInMillis(getWeekMondayMillis(now));
        for (int i = 0; i < RAIN_WEEKS && i < rainByWeek.length; i++) {
            Float val = rainPerWeek.get(weekCal.getTimeInMillis());
            rainByWeek[i] = val != null ? val : 0f;
            weekCal.add(Calendar.WEEK_OF_YEAR, -1);
        }
    }

    // -------------------------------------------------------------------------
    // Temperature helpers
    // -------------------------------------------------------------------------

    private static float[] computeTodayTempMinMax(List<float[]> rawTodayPoints) {
        float minT = Float.MAX_VALUE;
        float maxT = -Float.MAX_VALUE;
        for (float[] p : rawTodayPoints) {
            minT = Math.min(minT, p[1]);
            maxT = Math.max(maxT, p[1]);
        }
        if (minT == Float.MAX_VALUE) return new float[]{14f, 16f};
        return new float[]{minT, maxT};
    }

    private static List<float[]> bucketTempPointsHourly(List<float[]> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        raw.sort((a, b) -> Float.compare(a[0], b[0]));
        LinkedHashMap<Integer, Float> hourMap = new LinkedHashMap<>();
        for (float[] p : raw) {
            int hour = Math.min(23, Math.max(0, (int) Math.floor(p[0])));
            hourMap.put(hour, p[1]);
        }
        List<float[]> result = new ArrayList<>();
        for (Map.Entry<Integer, Float> entry : hourMap.entrySet()) {
            result.add(new float[]{entry.getKey(), entry.getValue()});
        }
        return result;
    }

    private static List<float[]> computeSixtyDayHourlyAverage(JSONArray states, long sinceMs) {
        double[] sum = new double[24];
        int[] count = new int[24];
        Calendar hourCal = Calendar.getInstance();
        for (int i = 0; i < states.length(); i++) {
            try {
                JSONObject obj = states.getJSONObject(i);
                float temp = Float.parseFloat(obj.getString("state"));
                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                if (ts < sinceMs) continue;
                hourCal.setTimeInMillis(ts);
                int hour = hourCal.get(Calendar.HOUR_OF_DAY);
                sum[hour] += temp;
                count[hour]++;
            } catch (Exception ignored) {}
        }
        List<float[]> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if (count[h] > 0) {
                result.add(new float[]{h, (float) (sum[h] / count[h])});
            }
        }
        if (!result.isEmpty()) {
            result.add(new float[]{24f, result.get(0)[1]});
        }
        return result;
    }

    private static float interpolateTempAtHour(List<float[]> points, float hour) {
        if (points.isEmpty()) return 0f;
        if (points.size() == 1) return points.get(0)[1];
        hour = Math.max(0f, Math.min(24f, hour));
        for (int i = 0; i < points.size() - 1; i++) {
            float[] a = points.get(i);
            float[] b = points.get(i + 1);
            if (hour >= a[0] && hour <= b[0]) {
                float span = b[0] - a[0];
                if (span < 0.0001f) return a[1];
                float t = (hour - a[0]) / span;
                return a[1] + t * (b[1] - a[1]);
            }
        }
        return points.get(points.size() - 1)[1];
    }

    private static List<float[]> alignAverageCurveToNow(List<float[]> avgPoints, float currentHour, float currentTemp) {
        if (avgPoints.isEmpty()) return avgPoints;
        float offset = currentTemp - interpolateTempAtHour(avgPoints, currentHour);
        List<float[]> adjusted = new ArrayList<>(avgPoints.size());
        for (float[] p : avgPoints) {
            adjusted.add(new float[]{p[0], p[1] + offset});
        }
        return adjusted;
    }

    @Nullable
    private static List<DetailDashboardData.DailyTempRange> fetchTempDailyMinMaxFromStatistics(
            long startMs, long endMs, int dayCount, SimpleDateFormat isoFmt) {
        try {
            SimpleDateFormat haTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            haTimeFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            JSONObject statsBody = new JSONObject();
            statsBody.put("start_time", haTimeFmt.format(new Date(startMs)));
            statsBody.put("end_time", haTimeFmt.format(new Date(endMs)));
            JSONArray statIds = new JSONArray();
            statIds.put(TEMP_ENTITY_ID);
            statsBody.put("statistic_ids", statIds);
            statsBody.put("period", "day");
            JSONArray types = new JSONArray();
            types.put("min");
            types.put("max");
            statsBody.put("types", types);

            String statsJson = fetchHaServiceResponseJson("Temperature daily min/max",
                    "recorder", "get_statistics", statsBody,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (statsJson == null) return null;
            return parseTempDailyMinMaxFromStatistics(statsJson, endMs, dayCount);
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch temperature daily statistics", e);
            return null;
        }
    }

    @NonNull
    private static List<DetailDashboardData.DailyTempRange> parseTempDailyMinMaxFromStatistics(
            @NonNull String json, long periodEndMs, int dayCount) throws JSONException {
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFmt.setTimeZone(java.util.TimeZone.getDefault());
        TreeMap<String, float[]> perDay = new TreeMap<>();

        JSONObject root = new JSONObject(json);
        JSONObject serviceResponse = root.getJSONObject("service_response");
        JSONObject statistics = serviceResponse.getJSONObject("statistics");
        if (statistics.has(TEMP_ENTITY_ID)) {
            JSONArray periods = statistics.getJSONArray(TEMP_ENTITY_ID);
            for (int i = 0; i < periods.length(); i++) {
                JSONObject row = periods.getJSONObject(i);
                if ((!row.has("min") || row.isNull("min")) && (!row.has("max") || row.isNull("max"))) continue;
                float min = row.has("min") && !row.isNull("min") ? (float) row.getDouble("min") : Float.NaN;
                float max = row.has("max") && !row.isNull("max") ? (float) row.getDouble("max") : Float.NaN;
                if (Float.isNaN(min) && Float.isNaN(max)) continue;
                if (Float.isNaN(min)) min = max;
                if (Float.isNaN(max)) max = min;
                long ts = parseHaDatetime(row.getString("start"));
                String dayKey = dayFmt.format(new Date(ts));
                float[] mm = perDay.get(dayKey);
                if (mm == null) {
                    perDay.put(dayKey, new float[]{min, max});
                } else {
                    mm[0] = Math.min(mm[0], min);
                    mm[1] = Math.max(mm[1], max);
                }
            }
        }
        return buildDailyTempRangeList(perDay, periodEndMs, dayCount, dayFmt);
    }

    private static List<DetailDashboardData.DailyTempRange> computeDailyTempMinMax(
            JSONArray states, long periodStartMs, long periodEndMs, int dayCount) throws JSONException {
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFmt.setTimeZone(java.util.TimeZone.getDefault());
        TreeMap<String, float[]> perDay = new TreeMap<>();
        for (int i = 0; i < states.length(); i++) {
            JSONObject obj = states.getJSONObject(i);
            try {
                String stateStr = obj.getString("state");
                if ("unavailable".equals(stateStr) || "unknown".equals(stateStr)) continue;
                float temp = Float.parseFloat(stateStr);
                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                if (ts < periodStartMs || ts > periodEndMs) continue;
                String dayKey = dayFmt.format(new Date(ts));
                float[] mm = perDay.get(dayKey);
                if (mm == null) {
                    perDay.put(dayKey, new float[]{temp, temp});
                } else {
                    mm[0] = Math.min(mm[0], temp);
                    mm[1] = Math.max(mm[1], temp);
                }
            } catch (NumberFormatException ignored) {}
        }
        return buildDailyTempRangeList(perDay, periodEndMs, dayCount, dayFmt);
    }

    @NonNull
    private static List<DetailDashboardData.DailyTempRange> buildDailyTempRangeList(
            @NonNull TreeMap<String, float[]> perDay, long periodEndMs, int dayCount,
            @NonNull SimpleDateFormat dayFmt) {
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeZone(java.util.TimeZone.getDefault());
        endCal.setTimeInMillis(periodEndMs);
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);

        List<DetailDashboardData.DailyTempRange> result = new ArrayList<>();
        for (int d = dayCount - 1; d >= 0; d--) {
            Calendar dc = (Calendar) endCal.clone();
            dc.add(Calendar.DAY_OF_MONTH, -d);
            String dayKey = dayFmt.format(dc.getTime());
            int dayOfMonth = dc.get(Calendar.DAY_OF_MONTH);
            float[] mm = perDay.get(dayKey);
            if (mm != null) {
                result.add(new DetailDashboardData.DailyTempRange(dayOfMonth, mm[0], mm[1], true));
            } else {
                result.add(new DetailDashboardData.DailyTempRange(dayOfMonth, 0f, 0f, false));
            }
        }
        return result;
    }

    private static boolean hasAnyTempDayData(@NonNull List<DetailDashboardData.DailyTempRange> days) {
        for (DetailDashboardData.DailyTempRange day : days) {
            if (day.hasData) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Solar energy
    // -------------------------------------------------------------------------

    private static float computeRollingSolarEnergy24h(long now, SimpleDateFormat isoFmt) {
        float current = fetchSensorState("sensor.solenergi", Float.NaN);
        if (Float.isNaN(current)) return 0f;
        long targetMs = now - 24L * 3_600_000L;
        float baseline = fetchAccumulatedSensorValueAtOrBefore("sensor.solenergi", targetMs, now, isoFmt);
        if (Float.isNaN(baseline)) return 0f;
        return Math.max(0f, current - baseline);
    }

    private static float fetchAccumulatedSensorValueAtOrBefore(String entityId, long targetMs,
            long now, SimpleDateFormat isoFmt) {
        try {
            long startMs = targetMs - 2L * 3_600_000L;
            String url = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(startMs))
                    + "?filter_entity_id=" + entityId
                    + "&end_time=" + isoFmt.format(new Date(now));
            String json = fetchHaJsonWithRetry("Solar cumulative (" + entityId + ")", url,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (json == null || json.isEmpty()) return Float.NaN;
            JSONArray outer = new JSONArray(json);
            if (outer.length() == 0) return Float.NaN;
            JSONArray states = outer.getJSONArray(0);
            float valueAtOrBefore = Float.NaN;
            for (int i = 0; i < states.length(); i++) {
                JSONObject obj = states.getJSONObject(i);
                String stateStr = obj.getString("state");
                if ("unavailable".equals(stateStr) || "unknown".equals(stateStr)) continue;
                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                try {
                    float val = Float.parseFloat(stateStr);
                    if (ts <= targetMs) valueAtOrBefore = val;
                } catch (NumberFormatException ignored) {}
            }
            return valueAtOrBefore;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch accumulated value for " + entityId, e);
            return Float.NaN;
        }
    }

    // -------------------------------------------------------------------------
    // Motion history
    // -------------------------------------------------------------------------

    private static long fetchMotionHistory(String entityId, long now, SimpleDateFormat isoFmt) {
        long motionTime = 0L;
        try {
            String urlStr = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(now - 24L * 3600_000))
                    + "?filter_entity_id=" + entityId
                    + "&end_time=" + isoFmt.format(new Date(now));
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();
                JSONArray outer = new JSONArray(json);
                if (outer.length() > 0) {
                    JSONArray states = outer.getJSONArray(0);
                    for (int i = 0; i < states.length(); i++) {
                        JSONObject obj = states.getJSONObject(i);
                        if ("on".equals(obj.getString("state"))) {
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            if (ts > motionTime) motionTime = ts;
                        }
                    }
                }
            } else {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch motion history for " + entityId, e);
        }
        return motionTime;
    }

    // -------------------------------------------------------------------------
    // Weather camera battery
    // -------------------------------------------------------------------------

    private static void updateWeatherBattery(DetailDashboardData data) {
        int current = fetchWeatherCameraStatusSync();
        if (current < 0) return;
        int min = current;
        int max = current;
        int[] range = fetchWeatherCameraBatteryRangeSinceMidnight();
        if (range != null) {
            min = Math.min(range[0], current);
            max = Math.max(range[1], current);
        }
        data.weatherBatteryPercent = current;
        data.weatherBatteryMinPercent = min;
        data.weatherBatteryMaxPercent = max;
    }

    private static int fetchWeatherCameraStatusSync() {
        try {
            String imageUrl = BuildConfig.WEATHER_CAMERA_URL;
            int lastSlash = imageUrl.lastIndexOf('/');
            String statusUrl = lastSlash >= 0
                    ? imageUrl.substring(0, lastSlash + 1) + "status.json"
                    : "https://weathercamera.s3.us-east-1.amazonaws.com/status.json";
            URL url = new URL(statusUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();
                JSONObject obj = new JSONObject(json);
                if (obj.has("percent") && !obj.isNull("percent")) {
                    return obj.getInt("percent");
                }
            } else {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch weather camera status", e);
        }
        return -1;
    }

    @Nullable
    private static int[] fetchWeatherCameraBatteryRangeSinceMidnight() {
        try {
            long now = System.currentTimeMillis();
            Calendar dayCal = Calendar.getInstance();
            dayCal.setTimeInMillis(now);
            dayCal.set(Calendar.HOUR_OF_DAY, 0);
            dayCal.set(Calendar.MINUTE, 0);
            dayCal.set(Calendar.SECOND, 0);
            dayCal.set(Calendar.MILLISECOND, 0);
            long midnight = dayCal.getTimeInMillis();

            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String url = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new Date(midnight))
                    + "?filter_entity_id=" + WEATHER_CAMERA_BATTERY_ENTITY
                    + "&end_time=" + isoFmt.format(new Date(now))
                    + "&minimal_response";
            String json = fetchHaJsonWithRetry("Weather camera battery history", url,
                    HA_CONNECT_TIMEOUT_MS, HA_READ_TIMEOUT_MS);
            if (json == null) return null;
            JSONArray outer = new JSONArray(json);
            if (outer.length() == 0) return null;
            JSONArray states = outer.getJSONArray(0);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            boolean found = false;
            for (int i = 0; i < states.length(); i++) {
                try {
                    JSONObject obj = states.getJSONObject(i);
                    int pct = Math.round(Float.parseFloat(obj.getString("state")));
                    if (pct < 0 || pct > 100) continue;
                    min = Math.min(min, pct);
                    max = Math.max(max, pct);
                    found = true;
                } catch (Exception ignored) {}
            }
            return found ? new int[]{min, max} : null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch weather camera battery range", e);
            return null;
        }
    }
}
