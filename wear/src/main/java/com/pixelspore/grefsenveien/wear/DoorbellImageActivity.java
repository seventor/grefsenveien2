package com.pixelspore.grefsenveien.wear;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen doorbell / yard camera image from {@link BuildConfig#S3_IMAGE_URL}.
 */
public class DoorbellImageActivity extends Activity {

    private static final String TAG = "DoorbellImage";
    /** Extra zoom on top of center-crop fill. */
    private static final float ZOOM_FACTOR = 1.40f;
    /** Zoom pivot: 20% left of image center (= 30% from left edge). */
    private static final float PIVOT_X_FROM_LEFT = 0.30f;
    /** Zoom pivot: 20% above image center (= 30% from top edge). */
    private static final float PIVOT_Y_FROM_TOP = 0.30f;

    private ImageView imgDoorbell;
    private ProgressBar progress;
    private TextView tvStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doorbell_image);

        imgDoorbell = findViewById(R.id.imgDoorbell);
        progress = findViewById(R.id.progressDoorbell);
        tvStatus = findViewById(R.id.tvDoorbellStatus);

        View root = findViewById(R.id.doorbellRoot);
        root.setOnClickListener(v -> finish());
        imgDoorbell.setOnClickListener(v -> finish());

        loadImage();
    }

    private void loadImage() {
        String imageUrl = BuildConfig.S3_IMAGE_URL;
        if (imageUrl == null || imageUrl.isEmpty()) {
            showError();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.wear_camera_loading);

        executor.execute(() -> {
            // Best-effort: ask doorbell to refresh for next open (don't block this view).
            triggerFreshCaptureAsync();

            Bitmap bitmap = null;
            String timestamp = null;
            try {
                String urlWithTimestamp = imageUrl
                        + (imageUrl.contains("?") ? "&" : "?")
                        + "t=" + System.currentTimeMillis();
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(urlWithTimestamp).openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.connect();

                if (connection.getResponseCode() == 200) {
                    timestamp = formatTimestamp(
                            connection.getHeaderField("Last-Modified"),
                            connection.getHeaderField("Date"));
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(input);
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load doorbell image", e);
            }

            final Bitmap finalBitmap = bitmap;
            final String finalTimestamp = timestamp;
            handler.post(() -> {
                progress.setVisibility(View.GONE);
                if (finalBitmap != null) {
                    imgDoorbell.setImageBitmap(finalBitmap);
                    applyFocusedZoom(finalBitmap);
                    tvStatus.setText(finalTimestamp != null ? finalTimestamp : "");
                } else {
                    showError();
                }
            });
        });
    }

    /**
     * Fills the view (center-crop), then zooms 40% around a point 20% left and
     * 20% up from the center of the original bitmap.
     */
    private void applyFocusedZoom(Bitmap bitmap) {
        imgDoorbell.post(() -> {
            int viewW = imgDoorbell.getWidth();
            int viewH = imgDoorbell.getHeight();
            if (viewW == 0 || viewH == 0 || bitmap == null) {
                return;
            }

            int bmpW = bitmap.getWidth();
            int bmpH = bitmap.getHeight();
            if (bmpW == 0 || bmpH == 0) {
                return;
            }

            float fillScale = Math.max((float) viewW / bmpW, (float) viewH / bmpH);
            float scaledW = bmpW * fillScale;
            float scaledH = bmpH * fillScale;
            float dx = (viewW - scaledW) / 2f;
            float dy = (viewH - scaledH) / 2f;

            float pivotBmpX = bmpW * PIVOT_X_FROM_LEFT;
            float pivotBmpY = bmpH * PIVOT_Y_FROM_TOP;
            float pivotViewX = dx + pivotBmpX * fillScale;
            float pivotViewY = dy + pivotBmpY * fillScale;

            Matrix matrix = new Matrix();
            matrix.setScale(fillScale, fillScale);
            matrix.postTranslate(dx, dy);
            matrix.postScale(ZOOM_FACTOR, ZOOM_FACTOR, pivotViewX, pivotViewY);
            imgDoorbell.setImageMatrix(matrix);
        });
    }

    private void triggerFreshCaptureAsync() {
        String takeUrl = BuildConfig.DOORBELL_TAKE_IMAGE_URL;
        if (takeUrl == null || takeUrl.isEmpty()) {
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(takeUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Could not trigger doorbell capture", e);
            }
        });
    }

    private static String formatTimestamp(String lastModified, String dateHeader) {
        String raw = lastModified != null ? lastModified : dateHeader;
        if (raw == null) {
            return null;
        }
        try {
            SimpleDateFormat httpFormat =
                    new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            Date parsed = httpFormat.parse(raw);
            if (parsed != null) {
                return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(parsed);
            }
        } catch (Exception ignored) {
            // Fall through
        }
        return raw;
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        tvStatus.setText(R.string.wear_camera_error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
