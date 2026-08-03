package com.pixelspore.grefsenveien.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

/**
 * Stores the phone-signed-in user email on the watch (synced via Wear Data Layer).
 */
public final class UserEmailStore {

    private static final String TAG = "UserEmailStore";
    static final String PREFS_NAME = "GrefsenveienPrefs";
    static final String KEY_USER_EMAIL = "user_email";
    static final String DATA_PATH = "/grefsenveien/user";

    private UserEmailStore() {
    }

    public static String getUserEmail(Context context) {
        return prefs(context).getString(KEY_USER_EMAIL, null);
    }

    public static boolean isLoggedIn(Context context) {
        String email = getUserEmail(context);
        return email != null && !email.isEmpty();
    }

    public static void saveUserEmail(Context context, String email) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (email == null || email.isEmpty()) {
            editor.remove(KEY_USER_EMAIL);
        } else {
            editor.putString(KEY_USER_EMAIL, email);
        }
        editor.apply();
    }

    public static void applyDataEvents(Context context, DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            if (!DATA_PATH.equals(item.getUri().getPath())) {
                continue;
            }
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                saveUserEmail(context, map.getString(KEY_USER_EMAIL, ""));
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                saveUserEmail(context, null);
            }
        }
    }

    public static void refreshFromPhone(Context context) {
        Context appContext = context.getApplicationContext();
        DataClient dataClient = Wearable.getDataClient(appContext);
        Uri uri = Uri.parse("wear://*" + DATA_PATH);
        dataClient.getDataItems(uri)
                .addOnSuccessListener(buffer -> {
                    try {
                        for (DataItem item : buffer) {
                            DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                            saveUserEmail(appContext, map.getString(KEY_USER_EMAIL, ""));
                        }
                    } finally {
                        buffer.release();
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Could not refresh user email from phone", e));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
