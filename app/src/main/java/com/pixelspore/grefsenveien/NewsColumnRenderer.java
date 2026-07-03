package com.pixelspore.grefsenveien;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class NewsColumnRenderer {

    static final float HEADER_HEIGHT_RATIO = 0.11f;
    static final float LOGO_TEXT_RATIO = 0.38f;
    static final float TITLE_TEXT_RATIO = 0.042f;
    static final float TIME_TEXT_RATIO = 0.032f;
    static final float SECTION_HEIGHT_RATIO = 0.055f;
    static final float ITEM_GAP_RATIO = 0.018f;
    static final float HORIZONTAL_PAD_RATIO = 0.06f;
    private static final int MAX_HEADLINES = 40;

    enum Brand {
        VG("VG", "#E40000"),
        AFTENPOSTEN("Aftenposten", "#002E5D"),
        NRK("NRK", "#0068CE");

        final String label;
        final int headerColor;

        Brand(String label, String colorHex) {
            this.label = label;
            this.headerColor = android.graphics.Color.parseColor(colorHex);
        }
    }

    private NewsColumnRenderer() {}

    static Bitmap render(@NonNull Brand brand, int width, int height,
            @NonNull List<String> titles, @NonNull List<String> timestamps) throws IllegalStateException {
        if (titles.isEmpty()) {
            throw new IllegalStateException("No headlines found for " + brand.label);
        }
        width = Math.max(280, width);
        height = Math.max(320, height);

        int pad = Math.round(width * HORIZONTAL_PAD_RATIO);
        int headerH = Math.round(width * HEADER_HEIGHT_RATIO);
        int sectionH = Math.round(width * SECTION_HEIGHT_RATIO);
        int itemGap = Math.max(4, Math.round(width * ITEM_GAP_RATIO));
        int bottomPad = pad;

        int bgColor = android.graphics.Color.parseColor("#F4F4F4");
        int titleColor = android.graphics.Color.parseColor("#111111");
        int timeColor = android.graphics.Color.parseColor("#666666");

        float logoSize = headerH * LOGO_TEXT_RATIO;
        float titleSize = width * TITLE_TEXT_RATIO;
        float timeSize = width * TIME_TEXT_RATIO;

        TextPaint logoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        logoPaint.setColor(android.graphics.Color.WHITE);
        logoPaint.setTextSize(logoSize);
        logoPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint sectionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sectionPaint.setColor(titleColor);
        sectionPaint.setTextSize(titleSize);
        sectionPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(titleColor);
        titlePaint.setTextSize(titleSize);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setColor(timeColor);
        timePaint.setTextSize(timeSize);

        int contentWidth = width - pad * 2;
        int availableForItems = height - headerH - sectionH - bottomPad;

        List<StaticLayout> titleLayouts = new ArrayList<>();
        List<String> itemTimes = new ArrayList<>();
        int usedHeight = 0;
        int maxItems = Math.min(MAX_HEADLINES, titles.size());

        for (int i = 0; i < maxItems; i++) {
            String title = decodeHtml(titles.get(i).trim());
            int maxLines = titleLayouts.isEmpty() ? 3 : 2;
            StaticLayout layout = buildLayout(title, titlePaint, contentWidth, maxLines);
            int itemH = layout.getHeight() + 4 + Math.round(timeSize) + itemGap;
            if (!titleLayouts.isEmpty() && usedHeight + itemH > availableForItems) {
                break;
            }
            titleLayouts.add(layout);
            itemTimes.add(i < timestamps.size() ? decodeHtml(timestamps.get(i).trim()) : "");
            usedHeight += itemH;
        }

        if (titleLayouts.isEmpty()) {
            String title = decodeHtml(titles.get(0).trim());
            titleLayouts.add(buildLayout(title, titlePaint, contentWidth, 1));
            itemTimes.add(timestamps.isEmpty() ? "" : decodeHtml(timestamps.get(0).trim()));
            usedHeight = titleLayouts.get(0).getHeight() + 4 + Math.round(timeSize) + itemGap;
        }

        float extraGapPerItem = 0f;
        if (titleLayouts.size() > 1 && usedHeight < availableForItems) {
            extraGapPerItem = (availableForItems - usedHeight) / (float) titleLayouts.size();
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bgColor);

        Paint headerBg = new Paint();
        headerBg.setColor(brand.headerColor);
        canvas.drawRect(0, 0, width, headerH, headerBg);
        canvas.drawText(brand.label, pad, headerH * 0.68f, logoPaint);

        float y = headerH + sectionH * 0.75f;
        canvas.drawText("Siste nytt", pad, y, sectionPaint);
        y += sectionH * 0.55f;

        for (int i = 0; i < titleLayouts.size(); i++) {
            StaticLayout layout = titleLayouts.get(i);
            canvas.save();
            canvas.translate(pad, y);
            layout.draw(canvas);
            canvas.restore();
            y += layout.getHeight() + 4f;

            String time = itemTimes.get(i);
            if (!time.isEmpty()) {
                canvas.drawText(time, pad, y + timeSize, timePaint);
                y += timeSize;
            }
            y += itemGap + extraGapPerItem;
        }

        return bitmap;
    }

    private static StaticLayout buildLayout(String text, TextPaint paint, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.08f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static String decodeHtml(String raw) {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
    }
}
