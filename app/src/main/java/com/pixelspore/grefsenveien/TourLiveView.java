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

public final class TourLiveView extends View {

    private static final long UPDATE_INTERVAL_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            requestLiveData(true);
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Nullable
    private TourLiveData liveData;
    private boolean fetchInProgress;
    private boolean fetchFailed;

    public TourLiveView(Context context) {
        super(context);
        init();
    }

    public TourLiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TourLiveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(0xFFFFFFFF);
    }

    public void startUpdating() {
        handler.removeCallbacks(updater);
        requestLiveData(false);
        handler.post(updater);
    }

    public void stopUpdating() {
        handler.removeCallbacks(updater);
    }

    private void requestLiveData(boolean forceRefresh) {
        if (fetchInProgress) {
            return;
        }
        if (!forceRefresh && liveData != null) {
            return;
        }

        fetchInProgress = true;
        fetchFailed = false;
        invalidate();

        TourLiveFetcher.fetch(new TourLiveFetcher.Callback() {
            @Override
            public void onDataReady(@NonNull TourLiveData data) {
                handler.post(() -> applyLiveData(data));
            }

            @Override
            public void onError() {
                handler.post(() -> applyFetchFailed());
            }
        });
    }

    private void applyLiveData(@NonNull TourLiveData data) {
        liveData = data;
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

        if (liveData != null) {
            TourLiveRenderer.draw(canvas, liveData, 0f, 0f, w, h, TourLiveRenderer.LayoutMode.PHONE);
            return;
        }

        canvas.drawColor(0xFFFFFFFF);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFF000000);
        text.setTextSize(Math.max(28f, w * 0.04f));
        String msg = fetchInProgress ? "Henter live-data..." : "Kunne ikke hente Tour live";
        canvas.drawText(msg, 24f, h * 0.5f, text);
    }
}
