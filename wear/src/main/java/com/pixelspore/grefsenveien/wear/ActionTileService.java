package com.pixelspore.grefsenveien.wear;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.DeviceParametersBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionTileService extends TileService {
    private static final String RESOURCES_VERSION = "1";
    private static final String ID_CLICK_GARAGE = "click_garage";
    private static final String ID_CLICK_GATE = "click_gate";
    
    private static final String GARAGE_URL = BuildConfig.GARAGE_WEBHOOK_URL;
    private static final String GATE_URL = BuildConfig.GATE_WEBHOOK_URL;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String statusText = "Grefsenveien";

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        if (requestParams.getState() != null && requestParams.getState().getLastClickableId() != null) {
            String clickId = requestParams.getState().getLastClickableId();
            if (ID_CLICK_GARAGE.equals(clickId)) {
                statusText = "Sender Garasje...";
                triggerWebhook(GARAGE_URL, "Garasje");
            } else if (ID_CLICK_GATE.equals(clickId)) {
                statusText = "Sender Port...";
                triggerWebhook(GATE_URL, "Port");
            }
        }

        return Futures.immediateFuture(new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(new TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(new LayoutElementBuilders.Layout.Builder()
                                        .setRoot(buildLayout(requestParams.getDeviceConfiguration()))
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest requestParams) {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping("ic_garage", new ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(new ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_garage)
                                .build())
                        .build())
                .addIdToImageMapping("ic_gate", new ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(new ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_gate)
                                .build())
                        .build())
                .build());
    }

    private LayoutElementBuilders.LayoutElement buildLayout(DeviceParametersBuilders.DeviceParameters deviceParams) {
        // Status Text
        LayoutElementBuilders.Text textStatus = new LayoutElementBuilders.Text.Builder()
                .setText(statusText)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(14))
                        .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF))
                        .build())
                .build();

        // Garage Button
        LayoutElementBuilders.Image imgGarage = new LayoutElementBuilders.Image.Builder()
                .setResourceId("ic_garage")
                .setWidth(DimensionBuilders.dp(48))
                .setHeight(DimensionBuilders.dp(48))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId(ID_CLICK_GARAGE)
                                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                                .build())
                        .build())
                .build();

        // Gate Button
        LayoutElementBuilders.Image imgGate = new LayoutElementBuilders.Image.Builder()
                .setResourceId("ic_gate")
                .setWidth(DimensionBuilders.dp(48))
                .setHeight(DimensionBuilders.dp(48))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId(ID_CLICK_GATE)
                                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                                .build())
                        .build())
                .build();

        // Row of buttons
        LayoutElementBuilders.Row buttonRow = new LayoutElementBuilders.Row.Builder()
                .addContent(imgGarage)
                .addContent(new LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(24)).build())
                .addContent(imgGate)
                .build();

        // Main Column
        return new LayoutElementBuilders.Column.Builder()
                .addContent(textStatus)
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(16)).build())
                .addContent(buttonRow)
                .build();
    }

    private void triggerWebhook(String targetUrl, String actionName) {
        executor.execute(() -> {
            boolean success = false;
            try {
                URL url = new URL(targetUrl);
                android.util.Log.i("GrefsenveienApp", "Wear Tile -> Calling webhook: " + url.toString());
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
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalSuccess) {
                    statusText = actionName + ": Vellykket!";
                } else {
                    statusText = actionName + ": Feilet!";
                }
                androidx.wear.tiles.TileService.getUpdater(this).requestUpdate(ActionTileService.class);
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
