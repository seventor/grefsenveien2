package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DetailDashboardView extends FrameLayout {

    private static final long UPDATE_INTERVAL_MS = 60_000L;
    private static final int BG_COLOR = Color.parseColor("#111318");
    private static final int WIDGET_GAP_DP = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            requestData(true);
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Nullable private DetailDashboardData data;
    private boolean fetchInProgress;
    private boolean fetchFailed;

    private ScrollView scrollView;
    private LinearLayout widgetContainer;

    private WidgetPanel panelOutdoorTemp;
    private WidgetPanel panelRain12w;
    private WidgetPanel panelNow;
    private WidgetPanel panelLightning;
    private WidgetPanel panelTempMinMax60d;
    private WidgetPanel panelSoil;
    private WidgetPanel panelWeatherCam;
    private WidgetPanel panelYardCam;
    private WidgetPanel panelRoomGrid;
    private WidgetPanel panelSunPath;
    private WidgetPanel panelMailboxCam;

    private View loadingView;

    public DetailDashboardView(Context context) {
        super(context);
        init(context);
    }

    public DetailDashboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DetailDashboardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(BG_COLOR);

        scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);

        widgetContainer = new LinearLayout(context);
        widgetContainer.setOrientation(LinearLayout.VERTICAL);
        int padPx = dpToPx(context, WIDGET_GAP_DP);
        widgetContainer.setPadding(padPx, padPx, padPx, padPx);

        int gapPx = dpToPx(context, WIDGET_GAP_DP);
        int chartHeightPx = dpToPx(context, 240);
        int cameraHeightPx = dpToPx(context, 240);
        int roomGridHeightPx = dpToPx(context, 280);
        int sunHeightPx = dpToPx(context, 240);

        panelOutdoorTemp = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.OUTDOOR_TEMP);
        panelRain12w = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.RAIN_12W);
        panelNow = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.NOW);
        panelLightning = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.LIGHTNING);
        panelTempMinMax60d = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.TEMP_MINMAX_60D);
        panelSoil = addWidgetPanel(context, chartHeightPx, gapPx, WidgetType.SOIL);
        panelWeatherCam = addWidgetPanel(context, cameraHeightPx, gapPx, WidgetType.WEATHER_CAM);
        panelYardCam = addWidgetPanel(context, cameraHeightPx, gapPx, WidgetType.YARD_CAM);
        panelRoomGrid = addWidgetPanel(context, roomGridHeightPx, gapPx, WidgetType.ROOM_GRID);
        panelSunPath = addWidgetPanel(context, sunHeightPx, gapPx, WidgetType.SUN_PATH);
        panelMailboxCam = addWidgetPanel(context, cameraHeightPx, 0, WidgetType.MAILBOX_CAM);

        loadingView = new LoadingOverlay(context);
        loadingView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scrollView.addView(widgetContainer);
        addView(scrollView);
        addView(loadingView);

        setWidgetVisibility(false);
    }

    private WidgetPanel addWidgetPanel(Context context, int heightPx, int bottomMarginPx, WidgetType type) {
        WidgetPanel panel = new WidgetPanel(context, type);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
        lp.bottomMargin = bottomMarginPx;
        panel.setLayoutParams(lp);
        widgetContainer.addView(panel);
        return panel;
    }

    private void setWidgetVisibility(boolean visible) {
        scrollView.setVisibility(visible ? VISIBLE : GONE);
        loadingView.setVisibility(visible ? GONE : VISIBLE);
    }

    void startUpdating() {
        handler.removeCallbacks(updater);
        requestData(false);
        handler.post(updater);
    }

    void stopUpdating() {
        handler.removeCallbacks(updater);
    }

    private void requestData(boolean forceRefresh) {
        if (fetchInProgress) return;
        if (!forceRefresh && data != null) return;

        fetchInProgress = true;
        fetchFailed = false;
        if (data == null) {
            setWidgetVisibility(false);
            loadingView.invalidate();
        }

        DetailDashboardFetcher.fetch(getContext(), new DetailDashboardFetcher.Callback() {
            @Override
            public void onDataReady(@NonNull DetailDashboardData newData) {
                handler.post(() -> applyData(newData));
            }

            @Override
            public void onError() {
                handler.post(() -> applyFetchFailed());
            }
        });
    }

    private void applyData(@NonNull DetailDashboardData newData) {
        data = newData;
        fetchInProgress = false;
        fetchFailed = false;
        setWidgetVisibility(true);
        invalidateAllPanels();
    }

    private void applyFetchFailed() {
        fetchInProgress = false;
        fetchFailed = true;
        if (data == null) {
            loadingView.invalidate();
        }
    }

    private void invalidateAllPanels() {
        panelOutdoorTemp.invalidate();
        panelRain12w.invalidate();
        panelNow.invalidate();
        panelLightning.invalidate();
        panelTempMinMax60d.invalidate();
        panelSoil.invalidate();
        panelWeatherCam.invalidate();
        panelYardCam.invalidate();
        panelRoomGrid.invalidate();
        panelSunPath.invalidate();
        panelMailboxCam.invalidate();
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // Widget types
    // =========================================================================

    private enum WidgetType {
        OUTDOOR_TEMP, RAIN_12W, NOW, LIGHTNING, TEMP_MINMAX_60D, SOIL,
        WEATHER_CAM, YARD_CAM, ROOM_GRID, SUN_PATH, MAILBOX_CAM
    }

    // =========================================================================
    // WidgetPanel – draws one widget via the renderer
    // =========================================================================

    private final class WidgetPanel extends View {

        private final WidgetType type;

        WidgetPanel(Context context, WidgetType type) {
            super(context);
            this.type = type;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            DetailDashboardData d = data;
            if (d == null) return;
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            float S = w / 420f;

            switch (type) {
                case OUTDOOR_TEMP:
                    DetailDashboardRenderer.drawOutdoorTemp(canvas, d, w, h);
                    break;
                case RAIN_12W:
                    DetailDashboardRenderer.drawRain12w(canvas, d, w, h);
                    break;
                case NOW:
                    DetailDashboardRenderer.drawNow(canvas, d, w, h, getContext());
                    break;
                case LIGHTNING:
                    DetailDashboardRenderer.drawLightning(canvas, d, w, h);
                    break;
                case TEMP_MINMAX_60D:
                    DetailDashboardRenderer.drawTempMinMax60d(canvas, d, w, h);
                    break;
                case SOIL:
                    DetailDashboardRenderer.drawSoil(canvas, d, w, h);
                    break;
                case WEATHER_CAM:
                    DetailDashboardRenderer.drawCamera(canvas, d.weatherBitmap, "V\u00c6R",
                            d.weatherTimestamp, w, h, false, S,
                            d.weatherBatteryPercent, d.weatherBatteryMinPercent, d.weatherBatteryMaxPercent);
                    break;
                case YARD_CAM:
                    DetailDashboardRenderer.drawCamera(canvas, d.yardBitmap, "G\u00c5RDSPLASSEN",
                            d.yardTimestamp, w, h, true, S, -1, -1, -1);
                    break;
                case ROOM_GRID:
                    DetailDashboardRenderer.drawRoomGrid(canvas, d, w, h);
                    break;
                case SUN_PATH:
                    DetailDashboardRenderer.drawSunPath(canvas, d, w, h);
                    break;
                case MAILBOX_CAM:
                    DetailDashboardRenderer.drawCamera(canvas, d.mailboxBitmap, "POSTKASSEN",
                            d.mailboxTimestamp, w, h, false, S, -1, -1, -1);
                    break;
            }
        }
    }

    // =========================================================================
    // Loading overlay
    // =========================================================================

    private final class LoadingOverlay extends View {

        LoadingOverlay(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            canvas.drawColor(BG_COLOR);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#8E9AA8"));
            textPaint.setTextSize(Math.max(18f, w * 0.04f));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);

            String msg;
            if (fetchFailed && data == null) {
                msg = "Kunne ikke hente detaljer";
            } else {
                msg = "Henter detaljer...";
            }
            canvas.drawText(msg, w / 2f, h / 2f, textPaint);
        }
    }
}
