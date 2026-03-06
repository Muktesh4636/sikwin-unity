package com.unity3d.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;

import com.google.androidgamesdk.GameActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnityPlayerGameActivity extends GameActivity implements IUnityPlayerLifecycleEvents, IUnityPermissionRequestSupport, IUnityPlayerSupport
{
    private static final String TAG = "UnityPlayerGA";
    private static final String GAMEOBJECT = "GameManager";
    // Unity receiver name differs between exports (some use singular Token, some plural Tokens).
    private static final String[] METHODS = {"SetAccessAndRefreshTokens", "SetAccessAndRefreshToken"};
    private boolean mTokensSentToUnity = false;

    private static final String LOGIN_METHOD = "SetLoginCredential";
    private boolean mCredsSentToUnity = false;

    private static final String SOUND_ICON_ON = "\uD83D\uDD0A";  // 🔊
    private static final String SOUND_ICON_OFF = "\uD83D\uDD07";   // 🔇
    private boolean soundMuted = false;
    private int savedVolumeBeforeMute = -1;

    private View backOverlayView;
    private View balanceOverlayView;
    private View soundOverlayView;
    private View micOverlayView;
    private View lightningOverlayView;

    private static final String FREQUENCY_API_URL = "https://gunduata.club/api/game/frequency/";
    private static final int LIGHTNING_CHECK_DELAY_MS = 5000;
    private static final int LIGHTNING_DISPLAY_MS = 3000;
    // Disable the "spark/lightning" overlay unless explicitly needed.
    // Users reported seeing a spark symbol while playing.
    private static final boolean ENABLE_LIGHTNING_OVERLAY = false;
    private static final int IME_HIDE_DURATION_MS = 15000;
    private static final int IME_HIDE_INTERVAL_MS = 400;
    private final ExecutorService frequencyExecutor = Executors.newSingleThreadExecutor();
    private final Handler imeHideHandler = new Handler(Looper.getMainLooper());
    private Runnable imeHideRunnable;
    private final Handler micOverlayHandler = new Handler(Looper.getMainLooper());
    private Runnable micOverlayRunnable;

    class GameActivitySurfaceView extends InputEnabledSurfaceView
    {
        GameActivity mGameActivity;
        public GameActivitySurfaceView(GameActivity activity) {
            super(activity);
            mGameActivity = activity;
        }

        // Reroute motion events from captured pointer to normal events
        // Otherwise when doing Cursor.lockState = CursorLockMode.Locked from C# the touch and mouse events will stop working
        @Override public boolean onCapturedPointerEvent(MotionEvent event) {
            return mGameActivity.onTouchEvent(event);
        }
    }

    protected UnityPlayerForGameActivity mUnityPlayer;
    protected String updateUnityCommandLineArguments(String cmdLine)
    {
        return cmdLine;
    }

    static
    {
        System.loadLibrary("game");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        copyTokensToUnityPrefsBeforeUnityStarts();
        // IMPORTANT: do NOT persist raw credentials into Unity prefs when tokens are available.
        // Unity storing creds is the main reason it re-logins and invalidates Kotlin session.
        copyCredentialsToUnityPrefsBeforeUnityStarts();
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // Ensure back (device or in-game) always returns to Kotlin app
        try {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish();
                }
            });
        } catch (Throwable t) {
            Log.d(TAG, "OnBackPressedDispatcher not available: " + t.getMessage());
        }
    }

    /** Write tokens to Unity PlayerPrefs (SharedPreferences) BEFORE super.onCreate so Unity sees them when it loads. */
    private void copyTokensToUnityPrefsBeforeUnityStarts() {
        String access = null, refresh = null;
        Intent intent = getIntent();
        if (intent != null) {
            access = intent.getStringExtra("access");
            if (access == null) access = intent.getStringExtra("access_token");
            if (access == null) access = intent.getStringExtra("auth_token");
            if (access == null) access = intent.getStringExtra("token");
            refresh = intent.getStringExtra("refresh");
            if (refresh == null) refresh = intent.getStringExtra("refresh_token");
        }
        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) {
            try {
                Class<?> holder = Class.forName("com.unity3d.player.UnityTokenHolder");
                access = (String) holder.getMethod("getAccessToken").invoke(null);
                refresh = (String) holder.getMethod("getRefreshToken").invoke(null);
                if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty())) {
                    Log.d(TAG, "copyTokensToUnityPrefs: from UnityTokenHolder");
                }
            } catch (Throwable ignored) {}
        }
        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("gunduata_prefs", MODE_PRIVATE);
            access = prefs.getString("access", null);
            if (access == null) access = prefs.getString("user_token", null);
            if (access == null) access = prefs.getString("auth_token", null);
            refresh = prefs.getString("refresh", null);
            if (refresh == null) refresh = prefs.getString("refresh_token", null);
            if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty())) {
                Log.d(TAG, "copyTokensToUnityPrefs: from gunduata_prefs");
            }
        }
        if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty())) {
            if (intent != null && (intent.getStringExtra("access") != null || intent.getStringExtra("access_token") != null)) {
                Log.d(TAG, "copyTokensToUnityPrefs: from Intent");
            }
            writeTokensToAllPrefs(access != null ? access : "", refresh != null ? refresh : "");
        } else {
            Log.w(TAG, "copyTokensToUnityPrefs: no tokens (Intent/UnityTokenHolder/gunduata_prefs)");
        }
    }

    /** Write raw username/password to Unity PlayerPrefs (SharedPreferences) BEFORE super.onCreate so Unity can read in Awake/Start. */
    private void copyCredentialsToUnityPrefsBeforeUnityStarts() {
        Intent intent = getIntent();
        if (intent == null) return;
        // If we already have tokens, avoid writing creds to prefs (prevents Unity auto-login).
        String access = intent.getStringExtra("access");
        if (access == null) access = intent.getStringExtra("access_token");
        if (access == null) access = intent.getStringExtra("auth_token");
        if (access == null) access = intent.getStringExtra("token");
        String refresh = intent.getStringExtra("refresh");
        if (refresh == null) refresh = intent.getStringExtra("refresh_token");
        if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty())) {
            return;
        }
        String username = intent.getStringExtra("username");
        String password = intent.getStringExtra("password");
        if ((username == null || username.isEmpty()) && (password == null || password.isEmpty())) return;
        writeCredentialsToAllPrefs(username != null ? username : "", password != null ? password : "");
        Log.d(TAG, "Wrote credentials to Unity prefs (user=" + (username != null && !username.isEmpty()) + " pass=" + (password != null && !password.isEmpty()) + ")");
    }

    @Override
    public UnityPlayerForGameActivity getUnityPlayerConnection() {
        return mUnityPlayer;
    }

    // Soft keyboard relies on inset listener for listening to various events - keyboard opened/closed/text entered.
    private void applyInsetListener(SurfaceView surfaceView)
    {
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> onApplyWindowInsets(surfaceView, ViewCompat.getRootWindowInsets(getWindow().getDecorView())));
    }

    @Override protected InputEnabledSurfaceView createSurfaceView() {
        return new GameActivitySurfaceView(this);
    }

    @Override protected void onCreateSurfaceView() {
        super.onCreateSurfaceView();
        FrameLayout frameLayout = findViewById(contentViewId);

        applyInsetListener(mSurfaceView);

        mSurfaceView.setId(UnityPlayerForGameActivity.getUnityViewIdentifier(this));

        String cmdLine = updateUnityCommandLineArguments(getIntent().getStringExtra("unity"));
        getIntent().putExtra("unity", cmdLine);
        // Unity requires access to frame layout for setting the static splash screen.
        // Note: we cannot initialize in onCreate (after super.onCreate), because game activity native thread would be already started and unity runtime initialized
        //       we also cannot initialize before super.onCreate since frameLayout is not yet available.
        mUnityPlayer = new UnityPlayerForGameActivity(this, frameLayout, mSurfaceView, this);

        // Overlay elevation so they stay above Unity's dynamically added views
        float overlayElevationPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, getResources().getDisplayMetrics());

        // Top-left overlay (back or balance): tap to return to Kotlin deposit page (no Unity rebuild needed)
        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, getResources().getDisplayMetrics());
        backOverlayView = new View(this);
        backOverlayView.setBackgroundColor(0x00000000);
        backOverlayView.setClickable(true);
        backOverlayView.setFocusable(true);
        backOverlayView.setOnClickListener(v -> openDepositInKotlin());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) backOverlayView.setElevation(overlayElevationPx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
        lp.gravity = Gravity.START | Gravity.TOP;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        frameLayout.addView(backOverlayView, lp);

        // Top-right balance overlay: tap to open Deposit page in Kotlin (no Unity rebuild needed)
        int balanceW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, getResources().getDisplayMetrics());
        int balanceH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());
        balanceOverlayView = new View(this);
        balanceOverlayView.setBackgroundColor(0x00000000);
        balanceOverlayView.setClickable(true);
        balanceOverlayView.setFocusable(true);
        balanceOverlayView.setOnClickListener(v -> openDepositInKotlin());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) balanceOverlayView.setElevation(overlayElevationPx);
        FrameLayout.LayoutParams balanceLp = new FrameLayout.LayoutParams(balanceW, balanceH);
        balanceLp.gravity = Gravity.END | Gravity.TOP;
        balanceLp.rightMargin = 0;
        balanceLp.topMargin = 0;
        frameLayout.addView(balanceOverlayView, balanceLp);

        // Sound icon (mute/unmute) below the exposure (EXP) row, top-right
        int soundSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        int soundTopPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 76, getResources().getDisplayMetrics()); // under exposure row
        int soundRightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        soundOverlayView = new TextView(this);
        ((TextView) soundOverlayView).setText(SOUND_ICON_ON);
        ((TextView) soundOverlayView).setTextSize(22);
        ((TextView) soundOverlayView).setGravity(Gravity.CENTER);
        soundOverlayView.setBackgroundColor(0x33000000);
        soundOverlayView.setClickable(true);
        soundOverlayView.setFocusable(true);
        soundOverlayView.setOnClickListener(v -> toggleSoundMute((TextView) soundOverlayView));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) soundOverlayView.setElevation(overlayElevationPx);
        FrameLayout.LayoutParams soundLp = new FrameLayout.LayoutParams(soundSizePx, soundSizePx);
        soundLp.gravity = Gravity.END | Gravity.TOP;
        soundLp.topMargin = soundTopPx;
        soundLp.rightMargin = soundRightPx;
        frameLayout.addView(soundOverlayView, soundLp);

        // Mic icon overlay: show 15 seconds after game opens
        int micSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        int micTopPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 124, getResources().getDisplayMetrics());
        int micRightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        micOverlayView = new TextView(this);
        ((TextView) micOverlayView).setText("\uD83C\uDFA4"); // 🎤
        ((TextView) micOverlayView).setTextSize(22);
        ((TextView) micOverlayView).setGravity(Gravity.CENTER);
        micOverlayView.setBackgroundColor(0x33000000);
        micOverlayView.setClickable(false);
        micOverlayView.setFocusable(false);
        micOverlayView.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micOverlayView.setElevation(overlayElevationPx);
        FrameLayout.LayoutParams micLp = new FrameLayout.LayoutParams(micSizePx, micSizePx);
        micLp.gravity = Gravity.END | Gravity.TOP;
        micLp.topMargin = micTopPx;
        micLp.rightMargin = micRightPx;
        frameLayout.addView(micOverlayView, micLp);

        // Lightning effect overlay: full-screen glow when any number has frequency > 2 (no Unity rebuild)
        FrameLayout lightningContainer = new FrameLayout(this);
        lightningContainer.setBackgroundColor(Color.argb(100, 255, 255, 200));
        lightningContainer.setClickable(false);
        lightningContainer.setVisibility(View.GONE);
        TextView lightningEmoji = new TextView(this);
        lightningEmoji.setText("\u26A1");
        lightningEmoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 72);
        lightningEmoji.setTextColor(Color.argb(220, 255, 255, 0));
        lightningEmoji.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams emojiLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        emojiLp.gravity = Gravity.CENTER;
        lightningContainer.addView(lightningEmoji, emojiLp);
        FrameLayout.LayoutParams lightningLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) lightningContainer.setElevation(overlayElevationPx - 10);
        frameLayout.addView(lightningContainer, lightningLp);
        lightningOverlayView = lightningContainer;
    }

    private void bringOverlaysToFront() {
        if (backOverlayView != null) backOverlayView.bringToFront();
        if (balanceOverlayView != null) balanceOverlayView.bringToFront();
        if (soundOverlayView != null) soundOverlayView.bringToFront();
        if (micOverlayView != null) micOverlayView.bringToFront();
    }

    private void toggleSoundMute(TextView soundIcon) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (soundMuted) {
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int restore = savedVolumeBeforeMute >= 0 ? savedVolumeBeforeMute : maxVol / 2;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0);
            soundMuted = false;
            soundIcon.setText(SOUND_ICON_ON);
        } else {
            savedVolumeBeforeMute = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            soundMuted = true;
            soundIcon.setText(SOUND_ICON_OFF);
        }
    }

    private String getAccessTokenForApi() {
        Intent intent = getIntent();
        if (intent != null) {
            String t = intent.getStringExtra("access");
            if (t == null) t = intent.getStringExtra("access_token");
            if (t == null) t = intent.getStringExtra("auth_token");
            if (t == null) t = intent.getStringExtra("token");
            if (t != null && !t.isEmpty()) return t;
        }
        try {
            Class<?> holder = Class.forName("com.unity3d.player.UnityTokenHolder");
            Object tok = holder.getMethod("getAccessToken").invoke(null);
            if (tok != null && !tok.toString().isEmpty()) return tok.toString();
        } catch (Throwable ignored) {}
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("gunduata_prefs", MODE_PRIVATE);
        String t = prefs.getString("access", null);
        if (t == null) t = prefs.getString("auth_token", null);
        if (t == null) t = prefs.getString("user_token", null);
        return t;
    }

    private void fetchWinningFrequencyAndShowLightning() {
        if (!ENABLE_LIGHTNING_OVERLAY) return;
        String token = getAccessTokenForApi();
        if (token == null || token.isEmpty()) return;
        frequencyExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(FREQUENCY_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                if (code != 200) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                JSONArray arr = json.optJSONArray("WinningNumbers");
                if (arr == null) arr = json.optJSONArray("winning_numbers");
                if (arr == null) return;
                Set<Integer> hot = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    int freq = o.optInt("frequency", o.optInt("Frequency", 0));
                    if (freq > 2) {
                        int num = o.optInt("number", o.optInt("Number", 0));
                        if (num >= 1 && num <= 6) hot.add(num);
                    }
                }
                if (!hot.isEmpty()) {
                    runOnUiThread(() -> showLightningOverlay());
                }
            } catch (Throwable t) {
                Log.d(TAG, "Frequency API / lightning check: " + t.getMessage());
            } finally {
                if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        });
    }

    private void showLightningOverlay() {
        if (!ENABLE_LIGHTNING_OVERLAY) return;
        if (lightningOverlayView == null) return;
        lightningOverlayView.setVisibility(View.VISIBLE);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (lightningOverlayView != null) lightningOverlayView.setVisibility(View.GONE);
        }, LIGHTNING_DISPLAY_MS);
    }

    @Override
    public void onUnityPlayerUnloaded() {
        finish();
    }

    @Override
    public void onUnityPlayerQuitted() {
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Call this from Unity when the user taps the in-game back button so they return to the Kotlin app.
     * From Unity C#: new AndroidJavaClass("com.unity3d.player.UnityPlayer").GetStatic<AndroidJavaObject>("currentActivity").Call("goBackToKotlin");
     */
    public void goBackToKotlin() {
        runOnUiThread(this::finish);
    }

    /**
     * Call this from Unity when the user taps the balance so they go to the Deposit page in the Kotlin app.
     * From Unity C#: new AndroidJavaClass("com.unity3d.player.UnityPlayer").GetStatic<AndroidJavaObject>("currentActivity").Call("openDepositInKotlin");
     */
    public void openDepositInKotlin() {
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent();
                intent.setClassName(getPackageName(), "com.sikwin.app.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("redirect", "deposit");
                startActivity(intent);
                finish();
            } catch (Throwable t) {
                Log.e(TAG, "openDepositInKotlin failed: " + t.getMessage());
            }
        });
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        try { frequencyExecutor.shutdown(); } catch (Throwable ignored) {}
        mUnityPlayer.destroy();
        super.onDestroy();
    }

    @Override protected void onStop()
    {
        // Note: we want Java onStop callbacks to be processed before the native part processes the onStop callback
        mUnityPlayer.onStop();
        super.onStop();
    }

    @Override protected void onStart()
    {
        // Note: we want Java onStart callbacks to be processed before the native part processes the onStart callback
        mUnityPlayer.onStart();
        super.onStart();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        imeHideHandler.removeCallbacks(imeHideRunnable);
        // Note: we want Java onPause callbacks to be processed before the native part processes the onPause callback
        mUnityPlayer.onPause();
        super.onPause();

        // Stop any pending mic overlay show when leaving game
        if (micOverlayRunnable != null) {
            micOverlayHandler.removeCallbacks(micOverlayRunnable);
        }
        if (micOverlayView != null) {
            micOverlayView.setVisibility(View.GONE);
        }
    }

    // Resume Unity
    @Override protected void onResume()
    {
        // Note: we want Java onResume callbacks to be processed before the native part processes the onResume callback
        mUnityPlayer.onResume();
        super.onResume();
        // Defer token/credential sending and overlay bring-to-front so they don't block the first frame (reduces freeze)
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(() -> {
            sendTokensToUnityWhenReady();
            sendCredentialsToUnityWhenReady();
            bringOverlaysToFront();
        }, 80);
        mainHandler.postDelayed(this::bringOverlaysToFront, 600);
        if (ENABLE_LIGHTNING_OVERLAY) {
            mainHandler.postDelayed(this::fetchWinningFrequencyAndShowLightning, LIGHTNING_CHECK_DELAY_MS);
        }
        scheduleHideImeDuringLoading();

        // Show mic icon after 15 seconds from game open
        if (micOverlayRunnable != null) {
            micOverlayHandler.removeCallbacks(micOverlayRunnable);
        }
        micOverlayRunnable = () -> {
            try {
                if (micOverlayView != null) {
                    micOverlayView.setVisibility(View.VISIBLE);
                    bringOverlaysToFront();
                }
            } catch (Throwable ignored) {}
        };
        micOverlayHandler.postDelayed(micOverlayRunnable, 15000);
    }

    private void scheduleHideImeDuringLoading() {
        imeHideHandler.removeCallbacks(imeHideRunnable);
        final long start = android.os.SystemClock.uptimeMillis();
        imeHideRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                long elapsed = android.os.SystemClock.uptimeMillis() - start;
                if (elapsed >= IME_HIDE_DURATION_MS) {
                    try {
                        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    } catch (Throwable t) {
                        Log.d(TAG, "setSoftInputMode: " + t.getMessage());
                    }
                    return;
                }
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && getWindow() != null && getWindow().getDecorView() != null) {
                        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "hideSoftInput: " + t.getMessage());
                }
                imeHideHandler.postDelayed(this, IME_HIDE_INTERVAL_MS);
            }
        };
        imeHideHandler.post(imeHideRunnable);
    }

    /** Send raw username/password to Unity GameManager.SetLoginCredential when launched from Sikwin. */
    private void sendCredentialsToUnityWhenReady() {
        if (mCredsSentToUnity) return;
        Intent intent = getIntent();
        if (intent == null) return;

        // If tokens are available, do NOT send creds. Unity should rely on tokens only.
        String access = intent.getStringExtra("access");
        if (access == null) access = intent.getStringExtra("access_token");
        if (access == null) access = intent.getStringExtra("auth_token");
        if (access == null) access = intent.getStringExtra("token");
        String refresh = intent.getStringExtra("refresh");
        if (refresh == null) refresh = intent.getStringExtra("refresh_token");
        if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty())) {
            return;
        }

        final String username = intent.getStringExtra("username");
        final String password = intent.getStringExtra("password");
        if ((username == null || username.isEmpty()) && (password == null || password.isEmpty())) return;

        final Handler h = new Handler(Looper.getMainLooper());
        // Retry longer to cover first-scene load + GameManager instantiation timing.
        final int[] delaysMs = {800, 2000, 4500, 9000, 15000};
        for (int i = 0; i < delaysMs.length; i++) {
            final int delay = delaysMs[i];
            h.postDelayed(() -> {
                try {
                    String json = new JSONObject().put("username", username != null ? username : "").put("password", password != null ? password : "").toString();
                    UnityPlayer.UnitySendMessage(GAMEOBJECT, LOGIN_METHOD, json);
                    mCredsSentToUnity = true;
                    Log.d(TAG, "UnitySendMessage(GameManager, SetLoginCredential) at " + delay + "ms");
                } catch (Throwable t) {
                    Log.e(TAG, "Send credentials to Unity failed: " + t.getMessage());
                }
            }, delay);
        }
    }

    private static final String PREFS_NAME = "gunduata_prefs";
    /** All SharedPreferences Unity / SessionManager may read (PlayerPrefs and app prefs). */
    private static final String[] UNITY_PREF_NAMES = {
        "com.company.dicegame.v2.playerprefs",
        "com.sikwin.app.v2.playerprefs",
        "com.sikwin.app.playerprefs",
        "gunduata_prefs",
        "UnityPlayerPrefs",
        "dicegame.v2.playerprefs",
        "PlayerPrefs",
        "com.sikwin.app_playerprefs"
    };

    /** Write tokens to every prefs file Unity might read (PlayerPrefs on Android = SharedPreferences). */
    private void writeTokensToAllPrefs(String access, String refresh) {
        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) return;
        String acc = access != null ? access : "";
        String ref = refresh != null ? refresh : "";
        android.content.Context ctx = getApplicationContext();
        for (String name : UNITY_PREF_NAMES) {
            try {
                SharedPreferences p = ctx.getSharedPreferences(name, MODE_PRIVATE);
                SharedPreferences.Editor e = p.edit();
                if (!acc.isEmpty()) {
                    e.putString("access", acc);
                    e.putString("auth_token", acc);
                    e.putString("access_token", acc);
                    e.putString("accessToken", acc);
                    e.putString("AccessToken", acc);
                    e.putString("user_token", acc);
                    e.putString("token", acc);
                    e.putString("TOKEN", acc);
                }
                if (!ref.isEmpty()) {
                    e.putString("refresh", ref);
                    e.putString("refresh_token", ref);
                    e.putString("refreshToken", ref);
                    e.putString("RefreshToken", ref);
                    e.putString("REFRESH_TOKEN", ref);
                    e.putString("REFRESH", ref);
                }
                e.putString("is_logged_in", "true");
                e.apply();
            } catch (Throwable t) {
                Log.w(TAG, "writeTokensToAllPrefs " + name + ": " + t.getMessage());
            }
        }
        Log.d(TAG, "Wrote tokens to all Unity prefs (accessLen=" + acc.length() + ")");
    }

    /** Write username/password to every prefs file Unity might read (PlayerPrefs on Android = SharedPreferences). */
    private void writeCredentialsToAllPrefs(String username, String password) {
        if ((username == null || username.isEmpty()) && (password == null || password.isEmpty())) return;
        String user = username != null ? username : "";
        String pass = password != null ? password : "";
        android.content.Context ctx = getApplicationContext();
        for (String name : UNITY_PREF_NAMES) {
            try {
                // Never write raw credentials into Kotlin app prefs.
                // This prevents Unity from auto-login later and invalidating Kotlin session.
                if ("gunduata_prefs".equals(name) || (name != null && name.endsWith("_preferences")) || getPackageName().equals(name)) {
                    continue;
                }
                SharedPreferences p = ctx.getSharedPreferences(name, MODE_PRIVATE);
                SharedPreferences.Editor e = p.edit();
                if (!user.isEmpty()) {
                    e.putString("username", user);
                    e.putString("USERNAME_KEY", user);
                    e.putString("UserName", user);
                }
                if (!pass.isEmpty()) {
                    e.putString("password", pass);
                    e.putString("PASSWORD_KEY", pass);
                    e.putString("Password", pass);
                    e.putString("user_pass", pass);
                }
                e.apply();
            } catch (Throwable t) {
                Log.w(TAG, "writeCredentialsToAllPrefs " + name + ": " + t.getMessage());
            }
        }
    }

    /** Send access/refresh to Unity: 1) read from Intent / UnityTokenHolder / gunduata_prefs, 2) write to all prefs, 3) UnitySendMessage at multiple delays. */
    private void sendTokensToUnityWhenReady() {
        if (mTokensSentToUnity) return;
        Intent intent = getIntent();
        String access = null, refresh = null;
        if (intent != null) {
            access = intent.getStringExtra("access");
            if (access == null) access = intent.getStringExtra("access_token");
            if (access == null) access = intent.getStringExtra("auth_token");
            if (access == null) access = intent.getStringExtra("token");
            refresh = intent.getStringExtra("refresh");
            if (refresh == null) refresh = intent.getStringExtra("refresh_token");
        }

        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) {
            try {
                Class<?> holder = Class.forName("com.unity3d.player.UnityTokenHolder");
                access = (String) holder.getMethod("getAccessToken").invoke(null);
                refresh = (String) holder.getMethod("getRefreshToken").invoke(null);
            } catch (Throwable t) {
                Log.d(TAG, "UnityTokenHolder: " + t.getMessage());
            }
        }

        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            access = prefs.getString("access", null);
            if (access == null) access = prefs.getString("user_token", null);
            if (access == null) access = prefs.getString("auth_token", null);
            refresh = prefs.getString("refresh", null);
            if (refresh == null) refresh = prefs.getString("refresh_token", null);
            Log.d(TAG, "From gunduata_prefs: access=" + (access != null && !access.isEmpty()) + " refresh=" + (refresh != null && !refresh.isEmpty()));
        }

        if ((access == null || access.isEmpty()) && (refresh == null || refresh.isEmpty())) {
            Log.w(TAG, "No tokens found (Intent/UnityTokenHolder/gunduata_prefs). Login in Sikwin first.");
            return;
        }

        final String acc = access != null ? access : "";
        final String ref = refresh != null ? refresh : "";
        Log.d(TAG, "Sending tokens to Unity: accessLen=" + acc.length() + " refreshLen=" + ref.length());

        writeTokensToAllPrefs(acc, ref);

        final Handler h = new Handler(Looper.getMainLooper());
        final int[] delaysMs = {500, 1500, 3500, 8000};
        for (int i = 0; i < delaysMs.length; i++) {
            final int delay = delaysMs[i];
            h.postDelayed(() -> {
                try {
                    // Send multiple key names for maximum Unity compatibility
                    String json = new JSONObject()
                        .put("access", acc)
                        .put("refresh", ref)
                        .put("access_token", acc)
                        .put("refresh_token", ref)
                        .put("accessToken", acc)
                        .put("refreshToken", ref)
                        .put("AccessToken", acc)
                        .put("RefreshToken", ref)
                        .put("REFRESH_TOKEN", ref)
                        .put("token", acc)
                        .put("auth_token", acc)
                        .toString();
                    // Also send a couple of non-JSON formats in case Unity expects a delimiter instead of JSON.
                    String pipe = acc + "|" + ref;
                    String newline = acc + "\n" + ref;
                    for (int mi = 0; mi < METHODS.length; mi++) {
                        UnityPlayer.UnitySendMessage(GAMEOBJECT, METHODS[mi], json);
                        UnityPlayer.UnitySendMessage(GAMEOBJECT, METHODS[mi], pipe);
                        UnityPlayer.UnitySendMessage(GAMEOBJECT, METHODS[mi], newline);
                    }
                    mTokensSentToUnity = true;
                    Log.d(TAG, "UnitySendMessage(GameManager, SetAccessAndRefreshToken(s)) at " + delay + "ms");
                } catch (Throwable t) {
                    Log.e(TAG, "UnitySendMessage failed: " + t.getMessage());
                }
            }, delay);
        }
    }

    // Configuration changes are used by Video playback logic in Unity
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        mUnityPlayer.configurationChanged(newConfig);
        super.onConfigurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        mUnityPlayer.windowFocusChanged(hasFocus);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && (intent.hasExtra("access") || intent.hasExtra("access_token") || intent.hasExtra("refresh") || intent.hasExtra("refresh_token"))) {
            mTokensSentToUnity = false;
            String acc = intent.getStringExtra("access");
            if (acc == null) acc = intent.getStringExtra("access_token");
            if (acc == null) acc = intent.getStringExtra("auth_token");
            if (acc == null) acc = intent.getStringExtra("token");
            String ref = intent.getStringExtra("refresh");
            if (ref == null) ref = intent.getStringExtra("refresh_token");
            if ((acc != null && !acc.isEmpty()) || (ref != null && !ref.isEmpty())) {
                writeTokensToAllPrefs(acc != null ? acc : "", ref != null ? ref : "");
                Log.d(TAG, "onNewIntent: wrote tokens to Unity prefs");
            }
        }
        if (intent != null && (intent.hasExtra("username") || intent.hasExtra("password"))) {
            mCredsSentToUnity = false;
            String u = intent.getStringExtra("username");
            String p = intent.getStringExtra("password");
            if ((u != null && !u.isEmpty()) || (p != null && !p.isEmpty())) {
                writeCredentialsToAllPrefs(u != null ? u : "", p != null ? p : "");
                Log.d(TAG, "onNewIntent: wrote credentials to Unity prefs");
            }
        }
        mUnityPlayer.newIntent(intent);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void requestPermissions(PermissionRequest request)
    {
        mUnityPlayer.addPermissionRequest(request);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mUnityPlayer.permissionResponse(this, requestCode, permissions, grantResults);
    }
}
