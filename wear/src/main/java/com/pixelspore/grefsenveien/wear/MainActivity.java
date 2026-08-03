package com.pixelspore.grefsenveien.wear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        View btnGarage = findViewById(R.id.btnGarage);
        View btnGate = findViewById(R.id.btnGate);
        View btnCamera = findViewById(R.id.btnCamera);

        btnGarage.setOnClickListener(v -> triggerWebhook(BuildConfig.GARAGE_WEBHOOK_URL, "Garasje"));
        btnGate.setOnClickListener(v -> triggerWebhook(BuildConfig.GATE_WEBHOOK_URL, "Port"));
        btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, DoorbellImageActivity.class)));

        UserEmailStore.refreshFromPhone(this);
        updateLoginHint();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UserEmailStore.refreshFromPhone(this);
        updateLoginHint();
    }

    private void updateLoginHint() {
        if (tvStatus == null) {
            return;
        }
        CharSequence current = tvStatus.getText();
        boolean showingLoginHint = current != null
                && getString(R.string.wear_login_required).contentEquals(current);
        if (!UserEmailStore.isLoggedIn(this)) {
            tvStatus.setText(R.string.wear_login_required);
        } else if (showingLoginHint) {
            tvStatus.setText("");
        }
    }

    private void triggerWebhook(String targetUrl, String actionName) {
        if (!UserEmailStore.isLoggedIn(this)) {
            tvStatus.setText("Logg inn på telefon");
            Toast.makeText(this, "Logg inn i appen på telefonen først", Toast.LENGTH_LONG).show();
            UserEmailStore.refreshFromPhone(this);
            return;
        }

        tvStatus.setText("Sender...");

        executor.execute(() -> {
            WebhookCaller.Response response =
                    WebhookCaller.post(this, targetUrl, "Wear MainActivity");

            handler.post(() -> {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                switch (response.result) {
                    case SUCCESS:
                        tvStatus.setText(actionName + ": Åpnet " + time);
                        Toast.makeText(MainActivity.this, actionName + " aktivert", Toast.LENGTH_SHORT).show();
                        break;
                    case NOT_LOGGED_IN:
                        tvStatus.setText("Logg inn på telefon");
                        Toast.makeText(MainActivity.this, "Logg inn i appen på telefonen først", Toast.LENGTH_LONG).show();
                        break;
                    case HTTP_ERROR:
                        tvStatus.setText(actionName + ": Feil " + response.responseCode);
                        Toast.makeText(MainActivity.this, "Feilkode " + response.responseCode, Toast.LENGTH_LONG).show();
                        break;
                    case NETWORK_ERROR:
                    default:
                        tvStatus.setText(actionName + ": Feilet " + time);
                        Toast.makeText(MainActivity.this, "Nettverksfeil", Toast.LENGTH_LONG).show();
                        break;
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
