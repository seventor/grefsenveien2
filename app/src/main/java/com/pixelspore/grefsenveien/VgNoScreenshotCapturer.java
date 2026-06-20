package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
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

final class VgNoScreenshotCapturer {

    private static final String TAG = "GrefsenveienApp";
    private static final String VG_URL = "https://www.vg.no";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int MAX_HEADLINES = 10;
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<h3 class=\"_title[^\"]*\"[^>]*>([^<]+)");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("<span data-nosnippet=\"true\">([^<]+)</span>");

    interface Callback {
        void onBitmapReady(@NonNull Bitmap bitmap);
        void onError();
    }

    private VgNoScreenshotCapturer() {}

    static void capture(@NonNull Context context, int widthPx, @NonNull Callback callback) {
        int width = Math.max(320, widthPx);
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                Bitmap bitmap = fetchAndRender(width);
                main.post(() -> callback.onBitmapReady(bitmap));
            } catch (Exception e) {
                Log.e(TAG, "VG.no fetch failed", e);
                main.post(callback::onError);
            }
        }, "VgNoFetch").start();
    }

    private static Bitmap fetchAndRender(int width) throws Exception {
        String html = fetchHtml();
        List<String> titles = parseMatches(html, TITLE_PATTERN);
        List<String> timestamps = parseMatches(html, TIMESTAMP_PATTERN);
        if (titles.isEmpty()) {
            throw new IllegalStateException("No VG.no headlines found");
        }

        int pad = Math.round(width * 0.06f);
        int headerH = Math.round(width * 0.11f);
        int sectionH = Math.round(width * 0.08f);
        int itemGap = Math.round(width * 0.03f);

        int bgColor = android.graphics.Color.parseColor("#F4F4F4");
        int vgRed = android.graphics.Color.parseColor("#E40000");
        int titleColor = android.graphics.Color.parseColor("#111111");
        int timeColor = android.graphics.Color.parseColor("#666666");

        TextPaint logoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        logoPaint.setColor(android.graphics.Color.WHITE);
        logoPaint.setTextSize(headerH * 0.45f);
        logoPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint sectionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sectionPaint.setColor(titleColor);
        sectionPaint.setTextSize(width * 0.055f);
        sectionPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(titleColor);
        titlePaint.setTextSize(width * 0.048f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setColor(timeColor);
        timePaint.setTextSize(width * 0.034f);

        int maxItems = Math.min(MAX_HEADLINES, titles.size());
        int contentWidth = width - pad * 2;
        List<StaticLayout> titleLayouts = new ArrayList<>();
        List<String> itemTimes = new ArrayList<>();
        int contentHeight = headerH + sectionH;

        for (int i = 0; i < maxItems; i++) {
            String title = decodeHtml(titles.get(i).trim());
            StaticLayout layout = buildLayout(title, titlePaint, contentWidth);
            titleLayouts.add(layout);
            itemTimes.add(i < timestamps.size() ? decodeHtml(timestamps.get(i).trim()) : "");
            contentHeight += layout.getHeight() + timePaint.getTextSize() + itemGap;
        }

        contentHeight += pad;
        Bitmap bitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bgColor);

        Paint headerBg = new Paint();
        headerBg.setColor(vgRed);
        canvas.drawRect(0, 0, width, headerH, headerBg);
        canvas.drawText("VG", pad, headerH * 0.68f, logoPaint);

        float y = headerH + sectionH * 0.75f;
        canvas.drawText("Siste nytt", pad, y, sectionPaint);
        y += sectionH * 0.55f;

        for (int i = 0; i < maxItems; i++) {
            StaticLayout layout = titleLayouts.get(i);
            canvas.save();
            canvas.translate(pad, y);
            layout.draw(canvas);
            canvas.restore();
            y += layout.getHeight() + 4f;

            String time = itemTimes.get(i);
            if (!time.isEmpty()) {
                canvas.drawText(time, pad, y + timePaint.getTextSize(), timePaint);
                y += timePaint.getTextSize();
            }
            y += itemGap;
        }

        Log.d(TAG, "VG.no render ready " + width + "x" + contentHeight + " (" + maxItems + " headlines)");
        return bitmap;
    }

    private static StaticLayout buildLayout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build();
    }

    private static String decodeHtml(String raw) {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
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
            URL url = new URL(VG_URL);
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
                throw new IllegalStateException("VG.no HTTP " + code);
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
