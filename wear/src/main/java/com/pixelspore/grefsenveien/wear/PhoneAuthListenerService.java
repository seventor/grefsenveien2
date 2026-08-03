package com.pixelspore.grefsenveien.wear;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Receives signed-in user updates from the phone companion app.
 */
public class PhoneAuthListenerService extends WearableListenerService {

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        UserEmailStore.applyDataEvents(this, dataEvents);
    }
}
