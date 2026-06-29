package com.pixelspore.grefsenveien;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.SharedPreferences;
import android.content.Context;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.AppManager;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.navigation.model.NavigationTemplate;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import androidx.car.app.model.Template;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.car.app.CarToast;

import androidx.core.graphics.drawable.IconCompat;
import androidx.car.app.model.CarIcon;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import android.graphics.Path;
import android.graphics.DashPathEffect;
import android.graphics.Typeface;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainCarScreen extends Screen implements SurfaceCallback {

    private static final String MAILBOX_IMAGE_URL = BuildConfig.S3_MAILBOX_IMAGE_URL;

    private enum ViewMode { YARD, MAILBOX, HOME, SETTINGS }
    private enum ImageTarget { YARD, MAILBOX, WEATHER }
    private ViewMode currentMode = ViewMode.YARD;

    private String garageStatus = "";
    private String gateStatus = "";
    private Bitmap cameraBitmap = null;
    private String imageTimestamp = "";
    private Bitmap mailboxBitmap = null;
    private String mailboxTimestamp = "";
    private Bitmap weatherBitmap = null;
    private String weatherTimestamp = "";
    private Bitmap homeBitmap = null;
    private String homeTimestamp = "";
    private SurfaceContainer mSurfaceContainer;
    private Rect mVisibleArea;
    private int lastCanvasWidth;
    private int lastCanvasHeight;
    private int lastVisibleWidth;
    private int lastVisibleHeight;
    private int lastVisibleLeft;
    private int lastVisibleTop;
    private int detailPageVersion = 1;
    private android.graphics.RectF settingsVersion1Bounds = new android.graphics.RectF();
    private android.graphics.RectF settingsVersion2Bounds = new android.graphics.RectF();
    private Bitmap vgNoBitmap = null;
    private boolean vgNoCaptureInProgress = false;

    private float valJonatan = 23.3f;
    private float valLoftsgang = 25.5f;
    private float valKontor = 26.1f;
    private float valBad = 24.1f;
    private float valVinterhage = 30.0f;
    private float valKjokken = 23.2f;
    private float valLiteBad = 23.2f;
    private float valMats = 22.1f;
    private float valStue = 25.1f;
    private float valGang3 = 23.5f;
    private float valSoverom = 22.2f;
    private float valGang4 = 22.6f;
    private float valVaskerom = 23.7f;
    private long valStueMotionTime = 0L;
    private long valLoftsgangMotionTime = 0L;
    private long valGang4MotionTime = 0L;
    private long valJonatanMotionTime = 0L;
    private long valBadMotionTime = 0L;
    private long valVaskeromMotionTime = 0L;

    private float valSolarHourly = 0f;
    private float[] valSolar24h = new float[24];
    private float valHumidity = 45f;

    private static final class LightningEvent {
        final long timeMs;
        final float distanceKm;

        LightningEvent(long timeMs, float distanceKm) {
            this.timeMs = timeMs;
            this.distanceKm = distanceKm;
        }
    }

    private List<LightningEvent> lightningEvents7d = new ArrayList<>();
    private float lastLightningDistanceKm = -1f;
    private float nearestLightningDistanceKm = -1f;
    private int lightningCount7d = 0;
    private long lightningWindowStartMs = 0L;
    private long lightningWindowEndMs = 0L;

    private final android.os.Handler mUpdateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mImageUpdater = new Runnable() {
        @Override
        public void run() {
            fetchImageFromUrl(BuildConfig.S3_IMAGE_URL, ImageTarget.YARD);
            mUpdateHandler.postDelayed(this, 60000); // 60 sekunder
        }
    };

    private final Runnable mWeatherUpdater = new Runnable() {
        @Override
        public void run() {
            fetchImageFromUrl(BuildConfig.WEATHER_CAMERA_URL, ImageTarget.WEATHER);
            mUpdateHandler.postDelayed(this, 60000);
        }
    };

    private final Runnable mDoorbellUpdater = new Runnable() {
        @Override
        public void run() {
            triggerDoorbellImage();
            mUpdateHandler.postDelayed(this, 120000); // 120 sekunder
        }
    };

    private final Runnable mMailboxUpdater = new Runnable() {
        @Override
        public void run() {
            fetchImageFromUrl(MAILBOX_IMAGE_URL, ImageTarget.MAILBOX);
            mUpdateHandler.postDelayed(this, 120000);
        }
    };

    private final Runnable mHomeUpdater = new Runnable() {
        @Override
        public void run() {
            fetchHomeAssistantData();
            mUpdateHandler.postDelayed(this, 60000); // 60 sekunder
        }
    };

    private final Runnable mVgNoUpdater = new Runnable() {
        @Override
        public void run() {
            if (currentMode == ViewMode.SETTINGS) {
                requestVgNoScreenshot(true);
            }
            mUpdateHandler.postDelayed(this, 60_000);
        }
    };

    public MainCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        
        // Registrer oss for å få tilgang til tegne-lerretet i Android Auto
        carContext.getCarService(AppManager.class).setSurfaceCallback(this);
        
        // Gjenopprett siste valgte ViewMode
        try {
            android.content.SharedPreferences prefs = carContext.getSharedPreferences("GrefsenveienPrefs", android.content.Context.MODE_PRIVATE);
            String savedMode = prefs.getString("lastViewMode", null);
            if ("INFO".equals(savedMode)) {
                savedMode = "SETTINGS";
            }
            if (savedMode != null) {
                currentMode = ViewMode.valueOf(savedMode);
            }
            detailPageVersion = prefs.getInt("detailPageVersion", 1);
            if (detailPageVersion != 1 && detailPageVersion != 2) {
                detailPageVersion = 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        mSurfaceContainer = surfaceContainer;
        updateScreenInfoCache();
        drawCameraImage();
        
        // Start automatisk oppdatering hvert minutt
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.post(mImageUpdater);

        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
        mUpdateHandler.post(mDoorbellUpdater);

        mUpdateHandler.removeCallbacks(mMailboxUpdater);
        mUpdateHandler.post(mMailboxUpdater);

        mUpdateHandler.removeCallbacks(mWeatherUpdater);
        mUpdateHandler.post(mWeatherUpdater);

        mUpdateHandler.removeCallbacks(mHomeUpdater);
        mUpdateHandler.post(mHomeUpdater);

        mUpdateHandler.removeCallbacks(mVgNoUpdater);
        mUpdateHandler.post(mVgNoUpdater);

        if (currentMode == ViewMode.SETTINGS) {
            requestVgNoScreenshot(false);
        }
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        mVisibleArea = visibleArea;
        updateScreenInfoCache();
        drawCameraImage();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        mSurfaceContainer = null;
        
        // Stopp automatisk oppdatering når tegneflaten forsvinner
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
        mUpdateHandler.removeCallbacks(mMailboxUpdater);
        mUpdateHandler.removeCallbacks(mWeatherUpdater);
        mUpdateHandler.removeCallbacks(mHomeUpdater);
        mUpdateHandler.removeCallbacks(mVgNoUpdater);
    }

    private void drawCameraImage() {
        if (mSurfaceContainer == null || mSurfaceContainer.getSurface() == null) return;

        if (currentMode == ViewMode.SETTINGS) {
            try {
                Canvas canvas = mSurfaceContainer.getSurface().lockCanvas(null);
                if (canvas != null) {
                    drawSettingsScreen(canvas);
                    mSurfaceContainer.getSurface().unlockCanvasAndPost(canvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (currentMode == ViewMode.MAILBOX) {
            try {
                Canvas canvas = mSurfaceContainer.getSurface().lockCanvas(null);
                if (canvas != null) {
                    canvas.drawColor(android.graphics.Color.BLACK);
                    drawMailboxComposite(canvas);
                    mSurfaceContainer.getSurface().unlockCanvasAndPost(canvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        Bitmap displayBitmap = currentMode == ViewMode.HOME ? homeBitmap : cameraBitmap;
        String displayTimestamp = currentMode == ViewMode.HOME ? homeTimestamp : imageTimestamp;
        if (displayBitmap == null) return;

        try {
            Canvas canvas = mSurfaceContainer.getSurface().lockCanvas(null);
            if (canvas != null) {
                canvas.drawColor(android.graphics.Color.BLACK);

                // Tegn alltid bakgrunnsbildet over hele skjermen (full canvas)
                int drawWidth = canvas.getWidth();
                int drawHeight = canvas.getHeight();
                int offsetX = 0;
                int offsetY = 0;

                if (currentMode == ViewMode.HOME) {
                    Rect destRect = new Rect(0, 0, displayBitmap.getWidth(), displayBitmap.getHeight());
                    Paint bmpPaint = new Paint();
                    bmpPaint.setFilterBitmap(false);
                    bmpPaint.setAntiAlias(false);
                    canvas.drawBitmap(displayBitmap, null, destRect, bmpPaint);
                } else {
                    float scaleW = (float) drawWidth / displayBitmap.getWidth();
                    float scaleH = (float) drawHeight / displayBitmap.getHeight();
                    float scale = Math.max(scaleW, scaleH);

                    int scaledWidth  = Math.round(displayBitmap.getWidth()  * scale);
                    int scaledHeight = Math.round(displayBitmap.getHeight() * scale);
                    int left = offsetX + (drawWidth  - scaledWidth)  / 2;
                    int top  = offsetY + (drawHeight - scaledHeight) / 2;

                    Rect destRect = new Rect(left, top, left + scaledWidth, top + scaledHeight);
                    canvas.drawBitmap(displayBitmap, null, destRect, new Paint());
                }

                if (displayTimestamp != null && !displayTimestamp.isEmpty()) {
                    if (currentMode == ViewMode.HOME) {
                        float homeS = drawWidth / 1440f;
                        float hdrTxt = Math.max(17f, 23f * homeS);
                        float hdrOff = Math.max(24f, 32f * homeS);
                        Paint textPaint = new Paint();
                        textPaint.setColor(android.graphics.Color.WHITE);
                        textPaint.setTextSize(hdrTxt);
                        textPaint.setAntiAlias(true);
                        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                        float textX = Math.max(14f, 20f * homeS);
                        canvas.drawText(displayTimestamp, textX, hdrOff, textPaint);
                    } else {
                        drawTimestampBadge(canvas, displayTimestamp, 0f, 0f,
                                drawWidth, drawHeight, getCameraTabTimestampTextSize(drawWidth));
                    }
                }

                mSurfaceContainer.getSurface().unlockCanvasAndPost(canvas);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(float x, float y) {
        if (currentMode != ViewMode.SETTINGS) return;
        if (settingsVersion1Bounds.contains(x, y)) {
            selectDetailPageVersion(1);
        } else if (settingsVersion2Bounds.contains(x, y)) {
            selectDetailPageVersion(2);
        }
    }

    private void selectDetailPageVersion(int version) {
        if (detailPageVersion == version) return;
        setDetailPageVersion(version);
        CarToast.makeText(getCarContext(), "Detaljside: Versjon " + version, CarToast.LENGTH_SHORT).show();
        mUpdateHandler.removeCallbacks(mHomeUpdater);
        mUpdateHandler.post(mHomeUpdater);
        drawCameraImage();
        invalidate();
    }

    private void drawSettingsScreen(Canvas canvas) {
        updateScreenInfoCache();
        int cW = canvas.getWidth();
        int cH = canvas.getHeight();
        int vW = mVisibleArea != null ? mVisibleArea.width() : cW;
        int vH = mVisibleArea != null ? mVisibleArea.height() : cH;
        int vL = mVisibleArea != null ? mVisibleArea.left : 0;
        int vT = mVisibleArea != null ? mVisibleArea.top : 0;

        canvas.drawColor(android.graphics.Color.parseColor("#0D1117"));

        float splitX = cW * 0.5f;
        drawVgNoPanel(canvas, 0f, 0f, splitX, cH);

        Paint dividerPaint = new Paint();
        dividerPaint.setColor(android.graphics.Color.parseColor("#222633"));
        dividerPaint.setStrokeWidth(2f);
        canvas.drawLine(splitX, 0f, splitX, cH, dividerPaint);

        float rightPad = 20f;
        float x = splitX + rightPad;
        float y = vT + 28f;
        float rightWidth = cW - splitX - rightPad * 2f;

        Paint titlePaint = new Paint();
        titlePaint.setAntiAlias(true);
        titlePaint.setColor(android.graphics.Color.WHITE);
        titlePaint.setTextSize(30f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Innstillinger", x, y, titlePaint);
        y += 36f;

        Paint sectionPaint = new Paint();
        sectionPaint.setAntiAlias(true);
        sectionPaint.setColor(android.graphics.Color.parseColor("#CCCCCC"));
        sectionPaint.setTextSize(24f);
        sectionPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Detaljside", x, y, sectionPaint);
        y += 30f;

        float optionGap = 10f;
        float optionW = Math.min(200f, (rightWidth - optionGap) / 2f);
        float optionH = 40f;
        settingsVersion1Bounds.set(x, y, x + optionW, y + optionH);
        settingsVersion2Bounds.set(x + optionW + optionGap, y, x + optionW + optionGap + optionW, y + optionH);
        drawSettingsOption(canvas, settingsVersion1Bounds, "Versjon 1", detailPageVersion == 1);
        drawSettingsOption(canvas, settingsVersion2Bounds, "Versjon 2", detailPageVersion == 2);
        y += optionH + 32f;

        canvas.drawText("Skjerminfo", x, y, sectionPaint);
        y += 28f;

        Paint linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setColor(android.graphics.Color.parseColor("#CCCCCC"));
        linePaint.setTextSize(22f);
        String[] lines = {
                "Canvas:  " + cW + " \u00d7 " + cH + " px",
                "Synlig:  " + vW + " \u00d7 " + vH + " px",
                "Offset:  " + vL + ", " + vT,
        };
        for (String line : lines) {
            canvas.drawText(line, x, y, linePaint);
            y += 28f;
        }
    }

    private void drawVgNoPanel(Canvas canvas, float left, float top, float right, float bottom) {
        Paint bg = new Paint();
        bg.setColor(android.graphics.Color.parseColor("#10141C"));
        canvas.drawRect(left, top, right, bottom, bg);

        if (vgNoBitmap == null || vgNoBitmap.isRecycled()) {
            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setColor(android.graphics.Color.parseColor("#8E9AA8"));
            textPaint.setTextSize(36f);
            String msg = vgNoCaptureInProgress ? "Laster VG.no..." : "VG.no utilgjengelig";
            canvas.drawText(msg, left + 28f, top + (bottom - top) * 0.5f, textPaint);
            return;
        }

        float panelW = right - left;
        float panelH = bottom - top;
        float bmpW = vgNoBitmap.getWidth();
        float bmpH = vgNoBitmap.getHeight();
        if (bmpW <= 0f || bmpH <= 0f) return;

        float scale = panelW / bmpW;
        float srcH = Math.min(bmpH, panelH / scale);
        android.graphics.Rect src = new android.graphics.Rect(0, 0, (int) bmpW, (int) srcH);
        android.graphics.RectF dst = new android.graphics.RectF(left, top, right, top + srcH * scale);
        Paint bmpPaint = new Paint();
        bmpPaint.setFilterBitmap(false);
        bmpPaint.setAntiAlias(false);
        canvas.drawBitmap(vgNoBitmap, src, dst, bmpPaint);
    }

    private void requestVgNoScreenshot(boolean forceRefresh) {
        if (vgNoCaptureInProgress) return;
        if (!forceRefresh && vgNoBitmap != null && !vgNoBitmap.isRecycled()) return;

        vgNoCaptureInProgress = true;
        int captureWidth = Math.max(360, lastCanvasWidth > 0 ? lastCanvasWidth / 2 : 480);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        VgNoScreenshotCapturer.capture(getCarContext().getApplicationContext(), captureWidth, new VgNoScreenshotCapturer.Callback() {
            @Override
            public void onBitmapReady(@NonNull Bitmap bitmap) {
                mainHandler.post(() -> applyVgNoBitmap(bitmap));
            }

            @Override
            public void onError() {
                mainHandler.post(() -> {
                    vgNoCaptureInProgress = false;
                    refreshSettingsIfVisible();
                });
            }
        });
    }

    private void applyVgNoBitmap(@NonNull Bitmap bitmap) {
        if (vgNoBitmap != null && !vgNoBitmap.isRecycled()) {
            vgNoBitmap.recycle();
        }
        vgNoBitmap = bitmap;
        vgNoCaptureInProgress = false;
        refreshSettingsIfVisible();
    }

    private void refreshSettingsIfVisible() {
        if (currentMode != ViewMode.SETTINGS) return;
        drawCameraImage();
        invalidate();
    }

    private void drawSettingsOption(Canvas canvas, android.graphics.RectF bounds, String label, boolean selected) {
        Paint bg = new Paint();
        bg.setAntiAlias(true);
        bg.setColor(selected ? android.graphics.Color.parseColor("#1A3B82F6") : android.graphics.Color.parseColor("#151821"));
        canvas.drawRoundRect(bounds, 8f, 8f, bg);

        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(selected ? android.graphics.Color.parseColor("#3B82F6") : android.graphics.Color.parseColor("#222633"));
        canvas.drawRoundRect(bounds, 8f, 8f, stroke);

        Paint text = new Paint();
        text.setAntiAlias(true);
        text.setColor(android.graphics.Color.WHITE);
        text.setTextSize(22f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = text.getFontMetrics();
        float textY = bounds.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, bounds.left + 12f, textY, text);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return buildNavigationTemplate();
    }

    @NonNull
    private Template buildNavigationTemplate() {
        ActionStrip.Builder actionStripBuilder = new ActionStrip.Builder();
        actionStripBuilder.addAction(
            new Action.Builder()
                .setTitle("Garasje")
                .setFlags(Action.FLAG_IS_PERSISTENT)
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_material_garage_door)).build())
                .setBackgroundColor(CarColor.DEFAULT)
                .setOnClickListener(() -> makeUrlRequest("garasjen"))
                .build());
        actionStripBuilder.addAction(
            new Action.Builder()
                .setTitle("+1min")
                .setFlags(Action.FLAG_IS_PERSISTENT)
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_material_garage_door)).build())
                .setBackgroundColor(CarColor.DEFAULT)
                .setOnClickListener(() -> makeUrlRequest("garasjen", 60))
                .build());
        actionStripBuilder.addAction(
            new Action.Builder()
                .setTitle("Port")
                .setFlags(Action.FLAG_IS_PERSISTENT)
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_material_outdoor_garden)).build())
                .setBackgroundColor(CarColor.DEFAULT)
                .setOnClickListener(() -> makeUrlRequest("porten"))
                .build());
        ActionStrip actionStrip = actionStripBuilder.build();

        ActionStrip mapActionStrip = new ActionStrip.Builder()
            .addAction(
                new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                        currentMode == ViewMode.YARD ? R.drawable.ic_tab_car : R.drawable.ic_tab_car_outline)).build())
                    .setOnClickListener(() -> {
                        currentMode = ViewMode.YARD;
                        saveViewMode(ViewMode.YARD);
                        drawCameraImage();
                        invalidate();
                    })
                    .build())
            .addAction(
                new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                        currentMode == ViewMode.MAILBOX ? R.drawable.ic_tab_mailbox : R.drawable.ic_tab_mailbox_outline)).build())
                    .setOnClickListener(() -> {
                        currentMode = ViewMode.MAILBOX;
                        saveViewMode(ViewMode.MAILBOX);
                        drawCameraImage();
                        invalidate();
                    })
                    .build())
            .addAction(
                new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                        currentMode == ViewMode.HOME ? R.drawable.ic_tab_home : R.drawable.ic_tab_home_outline)).build())
                    .setOnClickListener(() -> {
                        currentMode = ViewMode.HOME;
                        saveViewMode(ViewMode.HOME);
                        drawCameraImage();
                        invalidate();
                    })
                    .build())
            .addAction(
                new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                        currentMode == ViewMode.SETTINGS ? R.drawable.ic_tab_settings : R.drawable.ic_tab_settings_outline)).build())
                    .setOnClickListener(() -> {
                        currentMode = ViewMode.SETTINGS;
                        saveViewMode(ViewMode.SETTINGS);
                        requestVgNoScreenshot(false);
                        drawCameraImage();
                        invalidate();
                    })
                    .build())
            .build();

        return new NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setMapActionStrip(mapActionStrip)
            .build();
    }

    private void updateScreenInfoCache() {
        if (mSurfaceContainer != null) {
            lastCanvasWidth = mSurfaceContainer.getWidth();
            lastCanvasHeight = mSurfaceContainer.getHeight();
        }
        if (mVisibleArea != null) {
            lastVisibleLeft = mVisibleArea.left;
            lastVisibleTop = mVisibleArea.top;
            lastVisibleWidth = mVisibleArea.width();
            lastVisibleHeight = mVisibleArea.height();
        }
    }

    private void setDetailPageVersion(int version) {
        detailPageVersion = version;
        try {
            getCarContext().getSharedPreferences("GrefsenveienPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("detailPageVersion", version)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveViewMode(ViewMode mode) {
        try {
            android.content.SharedPreferences prefs = getCarContext().getSharedPreferences("GrefsenveienPrefs", android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("lastViewMode", mode.name()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawMailboxComposite(Canvas canvas) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        float topPad = h * 0.105f;
        float gap = 6f;
        float halfW = (w - gap) / 2f;
        float tsTextSize = getCameraTabTimestampTextSize(w);

        float weatherTop = topPad;
        float weatherH = (h - topPad) * 0.2f;
        if (weatherBitmap != null && weatherBitmap.getWidth() > 0) {
            weatherH = w * ((float) weatherBitmap.getHeight() / weatherBitmap.getWidth());
        }
        float weatherBottom = weatherTop + weatherH;

        float bottomTop = weatherBottom + gap;
        float bottomBottom = h;
        float cornerRadius = Math.max(18f, w * 0.022f);

        drawMailboxImagePanel(canvas, weatherBitmap, weatherTimestamp, 0f, weatherTop, w, weatherBottom,
                true, tsTextSize, false, cornerRadius, true, true, false, false);
        drawMailboxImagePanel(canvas, cameraBitmap, imageTimestamp, 0f, bottomTop, halfW, bottomBottom,
                false, tsTextSize, true, cornerRadius, false, false, false, true);
        drawMailboxImagePanel(canvas, mailboxBitmap, mailboxTimestamp, halfW + gap, bottomTop, w, bottomBottom,
                false, tsTextSize, false, cornerRadius, false, false, true, false);
    }

    private void clipPanelWithCorners(Canvas canvas, float left, float top, float right, float bottom,
            float radius, boolean roundTopLeft, boolean roundTopRight,
            boolean roundBottomRight, boolean roundBottomLeft) {
        if (!roundTopLeft && !roundTopRight && !roundBottomRight && !roundBottomLeft) {
            canvas.clipRect(left, top, right, bottom);
            return;
        }
        float[] radii = new float[8];
        if (roundTopLeft) {
            radii[0] = radius;
            radii[1] = radius;
        }
        if (roundTopRight) {
            radii[2] = radius;
            radii[3] = radius;
        }
        if (roundBottomRight) {
            radii[4] = radius;
            radii[5] = radius;
        }
        if (roundBottomLeft) {
            radii[6] = radius;
            radii[7] = radius;
        }
        Path clipPath = new Path();
        clipPath.addRoundRect(new android.graphics.RectF(left, top, right, bottom), radii, Path.Direction.CW);
        canvas.clipPath(clipPath);
    }

    private void drawMailboxImagePanel(Canvas canvas, Bitmap bmp, String tsStr,
            float left, float top, float right, float bottom,
            boolean fitWidth, float tsTextSize, boolean yardCropOffset, float cornerRadius,
            boolean roundTopLeft, boolean roundTopRight, boolean roundBottomRight, boolean roundBottomLeft) {
        if (bmp == null) {
            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setColor(android.graphics.Color.GRAY);
            textPaint.setTextSize(28f);
            String placeholder = "Laster...";
            canvas.drawText(placeholder, left + ((right - left) - textPaint.measureText(placeholder)) / 2f,
                    top + (bottom - top) / 2f, textPaint);
            return;
        }

        canvas.save();
        clipPanelWithCorners(canvas, left, top, right, bottom, cornerRadius,
                roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);

        float cardW = right - left;
        float cardH = bottom - top;
        float bmpW = bmp.getWidth();
        float bmpH = bmp.getHeight();
        float scale;
        if (fitWidth) {
            scale = cardW / bmpW;
        } else {
            scale = Math.max(cardW / bmpW, cardH / bmpH);
            if (yardCropOffset) {
                scale *= 1.10f;
            }
        }
        float drawW = bmpW * scale;
        float drawH = bmpH * scale;
        float dx = left + (cardW - drawW) / 2f;
        float dy;
        if (yardCropOffset) {
            dy = top + cardH / 2f - 0.40f * drawH;
            dy = Math.max(top + cardH - drawH, Math.min(top, dy));
        } else {
            dy = top + (cardH - drawH) / 2f;
        }

        Rect src = new Rect(0, 0, (int) bmpW, (int) bmpH);
        android.graphics.RectF dst = new android.graphics.RectF(dx, dy, dx + drawW, dy + drawH);
        canvas.drawBitmap(bmp, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        canvas.restore();

        drawTimestampBadge(canvas, tsStr, left, top, right, bottom, tsTextSize);
    }

    private float getCameraTabTimestampTextSize(float canvasWidth) {
        float halfW = (canvasWidth - 6f) / 2f;
        return Math.max(17f, 23f * (halfW / 712f));
    }

    private void drawTimestampBadge(Canvas canvas, String tsStr, float left, float top, float right, float bottom, float textSize) {
        String displayTs = (tsStr != null && !tsStr.isEmpty()) ? tsStr : "Live";
        Paint tsText = new Paint();
        tsText.setAntiAlias(true);
        tsText.setColor(android.graphics.Color.parseColor("#CCCCCC"));
        tsText.setTextSize(textSize);

        int textPadding = 12;
        int edgeMargin = 10;
        Rect textBounds = new Rect();
        tsText.getTextBounds(displayTs, 0, displayTs.length(), textBounds);

        float rectLeft = left + edgeMargin;
        float rectTop = top + edgeMargin;
        float rectRight = rectLeft + textBounds.width() + textPadding * 2f;
        float rectBottom = rectTop + textBounds.height() + textPadding * 2f;

        Paint bgPaint = new Paint();
        bgPaint.setColor(android.graphics.Color.BLACK);
        bgPaint.setAlpha(200);
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgPaint);
        canvas.drawText(displayTs, rectLeft + textPadding, rectBottom - textPadding, tsText);
    }

    private void fetchImageFromUrl(String imageUrl, ImageTarget target) {
        new Thread(() -> {
            try {
                // Legg på et unikt tidsstempel for å tvinge Nabu Casa / proxyer til å gi oss et ferskt bilde (cache-busting)
                String urlWithTimestamp = imageUrl + (imageUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                Log.d("GrefsenveienApp", "Fetching image from: " + urlWithTimestamp);
                URL url = new URL(urlWithTimestamp);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false); // FORCING FRESH DOWNLOAD
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                Log.d("GrefsenveienApp", "Response Code: " + responseCode);

                if (responseCode == 200) {
                    // Hent tidsstempel fra headers (Last-Modified foretrekkes, ellers Date)
                    String lastMod = connection.getHeaderField("Last-Modified");
                    String dateHdr = connection.getHeaderField("Date");
                    Log.d("GrefsenveienApp", "Header Last-Modified: " + lastMod);
                    Log.d("GrefsenveienApp", "Header Date: " + dateHdr);
                    
                    String dateHeader = lastMod;
                    if (dateHeader == null) {
                        dateHeader = dateHdr;
                    }
                    
                    // Forenkle datoformatet for visning (valgfritt)
                    String finalTimestampStr = "Ukjent tid";
                    if (dateHeader != null) {
                        try {
                            // Standard HTTP-datoformat er "EEE, dd MMM yyyy HH:mm:ss zzz"
                            SimpleDateFormat httpFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                            Date parsedDate = httpFormat.parse(dateHeader);
                            if (parsedDate != null) {
                                SimpleDateFormat displayFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
                                finalTimestampStr = displayFormat.format(parsedDate);
                            } else {
                                finalTimestampStr = dateHeader;
                            }
                        } catch (Exception e) {
                            Log.e("GrefsenveienApp", "Failed to parse date header: " + dateHeader, e);
                            // Hvis parsing feiler, bruk råverdien
                            finalTimestampStr = dateHeader;
                        }
                    } else {
                        Log.w("GrefsenveienApp", "No date headers found in HTTP response");
                    }
                    Log.d("GrefsenveienApp", "Final imageTimestamp string: " + finalTimestampStr);

                    InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        // Sørg for at vi er på UI-tråden før vi bytter ut variablene som Canvasen tegner
                        final String newTimestamp = finalTimestampStr;

                        getCarContext().getMainExecutor().execute(() -> {
                            switch (target) {
                                case MAILBOX:
                                    mailboxBitmap = bitmap;
                                    mailboxTimestamp = newTimestamp;
                                    break;
                                case WEATHER:
                                    weatherBitmap = bitmap;
                                    weatherTimestamp = newTimestamp;
                                    break;
                                default:
                                    cameraBitmap = bitmap;
                                    imageTimestamp = newTimestamp;
                                    break;
                            }
                            drawCameraImage();
                            invalidate();
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void triggerDoorbellImage() {
        new Thread(() -> {
            try {
                URL url = new URL(BuildConfig.DOORBELL_TAKE_IMAGE_URL);
                Log.d("GrefsenveienApp", "MainCarScreen -> Calling URL to take new doorbell image");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    getCarContext().getMainExecutor().execute(() -> {
                        CarToast.makeText(getCarContext(), "HTTP " + responseCode, CarToast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void makeUrlRequest(String targetName) {
        makeUrlRequest(targetName, null);
    }

    private void makeUrlRequest(String targetName, Integer delaySeconds) {
        // Vis en umiddelbar beskjed om at vi prøver å sende signalet
        String statusText;
        if (targetName.equals("garasjen")) {
            statusText = delaySeconds != null
                    ? "Åpner/lukker Garasje (+" + delaySeconds + "s)..."
                    : "Åpner/lukker Garasje...";
        } else {
            statusText = "Åpner/lukker Port...";
        }
        
        SharedPreferences prefs = getCarContext().getSharedPreferences("GrefsenveienPrefs", Context.MODE_PRIVATE);
        String savedEmail = prefs.getString("user_email", null);
        
        if ((savedEmail == null || savedEmail.isEmpty())) {
            CarToast.makeText(getCarContext(), "Du må logge inn i appen på telefonen", CarToast.LENGTH_LONG).show();
            return;
        }

        CarToast.makeText(getCarContext(), statusText, CarToast.LENGTH_SHORT).show();

        // Make request in background thread
        new Thread(() -> {
            try {
                URL url;
                if (targetName.equals("garasjen")) {
                    url = new URL(BuildConfig.GARAGE_WEBHOOK_URL);
                } else {
                    url = new URL(BuildConfig.GATE_WEBHOOK_URL);
                }
                
                android.util.Log.i("GrefsenveienApp", "MainCarScreen -> Calling webhook: " + url.toString());
                
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String payload = "{\"token\":\"Xi3gQF4GTFR7aENMkMjftt4P\",\"user\":\"" + savedEmail + "\"";
                if (delaySeconds != null && targetName.equals("garasjen")) {
                    payload += ",\"delay\":" + delaySeconds;
                }
                payload += "}";

                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Get response code 
                int responseCode = connection.getResponseCode();

                connection.disconnect();

                // Update UI on main thread with result
                getCarContext().getMainExecutor().execute(() -> {
                    String resultMsg;
                    if (responseCode == 200) {
                        resultMsg = "Vellykket";
                        ActionFeedbackSound.playSuccess(getCarContext());
                    } else {
                        resultMsg = "Feilkode " + responseCode + " (" + targetName + ")";
                        ActionFeedbackSound.playError(getCarContext());
                    }
                    CarToast.makeText(getCarContext(), resultMsg, CarToast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                getCarContext().getMainExecutor().execute(() -> {
                    String errorMsg = "Nettverksfeil for " + targetName;
                    ActionFeedbackSound.playError(getCarContext());
                    CarToast.makeText(getCarContext(), errorMsg, CarToast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Home Assistant – temperaturhistorikk
    // -------------------------------------------------------------------------

    private static final int HA_HISTORY_CONNECT_TIMEOUT_MS = 15_000;
    private static final int HA_HISTORY_READ_TIMEOUT_MS = 15_000;
    private static final int HA_HISTORY_LONG_READ_TIMEOUT_MS = 60_000;
    private static final int HA_HISTORY_MAX_ATTEMPTS = 3;
    private static final int HA_HISTORY_RETRY_DELAY_MS = 1_500;

    @Nullable
    private String fetchHaHistoryJsonWithRetry(@NonNull String label, @NonNull String url) {
        return fetchHaHistoryJsonWithRetry(label, url,
                HA_HISTORY_CONNECT_TIMEOUT_MS, HA_HISTORY_READ_TIMEOUT_MS);
    }

    @Nullable
    private String fetchHaHistoryJsonWithRetry(@NonNull String label, @NonNull String url,
            int connectTimeoutMs, int readTimeoutMs) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= HA_HISTORY_MAX_ATTEMPTS; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                conn.connect();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (attempt > 1) {
                        Log.i("GrefsenveienApp", label + " succeeded on attempt " + attempt);
                    }
                    return json;
                }
                lastError = new IOException("HTTP " + responseCode);
                Log.w("GrefsenveienApp", label + " failed: non-200 HTTP " + responseCode
                        + " (attempt " + attempt + "/" + HA_HISTORY_MAX_ATTEMPTS + ")");
            } catch (java.net.SocketTimeoutException e) {
                lastError = e;
                Log.w("GrefsenveienApp", label + " failed: timeout after "
                        + connectTimeoutMs + "ms connect / "
                        + readTimeoutMs + "ms read"
                        + " (attempt " + attempt + "/" + HA_HISTORY_MAX_ATTEMPTS + ")");
            } catch (Exception e) {
                lastError = e;
                Log.w("GrefsenveienApp", label + " failed: " + e.getClass().getSimpleName()
                        + " - " + e.getMessage()
                        + " (attempt " + attempt + "/" + HA_HISTORY_MAX_ATTEMPTS + ")");
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            if (attempt < HA_HISTORY_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(HA_HISTORY_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastError != null) {
            Log.e("GrefsenveienApp", label + " gave up after " + HA_HISTORY_MAX_ATTEMPTS + " attempts", lastError);
        }
        return null;
    }

    private void fetchHomeAssistantData() {
        new Thread(() -> {
            long now = System.currentTimeMillis();
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            List<float[]> todayTempPoints = new ArrayList<>();
            List<float[]> yesterdayTempPoints = new ArrayList<>();
            List<float[]> twoDaysAgoTempPoints = new ArrayList<>();
            List<float[]> sixtyDayAvgTempPoints = new ArrayList<>();
            float[] rainByWeek = new float[12];
            float todayRainMm = 0f;
            float[] hourlyRain = new float[24];
            List<float[]> soilPoints = new ArrayList<>();
            float[] hourlySolar = new float[24];
            java.util.Calendar dayCal = java.util.Calendar.getInstance();
            dayCal.setTimeInMillis(now);
            dayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            dayCal.set(java.util.Calendar.MINUTE, 0);
            dayCal.set(java.util.Calendar.SECOND, 0);
            dayCal.set(java.util.Calendar.MILLISECOND, 0);
            long todayMidnight = dayCal.getTimeInMillis();
            long yesterdayMidnight = todayMidnight - 24L * 3600_000;
            long twoDaysAgoMidnight = yesterdayMidnight - 24L * 3600_000;

            // 1. Fetch Temp (72h for today + yesterday + 2 days ago comparison)
            try {
                String tempUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 72L * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_temp"
                        + "&end_time=" + isoFmt.format(new Date(now));
                String json = fetchHaHistoryJsonWithRetry(
                        "Temperature history (sensor.vaerstasjon_temp)", tempUrl);
                if (json != null) {
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        float lastTodayVal = 0f;
                        boolean hasTodayVal = false;
                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                float temp = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                if (ts >= todayMidnight) {
                                    float hourOfDay = (ts - todayMidnight) / 3_600_000f;
                                    if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                        todayTempPoints.add(new float[]{hourOfDay, temp});
                                        lastTodayVal = temp;
                                        hasTodayVal = true;
                                    }
                                } else if (ts >= yesterdayMidnight) {
                                    float hourOfDay = (ts - yesterdayMidnight) / 3_600_000f;
                                    if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                        yesterdayTempPoints.add(new float[]{hourOfDay, temp});
                                    }
                                } else if (ts >= twoDaysAgoMidnight) {
                                    float hourOfDay = (ts - twoDaysAgoMidnight) / 3_600_000f;
                                    if (hourOfDay >= 0f && hourOfDay <= 24f) {
                                        twoDaysAgoTempPoints.add(new float[]{hourOfDay, temp});
                                    }
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (hasTodayVal) {
                            float currentHour = (now - todayMidnight) / 3_600_000f;
                            todayTempPoints.add(new float[]{currentHour, lastTodayVal});
                        }
                    } else {
                        Log.w("GrefsenveienApp",
                                "Temperature history (sensor.vaerstasjon_temp): empty response array");
                    }
                }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to parse temperature history", e);
            }

            // 1b. Fetch 60d temp for diurnal average curve
            long sixtyDaysAgo = now - 60L * 24 * 3600_000;
            try {
                String avgTempUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(sixtyDaysAgo))
                        + "?filter_entity_id=sensor.vaerstasjon_temp"
                        + "&end_time=" + isoFmt.format(new Date(now));
                String avgJson = fetchHaHistoryJsonWithRetry(
                        "Temperature 60d average (sensor.vaerstasjon_temp)", avgTempUrl,
                        HA_HISTORY_CONNECT_TIMEOUT_MS, HA_HISTORY_LONG_READ_TIMEOUT_MS);
                if (avgJson != null) {
                    JSONArray outer = new JSONArray(avgJson);
                    if (outer.length() > 0) {
                        sixtyDayAvgTempPoints = computeSixtyDayHourlyAverage(outer.getJSONArray(0), sixtyDaysAgo);
                    } else {
                        Log.w("GrefsenveienApp",
                                "Temperature 60d average (sensor.vaerstasjon_temp): empty response array");
                    }
                }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to parse 60-day temperature average", e);
            }

            // 2. Fetch Rain 12 weeks (daily values aggregated per Monday week)
            try {
                String rainUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 91L * 24 * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_daily_rain"
                        + "&end_time=" + isoFmt.format(new Date(now));
                String json = fetchHaHistoryJsonWithRetry(
                        "Rain history (sensor.vaerstasjon_daily_rain)", rainUrl);
                if (json != null) {
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        dayFmt.setTimeZone(java.util.TimeZone.getDefault());
                        TreeMap<String, Float> maxPerDay = new TreeMap<>();
                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                float rain = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                String dayKey = dayFmt.format(new Date(ts));
                                Float cur = maxPerDay.get(dayKey);
                                if (cur == null || rain > cur) maxPerDay.put(dayKey, rain);
                            } catch (NumberFormatException ignored) {}
                        }
                        TreeMap<Long, Float> rainPerWeek = new TreeMap<>();
                        for (java.util.Map.Entry<String, Float> entry : maxPerDay.entrySet()) {
                            try {
                                Date day = dayFmt.parse(entry.getKey());
                                if (day == null) continue;
                                long weekMonday = getWeekMondayMillis(day.getTime());
                                float prev = rainPerWeek.containsKey(weekMonday) ? rainPerWeek.get(weekMonday) : 0f;
                                rainPerWeek.put(weekMonday, prev + entry.getValue());
                            } catch (Exception ignored) {}
                        }
                        Float todayVal = maxPerDay.get(dayFmt.format(new Date(now)));
                        if (todayVal != null) {
                            todayRainMm = todayVal;
                        }
                        Calendar weekCal = Calendar.getInstance();
                        weekCal.setTimeInMillis(getWeekMondayMillis(now));
                        for (int i = 0; i < 12; i++) {
                            Float val = rainPerWeek.get(weekCal.getTimeInMillis());
                            rainByWeek[i] = val != null ? val : 0f;
                            weekCal.add(Calendar.WEEK_OF_YEAR, -1);
                        }
                    } else {
                        Log.w("GrefsenveienApp",
                                "Rain history (sensor.vaerstasjon_daily_rain): empty response array");
                    }
                }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to parse rain history", e);
            }

            // 2b. Fetch Lightning 7d
            List<LightningEvent> lightningEvents = new ArrayList<>();
            long sevenDaysAgo = now - 7L * 24 * 3600_000;
            try {
                String lightningUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(sevenDaysAgo))
                        + "?filter_entity_id=sensor.vaerstasjon_lightning_strike_distance"
                        + "&end_time=" + isoFmt.format(new Date(now));
                String lightningJson = fetchHaHistoryJsonWithRetry(
                        "Lightning history (sensor.vaerstasjon_lightning_strike_distance)", lightningUrl);
                if (lightningJson != null) {
                    JSONArray outer = new JSONArray(lightningJson);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        float prevDist = -1f;
                        long prevTs = 0L;
                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                String stateStr = obj.getString("state");
                                if ("unavailable".equals(stateStr) || "unknown".equals(stateStr)) continue;
                                float dist = Float.parseFloat(stateStr);
                                if (dist <= 0f) continue;
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                if (ts < sevenDaysAgo) continue;
                                if (prevDist < 0f || Math.abs(dist - prevDist) > 0.01f || ts - prevTs > 120_000L) {
                                    lightningEvents.add(new LightningEvent(ts, dist));
                                }
                                prevDist = dist;
                                prevTs = ts;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to parse lightning history", e);
            }
            lightningEvents.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
            float lastLightningDist = fetchSensorState("sensor.vaerstasjon_lightning_strike_distance", -1f);
            if (lastLightningDist <= 0f && !lightningEvents.isEmpty()) {
                lastLightningDist = lightningEvents.get(lightningEvents.size() - 1).distanceKm;
            }
            float nearestLightningDist = -1f;
            for (LightningEvent event : lightningEvents) {
                if (nearestLightningDist < 0f || event.distanceKm < nearestLightningDist) {
                    nearestLightningDist = event.distanceKm;
                }
            }
            lightningEvents7d = lightningEvents;
            lastLightningDistanceKm = lastLightningDist;
            nearestLightningDistanceKm = nearestLightningDist;
            lightningCount7d = lightningEvents.size();
            lightningWindowStartMs = sevenDaysAgo;
            lightningWindowEndMs = now;

            // 3. Fetch Rain Rate per Hour
            try {
                String hourlyRainUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 24L * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_hourly_rain_rate"
                        + "&end_time=" + isoFmt.format(new Date(now));
                HttpURLConnection conn = (HttpURLConnection) new URL(hourlyRainUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    conn.disconnect();
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                float rate = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                int hourIndex = (int)((now - ts) / 3_600_000L);
                                if (hourIndex >= 0 && hourIndex < 24) {
                                    hourlyRain[hourIndex] = Math.max(hourlyRain[hourIndex], rate);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else { conn.disconnect(); }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch hourly_rain_rate", e);
            }

            // 4. Fetch Soil
            try {
                String soilUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 3L * 24 * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_soil_humidity_1"
                        + "&end_time=" + isoFmt.format(new Date(now));
                HttpURLConnection conn = (HttpURLConnection) new URL(soilUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    conn.disconnect();
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        float lastVal = 0f;
                        boolean hasVal = false;
                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                float humidity = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                float hoursAgo = (now - ts) / 3_600_000f;
                                if (hoursAgo >= 0 && hoursAgo <= 72) {
                                    soilPoints.add(new float[]{hoursAgo, humidity});
                                    lastVal = humidity;
                                    hasVal = true;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (hasVal) {
                            soilPoints.add(new float[]{0f, lastVal});
                        }
                    }
                } else { conn.disconnect(); }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch soil_humidity", e);
            }

            // 5. Fetch Solar
            try {
                String solarUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 24L * 3600_000))
                        + "?filter_entity_id=sensor.solenergi_hourly"
                        + "&end_time=" + isoFmt.format(new Date(now));
                HttpURLConnection conn = (HttpURLConnection) new URL(solarUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    conn.disconnect();
                    JSONArray outer = new JSONArray(json);
                    if (outer.length() > 0) {
                        JSONArray states = outer.getJSONArray(0);
                        
                        java.util.Calendar calNow = java.util.Calendar.getInstance();
                        calNow.setTimeInMillis(now);
                        calNow.set(java.util.Calendar.MINUTE, 0);
                        calNow.set(java.util.Calendar.SECOND, 0);
                        calNow.set(java.util.Calendar.MILLISECOND, 0);
                        long nowHourStart = calNow.getTimeInMillis();
                        
                        java.util.Calendar calTs = java.util.Calendar.getInstance();

                        for (int i = 0; i < states.length(); i++) {
                            JSONObject obj = states.getJSONObject(i);
                            try {
                                float val = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                
                                calTs.setTimeInMillis(ts);
                                calTs.set(java.util.Calendar.MINUTE, 0);
                                calTs.set(java.util.Calendar.SECOND, 0);
                                calTs.set(java.util.Calendar.MILLISECOND, 0);
                                long tsHourStart = calTs.getTimeInMillis();
                                
                                int hourIndex = (int)((nowHourStart - tsHourStart) / 3_600_000L);
                                if (hourIndex >= 0 && hourIndex < 24) {
                                    hourlySolar[hourIndex] = Math.max(hourlySolar[hourIndex], val);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else { conn.disconnect(); }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch solar_hourly", e);
            }

            try {
                valSolarHourly = fetchSensorState("sensor.solenergi_hourly", 0f);
                hourlySolar[0] = Math.max(hourlySolar[0], valSolarHourly);
                System.arraycopy(hourlySolar, 0, valSolar24h, 0, 24);
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch live solar_hourly", e);
            }

            float curTemp = 15f;
            if (todayTempPoints.isEmpty()) {
                todayTempPoints.add(new float[]{0f, 15f});
            } else {
                todayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
                curTemp = todayTempPoints.get(todayTempPoints.size() - 1)[1];
            }
            if (!yesterdayTempPoints.isEmpty()) {
                yesterdayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
            }
            if (!twoDaysAgoTempPoints.isEmpty()) {
                twoDaysAgoTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
            }
            float[] todayTempMinMax = computeTodayTempMinMax(todayTempPoints);
            todayTempPoints = bucketTempPointsHourly(todayTempPoints);
            yesterdayTempPoints = bucketTempPointsHourly(yesterdayTempPoints);
            twoDaysAgoTempPoints = bucketTempPointsHourly(twoDaysAgoTempPoints);
            float currentHour = (now - todayMidnight) / 3_600_000f;
            if (!todayTempPoints.isEmpty()) {
                float lastVal = todayTempPoints.get(todayTempPoints.size() - 1)[1];
                if (todayTempPoints.get(todayTempPoints.size() - 1)[0] >= currentHour) {
                    todayTempPoints.remove(todayTempPoints.size() - 1);
                }
                todayTempPoints.add(new float[]{currentHour, lastVal});
                curTemp = lastVal;
            }
            if (!yesterdayTempPoints.isEmpty()) {
                float[] lastYesterday = yesterdayTempPoints.get(yesterdayTempPoints.size() - 1);
                if (lastYesterday[0] < 24f) {
                    yesterdayTempPoints.add(new float[]{24f, lastYesterday[1]});
                }
            }
            if (!twoDaysAgoTempPoints.isEmpty()) {
                float[] lastTwoDaysAgo = twoDaysAgoTempPoints.get(twoDaysAgoTempPoints.size() - 1);
                if (lastTwoDaysAgo[0] < 24f) {
                    twoDaysAgoTempPoints.add(new float[]{24f, lastTwoDaysAgo[1]});
                }
            }
            if (!sixtyDayAvgTempPoints.isEmpty()) {
                sixtyDayAvgTempPoints = alignAverageCurveToNow(sixtyDayAvgTempPoints, currentHour, curTemp);
            }

            // Fetch live room temperatures (guaranteed robust as each is try-catched inside fetchSensorState)
            valJonatan = fetchSensorState("sensor.jonatan_temperatur_temperature", curTemp + 0.0f);
            valLoftsgang = fetchSensorState("sensor.loftsgang_temperatur_temperature", curTemp + 2.2f);
            valKontor = fetchSensorState("sensor.kontor_temperatur_temperature", curTemp + 2.8f);
            valBad = fetchSensorState("sensor.stort_bad_temperatur_temperatur", curTemp + 0.8f);
            valVinterhage = fetchSensorState("sensor.vaerstasjon_inside_temp", curTemp + 6.7f);
            valKjokken = fetchSensorState("sensor.kjokken_temperatur_temperature", curTemp - 0.1f);
            valLiteBad = fetchSensorState("sensor.lite_bad_temperatur_temperature", curTemp - 0.1f);
            valMats = fetchSensorState("sensor.mats_temperatur_temperature", curTemp - 1.2f);
            valStue = fetchSensorState("sensor.stue_temperatur_temperature", curTemp + 1.8f);
            valGang3 = fetchSensorState("sensor.innergang_temperatur_temperature_2", curTemp + 0.2f);
            valSoverom = fetchSensorState("sensor.soverom_temperatur_temperature", curTemp - 1.1f);
            valVaskerom = fetchSensorState("sensor.vaskerom_temperatur_temperature", curTemp + 0.4f);
            valHumidity = fetchSensorState("sensor.vaerstasjon_humidity", 45f);

            // Fetch motion histories
            valStueMotionTime = Math.max(
                fetchMotionHistory("binary_sensor.stue_ved_vindu_bevegelsessensor_occupancy", now, isoFmt),
                fetchMotionHistory("binary_sensor.stue_innerst_bevegelsessensor_occupancy", now, isoFmt)
            );
            valLoftsgangMotionTime = fetchMotionHistory("binary_sensor.loftsgang_bevegelsessensor_occupancy", now, isoFmt);
            valGang4MotionTime = fetchMotionHistory("binary_sensor.inngang_bevegelsessensor_motion_detection", now, isoFmt);
            valJonatanMotionTime = fetchMotionHistory("binary_sensor.jonatan_bevegelsessensor_occupancy", now, isoFmt);
            valBadMotionTime = fetchMotionHistory("binary_sensor.stort_bad_bevegelsessensor_occupancy", now, isoFmt);
            valVaskeromMotionTime = fetchMotionHistory("binary_sensor.vaskerom_bevegelsessensor_occupancy", now, isoFmt);

            try {
                // Bruk faktisk canvas-størrelse for å fylle hele skjermen
                int chartW = lastCanvasWidth > 0 ? lastCanvasWidth
                        : (mSurfaceContainer != null ? mSurfaceContainer.getWidth() : 1440);
                int chartH = lastCanvasHeight > 0 ? lastCanvasHeight
                        : (mSurfaceContainer != null ? mSurfaceContainer.getHeight() : 2080);
                Bitmap chart = renderTemperatureChart(todayTempPoints, yesterdayTempPoints, twoDaysAgoTempPoints, sixtyDayAvgTempPoints, todayTempMinMax[0], todayTempMinMax[1], rainByWeek, todayRainMm, hourlyRain, soilPoints, chartW, chartH);
                SimpleDateFormat disp = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                String ts = "Oppdatert " + disp.format(new Date(now));
                getCarContext().getMainExecutor().execute(() -> {
                    homeBitmap = chart;
                    homeTimestamp = ts;
                    drawCameraImage();
                    invalidate();
                });
            } catch (Exception e) {
                Log.e("GrefsenveienApp", "Failed to render chart bitmap", e);
            }
        }).start();
    }

    private long parseIsoTimestamp(String iso) {
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(s);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private long getWeekMondayMillis(long timeMs) {
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

    private String formatRainWeekLabel(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        return day + "." + month;
    }

    private float[] chooseRainYAxis(float maxRain) {
        float[] steps = {2f, 5f, 10f, 20f};
        for (float step : steps) {
            float axisMax = (float) Math.ceil(maxRain / step) * step;
            if (axisMax < step) axisMax = step;
            int lineCount = (int) (axisMax / step);
            if (lineCount >= 2 && lineCount <= 5) {
                return new float[]{axisMax, step};
            }
        }
        float step = 10f;
        float axisMax = Math.max(step, (float) Math.ceil(maxRain / step) * step);
        return new float[]{axisMax, step};
    }

    private String formatRainMm(float mm) {
        if (Math.abs(mm - Math.round(mm)) < 0.05f) {
            return String.format(Locale.getDefault(), "%.0fmm", mm);
        }
        return String.format(Locale.getDefault(), "%.1f", mm).replace('.', ',') + "mm";
    }

    private String formatLightningKm(float km) {
        if (km < 0f) return "?";
        return String.format(Locale.getDefault(), "%.0fkm", km);
    }

    private float[] chooseLightningYAxis(float maxKm) {
        float safeMax = Math.max(1f, maxKm);
        float[] steps = {5f, 10f, 20f, 30f, 50f};
        for (float step : steps) {
            float axisMax = (float) Math.ceil(safeMax / step) * step;
            if (axisMax < step) axisMax = step;
            int lineCount = (int) (axisMax / step);
            if (lineCount >= 2 && lineCount <= 5) {
                return new float[]{axisMax, step};
            }
        }
        float step = 10f;
        float axisMax = Math.max(step, (float) Math.ceil(safeMax / step) * step);
        return new float[]{axisMax, step};
    }

    private int lightningStrikeColor(float distanceKm, float axisMaxKm) {
        float proximity = 1f - Math.min(1f, distanceKm / Math.max(1f, axisMaxKm));
        int far = android.graphics.Color.parseColor("#FFD966");
        int near = android.graphics.Color.parseColor("#FF3B30");
        return interpolateColor(far, near, proximity);
    }

    private boolean isSameDayMs(long timeA, long timeB) {
        Calendar calA = Calendar.getInstance();
        calA.setTimeInMillis(timeA);
        Calendar calB = Calendar.getInstance();
        calB.setTimeInMillis(timeB);
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR)
                && calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR);
    }

    private List<Long> getMidnightBoundaries(long windowStart, long windowEnd) {
        List<Long> midnights = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(windowStart);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < windowStart) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        while (cal.getTimeInMillis() < windowEnd) {
            midnights.add(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return midnights;
    }

    private String formatLightningDayLabel(long segmentStartMs, long windowEndMs) {
        if (isSameDayMs(segmentStartMs, windowEndMs)) {
            return "Idag";
        }
        Calendar labelCal = Calendar.getInstance();
        labelCal.setTimeInMillis(segmentStartMs);
        return formatRainWeekLabel(labelCal);
    }

    private void drawLightningWidget(Canvas c, float left, float right, float top, float bottom,
            float S, float wPad, float hdrOff, float gLeft, float gTopOff, float tGW, float tGH,
            float xAxisYOffset, Paint gridP, Paint lblHdr, Paint lblValNormal, Paint lblP,
            Paint lblPy, float axisTxt, float baseTxt) {
        c.drawText("LYN 7D", left + wPad, top + hdrOff, lblHdr);

        String headerStr = "Siste: " + formatLightningKm(lastLightningDistanceKm)
                + ", N\u00e6rmeste: " + formatLightningKm(nearestLightningDistanceKm);
        c.drawText(headerStr, right - wPad - lblValNormal.measureText(headerStr), top + hdrOff, lblValNormal);

        float countW = Math.max(56f * S, tGW * 0.22f);
        float plotGL = left + gLeft;
        float plotGT = top + gTopOff;
        float plotGW = tGW - countW - 8f * S;
        float plotGH = tGH;
        float plotBase = plotGT + plotGH;

        float maxDist = 1f;
        for (LightningEvent event : lightningEvents7d) {
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
                c.drawText(String.format(Locale.getDefault(), "%.0f", km), left + wPad, y + 6f * S, yLblP);
            }
        }
        c.drawLine(plotGL, plotBase, plotGL + plotGW, plotBase, gridP);

        long windowStart = lightningWindowStartMs > 0 ? lightningWindowStartMs : System.currentTimeMillis() - 7L * 24 * 3600_000;
        long windowEnd = lightningWindowEndMs > 0 ? lightningWindowEndMs : System.currentTimeMillis();
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
        for (LightningEvent event : lightningEvents7d) {
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
        String countStr = String.format(Locale.getDefault(), "%d", lightningCount7d);
        Paint countLblP = new Paint(lblP);
        countLblP.setTextAlign(Paint.Align.CENTER);
        float countY = top + gTopOff + plotGH * 0.42f;
        c.drawText(countStr, countCenterX, countY, countP);
        c.drawText("lyn", countCenterX, countY + Math.max(18f, 24f * S), countLblP);
    }

    private List<float[]> bucketTempPointsHourly(List<float[]> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        raw.sort((a, b) -> Float.compare(a[0], b[0]));
        java.util.LinkedHashMap<Integer, Float> hourMap = new java.util.LinkedHashMap<>();
        for (float[] p : raw) {
            int hour = Math.min(23, Math.max(0, (int) Math.floor(p[0])));
            hourMap.put(hour, p[1]);
        }
        List<float[]> result = new ArrayList<>();
        for (java.util.Map.Entry<Integer, Float> entry : hourMap.entrySet()) {
            result.add(new float[]{entry.getKey(), entry.getValue()});
        }
        return result;
    }

    /** Min/max from all raw readings since midnight (not hourly-bucketed values). */
    private float[] computeTodayTempMinMax(List<float[]> rawTodayPoints) {
        float minT = Float.MAX_VALUE;
        float maxT = -Float.MAX_VALUE;
        for (float[] p : rawTodayPoints) {
            minT = Math.min(minT, p[1]);
            maxT = Math.max(maxT, p[1]);
        }
        if (minT == Float.MAX_VALUE) {
            return new float[]{14f, 16f};
        }
        return new float[]{minT, maxT};
    }

    /** Hour-of-day average (0–24) from all readings in the last 60 days. */
    private List<float[]> computeSixtyDayHourlyAverage(JSONArray states, long sinceMs) {
        double[] sum = new double[24];
        int[] count = new int[24];
        java.util.Calendar hourCal = java.util.Calendar.getInstance();
        for (int i = 0; i < states.length(); i++) {
            try {
                JSONObject obj = states.getJSONObject(i);
                float temp = Float.parseFloat(obj.getString("state"));
                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                if (ts < sinceMs) continue;
                hourCal.setTimeInMillis(ts);
                int hour = hourCal.get(java.util.Calendar.HOUR_OF_DAY);
                sum[hour] += temp;
                count[hour]++;
            } catch (Exception ignored) {}
        }
        List<float[]> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if (count[h] > 0) {
                result.add(new float[]{h, (float) (sum[h] / count[h])});
            }
        }
        if (!result.isEmpty()) {
            result.add(new float[]{24f, result.get(0)[1]});
        }
        return result;
    }

    private float interpolateTempAtHour(List<float[]> points, float hour) {
        if (points.isEmpty()) return 0f;
        if (points.size() == 1) return points.get(0)[1];
        hour = Math.max(0f, Math.min(24f, hour));
        for (int i = 0; i < points.size() - 1; i++) {
            float[] a = points.get(i);
            float[] b = points.get(i + 1);
            if (hour >= a[0] && hour <= b[0]) {
                float span = b[0] - a[0];
                if (span < 0.0001f) return a[1];
                float t = (hour - a[0]) / span;
                return a[1] + t * (b[1] - a[1]);
            }
        }
        return points.get(points.size() - 1)[1];
    }

    /** Shift diurnal average so it matches current outdoor temp at the current hour. */
    private List<float[]> alignAverageCurveToNow(List<float[]> avgPoints, float currentHour, float currentTemp) {
        if (avgPoints.isEmpty()) return avgPoints;
        float offset = currentTemp - interpolateTempAtHour(avgPoints, currentHour);
        List<float[]> adjusted = new ArrayList<>(avgPoints.size());
        for (float[] p : avgPoints) {
            adjusted.add(new float[]{p[0], p[1] + offset});
        }
        return adjusted;
    }

    private void buildSmoothPath(Path path, List<float[]> screenPoints, float tension) {
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

    private void drawOutdoorTempLine(Canvas canvas, List<float[]> points, float minT, float range,
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

    private void drawOutdoorTempDashedLine(Canvas canvas, List<float[]> points, float minT, float range,
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

    private Bitmap renderTemperatureChart(List<float[]> todayTempPoints, List<float[]> yesterdayTempPoints, List<float[]> twoDaysAgoTempPoints, List<float[]> sixtyDayAvgTempPoints, float todayMinT, float todayMaxT, float[] rainByWeek, float todayRainMm, float[] hourlyRain, List<float[]> soilPoints, int w, int h) {
        todayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
        yesterdayTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
        twoDaysAgoTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));
        sixtyDayAvgTempPoints.sort((a, b) -> Float.compare(a[0], b[0]));

        float minT = Float.MAX_VALUE, maxT = -Float.MAX_VALUE;
        for (float[] p : todayTempPoints) {
            minT = Math.min(minT, p[1]);
            maxT = Math.max(maxT, p[1]);
        }
        for (float[] p : yesterdayTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        for (float[] p : twoDaysAgoTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        for (float[] p : sixtyDayAvgTempPoints) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
        if (minT == Float.MAX_VALUE) { minT = 14f; maxT = 16f; }
        float range = maxT - minT;
        if (range < 2f) { minT -= 1; maxT += 1; range = 2f; }

        float minS = 0f, maxS = 100f;
        float curSoil = 0f;
        float actualMinS = 0f, actualMaxS = 100f;
        boolean hasSoilData = soilPoints != null && !soilPoints.isEmpty();
        if (hasSoilData) {
            soilPoints.sort((a, b) -> Float.compare(b[0], a[0]));
            if (soilPoints.get(0)[0] < 72f) {
                soilPoints.add(0, new float[]{72f, soilPoints.get(0)[1]});
            }
            if (soilPoints.get(soilPoints.size() - 1)[0] > 0f) {
                soilPoints.add(new float[]{0f, soilPoints.get(soilPoints.size() - 1)[1]});
            }
            actualMinS = Float.MAX_VALUE;
            actualMaxS = -Float.MAX_VALUE;
            for (float[] p : soilPoints) {
                actualMinS = Math.min(actualMinS, p[1]);
                actualMaxS = Math.max(actualMaxS, p[1]);
            }
            curSoil = soilPoints.get(soilPoints.size() - 1)[1];
            minS = 0f;
            maxS = 100f;
        }

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(android.graphics.Color.BLACK);

        // === PROPORTIONAL SCALE FACTOR ===
        float S = w / 1440f;

        // === PAINTS (scaled text sizes) ===
        Paint gridP = new Paint();
        gridP.setColor(android.graphics.Color.parseColor("#1E2A38")); gridP.setStrokeWidth(2f);
        float baseTxt = 15f * S;
        float axisTxt = Math.max(13f, 17f * S);
        float hdrTxt = Math.max(17f, 23f * S);
        Paint lblP = new Paint(); lblP.setAntiAlias(true); lblP.setColor(android.graphics.Color.WHITE); lblP.setTextSize(axisTxt);
        Paint lblPy = new Paint(); lblPy.setAntiAlias(true); lblPy.setColor(android.graphics.Color.WHITE); lblPy.setTextSize(axisTxt); lblPy.setTextAlign(Paint.Align.RIGHT);
        Paint lblHdr = new Paint(); lblHdr.setAntiAlias(true); lblHdr.setColor(android.graphics.Color.WHITE); lblHdr.setTextSize(hdrTxt); lblHdr.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint lblVal = new Paint(); lblVal.setAntiAlias(true); lblVal.setColor(android.graphics.Color.WHITE); lblVal.setTextSize(hdrTxt); lblVal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint lblValNormal = new Paint(lblVal); lblValNormal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        Paint barLblP = new Paint(); barLblP.setAntiAlias(true); barLblP.setColor(android.graphics.Color.WHITE); barLblP.setTextSize(axisTxt);
        Paint dayLblP = new Paint(); dayLblP.setAntiAlias(true); dayLblP.setColor(android.graphics.Color.WHITE); dayLblP.setTextSize(axisTxt);

        // === 3-COLUMN GRID ===
        float gap = 14f * S;
        float colW = (w - 2f * gap) / 3f;
        float col1L = 0f, col1R = colW;
        float col2L = colW + gap, col2R = 2f * colW + gap;
        float col3L = 2f * (colW + gap), col3R = (float) w;
        float curTemp = todayTempPoints.isEmpty() ? 15f : todayTempPoints.get(todayTempPoints.size() - 1)[1];

        // === ROW LAYOUT ===
        float topPad = h * 0.105f;
        float rowGap = 12f * S;
        float usableH = h - topPad - 3f * rowGap;
        float chartRowH = usableH * 0.21f;
        float camRowH = usableH * 0.27f;
        float roomH = usableH * 0.31f;
        float r1Top = topPad, r1Bot = r1Top + chartRowH;
        float r2Top = r1Bot + rowGap, r2Bot = r2Top + chartRowH;
        float r3Top = r2Bot + rowGap, r3Bot = r3Top + camRowH;
        float r4Top = r3Bot + rowGap;

        float wPad = Math.max(14f, 20f * S);
        float hdrOff = Math.max(24f, 32f * S);
        float gLeft = Math.max(42f, 58f * S);
        float gTopOff = Math.max(38f, 52f * S);
        float gBotOff = Math.max(26f, 35f * S);
        float xAxisYOffset = Math.max(18f, 25f * S);
        float tGW = colW - gLeft - wPad;
        float tGH = chartRowH - gTopOff - gBotOff;

        // === ROW 1: Utetemperatur | Regn 12 uker | Aktuelt temp ===

        // --- Utetemperatur ---
        drawWidgetCard(c, col1L, r1Top, col1R, r1Bot, S);
        c.drawText("UTE 24t", col1L + wPad, r1Top + hdrOff, lblHdr);
        String tHdr = String.format(Locale.getDefault(), "%.1f\u00b0\u2013%.1f\u00b0", todayMinT, todayMaxT);
        c.drawText(tHdr, col1R - wPad - lblValNormal.measureText(tHdr), r1Top + hdrOff, lblValNormal);
        float tGL = col1L + gLeft, tGT = r1Top + gTopOff;
        for (int i = 0; i <= 4; i++) { float frac = i/4f, y = tGT + tGH*(1-frac); c.drawLine(tGL, y, tGL+tGW, y, gridP); c.drawText(String.format(Locale.getDefault(), "%.0f\u00b0", minT+range*frac), tGL-4f*S, y+6f*S, lblPy); }
        Paint hourGridP = new Paint(gridP);
        hourGridP.setColor(android.graphics.Color.parseColor("#15202B"));
        for (int hr = 0; hr <= 24; hr++) {
            float x = tGL + tGW * (hr / 24f);
            c.drawLine(x, tGT, x, tGT + tGH, hr % 6 == 0 ? gridP : hourGridP);
        }
        for (int hr = 0; hr <= 24; hr += 6) {
            float x = tGL + tGW * (hr / 24f);
            String sl = String.format(Locale.getDefault(), "%02d", hr);
            c.drawText(sl, x - lblP.measureText(sl) / 2f, tGT + tGH + xAxisYOffset, lblP);
        }
        int colorToday = android.graphics.Color.parseColor("#27C93F");
        int colorTodayFill = android.graphics.Color.parseColor("#1A27C93F");
        int colorYesterday = android.graphics.Color.argb(90, 255, 95, 86);
        int colorTwoDaysAgo = android.graphics.Color.argb(45, 255, 95, 86);
        int colorSixtyDayAvg = android.graphics.Color.argb(140, 160, 168, 176);
        float lineTension = 0.1f;
        if (!sixtyDayAvgTempPoints.isEmpty()) {
            drawOutdoorTempDashedLine(c, sixtyDayAvgTempPoints, minT, range, tGL, tGT, tGW, tGH, colorSixtyDayAvg, S, lineTension);
        }
        if (!twoDaysAgoTempPoints.isEmpty()) {
            drawOutdoorTempLine(c, twoDaysAgoTempPoints, minT, range, tGL, tGT, tGW, tGH, colorTwoDaysAgo, S, lineTension, null);
        }
        if (!yesterdayTempPoints.isEmpty()) {
            drawOutdoorTempLine(c, yesterdayTempPoints, minT, range, tGL, tGT, tGW, tGH, colorYesterday, S, lineTension, null);
        }
        if (!todayTempPoints.isEmpty()) {
            drawOutdoorTempLine(c, todayTempPoints, minT, range, tGL, tGT, tGW, tGH, colorToday, S, lineTension, colorTodayFill);
        }

        // --- Regn 12 uker ---
        drawWidgetCard(c, col2L, r1Top, col2R, r1Bot, S);
        c.drawText("REGN 12u", col2L + wPad, r1Top + hdrOff, lblHdr);
        String todayRainStr = "Idag: " + formatRainMm(todayRainMm);
        c.drawText(todayRainStr, col2R - wPad - lblValNormal.measureText(todayRainStr), r1Top + hdrOff, lblValNormal);
        float rainTopExtra = Math.max(10f, 14f * S);
        float rainYLabelW = Math.max(14f, 18f * S);
        float r7GT = r1Top + gTopOff + rainTopExtra;
        float r7GH = tGH - rainTopExtra;
        float r7GL = col2L + wPad + rainYLabelW;
        float r7GW = col2R - col2L - wPad - rainYLabelW - wPad;
        float r7Base = r7GT + r7GH;
        float maxRain = 1f;
        for (float r : rainByWeek) maxRain = Math.max(maxRain, r);
        float[] rainAxis = chooseRainYAxis(maxRain);
        float rainAxisMax = rainAxis[0];
        float rainAxisStep = rainAxis[1];
        Paint rainYLblP = new Paint(lblPy);
        rainYLblP.setTextAlign(Paint.Align.LEFT);
        for (float mm = rainAxisStep; mm <= rainAxisMax + 0.01f; mm += rainAxisStep) {
            float y = r7Base - r7GH * (mm / rainAxisMax);
            c.drawLine(r7GL, y, r7GL + r7GW, y, gridP);
            String yLabel = (Math.abs(mm - Math.round(mm)) < 0.01f)
                    ? String.format(Locale.getDefault(), "%.0f", mm)
                    : String.format(Locale.getDefault(), "%.1f", mm);
            c.drawText(yLabel, col2L + wPad, y + 6f * S, rainYLblP);
        }
        c.drawLine(r7GL, r7Base, r7GL + r7GW, r7Base, gridP);
        int weekCount = rainByWeek.length;
        float bGap = r7GW / weekCount;
        float bW = bGap * 0.72f;
        float barRadius = Math.max(1.5f, 2f * S);
        java.util.Calendar weekLabelCal = java.util.Calendar.getInstance();
        weekLabelCal.setTimeInMillis(getWeekMondayMillis(System.currentTimeMillis()));
        String[] weekLabels = new String[weekCount];
        for (int i = 0; i < weekCount; i++) {
            weekLabels[i] = formatRainWeekLabel(weekLabelCal);
            weekLabelCal.add(java.util.Calendar.WEEK_OF_YEAR, -1);
        }
        Paint weekLblP = new Paint(dayLblP);
        weekLblP.setTextSize(Math.max(11f, axisTxt * 0.82f));
        Paint barP = new Paint();
        barP.setAntiAlias(false);
        Paint mmLblP = new Paint();
        mmLblP.setAntiAlias(true);
        mmLblP.setColor(android.graphics.Color.WHITE);
        mmLblP.setTextSize(axisTxt);
        mmLblP.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < weekCount; i++) {
            float bCX = r7GL + r7GW - bGap * i - bGap / 2f;
            float rainVal = rainByWeek[i];
            if (rainVal > 0.05f) {
                float bH = r7GH * (rainVal / rainAxisMax);
                float bT = r7Base - bH;
                barP.setShader(new android.graphics.LinearGradient(
                        0, r7Base, 0, bT,
                        android.graphics.Color.parseColor("#1B6ADF"),
                        android.graphics.Color.parseColor("#4FA5F7"),
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(bCX - bW / 2f, bT, bCX + bW / 2f, r7Base, barRadius, barRadius, barP);
                String mmStr = String.format(Locale.getDefault(), "%.1f", rainVal);
                android.graphics.Paint.FontMetrics mmFm = mmLblP.getFontMetrics();
                float mmY = bT - Math.max(4f * S, mmFm.descent + 2f * S);
                c.drawText(mmStr, bCX, mmY, mmLblP);
            }
            c.drawText(weekLabels[i], bCX - weekLblP.measureText(weekLabels[i]) / 2f, r7Base + xAxisYOffset, weekLblP);
        }

        // --- Aktuelt Utetemperatur ---
        drawWidgetCard(c, col3L, r1Top, col3R, r1Bot, S);
        c.drawText("N\u00c5", col3L + wPad, r1Top + hdrOff, lblHdr);
        Paint naP = new Paint(); naP.setAntiAlias(true); naP.setColor(android.graphics.Color.WHITE); naP.setTextSize(44f*S); naP.setTypeface(Typeface.DEFAULT_BOLD);
        String tempStr = String.format(Locale.getDefault(), "%.1f\u00b0C", curTemp);
        String humStr = String.format(Locale.getDefault(), "%.0f%%", valHumidity);
        float tempTextW = naP.measureText(tempStr);
        float humTextW = naP.measureText(humStr);
        float iconSize = 36f * S;
        float iconTextGap = 6f * S;
        float groupGap = 20f * S;
        float totalW = iconSize + iconTextGap + tempTextW + groupGap + iconSize + iconTextGap + humTextW;
        float startX = col3L + (colW - totalW) / 2f;
        float textY = r1Top + chartRowH * 0.51f + (naP.getTextSize() * 0.33f);
        float iconY = textY - naP.getTextSize() * 0.78f;
        try {
            android.graphics.drawable.Drawable thermD = androidx.core.content.ContextCompat.getDrawable(getCarContext(), R.drawable.ic_thermometer);
            if (thermD != null) {
                thermD.setBounds((int) startX, (int) iconY, (int) (startX + iconSize), (int) (iconY + iconSize));
                thermD.draw(c);
            } else {
                drawThermometerIcon(c, startX, iconY, iconSize, S, naP);
            }
        } catch (Exception e) {
            drawThermometerIcon(c, startX, iconY, iconSize, S, naP);
        }
        c.drawText(tempStr, startX + iconSize + iconTextGap, textY, naP);
        
        float humGroupX = startX + iconSize + iconTextGap + tempTextW + groupGap;
        try {
            android.graphics.drawable.Drawable dropD = androidx.core.content.ContextCompat.getDrawable(getCarContext(), R.drawable.ic_droplet);
            if (dropD != null) {
                dropD.setBounds((int) humGroupX, (int) iconY, (int) (humGroupX + iconSize), (int) (iconY + iconSize));
                dropD.draw(c);
            } else {
                drawDropletIcon(c, humGroupX, iconY, iconSize, S, naP);
            }
        } catch (Exception e) {
            drawDropletIcon(c, humGroupX, iconY, iconSize, S, naP);
        }
        c.drawText(humStr, humGroupX + iconSize + iconTextGap, textY, naP);

        // === ROW 2: Lyn/Regnstyrke | Solstraling | Jordfuktighet ===
        drawWidgetCard(c, col1L, r2Top, col1R, r2Bot, S);
        if (detailPageVersion == 2) {
            drawLightningWidget(c, col1L, col1R, r2Top, r2Bot, S, wPad, hdrOff, gLeft, gTopOff,
                    tGW, tGH, xAxisYOffset, gridP, lblHdr, lblValNormal, lblP, lblPy, axisTxt, baseTxt);
        } else {
        // --- Regnstyrke ---
        c.drawText("REGNSTYRKE", col1L+wPad, r2Top+hdrOff, lblHdr);
        float maxHR = 0.0f; for (float r : hourlyRain) maxHR = Math.max(maxHR, r);
        c.drawText(String.format(Locale.getDefault(), "%.1f mm/t", maxHR), col1R-wPad-lblVal.measureText(String.format(Locale.getDefault(), "%.1f mm/t", maxHR)), r2Top+hdrOff, lblVal);
        float rrGL = col1L+gLeft, rrGT = r2Top+gTopOff, rrGW = tGW, rrGH = tGH;
        c.drawLine(rrGL, rrGT, rrGL+rrGW, rrGT, gridP); c.drawLine(rrGL, rrGT+rrGH, rrGL+rrGW, rrGT+rrGH, gridP);
        float hbGap = rrGW/24f, hbW = hbGap*0.65f;
        Paint hBarP = new Paint(); hBarP.setAntiAlias(true);
        float divMaxHR = maxHR > 0f ? maxHR : 1f;
        for (int i = 0; i < 24; i++) { float bCX = rrGL+rrGW-hbGap*i-hbGap/2f; float bH = hourlyRain[i]>0 ? rrGH*(hourlyRain[i]/divMaxHR) : 2f; float bT = rrGT+rrGH-bH;
            hBarP.setShader(new android.graphics.LinearGradient(0, rrGT+rrGH, 0, bT, android.graphics.Color.parseColor("#1B6ADF"), android.graphics.Color.parseColor("#4FA5F7"), android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(bCX-hbW/2f, bT, bCX+hbW/2f, rrGT+rrGH+4f*S, 3f*S, 3f*S, hBarP); }
        for (int hr = 0; hr <= 24; hr += 6) { float x = rrGL+rrGW*(1f-hr/24f); c.drawLine(x, rrGT, x, rrGT+rrGH, gridP);
            String sl = new SimpleDateFormat("HH", Locale.getDefault()).format(new Date(System.currentTimeMillis()-hr*3_600_000L));
            c.drawText(sl, x-lblP.measureText(sl)/2f, rrGT+rrGH+xAxisYOffset, lblP); }
        }

        // --- Solstraling ---
        drawWidgetCard(c, col2L, r2Top, col2R, r2Bot, S);
        c.drawText("SOL", col2L+wPad, r2Top+hdrOff, lblHdr);
        float sTotal = 0f; for (float v : valSolar24h) sTotal += v;
        String solValStr = String.format(Locale.getDefault(), "I dag %.2f kWh/m\u00b2", sTotal);
        c.drawText(solValStr, col2R-wPad-lblVal.measureText(solValStr), r2Top+hdrOff, lblVal);
        float solGL = col2L+gLeft, solGT = r2Top+gTopOff, solGW = tGW, solGH = tGH;
        float sMaxVal = 0.1f; for (float v : valSolar24h) sMaxVal = Math.max(sMaxVal, v);
        float sGp = 1f*S, sBW = (solGW-23f*sGp)/24f;
        Paint sBarP = new Paint(); sBarP.setAntiAlias(true);
        for (int i = 0; i < 24; i++) { float x = solGL+(23-i)*(sBW+sGp); float bH = valSolar24h[i]>0 ? (valSolar24h[i]/sMaxVal)*solGH : 2f; float top = solGT+solGH-bH;
            sBarP.setShader(new android.graphics.LinearGradient(0, solGT+solGH, 0, top, android.graphics.Color.parseColor("#E68A4F"), android.graphics.Color.parseColor("#FFC107"), android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(x, top, x+sBW, solGT+solGH+4f*S, 3f*S, 3f*S, sBarP); }
        for (int hr = 0; hr <= 24; hr += 6) { float x = solGL+solGW*(1f-hr/24f); c.drawLine(x, solGT, x, solGT+solGH, gridP);
            String sl = new SimpleDateFormat("H", Locale.getDefault()).format(new Date(System.currentTimeMillis()-hr*3_600_000L));
            c.drawText(sl, x-lblP.measureText(sl)/2f, solGT+solGH+xAxisYOffset, lblP); }

        // --- Jordfuktighet ---
        drawWidgetCard(c, col3L, r2Top, col3R, r2Bot, S);
        c.drawText("JORD", col3L+wPad, r2Top+hdrOff, lblHdr);
        float rangeS = maxS - minS; if (rangeS < 1f) rangeS = 1f;
        String soilStr = hasSoilData ? String.format(Locale.getDefault(), "%.0f%%", curSoil) : "?";
        c.drawText(soilStr, col3R-wPad-lblVal.measureText(soilStr), r2Top+hdrOff, lblVal);
        float sGL = col3L+gLeft, sGT = r2Top+gTopOff, sGW = tGW, sGH = tGH;
        for (int i = 0; i <= 4; i++) { float frac = i/4f, y = sGT+sGH*(1-frac); c.drawLine(sGL, y, sGL+sGW, y, gridP); c.drawText(String.format(Locale.getDefault(), "%.0f%%", minS+rangeS*frac), sGL-4f*S, y+6f*S, lblPy); }
        if (hasSoilData) {
            String[] soilDays = {"N\u00e5", "1d", "2d", "3d"};
            for (int d = 0; d <= 3; d++) {
                float x = sGL+sGW*(1f-d/3f);
                c.drawLine(x, sGT, x, sGT+sGH, gridP);
                c.drawText(soilDays[d], x-lblP.measureText(soilDays[d])/2f, sGT+sGH+xAxisYOffset, lblP);
            }
            Paint fillPS = new Paint(); fillPS.setColor(android.graphics.Color.parseColor("#1A3B82F6")); fillPS.setStyle(Paint.Style.FILL); fillPS.setAntiAlias(true);
            Path fillPathS = new Path(); fillPathS.moveTo(sGL+sGW*(1f-soilPoints.get(0)[0]/72f), sGT+sGH);
            for (float[] p : soilPoints) fillPathS.lineTo(sGL+sGW*(1f-p[0]/72f), sGT+sGH*(1f-(p[1]-minS)/rangeS));
            fillPathS.lineTo(sGL+sGW*(1f-soilPoints.get(soilPoints.size()-1)[0]/72f), sGT+sGH); fillPathS.close(); c.drawPath(fillPathS, fillPS);
            Paint linePS = new Paint(); linePS.setColor(android.graphics.Color.parseColor("#3B82F6")); linePS.setStrokeWidth(3f*S); linePS.setStyle(Paint.Style.STROKE); linePS.setAntiAlias(true); linePS.setStrokeJoin(Paint.Join.ROUND); linePS.setStrokeCap(Paint.Cap.ROUND);
            Path linePathS = new Path(); boolean firstS = true;
            for (float[] p : soilPoints) { float x = sGL+sGW*(1f-p[0]/72f), y = sGT+sGH*(1f-(p[1]-minS)/rangeS); if (firstS) { linePathS.moveTo(x, y); firstS = false; } else linePathS.lineTo(x, y); }
            c.drawPath(linePathS, linePS);
        } else {
            for (int d = 0; d <= 3; d++) {
                float x = sGL+sGW*(1f-d/3f);
                c.drawLine(x, sGT, x, sGT+sGH, gridP);
            }
            Paint noDP = new Paint(); noDP.setColor(android.graphics.Color.GRAY); noDP.setTextSize(baseTxt); noDP.setAntiAlias(true); c.drawText("Ingen data", sGL+sGW*0.2f, sGT+sGH/2f, noDP);
        }

        // === ROW 3–4: Camera widgets (Versjon 2 only) ===
        if (detailPageVersion == 2) {
            float r4Bot = r4Top + camRowH;
            drawCameraWidget(c, weatherBitmap, "V\u00c6R", weatherTimestamp, col1L, r3Top, col2R, r3Bot, false, false, S, wPad, hdrOff, lblHdr, lblValNormal);
            drawCameraWidget(c, cameraBitmap, "G\u00c5RDSPLASSEN", imageTimestamp, col3L, r3Top, col3R, r3Bot, false, true, S, wPad, hdrOff, lblHdr, lblValNormal);
            drawCameraWidget(c, mailboxBitmap, "POSTKASSEN", mailboxTimestamp, col3L, r4Top, col3R, r4Bot, false, false, S, wPad, hdrOff, lblHdr, lblValNormal);
        }

        // === ROW 3: Cameras + room cards (Versjon 1 only) ===
        if (detailPageVersion == 1) {
        float camW = (w - gap) / 2f;
        drawCameraWidget(c, cameraBitmap, "G\u00c5RDSPLASSEN", imageTimestamp, 0f, r3Top, camW, r3Bot, false, true, S, wPad, hdrOff, lblHdr, lblValNormal);
        drawCameraWidget(c, mailboxBitmap, "POSTKASSEN", mailboxTimestamp, camW+gap, r3Top, (float) w, r3Bot, false, false, S, wPad, hdrOff, lblHdr, lblValNormal);

        // === ROOM TEMPERATURES ===
        float gapRoom = 8f * S;
        float roomCardH = (roomH - 3f * gapRoom) / 4f;
        float gridY = r4Top;

        // Row 1: 3 cards (Attic / Loft level)
        float rw3 = ((float) w - 2f * gapRoom) / 3f;
        drawRoomCard(c, "Jonatan", valJonatan, 0f, gridY, rw3, roomCardH, valJonatanMotionTime);
        drawRoomCard(c, "Loftsgang", valLoftsgang, rw3+gapRoom, gridY, rw3, roomCardH, valLoftsgangMotionTime);
        drawRoomCard(c, "Kontor", valKontor, 2f*(rw3+gapRoom), gridY, rw3, roomCardH, 0L);

        gridY += roomCardH + gapRoom;

        // Row 2: 4 cards (First floor / Overetasje)
        float rw4 = ((float) w - 3f * gapRoom) / 4f;
        drawRoomCard(c, "Bad", valBad, 0f, gridY, rw4, roomCardH, valBadMotionTime);
        drawRoomCard(c, "Kj\u00f8kken", valKjokken, rw4+gapRoom, gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Lite bad", valLiteBad, 2f*(rw4+gapRoom), gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Mats", valMats, 3f*(rw4+gapRoom), gridY, rw4, roomCardH, 0L);

        gridY += roomCardH + gapRoom;

        // Row 3: 4 cards (Main floor / Hovedetasje)
        drawRoomCard(c, "Vinterhage", valVinterhage, 0f, gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Stue", valStue, rw4+gapRoom, gridY, rw4, roomCardH, valStueMotionTime);
        drawRoomCard(c, "Gang", valGang3, 2f*(rw4+gapRoom), gridY, rw4, roomCardH, 0L);
        drawRoomCard(c, "Soverom", valSoverom, 3f*(rw4+gapRoom), gridY, rw4, roomCardH, 0L);

        gridY += roomCardH + gapRoom;

        // Row 4: 2 cards (Basement / Underetasje)
        float rw2 = ((float) w - gapRoom) / 2f;
        drawRoomCard(c, "Gang", valGang4, 0f, gridY, rw2, roomCardH, valGang4MotionTime);
        drawRoomCard(c, "Vaskerom", valVaskerom, rw2+gapRoom, gridY, rw2, roomCardH, valVaskeromMotionTime);
        }

        return bmp;
    }

    private float fetchSensorState(String entityId, float fallbackValue) {
        try {
            String urlStr = BuildConfig.HA_BASE_URL + "/api/states/" + entityId;
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                conn.disconnect();
                org.json.JSONObject obj = new org.json.JSONObject(json);
                return Float.parseFloat(obj.getString("state"));
            } else {
                conn.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.e("GrefsenveienApp", "Failed to fetch state for " + entityId, e);
        }
        return fallbackValue;
    }

    private long fetchMotionHistory(String entityId, long now, java.text.SimpleDateFormat isoFmt) {
        long motionTime = 0L;
        try {
            String urlStr = BuildConfig.HA_BASE_URL + "/api/history/period/"
                    + isoFmt.format(new java.util.Date(now - 24L * 3600_000))
                    + "?filter_entity_id=" + entityId
                    + "&end_time=" + isoFmt.format(new java.util.Date(now));
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.HA_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                conn.disconnect();
                org.json.JSONArray outer = new org.json.JSONArray(json);
                if (outer.length() > 0) {
                    org.json.JSONArray states = outer.getJSONArray(0);
                    for (int i = 0; i < states.length(); i++) {
                        org.json.JSONObject obj = states.getJSONObject(i);
                        String state = obj.getString("state");
                        if ("on".equals(state)) {
                            long ts = parseIsoTimestamp(obj.getString("last_changed"));
                            if (ts > motionTime) {
                                motionTime = ts;
                            }
                        }
                    }
                }
            } else {
                conn.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.w("GrefsenveienApp", "Failed to fetch motion history for " + entityId, e);
        }
        return motionTime;
    }

    private int interpolateColor(int c1, int c2, float fraction) {
        int r1 = android.graphics.Color.red(c1);
        int g1 = android.graphics.Color.green(c1);
        int b1 = android.graphics.Color.blue(c1);
        
        int r2 = android.graphics.Color.red(c2);
        int g2 = android.graphics.Color.green(c2);
        int b2 = android.graphics.Color.blue(c2);
        
        int r = (int) (r1 + (r2 - r1) * fraction);
        int g = (int) (g1 + (g2 - g1) * fraction);
        int b = (int) (b1 + (b2 - b1) * fraction);
        
        return android.graphics.Color.rgb(r, g, b);
    }

    private int getTemperatureColor(float temp) {
        int c15 = android.graphics.Color.parseColor("#481581");
        int c18 = android.graphics.Color.parseColor("#6ac6ef");
        int c22 = android.graphics.Color.parseColor("#0eb30e");
        int c24 = android.graphics.Color.parseColor("#ffb37a");
        int c29 = android.graphics.Color.parseColor("#78003d");
        
        if (temp <= 15f) {
            return c15;
        } else if (temp < 18f) {
            float frac = (temp - 15f) / (18f - 15f);
            return interpolateColor(c15, c18, frac);
        } else if (temp < 22f) {
            float frac = (temp - 18f) / (22f - 18f);
            return interpolateColor(c18, c22, frac);
        } else if (temp < 24f) {
            float frac = (temp - 22f) / (24f - 22f);
            return interpolateColor(c22, c24, frac);
        } else if (temp < 29f) {
            float frac = (temp - 24f) / (29f - 24f);
            return interpolateColor(c24, c29, frac);
        } else {
            return c29;
        }
    }

    private void drawRoomCard(android.graphics.Canvas canvas, String name, float temp, float x, float y, float w, float h, long motionTime) {
        int color = getTemperatureColor(temp);
        float radius = h * 0.16f;
        float strokeW = Math.max(1.5f, h * 0.04f);
        
        android.graphics.Paint baseBgPaint = new android.graphics.Paint();
        baseBgPaint.setStyle(android.graphics.Paint.Style.FILL);
        baseBgPaint.setColor(android.graphics.Color.parseColor("#11141E")); // Dark charcoal background
        baseBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, baseBgPaint);

        android.graphics.Paint tintBgPaint = new android.graphics.Paint();
        tintBgPaint.setStyle(android.graphics.Paint.Style.FILL);
        int tintColor = android.graphics.Color.argb(30, 
                android.graphics.Color.red(color), 
                android.graphics.Color.green(color), 
                android.graphics.Color.blue(color)); // 12% opacity tint
        tintBgPaint.setColor(tintColor);
        tintBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, tintBgPaint);
        
        android.graphics.Paint strokePaint = new android.graphics.Paint();
        strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeW);
        strokePaint.setColor(color);
        strokePaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, strokePaint);
        
        float textSize = Math.min(16f, h * 0.26f);
        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(textSize);
        textPaint.setColor(android.graphics.Color.parseColor("#E1E4EA"));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float nameWidth = textPaint.measureText(name);

        boolean recentlyDetected = false;
        String motionTimeStr = "";
        
        if (motionTime > 0) {
            long diff = System.currentTimeMillis() - motionTime;
            if (diff >= 0 && diff <= 3600_000L) {
                recentlyDetected = true;
                motionTimeStr = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date(motionTime));
            }
        }

        canvas.drawText(name, x + (w - nameWidth) / 2f, y + h * 0.36f, textPaint);
        
        android.graphics.Paint tempPaint = new android.graphics.Paint();
        tempPaint.setAntiAlias(true);
        tempPaint.setTextSize(textSize * 1.15f);
        tempPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tempPaint.setColor(color);
        String tempStr = String.format(Locale.getDefault(), "%.1f°", temp);
        float tempWidth = tempPaint.measureText(tempStr);
        canvas.drawText(tempStr, x + (w - tempWidth) / 2f, y + h * 0.83f, tempPaint);

        if (recentlyDetected) {
            float S = w / 164f; // Local scale factor for card
            android.graphics.Paint motionPaint = new android.graphics.Paint();
            motionPaint.setAntiAlias(true);
            motionPaint.setTextSize(textSize);
            motionPaint.setColor(android.graphics.Color.parseColor("#8E9AA8"));
            motionPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            // Draw on vertical layout (på høykant) along the right edge
            canvas.save();
            float cx = x + w - Math.max(10f, 12f * S);
            float cy = y + h / 2f;
            canvas.translate(cx, cy);
            canvas.rotate(-90f);
            float txtW = motionPaint.measureText(motionTimeStr);
            canvas.drawText(motionTimeStr, -txtW / 2f, motionPaint.getTextSize() * 0.36f, motionPaint);
            canvas.restore();
        }
    }

    private void drawWidgetCard(android.graphics.Canvas canvas, float left, float top, float right, float bottom, float S) {
        float cornerRadius = 18f;
        android.graphics.Paint bg = new android.graphics.Paint();
        bg.setColor(android.graphics.Color.parseColor("#151821"));
        bg.setStyle(android.graphics.Paint.Style.FILL);
        bg.setAntiAlias(true);
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, bg);
        drawWidgetCardBorder(canvas, left, top, right, bottom, S);
    }

    private void drawWidgetCardBorder(android.graphics.Canvas canvas, float left, float top, float right, float bottom, float S) {
        float cornerRadius = 18f;
        android.graphics.Paint stroke = new android.graphics.Paint();
        stroke.setColor(android.graphics.Color.parseColor("#222633"));
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        if (detailPageVersion == 2) {
            float strokeWidth = Math.max(1f, 1.5f * S);
            stroke.setStrokeWidth(strokeWidth);
            stroke.setAntiAlias(false);
            float inset = strokeWidth / 2f;
            canvas.drawRoundRect(
                    left + inset, top + inset, right - inset, bottom - inset,
                    Math.max(0f, cornerRadius - inset), Math.max(0f, cornerRadius - inset),
                    stroke);
        } else {
            stroke.setStrokeWidth(3f);
            stroke.setAntiAlias(true);
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, stroke);
        }
    }

    private void drawThermometerIcon(android.graphics.Canvas canvas, float x, float y, float size, float S, android.graphics.Paint paint) {
        android.graphics.Paint p = new android.graphics.Paint(paint);
        p.setStyle(android.graphics.Paint.Style.FILL);
        
        float cx = x + size * 0.5f;
        float cy = y + size * 0.70f;
        float rBulb = size * 0.20f;
        float wStem = size * 0.08f;
        float topStem = y + size * 0.10f;
        float botStem = cy - rBulb * 0.5f;
        
        android.graphics.Path path = new android.graphics.Path();
        path.arcTo(new android.graphics.RectF(cx - wStem, topStem, cx + wStem, topStem + wStem * 2f), 180, 180, false);
        path.lineTo(cx + wStem, botStem);
        path.arcTo(new android.graphics.RectF(cx - rBulb, cy - rBulb, cx + rBulb, cy + rBulb), -120, 300, false);
        path.close();
        
        canvas.drawPath(path, p);
    }

    private void drawDropletIcon(android.graphics.Canvas canvas, float x, float y, float size, float S, android.graphics.Paint paint) {
        android.graphics.Paint p = new android.graphics.Paint(paint);
        p.setStyle(android.graphics.Paint.Style.FILL);
        
        float cx = x + size * 0.5f;
        float cy = y + size * 0.65f;
        float r = size * 0.24f;
        
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, y + size * 0.15f);
        path.cubicTo(cx + r * 0.8f, cy - r * 0.5f, cx + r, cy - r * 0.2f, cx + r, cy);
        path.arcTo(new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), 0, 180, false);
        path.cubicTo(cx - r, cy - r * 0.2f, cx - r * 0.8f, cy - r * 0.5f, cx, y + size * 0.15f);
        path.close();
        
        canvas.drawPath(path, p);
    }

    private void drawCameraWidget(android.graphics.Canvas canvas, Bitmap bmp, String title, String tsStr,
            float left, float top, float right, float bottom, boolean fitContain, boolean yardCropOffset,
            float layoutS, float wPad, float hdrOff, Paint lblHdr, Paint lblValNormal) {
        drawWidgetCard(canvas, left, top, right, bottom, layoutS);
        if (bmp != null) {
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(new android.graphics.RectF(left, top, right, bottom), 18f, 18f, android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

            float cardW = right - left;
            float cardH = bottom - top;
            float bmpW = bmp.getWidth();
            float bmpH = bmp.getHeight();
            float scale;
            if (fitContain) {
                scale = Math.min(cardW / bmpW, cardH / bmpH);
            } else {
                scale = Math.max(cardW / bmpW, cardH / bmpH);
                if (yardCropOffset) {
                    scale *= 1.10f;
                }
            }
            float drawW = bmpW * scale;
            float drawH = bmpH * scale;
            float dx = left + (cardW - drawW) / 2f;
            float dy;
            if (yardCropOffset) {
                dy = top + cardH / 2f - 0.40f * drawH;
                dy = Math.max(top + cardH - drawH, Math.min(top, dy));
            } else {
                dy = top + (cardH - drawH) / 2f;
            }
            Rect src = new Rect(0, 0, (int) bmpW, (int) bmpH);
            android.graphics.RectF dst = new android.graphics.RectF(dx, dy, dx + drawW, dy + drawH);
            Paint bmpPaint = new Paint();
            if (detailPageVersion == 2) {
                bmpPaint.setFilterBitmap(false);
                bmpPaint.setAntiAlias(false);
            } else {
                bmpPaint.setFilterBitmap(true);
                bmpPaint.setAntiAlias(true);
            }
            canvas.drawBitmap(bmp, src, dst, bmpPaint);

            Paint bannerP = new Paint();
            bannerP.setColor(android.graphics.Color.argb(160, 0, 0, 0));
            canvas.drawRect(left, top, right, top + hdrOff, bannerP);

            float bannerMidY = top + hdrOff / 2f;
            Paint.FontMetrics hdrFm = lblHdr.getFontMetrics();
            float titleBaselineY = bannerMidY - (hdrFm.ascent + hdrFm.descent) / 2f;
            canvas.drawText(title, left + wPad, titleBaselineY, lblHdr);

            String displayTs = (tsStr != null && !tsStr.isEmpty()) ? tsStr : "Live";
            Paint.FontMetrics tsFm = lblValNormal.getFontMetrics();
            float tsBaselineY = bannerMidY - (tsFm.ascent + tsFm.descent) / 2f;
            canvas.drawText(displayTs, right - wPad - lblValNormal.measureText(displayTs), tsBaselineY, lblValNormal);

            canvas.restore();
            drawWidgetCardBorder(canvas, left, top, right, bottom, layoutS);
        } else {
            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setColor(android.graphics.Color.GRAY);
            textPaint.setTextSize(28f);
            String placeholder = title + " laster...";
            canvas.drawText(placeholder, left + ((right - left) - textPaint.measureText(placeholder)) / 2f, top + (bottom - top) / 2f, textPaint);
        }
    }
}
