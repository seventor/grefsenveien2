package com.pixelspore.grefsenveien.wear;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DeviceParametersBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.protolayout.material.Button;
import androidx.wear.protolayout.material.ButtonColors;
import androidx.wear.protolayout.material.Text;
import androidx.wear.protolayout.material.Typography;
import androidx.wear.protolayout.material.layouts.PrimaryLayout;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wear OS Tile with Garasje / Port shortcuts (swipe from the watch face).
 */
public class ActionTileService extends TileService {

    private static final String TAG = "ActionTileService";
    private static final String RESOURCES_VERSION = "4";
    private static final String ID_CLICK_GARAGE = "click_garage";
    private static final String ID_CLICK_GATE = "click_gate";
    private static final String RES_ID_GARAGE = "ic_garage";
    private static final String RES_ID_GATE = "ic_gate";

    private static final int COLOR_GARAGE = 0xFF1B6BC8;
    private static final int COLOR_GATE = 0xFF2E8B57;
    private static final int COLOR_ON_BUTTON = 0xFFFFFFFF;
    private static final int COLOR_STATUS = 0xFFB0B0B0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String statusText = "Grefsenveien";

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            @NonNull RequestBuilders.TileRequest requestParams) {
        UserEmailStore.refreshFromPhone(this);

        String clickId = requestParams.getState() != null
                ? requestParams.getState().getLastClickableId()
                : null;

        if (ID_CLICK_GARAGE.equals(clickId)) {
            statusText = "Sender Garasje...";
            triggerWebhook(BuildConfig.GARAGE_WEBHOOK_URL, "Garasje");
        } else if (ID_CLICK_GATE.equals(clickId)) {
            statusText = "Sender Port...";
            triggerWebhook(BuildConfig.GATE_WEBHOOK_URL, "Port");
        } else if (!UserEmailStore.isLoggedIn(this)) {
            statusText = getString(R.string.wear_login_required);
        } else if (getString(R.string.wear_login_required).equals(statusText)) {
            statusText = "Grefsenveien";
        }

        DeviceParametersBuilders.DeviceParameters deviceParams =
                requestParams.getDeviceConfiguration();

        return Futures.immediateFuture(new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(0)
                .setTileTimeline(new TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(new LayoutElementBuilders.Layout.Builder()
                                        .setRoot(buildLayout(deviceParams))
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest requestParams) {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(RES_ID_GARAGE, imageResource(R.drawable.ic_material_garage_door))
                .addIdToImageMapping(RES_ID_GATE, imageResource(R.drawable.ic_material_outdoor_garden))
                .build());
    }

    private static ResourceBuilders.ImageResource imageResource(int resId) {
        return new ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                        new ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(resId)
                                .build())
                .build();
    }

    private LayoutElementBuilders.LayoutElement buildLayout(
            DeviceParametersBuilders.DeviceParameters deviceParams) {

        ModifiersBuilders.Clickable garageClick = new ModifiersBuilders.Clickable.Builder()
                .setId(ID_CLICK_GARAGE)
                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                .build();

        ModifiersBuilders.Clickable gateClick = new ModifiersBuilders.Clickable.Builder()
                .setId(ID_CLICK_GATE)
                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                .build();

        Button garageButton = new Button.Builder(this, garageClick)
                .setContentDescription(getString(R.string.wear_garage))
                .setIconContent(RES_ID_GARAGE)
                .setButtonColors(new ButtonColors(COLOR_GARAGE, COLOR_ON_BUTTON))
                .setSize(DimensionBuilders.dp(56))
                .build();

        Button gateButton = new Button.Builder(this, gateClick)
                .setContentDescription(getString(R.string.wear_gate))
                .setIconContent(RES_ID_GATE)
                .setButtonColors(new ButtonColors(COLOR_GATE, COLOR_ON_BUTTON))
                .setSize(DimensionBuilders.dp(56))
                .build();

        Text title = new Text.Builder(this, statusText)
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .setColor(ColorBuilders.argb(COLOR_STATUS))
                .setMaxLines(2)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                .build();

        // One column per action so the label stays centered under its circle.
        LayoutElementBuilders.Row content = new LayoutElementBuilders.Row.Builder()
                .setWidth(DimensionBuilders.wrap())
                .setHeight(DimensionBuilders.wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(buttonWithLabel(garageButton, getString(R.string.wear_garage)))
                .addContent(new LayoutElementBuilders.Spacer.Builder()
                        .setWidth(DimensionBuilders.dp(16))
                        .build())
                .addContent(buttonWithLabel(gateButton, getString(R.string.wear_gate)))
                .build();

        return new PrimaryLayout.Builder(deviceParams)
                .setPrimaryLabelTextContent(title)
                .setContent(content)
                .build();
    }

    private LayoutElementBuilders.LayoutElement buttonWithLabel(
            Button button, String label) {
        LayoutElementBuilders.Text labelText = new LayoutElementBuilders.Text.Builder()
                .setText(label)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(11))
                        .setColor(ColorBuilders.argb(COLOR_ON_BUTTON))
                        .build())
                .setMaxLines(1)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                .build();

        LayoutElementBuilders.Box labelBox = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(64))
                .setHeight(DimensionBuilders.wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(labelText)
                .build();

        return new LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.dp(64))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(button)
                .addContent(new LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(4))
                        .build())
                .addContent(labelBox)
                .build();
    }

    private void triggerWebhook(String targetUrl, String actionName) {
        executor.execute(() -> {
            WebhookCaller.Response response =
                    WebhookCaller.post(this, targetUrl, "Wear Tile");

            new Handler(Looper.getMainLooper()).post(() -> {
                switch (response.result) {
                    case SUCCESS:
                        statusText = actionName + ": OK";
                        break;
                    case NOT_LOGGED_IN:
                        statusText = getString(R.string.wear_login_required);
                        break;
                    case HTTP_ERROR:
                        statusText = actionName + ": " + response.responseCode;
                        break;
                    case NETWORK_ERROR:
                    default:
                        statusText = actionName + ": Feilet";
                        break;
                }
                Log.i(TAG, "Tile status: " + statusText);
                getUpdater(this).requestUpdate(ActionTileService.class);
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
