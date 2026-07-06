# Kotlin + Unity integration (this project)

How the **Jetpack Compose / Kotlin app** (`sikwin/app`) and the **Unity game** (`unityLibrary`) work together in one APK.

## Build wiring

| Piece | Location |
|-------|----------|
| Include Unity module | `sikwin/settings.gradle` → `include ':unityLibrary'` |
| App depends on Unity | `sikwin/app/build.gradle` → `implementation project(':unityLibrary')` |
| Unity Android export | `sikwin/unityLibrary/` (jars, native libs, assets) |

## Runtime model

- **Kotlin:** Compose UI, `NavHost`, Retrofit (`RetrofitClient`), `SessionManager`.
- **Unity:** Hosted in **`com.unity3d.player.UnityPlayerGameActivity`** (declared in `sikwin/app/src/main/AndroidManifest.xml`).
- Unity may run in a **separate process** (`:unity`), so auth must be shared via **SharedPreferences**, **static holder**, **Intent extras**, or **UnitySendMessage** — not only Kotlin heap memory.

## Launching the game from Kotlin

Kotlin **does not** embed Unity inside a Compose `AndroidView` for the main game flow. It **starts** Unity’s activity with an `Intent` and token-related extras.

Primary entry: **`AppNavigation.kt`** → `executeGameLaunch()` → `Intent` to `UnityPlayerGameActivity`.

Also: **`GunduAtaGameScreen.kt`** launches the same activity.

## Auth: Kotlin → Unity

| Mechanism | Source file | Purpose |
|-----------|-------------|---------|
| Static token holder | `unityLibrary/.../UnityTokenHolder.kt` (stub or export) | Fast path for Unity startup |
| `UnityTokenHolder.setTokens` | Called from `SessionManager`, `GunduAtaViewModel`, `AppNavigation` | Set access/refresh before Unity reads prefs |
| SharedPreferences sync | `SessionManager.syncAuthToUnity()` | Writes tokens under multiple pref names Unity may read (`*.v2.playerprefs`, `gunduata_prefs`, `UnityPlayerPrefs`, etc.); uses `commit()` for cross-process visibility |
| UnitySendMessage (JSON) | `UnityTokenHelper.kt` | Reflection `UnityPlayer.UnitySendMessage` → GameObject methods (e.g. token setters) when Unity is running |
| Intent extras | `AppNavigation.executeGameLaunch()` | `token`, `refresh_token`, `user_id`, optional preloaded colour-round fields |
| Broadcast | `SessionManager` → `com.sikwin.app.TOKEN_UPDATE` | Optional listener on Unity side |

Code intentionally avoids pushing **username/password** into Unity in some paths to reduce backend **single-session** / logout conflicts (see comments in `AppNavigation.kt`).

## Auth: Unity → Kotlin

| Mechanism | Source file |
|-----------|-------------|
| Read newer tokens from Unity prefs | `SessionManager.syncAuthFromUnityPrefs()` |

Used when Unity refreshes login or Kotlin should adopt Unity’s session after returning from the game.

## API base URL

REST calls use **`Constants.BASE_URL`** (`sikwin/app/src/main/java/com/sikwin/app/utils/Constants.kt`). Unity typically talks to the **same backend** using tokens supplied by Kotlin / `UnityTokenHolder`.

## Logout / cleanup

- `GunduAtaViewModel.clearUnityAuthentication()` / `SessionManager` scrubbing clears Unity-facing prefs so stale Unity auto-login does not invalidate Kotlin’s session.

## Key files (quick open)

- `sikwin/app/src/main/java/com/sikwin/app/navigation/AppNavigation.kt` — `executeGameLaunch()`
- `sikwin/app/src/main/java/com/sikwin/app/data/auth/SessionManager.kt` — `syncAuthToUnity()`, `syncAuthFromUnityPrefs()`
- `sikwin/app/src/main/java/com/sikwin/app/utils/UnityTokenHelper.kt` — `UnitySendMessage`
- `sikwin/app/src/main/java/com/sikwin/app/ui/screens/GunduAtaGameScreen.kt` — alternate Unity launch
- `sikwin/app/src/main/java/com/sikwin/app/data/api/RetrofitClient.kt` — OkHttp / Retrofit for Kotlin APIs
- `sikwin/app/src/main/AndroidManifest.xml` — `UnityPlayerGameActivity`

---

*Generated as project documentation; adjust if your Unity export renames activities or PlayerPrefs keys.*
