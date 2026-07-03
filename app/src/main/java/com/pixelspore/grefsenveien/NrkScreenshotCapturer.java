package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class NrkScreenshotCapturer {

    private static final String TAG = "GrefsenveienApp";
    private static final String NRK_URL = "https://www.nrk.no";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<h3 class=\"kur-newsfeed__message-title\">([^<]+)");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("<time[^>]*class=\"kur-newsfeed__message-time\"[^>]*>\\s*([^<]+)");

    interface Callback {
        void onBitmapReady(@NonNull Bitmap bitmap);
        void onError();
    }

    private NrkScreenshotCapturer() {}

    static void capture(@NonNull Context context, int widthPx, int heightPx, @NonNull Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                Bitmap bitmap = fetchAndRender(widthPx, heightPx);
                main.post(() -> callback.onBitmapReady(bitmap));
            } catch (Exception e) {
                Log.e(TAG, "NRK.no fetch failed", e);
                main.post(callback::onError);
            }
        }, "NrkFetch").start();
    }

    private static Bitmap fetchAndRender(int width, int height) throws Exception {
        String html = fetchHtml();
        List<String> titles = parseMatches(html, TITLE_PATTERN);
        List<String> timestamps = parseMatches(html, TIMESTAMP_PATTERN);
        Bitmap bitmap = NewsColumnRenderer.render(NewsColumnRenderer.Brand.NRK, width, height, titles, timestamps);
        Log.d(TAG, "NRK.no render ready " + bitmap.getWidth() + "x" + bitmap.getHeight());
        return bitmap;
    }

    private static List<String> parseMatches(String html, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            results.add(matcher.group(1));
        }
        return results;
    }

    private static String fetchHtml() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(NRK_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.setRequestProperty("Accept-Language", "nb-NO,nb;q=0.9,no;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "gzip");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("NRK.no HTTP " + code);
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
