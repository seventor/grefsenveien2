package com.pixelspore.grefsenveien;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;

import java.util.List;

final class TourStandingsRenderer {

    private static final int TOP_COUNT = 10;
    private static final int TICK_INTERVAL_SEC = 15 * 60;
    private static final int HOUR_SEC = 60 * 60;
    private static final String BIKE_ICON = "\uD83D\uDEB4";

    private static final int COLOR_YELLOW = 0xFFFFD200;
    private static final int COLOR_INK = 0xFF161616;
    private static final int COLOR_NOR = 0xFFC0392B;
    private static final int COLOR_NOR_ROW_BG = 0x0FC8392B;
    private static final int COLOR_LINE = 0x14000000;

    private TourStandingsRenderer() {}

    static void draw(@NonNull Canvas canvas, @NonNull TourStandingsData data,
            float left, float top, float right, float bottom) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0f || height <= 0f) {
            return;
        }

        float scale = width / 1200f;
        float padX = 20f * scale;
        float titleBarH = 54f * scale;
        float colHeaderH = 40f * scale;
        float sectionHeaderH = 42f * scale;

        List<TourStandingsRider> topRiders = data.getTopRiders(TOP_COUNT);
        List<TourStandingsRider> extraNor = data.getNorwegiansOutsideTop(TOP_COUNT);
        int rowCount = topRiders.size() + extraNor.size();
        if (rowCount == 0) {
            return;
        }

        float rowAreaH = height - titleBarH - colHeaderH
                - (extraNor.isEmpty() ? 0f : sectionHeaderH);
        float rowH = Math.max(42f * scale, rowAreaH / rowCount);

        Paint bgPaint = new Paint();
        bgPaint.setColor(0xFFFFFFFF);
        canvas.drawRect(left, top, right, bottom, bgPaint);

        Paint titleBarPaint = new Paint();
        titleBarPaint.setColor(COLOR_YELLOW);
        canvas.drawRect(left, top, right, top + titleBarH, titleBarPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(COLOR_INK);
        titlePaint.setTextSize(30f * scale);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        String title = "Tour de France";
        if (data.stage > 0) {
            title += " · Etappe " + data.stage;
        }
        if (data.year > 0) {
            title += " " + data.year;
        }
        canvas.drawText(title, left + padX, top + titleBarH * 0.68f, titlePaint);

        float y = top + titleBarH;
        drawColumnHeader(canvas, left, y, right, y + colHeaderH, padX, scale);
        y += colHeaderH;

        int maxTopGap = maxGapSeconds(topRiders);
        for (TourStandingsRider rider : topRiders) {
            y = drawRiderRow(canvas, rider, left, y, right, rowH, padX, scale, maxTopGap, true);
        }

        if (!extraNor.isEmpty()) {
            Paint sectionPaint = new Paint();
            sectionPaint.setColor(0xFFF3F3F3);
            canvas.drawRect(left, y, right, y + sectionHeaderH, sectionPaint);
            Paint sectionText = new Paint(Paint.ANTI_ALIAS_FLAG);
            sectionText.setColor(COLOR_INK);
            sectionText.setTextSize(18f * scale);
            sectionText.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("NORSKE RYTTERE UTENFOR TOPP " + TOP_COUNT,
                    left + padX, y + sectionHeaderH * 0.68f, sectionText);
            y += sectionHeaderH;

            int maxExtraGap = maxGapSeconds(extraNor);
            for (TourStandingsRider rider : extraNor) {
                y = drawRiderRow(canvas, rider, left, y, right, rowH, padX, scale, maxExtraGap, true);
            }
        }
    }

    private static float drawRiderRow(Canvas canvas, TourStandingsRider rider,
            float left, float top, float right, float rowH, float padX, float scale,
            int maxGapSec, boolean drawGapBar) {
        float width = right - left;
        boolean isNor = rider.isNorwegian();

        Paint rowBg = new Paint();
        rowBg.setColor(isNor ? COLOR_NOR_ROW_BG : 0xFFFFFFFF);
        canvas.drawRect(left, top, right, top + rowH, rowBg);

        if (isNor) {
            Paint norStripe = new Paint();
            norStripe.setColor(COLOR_NOR);
            canvas.drawRect(left, top, left + 3f * scale, top + rowH, norStripe);
        }

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_LINE);
        canvas.drawLine(left, top + rowH, right, top + rowH, linePaint);

        int textColor = isNor ? COLOR_NOR : COLOR_INK;
        float rankW = width * 0.045f;
        float nameW = width * 0.17f;
        float bibW = width * 0.065f;
        float teamW = width * 0.24f;
        float natW = width * 0.055f;
        float timeW = width * 0.11f;
        float gapLeft = left + padX + rankW + nameW + bibW + teamW + natW + timeW;
        float gapRight = right - padX;

        float textY = top + rowH * 0.68f;
        float rankSize = 22f * scale;
        float bodySize = 19f * scale;
        float monoSize = 17f * scale;

        Paint rankPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rankPaint.setColor(textColor);
        rankPaint.setTextSize(rankSize);
        rankPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(String.valueOf(rider.rank), left + padX, textY, rankPaint);

        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(textColor);
        namePaint.setTextSize(bodySize);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);
        String name = rider.rider;
        if (isNor) {
            name += " \uD83C\uDDF3\uD83C\uDDF4";
        }
        canvas.drawText(truncate(namePaint, name, nameW - 4f * scale),
                left + padX + rankW, textY, namePaint);

        Paint bibPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bibPaint.setColor(COLOR_INK);
        bibPaint.setTextSize(monoSize);
        bibPaint.setTypeface(Typeface.MONOSPACE);
        canvas.drawText(rider.bib, left + padX + rankW + nameW, textY, bibPaint);

        Paint teamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        teamPaint.setColor(COLOR_INK);
        teamPaint.setTextSize(bodySize);
        canvas.drawText(truncate(teamPaint, rider.team, teamW - 4f * scale),
                left + padX + rankW + nameW + bibW, textY, teamPaint);

        Paint natPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        natPaint.setColor(COLOR_INK);
        natPaint.setTextSize(bodySize);
        canvas.drawText(rider.nationality,
                left + padX + rankW + nameW + bibW + teamW, textY, natPaint);

        Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setColor(textColor);
        timePaint.setTextSize(monoSize);
        timePaint.setTypeface(Typeface.MONOSPACE);
        String timeOrGap = rider.rank == 1 ? rider.time : rider.gap;
        canvas.drawText(timeOrGap,
                left + padX + rankW + nameW + bibW + teamW + natW, textY, timePaint);

        if (drawGapBar && gapRight > gapLeft + 20f * scale) {
            drawGapBar(canvas, rider, gapLeft, gapRight, top, rowH, scale, maxGapSec, isNor);
        }

        return top + rowH;
    }

    private static void drawColumnHeader(Canvas canvas, float left, float top, float right,
            float bottom, float padX, float scale) {
        Paint bg = new Paint();
        bg.setColor(0xFFF8F8F8);
        canvas.drawRect(left, top, right, bottom, bg);

        float width = right - left;
        float rankW = width * 0.045f;
        float nameW = width * 0.17f;
        float bibW = width * 0.065f;
        float teamW = width * 0.24f;
        float natW = width * 0.055f;
        float timeW = width * 0.11f;

        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(COLOR_INK);
        headerPaint.setTextSize(16f * scale);
        headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
        float textY = top + (bottom - top) * 0.72f;

        float x = left + padX;
        canvas.drawText("#", x, textY, headerPaint);
        x += rankW;
        canvas.drawText("RYTTER", x, textY, headerPaint);
        x += nameW;
        canvas.drawText("STARTNR", x, textY, headerPaint);
        x += bibW;
        canvas.drawText("LAG", x, textY, headerPaint);
        x += teamW;
        canvas.drawText("NAT", x, textY, headerPaint);
        x += natW;
        canvas.drawText("TID / GAP", x, textY, headerPaint);
        canvas.drawText("AVSTAND TIL LEDER",
                left + padX + rankW + nameW + bibW + teamW + natW + timeW, textY, headerPaint);

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_LINE);
        canvas.drawLine(left, bottom, right, bottom, linePaint);
    }

    private static void drawGapBar(Canvas canvas, TourStandingsRider rider,
            float barLeft, float barRight, float rowTop, float rowH, float scale,
            int maxGapSec, boolean isNor) {
        float lineY = rowTop + rowH * 0.55f;
        float barW = barRight - barLeft;

        Paint trackPaint = new Paint();
        trackPaint.setColor(0x1A000000);
        trackPaint.setStrokeWidth(Math.max(1.5f, 2f * scale));
        canvas.drawLine(barLeft, lineY, barRight, lineY, trackPaint);

        Paint startTick = new Paint();
        startTick.setColor(0x4D000000);
        canvas.drawRect(barLeft, lineY - 4f * scale, barLeft + 2f * scale, lineY + 4f * scale, startTick);

        drawGapTicks(canvas, barLeft, barRight, lineY, maxGapSec, scale);

        int gapSec = gapToSeconds(rider.gap);
        float rawPct = maxGapSec > 0 ? Math.min(100f, (gapSec * 100f) / maxGapSec) : 0f;
        float pct = rawPct == 0f ? 0f : Math.max(3f, Math.min(97f, rawPct));
        float bikeX = barLeft + barW * (pct / 100f);
        float bikeY = lineY;
        float radius = 14f * scale;

        Paint bikeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bikeBg.setColor(isNor ? 0x2EC0392B : 0x40FFD200);
        canvas.drawCircle(bikeX, bikeY, radius, bikeBg);

        Paint bikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bikePaint.setTextSize(20f * scale);
        float iconW = bikePaint.measureText(BIKE_ICON);
        Paint.FontMetrics fm = bikePaint.getFontMetrics();
        canvas.drawText(BIKE_ICON, bikeX - iconW / 2f, bikeY - (fm.ascent + fm.descent) / 2f, bikePaint);
    }

    private static void drawGapTicks(Canvas canvas, float barLeft, float barRight, float lineY,
            int maxGapSec, float scale) {
        if (maxGapSec <= TICK_INTERVAL_SEC) {
            return;
        }
        float barW = barRight - barLeft;
        Paint smallTick = new Paint();
        smallTick.setColor(0x38000000);
        Paint hourTick = new Paint();
        hourTick.setColor(0x66000000);

        for (int sec = TICK_INTERVAL_SEC; sec < maxGapSec; sec += TICK_INTERVAL_SEC) {
            float pct = (sec * 100f) / maxGapSec;
            float x = barLeft + barW * (pct / 100f);
            if (sec % HOUR_SEC == 0) {
                canvas.drawRect(x - 1f * scale, lineY - 6f * scale,
                        x + 1f * scale, lineY + 6f * scale, hourTick);
            } else {
                canvas.drawRect(x - 0.5f * scale, lineY - 3f * scale,
                        x + 0.5f * scale, lineY + 3f * scale, smallTick);
            }
        }
    }

    private static int maxGapSeconds(List<TourStandingsRider> riders) {
        int max = 1;
        for (TourStandingsRider rider : riders) {
            max = Math.max(max, gapToSeconds(rider.gap));
        }
        return max;
    }

    static int gapToSeconds(String gap) {
        if (gap == null || gap.isEmpty() || "-".equals(gap) || "\u2014".equals(gap)
                || "00:00:00".equals(gap)) {
            return 0;
        }
        String cleaned = gap.startsWith("+") ? gap.substring(1) : gap;
        String[] parts = cleaned.split(":");
        if (parts.length != 3) {
            return 0;
        }
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            int seconds = Integer.parseInt(parts[2].trim());
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String truncate(Paint paint, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        for (int i = text.length() - 1; i > 0; i--) {
            String candidate = text.substring(0, i) + ellipsis;
            if (paint.measureText(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }
}
