# Unity: How to Use Tokens Passed from Sikwin (Android)

Android writes your access/refresh tokens into **PlayerPrefs** before Unity loads, and also sends them via **UnitySendMessage**. For the game to **stop asking for login**, your Unity code must read these and set them on your API/auth client.

## 1. Read from PlayerPrefs in Start() (recommended)

In your **GameManager** (or the script that controls login), add this in **Start()**:

```csharp
void Start()
{
    // Tokens from Sikwin are in PlayerPrefs (Android writes them before Unity starts)
    string access = PlayerPrefs.GetString("access", "");
    if (string.IsNullOrEmpty(access))
        access = PlayerPrefs.GetString("auth_token", "");
    if (string.IsNullOrEmpty(access))
        access = PlayerPrefs.GetString("access_token", "");

    string refresh = PlayerPrefs.GetString("refresh", "");
    if (string.IsNullOrEmpty(refresh))
        refresh = PlayerPrefs.GetString("refresh_token", "");

    if (!string.IsNullOrEmpty(access))
    {
        // Set on your API client and skip login screen
        // Example: ApiClient.SetAccessToken(access);
        //          ApiClient.SetRefreshToken(refresh);
        //          ShowMainGame();  // or whatever skips login
        Debug.Log("[GameManager] Using tokens from Android, skipping login.");
    }
}
```

Use your actual API client / auth setup instead of the comments.

## 2. (Optional) Handle UnitySendMessage

If you want to also handle tokens sent later (e.g. user logs in in Sikwin and then opens the game), add this **public** method to the **same GameObject** that is named **"GameManager"** in the hierarchy:

```csharp
[System.Serializable]
private class TokenPayload { public string access; public string refresh; }

public void SetAccessAndRefreshTokens(string json)
{
    if (string.IsNullOrEmpty(json)) return;
    try
    {
        var payload = JsonUtility.FromJson<TokenPayload>(json);
        if (payload != null && !string.IsNullOrEmpty(payload.access))
        {
            // Same as above: set on ApiClient and skip login
            Debug.Log("[GameManager] SetAccessAndRefreshTokens received.");
        }
    }
    catch (System.Exception e) { Debug.LogWarning(e.Message); }
}
```

## Important

- The **GameObject** in the first scene that loads must be named exactly **"GameManager"** if you use `SetAccessAndRefreshTokens`.
- **PlayerPrefs** is the main path: Android writes tokens into the same store Unity uses (SharedPreferences) **before** Unity runs, so reading in **Start()** is enough.
- Rebuild your Unity project and export again if you change scripts; the current Sikwin APK already writes tokens correctly.
