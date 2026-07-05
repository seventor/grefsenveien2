package com.pixelspore.grefsenveien;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class TourLiveRenderer {

    private static final int TOP_VIRTUAL = 10;
    private static final float LEFT_PANEL_FRACTION = 0.34f;
    private static final String BIKE_ICON = "\uD83D\uDEB4";
    private static final int COLOR_YELLOW = 0xFFFFD200;
    private static final int COLOR_INK = 0xFF161616;
    private static final int COLOR_NOR = 0xFFC0392B;
    private static final int COLOR_NOR_ROW_BG = 0x0FC8392B;
    private static final int COLOR_PANEL_RIGHT = 0xFFF3F3F3;
    private static final int COLOR_GROUP_LEAD = 0xFFF8EDE4;
    private static final int COLOR_LINE = 0x14000000;
    private static final int COLOR_CARD_BORDER = 0xFFE2E2E2;
    private static final int COLOR_MUTED = 0xFF888888;
    private static final String[] JERSEY_DISPLAY_ORDER = {"GUL", "HVIT", "POL", "GRO"};

    static void draw(@NonNull Canvas canvas, @NonNull TourLiveData data,
            float left, float top, float right, float bottom) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0f || height <= 0f) {
            return;
        }

        float scale = width / 1200f;
        float splitX = left + width * LEFT_PANEL_FRACTION;
        float padX = 20f * scale;

        canvas.drawColor(0xFFFFFFFF);
        drawLeftPanel(canvas, data, left, top, splitX, bottom, padX, scale);
        drawRightPanel(canvas, data, splitX, top, right, bottom, padX, scale);
    }

    private static void drawLeftPanel(Canvas canvas, TourLiveData data,
            float left, float top, float right, float bottom, float padX, float scale) {
        canvas.drawRect(left, top, right, bottom, paintColor(0xFFFFFFFF));

        float contentLeft = left + padX;
        float contentRight = right - padX;
        float y = top + padX;

        y = drawStageProgressCard(canvas, data, contentLeft, y, contentRight, scale) + padX;

        float jerseysTop = bottom - padX - estimateJerseysHeight(data.jerseys.size(), scale);
        float virtualBottom = jerseysTop - padX * 0.75f;

        drawVirtualStandings(canvas, data, contentLeft, y, contentRight, virtualBottom, padX, scale);
        drawJerseys(canvas, data, contentLeft, jerseysTop, contentRight, bottom - padX, padX, scale);
    }

    private static float drawStageProgressCard(Canvas canvas, TourLiveData data,
            float left, float top, float right, float scale) {
        float innerPad = 20f * scale;
        float cardH = 136f * scale;
        float radius = 12f * scale;
        RectF card = new RectF(left, top, right, top + cardH);
        drawRoundedRect(canvas, card, radius, 0xFFFFFFFF, COLOR_CARD_BORDER, 1.5f * scale);

        float x = left + innerPad;
        float innerRight = right - innerPad;
        float innerW = innerRight - x;

        Paint label = textPaint(COLOR_MUTED, 14f * scale, false);
        canvas.drawText("Igjen av etappen", x, top + innerPad + 16f * scale, label);

        String kmText = data.kmLeft > 0 ? data.kmLeft + " km" : "— km";
        Paint big = textPaint(COLOR_INK, 48f * scale, true);
        canvas.drawText(kmText, x, top + innerPad + 62f * scale, big);

        float barTop = top + innerPad + 72f * scale;
        float barH = 10f * scale;
        float barRadius = 5f * scale;
        RectF trackRect = new RectF(x, barTop, innerRight, barTop + barH);
        drawRoundedRect(canvas, trackRect, barRadius, 0xFFE8E8E8, 0, 0f);
        float fillW = innerW * Math.max(0f, Math.min(100f, data.progressPct)) / 100f;
        if (fillW > 0f) {
            RectF fillRect = new RectF(x, barTop, x + fillW, barTop + barH);
            drawRoundedRect(canvas, fillRect, barRadius, COLOR_YELLOW, 0, 0f);
        }

        float statsY = barTop + barH + 24f * scale;
        float monoSize = 17f * scale;
        String speed = data.avgSpeedKmh.isEmpty() ? "—" : data.avgSpeedKmh;
        String finish = data.estimatedFinishLocal.isEmpty() ? "—" : data.estimatedFinishLocal;
        drawStatLine(canvas, x, statsY, "Snitt ", speed, " km/t", monoSize, scale);
        String finishPrefix = "Est. mål ";
        Paint finishRegular = textPaint(COLOR_MUTED, monoSize, false);
        Paint finishBold = textPaint(COLOR_INK, monoSize, true);
        float finishW = finishRegular.measureText(finishPrefix) + finishBold.measureText(finish);
        drawStatLine(canvas, innerRight - finishW, statsY, finishPrefix, finish, "", monoSize, scale);

        return top + cardH;
    }

    private static void drawStatLine(Canvas canvas, float x, float y,
            String prefix, String value, String suffix, float monoSize, float scale) {
        Paint regular = textPaint(COLOR_MUTED, monoSize, false);
        Paint bold = textPaint(COLOR_INK, monoSize, true);
        canvas.drawText(prefix, x, y, regular);
        float offset = x + regular.measureText(prefix);
        canvas.drawText(value, offset, y, bold);
        offset += bold.measureText(value);
        if (!suffix.isEmpty()) {
            canvas.drawText(suffix, offset, y, regular);
        }
    }

    private static void drawVirtualStandings(Canvas canvas, TourLiveData data,
            float left, float top, float right, float maxBottom, float padX, float scale) {
        float width = right - left;
        float sectionSize = 18f * scale;
        float headerSize = 16f * scale;
        float rankSize = 22f * scale;
        float bodySize = 19f * scale;
        float monoSize = 17f * scale;
        float rowH = 42f * scale;
        float colHeaderH = 40f * scale;

        Paint sectionPaint = textPaint(COLOR_INK, sectionSize, true);
        canvas.drawText("VIRTUELT SAMMENLAGT · TOPP " + TOP_VIRTUAL,
                left, top + sectionSize * 0.85f, sectionPaint);

        float y = top + sectionSize + 8f * scale;
        float rankW = width * 0.11f;
        float nameW = width * 0.44f;
        float yestW = width * 0.22f;

        Paint headerBg = paintColor(0xFFF8F8F8);
        canvas.drawRect(left, y, right, y + colHeaderH, headerBg);
        Paint colHdr = textPaint(COLOR_INK, headerSize, true);
        float hdrY = y + colHeaderH * 0.72f;
        canvas.drawText("#", left + padX * 0.5f, hdrY, colHdr);
        canvas.drawText("Rytter", left + rankW, hdrY, colHdr);
        float yestHdrRight = left + rankW + nameW + yestW;
        canvas.drawText("Gårs", yestHdrRight - colHdr.measureText("Gårs"), hdrY, colHdr);
        canvas.drawText("Bak", right - padX * 0.5f - colHdr.measureText("Bak"), hdrY, colHdr);

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(1f);
        canvas.drawLine(left, y + colHeaderH, right, y + colHeaderH, linePaint);
        y += colHeaderH;

        List<TourLiveVirtualRider> riders = data.getTopVirtual(TOP_VIRTUAL);
        for (TourLiveVirtualRider rider : riders) {
            if (y + rowH > maxBottom) {
                break;
            }
            drawVirtualRow(canvas, rider, left, y, right, rowH, padX, scale,
                    rankW, nameW, yestW, rankSize, bodySize, monoSize);
            y += rowH;
        }
    }

    private static void drawVirtualRow(Canvas canvas, TourLiveVirtualRider rider,
            float left, float top, float right, float rowH, float padX, float scale,
            float rankW, float nameW, float yestW,
            float rankSize, float bodySize, float monoSize) {
        boolean isNor = rider.isNorwegian;

        Paint rowBg = paintColor(isNor ? COLOR_NOR_ROW_BG : 0xFFFFFFFF);
        canvas.drawRect(left, top, right, top + rowH, rowBg);
        if (isNor) {
            Paint norStripe = paintColor(COLOR_NOR);
            canvas.drawRect(left, top, left + 3f * scale, top + rowH, norStripe);
        }

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(1f);
        canvas.drawLine(left, top + rowH, right, top + rowH, linePaint);

        int textColor = isNor ? COLOR_NOR : COLOR_INK;
        float textY = top + rowH * 0.68f;

        Paint rankPaint = textPaint(textColor, rankSize, true);
        canvas.drawText(String.valueOf(rider.virtualRank), left + padX * 0.5f, textY, rankPaint);

        Paint namePaint = textPaint(textColor, bodySize, true);
        String name = rider.rider;
        if (rider.telemetryEstimated) {
            name += " (est.)";
        }
        if (isNor) {
            name += " \uD83C\uDDF3\uD83C\uDDF4";
        }
        canvas.drawText(name, left + rankW, textY, namePaint);

        Paint yestPaint = textPaint(COLOR_MUTED, monoSize, true);
        yestPaint.setTypeface(Typeface.MONOSPACE);
        String yestText = formatTimeCell(rider.yesterdayGap);
        float yestRight = left + rankW + nameW + yestW;
        canvas.drawText(yestText, yestRight - yestPaint.measureText(yestText), textY, yestPaint);

        Paint gapPaint = textPaint(textColor, monoSize, true);
        gapPaint.setTypeface(Typeface.MONOSPACE);
        String gapText = formatTimeCell(rider.virtualGap);
        canvas.drawText(gapText, right - padX * 0.5f - gapPaint.measureText(gapText), textY, gapPaint);
    }

    private static float estimateJerseysHeight(int jerseyCount, float scale) {
        float sectionSize = 18f * scale;
        float rowH = 42f * scale;
        float gap = 8f * scale;
        int rows = Math.max(0, jerseyCount);
        if (rows == 0) {
            return sectionSize + 8f * scale;
        }
        return sectionSize + 8f * scale + rows * rowH + (rows - 1) * gap;
    }

    private static void drawJerseys(Canvas canvas, TourLiveData data,
            float left, float top, float right, float bottom, float padX, float scale) {
        float bodySize = 19f * scale;
        float monoSize = 17f * scale;
        float sectionSize = 18f * scale;
        float rowH = 42f * scale;
        float gap = 8f * scale;
        float radius = 12f * scale;
        float innerPad = 16f * scale;

        Paint header = textPaint(COLOR_INK, sectionSize, true);
        canvas.drawText("TRØYER", left, top + sectionSize * 0.85f, header);

        float y = top + sectionSize + 8f * scale;
        for (TourLiveJersey jersey : data.jerseys) {
            if (y + rowH > bottom) {
                break;
            }
            RectF row = new RectF(left, y, right, y + rowH);
            drawRoundedRect(canvas, row, radius, 0xFFFFFFFF, COLOR_CARD_BORDER, 1.5f * scale);

            float dotX = left + innerPad + 8f * scale;
            float dotY = y + rowH * 0.5f;
            drawJerseyDot(canvas, dotX, dotY, jersey.code, 8f * scale);

            float textY = y + rowH * 0.68f;
            Paint titlePaint = textPaint(COLOR_INK, bodySize, false);
            canvas.drawText(jersey.title, left + innerPad + 28f * scale, textY, titlePaint);

            String rider = jersey.rider;
            if (jersey.telemetryEstimated) {
                rider += " (est.)";
            }
            String groupPart = " · gruppe " + jersey.groupNumber;
            Paint riderPaint = textPaint(COLOR_INK, bodySize, true);
            Paint groupPaint = textPaint(COLOR_MUTED, monoSize, false);
            float rightEdge = right - innerPad;
            float groupW = groupPaint.measureText(groupPart);
            float riderW = riderPaint.measureText(rider);
            canvas.drawText(groupPart, rightEdge - groupW, textY, groupPaint);
            canvas.drawText(rider, rightEdge - groupW - riderW, textY, riderPaint);

            y += rowH + gap;
        }
    }

    private static void drawRightPanel(Canvas canvas, TourLiveData data,
            float left, float top, float right, float bottom, float padX, float scale) {
        canvas.drawRect(left, top, right, bottom, paintColor(COLOR_PANEL_RIGHT));

        float contentLeft = left + padX;
        float contentRight = right - padX;
        float contentWidth = contentRight - contentLeft;

        float headerSize = 16f * scale;
        Paint header = textPaint(COLOR_INK, headerSize, true);
        float headerY = top + padX + headerSize * 0.85f;
        canvas.drawText("FELTET NÅ · TID MÅLT FRA ETAPPELEDEREN", contentLeft, headerY, header);

        float trackTop = headerY + 10f * scale;
        float trackH = 56f * scale;
        drawFieldTrack(canvas, data, contentLeft, trackTop, contentRight, trackTop + trackH, scale);

        float y = trackTop + trackH + 10f * scale;
        float trackBottom = bottom - padX;
        for (int i = 0; i < data.fieldGroups.size(); i++) {
            TourLiveFieldGroup group = data.fieldGroups.get(i);
            if (i > 0 && group.gapToPreviousGroupText != null) {
                y = drawGapBetween(canvas, group.gapToPreviousGroupText,
                        contentLeft, y, contentRight, scale) + 6f * scale;
            }
            boolean highlight = !group.jerseyCodes.isEmpty();
            float cardH = estimateGroupCardHeight(group, contentWidth, scale);
            if (y + cardH > trackBottom) {
                break;
            }
            y = drawGroupCard(canvas, group, contentLeft, y, contentRight, highlight, scale) + 8f * scale;
        }
    }

    private static void drawFieldTrack(Canvas canvas, TourLiveData data,
            float left, float top, float right, float bottom, float scale) {
        float lineY = top + (bottom - top) * 0.62f;
        Paint trackLine = new Paint();
        trackLine.setColor(0xFFCCCCCC);
        trackLine.setStrokeWidth(Math.max(1f, 1.5f * scale));
        canvas.drawLine(left, lineY, right, lineY, trackLine);

        float width = right - left;
        Paint gapPaint = textPaint(COLOR_INK, 14f * scale, true);
        gapPaint.setTypeface(Typeface.MONOSPACE);
        for (TourLiveTrackGap gap : data.trackGaps) {
            float x = left + width * (gap.midPct / 100f);
            String text = formatRelativeGap(gap.gapText);
            canvas.drawText(text, x - gapPaint.measureText(text) / 2f, lineY - 12f * scale, gapPaint);
        }

        Paint numPaint = textPaint(COLOR_INK, 12f * scale, true);
        Paint bikePaint = textPaint(COLOR_INK, 16f * scale, false);
        for (TourLiveTrackGroup group : data.trackGroups) {
            float x = left + width * (group.positionPct / 100f);
            String num = String.valueOf(group.number);
            canvas.drawText(num, x - numPaint.measureText(num) / 2f, lineY - 26f * scale, numPaint);
            float iconW = bikePaint.measureText(BIKE_ICON);
            canvas.drawText(BIKE_ICON, x - iconW / 2f, lineY + 14f * scale, bikePaint);
        }
    }

    private static float drawGapBetween(Canvas canvas, String gapText,
            float left, float top, float right, float scale) {
        float h = 36f * scale;
        Paint arrow = textPaint(COLOR_MUTED, 14f * scale, false);
        Paint gap = textPaint(COLOR_INK, 15f * scale, true);
        gap.setTypeface(Typeface.MONOSPACE);
        float centerX = (left + right) / 2f;
        String text = formatRelativeGap(gapText);
        canvas.drawText("\u2193", centerX - 4f * scale, top + 12f * scale, arrow);
        canvas.drawText(text, centerX - gap.measureText(text) / 2f, top + 28f * scale, gap);
        return top + h;
    }

    private static float drawGroupCard(Canvas canvas, TourLiveFieldGroup group,
            float left, float top, float right, boolean highlight, float scale) {
        float width = right - left;
        float pad = 16f * scale;
        float radius = 12f * scale;
        float cardH = estimateGroupCardHeight(group, width, scale);
        RectF rect = new RectF(left, top, right, top + cardH);

        RectF shadow = new RectF(rect);
        shadow.offset(0f, 2f * scale);
        drawRoundedRect(canvas, shadow, radius, 0x12000000, 0, 0f);
        drawRoundedRect(canvas, rect, radius, COLOR_GROUP_LEAD,
                highlight ? COLOR_YELLOW : COLOR_CARD_BORDER,
                highlight ? 2.5f * scale : 1f * scale);

        float headerY = top + pad + 20f * scale;
        Paint numPaint = textPaint(COLOR_INK, 20f * scale, true);
        canvas.drawText(String.valueOf(group.number), left + pad, headerY, numPaint);

        float dotX = left + pad + numPaint.measureText(String.valueOf(group.number)) + 12f * scale;
        if (!group.jerseyCodes.isEmpty()) {
            float dotY = top + pad + 14f * scale;
            for (String code : sortJerseyCodes(group.jerseyCodes)) {
                drawJerseyDot(canvas, dotX, dotY, code, 7f * scale);
                dotX += 18f * scale;
            }
        }

        String gapDisplay = formatGroupGap(group.gapText);
        Paint gapPaint = textPaint(COLOR_INK, 17f * scale, true);
        gapPaint.setTypeface(Typeface.MONOSPACE);
        float gapW = gapPaint.measureText(gapDisplay);
        canvas.drawText(gapDisplay, right - pad - gapW, headerY, gapPaint);

        String countText = group.riderCount + " ryttere";
        Paint countPaint = textPaint(COLOR_INK, 17f * scale, false);
        float countW = countPaint.measureText(countText);
        canvas.drawText(countText, right - pad - gapW - 14f * scale - countW, headerY, countPaint);

        float namesTop = top + pad + 36f * scale;
        drawWrappedNames(canvas, group, left, namesTop, right, pad, scale);

        return top + cardH;
    }

    private static void drawWrappedNames(Canvas canvas, TourLiveFieldGroup group,
            float left, float top, float right, float pad, float scale) {
        float nameSize = 15f * scale;
        float nameGap = 12f * scale;
        float lineH = 22f * scale;
        float x = left + pad;
        float y = top + lineH * 0.75f;
        float maxX = right - pad;

        Paint namePaint = textPaint(COLOR_INK, nameSize, true);
        for (TourLiveDisplayName name : group.displayNames) {
            namePaint.setColor(name.isNorwegian ? COLOR_NOR : COLOR_INK);
            String text = name.rider;
            if (name.isNorwegian) {
                text += " \uD83C\uDDF3\uD83C\uDDF4";
            }
            float textW = namePaint.measureText(text) + nameGap;
            if (x + textW > maxX && x > left + pad) {
                x = left + pad;
                y += lineH;
            }
            canvas.drawText(text, x, y, namePaint);
            x += textW;
        }

        int others = group.riderCount - group.displayNames.size();
        if (others > 0) {
            String othersText = "+" + others + " andre";
            Paint otherPaint = textPaint(COLOR_INK, nameSize, true);
            float othersW = otherPaint.measureText(othersText) + nameGap;
            if (x + othersW > maxX && x > left + pad) {
                x = left + pad;
                y += lineH;
            }
            canvas.drawText(othersText, x, y, otherPaint);
        }
    }

    private static float estimateGroupCardHeight(TourLiveFieldGroup group, float width, float scale) {
        float pad = 16f * scale;
        float headerH = 36f * scale;
        float nameSize = 15f * scale;
        float nameGap = 12f * scale;
        float lineH = 22f * scale;
        float contentWidth = width - pad * 2f;

        Paint namePaint = textPaint(COLOR_INK, nameSize, true);
        float x = 0f;
        int rows = 1;
        for (TourLiveDisplayName name : group.displayNames) {
            String text = name.rider;
            if (name.isNorwegian) {
                text += " \uD83C\uDDF3\uD83C\uDDF4";
            }
            float textW = namePaint.measureText(text) + nameGap;
            if (x + textW > contentWidth && x > 0f) {
                x = 0f;
                rows++;
            }
            x += textW;
        }

        int others = group.riderCount - group.displayNames.size();
        if (others > 0) {
            String othersText = "+" + others + " andre";
            float othersW = namePaint.measureText(othersText) + nameGap;
            if (x + othersW > contentWidth && x > 0f) {
                rows++;
            }
        }

        return pad + headerH + rows * lineH + pad;
    }

    @NonNull
    private static List<String> sortJerseyCodes(@NonNull List<String> codes) {
        List<String> sorted = new ArrayList<>();
        for (String order : JERSEY_DISPLAY_ORDER) {
            if (codes.contains(order)) {
                sorted.add(order);
            }
        }
        for (String code : codes) {
            if (!sorted.contains(code)) {
                sorted.add(code);
            }
        }
        return sorted;
    }

    private static String formatRelativeGap(String gap) {
        if (gap == null || gap.isEmpty()) {
            return "";
        }
        return gap.startsWith("+") ? gap : "+" + gap;
    }

    private static String formatGroupGap(String gap) {
        if (gap == null || gap.isEmpty() || "\u2014".equals(gap) || "-".equals(gap)) {
            return "\u2014";
        }
        return gap.startsWith("+") ? gap : "+" + gap;
    }

    private static void drawRoundedRect(Canvas canvas, RectF rect, float radius,
            int fillColor, int strokeColor, float strokeWidth) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(fillColor);
        canvas.drawRoundRect(rect, radius, radius, fill);
        if (strokeColor != 0 && strokeWidth > 0f) {
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(strokeColor);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(strokeWidth);
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }
    }

    private static void drawJerseyDot(Canvas canvas, float cx, float cy, String code, float radius) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (code) {
            case "GUL":
                fill.setColor(COLOR_YELLOW);
                break;
            case "GRO":
                fill.setColor(0xFF2ECC40);
                break;
            case "POL":
                fill.setColor(0xFFFFFFFF);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius, fill);
                Paint polStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                polStroke.setColor(0xFFE74C3C);
                polStroke.setStyle(Paint.Style.STROKE);
                polStroke.setStrokeWidth(Math.max(1f, radius * 0.3f));
                canvas.drawCircle(cx, cy, radius, polStroke);
                return;
            case "HVIT":
                fill.setColor(0xFFFFFFFF);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius, fill);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setColor(0xFFBBBBBB);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(Math.max(1f, radius * 0.25f));
                canvas.drawCircle(cx, cy, radius, stroke);
                return;
            default:
                fill.setColor(0xFFCCCCCC);
                break;
        }
        canvas.drawCircle(cx, cy, radius, fill);
    }

    private static String formatTimeCell(String gap) {
        if (gap == null || gap.isEmpty()) {
            return "—";
        }
        return gap;
    }

    private static String formatGap(String gap) {
        if (gap == null || gap.isEmpty() || "0:00".equals(gap)) {
            return "—";
        }
        return gap.startsWith("+") ? gap : "+" + gap;
    }

    private static Paint paintColor(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        return paint;
    }

    private static Paint textPaint(int color, float size, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        if (bold) {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return paint;
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
