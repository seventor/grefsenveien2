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

    private float valSolarHourly = 0f;
    private float[] valSolar24h = new float[24];

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

                int drawWidth = mVisibleArea != null ? mVisibleArea.width() : canvas.getWidth();
                int drawHeight = mVisibleArea != null ? mVisibleArea.height() : canvas.getHeight();
                int offsetX = mVisibleArea != null ? mVisibleArea.left : 0;
                int offsetY = mVisibleArea != null ? mVisibleArea.top : 0;

                // HOME: skalér til å passe begge dimensjoner (fit-both), andre: skalér etter bredde
                float scale;
                if (currentMode == ViewMode.HOME) {
                    float scaleW = (float) drawWidth / displayBitmap.getWidth();
                    float scaleH = (float) drawHeight / displayBitmap.getHeight();
                    scale = Math.min(scaleW, scaleH);
                } else {
                    scale = (float) drawWidth / displayBitmap.getWidth();
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
                    textPaint.setTextSize(26f); textPaint.setAntiAlias(true);
                    Paint bgPaint = new Paint();
                    bgPaint.setColor(android.graphics.Color.BLACK); bgPaint.setAlpha(200);
                    Rect textBounds = new Rect();
                    textPaint.getTextBounds(displayTimestamp, 0, displayTimestamp.length(), textBounds);
                    int textPadding = 16, edgeMargin = 40;
                    int rectLeft   = offsetX + drawWidth  - textBounds.width()  - textPadding * 2 - edgeMargin;
                    int rectTop    = offsetY + drawHeight - textBounds.height() - textPadding * 2 - edgeMargin;
                    int rectRight  = offsetX + drawWidth  - edgeMargin;
                    int rectBottom = offsetY + drawHeight - edgeMargin;
                    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgPaint);
                    canvas.drawText(displayTimestamp, rectLeft + textPadding, rectBottom - textPadding, textPaint);
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
        
        if (targetName.equals("garasjen") && (savedEmail == null || savedEmail.isEmpty())) {
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
                
                if (targetName.equals("garasjen")) {
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    
                    String payload = "{\"token\":\"Xi3gQF4GTFR7aENMkMjftt4P\",\"user\":\"" + savedEmail + "\"}";
                    
                    try (java.io.OutputStream os = connection.getOutputStream()) {
                        byte[] input = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                } else {
                    connection.setRequestMethod("GET");
                    connection.connect();
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
            valGang4 = fetchSensorState("sensor.inngang_temperatur_temperature", curTemp - 0.7f);
            valVaskerom = fetchSensorState("sensor.vaskerom_temperatur_temperature", curTemp + 0.4f);

            try {
                Bitmap chart = renderTemperatureChart(tempPoints, rainByDay, hourlyRain, soilPoints, 1440, 2080);
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
        c.drawColor(android.graphics.Color.parseColor("#0D1117"));

        // --- 0. Standardized Widget Grid Layout (2 columns, 4 rows) ---
        Paint gridP = new Paint();
        gridP.setColor(android.graphics.Color.parseColor("#1E2A38")); gridP.setStrokeWidth(2f);
        Paint lblP = new Paint();
        lblP.setAntiAlias(true); lblP.setColor(android.graphics.Color.WHITE); lblP.setTextSize(26f);
        
        Paint lblPy = new Paint();
        lblPy.setAntiAlias(true); lblPy.setColor(android.graphics.Color.WHITE); lblPy.setTextSize(26f);
        lblPy.setTextAlign(Paint.Align.RIGHT);
        
        Paint lblHdr = new Paint(); lblHdr.setAntiAlias(true); lblHdr.setColor(android.graphics.Color.WHITE);
        lblHdr.setTextSize(26f); lblHdr.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        Paint lblVal = new Paint(); lblVal.setAntiAlias(true); lblVal.setColor(android.graphics.Color.WHITE);
        lblVal.setTextSize(26f); lblVal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        float cardW = 712f;
        float gap = 16f;
        float cW = cardW - 105f; // 607f
        float cH = 150f;
        
        float col1Left = 0f;
        float col1Right = cardW;
        float col2Left = cardW + gap; // 728f
        float col2Right = 1440f;
        
        // Midten: Utetemperatur
        float curTemp = points.get(points.size() - 1)[1];

        // ==========================================
        // ROW COORDINATES DEFINITION
        // ==========================================
        // We leave 110f pixels of vertical breathing room at the very top (luft)
        // so that template action buttons on CarPlay/AA do not overlap the widgets.
        float r1Top = 110f, r1Bottom = 390f;
        float r2Top = 414f, r2Bottom = 694f;
        float r3Top = 718f, r3Bottom = 998f;
        
        // --- 1. UTETEMPERATUR WIDGET (Slot 1) ---
        float tLeft = col1Left, tRight = col1Right;
        drawWidgetCard(c, tLeft, r1Top, tRight, r1Bottom);
        c.drawText("UTETEMPERATUR", tLeft + 30f, r1Top + 45f, lblHdr);
        String tHdrStr = String.format(Locale.getDefault(), "Min %.1f°  Maks %.1f°", minT, maxT);
        c.drawText(tHdrStr, tRight - 30f - lblVal.measureText(tHdrStr), r1Top + 45f, lblVal);
        
        float tGraphLeft = tLeft + 75f;
        float tGraphTop = r1Top + 70f;
        for (int i = 0; i <= 5; i++) {
            float frac = i / 5f;
            float y = tGraphTop + cH * (1 - frac);
            c.drawLine(tGraphLeft, y, tGraphLeft + cW, y, gridP);
            c.drawText(String.format(Locale.getDefault(), "%.0f°", minT + range * frac), tGraphLeft - 18f, y + 10f, lblPy);
        }
        for (int hr = 0; hr <= 24; hr += 6) {
            float x = tGraphLeft + cW * (1f - hr / 24f);
            c.drawLine(x, tGraphTop, x, tGraphTop + cH, gridP);
            long ms = System.currentTimeMillis() - hr * 3_600_000L;
            c.drawText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms)), x - 32f, tGraphTop + cH + 32f, lblP);
        }
        Paint fillP = new Paint();
        fillP.setColor(android.graphics.Color.parseColor("#1A3B82F6")); fillP.setStyle(Paint.Style.FILL); fillP.setAntiAlias(true);
        Path fillPath = new Path();
        fillPath.moveTo(tGraphLeft + cW * (1f - points.get(0)[0] / 24f), tGraphTop + cH);
        for (float[] p : points) fillPath.lineTo(tGraphLeft + cW * (1f - p[0] / 24f), tGraphTop + cH * (1f - (p[1] - minT) / range));
        fillPath.lineTo(tGraphLeft + cW * (1f - points.get(points.size()-1)[0] / 24f), tGraphTop + cH);
        fillPath.close();
        c.drawPath(fillPath, fillP);
        Paint lineP = new Paint();
        lineP.setColor(android.graphics.Color.parseColor("#3B82F6")); lineP.setStrokeWidth(4f);
        lineP.setStyle(Paint.Style.STROKE); lineP.setAntiAlias(true);
        lineP.setStrokeJoin(Paint.Join.ROUND); lineP.setStrokeCap(Paint.Cap.ROUND);
        Path linePath = new Path(); boolean first = true;
        for (float[] p : points) {
            float x = tGraphLeft + cW * (1f - p[0] / 24f);
            float y = tGraphTop + cH * (1f - (p[1] - minT) / range);
            if (first) { linePath.moveTo(x, y); first = false; } else linePath.lineTo(x, y);
        }
        c.drawPath(linePath, lineP);

        // --- 2. AKTUELT UTETEMPERATUR WIDGET (Slot 2, Row 1 Right) ---
        float tempLeft = col2Left, tempRight = col2Right;
        drawWidgetCard(c, tempLeft, r1Top, tempRight, r1Bottom);
        c.drawText("AKTUELT UTETEMPERATUR", tempLeft + 30f, r1Top + 45f, lblHdr);
        
        Paint curTempP = new Paint();
        curTempP.setAntiAlias(true); curTempP.setColor(android.graphics.Color.WHITE);
        curTempP.setTextSize(82f); curTempP.setTypeface(Typeface.DEFAULT_BOLD);
        String curTempStr = String.format(Locale.getDefault(), "%.1f°C", curTemp);
        float curTempW = curTempP.measureText(curTempStr);
        c.drawText(curTempStr, tempLeft + (cardW - curTempW) / 2f, r1Top + 150f, curTempP);
        
        Paint curMinMaxP = new Paint();
        curMinMaxP.setAntiAlias(true); curMinMaxP.setColor(android.graphics.Color.parseColor("#AAAAAA"));
        curMinMaxP.setTextSize(26f);
        String curMinMaxStr = String.format(Locale.getDefault(), "Min %.1f°   Maks %.1f°", minT, maxT);
        float curMinMaxW = curMinMaxP.measureText(curMinMaxStr);
        c.drawText(curMinMaxStr, tempLeft + (cardW - curMinMaxW) / 2f, r1Top + 220f, curMinMaxP);

        // ==========================================
        // ROW 2: 3. Rain 7d (Left) & 4. Soil Moisture (Right)
        // ==========================================

        
        // --- 3. REGN SISTE 7 DAGER (Slot 3) ---
        float r7Left = col1Left, r7Right = col1Right;
        drawWidgetCard(c, r7Left, r2Top, r7Right, r2Bottom);
        c.drawText("REGN 7 DAGER", r7Left + 30f, r2Top + 45f, lblHdr);
        float totalRain = 0; for (float v : rainByDay) totalRain += v;
        String totalRainStr = String.format(Locale.getDefault(), "Totalt %.1f mm", totalRain);
        c.drawText(totalRainStr, r7Right - 30f - lblVal.measureText(totalRainStr), r2Top + 45f, lblVal);
        
        float r7GraphLeft = r7Left + 75f;
        float r7GraphTop = r2Top + 70f;
        c.drawLine(r7GraphLeft, r7GraphTop, r7GraphLeft + cW, r7GraphTop, gridP);
        c.drawLine(r7GraphLeft, r7GraphTop + cH, r7GraphLeft + cW, r7GraphTop + cH, gridP);
        float maxRain = 1f;
        for (float r : rainByDay) maxRain = Math.max(maxRain, r);
        c.drawText(String.format(Locale.getDefault(), "%.1f", maxRain), r7GraphLeft - 18f, r7GraphTop + 20f, lblPy);
        c.drawText("0.0", r7GraphLeft - 18f, r7GraphTop + cH + 10f, lblPy);
        
        float barGap = cW / 7f;
        float barW = barGap * 0.55f;
        String[] dayNames = {"I dag", "I går", "2d", "3d", "4d", "5d", "6d"};
        Paint barP = new Paint(); barP.setAntiAlias(true);
        Paint barLblP = new Paint(); barLblP.setAntiAlias(true); barLblP.setColor(android.graphics.Color.WHITE); barLblP.setTextSize(24f);
        Paint dayLblP = new Paint(); dayLblP.setAntiAlias(true); dayLblP.setColor(android.graphics.Color.WHITE); dayLblP.setTextSize(24f);
        for (int i = 0; i < 7; i++) {
            float barCenterX = r7GraphLeft + cW - barGap * i - barGap / 2f;
            float barHeight = rainByDay[i] > 0 ? cH * (rainByDay[i] / maxRain) : 4f;
            float barTop = r7GraphTop + cH - barHeight;
            float barLeft = barCenterX - barW / 2f;
            float barRight = barCenterX + barW / 2f;
            
            barP.setShader(new android.graphics.LinearGradient(
                0, r7GraphTop + cH,
                0, barTop,
                android.graphics.Color.parseColor("#1B6ADF"), // Bottom color
                android.graphics.Color.parseColor("#4FA5F7"), // Top color
                android.graphics.Shader.TileMode.CLAMP
            ));
            // Draw top-rounded rect (radius 8f)
            c.drawRoundRect(barLeft, barTop, barRight, r7GraphTop + cH + 12f, 8f, 8f, barP);
            
            if (rainByDay[i] > 0) {
                String valStr = String.format(Locale.getDefault(), "%.1f", rainByDay[i]);
                float valW = barLblP.measureText(valStr);
                c.drawText(valStr, barCenterX - valW / 2f, barTop - 6f, barLblP);
            }
            float dnW = dayLblP.measureText(dayNames[i]);
            c.drawText(dayNames[i], barCenterX - dnW / 2f, r7GraphTop + cH + 32f, dayLblP);
        }

        // --- 4. REGNSTYRKE WIDGET (Slot 4, Row 2 Right) ---
        float rrLeft = col2Left, rrRight = col2Right;
        drawWidgetCard(c, rrLeft, r2Top, rrRight, r2Bottom);
        c.drawText("REGNSTYRKE", rrLeft + 30f, r2Top + 45f, lblHdr);
        float maxHourlyRain = 0.5f;
        for (float r : hourlyRain) maxHourlyRain = Math.max(maxHourlyRain, r);
        String maxRainRateStr = String.format(Locale.getDefault(), "Maks %.1f mm/t", maxHourlyRain);
        c.drawText(maxRainRateStr, rrRight - 30f - lblVal.measureText(maxRainRateStr), r2Top + 45f, lblVal);
        
        float rrGraphLeft = rrLeft + 75f;
        float rrGraphTop = r2Top + 70f;
        c.drawLine(rrGraphLeft, rrGraphTop, rrGraphLeft + cW, rrGraphTop, gridP);
        c.drawLine(rrGraphLeft, rrGraphTop + cH, rrGraphLeft + cW, rrGraphTop + cH, gridP);
        c.drawText(String.format(Locale.getDefault(), "%.1f", maxHourlyRain), rrGraphLeft - 18f, rrGraphTop + 20f, lblPy);
        c.drawText("0.0", rrGraphLeft - 18f, rrGraphTop + cH + 10f, lblPy);
        
        float hBarGap = cW / 24f;
        float hBarW = hBarGap * 0.65f;
        Paint hBarP = new Paint();
        hBarP.setAntiAlias(true);
        for (int i = 0; i < 24; i++) {
            float barCenterX = rrGraphLeft + cW - hBarGap * i - hBarGap / 2f;
            float barHeight = hourlyRain[i] > 0 ? cH * (hourlyRain[i] / maxHourlyRain) : 2f;
            float barTop = rrGraphTop + cH - barHeight;
            float barLeft = barCenterX - hBarW / 2f;
            float barRight = barCenterX + hBarW / 2f;
            
            hBarP.setShader(new android.graphics.LinearGradient(
                0, rrGraphTop + cH,
                0, barTop,
                android.graphics.Color.parseColor("#1B6ADF"), // Bottom color
                android.graphics.Color.parseColor("#4FA5F7"), // Top color
                android.graphics.Shader.TileMode.CLAMP
            ));
            // Draw top-rounded rect (radius 4f)
            c.drawRoundRect(barLeft, barTop, barRight, rrGraphTop + cH + 8f, 4f, 4f, hBarP);
            
            if (hourlyRain[i] >= 0.1f) {
                String valStr = String.format(Locale.getDefault(), "%.1f", hourlyRain[i]);
                Paint valP = new Paint(); valP.setAntiAlias(true); valP.setColor(android.graphics.Color.WHITE); valP.setTextSize(18f);
                float valW = valP.measureText(valStr);
                c.drawText(valStr, barCenterX - valW / 2f, barTop - 4f, valP);
            }
        }
        for (int hr = 0; hr <= 24; hr += 6) {
            float x = rrGraphLeft + cW * (1f - hr / 24f);
            c.drawLine(x, rrGraphTop, x, rrGraphTop + cH, gridP);
            long ms = System.currentTimeMillis() - hr * 3_600_000L;
            c.drawText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms)), x - 32f, rrGraphTop + cH + 32f, lblP);
        }

        // ==========================================
        // ROW 3: 5. Solar (Left) & 6. Gårdsplassen (Right)
        // ==========================================

        
        // --- 5. SOLSTRALING WIDGET (Slot 5) ---
        float solLeft = col1Left, solRight = col1Right;
        drawWidgetCard(c, solLeft, r3Top, solRight, r3Bottom);
        c.drawText("SOLSTRALING", solLeft + 30f, r3Top + 45f, lblHdr);
        float sTotal = 0f;
        for (float v : valSolar24h) sTotal += v;
        String sTotalStr = String.format(Locale.getDefault(), "%.2f kWh/m²", sTotal);
        c.drawText(sTotalStr, solRight - 30f - lblVal.measureText(sTotalStr), r3Top + 45f, lblVal);
        
        float solGraphLeft = solLeft + 75f;
        float solGraphTop = r3Top + 65f;
        float sMaxBarH = 150f;
        float sBarAreaLeft = solGraphLeft;
        float sBarAreaWidth = cW;
        float sGap = 2f;
        float sBarW = (sBarAreaWidth - 23f * sGap) / 24f;
        float sMaxVal = 0.1f;
        for (float v : valSolar24h) sMaxVal = Math.max(sMaxVal, v);
        Paint sBarP = new Paint(); sBarP.setAntiAlias(true);
        for (int i = 0; i < 24; i++) {
            float x = sBarAreaLeft + (23 - i) * (sBarW + sGap);
            float barH = valSolar24h[i] > 0 ? (valSolar24h[i] / sMaxVal) * sMaxBarH : 2f;
            float top = solGraphTop + sMaxBarH - barH;
            
            sBarP.setShader(new android.graphics.LinearGradient(
                0, solGraphTop + sMaxBarH,
                0, top,
                android.graphics.Color.parseColor("#E68A4F"), // Bottom warm orange-yellow
                android.graphics.Color.parseColor("#FFC107"), // Top sun yellow
                android.graphics.Shader.TileMode.CLAMP
            ));
            // Draw top-rounded rect (radius 4f)
            c.drawRoundRect(x, top, x + sBarW, solGraphTop + sMaxBarH + 8f, 4f, 4f, sBarP);
        }
        for (int hr = 0; hr <= 24; hr += 6) {
            float x = solGraphLeft + cW * (1f - hr / 24f);
            c.drawLine(x, solGraphTop, x, solGraphTop + sMaxBarH, gridP);
            long ms = System.currentTimeMillis() - hr * 3_600_000L;
            String label = new SimpleDateFormat("H", Locale.getDefault()).format(new Date(ms));
            float labelW = lblP.measureText(label);
            c.drawText(label, x - labelW / 2f, solGraphTop + sMaxBarH + 34f, lblP);
        }

        // --- 6. JORDFUKTIGHET WIDGET (Slot 6, Row 3 Right) ---
        float sLeft = col2Left, sRight = col2Right;
        drawWidgetCard(c, sLeft, r3Top, sRight, r3Bottom);
        c.drawText("JORDFUKTIGHET", sLeft + 30f, r3Top + 45f, lblHdr);
        float rangeS = maxS - minS;
        if (rangeS < 1f) rangeS = 1f;
        String soilStr = hasSoilData 
            ? String.format(Locale.getDefault(), "Nå %.0f%% (Min %.0f%%  Maks %.0f%%)", curSoil, actualMinS, actualMaxS)
            : "Ingen data";
        c.drawText(soilStr, sRight - 30f - lblVal.measureText(soilStr), r3Top + 45f, lblVal);
        
        float sGraphLeft = sLeft + 75f;
        float sGraphTop = r3Top + 70f;
        for (int i = 0; i <= 5; i++) {
            float frac = i / 5f;
            float y = sGraphTop + cH * (1 - frac);
            c.drawLine(sGraphLeft, y, sGraphLeft + cW, y, gridP);
            c.drawText(String.format(Locale.getDefault(), "%.0f%%", minS + rangeS * frac), sGraphLeft - 18f, y + 10f, lblPy);
        }
        if (hasSoilData) {
            float soilBarGap = cW / 3f;
            for (int d = 0; d <= 3; d++) {
                float x = sGraphLeft + cW * (1f - d / 3f);
                c.drawLine(x, sGraphTop, x, sGraphTop + cH, gridP);
                if (d < 3) {
                    float dayCenterX = sGraphLeft + cW - soilBarGap * d - soilBarGap / 2f;
                    float dnW = dayLblP.measureText(dayNames[d]);
                    c.drawText(dayNames[d], dayCenterX - dnW / 2f, sGraphTop + cH + 32f, dayLblP);
                }
            }
            Paint fillPS = new Paint();
            fillPS.setColor(android.graphics.Color.parseColor("#1A3B82F6")); fillPS.setStyle(Paint.Style.FILL); fillPS.setAntiAlias(true);
            Path fillPathS = new Path();
            fillPathS.moveTo(sGraphLeft + cW * (1f - soilPoints.get(0)[0] / 72f), sGraphTop + cH);
            for (float[] p : soilPoints) fillPathS.lineTo(sGraphLeft + cW * (1f - p[0] / 72f), sGraphTop + cH * (1f - (p[1] - minS) / rangeS));
            fillPathS.lineTo(sGraphLeft + cW * (1f - soilPoints.get(soilPoints.size()-1)[0] / 72f), sGraphTop + cH);
            fillPathS.close();
            c.drawPath(fillPathS, fillPS);
            Paint linePS = new Paint();
            linePS.setColor(android.graphics.Color.parseColor("#3B82F6")); // DodgerBlue/iOS Blue
            linePS.setStrokeWidth(4f); linePS.setStyle(Paint.Style.STROKE); linePS.setAntiAlias(true);
            linePS.setStrokeJoin(Paint.Join.ROUND); linePS.setStrokeCap(Paint.Cap.ROUND);
            Path linePathS = new Path(); boolean firstS = true;
            for (float[] p : soilPoints) {
                float x = sGraphLeft + cW * (1f - p[0] / 72f);
                float y = sGraphTop + cH * (1f - (p[1] - minS) / rangeS);
                if (firstS) { linePathS.moveTo(x, y); firstS = false; } else linePathS.lineTo(x, y);
            }
            c.drawPath(linePathS, linePS);
        } else {
            Paint noDataP = new Paint(); noDataP.setColor(android.graphics.Color.GRAY); noDataP.setTextSize(36f); noDataP.setAntiAlias(true);
            String noDataText = "Ingen data tilgjengelig";
            c.drawText(noDataText, sGraphLeft + (cW - noDataP.measureText(noDataText)) / 2f, sGraphTop + cH / 2f, noDataP);
        }

        // ==========================================
        // ROW 4: 7. Gårdsplassen (Left) & 8. Postkassen (Right)
        // ==========================================
        float r4Top = 1022f, r4Bottom = 1460f;
        
        // --- Gårdsplassen Camera on Row 4 Left ---
        drawCameraWidget(c, cameraBitmap, "GÅRDSPLASSEN", imageTimestamp, col1Left, r4Top, col1Right, r4Bottom);

        // --- Postkassen Camera on Row 4 Right ---
        drawCameraWidget(c, mailboxBitmap, "POSTKASSEN", mailboxTimestamp, col2Left, r4Top, col2Right, r4Bottom);

        // --- Kontroll & Tid Widget: Dropped to leave open space on Row 3 Right ---

        // === 5. ROMTEMPERATURER GRID (Y: 1480 -> 1956) ===
        
        float startX = 0f;
        float totalWidth = 1440f;
        float gapRoom = 12f;
        float cardH = 110f;
        float gridY = 1480f;
        
        // Row 1: Jonatan, Loftsgang, Kontor
        float row1W = (totalWidth - 2 * gapRoom) / 3f;
        drawRoomCard(c, "Jonatan", valJonatan, startX, gridY, row1W, cardH);
        drawRoomCard(c, "Loftsgang", valLoftsgang, startX + row1W + gapRoom, gridY, row1W, cardH);
        drawRoomCard(c, "Kontor", valKontor, startX + 2 * (row1W + gapRoom), gridY, row1W, cardH);
        
        // Row 2: Bad, Kjøkken, Lite bad, Mats
        gridY += cardH + gapRoom;
        float row2W = (totalWidth - 3 * gapRoom) / 4f;
        drawRoomCard(c, "Bad", valBad, startX, gridY, row2W, cardH);
        drawRoomCard(c, "Kjøkken", valKjokken, startX + row2W + gapRoom, gridY, row2W, cardH);
        drawRoomCard(c, "Lite bad", valLiteBad, startX + 2 * (row2W + gapRoom), gridY, row2W, cardH);
        drawRoomCard(c, "Mats", valMats, startX + 3 * (row2W + gapRoom), gridY, row2W, cardH);
        
        // Row 3: Vinterhage, Stue, Gang, Soverom
        gridY += cardH + gapRoom;
        drawRoomCard(c, "Vinterhage", valVinterhage, startX, gridY, row2W, cardH);
        drawRoomCard(c, "Stue", valStue, startX + row2W + gapRoom, gridY, row2W, cardH);
        drawRoomCard(c, "Gang", valGang3, startX + 2 * (row2W + gapRoom), gridY, row2W, cardH);
        drawRoomCard(c, "Soverom", valSoverom, startX + 3 * (row2W + gapRoom), gridY, row2W, cardH);
        
        // Row 4: Gang, Vaskerom
        gridY += cardH + gapRoom;
        float row4W = (totalWidth - gapRoom) / 2f;
        drawRoomCard(c, "Gang", valGang4, startX, gridY, row4W, cardH);
        drawRoomCard(c, "Vaskerom", valVaskerom, startX + row4W + gapRoom, gridY, row4W, cardH);

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

    private void drawRoomCard(android.graphics.Canvas canvas, String name, float temp, float x, float y, float w, float h) {
        int color = getTemperatureColor(temp);
        
        android.graphics.Paint baseBgPaint = new android.graphics.Paint();
        baseBgPaint.setStyle(android.graphics.Paint.Style.FILL);
        baseBgPaint.setColor(android.graphics.Color.parseColor("#0D1117")); // Match chart background
        baseBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, 14f, 14f, baseBgPaint);

        android.graphics.Paint tintBgPaint = new android.graphics.Paint();
        tintBgPaint.setStyle(android.graphics.Paint.Style.FILL);
        int tintColor = android.graphics.Color.argb(38, 
                android.graphics.Color.red(color), 
                android.graphics.Color.green(color), 
                android.graphics.Color.blue(color)); // 15% opacity tint overlay
        tintBgPaint.setColor(tintColor);
        tintBgPaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, 14f, 14f, tintBgPaint);
        
        android.graphics.Paint strokePaint = new android.graphics.Paint();
        strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        strokePaint.setColor(color);
        strokePaint.setAntiAlias(true);
        canvas.drawRoundRect(x, y, x + w, y + h, 14f, 14f, strokePaint);
        
        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(28f);
        textPaint.setColor(android.graphics.Color.parseColor("#E1E4EA"));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        float nameWidth = textPaint.measureText(name);
        canvas.drawText(name, x + (w - nameWidth) / 2f, y + 42f, textPaint);
        
        android.graphics.Paint tempPaint = new android.graphics.Paint();
        tempPaint.setAntiAlias(true);
        tempPaint.setTextSize(36f);
        tempPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tempPaint.setColor(color);
        String tempStr = String.format(Locale.getDefault(), "%.1f°", temp);
        float tempWidth = tempPaint.measureText(tempStr);
        canvas.drawText(tempStr, x + (w - tempWidth) / 2f, y + 85f, tempPaint);
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
            float drawW = bmpW * scale;
            float drawH = bmpH * scale;
            float dx = left + (cardW - drawW) / 2f;
            float dy;
            if (title != null && title.equals("GÅRDSPLASSEN")) {
                // Center viewport at 10% above the middle of the image (0.40 of drawH)
                dy = top + cardH / 2f - 0.40f * drawH;
                // Clamp dy to prevent drawing outside card boundaries
                dy = Math.max(top + cardH - drawH, Math.min(top, dy));
            } else {
                dy = top + (cardH - drawH) / 2f; // Symmetrical center crop
            }
            Rect src = new Rect(0, 0, (int)bmpW, (int)bmpH);
            android.graphics.RectF dst = new android.graphics.RectF(dx, dy, dx + drawW, dy + drawH);
            canvas.drawBitmap(bmp, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            
            // Overlay banner
            Paint bannerP = new Paint();
            bannerP.setColor(android.graphics.Color.argb(160, 0, 0, 0));
            canvas.drawRect(left, top, right, top + 55f, bannerP);
            
            Paint bannerText = new Paint();
            bannerText.setAntiAlias(true);
            bannerText.setColor(android.graphics.Color.WHITE);
            bannerText.setTextSize(24f);
            bannerText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(title, left + 20f, top + 36f, bannerText);
            
            Paint tsText = new Paint();
            tsText.setAntiAlias(true);
            tsText.setColor(android.graphics.Color.parseColor("#CCCCCC"));
            tsText.setTextSize(22f);
            String displayTs = (tsStr != null && !tsStr.isEmpty()) ? tsStr : "Live";
            canvas.drawText(displayTs, right - 20f - tsText.measureText(displayTs), top + 35f, tsText);
            
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
