package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DetailDashboardRenderer {

    private static final float CORNER_RADIUS = 18f;
    private static final int BG_COLOR = Color.parseColor("#151821");
    private static final int GRID_COLOR = Color.parseColor("#1E2A38");
    private static final int GRID_MINOR_COLOR = Color.parseColor("#15202B");
    private static final int BORDER_COLOR = Color.parseColor("#222633");
    private static final double HOME_LATITUDE = 59.9493;

    private DetailDashboardRenderer() {}

    // -------------------------------------------------------------------------
    // Outdoor temperature 24h
    // -------------------------------------------------------------------------

    static void drawOutdoorTemp(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblValNormal = paints[1], lblP = paints[2], lblPy = paints[3], gridP = paints[4];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float axisTxt = Math.max(13f, 17f * S);
        float gLeft = Math.max(42f, 58f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);
        float tGW = w - gLeft - wPad;
        float tGH = h - gTopOff - gBotOff;

        c.drawText("UTE 24t", wPad, hdrOff, lblHdr);
        String tHdr = String.format(Locale.getDefault(), "%.1f\u00b0\u2013%.1f\u00b0", d.todayTempMin, d.todayTempMax);
        c.drawText(tHdr, w - wPad - lblValNormal.measureText(tHdr), hdrOff, lblValNormal);

        float minT = Float.MAX_VALUE, maxT = -Float.MAX_VALUE;
        for (float[] p : d.todayTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        for (float[] p : d.yesterdayTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        for (float[] p : d.twoDaysAgoTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        for (float[] p : d.sixtyDayAvgTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        if (minT == Float.MAX_VALUE) { minT = 14f; maxT = 16f; }
        float range = maxT - minT;
        if (range < 2f) { minT -= 1; maxT += 1; range = 2f; }

        float tGL = gLeft, tGT = gTopOff;
        for (int i = 0; i <= 4; i++) {
            float frac = i / 4f, y = tGT + tGH * (1 - frac);
            c.drawLine(tGL, y, tGL + tGW, y, gridP);
            c.drawText(String.format(Locale.getDefault(), "%.0f\u00b0", minT + range * frac), tGL - 4f * S, y + 6f * S, lblPy);
        }
        Paint hourGridP = new Paint(gridP);
        hourGridP.setColor(GRID_MINOR_COLOR);
        for (int hr = 0; hr <= 24; hr++) {
            float x = tGL + tGW * (hr / 24f);
            c.drawLine(x, tGT, x, tGT + tGH, hr % 6 == 0 ? gridP : hourGridP);
        }
        for (int hr = 0; hr <= 24; hr += 6) {
            float x = tGL + tGW * (hr / 24f);
            String sl = String.format(Locale.getDefault(), "%02d", hr);
            c.drawText(sl, x - lblP.measureText(sl) / 2f, tGT + tGH + xAxisYOffset, lblP);
        }

        int colorToday = Color.parseColor("#27C93F");
        int colorTodayFill = Color.parseColor("#1A27C93F");
        int colorYesterday = Color.argb(90, 255, 95, 86);
        int colorTwoDaysAgo = Color.argb(45, 255, 95, 86);
        int colorSixtyDayAvg = Color.argb(140, 160, 168, 176);
        float lineTension = 0.1f;

        if (!d.sixtyDayAvgTempPoints.isEmpty())
            drawTempDashedLine(c, d.sixtyDayAvgTempPoints, minT, range, tGL, tGT, tGW, tGH, colorSixtyDayAvg, S, lineTension);
        if (!d.twoDaysAgoTempPoints.isEmpty())
            drawTempLine(c, d.twoDaysAgoTempPoints, minT, range, tGL, tGT, tGW, tGH, colorTwoDaysAgo, S, lineTension, null);
        if (!d.yesterdayTempPoints.isEmpty())
            drawTempLine(c, d.yesterdayTempPoints, minT, range, tGL, tGT, tGW, tGH, colorYesterday, S, lineTension, null);
        if (!d.todayTempPoints.isEmpty())
            drawTempLine(c, d.todayTempPoints, minT, range, tGL, tGT, tGW, tGH, colorToday, S, lineTension, colorTodayFill);
    }

    // -------------------------------------------------------------------------
    // Rain 12 weeks
    // -------------------------------------------------------------------------

    static void drawRain12w(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblValNormal = paints[1], lblP = paints[2], lblPy = paints[3], gridP = paints[4];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float axisTxt = Math.max(13f, 17f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);

        c.drawText("REGN 12u", wPad, hdrOff, lblHdr);
        String todayRainStr = "Idag: " + formatRainMm(d.todayRainMm);
        c.drawText(todayRainStr, w - wPad - lblValNormal.measureText(todayRainStr), hdrOff, lblValNormal);

        float rainTopExtra = Math.max(10f, 14f * S);
        float rainYLabelW = Math.max(14f, 18f * S);
        float tGH = h - gTopOff - gBotOff - rainTopExtra;
        float rGT = gTopOff + rainTopExtra;
        float rGL = wPad + rainYLabelW;
        float rGW = w - wPad - rainYLabelW - wPad;
        float rBase = rGT + tGH;

        float maxRain = 1f;
        for (float r : d.rainByWeek) maxRain = Math.max(maxRain, r);
        float[] rainAxis = chooseRainYAxis(maxRain);
        float rainAxisMax = rainAxis[0];
        float rainAxisStep = rainAxis[1];

        Paint rainYLblP = new Paint(lblPy);
        rainYLblP.setTextAlign(Paint.Align.LEFT);
        for (float mm = rainAxisStep; mm <= rainAxisMax + 0.01f; mm += rainAxisStep) {
            float y = rBase - tGH * (mm / rainAxisMax);
            c.drawLine(rGL, y, rGL + rGW, y, gridP);
            String yLabel = (Math.abs(mm - Math.round(mm)) < 0.01f)
                    ? String.format(Locale.getDefault(), "%.0f", mm)
                    : String.format(Locale.getDefault(), "%.1f", mm);
            c.drawText(yLabel, wPad, y + 6f * S, rainYLblP);
        }
        c.drawLine(rGL, rBase, rGL + rGW, rBase, gridP);

        int weekCount = d.rainByWeek.length;
        float bGap = rGW / weekCount;
        float bW = bGap * 0.72f;
        float barRadius = Math.max(1.5f, 2f * S);

        Calendar weekLabelCal = Calendar.getInstance();
        weekLabelCal.setTimeInMillis(getWeekMondayMillis(System.currentTimeMillis()));
        String[] weekLabels = new String[weekCount];
        for (int i = 0; i < weekCount; i++) {
            weekLabels[i] = formatRainWeekLabel(weekLabelCal);
            weekLabelCal.add(Calendar.WEEK_OF_YEAR, -1);
        }

        Paint weekLblP = new Paint(lblP);
        weekLblP.setTextSize(Math.max(11f, axisTxt * 0.82f));
        Paint barP = new Paint();
        barP.setAntiAlias(false);
        Paint mmLblP = new Paint();
        mmLblP.setAntiAlias(true);
        mmLblP.setColor(Color.WHITE);
        mmLblP.setTextSize(axisTxt);
        mmLblP.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < weekCount; i++) {
            float bCX = rGL + rGW - bGap * i - bGap / 2f;
            float rainVal = d.rainByWeek[i];
            if (rainVal > 0.05f) {
                float bH = tGH * (rainVal / rainAxisMax);
                float bT = rBase - bH;
                barP.setShader(new LinearGradient(0, rBase, 0, bT,
                        Color.parseColor("#1B6ADF"), Color.parseColor("#4FA5F7"),
                        Shader.TileMode.CLAMP));
                c.drawRoundRect(bCX - bW / 2f, bT, bCX + bW / 2f, rBase, barRadius, barRadius, barP);
                String mmStr = String.format(Locale.getDefault(), "%d", Math.round(rainVal));
                Paint.FontMetrics mmFm = mmLblP.getFontMetrics();
                float mmY = bT - Math.max(4f * S, mmFm.descent + 2f * S);
                c.drawText(mmStr, bCX, mmY, mmLblP);
            }
            c.drawText(weekLabels[i], bCX - weekLblP.measureText(weekLabels[i]) / 2f, rBase + xAxisYOffset, weekLblP);
        }
    }

    // -------------------------------------------------------------------------
    // NÅ (current readings)
    // -------------------------------------------------------------------------

    static void drawNow(Canvas c, DetailDashboardData d, float w, float h, @Nullable Context ctx) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);

        c.drawText("N\u00c5", wPad, hdrOff, lblHdr);

        float gTopOff = Math.max(38f, 52f * S);
        Paint naValP = new Paint();
        naValP.setAntiAlias(true);
        naValP.setColor(Color.WHITE);
        naValP.setTextSize(Math.max(22f, 28f * S));
        naValP.setTypeface(Typeface.DEFAULT_BOLD);

        float naIconSize = 30f * S;
        float naIconGap = 8f * S;
        Paint.FontMetrics naFm = naValP.getFontMetrics();
        float naContentTop = gTopOff;
        float naContentH = h - naContentTop - wPad * 0.4f;
        float naMidX = w / 2f;
        float naLeftX = wPad;
        float naRightX = naMidX + wPad * 0.55f;

        float naRow1CenterY = naContentTop + naContentH * 0.22f;
        float naRow2CenterY = naContentTop + naContentH * 0.50f;
        float naRow3CenterY = naContentTop + naContentH * 0.78f;

        // Row 1 left: temperature
        String tempStr = String.format(Locale.getDefault(), "%.1f\u00b0C", d.currentOutdoorTemp);
        float naRow1Baseline = naRow1CenterY - (naFm.ascent + naFm.descent) / 2f;
        float naRow1IconY = naRow1CenterY - naIconSize / 2f;
        drawIconWithFallback(c, ctx, R.drawable.ic_thermometer, naLeftX, naRow1IconY, naIconSize, S, naValP, IconType.THERMOMETER);
        c.drawText(tempStr, naLeftX + naIconSize + naIconGap, naRow1Baseline, naValP);

        // Row 2 left: humidity
        String humStr = String.format(Locale.getDefault(), "%.0f%%", d.valHumidity);
        float naRow2Baseline = naRow2CenterY - (naFm.ascent + naFm.descent) / 2f;
        float naRow2IconY = naRow2CenterY - naIconSize / 2f;
        drawIconWithFallback(c, ctx, R.drawable.ic_droplet, naLeftX, naRow2IconY, naIconSize, S, naValP, IconType.DROPLET);
        c.drawText(humStr, naLeftX + naIconSize + naIconGap, naRow2Baseline, naValP);

        // Row 3 left: rain rate
        String rainStr = String.format(Locale.getDefault(), "%.1f mm/t", d.valRainRate);
        float naRow3Baseline = naRow3CenterY - (naFm.ascent + naFm.descent) / 2f;
        float naRow3IconY = naRow3CenterY - naIconSize / 2f;
        drawIconWithFallback(c, ctx, R.drawable.ic_rain, naLeftX, naRow3IconY, naIconSize, S, naValP, IconType.RAIN);
        c.drawText(rainStr, naLeftX + naIconSize + naIconGap, naRow3Baseline, naValP);

        // Row 1 right: solar radiation
        String solRadStr = String.format(Locale.getDefault(), "%.0f W/m\u00b2", d.valSolarRad);
        drawSunIcon(c, naRightX, naRow1IconY, naIconSize, naValP);
        c.drawText(solRadStr, naRightX + naIconSize + naIconGap, naRow1Baseline, naValP);

        // Row 2 right: solar energy 24h
        String solEnergyStr = String.format(Locale.getDefault(), "%.1f kWh/m\u00b2", d.valSolarEnergy24h);
        drawSunIcon(c, naRightX, naRow2IconY, naIconSize, naValP);
        c.drawText(solEnergyStr, naRightX + naIconSize + naIconGap, naRow2Baseline, naValP);
    }

    // -------------------------------------------------------------------------
    // Lightning 7d
    // -------------------------------------------------------------------------

    static void drawLightning(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblValNormal = paints[1], lblP = paints[2], lblPy = paints[3], gridP = paints[4];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float axisTxt = Math.max(13f, 17f * S);
        float gLeft = Math.max(42f, 58f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);
        float tGW = w - gLeft - wPad;
        float tGH = h - gTopOff - gBotOff;

        c.drawText("LYN 7D", wPad, hdrOff, lblHdr);
        String headerStr = "Siste: " + formatLightningKm(d.lastLightningDistanceKm)
                + ", N\u00e6rmeste: " + formatLightningKm(d.nearestLightningDistanceKm);
        c.drawText(headerStr, w - wPad - lblValNormal.measureText(headerStr), hdrOff, lblValNormal);

        float countW = Math.max(56f * S, tGW * 0.22f);
        float plotGL = gLeft;
        float plotGT = gTopOff;
        float plotGW = tGW - countW - 8f * S;
        float plotGH = tGH;
        float plotBase = plotGT + plotGH;

        float maxDist = 1f;
        for (DetailDashboardData.LightningEvent event : d.lightningEvents7d) {
            maxDist = Math.max(maxDist, event.distanceKm);
        }
        float[] yAxis = chooseLightningYAxis(maxDist);
        float axisMaxKm = yAxis[0];
        float axisStepKm = yAxis[1];

        Paint yLblP = new Paint(lblPy);
        yLblP.setTextAlign(Paint.Align.LEFT);
        for (float km = 0f; km <= axisMaxKm + 0.01f; km += axisStepKm) {
            float y = plotBase - plotGH * (km / axisMaxKm);
            c.drawLine(plotGL, y, plotGL + plotGW, y, gridP);
            if (km > 0.01f) {
                c.drawText(String.format(Locale.getDefault(), "%.0f", km), wPad, y + 6f * S, yLblP);
            }
        }
        c.drawLine(plotGL, plotBase, plotGL + plotGW, plotBase, gridP);

        long windowStart = d.lightningWindowStartMs > 0 ? d.lightningWindowStartMs : System.currentTimeMillis() - 7L * 24 * 3600_000;
        long windowEnd = d.lightningWindowEndMs > 0 ? d.lightningWindowEndMs : System.currentTimeMillis();
        long windowSpan = Math.max(1L, windowEnd - windowStart);

        List<Long> midnightBoundaries = getMidnightBoundaries(windowStart, windowEnd);
        for (long midnightMs : midnightBoundaries) {
            float x = plotGL + plotGW * ((midnightMs - windowStart) / (float) windowSpan);
            c.drawLine(x, plotGT, x, plotBase, gridP);
        }

        List<Long> segmentBounds = new ArrayList<>();
        segmentBounds.add(windowStart);
        segmentBounds.addAll(midnightBoundaries);
        segmentBounds.add(windowEnd);

        Paint weekLblP = new Paint(lblP);
        weekLblP.setTextSize(Math.max(11f, axisTxt * 0.82f));
        weekLblP.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < segmentBounds.size() - 1; i++) {
            long segStart = segmentBounds.get(i);
            long segEnd = segmentBounds.get(i + 1);
            float centerX = plotGL + plotGW * (float) ((segStart + segEnd) / 2.0 - windowStart) / windowSpan;
            String label = formatLightningDayLabel(segStart, windowEnd);
            c.drawText(label, centerX, plotBase + xAxisYOffset, weekLblP);
        }

        float dotR = Math.max(4f, 5.5f * S);
        Paint dotP = new Paint();
        dotP.setAntiAlias(true);
        dotP.setStyle(Paint.Style.FILL);
        for (DetailDashboardData.LightningEvent event : d.lightningEvents7d) {
            float xFrac = (event.timeMs - windowStart) / (float) windowSpan;
            float x = plotGL + plotGW * xFrac;
            float y = plotBase - plotGH * (event.distanceKm / axisMaxKm);
            dotP.setColor(lightningStrikeColor(event.distanceKm, axisMaxKm));
            c.drawCircle(x, y, dotR, dotP);
        }

        float countLeft = plotGL + plotGW + 12f * S;
        float countCenterX = countLeft + countW / 2f;
        Paint countP = new Paint(lblValNormal);
        countP.setTextAlign(Paint.Align.CENTER);
        String countStr = String.format(Locale.getDefault(), "%d", d.lightningCount7d);
        Paint countLblP = new Paint(lblP);
        countLblP.setTextAlign(Paint.Align.CENTER);
        float countY = gTopOff + plotGH * 0.42f;
        c.drawText(countStr, countCenterX, countY, countP);
        c.drawText("lyn", countCenterX, countY + Math.max(18f, 24f * S), countLblP);
    }

    // -------------------------------------------------------------------------
    // Temp min/max 60d
    // -------------------------------------------------------------------------

    static void drawTempMinMax60d(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblValNormal = paints[1], lblP = paints[2], lblPy = paints[3], gridP = paints[4];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float axisTxt = Math.max(13f, 17f * S);
        float gLeft = Math.max(42f, 58f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);
        float tGW = w - gLeft - wPad;
        float tGH = h - gTopOff - gBotOff;

        c.drawText("TEMP SISTE 60D", wPad, hdrOff, lblHdr);
        if (!Float.isNaN(d.temp60dPeriodMin) && !Float.isNaN(d.temp60dPeriodMax)) {
            String tHdr = String.format(Locale.getDefault(), "%.1f\u00b0\u2013%.1f\u00b0",
                    d.temp60dPeriodMin, d.temp60dPeriodMax);
            c.drawText(tHdr, w - wPad - lblValNormal.measureText(tHdr), hdrOff, lblValNormal);
        }

        float chartGL = gLeft;
        float chartGT = gTopOff;
        float chartGH = tGH;
        float chartBase = chartGT + chartGH;

        boolean hasAnyData = false;
        for (DetailDashboardData.DailyTempRange day : d.tempMinMax60d) {
            if (day.hasData) { hasAnyData = true; break; }
        }

        if (!hasAnyData || Float.isNaN(d.temp60dPeriodMin) || Float.isNaN(d.temp60dPeriodMax)) {
            Paint noDP = new Paint();
            noDP.setColor(Color.GRAY);
            noDP.setTextSize(Math.max(13f, 15f * S));
            noDP.setAntiAlias(true);
            c.drawText("Ingen data", chartGL + tGW * 0.2f, chartGT + chartGH / 2f, noDP);
            return;
        }

        float[] axis = chooseTempChartAxis(d.temp60dPeriodMin, d.temp60dPeriodMax);
        float chartMin = axis[0];
        float chartMax = axis[1];
        float chartRange = chartMax - chartMin;
        if (chartRange < 1f) chartRange = 1f;

        Paint yLblP = new Paint(lblPy);
        yLblP.setTextAlign(Paint.Align.LEFT);
        for (float deg = chartMin; deg <= chartMax + 0.01f; deg += 5f) {
            float y = chartBase - chartGH * ((deg - chartMin) / chartRange);
            c.drawLine(chartGL, y, chartGL + tGW, y, gridP);
            if (deg > chartMin + 0.01f) {
                c.drawText(String.format(Locale.getDefault(), "%.0f\u00b0", deg), wPad, y + 6f * S, yLblP);
            }
        }

        int dayCount = d.tempMinMax60d.size();
        float barGap = Math.max(0.25f, 0.4f * S);
        float barW = Math.max(0.8f, (tGW - (dayCount - 1) * barGap) / dayCount);
        Paint barP = new Paint();
        barP.setAntiAlias(true);
        int colorTop = Color.parseColor("#6EC6F5");
        int colorBot = Color.parseColor("#1B6ADF");
        float barRadius = Math.max(0.4f, 0.7f * S);

        for (int i = 0; i < dayCount; i++) {
            DetailDashboardData.DailyTempRange day = d.tempMinMax60d.get(i);
            if (!day.hasData) continue;
            float barLeft = chartGL + i * (barW + barGap);
            float barRight = barLeft + barW;
            float yTop = chartBase - chartGH * ((day.max - chartMin) / chartRange);
            float yBot = chartBase - chartGH * ((day.min - chartMin) / chartRange);
            if (yBot - yTop < 2f * S) yBot = yTop + 2f * S;
            barP.setShader(new LinearGradient(0f, yTop, 0f, yBot, colorTop, colorBot, Shader.TileMode.CLAMP));
            c.drawRoundRect(barLeft, yTop, barRight, yBot, barRadius, barRadius, barP);
        }

        for (int i = 0; i < dayCount; i++) {
            if (i % 4 != 0 && i != dayCount - 1) continue;
            DetailDashboardData.DailyTempRange day = d.tempMinMax60d.get(i);
            float bCX = chartGL + i * (barW + barGap) + barW / 2f;
            String dayLabel = String.format(Locale.getDefault(), "%d", day.dayOfMonth);
            c.drawText(dayLabel, bCX - lblP.measureText(dayLabel) / 2f, chartBase + xAxisYOffset, lblP);
        }
    }

    // -------------------------------------------------------------------------
    // Soil humidity
    // -------------------------------------------------------------------------

    static void drawSoil(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblVal = paints[5], lblP = paints[2], lblPy = paints[3], gridP = paints[4];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float gLeft = Math.max(42f, 58f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);
        float tGW = w - gLeft - wPad;
        float tGH = h - gTopOff - gBotOff;

        boolean hasSoilData = d.soilPoints != null && !d.soilPoints.isEmpty();
        float curSoil = 0f;
        if (hasSoilData) {
            d.soilPoints.sort((a, b) -> Float.compare(b[0], a[0]));
            if (d.soilPoints.get(0)[0] < 72f) d.soilPoints.add(0, new float[]{72f, d.soilPoints.get(0)[1]});
            if (d.soilPoints.get(d.soilPoints.size() - 1)[0] > 0f)
                d.soilPoints.add(new float[]{0f, d.soilPoints.get(d.soilPoints.size() - 1)[1]});
            curSoil = d.soilPoints.get(d.soilPoints.size() - 1)[1];
        }

        c.drawText("JORD", wPad, hdrOff, lblHdr);
        String soilStr = hasSoilData ? String.format(Locale.getDefault(), "%.0f%%", curSoil) : "?";
        c.drawText(soilStr, w - wPad - lblVal.measureText(soilStr), hdrOff, lblVal);

        float sGL = gLeft, sGT = gTopOff, sGW = tGW, sGH = tGH;
        float minS = 0f, maxS = 100f, rangeS = maxS - minS;
        for (int i = 0; i <= 4; i++) {
            float frac = i / 4f, y = sGT + sGH * (1 - frac);
            c.drawLine(sGL, y, sGL + sGW, y, gridP);
            c.drawText(String.format(Locale.getDefault(), "%.0f%%", minS + rangeS * frac), sGL - 4f * S, y + 6f * S, lblPy);
        }

        if (hasSoilData) {
            String[] soilDays = {"N\u00e5", "1d", "2d", "3d"};
            for (int dd = 0; dd <= 3; dd++) {
                float x = sGL + sGW * (1f - dd / 3f);
                c.drawLine(x, sGT, x, sGT + sGH, gridP);
                c.drawText(soilDays[dd], x - lblP.measureText(soilDays[dd]) / 2f, sGT + sGH + xAxisYOffset, lblP);
            }
            Paint fillPS = new Paint(); fillPS.setColor(Color.parseColor("#1A3B82F6")); fillPS.setStyle(Paint.Style.FILL); fillPS.setAntiAlias(true);
            Path fillPathS = new Path(); fillPathS.moveTo(sGL + sGW * (1f - d.soilPoints.get(0)[0] / 72f), sGT + sGH);
            for (float[] p : d.soilPoints) fillPathS.lineTo(sGL + sGW * (1f - p[0] / 72f), sGT + sGH * (1f - (p[1] - minS) / rangeS));
            fillPathS.lineTo(sGL + sGW * (1f - d.soilPoints.get(d.soilPoints.size() - 1)[0] / 72f), sGT + sGH); fillPathS.close();
            c.drawPath(fillPathS, fillPS);
            Paint linePS = new Paint(); linePS.setColor(Color.parseColor("#3B82F6")); linePS.setStrokeWidth(3f * S); linePS.setStyle(Paint.Style.STROKE); linePS.setAntiAlias(true); linePS.setStrokeJoin(Paint.Join.ROUND); linePS.setStrokeCap(Paint.Cap.ROUND);
            Path linePathS = new Path(); boolean firstS = true;
            for (float[] p : d.soilPoints) { float x = sGL + sGW * (1f - p[0] / 72f), y = sGT + sGH * (1f - (p[1] - minS) / rangeS); if (firstS) { linePathS.moveTo(x, y); firstS = false; } else linePathS.lineTo(x, y); }
            c.drawPath(linePathS, linePS);
        } else {
            for (int dd = 0; dd <= 3; dd++) {
                float x = sGL + sGW * (1f - dd / 3f);
                c.drawLine(x, sGT, x, sGT + sGH, gridP);
            }
            Paint noDP = new Paint(); noDP.setColor(Color.GRAY); noDP.setTextSize(Math.max(13f, 15f * S)); noDP.setAntiAlias(true);
            c.drawText("Ingen data", sGL + sGW * 0.2f, sGT + sGH / 2f, noDP);
        }
    }

    // -------------------------------------------------------------------------
    // Camera widget
    // -------------------------------------------------------------------------

    static void drawCamera(Canvas c, @Nullable Bitmap bmp, String title, String tsStr,
            float w, float h, boolean yardCropOffset, float S,
            int batteryPercent, int batteryMinPercent, int batteryMaxPercent) {
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float hdrTxt = Math.max(17f, 23f * S);

        Paint lblHdr = new Paint();
        lblHdr.setAntiAlias(true);
        lblHdr.setColor(Color.WHITE);
        lblHdr.setTextSize(hdrTxt);
        lblHdr.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint lblValNormal = new Paint(lblHdr);
        lblValNormal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        drawWidgetCard(c, 0, 0, w, h, S);
        if (bmp != null) {
            Path clipPath = new Path();
            clipPath.addRoundRect(new RectF(0, 0, w, h), CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW);
            c.save();
            c.clipPath(clipPath);

            float bmpW = bmp.getWidth();
            float bmpH = bmp.getHeight();
            float scale = Math.max(w / bmpW, h / bmpH);
            if (yardCropOffset) scale *= 1.10f;
            float drawW = bmpW * scale;
            float drawH = bmpH * scale;
            float dx = (w - drawW) / 2f;
            float dy;
            if (yardCropOffset) {
                dy = h / 2f - 0.40f * drawH;
                dy = Math.max(h - drawH, Math.min(0, dy));
            } else {
                dy = (h - drawH) / 2f;
            }
            Rect src = new Rect(0, 0, (int) bmpW, (int) bmpH);
            RectF dst = new RectF(dx, dy, dx + drawW, dy + drawH);
            Paint bmpPaint = new Paint();
            bmpPaint.setFilterBitmap(false);
            bmpPaint.setAntiAlias(false);
            c.drawBitmap(bmp, src, dst, bmpPaint);

            Paint bannerP = new Paint();
            bannerP.setColor(Color.argb(160, 0, 0, 0));
            c.drawRect(0, 0, w, hdrOff, bannerP);

            float bannerMidY = hdrOff / 2f;
            Paint.FontMetrics hdrFm = lblHdr.getFontMetrics();
            float titleBaselineY = bannerMidY - (hdrFm.ascent + hdrFm.descent) / 2f;
            c.drawText(title, wPad, titleBaselineY, lblHdr);

            String displayTs = (tsStr != null && !tsStr.isEmpty()) ? tsStr : "Live";
            String rightText = displayTs;
            if (batteryPercent >= 0) {
                if (batteryMinPercent >= 0 && batteryMaxPercent >= 0) {
                    rightText = String.format(Locale.getDefault(), "%d%% (%d%% - %d%%)  %s",
                            batteryPercent, batteryMinPercent, batteryMaxPercent, displayTs);
                } else {
                    rightText = String.format(Locale.getDefault(), "%d%%  %s", batteryPercent, displayTs);
                }
            }
            Paint.FontMetrics tsFm = lblValNormal.getFontMetrics();
            float tsBaselineY = bannerMidY - (tsFm.ascent + tsFm.descent) / 2f;
            c.drawText(rightText, w - wPad - lblValNormal.measureText(rightText), tsBaselineY, lblValNormal);

            c.restore();
            drawWidgetCardBorder(c, 0, 0, w, h, S);
        } else {
            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setColor(Color.GRAY);
            textPaint.setTextSize(28f);
            String placeholder = title + " laster...";
            c.drawText(placeholder, (w - textPaint.measureText(placeholder)) / 2f, h / 2f, textPaint);
        }
    }

    // -------------------------------------------------------------------------
    // Sun path
    // -------------------------------------------------------------------------

    static void drawSunPath(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        drawWidgetCard(c, 0, 0, w, h, S);
        Paint[] paints = createStandardPaints(S);
        Paint lblHdr = paints[0], lblValNormal = paints[1], lblP = paints[2];
        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float axisTxt = Math.max(13f, 17f * S);

        c.drawText("SOL", wPad, hdrOff, lblHdr);
        String posStr = String.format(Locale.getDefault(), "H\u00d8YDE %.0f\u00b0 / %.0f\u00b0",
                d.sunElevation, d.sunAzimuth);
        c.drawText(posStr, w - wPad - lblValNormal.measureText(posStr), hdrOff, lblValNormal);

        long nowMs = System.currentTimeMillis();
        float[] riseSetAz = computeTodaySunriseSunsetAzimuth(nowMs);
        float riseAz = riseSetAz[0];
        float setAz = riseSetAz[1];
        float rawSpan = normalizeDegrees(setAz - riseAz);
        int upDeg = Math.round(rawSpan);
        if (upDeg <= 0) upDeg = 360;
        if (upDeg > 360) upDeg = 360;
        int downDeg = 360 - upDeg;

        Paint statsP = new Paint(lblP);
        statsP.setTextSize(Math.max(11f, axisTxt * 0.95f));
        statsP.setColor(Color.parseColor("#C5D0DC"));
        Paint statsMutedP = new Paint(statsP);
        statsMutedP.setColor(Color.parseColor("#8E9AA8"));
        float statsY = hdrOff + Math.max(14f, axisTxt * 1.15f);
        String upStr = String.format(Locale.getDefault(), "Oppe %d\u00b0", upDeg);
        String downStr = String.format(Locale.getDefault(), "Nede %d\u00b0", downDeg);
        c.drawText(upStr, wPad, statsY, statsP);
        c.drawText(downStr, w - wPad - statsMutedP.measureText(downStr), statsY, statsMutedP);

        float timeBlockH = axisTxt * 2.4f;
        float labelPad = Math.max(12f, axisTxt * 1.25f);
        float horizonY = h - wPad - timeBlockH;
        float contentTop = statsY + Math.max(6f, 8f * S);
        float plotLeft = wPad * 1.2f;
        float plotRight = w - wPad * 1.2f;
        float plotCx = (plotLeft + plotRight) / 2f;
        float maxHalfWidth = Math.max(8f, (plotRight - plotLeft) / 2f - labelPad);
        float maxArcHeight = Math.max(8f, horizonY - contentTop - labelPad * 0.85f);

        float span = Math.max(25f, Math.min(335f, rawSpan));
        double halfSpanRad = Math.toRadians(span / 2.0);
        double sinHalf = Math.sin(halfSpanRad);
        double cosHalf = Math.cos(halfSpanRad);
        if (sinHalf < 0.05) sinHalf = 0.05;

        float rFromWidth = (float) (maxHalfWidth / sinHalf);
        float rFromHeight = (float) (maxArcHeight / Math.max(0.05, 1.0 - cosHalf));
        float arcR = Math.min(rFromWidth, rFromHeight);
        if (arcR < 8f) arcR = 8f;
        float halfWidth = (float) (arcR * sinHalf);
        float arcCx = plotCx;
        float arcCy = horizonY + (float) (arcR * cosHalf);

        RectF arcOval = new RectF(arcCx - arcR, arcCy - arcR, arcCx + arcR, arcCy + arcR);
        float leftAngle = normalizeDegrees((float) Math.toDegrees(Math.atan2(horizonY - arcCy, -halfWidth)));
        float rightAngle = normalizeDegrees((float) Math.toDegrees(Math.atan2(horizonY - arcCy, halfWidth)));
        float sweep = normalizeDegrees(rightAngle - leftAngle);
        float midAngle = normalizeDegrees(leftAngle + sweep / 2f);
        float distToTop = Math.min(normalizeDegrees(midAngle - 270f), normalizeDegrees(270f - midAngle));
        if (distToTop > 90f) sweep = sweep - 360f;
        float nightSweep = (sweep >= 0f) ? (sweep - 360f) : (sweep + 360f);

        float chordLeft = arcCx - halfWidth;
        float chordRight = arcCx + halfWidth;

        c.save();
        Path clipPath = new Path();
        clipPath.addRoundRect(new RectF(0, 0, w, h), CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW);
        c.clipPath(clipPath);

        Path fillPath = new Path();
        fillPath.moveTo(chordLeft, horizonY);
        fillPath.arcTo(arcOval, leftAngle, sweep, false);
        fillPath.close();
        Paint fillP = new Paint();
        fillP.setAntiAlias(true);
        fillP.setStyle(Paint.Style.FILL);
        float fillTopY = Math.min(arcCy - arcR, horizonY - arcR * 0.2f);
        fillP.setShader(new LinearGradient(0f, fillTopY, 0f, horizonY,
                Color.parseColor("#1F2A3D"), Color.parseColor("#10141C"), Shader.TileMode.CLAMP));
        c.drawPath(fillPath, fillP);

        Path nightPath = new Path();
        nightPath.addArc(arcOval, rightAngle, nightSweep);
        Paint nightStrokeP = new Paint();
        nightStrokeP.setAntiAlias(true);
        nightStrokeP.setStyle(Paint.Style.STROKE);
        nightStrokeP.setStrokeWidth(Math.max(1.5f, 2f * S));
        nightStrokeP.setStrokeCap(Paint.Cap.ROUND);
        nightStrokeP.setColor(Color.parseColor("#2A3340"));
        c.drawPath(nightPath, nightStrokeP);

        Path arcPath = new Path();
        arcPath.addArc(arcOval, leftAngle, sweep);
        Paint arcStrokeP = new Paint();
        arcStrokeP.setAntiAlias(true);
        arcStrokeP.setStyle(Paint.Style.STROKE);
        arcStrokeP.setStrokeWidth(Math.max(2f, 2.5f * S));
        arcStrokeP.setStrokeCap(Paint.Cap.ROUND);
        arcStrokeP.setColor(Color.parseColor("#4A5568"));
        c.drawPath(arcPath, arcStrokeP);

        Paint tickP = new Paint();
        tickP.setAntiAlias(true);
        tickP.setStyle(Paint.Style.STROKE);
        tickP.setStrokeWidth(Math.max(1f, 1.5f * S));
        tickP.setStrokeCap(Paint.Cap.ROUND);
        tickP.setColor(Color.parseColor("#5C6A7D"));
        float tickLen = Math.max(3f, 5f * S);
        int tickCount = Math.max(2, Math.round(Math.abs(sweep) / 10f));
        for (int i = 0; i <= tickCount; i++) {
            float ang = leftAngle + sweep * (i / (float) tickCount);
            double rad = Math.toRadians(ang);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float x1 = arcCx + arcR * cos;
            float y1 = arcCy + arcR * sin;
            c.drawLine(x1, y1, x1 + cos * tickLen, y1 + sin * tickLen, tickP);
        }

        Paint horizonP = new Paint();
        horizonP.setColor(Color.parseColor("#222633"));
        horizonP.setStrokeWidth(Math.max(1f, 1.5f * S));
        c.drawLine(Math.min(plotLeft, chordLeft), horizonY, Math.max(plotRight, chordRight), horizonY, horizonP);

        Paint azLblP = new Paint(lblP);
        azLblP.setTextSize(Math.max(9f, axisTxt * 0.72f));
        azLblP.setTextAlign(Paint.Align.CENTER);
        azLblP.setColor(Color.parseColor("#8E9AA8"));
        Paint azCardinalP = new Paint(azLblP);
        azCardinalP.setColor(Color.parseColor("#D0D7E0"));
        azCardinalP.setTextSize(Math.max(10f, axisTxt * 0.78f));
        float labelR = arcR + Math.max(9f, labelPad * 0.72f);
        Paint.FontMetrics azFm = azLblP.getFontMetrics();
        float azTextOffsetY = -(azFm.ascent + azFm.descent) / 2f;
        int[] labelAzimuths = {45, 90, 135, 180, 225, 270, 315};
        String[] labelNames = {null, "\u00d8", null, "S", null, "V", null};
        for (int i = 0; i < labelAzimuths.length; i++) {
            float labelAz = labelAzimuths[i];
            float alongDay = normalizeDegrees(labelAz - riseAz);
            if (alongDay > span + 0.8f) continue;
            float t = Math.max(0f, Math.min(1f, alongDay / Math.max(1f, span)));
            float thetaDeg = leftAngle + sweep * t;
            double rad = Math.toRadians(thetaDeg);
            float lx = arcCx + labelR * (float) Math.cos(rad);
            float ly = arcCy + labelR * (float) Math.sin(rad);
            if (lx < 2f || lx > w - 2f || ly < hdrOff || ly > h - timeBlockH * 0.35f) continue;
            String text;
            Paint useP;
            if (labelNames[i] != null) {
                text = String.format(Locale.getDefault(), "%s %d\u00b0", labelNames[i], labelAzimuths[i]);
                useP = azCardinalP;
            } else {
                text = String.format(Locale.getDefault(), "%d\u00b0", labelAzimuths[i]);
                useP = azLblP;
            }
            c.drawText(text, lx, ly + azTextOffsetY, useP);
        }

        float az = normalizeDegrees(d.sunAzimuth);
        float along = normalizeDegrees(az - riseAz);
        if (along <= span + 0.5f && d.sunElevation >= 0f) {
            float t = Math.max(0f, Math.min(1f, along / span));
            float thetaDeg = leftAngle + sweep * t;
            double thetaRad = Math.toRadians(thetaDeg);
            float sunX = arcCx + arcR * (float) Math.cos(thetaRad);
            float sunY = arcCy + arcR * (float) Math.sin(thetaRad);
            float sunSize = Math.max(16f, 20f * S);

            Paint outerGlowP = new Paint(); outerGlowP.setAntiAlias(true); outerGlowP.setStyle(Paint.Style.FILL); outerGlowP.setColor(Color.parseColor("#55FFD700"));
            c.drawCircle(sunX, sunY, sunSize * 1.15f, outerGlowP);
            Paint midGlowP = new Paint(); midGlowP.setAntiAlias(true); midGlowP.setStyle(Paint.Style.FILL); midGlowP.setColor(Color.parseColor("#88FFC107"));
            c.drawCircle(sunX, sunY, sunSize * 0.85f, midGlowP);
            Paint innerGlowP = new Paint(); innerGlowP.setAntiAlias(true); innerGlowP.setStyle(Paint.Style.FILL); innerGlowP.setColor(Color.parseColor("#CCFFF59D"));
            c.drawCircle(sunX, sunY, sunSize * 0.42f, innerGlowP);
            Paint sunP = new Paint(); sunP.setAntiAlias(true); sunP.setColor(Color.parseColor("#FFD700"));
            drawSunIcon(c, sunX - sunSize / 2f, sunY - sunSize / 2f, sunSize, sunP);
        }
        c.restore();

        long todayLenMs = computeDayLengthMs(nowMs);
        long yesterdayLenMs = computeDayLengthMs(nowMs - 24L * 3_600_000L);
        String dayLenStr = formatDayLengthHm(todayLenMs);
        String dayDeltaStr = formatDayLengthDelta(todayLenMs - yesterdayLenMs);
        Paint dayLenP = new Paint(lblP); dayLenP.setTextSize(Math.max(11f, axisTxt * 0.92f)); dayLenP.setColor(Color.parseColor("#C5D0DC")); dayLenP.setTextAlign(Paint.Align.LEFT);
        Paint dayDeltaP = new Paint(dayLenP); dayDeltaP.setTextSize(Math.max(10f, axisTxt * 0.78f)); dayDeltaP.setColor(Color.parseColor("#8E9AA8"));
        float dayLenX = wPad;
        float dayLenY = contentTop + (horizonY - contentTop) * 0.42f;
        c.drawText(dayLenStr, dayLenX, dayLenY, dayLenP);
        c.drawText(dayDeltaStr, dayLenX, dayLenY + Math.max(14f, axisTxt * 1.05f), dayDeltaP);

        Paint timeP = new Paint(lblP); timeP.setTextSize(axisTxt);
        Paint timeLblP = new Paint(lblP); timeLblP.setTextSize(Math.max(10f, axisTxt * 0.82f)); timeLblP.setColor(Color.parseColor("#8E9AA8"));
        float timeY = h - wPad;
        c.drawText("Opp", plotLeft, timeY - axisTxt * 1.15f, timeLblP);
        c.drawText(formatSunTime(d.sunNextRisingMs), plotLeft, timeY, timeP);
        String setStr = formatSunTime(d.sunNextSettingMs);
        float setTextW = timeP.measureText(setStr);
        c.drawText("Ned", plotRight - timeLblP.measureText("Ned"), timeY - axisTxt * 1.15f, timeLblP);
        c.drawText(setStr, plotRight - setTextW, timeY, timeP);
    }

    // -------------------------------------------------------------------------
    // Room temperature grid
    // -------------------------------------------------------------------------

    static void drawRoomGrid(Canvas c, DetailDashboardData d, float w, float h) {
        float S = w / 420f;
        float gapRoom = 8f * S;
        float roomCardH = (h - 3f * gapRoom) / 4f;
        float gridY = 0;

        float rw3 = (w - 2f * gapRoom) / 3f;
        drawRoomCard(c, "Jonatan", d.valJonatan, 0, gridY, rw3, roomCardH, d.valJonatanMotionTime);
        drawRoomCard(c, "Loftsgang", d.valLoftsgang, rw3 + gapRoom, gridY, rw3, roomCardH, d.valLoftsgangMotionTime);
        drawRoomCard(c, "Kontor", d.valKontor, 2f * (rw3 + gapRoom), gridY, rw3, roomCardH, 0L);

        gridY += roomCardH + gapRoom;
        float rw4 = (w - 3f * gapRoom) / 4f;
        drawRoomCard(c, "Bad", d.valBad, 0, gridY, rw4, roomCardH, d.valBadMotionTime);
        drawRoomCard(c, "Kj\u00f8kken", d.valKjokken, rw4 + gapRoom, gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Lite bad", d.valLiteBad, 2f * (rw4 + gapRoom), gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Mats", d.valMats, 3f * (rw4 + gapRoom), gridY, rw4, roomCardH, 0L);

        gridY += roomCardH + gapRoom;
        drawRoomCard(c, "Vinterhage", d.valVinterhage, 0, gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Stue", d.valStue, rw4 + gapRoom, gridY, rw4, roomCardH, d.valStueMotionTime);
        drawRoomCard(c, "Gang", d.valGang3, 2f * (rw4 + gapRoom), gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Soverom", d.valSoverom, 3f * (rw4 + gapRoom), gridY, rw4, roomCardH, 0L);

        gridY += roomCardH + gapRoom;
        float rw2 = (w - gapRoom) / 2f;
        drawRoomCard(c, "Gang", d.valGang4, 0, gridY, rw2, roomCardH, d.valGang4MotionTime);
        drawRoomCard(c, "Vaskerom", d.valVaskerom, rw2 + gapRoom, gridY, rw2, roomCardH, d.valVaskeromMotionTime);
    }

    // -------------------------------------------------------------------------
    // Shared drawing helpers
    // -------------------------------------------------------------------------

    private static void drawWidgetCard(Canvas canvas, float left, float top, float right, float bottom, float S) {
        Paint bg = new Paint();
        bg.setColor(BG_COLOR);
        bg.setStyle(Paint.Style.FILL);
        bg.setAntiAlias(true);
        canvas.drawRoundRect(left, top, right, bottom, CORNER_RADIUS, CORNER_RADIUS, bg);
        drawWidgetCardBorder(canvas, left, top, right, bottom, S);
    }

    private static void drawWidgetCardBorder(Canvas canvas, float left, float top, float right, float bottom, float S) {
        Paint stroke = new Paint();
        stroke.setColor(BORDER_COLOR);
        stroke.setStyle(Paint.Style.STROKE);
        float strokeWidth = Math.max(1f, 1.5f * S);
        stroke.setStrokeWidth(strokeWidth);
        stroke.setAntiAlias(false);
        float inset = strokeWidth / 2f;
        canvas.drawRoundRect(left + inset, top + inset, right - inset, bottom - inset,
                Math.max(0f, CORNER_RADIUS - inset), Math.max(0f, CORNER_RADIUS - inset), stroke);
    }

    private static Paint[] createStandardPaints(float S) {
        float axisTxt = Math.max(13f, 17f * S);
        float hdrTxt = Math.max(17f, 23f * S);

        Paint lblHdr = new Paint(); lblHdr.setAntiAlias(true); lblHdr.setColor(Color.WHITE); lblHdr.setTextSize(hdrTxt);
        lblHdr.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint lblValNormal = new Paint(lblHdr); lblValNormal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        Paint lblP = new Paint(); lblP.setAntiAlias(true); lblP.setColor(Color.WHITE); lblP.setTextSize(axisTxt);
        Paint lblPy = new Paint(lblP); lblPy.setTextAlign(Paint.Align.RIGHT);
        Paint gridP = new Paint(); gridP.setColor(GRID_COLOR); gridP.setStrokeWidth(2f);
        Paint lblVal = new Paint(lblHdr);

        return new Paint[]{lblHdr, lblValNormal, lblP, lblPy, gridP, lblVal};
    }

    // -------------------------------------------------------------------------
    // Temperature line helpers
    // -------------------------------------------------------------------------

    private static void drawTempLine(Canvas canvas, List<float[]> points, float minT, float range,
            float tGL, float tGT, float tGW, float tGH, int lineColor, float S, float tension, Integer fillColor) {
        if (points.size() < 2) return;
        List<float[]> screen = new ArrayList<>();
        for (float[] p : points) {
            float x = tGL + tGW * (p[0] / 24f);
            float y = tGT + tGH * (1f - (p[1] - minT) / range);
            screen.add(new float[]{x, y});
        }
        Path linePath = new Path();
        buildSmoothPath(linePath, screen, tension);

        if (fillColor != null) {
            Path fillPath = new Path(linePath);
            float[] first = screen.get(0);
            float[] last = screen.get(screen.size() - 1);
            fillPath.lineTo(last[0], tGT + tGH);
            fillPath.lineTo(first[0], tGT + tGH);
            fillPath.close();
            Paint fillP = new Paint();
            fillP.setColor(fillColor);
            fillP.setStyle(Paint.Style.FILL);
            fillP.setAntiAlias(true);
            canvas.drawPath(fillPath, fillP);
        }

        Paint lineP = new Paint();
        lineP.setColor(lineColor);
        lineP.setStrokeWidth(3f * S);
        lineP.setStyle(Paint.Style.STROKE);
        lineP.setAntiAlias(true);
        lineP.setStrokeJoin(Paint.Join.ROUND);
        lineP.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(linePath, lineP);
    }

    private static void drawTempDashedLine(Canvas canvas, List<float[]> points, float minT, float range,
            float tGL, float tGT, float tGW, float tGH, int lineColor, float S, float tension) {
        if (points.size() < 2) return;
        List<float[]> screen = new ArrayList<>();
        for (float[] p : points) {
            float x = tGL + tGW * (p[0] / 24f);
            float y = tGT + tGH * (1f - (p[1] - minT) / range);
            screen.add(new float[]{x, y});
        }
        Path linePath = new Path();
        buildSmoothPath(linePath, screen, tension);
        Paint lineP = new Paint();
        lineP.setColor(lineColor);
        lineP.setStrokeWidth(2.5f * S);
        lineP.setStyle(Paint.Style.STROKE);
        lineP.setAntiAlias(true);
        lineP.setStrokeJoin(Paint.Join.ROUND);
        lineP.setStrokeCap(Paint.Cap.ROUND);
        lineP.setPathEffect(new DashPathEffect(new float[]{10f * S, 7f * S}, 0f));
        canvas.drawPath(linePath, lineP);
    }

    private static void buildSmoothPath(Path path, List<float[]> screenPoints, float tension) {
        if (screenPoints.isEmpty()) return;
        path.moveTo(screenPoints.get(0)[0], screenPoints.get(0)[1]);
        if (screenPoints.size() == 1) return;
        for (int i = 0; i < screenPoints.size() - 1; i++) {
            float[] p0 = screenPoints.get(Math.max(0, i - 1));
            float[] p1 = screenPoints.get(i);
            float[] p2 = screenPoints.get(i + 1);
            float[] p3 = screenPoints.get(Math.min(screenPoints.size() - 1, i + 2));
            float cp1x = p1[0] + (p2[0] - p0[0]) * tension;
            float cp1y = p1[1] + (p2[1] - p0[1]) * tension;
            float cp2x = p2[0] - (p3[0] - p1[0]) * tension;
            float cp2y = p2[1] - (p3[1] - p1[1]) * tension;
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2[0], p2[1]);
        }
    }

    // -------------------------------------------------------------------------
    // Room card
    // -------------------------------------------------------------------------

    private static void drawRoomCard(Canvas canvas, String name, float temp, float x, float y, float w, float h, long motionTime) {
        int color = getTemperatureColor(temp);
        float radius = h * 0.16f;
        float strokeW = Math.max(1.5f, h * 0.04f);

        Paint baseBgPaint = new Paint();
        baseBgPaint.setStyle(Paint.Style.FILL);
        baseBgPaint.setColor(Color.parseColor("#11141E"));
        baseBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, baseBgPaint);

        Paint tintBgPaint = new Paint();
        tintBgPaint.setStyle(Paint.Style.FILL);
        tintBgPaint.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        tintBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, tintBgPaint);

        Paint strokePaint = new Paint();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeW);
        strokePaint.setColor(color);
        strokePaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, strokePaint);

        float textSize = Math.min(16f, h * 0.26f);
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(textSize);
        textPaint.setColor(Color.parseColor("#E1E4EA"));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        float nameWidth = textPaint.measureText(name);
        canvas.drawText(name, x + (w - nameWidth) / 2f, y + h * 0.36f, textPaint);

        Paint tempPaint = new Paint();
        tempPaint.setAntiAlias(true);
        tempPaint.setTextSize(textSize * 1.15f);
        tempPaint.setTypeface(Typeface.DEFAULT_BOLD);
        tempPaint.setColor(color);
        String tempStr = String.format(Locale.getDefault(), "%.1f\u00b0", temp);
        float tempWidth = tempPaint.measureText(tempStr);
        canvas.drawText(tempStr, x + (w - tempWidth) / 2f, y + h * 0.83f, tempPaint);

        boolean recentlyDetected = false;
        String motionTimeStr = "";
        if (motionTime > 0) {
            long diff = System.currentTimeMillis() - motionTime;
            if (diff >= 0 && diff <= 3600_000L) {
                recentlyDetected = true;
                motionTimeStr = new SimpleDateFormat("mm", Locale.getDefault()).format(new Date(motionTime));
            }
        }
        if (recentlyDetected) {
            float cardS = w / 164f;
            Paint motionPaint = new Paint();
            motionPaint.setAntiAlias(true);
            motionPaint.setTextSize(textSize * 0.9f);
            motionPaint.setColor(Color.parseColor("#8E9AA8"));
            motionPaint.setTypeface(Typeface.DEFAULT_BOLD);
            float pad = Math.max(6f, 8f * cardS);
            Paint.FontMetrics motionFm = motionPaint.getFontMetrics();
            float motionBaselineY = y + pad - motionFm.ascent;
            float motionX = x + w - pad - motionPaint.measureText(motionTimeStr);
            canvas.drawText(motionTimeStr, motionX, motionBaselineY, motionPaint);
        }
    }

    // -------------------------------------------------------------------------
    // Color helpers
    // -------------------------------------------------------------------------

    private static int getTemperatureColor(float temp) {
        int c15 = Color.parseColor("#481581");
        int c18 = Color.parseColor("#6ac6ef");
        int c22 = Color.parseColor("#0eb30e");
        int c24 = Color.parseColor("#ffb37a");
        int c29 = Color.parseColor("#78003d");
        if (temp <= 15f) return c15;
        else if (temp < 18f) return interpolateColor(c15, c18, (temp - 15f) / 3f);
        else if (temp < 22f) return interpolateColor(c18, c22, (temp - 18f) / 4f);
        else if (temp < 24f) return interpolateColor(c22, c24, (temp - 22f) / 2f);
        else if (temp < 29f) return interpolateColor(c24, c29, (temp - 24f) / 5f);
        else return c29;
    }

    private static int interpolateColor(int c1, int c2, float fraction) {
        int r = (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * fraction);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * fraction);
        int b = (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * fraction);
        return Color.rgb(r, g, b);
    }

    private static int lightningStrikeColor(float distanceKm, float axisMaxKm) {
        float proximity = 1f - Math.min(1f, distanceKm / Math.max(1f, axisMaxKm));
        int far = Color.parseColor("#FFD966");
        int near = Color.parseColor("#FF3B30");
        return interpolateColor(far, near, proximity);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private static String formatRainMm(float mm) {
        if (Math.abs(mm - Math.round(mm)) < 0.05f) {
            return String.format(Locale.getDefault(), "%.0fmm", mm);
        }
        return String.format(Locale.getDefault(), "%.1f", mm).replace('.', ',') + "mm";
    }

    private static String formatLightningKm(float km) {
        if (km < 0f) return "?";
        return String.format(Locale.getDefault(), "%.0fkm", km);
    }

    private static String formatRainWeekLabel(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        return day + "." + month;
    }

    private static String formatSunTime(long timeMs) {
        if (timeMs <= 0L) return "--:--";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timeMs));
    }

    private static String formatDayLengthHm(long durationMs) {
        long totalMin = Math.max(0L, Math.round(durationMs / 60_000.0));
        long hours = totalMin / 60L;
        long mins = totalMin % 60L;
        return String.format(Locale.getDefault(), "%dt %02dm", hours, mins);
    }

    private static String formatDayLengthDelta(long deltaMs) {
        long totalMin = Math.round(Math.abs(deltaMs) / 60_000.0);
        if (totalMin <= 0L) return "\u00b10m";
        String sign = deltaMs > 0L ? "+" : "\u2212";
        if (totalMin >= 60L) return sign + (totalMin / 60L) + "t " + String.format(Locale.getDefault(), "%02dm", totalMin % 60L);
        return sign + totalMin + "m";
    }

    // -------------------------------------------------------------------------
    // Y-axis helpers
    // -------------------------------------------------------------------------

    private static float[] chooseRainYAxis(float maxRain) {
        float[] steps = {2f, 5f, 10f, 20f};
        for (float step : steps) {
            float axisMax = (float) Math.ceil(maxRain / step) * step;
            if (axisMax < step) axisMax = step;
            int lineCount = (int) (axisMax / step);
            if (lineCount >= 2 && lineCount <= 5) return new float[]{axisMax, step};
        }
        float step = 10f;
        float axisMax = Math.max(step, (float) Math.ceil(maxRain / step) * step);
        return new float[]{axisMax, step};
    }

    private static float[] chooseLightningYAxis(float maxKm) {
        float safeMax = Math.max(1f, maxKm);
        float[] steps = {5f, 10f, 20f, 30f, 50f};
        for (float step : steps) {
            float axisMax = (float) Math.ceil(safeMax / step) * step;
            if (axisMax < step) axisMax = step;
            int lineCount = (int) (axisMax / step);
            if (lineCount >= 2 && lineCount <= 5) return new float[]{axisMax, step};
        }
        float step = 10f;
        float axisMax = Math.max(step, (float) Math.ceil(safeMax / step) * step);
        return new float[]{axisMax, step};
    }

    private static float[] chooseTempChartAxis(float dataMin, float dataMax) {
        float chartMin = (float) (Math.floor((dataMin - 2f) / 5.0) * 5.0);
        float chartMax = (float) (Math.ceil((dataMax + 2f) / 5.0) * 5.0);
        if (chartMax - chartMin < 10f) chartMax = chartMin + 10f;
        return new float[]{chartMin, chartMax};
    }

    // -------------------------------------------------------------------------
    // Lightning day helpers
    // -------------------------------------------------------------------------

    private static List<Long> getMidnightBoundaries(long windowStart, long windowEnd) {
        List<Long> midnights = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(windowStart);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < windowStart) cal.add(Calendar.DAY_OF_MONTH, 1);
        while (cal.getTimeInMillis() < windowEnd) {
            midnights.add(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return midnights;
    }

    private static boolean isSameDayMs(long timeA, long timeB) {
        Calendar calA = Calendar.getInstance(); calA.setTimeInMillis(timeA);
        Calendar calB = Calendar.getInstance(); calB.setTimeInMillis(timeB);
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) && calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR);
    }

    private static String formatLightningDayLabel(long segmentStartMs, long windowEndMs) {
        if (isSameDayMs(segmentStartMs, windowEndMs)) return "Idag";
        Calendar labelCal = Calendar.getInstance();
        labelCal.setTimeInMillis(segmentStartMs);
        return formatRainWeekLabel(labelCal);
    }

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

    // -------------------------------------------------------------------------
    // Solar computation helpers
    // -------------------------------------------------------------------------

    @NonNull
    private static float[] computeTodaySunriseSunsetAzimuth(long nowMs) {
        double omega = computeSolarHourAngleRad(nowMs);
        double lat = Math.toRadians(HOME_LATITUDE);
        double decl = computeSolarDeclinationRad(nowMs);
        float riseAz = (float) solarAzimuthDegrees(lat, decl, -omega);
        float setAz = (float) solarAzimuthDegrees(lat, decl, omega);
        return new float[]{riseAz, setAz};
    }

    private static double computeSolarDeclinationRad(long nowMs) {
        Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(nowMs);
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        return Math.toRadians(23.44 * Math.sin(Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))));
    }

    private static double computeSolarHourAngleRad(long nowMs) {
        double lat = Math.toRadians(HOME_LATITUDE);
        double decl = computeSolarDeclinationRad(nowMs);
        double cosOmega = -Math.tan(lat) * Math.tan(decl);
        cosOmega = Math.max(-1.0, Math.min(1.0, cosOmega));
        return Math.acos(cosOmega);
    }

    private static long computeDayLengthMs(long nowMs) {
        double omegaDeg = Math.toDegrees(computeSolarHourAngleRad(nowMs));
        return Math.round(2.0 * omegaDeg / 15.0 * 3_600_000.0);
    }

    private static double solarAzimuthDegrees(double latRad, double declRad, double hourAngleRad) {
        double sinAz = -Math.cos(declRad) * Math.sin(hourAngleRad);
        double cosAz = Math.sin(declRad) * Math.cos(latRad) - Math.cos(declRad) * Math.sin(latRad) * Math.cos(hourAngleRad);
        double az = Math.toDegrees(Math.atan2(sinAz, cosAz));
        if (az < 0.0) az += 360.0;
        return az;
    }

    private static float normalizeDegrees(float deg) {
        float d = deg % 360f;
        if (d < 0f) d += 360f;
        return d;
    }

    // -------------------------------------------------------------------------
    // Icon drawing helpers
    // -------------------------------------------------------------------------

    private enum IconType { THERMOMETER, DROPLET, RAIN }

    private static void drawIconWithFallback(Canvas c, @Nullable Context ctx, int drawableRes,
            float x, float y, float size, float S, Paint paint, IconType fallback) {
        if (ctx != null) {
            try {
                android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(ctx, drawableRes);
                if (drawable != null) {
                    drawable.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
                    drawable.draw(c);
                    return;
                }
            } catch (Exception ignored) {}
        }
        switch (fallback) {
            case THERMOMETER: drawThermometerIcon(c, x, y, size, S, paint); break;
            case DROPLET: drawDropletIcon(c, x, y, size, S, paint); break;
            case RAIN: drawRainIcon(c, x, y, size, paint); break;
        }
    }

    private static void drawThermometerIcon(Canvas canvas, float x, float y, float size, float S, Paint paint) {
        Paint p = new Paint(paint); p.setStyle(Paint.Style.FILL);
        float cx = x + size * 0.5f;
        float cy = y + size * 0.70f;
        float rBulb = size * 0.20f;
        float wStem = size * 0.08f;
        float topStem = y + size * 0.10f;
        float botStem = cy - rBulb * 0.5f;
        Path path = new Path();
        path.arcTo(new RectF(cx - wStem, topStem, cx + wStem, topStem + wStem * 2f), 180, 180, false);
        path.lineTo(cx + wStem, botStem);
        path.arcTo(new RectF(cx - rBulb, cy - rBulb, cx + rBulb, cy + rBulb), -120, 300, false);
        path.close();
        canvas.drawPath(path, p);
    }

    private static void drawSunIcon(Canvas canvas, float x, float y, float size, Paint paint) {
        Paint p = new Paint(paint); p.setStyle(Paint.Style.FILL);
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float coreR = size * 0.22f;
        canvas.drawCircle(cx, cy, coreR, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, size * 0.07f));
        p.setStrokeCap(Paint.Cap.ROUND);
        float rayInner = coreR * 1.35f;
        float rayOuter = size * 0.42f;
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0;
            canvas.drawLine(cx + (float) (Math.cos(ang) * rayInner), cy + (float) (Math.sin(ang) * rayInner),
                    cx + (float) (Math.cos(ang) * rayOuter), cy + (float) (Math.sin(ang) * rayOuter), p);
        }
    }

    private static void drawDropletIcon(Canvas canvas, float x, float y, float size, float S, Paint paint) {
        Paint p = new Paint(paint); p.setStyle(Paint.Style.FILL);
        float cx = x + size * 0.5f;
        float cy = y + size * 0.65f;
        float r = size * 0.24f;
        Path path = new Path();
        path.moveTo(cx, y + size * 0.15f);
        path.cubicTo(cx + r * 0.8f, cy - r * 0.5f, cx + r, cy - r * 0.2f, cx + r, cy);
        path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), 0, 180, false);
        path.cubicTo(cx - r, cy - r * 0.2f, cx - r * 0.8f, cy - r * 0.5f, cx, y + size * 0.15f);
        path.close();
        canvas.drawPath(path, p);
    }

    private static void drawRainIcon(Canvas canvas, float x, float y, float size, Paint paint) {
        Paint p = new Paint(paint); p.setStyle(Paint.Style.FILL);
        float cloudW = size * 0.82f;
        float cloudH = size * 0.34f;
        float cloudLeft = x + (size - cloudW) / 2f;
        float cloudTop = y + size * 0.12f;
        canvas.drawRoundRect(cloudLeft, cloudTop, cloudLeft + cloudW, cloudTop + cloudH, cloudH / 2f, cloudH / 2f, p);
        p.setStrokeWidth(Math.max(1.5f, size * 0.08f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        float dropTop = cloudTop + cloudH + size * 0.08f;
        float dropBot = y + size * 0.88f;
        for (int i = 0; i < 3; i++) {
            float dropX = cloudLeft + cloudW * (0.28f + i * 0.22f);
            canvas.drawLine(dropX, dropTop, dropX, dropBot, p);
        }
    }
}
