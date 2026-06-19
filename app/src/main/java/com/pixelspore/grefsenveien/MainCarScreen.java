package com.pixelspore.grefsenveien;

import androidx.annotation.NonNull;
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
import android.graphics.Typeface;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainCarScreen extends Screen implements SurfaceCallback {

    private static final String MAILBOX_IMAGE_URL = BuildConfig.S3_MAILBOX_IMAGE_URL;

    private enum ViewMode { YARD, MAILBOX, HOME, INFO }
    private ViewMode currentMode = ViewMode.YARD;

    private String garageStatus = "";
    private String gateStatus = "";
    private Bitmap cameraBitmap = null;
    private String imageTimestamp = "";
    private Bitmap mailboxBitmap = null;
    private String mailboxTimestamp = "";
    private Bitmap homeBitmap = null;
    private String homeTimestamp = "";
    private SurfaceContainer mSurfaceContainer;
    private Rect mVisibleArea;

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

    private final android.os.Handler mUpdateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mImageUpdater = new Runnable() {
        @Override
        public void run() {
            fetchImageFromUrl(BuildConfig.S3_IMAGE_URL, false);
            mUpdateHandler.postDelayed(this, 60000); // 60 sekunder
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
            fetchImageFromUrl(MAILBOX_IMAGE_URL, true);
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

    public MainCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        
        // Registrer oss for å få tilgang til tegne-lerretet i Android Auto
        carContext.getCarService(AppManager.class).setSurfaceCallback(this);
        
        // Gjenopprett siste valgte ViewMode
        try {
            android.content.SharedPreferences prefs = carContext.getSharedPreferences("GrefsenveienPrefs", android.content.Context.MODE_PRIVATE);
            String savedMode = prefs.getString("lastViewMode", null);
            if (savedMode != null) {
                currentMode = ViewMode.valueOf(savedMode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        mSurfaceContainer = surfaceContainer;
        drawCameraImage();
        
        // Start automatisk oppdatering hvert minutt
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.post(mImageUpdater);

        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
        mUpdateHandler.post(mDoorbellUpdater);

        mUpdateHandler.removeCallbacks(mMailboxUpdater);
        mUpdateHandler.post(mMailboxUpdater);

        mUpdateHandler.removeCallbacks(mHomeUpdater);
        mUpdateHandler.post(mHomeUpdater);
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        mVisibleArea = visibleArea;
        drawCameraImage();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        mSurfaceContainer = null;
        
        // Stopp automatisk oppdatering når tegneflaten forsvinner
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
        mUpdateHandler.removeCallbacks(mMailboxUpdater);
        mUpdateHandler.removeCallbacks(mHomeUpdater);
    }

    private void drawCameraImage() {
        if (mSurfaceContainer == null || mSurfaceContainer.getSurface() == null) return;

        // --- INFO-modus: tegn direkte på canvas uten bitmap ---
        if (currentMode == ViewMode.INFO) {
            try {
                Canvas canvas = mSurfaceContainer.getSurface().lockCanvas(null);
                if (canvas != null) {
                    int cW = canvas.getWidth(), cH = canvas.getHeight();
                    int vW = mVisibleArea != null ? mVisibleArea.width() : cW;
                    int vH = mVisibleArea != null ? mVisibleArea.height() : cH;
                    int vL = mVisibleArea != null ? mVisibleArea.left : 0;
                    int vT = mVisibleArea != null ? mVisibleArea.top : 0;

                    canvas.drawColor(android.graphics.Color.parseColor("#0D1117"));
                    Paint hP = new Paint(); hP.setAntiAlias(true); hP.setColor(android.graphics.Color.WHITE);
                    hP.setTextSize(80f); hP.setTypeface(Typeface.DEFAULT_BOLD);
                    canvas.drawText("Skjerminfo", vL + 60f, vT + 100f, hP);

                    Paint lP = new Paint(); lP.setAntiAlias(true); lP.setColor(android.graphics.Color.parseColor("#CCCCCC")); lP.setTextSize(52f);
                    String[] lines = {
                        "Canvas:  " + cW + " × " + cH + " px",
                        "Synlig:  " + vW + " × " + vH + " px",
                        "Offset:  " + vL + ", " + vT,
                    };
                    for (int i = 0; i < lines.length; i++)
                        canvas.drawText(lines[i], vL + 60f, vT + 200f + i * 80f, lP);

                    mSurfaceContainer.getSurface().unlockCanvasAndPost(canvas);
                }
            } catch (Exception e) { e.printStackTrace(); }
            return;
        }

        Bitmap displayBitmap = currentMode == ViewMode.MAILBOX ? mailboxBitmap
                             : currentMode == ViewMode.HOME    ? homeBitmap
                             : cameraBitmap;
        String displayTimestamp = currentMode == ViewMode.MAILBOX ? mailboxTimestamp
                                : currentMode == ViewMode.HOME    ? homeTimestamp
                                : imageTimestamp;
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

                // HOME: skalér til å passe begge dimensjoner (fit-both), andre: cover (fyll hele skjermen)
                float scaleW = (float) drawWidth / displayBitmap.getWidth();
                float scaleH = (float) drawHeight / displayBitmap.getHeight();
                float scale;
                if (currentMode == ViewMode.HOME) {
                    scale = Math.min(scaleW, scaleH);
                } else {
                    scale = Math.max(scaleW, scaleH);
                }

                int scaledWidth  = Math.round(displayBitmap.getWidth()  * scale);
                int scaledHeight = Math.round(displayBitmap.getHeight() * scale);
                int left = offsetX + (drawWidth  - scaledWidth)  / 2;
                int top  = offsetY + (drawHeight - scaledHeight) / 2;

                Rect destRect = new Rect(left, top, left + scaledWidth, top + scaledHeight);
                canvas.drawBitmap(displayBitmap, null, destRect, new Paint());

                if (displayTimestamp != null && !displayTimestamp.isEmpty()) {
                    Paint textPaint = new Paint();
                    textPaint.setColor(android.graphics.Color.WHITE);
                    textPaint.setTextSize(24f);
                    textPaint.setAntiAlias(true);
                    
                    int vWidth = mVisibleArea != null ? mVisibleArea.width() : canvas.getWidth();
                    int vHeight = mVisibleArea != null ? mVisibleArea.height() : canvas.getHeight();
                    int vLeft = mVisibleArea != null ? mVisibleArea.left : 0;
                    int vTop = mVisibleArea != null ? mVisibleArea.top : 0;

                    if (currentMode == ViewMode.HOME) {
                        // Place at absolute canvas top, aligned with the Action buttons in the system padding area
                        float textX = 12f;
                        float textY = 56f;
                        canvas.drawText(displayTimestamp, textX, textY, textPaint);
                    } else {
                        // Bottom-right placement with black background box for camera modes
                        Paint bgPaint = new Paint();
                        bgPaint.setColor(android.graphics.Color.BLACK);
                        bgPaint.setAlpha(200);
                        Rect textBounds = new Rect();
                        textPaint.getTextBounds(displayTimestamp, 0, displayTimestamp.length(), textBounds);
                        int textPadding = 16, edgeMargin = 40;
                        
                        int rectLeft   = vLeft + vWidth  - textBounds.width()  - textPadding * 2 - edgeMargin;
                        int rectTop    = vTop + vHeight - textBounds.height() - textPadding * 2 - edgeMargin;
                        int rectRight  = vLeft + vWidth  - edgeMargin;
                        int rectBottom = vTop + vHeight - edgeMargin;
                        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgPaint);
                        canvas.drawText(displayTimestamp, rectLeft + textPadding, rectBottom - textPadding, textPaint);
                    }
                }

                mSurfaceContainer.getSurface().unlockCanvasAndPost(canvas);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip.Builder actionStripBuilder = new ActionStrip.Builder();
        if (currentMode == ViewMode.YARD || currentMode == ViewMode.HOME) {
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
                    .setTitle("Port")
                    .setFlags(Action.FLAG_IS_PERSISTENT)
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_material_outdoor_garden)).build())
                    .setBackgroundColor(CarColor.DEFAULT)
                    .setOnClickListener(() -> makeUrlRequest("porten"))
                    .build());
        }
        actionStripBuilder.addAction(
            new Action.Builder()
                .setTitle("Oppdater")
                .setFlags(Action.FLAG_IS_PERSISTENT)
                .setBackgroundColor(CarColor.DEFAULT)
                .setOnClickListener(() -> {
                    if (currentMode == ViewMode.INFO) {
                        CarToast.makeText(getCarContext(), "Oppdaterer info...", CarToast.LENGTH_SHORT).show();
                        drawCameraImage();
                        invalidate();
                    } else {
                        CarToast.makeText(getCarContext(), "Henter nytt bilde...", CarToast.LENGTH_SHORT).show();
                        if (currentMode == ViewMode.MAILBOX) {
                            mUpdateHandler.removeCallbacks(mMailboxUpdater);
                            mUpdateHandler.post(mMailboxUpdater);
                        } else if (currentMode == ViewMode.HOME) {
                            mUpdateHandler.removeCallbacks(mHomeUpdater);
                            mUpdateHandler.post(mHomeUpdater);
                        } else {
                            mUpdateHandler.removeCallbacks(mImageUpdater);
                            mUpdateHandler.post(mImageUpdater);
                        }
                    }
                })
                .build());
        ActionStrip actionStrip = actionStripBuilder.build();

        ActionStrip mapActionStrip = new ActionStrip.Builder()
            .addAction(
                new Action.Builder()
                    .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                        currentMode == ViewMode.YARD ? R.drawable.ic_tab_yard : R.drawable.ic_tab_yard_outline)).build())
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
                        currentMode == ViewMode.INFO ? R.drawable.ic_tab_info : R.drawable.ic_tab_info_outline)).build())
                    .setOnClickListener(() -> {
                        currentMode = ViewMode.INFO;
                        saveViewMode(ViewMode.INFO);
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

    private void saveViewMode(ViewMode mode) {
        try {
            android.content.SharedPreferences prefs = getCarContext().getSharedPreferences("GrefsenveienPrefs", android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("lastViewMode", mode.name()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchImageFromUrl(String imageUrl, boolean isMailbox) {
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
                            if (isMailbox) {
                                mailboxBitmap = bitmap;
                                mailboxTimestamp = newTimestamp;
                            } else {
                                cameraBitmap = bitmap;
                                imageTimestamp = newTimestamp;
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
        // Vis en umiddelbar beskjed om at vi prøver å sende signalet
        String statusText;
        if (targetName.equals("garasjen")) {
            statusText = "Åpner/lukker Garasje...";
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

                String payload = "{\"token\":\"Xi3gQF4GTFR7aENMkMjftt4P\",\"user\":\"" + savedEmail + "\"}";

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
                    } else {
                        resultMsg = "Feilkode " + responseCode + " (" + targetName + ")";
                    }
                    CarToast.makeText(getCarContext(), resultMsg, CarToast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                getCarContext().getMainExecutor().execute(() -> {
                    String errorMsg = "Nettverksfeil for " + targetName;
                    CarToast.makeText(getCarContext(), errorMsg, CarToast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Home Assistant – temperaturhistorikk
    // -------------------------------------------------------------------------

    private void fetchHomeAssistantData() {
        new Thread(() -> {
            long now = System.currentTimeMillis();
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            List<float[]> tempPoints = new ArrayList<>();
            float[] rainByDay = new float[7];
            float[] hourlyRain = new float[24];
            List<float[]> soilPoints = new ArrayList<>();
            float[] hourlySolar = new float[24];

            // 1. Fetch Temp
            try {
                String tempUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 24L * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_temp"
                        + "&end_time=" + isoFmt.format(new Date(now));
                HttpURLConnection conn = (HttpURLConnection) new URL(tempUrl).openConnection();
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
                                float temp = Float.parseFloat(obj.getString("state"));
                                long ts = parseIsoTimestamp(obj.getString("last_changed"));
                                float hoursAgo = (now - ts) / 3_600_000f;
                                if (hoursAgo >= 0 && hoursAgo <= 24) {
                                    tempPoints.add(new float[]{hoursAgo, temp});
                                    lastVal = temp;
                                    hasVal = true;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (hasVal) {
                            tempPoints.add(new float[]{0f, lastVal});
                        }
                    }
                } else { conn.disconnect(); }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch vaerstasjon_temp", e);
            }

            // 2. Fetch Rain 7d
            try {
                String rainUrl = BuildConfig.HA_BASE_URL + "/api/history/period/"
                        + isoFmt.format(new Date(now - 7L * 24 * 3600_000))
                        + "?filter_entity_id=sensor.vaerstasjon_daily_rain"
                        + "&end_time=" + isoFmt.format(new Date(now));
                HttpURLConnection conn = (HttpURLConnection) new URL(rainUrl).openConnection();
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
                        SimpleDateFormat dayFmt2 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        Calendar cal = Calendar.getInstance();
                        for (int i = 0; i < 7; i++) {
                            Float val = maxPerDay.get(dayFmt2.format(cal.getTime()));
                            rainByDay[i] = val != null ? val : 0f;
                            cal.add(Calendar.DAY_OF_MONTH, -1);
                        }
                    }
                } else { conn.disconnect(); }
            } catch (Exception e) {
                Log.w("GrefsenveienApp", "Failed to fetch daily_rain", e);
            }

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

            // Always ensure tempPoints is not empty to render dashboard cleanly
            float curTemp = 15f;
            if (tempPoints.isEmpty()) {
                tempPoints.add(new float[]{0f, 15f});
            } else {
                curTemp = tempPoints.get(tempPoints.size() - 1)[1];
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
                int chartW = mSurfaceContainer != null
                        ? mSurfaceContainer.getWidth() : 1440;
                int chartH = mSurfaceContainer != null
                        ? mSurfaceContainer.getHeight() : 2080;
                Bitmap chart = renderTemperatureChart(tempPoints, rainByDay, hourlyRain, soilPoints, chartW, chartH);
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

    private Bitmap renderTemperatureChart(List<float[]> points, float[] rainByDay, float[] hourlyRain, List<float[]> soilPoints, int w, int h) {
        points.sort((a, b) -> Float.compare(b[0], a[0])); // eldst → nyest

        float minT = Float.MAX_VALUE, maxT = -Float.MAX_VALUE;
        for (float[] p : points) { minT = Math.min(minT, p[1]); maxT = Math.max(maxT, p[1]); }
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
        Paint barLblP = new Paint(); barLblP.setAntiAlias(true); barLblP.setColor(android.graphics.Color.WHITE); barLblP.setTextSize(axisTxt);
        Paint dayLblP = new Paint(); dayLblP.setAntiAlias(true); dayLblP.setColor(android.graphics.Color.WHITE); dayLblP.setTextSize(axisTxt);

        // === 3-COLUMN GRID ===
        float gap = 14f * S;
        float colW = (w - 2f * gap) / 3f;
        float col1L = 0f, col1R = colW;
        float col2L = colW + gap, col2R = 2f * colW + gap;
        float col3L = 2f * (colW + gap), col3R = (float) w;
        float curTemp = points.get(points.size() - 1)[1];

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

        // === ROW 1: Utetemperatur | Aktuelt temp | Regn 7 dager ===
        drawWidgetCard(c, col1L, r1Top, col1R, r1Bot);
        c.drawText("UTE 24t", col1L + wPad, r1Top + hdrOff, lblHdr);
        String tHdr = String.format(Locale.getDefault(), "%.1f\u00b0\u2013%.1f\u00b0", minT, maxT);
        c.drawText(tHdr, col1R - wPad - lblVal.measureText(tHdr), r1Top + hdrOff, lblVal);
        float tGL = col1L + gLeft, tGT = r1Top + gTopOff;
        for (int i = 0; i <= 4; i++) { float frac = i/4f, y = tGT + tGH*(1-frac); c.drawLine(tGL, y, tGL+tGW, y, gridP); c.drawText(String.format(Locale.getDefault(), "%.0f\u00b0", minT+range*frac), tGL-4f*S, y+6f*S, lblPy); }
        for (int hr = 0; hr <= 24; hr += 6) { float x = tGL+tGW*(1f-hr/24f); c.drawLine(x, tGT, x, tGT+tGH, gridP);
            String sl = new SimpleDateFormat("HH", Locale.getDefault()).format(new Date(System.currentTimeMillis()-hr*3_600_000L));
            c.drawText(sl, x-lblP.measureText(sl)/2f, tGT+tGH+xAxisYOffset, lblP); }
        Paint fillP = new Paint(); fillP.setColor(android.graphics.Color.parseColor("#1A3B82F6")); fillP.setStyle(Paint.Style.FILL); fillP.setAntiAlias(true);
        Path fillPath = new Path(); fillPath.moveTo(tGL+tGW*(1f-points.get(0)[0]/24f), tGT+tGH);
        for (float[] p : points) fillPath.lineTo(tGL+tGW*(1f-p[0]/24f), tGT+tGH*(1f-(p[1]-minT)/range));
        fillPath.lineTo(tGL+tGW*(1f-points.get(points.size()-1)[0]/24f), tGT+tGH); fillPath.close(); c.drawPath(fillPath, fillP);
        Paint lineP = new Paint(); lineP.setColor(android.graphics.Color.parseColor("#3B82F6")); lineP.setStrokeWidth(3f*S); lineP.setStyle(Paint.Style.STROKE); lineP.setAntiAlias(true); lineP.setStrokeJoin(Paint.Join.ROUND); lineP.setStrokeCap(Paint.Cap.ROUND);
        Path linePath = new Path(); boolean first = true;
        for (float[] p : points) { float x = tGL+tGW*(1f-p[0]/24f), y = tGT+tGH*(1f-(p[1]-minT)/range); if (first) { linePath.moveTo(x, y); first = false; } else linePath.lineTo(x, y); }
        c.drawPath(linePath, lineP);

        // --- Aktuelt Utetemperatur ---
        drawWidgetCard(c, col2L, r1Top, col2R, r1Bot);
        c.drawText("N\u00c5", col2L + wPad, r1Top + hdrOff, lblHdr);
        Paint naP = new Paint(); naP.setAntiAlias(true); naP.setColor(android.graphics.Color.WHITE); naP.setTextSize(44f*S); naP.setTypeface(Typeface.DEFAULT_BOLD);
        String tempStr = String.format(Locale.getDefault(), "%.1f\u00b0C", curTemp);
        String humStr = String.format(Locale.getDefault(), "%.0f%%", valHumidity);
        float tempTextW = naP.measureText(tempStr);
        float humTextW = naP.measureText(humStr);
        float iconSize = 36f * S;
        float iconTextGap = 6f * S;
        float groupGap = 20f * S;
        float totalW = iconSize + iconTextGap + tempTextW + groupGap + iconSize + iconTextGap + humTextW;
        float startX = col2L + (colW - totalW) / 2f;
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

        // --- Regn 7 dager ---
        drawWidgetCard(c, col3L, r1Top, col3R, r1Bot);
        c.drawText("REGN 7d", col3L + wPad, r1Top + hdrOff, lblHdr);
        float totalRain = 0; for (float v : rainByDay) totalRain += v;
        c.drawText(String.format(Locale.getDefault(), "%.1f mm", totalRain), col3R-wPad-lblVal.measureText(String.format(Locale.getDefault(), "%.1f mm", totalRain)), r1Top+hdrOff, lblVal);
        float r7GL = col3L+gLeft, r7GT = r1Top+gTopOff, r7GW = tGW, r7GH = tGH;
        c.drawLine(r7GL, r7GT, r7GL+r7GW, r7GT, gridP); c.drawLine(r7GL, r7GT+r7GH, r7GL+r7GW, r7GT+r7GH, gridP);
        float maxRain = 1f; for (float r : rainByDay) maxRain = Math.max(maxRain, r);
        c.drawText(String.format(Locale.getDefault(), "%.1f", maxRain), r7GL-4f*S, r7GT+14f*S, lblPy);
        float bGap = r7GW/7f, bW = bGap*0.55f;
        String[] dayNames = new String[7];
        dayNames[0] = "I dag";
        java.text.SimpleDateFormat daySdf = new java.text.SimpleDateFormat("E", new java.util.Locale("no", "NO"));
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 1; i < 7; i++) {
            java.util.Calendar tempCal = (java.util.Calendar) cal.clone();
            tempCal.add(java.util.Calendar.DAY_OF_YEAR, -i);
            String name = daySdf.format(tempCal.getTime());
            if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
            if (name.length() > 0) name = name.substring(0, 1).toUpperCase() + name.substring(1);
            dayNames[i] = name;
        }
        Paint barP = new Paint(); barP.setAntiAlias(true);
        for (int i = 0; i < 7; i++) { float bCX = r7GL+r7GW-bGap*i-bGap/2f; float bH = rainByDay[i]>0 ? r7GH*(rainByDay[i]/maxRain) : 3f; float bT = r7GT+r7GH-bH;
            barP.setShader(new android.graphics.LinearGradient(0, r7GT+r7GH, 0, bT, android.graphics.Color.parseColor("#1B6ADF"), android.graphics.Color.parseColor("#4FA5F7"), android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(bCX-bW/2f, bT, bCX+bW/2f, r7GT+r7GH+6f*S, 6f*S, 6f*S, barP);
            c.drawText(dayNames[i], bCX-dayLblP.measureText(dayNames[i])/2f, r7GT+r7GH+xAxisYOffset, dayLblP); }

        // === ROW 2: Regnstyrke | Solstraling | Jordfuktighet ===
        // --- Regnstyrke ---
        drawWidgetCard(c, col1L, r2Top, col1R, r2Bot);
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

        // --- Solstraling ---
        drawWidgetCard(c, col2L, r2Top, col2R, r2Bot);
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
        drawWidgetCard(c, col3L, r2Top, col3R, r2Bot);
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

        // === ROW 3: Cameras ===
        float camW = (w - gap) / 2f;
        drawCameraWidget(c, cameraBitmap, "G\u00c5RDSPLASSEN", imageTimestamp, 0f, r3Top, camW, r3Bot);
        drawCameraWidget(c, mailboxBitmap, "POSTKASSEN", mailboxTimestamp, camW+gap, r3Top, (float) w, r3Bot);

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

    private void drawWidgetCard(android.graphics.Canvas canvas, float left, float top, float right, float bottom) {
        android.graphics.Paint bg = new android.graphics.Paint();
        bg.setColor(android.graphics.Color.parseColor("#151821"));
        bg.setStyle(android.graphics.Paint.Style.FILL);
        bg.setAntiAlias(true);
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, bg);
        
        android.graphics.Paint stroke = new android.graphics.Paint();
        stroke.setColor(android.graphics.Color.parseColor("#222633"));
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setAntiAlias(true);
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, stroke);
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

    private void drawCameraWidget(android.graphics.Canvas canvas, Bitmap bmp, String title, String tsStr, float left, float top, float right, float bottom) {
        drawWidgetCard(canvas, left, top, right, bottom);
        if (bmp != null) {
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(new android.graphics.RectF(left, top, right, bottom), 18f, 18f, android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            
            float cardW = right - left;
            float cardH = bottom - top;
            float bmpW = bmp.getWidth();
            float bmpH = bmp.getHeight();
            float scale = Math.max(cardW / bmpW, cardH / bmpH);
            if (title != null && (title.equals("G\u00c5RDSPLASSEN") || title.equals("GÅRDSPLASSEN"))) {
                scale *= 1.10f;
            }
            float drawW = bmpW * scale;
            float drawH = bmpH * scale;
            float dx = left + (cardW - drawW) / 2f;
            float dy;
            if (title != null && title.equals("GÅRDSPLASSEN")) {
                dy = top + cardH / 2f - 0.40f * drawH;
                dy = Math.max(top + cardH - drawH, Math.min(top, dy));
            } else {
                dy = top + (cardH - drawH) / 2f;
            }
            Rect src = new Rect(0, 0, (int)bmpW, (int)bmpH);
            android.graphics.RectF dst = new android.graphics.RectF(dx, dy, dx + drawW, dy + drawH);
            canvas.drawBitmap(bmp, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            
            // Scaled banner and overlays
            float S = cardW / 712f;
            float bannerH = Math.max(30f, 40f * S);
            Paint bannerP = new Paint();
            bannerP.setColor(android.graphics.Color.argb(160, 0, 0, 0));
            canvas.drawRect(left, top, right, top + bannerH, bannerP);
            
            Paint bannerText = new Paint();
            bannerText.setAntiAlias(true);
            bannerText.setColor(android.graphics.Color.WHITE);
            bannerText.setTextSize(Math.max(17f, 23f * S));
            bannerText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(title, left + 22f * S, top + bannerH * 0.65f, bannerText);
            
            Paint tsText = new Paint();
            tsText.setAntiAlias(true);
            tsText.setColor(android.graphics.Color.parseColor("#CCCCCC"));
            tsText.setTextSize(Math.max(17f, 23f * S));
            String displayTs = (tsStr != null && !tsStr.isEmpty()) ? tsStr : "Live";
            canvas.drawText(displayTs, right - 22f * S - tsText.measureText(displayTs), top + bannerH * 0.65f, tsText);
            
            canvas.restore();
            
            // Draw border again on top of image
            android.graphics.Paint stroke = new android.graphics.Paint();
            stroke.setColor(android.graphics.Color.parseColor("#222633"));
            stroke.setStyle(android.graphics.Paint.Style.STROKE);
            stroke.setStrokeWidth(3f);
            stroke.setAntiAlias(true);
            canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, stroke);
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
