package com.pixelspore.grefsenveien;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class TourLiveView extends LinearLayout {

    private static final long UPDATE_INTERVAL_MS = 60_000L;
    private static final float LEFT_PANEL_WEIGHT = 0.42f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            requestLiveData(true);
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    private PanelView leftPanel;
    private ScrollView scrollView;
    private PanelView rightPanel;

    @Nullable
    private TourLiveData liveData;
    private boolean fetchInProgress;
    private boolean fetchFailed;
    private int layoutWidth;

    public TourLiveView(Context context) {
        super(context);
        init(context);
    }

    public TourLiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TourLiveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setBackgroundColor(0xFFFFFFFF);

        leftPanel = new PanelView(context, PanelSide.LEFT);
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF3F3F3);
        scrollView.setVerticalScrollBarEnabled(true);
        rightPanel = new PanelView(context, PanelSide.RIGHT);
        scrollView.addView(rightPanel, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addView(leftPanel, new LayoutParams(0, LayoutParams.MATCH_PARENT, LEFT_PANEL_WEIGHT));
        addView(scrollView, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f - LEFT_PANEL_WEIGHT));
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
        refreshPanels();

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
        refreshPanels();
    }

    private void applyFetchFailed() {
        fetchInProgress = false;
        fetchFailed = true;
        refreshPanels();
    }

    private void refreshPanels() {
        leftPanel.setState(liveData, layoutWidth, scrollView.getHeight(), fetchInProgress, fetchFailed);
        rightPanel.setState(liveData, layoutWidth, scrollView.getHeight(), fetchInProgress, fetchFailed);
        leftPanel.invalidate();
        rightPanel.requestLayout();
        rightPanel.invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutWidth = w;
        post(this::refreshPanels);
    }

    private enum PanelSide {
        LEFT,
        RIGHT
    }

    private final class PanelView extends View {
        private final PanelSide side;

        @Nullable
        private TourLiveData data;
        private int fullLayoutWidth;
        private int viewportHeight;
        private boolean loading;
        private boolean failed;

        PanelView(Context context, PanelSide side) {
            super(context);
            this.side = side;
            if (side == PanelSide.LEFT) {
                setBackgroundColor(0xFFFFFFFF);
            } else {
                setBackgroundColor(0xFFF3F3F3);
            }
        }

        void setState(@Nullable TourLiveData data, int fullLayoutWidth, int viewportHeight,
                boolean loading, boolean failed) {
            this.data = data;
            this.fullLayoutWidth = fullLayoutWidth;
            this.viewportHeight = viewportHeight > 0 ? viewportHeight : getHeight();
            this.loading = loading;
            this.failed = failed;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (side == PanelSide.RIGHT && data != null && fullLayoutWidth > 0) {
                int viewport = viewportHeight > 0 ? viewportHeight : MeasureSpec.getSize(heightMeasureSpec);
                boolean showMissing = TourLiveRenderer.shouldShowMissingTelemetry(
                        data, width, viewport, fullLayoutWidth, TourLiveRenderer.LayoutMode.PHONE);
                float contentHeight = TourLiveRenderer.measureRightPanelContentHeight(
                        data, width, fullLayoutWidth, TourLiveRenderer.LayoutMode.PHONE, showMissing);
                setMeasuredDimension(width, (int) Math.ceil(contentHeight));
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) {
                return;
            }

            if (data != null && fullLayoutWidth > 0) {
                if (side == PanelSide.LEFT) {
                    TourLiveRenderer.drawLeftPanelOnly(canvas, data, 0f, 0f, w, h,
                            fullLayoutWidth, TourLiveRenderer.LayoutMode.PHONE);
                } else {
                    int viewport = viewportHeight > 0 ? viewportHeight : (int) h;
                    boolean showMissing = TourLiveRenderer.shouldShowMissingTelemetry(
                            data, w, viewport, fullLayoutWidth, TourLiveRenderer.LayoutMode.PHONE);
                    TourLiveRenderer.drawRightPanelOnly(canvas, data, 0f, 0f, w, h,
                            fullLayoutWidth, TourLiveRenderer.LayoutMode.PHONE,
                            TourLiveRenderer.RightPanelOptions.forPhone(showMissing));
                }
                return;
            }

            if (side == PanelSide.LEFT) {
                canvas.drawColor(0xFFFFFFFF);
                Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
                text.setColor(0xFF000000);
                text.setTextSize(Math.max(28f, w * 0.06f));
                String msg = loading ? "Henter live-data..." : "Kunne ikke hente Tour live";
                canvas.drawText(msg, 24f, h * 0.5f, text);
            } else {
                canvas.drawColor(0xFFF3F3F3);
            }
        }
    }
}
