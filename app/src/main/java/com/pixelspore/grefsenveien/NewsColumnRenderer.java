package com.pixelspore.grefsenveien;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

final class NewsColumnRenderer {

    static final float HEADER_HEIGHT_RATIO = 0.11f;
    static final float LOGO_TEXT_RATIO = 0.38f;
    static final float TITLE_TEXT_RATIO = 0.052f;
    static final float TIME_TEXT_RATIO = 0.040f;
    static final float CONTENT_TOP_PAD_RATIO = 0.02f;
    static final float ITEM_GAP_RATIO = 0.018f;
    static final float TITLE_TIME_GAP_RATIO = 0.014f;
    static final float TIME_BOTTOM_PAD_RATIO = 0.028f;
    static final float IMAGE_SIZE_RATIO = 0.22f;
    static final float IMAGE_TEXT_GAP_RATIO = 0.025f;
    static final float HORIZONTAL_PAD_RATIO = 0.06f;
    private static final int MAX_ITEMS_TO_CONSIDER = 60;

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

    static Bitmap render(@NonNull Brand brand, int width, int height, @NonNull List<NewsItem> items)
            throws IllegalStateException {
        if (items.isEmpty()) {
            throw new IllegalStateException("No headlines found for " + brand.label);
        }
        width = Math.max(280, width);
        height = Math.max(320, height);

        int pad = Math.round(width * HORIZONTAL_PAD_RATIO);
        int headerH = Math.round(width * HEADER_HEIGHT_RATIO);
        int contentTopPad = Math.round(width * CONTENT_TOP_PAD_RATIO);
        int itemGap = Math.round(width * ITEM_GAP_RATIO);
        int titleTimeGap = Math.round(width * TITLE_TIME_GAP_RATIO);
        int timeBottomPad = Math.round(width * TIME_BOTTOM_PAD_RATIO);
        int thumbSize = Math.round(width * IMAGE_SIZE_RATIO);
        int thumbGap = Math.round(width * IMAGE_TEXT_GAP_RATIO);
        int bottomPad = pad;

        int bgColor = android.graphics.Color.WHITE;
        int titleColor = android.graphics.Color.parseColor("#111111");
        int timeColor = android.graphics.Color.parseColor("#666666");
        int recentTimeColor = android.graphics.Color.parseColor("#E40000");
        int imagePlaceholder = android.graphics.Color.parseColor("#DDDDDD");
        int recentItemBgColor = android.graphics.Color.parseColor("#F8EDE4");
        int recentStripeColor = android.graphics.Color.parseColor("#E40000");
        int dividerColor = android.graphics.Color.parseColor("#E5E5E5");

        float logoSize = headerH * LOGO_TEXT_RATIO;
        float titleSize = width * TITLE_TEXT_RATIO;
        float timeSize = width * TIME_TEXT_RATIO;

        TextPaint logoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        logoPaint.setColor(android.graphics.Color.WHITE);
        logoPaint.setTextSize(logoSize);
        logoPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(titleColor);
        titlePaint.setTextSize(titleSize);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setColor(timeColor);
        timePaint.setTextSize(timeSize);

        int contentWidth = width - pad * 2;
        int textWidthWithImage = contentWidth - thumbSize - thumbGap;
        int availableForItems = height - headerH - contentTopPad - bottomPad;

        List<RenderedNewsItem> renderedItems = new ArrayList<>();
        int usedHeight = 0;
        int maxItems = Math.min(MAX_ITEMS_TO_CONSIDER, items.size());

        for (int i = 0; i < maxItems; i++) {
            NewsItem item = items.get(i);
            boolean hasImage = item.image != null && !item.image.isRecycled();
            int textWidth = hasImage ? textWidthWithImage : contentWidth;
            StaticLayout layout = buildLayout(item.title, titlePaint, textWidth);
            int textBlockH = layout.getHeight() + titleTimeGap + Math.round(timeSize) + timeBottomPad;
            int blockH = hasImage ? Math.max(thumbSize, textBlockH) : textBlockH;
            int totalItemH = blockH + itemGap;

            if (!renderedItems.isEmpty() && usedHeight + totalItemH > availableForItems) {
                break;
            }
            renderedItems.add(new RenderedNewsItem(item, layout, hasImage, blockH));
            usedHeight += totalItemH;
        }

        if (renderedItems.isEmpty()) {
            NewsItem item = items.get(0);
            boolean hasImage = item.image != null && !item.image.isRecycled();
            int textWidth = hasImage ? textWidthWithImage : contentWidth;
            StaticLayout layout = buildLayout(item.title, titlePaint, textWidth);
            int textBlockH = layout.getHeight() + titleTimeGap + Math.round(timeSize) + timeBottomPad;
            int blockH = hasImage ? Math.max(thumbSize, textBlockH) : textBlockH;
            renderedItems.add(new RenderedNewsItem(item, layout, hasImage, blockH));
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bgColor);

        Paint headerBg = new Paint();
        headerBg.setColor(brand.headerColor);
        canvas.drawRect(0, 0, width, headerH, headerBg);
        canvas.drawText(brand.label, pad, headerH * 0.68f, logoPaint);

        float y = headerH + contentTopPad;

        Paint imagePaint = new Paint();
        imagePaint.setFilterBitmap(true);
        imagePaint.setAntiAlias(true);
        Paint placeholderPaint = new Paint();
        placeholderPaint.setColor(imagePlaceholder);
        Paint recentItemBgPaint = new Paint();
        recentItemBgPaint.setColor(recentItemBgColor);
        Paint recentStripePaint = new Paint();
        recentStripePaint.setColor(recentStripeColor);
        Paint dividerPaint = new Paint();
        dividerPaint.setColor(dividerColor);
        dividerPaint.setStrokeWidth(1f);

        for (int i = 0; i < renderedItems.size(); i++) {
            RenderedNewsItem rendered = renderedItems.get(i);
            float blockTop = y;
            boolean isRecent = NewsTimestampParser.isPublishedWithinLast30Minutes(rendered.item.publishedMs);
            float textLeft = pad;

            if (isRecent) {
                canvas.drawRect(0f, blockTop, width, blockTop + rendered.blockH, recentItemBgPaint);
                canvas.drawRect(0f, blockTop, 4f, blockTop + rendered.blockH, recentStripePaint);
            }

            if (rendered.hasImage) {
                android.graphics.RectF imageRect = new android.graphics.RectF(
                        pad, blockTop, pad + thumbSize, blockTop + thumbSize);
                canvas.drawRect(imageRect, placeholderPaint);
                drawCenterCroppedBitmap(canvas, rendered.item.image, imageRect, imagePaint);
                textLeft = pad + thumbSize + thumbGap;
            }

            canvas.save();
            canvas.translate(textLeft, blockTop);
            rendered.layout.draw(canvas);
            canvas.restore();

            float timeY = blockTop + rendered.layout.getHeight() + titleTimeGap + timeSize;
            String displayTime = NewsTimestampParser.formatClockTime(
                    rendered.item.publishedMs, rendered.item.timestamp);
            if (!displayTime.isEmpty()) {
                timePaint.setColor(isRecent ? recentTimeColor : timeColor);
                canvas.drawText(displayTime, textLeft, timeY, timePaint);
            }

            y += rendered.blockH;
            if (i < renderedItems.size() - 1) {
                canvas.drawLine(0f, y, width, y, dividerPaint);
            }
            y += itemGap;
        }

        return bitmap;
    }

    static void loadItemImages(@NonNull List<NewsItem> items, int thumbSizePx, @Nullable String referer) {
        int max = Math.min(MAX_ITEMS_TO_CONSIDER, items.size());
        for (int i = 0; i < max; i++) {
            NewsItem item = items.get(i);
            if (item.imageUrl == null) {
                continue;
            }
            item.image = NewsImageLoader.load(item.imageUrl, thumbSizePx, referer);
        }
    }

    private static void drawCenterCroppedBitmap(Canvas canvas, Bitmap bitmap,
            android.graphics.RectF dst, Paint paint) {
        int bmpW = bitmap.getWidth();
        int bmpH = bitmap.getHeight();
        if (bmpW <= 0 || bmpH <= 0 || dst.width() <= 0f || dst.height() <= 0f) {
            return;
        }

        float dstRatio = dst.width() / dst.height();
        float srcRatio = bmpW / (float) bmpH;
        android.graphics.Rect src;
        if (srcRatio > dstRatio) {
            int srcH = bmpH;
            int srcW = Math.round(bmpH * dstRatio);
            int left = (bmpW - srcW) / 2;
            src = new android.graphics.Rect(left, 0, left + srcW, srcH);
        } else {
            int srcW = bmpW;
            int srcH = Math.round(bmpW / dstRatio);
            int top = (bmpH - srcH) / 2;
            src = new android.graphics.Rect(0, top, srcW, top + srcH);
        }
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    static void recycleItemImages(@NonNull List<NewsItem> items) {
        for (NewsItem item : items) {
            if (item.image != null && !item.image.isRecycled()) {
                item.image.recycle();
            }
            item.image = null;
        }
    }

    private static StaticLayout buildLayout(String text, TextPaint paint, int width) {
        String safeText = text == null ? "" : text;
        return StaticLayout.Builder.obtain(safeText, 0, safeText.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.06f)
                .setIncludePad(false)
                .build();
    }

    private static final class RenderedNewsItem {
        final NewsItem item;
        final StaticLayout layout;
        final boolean hasImage;
        final int blockH;

        RenderedNewsItem(NewsItem item, StaticLayout layout, boolean hasImage, int blockH) {
            this.item = item;
            this.layout = layout;
            this.hasImage = hasImage;
            this.blockH = blockH;
        }
    }
}
