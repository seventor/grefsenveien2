package com.pixelspore.grefsenveien;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * Short synthesized tones for webhook action feedback in Android Auto.
 * Uses the media stream with transient audio focus so tones are not muted
 * during car projection (USAGE_NOTIFICATION is silenced in Android Auto).
 */
public final class ActionFeedbackSound {

    private static final String TAG = "GrefsenveienApp";
    private static final int SAMPLE_RATE = 22050;

    private ActionFeedbackSound() {}

    /** Ascending sweep: dark/low to bright/high. */
    public static void playSuccess(Context context) {
        playSweep(context, 380f, 980f, 210);
    }

    /** Descending sweep: bright/high to dark/low. */
    public static void playError(Context context) {
        playSweep(context, 920f, 300f, 260);
    }

    private static void playSweep(Context context, float startHz, float endHz, int durationMs) {
        if (context == null) return;
        new Thread(() -> {
            AudioTrack track = null;
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            AudioFocusRequest focusRequest = null;
            boolean hasFocus = false;
            try {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                if (audioManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        focusRequest = new AudioFocusRequest.Builder(
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                                .setAudioAttributes(attributes)
                                .setAcceptsDelayedFocusGain(false)
                                .setWillPauseWhenDucked(false)
                                .build();
                        int focusResult = audioManager.requestAudioFocus(focusRequest);
                        hasFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                        Log.d(TAG, "Feedback audio focus (media): " + focusResult);
                    } else {
                        @SuppressWarnings("deprecation")
                        int focusResult = audioManager.requestAudioFocus(
                                focusChange -> {},
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                        hasFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                        Log.d(TAG, "Feedback audio focus (legacy): " + focusResult);
                    }
                }

                int numSamples = Math.max(1, SAMPLE_RATE * durationMs / 1000);
                short[] buffer = new short[numSamples];
                double phase = 0;
                for (int i = 0; i < numSamples; i++) {
                    float progress = numSamples <= 1 ? 1f : (float) i / (numSamples - 1);
                    float freq = startHz + (endHz - startHz) * progress;
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    float envelope = envelope(progress);
                    buffer[i] = (short) (Math.sin(phase) * envelope * Short.MAX_VALUE * 0.55);
                }

                int bufferBytes = buffer.length * 2;
                track = new AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.setVolume(1.0f);
                track.write(buffer, 0, buffer.length);
                track.play();
                Log.d(TAG, "Playing feedback tone " + startHz + "->" + endHz + "Hz, focus=" + hasFocus);
                Thread.sleep(durationMs + 40L);
            } catch (Exception e) {
                Log.w(TAG, "Failed to play feedback sound", e);
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                    } catch (Exception ignored) {}
                    track.release();
                }
                if (audioManager != null && hasFocus) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                        audioManager.abandonAudioFocusRequest(focusRequest);
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        @SuppressWarnings("deprecation")
                        int unused = audioManager.abandonAudioFocus(focusChange -> {});
                    }
                }
            }
        }, "ActionFeedbackSound").start();
    }

    private static float envelope(float progress) {
        if (progress < 0.1f) {
            return progress / 0.1f;
        }
        if (progress > 0.72f) {
            return (1f - progress) / 0.28f;
        }
        return 1f;
    }
}
