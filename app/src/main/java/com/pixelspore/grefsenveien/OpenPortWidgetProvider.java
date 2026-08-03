package com.pixelspore.grefsenveien;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home-screen widget with a single "Port" button that triggers the gate webhook.
 */
public class OpenPortWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_OPEN_PORT = "com.pixelspore.grefsenveien.action.OPEN_PORT";

    private static final String PREFS_NAME = "GrefsenveienPrefs";
    private static final String TAG = "OpenPortWidget";
    private static final String WEBHOOK_TOKEN = "Xi3gQF4GTFR7aENMkMjftt4P";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_OPEN_PORT.equals(intent.getAction())) {
            openPort(context);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_open_port);

        Intent clickIntent = new Intent(context, OpenPortWidgetProvider.class);
        clickIntent.setAction(ACTION_OPEN_PORT);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_port_button, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private void openPort(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", null);

        if (userEmail == null || userEmail.isEmpty()) {
            mainHandler.post(() -> Toast.makeText(
                    appContext,
                    R.string.widget_open_port_login_required,
                    Toast.LENGTH_LONG).show());
            Intent launch = new Intent(appContext, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(launch);
            return;
        }

        mainHandler.post(() -> Toast.makeText(
                appContext,
                R.string.widget_open_port_sending,
                Toast.LENGTH_SHORT).show());

        final String email = userEmail;
        executor.execute(() -> {
            String resultMessage;
            try {
                URL url = new URL(BuildConfig.GATE_WEBHOOK_URL);
                Log.i(TAG, "Widget -> Calling webhook: " + url);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String payload = "{\"token\":\"" + WEBHOOK_TOKEN + "\",\"user\":\"" + email + "\"}";
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 200) {
                    resultMessage = appContext.getString(R.string.widget_open_port_success);
                } else {
                    resultMessage = appContext.getString(R.string.widget_open_port_error, responseCode);
                }
            } catch (IOException e) {
                Log.e(TAG, "Widget webhook failed", e);
                resultMessage = appContext.getString(R.string.widget_open_port_network_error);
            }

            final String toastText = resultMessage;
            mainHandler.post(() -> Toast.makeText(appContext, toastText, Toast.LENGTH_SHORT).show());
        });
    }
}
