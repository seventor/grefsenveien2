package com.pixelspore.grefsenveien;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Pushes the signed-in user email to the Wear companion app via the Data Layer.
 */
public final class WearUserSync {

    private static final String TAG = "WearUserSync";
    static final String DATA_PATH = "/grefsenveien/user";
    static final String KEY_USER_EMAIL = "user_email";

    private WearUserSync() {
    }

    public static void syncUserEmail(Context context, String email) {
        Context appContext = context.getApplicationContext();
        String value = email != null ? email : "";

        PutDataMapRequest request = PutDataMapRequest.create(DATA_PATH);
        request.getDataMap().putString(KEY_USER_EMAIL, value);
        // Force a Data Layer update even when the email string is unchanged.
        request.getDataMap().putLong("updated_at", System.currentTimeMillis());
        request.setUrgent();

        Wearable.getDataClient(appContext)
                .putDataItem(request.asPutDataRequest())
                .addOnSuccessListener(item -> Log.i(TAG, "Synced user email to Wear"))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync user email to Wear", e));
    }
}
