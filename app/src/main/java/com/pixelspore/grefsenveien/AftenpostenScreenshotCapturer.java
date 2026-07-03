package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class AftenpostenScreenshotCapturer {

    private static final String TAG = "GrefsenveienApp";
    private static final String AP_URL = "https://www.aftenposten.no";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    interface Callback {
        void onBitmapReady(@NonNull Bitmap bitmap);
        void onError();
    }

    private AftenpostenScreenshotCapturer() {}

    static void capture(@NonNull Context context, int widthPx, int heightPx, @NonNull Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            Bitmap bitmap = fetchAndRender(widthPx, heightPx);
            if (bitmap != null) {
                main.post(() -> callback.onBitmapReady(bitmap));
            } else {
                main.post(callback::onError);
            }
        }, "AftenpostenFetch").start();
    }

    @Nullable
    private static Bitmap fetchAndRender(int width, int height) {
        try {
            String html = fetchHtml();
            List<NewsItem> items = NewsFeedParser.parseSchibsted(html);
            if (items.isEmpty()) {
                Log.w(TAG, "No Aftenposten headlines found");
                return null;
            }
            int thumbSize = Math.round(width * NewsColumnRenderer.IMAGE_SIZE_RATIO);
            NewsColumnRenderer.loadItemImages(items, thumbSize, "https://www.aftenposten.no/");
            try {
                Bitmap bitmap = NewsColumnRenderer.render(
                        NewsColumnRenderer.Brand.AFTENPOSTEN, width, height, items);
                Log.d(TAG, "Aftenposten.no render ready " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return bitmap;
            } finally {
                NewsColumnRenderer.recycleItemImages(items);
            }
        } catch (Exception e) {
            Log.e(TAG, "Aftenposten.no fetch failed", e);
            return null;
        }
    }

    private static String fetchHtml() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(AP_URL);
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
                throw new IllegalStateException("Aftenposten.no HTTP " + code);
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
