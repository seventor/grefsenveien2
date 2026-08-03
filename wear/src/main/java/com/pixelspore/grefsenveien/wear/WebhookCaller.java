package com.pixelspore.grefsenveien.wear;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Triggers garage/gate webhooks the same way as the phone app and home-screen widget:
 * POST JSON {@code {"token":"...","user":"<signed-in email>"}}.
 */
public final class WebhookCaller {

    private static final String TAG = "WebhookCaller";
    private static final String WEBHOOK_TOKEN = "Xi3gQF4GTFR7aENMkMjftt4P";

    public enum Result {
        SUCCESS,
        HTTP_ERROR,
        NETWORK_ERROR,
        NOT_LOGGED_IN
    }

    public static final class Response {
        public final Result result;
        public final int responseCode;

        private Response(Result result, int responseCode) {
            this.result = result;
            this.responseCode = responseCode;
        }

        static Response success() {
            return new Response(Result.SUCCESS, 200);
        }

        static Response httpError(int code) {
            return new Response(Result.HTTP_ERROR, code);
        }

        static Response networkError() {
            return new Response(Result.NETWORK_ERROR, -1);
        }

        static Response notLoggedIn() {
            return new Response(Result.NOT_LOGGED_IN, -1);
        }
    }

    private WebhookCaller() {
    }

    public static Response post(Context context, String webhookUrl, String logLabel) {
        String userEmail = UserEmailStore.getUserEmail(context);
        if (userEmail == null || userEmail.isEmpty()) {
            Log.w(TAG, logLabel + " blocked: not logged in");
            return Response.notLoggedIn();
        }

        try {
            URL url = new URL(webhookUrl);
            Log.i(TAG, logLabel + " -> Calling webhook: " + url);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String payload = "{\"token\":\"" + WEBHOOK_TOKEN + "\",\"user\":\"" + userEmail + "\"}";
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode == 200) {
                return Response.success();
            }
            return Response.httpError(responseCode);
        } catch (IOException e) {
            Log.e(TAG, logLabel + " webhook failed", e);
            return Response.networkError();
        }
    }
}
