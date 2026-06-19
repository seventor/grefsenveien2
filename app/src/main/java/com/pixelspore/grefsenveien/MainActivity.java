package com.pixelspore.grefsenveien;

import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity {

    private String garageStatus = "";
    private String gateStatus = "";
    private TextView statusText;
    private ImageView imgCamera;
    private TextView tvImageTimestamp;
    private ImageView imgMailbox;
    private TextView tvMailboxTimestamp;
    private TextView tvLoggedInAs;
    
    private SharedPreferences prefs;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> signInLauncher;

    private final Handler mUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable mImageUpdater = new Runnable() {
        @Override
        public void run() {
            fetchImageFromUrl(BuildConfig.S3_IMAGE_URL);
            mUpdateHandler.postDelayed(this, 60000); // 60 sekunder
        }
    };

    private final Runnable mDoorbellUpdater = new Runnable() {
        @Override
        public void run() {
            triggerDoorbellImage();
            mUpdateHandler.postDelayed(this, 120000); // 120 sekunder
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        imgCamera = findViewById(R.id.imgCamera);
        tvImageTimestamp = findViewById(R.id.tvImageTimestamp);
        imgMailbox = findViewById(R.id.imgMailbox);
        tvMailboxTimestamp = findViewById(R.id.tvMailboxTimestamp);
        tvLoggedInAs = findViewById(R.id.tvLoggedInAs);
        View btnGarage = findViewById(R.id.btnGarage);
        View btnGate = findViewById(R.id.btnGate);
        View btnLogout = findViewById(R.id.btnLogout);

        btnGarage.setOnClickListener(v -> makeUrlRequest("garasjen"));
        btnGate.setOnClickListener(v -> makeUrlRequest("porten"));
        btnLogout.setOnClickListener(v -> handleLogout());
        
        updateStatusText();

        prefs = getSharedPreferences("GrefsenveienPrefs", MODE_PRIVATE);
        
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null) {
                        // Normalt sign-in-svar med data
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        handleSignInResult(task);
                    } else if (result.getResultCode() == RESULT_OK) {
                        // RESULT_OK men uten data – Google returnerte stille (silent sign-in).
                        // Prøv å hente konto direkte.
                        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                        if (account != null && account.getEmail() != null) {
                            prefs.edit().putString("user_email", account.getEmail()).apply();
                            if (tvLoggedInAs != null) {
                                tvLoggedInAs.setText("Innlogget som " + account.getEmail());
                            }
                        } else {
                            // Prøv igjen
                            mUpdateHandler.postDelayed(this::checkLoginStatus, 1000);
                        }
                    } else {
                        // RESULT_CANCELED og ingen data = brukeren trykket Tilbake
                        finish();
                    }
                }
        );


        checkLoginStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.post(mImageUpdater);

        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
        mUpdateHandler.post(mDoorbellUpdater);

        // Oppdater felt med innlogget bruker
        if (prefs != null) {
            String savedEmail = prefs.getString("user_email", null);
            if (savedEmail != null && tvLoggedInAs != null) {
                tvLoggedInAs.setText("Innlogget som " + savedEmail);
            }
        }

        // Last inn postkassebilde en gang ved åpning
        if (!BuildConfig.S3_MAILBOX_IMAGE_URL.isEmpty()) {
            fetchMailboxImage(BuildConfig.S3_MAILBOX_IMAGE_URL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUpdateHandler.removeCallbacks(mImageUpdater);
        mUpdateHandler.removeCallbacks(mDoorbellUpdater);
    }

    private void checkLoginStatus() {
        String savedEmail = prefs.getString("user_email", null);
        if (savedEmail == null) {
            // Need to sign in
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                prefs.edit().putString("user_email", account.getEmail()).apply();
                // Oppdater "Innlogget som"-feltet umiddelbart
                if (tvLoggedInAs != null) {
                    tvLoggedInAs.setText("Innlogget som " + account.getEmail());
                }
            }
        } catch (ApiException e) {
            int code = e.getStatusCode();
            android.util.Log.w("GrefsenveienApp", "signInResult:failed code=" + code);
            String msg;
            if (code == 10) {
                msg = "Innlogging feilet (app-konfigurasjon mangler i Google Cloud Console, kode 10)";
            } else {
                msg = "Innlogging feilet (kode " + code + "), prøv igjen";
            }
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
            // Ikke lukk appen – la brukeren prøve på nytt
            mUpdateHandler.postDelayed(this::checkLoginStatus, 2000);
        }
    }

    private void handleLogout() {
        if (mGoogleSignInClient != null) {
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                prefs.edit().remove("user_email").apply();
                checkLoginStatus();
            });
        }
    }

    private void updateStatusText() {
        StringBuilder statusBuilder = new StringBuilder();
        if (!garageStatus.isEmpty()) {
            statusBuilder.append("Garasje: ").append(garageStatus);
        }
        if (!gateStatus.isEmpty()) {
            if (statusBuilder.length() > 0) {
                statusBuilder.append("\n");
            }
            statusBuilder.append("Port: ").append(gateStatus);
        }
        
        String combinedStatus = statusBuilder.length() > 0 ? statusBuilder.toString() : " ";
        statusText.setText(combinedStatus);
    }

    private void fetchImageFromUrl(String imageUrl) {
        new Thread(() -> {
            try {
                String urlWithTimestamp = imageUrl + (imageUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                URL url = new URL(urlWithTimestamp);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    String lastMod = connection.getHeaderField("Last-Modified");
                    String dateHdr = connection.getHeaderField("Date");
                    
                    String dateHeader = lastMod != null ? lastMod : dateHdr;
                    
                    String finalTimestampStr = "Ukjent tid";
                    if (dateHeader != null) {
                        try {
                            SimpleDateFormat httpFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                            Date parsedDate = httpFormat.parse(dateHeader);
                            if (parsedDate != null) {
                                SimpleDateFormat displayFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
                                finalTimestampStr = displayFormat.format(parsedDate);
                            } else {
                                finalTimestampStr = dateHeader;
                            }
                        } catch (Exception e) {
                            finalTimestampStr = dateHeader;
                        }
                    }

                    java.io.InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        final String newTimestamp = finalTimestampStr;
                        runOnUiThread(() -> {
                            imgCamera.setImageBitmap(bitmap);
                            tvImageTimestamp.setText(newTimestamp);
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchMailboxImage(String imageUrl) {
        new Thread(() -> {
            try {
                String urlWithTimestamp = imageUrl + (imageUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                URL url = new URL(urlWithTimestamp);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                
                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    String lastMod = connection.getHeaderField("Last-Modified");
                    String dateHdr = connection.getHeaderField("Date");
                    String dateHeader = lastMod != null ? lastMod : dateHdr;
                    
                    String finalTimestampStr = "";
                    if (dateHeader != null) {
                        try {
                            SimpleDateFormat httpFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                            Date parsedDate = httpFormat.parse(dateHeader);
                            if (parsedDate != null) {
                                SimpleDateFormat displayFormat = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
                                finalTimestampStr = displayFormat.format(parsedDate);
                            }
                        } catch (Exception e) {
                            finalTimestampStr = dateHeader;
                        }
                    }

                    java.io.InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        final String newTimestamp = finalTimestampStr;
                        runOnUiThread(() -> {
                            imgMailbox.setImageBitmap(bitmap);
                            tvMailboxTimestamp.setText(newTimestamp);
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void triggerDoorbellImage() {
        new Thread(() -> {
            try {
                URL url = new URL(BuildConfig.DOORBELL_TAKE_IMAGE_URL);
                android.util.Log.d("GrefsenveienApp", "MainActivity -> Calling URL to take new doorbell image");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void makeUrlRequest(String targetName) {
        // Update UI to show waiting state
        if (targetName.equals("garasjen")) {
            garageStatus = "Sender melding...";
        } else {
            gateStatus = "Sender melding...";
        }
        updateStatusText();

        // Make request in background thread
        new Thread(() -> {
            try {
                URL url;
                if (targetName.equals("garasjen")) {
                    url = new URL(BuildConfig.GARAGE_WEBHOOK_URL);
                } else {
                    url = new URL(BuildConfig.GATE_WEBHOOK_URL);
                }
                
                android.util.Log.i("GrefsenveienApp", "MainActivity (Phone) -> Calling webhook: " + url.toString());
                
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String userEmail = prefs.getString("user_email", "unknown");
                String payload = "{\"token\":\"Xi3gQF4GTFR7aENMkMjftt4P\",\"user\":\"" + userEmail + "\"}";

                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Get response code 
                int responseCode = connection.getResponseCode();
                connection.disconnect();

                // Update UI on main thread with result
                runOnUiThread(() -> {
                    String finalTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    String resultMsg;
                    if (responseCode == 200) {
                        resultMsg = "Sendt kl " + finalTime;
                    } else {
                        resultMsg = "Feil (Kode " + responseCode + ") kl " + finalTime;
                    }
                    
                    if (targetName.equals("garasjen")) {
                        garageStatus = resultMsg;
                    } else {
                        gateStatus = resultMsg;
                    }
                    updateStatusText();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    String errorTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    if (targetName.equals("garasjen")) {
                        garageStatus = "Nettverksfeil kl " + errorTime;
                    } else {
                        gateStatus = "Nettverksfeil kl " + errorTime;
                    }
                    updateStatusText();
                });
            }
        }).start();
    }
}
