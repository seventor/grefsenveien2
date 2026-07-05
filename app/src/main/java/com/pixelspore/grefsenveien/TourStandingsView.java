package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class TourStandingsView extends View {

    private static final long UPDATE_INTERVAL_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            requestStandings(true);
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Nullable
    private TourStandingsData standingsData;
    private boolean fetchInProgress;
    private boolean fetchFailed;

    public TourStandingsView(Context context) {
        super(context);
        init();
    }

    public TourStandingsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TourStandingsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(0xFF0D1117);
    }

    void startUpdating() {
        handler.removeCallbacks(updater);
        requestStandings(false);
        handler.post(updater);
    }

    void stopUpdating() {
        handler.removeCallbacks(updater);
    }

    private void requestStandings(boolean forceRefresh) {
        if (fetchInProgress) {
            return;
        }
        if (!forceRefresh && standingsData != null) {
            return;
        }

        fetchInProgress = true;
        fetchFailed = false;
        invalidate();

        TourStandingsFetcher.fetch(new TourStandingsFetcher.Callback() {
            @Override
            public void onDataReady(@NonNull TourStandingsData data) {
                handler.post(() -> applyStandings(data));
            }

            @Override
            public void onError() {
                handler.post(() -> applyFetchFailed());
            }
        });
    }

    private void applyStandings(@NonNull TourStandingsData data) {
        standingsData = data;
        fetchInProgress = false;
        fetchFailed = false;
        invalidate();
    }

    private void applyFetchFailed() {
        fetchInProgress = false;
        fetchFailed = true;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }

        if (standingsData != null) {
            TourStandingsRenderer.draw(canvas, standingsData, 0f, 0f, w, h);
            return;
        }

        Paint bg = new Paint();
        bg.setColor(0xFFFFFFFF);
        canvas.drawRect(0f, 0f, w, h, bg);

        Paint titleBar = new Paint();
        titleBar.setColor(0xFFFFD200);
        float titleBarH = Math.max(48f, h * 0.08f);
        canvas.drawRect(0f, 0f, w, titleBarH, titleBar);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFF000000);
        text.setTextSize(Math.max(28f, w * 0.04f));
        String msg = fetchInProgress ? "Henter sammenlagt..." : "Kunne ikke hente Tour de France";
        canvas.drawText(msg, 24f, h * 0.5f, text);
    }
}
