package com.pixelspore.grefsenveien;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

final class DetailDashboardData {

    static final class DailyTempRange {
        final int dayOfMonth;
        final float min;
        final float max;
        final boolean hasData;

        DailyTempRange(int dayOfMonth, float min, float max, boolean hasData) {
            this.dayOfMonth = dayOfMonth;
            this.min = min;
            this.max = max;
            this.hasData = hasData;
        }
    }

    static final class LightningEvent {
        final long timeMs;
        final float distanceKm;

        LightningEvent(long timeMs, float distanceKm) {
            this.timeMs = timeMs;
            this.distanceKm = distanceKm;
        }
    }

    // Outdoor temperature curves (hour-of-day vs °C)
    List<float[]> todayTempPoints = new ArrayList<>();
    List<float[]> yesterdayTempPoints = new ArrayList<>();
    List<float[]> twoDaysAgoTempPoints = new ArrayList<>();
    List<float[]> sixtyDayAvgTempPoints = new ArrayList<>();

    float todayTempMin = 14f;
    float todayTempMax = 16f;

    // Rain
    final float[] rainByWeek = new float[12];
    float todayRainMm = 0f;
    float[] hourlyRain = new float[24];

    // Soil
    List<float[]> soilPoints = new ArrayList<>();

    // Room temperatures
    float valJonatan = 23.3f;
    float valLoftsgang = 25.5f;
    float valKontor = 26.1f;
    float valBad = 24.1f;
    float valVinterhage = 30.0f;
    float valKjokken = 23.2f;
    float valLiteBad = 23.2f;
    float valMats = 22.1f;
    float valStue = 25.1f;
    float valGang3 = 23.5f;
    float valSoverom = 22.2f;
    float valGang4 = 22.6f;
    float valVaskerom = 23.7f;

    // Motion detection times
    long valStueMotionTime = 0L;
    long valLoftsgangMotionTime = 0L;
    long valGang4MotionTime = 0L;
    long valJonatanMotionTime = 0L;
    long valBadMotionTime = 0L;
    long valVaskeromMotionTime = 0L;

    // Current weather readings
    float valHumidity = 45f;
    float valSolarRad = 0f;
    float valSolarEnergy24h = 0f;
    float valRainRate = 0f;

    // Sun
    float sunAzimuth = 180f;
    float sunElevation = 0f;
    long sunNextRisingMs = 0L;
    long sunNextSettingMs = 0L;

    // Lightning
    List<LightningEvent> lightningEvents7d = new ArrayList<>();
    float lastLightningDistanceKm = -1f;
    float nearestLightningDistanceKm = -1f;
    int lightningCount7d = 0;
    long lightningWindowStartMs = 0L;
    long lightningWindowEndMs = 0L;

    // Daily temp min/max (60 days)
    List<DailyTempRange> tempMinMax60d = new ArrayList<>();
    float temp60dPeriodMin = Float.NaN;
    float temp60dPeriodMax = Float.NaN;

    // Camera bitmaps + timestamps
    @Nullable Bitmap weatherBitmap = null;
    String weatherTimestamp = "";
    @Nullable Bitmap yardBitmap = null;
    String yardTimestamp = "";
    @Nullable Bitmap mailboxBitmap = null;
    String mailboxTimestamp = "";

    // Weather camera battery
    int weatherBatteryPercent = -1;
    int weatherBatteryMinPercent = -1;
    int weatherBatteryMaxPercent = -1;

    // Current outdoor temp (convenience, derived from todayTempPoints)
    float currentOutdoorTemp = 15f;
}
