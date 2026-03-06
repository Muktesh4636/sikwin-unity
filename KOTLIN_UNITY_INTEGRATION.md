# Kotlin (Sikwin) + Unity (Gundu Ata) Integration

## Status: correctly integrated (single APK)

The Sikwin app and the Gundu Ata Unity game are integrated into **one APK**. Tapping Gundu Ata in Sikwin launches the embedded Unity game and passes auth tokens so the game can skip login.

---

## What’s integrated

### 1. Single APK layout
- **App**: `com.sikwin.app` (Kotlin/Compose) – main launcher, home, login, wallet, etc.
- **Unity**: Embedded via `unityLibrary` – same APK, no separate install.
- **Build output**: `sikwin/app/build/outputs/apk/debug/Sikwin-debug.apk`.

### 2. Sikwin `unityLibrary` (embedded Unity)
- **Native libs** (from Gundu Ata.apk): `libgame.so`, `libunity.so`, `libil2cpp.so`, `libmain.so`, `libswappywrapper.so`, `libc++_shared.so` in `unityLibrary/src/main/jniLibs/arm64-v8a/`.
- **Game assets**: `unityLibrary/src/main/assets/bin/Data/` (boot.config, unity_app_guid, data.unity3d, Managed, etc.) – path required by Unity Android.
- **Unity engine**: `unity-classes.jar` in `unityLibrary/src/main/libs/`.
- **Activity**: `UnityPlayerGameActivity.java` (from Unity project) – loads `libgame`, hosts the Unity view, sends tokens to GameManager.
- **Token holder**: `UnityTokenHolder.kt` – stores access/refresh for Unity to read.

### 3. Kotlin → Unity token flow
- **SessionManager.syncAuthToUnity()**: Writes tokens to SharedPreferences/PlayerPrefs and **UnityTokenHolder**.
- **UnityTokenHelper**: Sends tokens via **UnitySendMessage** (`GameManager`, `SetAccessAndRefreshTokens`, JSON) and **broadcast** `com.sikwin.app.TOKEN_UPDATE`.
- **On game launch**: `executeGameLaunch()` calls `syncAuthToUnity()`, `UnityTokenHolder.setTokens()`, `UnityTokenHelper.sendTokensToUnity()`, then starts `UnityPlayerGameActivity` with Intent extras (`access`, `refresh`, etc.).
- **Unity activity**: In `onResume`, reads tokens from Intent or UnityTokenHolder and, after 1.5 s, calls `UnityPlayer.UnitySendMessage("GameManager", "SetAccessAndRefreshTokens", json)` so the C# game can skip login.

### 4. Manifest and launch
- **AndroidManifest**: Declares `com.unity3d.player.UnityPlayerGameActivity` with `android.app.lib_name=game`, `unityplayer.UnityActivity=true`, and **windowSoftInputMode=adjustResize|stateVisible** so the keyboard works when typing in Unity.
- **Launch**: Home → tap Gundu Ata (when logged in) → `AppNavigation.executeGameLaunch()` → `startActivity(UnityPlayerGameActivity)` with token extras.

### 5. Back from game to Kotlin
- **Device back key**: `UnityPlayerGameActivity` handles `KEYCODE_BACK` and calls `finish()`, so the user returns to the Kotlin app. `OnBackPressedDispatcher` is also used when available.
- **In-game back symbol**: The activity exposes `goBackToKotlin()`. From Unity C#, when the user taps your in-game back button, call:
```csharp
#if UNITY_ANDROID && !UNITY_EDITOR
using UnityEngine;

public static void GoBackToKotlin()
{
    try
    {
        var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        activity?.Call("goBackToKotlin");
    }
    catch (System.Exception e) { Debug.LogWarning("GoBackToKotlin: " + e.Message); }
}
#endif
```
  Call `GoBackToKotlin()` from your back button’s `OnClick` (or equivalent).

### 6. Balance tap → Deposit in Kotlin
- When the user taps the **balance** in the game, call from Unity C#:
```csharp
#if UNITY_ANDROID && !UNITY_EDITOR
var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
activity?.Call("openDepositInKotlin");
#endif
```
  This brings the Kotlin app to the front and navigates to the **Deposit** page, then closes the game screen. A snippet is in **`unity/OpenDepositInKotlin.cs.snippet`** — call `OpenDepositInKotlin.OpenDeposit()` from your balance button’s `OnClick`.

### 7. Tokens written before Unity starts
- In **onCreate()** (before Unity loads), the activity reads tokens from Intent / UnityTokenHolder / `gunduata_prefs` and writes them to all Unity PlayerPrefs files (`com.company.dicegame.v2.playerprefs`, etc.) so Unity can read in **Start()**.

---

## Why Unity still asks you to enter details

Kotlin **does** pass tokens into Unity (PlayerPrefs + Intent + UnitySendMessage). Unity will **stop** asking for login only if your **Unity C# code** reads those tokens and skips the login screen.

You must add this in your **Unity GameManager** (or login controller) **Start()**:

```csharp
void Start()
{
    string access = PlayerPrefs.GetString("access", "");
    if (string.IsNullOrEmpty(access)) access = PlayerPrefs.GetString("auth_token", "");
    string refresh = PlayerPrefs.GetString("refresh", "");
    if (string.IsNullOrEmpty(refresh)) refresh = PlayerPrefs.GetString("refresh_token", "");

    if (!string.IsNullOrEmpty(access))
    {
        // Set on your API client and skip login UI, e.g.:
        // ApiClient.SetAccessToken(access);
        // ApiClient.SetRefreshToken(refresh);
        // Then show main game instead of login form
    }
}
```

Full instructions: **`unity/UNITY_READ_TOKENS_FROM_ANDROID.md`**

---

## Build and install

- **Build**: `cd sikwin && ./gradlew assembleDebug`
- **APK**: `sikwin/app/build/outputs/apk/debug/Sikwin-debug.apk`
- **Install**: `adb install -r path/to/Sikwin-debug.apk`

One APK contains both Kotlin app and Unity game; no separate Gundu Ata install.

---

## Lightning effect for high-frequency dice

When any number appears **more than 2 times** in a roll, the game applies a “lightning” effect to those dice (driven by the frequency API).

- **Logic**: `GameController` calls `GetWinningFrequency`; numbers with `Frequency > 2` are collected and passed to `DiceAndBox.ApplyLightningForNumbers()`. Each die whose face value is in that set has its optional lightning effect enabled.
- **Unity**: On the **dice prefab** used by `TargetedDiceRoller`, add a child GameObject (e.g. particle system or glow) and assign it to `TargetedDie.lightningEffect`. If assigned, it is turned on only for dice showing a number that appeared more than twice.
- **No prefab change**: If `lightningEffect` is left unassigned, the script does nothing and no errors occur.
