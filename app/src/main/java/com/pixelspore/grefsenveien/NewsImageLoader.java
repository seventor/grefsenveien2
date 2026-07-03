package com.pixelspore.grefsenveien;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

final class NewsImageLoader {

    private static final String TAG = "GrefsenveienApp";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private NewsImageLoader() {}

    @Nullable
    static Bitmap load(@Nullable String imageUrl, int maxDimensionPx, @Nullable String referer) {
        if (imageUrl == null || imageUrl.isEmpty() || maxDimensionPx <= 0) {
            return null;
        }
        String normalizedUrl = imageUrl.replace("&amp;", "&");
        HttpURLConnection connection = null;
        try {
            URL url = new URL(normalizedUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "nb-NO,nb;q=0.9,no;q=0.8");
            if (referer != null && !referer.isEmpty()) {
                connection.setRequestProperty("Referer", referer);
            }

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "News image HTTP " + code + ": " + normalizedUrl);
                return null;
            }

            InputStream stream = connection.getInputStream();
            String encoding = connection.getContentEncoding();
            if (encoding != null && encoding.contains("gzip")) {
                stream = new GZIPInputStream(stream);
            }

            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            byte[] data = buffer.toByteArray();
            if (data.length == 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (decoded == null) {
                Log.w(TAG, "News image decode failed: " + normalizedUrl);
                return null;
            }
            return downscaleKeepingAspect(decoded, maxDimensionPx);
        } catch (Exception e) {
            Log.w(TAG, "News image load failed: " + normalizedUrl, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Bitmap downscaleKeepingAspect(Bitmap bitmap, int maxDimensionPx) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxEdge = Math.max(width, height);
        if (maxEdge <= maxDimensionPx) {
            return bitmap;
        }
        float scale = maxDimensionPx / (float) maxEdge;
        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }
}
