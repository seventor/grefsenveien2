package com.pixelspore.grefsenveien.wear;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String GARAGE_URL = BuildConfig.GARAGE_WEBHOOK_URL;
    private static final String GATE_URL = BuildConfig.GATE_WEBHOOK_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        View btnGarage = findViewById(R.id.btnGarage);
        View btnGate = findViewById(R.id.btnGate);

        btnGarage.setOnClickListener(v -> triggerWebhook(GARAGE_URL, "Garasje"));
        btnGate.setOnClickListener(v -> triggerWebhook(GATE_URL, "Port"));
    }

    private void triggerWebhook(String targetUrl, String actionName) {
        tvStatus.setText("Sender...");
        
        executor.execute(() -> {
            boolean success = false;
            try {
                URL url = new URL(targetUrl);
                android.util.Log.i("GrefsenveienApp", "Wear MainActivity -> Calling webhook: " + url.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String payload = "{\"token\":\"Xi3gQF4GTFR7aENMkMjftt4P\",\"user\":\"thomas@gmail.com\"}";
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                success = (responseCode >= 200 && responseCode < 300);
                conn.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            handler.post(() -> {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                if (finalSuccess) {
                    tvStatus.setText(actionName + ": Åpnet " + time);
                    Toast.makeText(MainActivity.this, actionName + " aktivert", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText(actionName + ": Feilet " + time);
                    Toast.makeText(MainActivity.this, "Nettverksfeil", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
