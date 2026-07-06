using System;
using System.Collections;
using UnityEngine;
using static GameApiClient;

[Serializable]
public class TokenData
{
    public string accessToken;
    public string refreshToken;
}

[Serializable]
public class UserData
{
    public string username;
    public string password;
}

public class GameManager : MonoBehaviour
{
    public static GameManager Instance { get; private set; }

    [Header("API")]
    [SerializeField] private GameApiClient apiClient;

    public GameApiClient ApiClient => apiClient;

    public float WalletAmount { get; private set; }
    public GameSettings GameSettings { get; private set; }
    public Action<float> OnWalletUpdated;

    public int loadingTime { get; private set; }

    // callback holder for actions that must wait until Start() finishes
    private Action onInitializationCompelete;

    // flag to know whether Start() ran
    private bool started = false;

    private void Awake()
    {
        Application.targetFrameRate = 60;
        loadingTime = 8; // Default loading time, will be updated from server
        if (Instance != null)
        {
            Destroy(gameObject);
            return;
        }

        Instance = this;
        DontDestroyOnLoad(gameObject);
    }

    private void Start()
    {
        ApiClient.OnLoginSuccess += FetchInitialData;
        ApiClient.GetLoadingTime((ok, time, err) =>
        {
            if (ok)
                loadingTime = time.loading_time;
        });
        ApiClient.GetSoundSettings((ok, settings, err) =>
        {
            if (ok && settings != null)
                AudioManager.Instance.SetBackgroundMusicVolume(settings.BackgroundMusicVolume);
        });
        UIManager.Instance.AutoLoginIfPossible();

        // mark started and run any deferred action once
        started = true;
        try
        {
            onInitializationCompelete?.Invoke();
        }
        catch (Exception ex)
        {
            Debug.LogWarning("onInitializationCompelete invoke error: " + ex.Message);
        }
        finally
        {
            onInitializationCompelete = null;
        }
    }

    private void OnDestroy()
    {
        // NOTE: avoid deleting PlayerPrefs here - that wipes tokens/credentials unexpectedly.
        // Keep existing behavior minimal.
    }

    private void OnApplicationQuit()
    {
#if !UNITY_EDITOR
        PlayerPrefs.DeleteAll();
#endif
    }

    private void FetchInitialData()
    {
        apiClient.GetGameSettings((ok, settings, err) =>
        {
            if (ok && settings != null)
                GameSettings = settings;
        });

        RefreshWallet();
    }

    public void RefreshWallet()
    {
        apiClient.GetWallet((ok, wallet, err) =>
        {
            if (ok && wallet != null)
            {
                WalletAmount = float.Parse(wallet.balance);
                OnWalletUpdated?.Invoke(WalletAmount);
            }
        });
    }

    // Called from Kotlin with {"accessToken":"...", "refreshToken":"..."}
    // Behaves like native login: sets tokens on GameApiClient, starts websocket and fetches initial data.
    public void SetAccessAndRefreshToken(string json)
    {
        if (string.IsNullOrEmpty(json))
        {
            Debug.LogWarning("SetAccessAndRefreshToken called with empty json");
            return;
        }

        TokenData data;
        try
        {
            data = JsonUtility.FromJson<TokenData>(json);
        }
        catch (Exception ex)
        {
            Debug.LogWarning("SetAccessAndRefreshToken: invalid json: " + ex.Message);
            return;
        }

        if (data == null)
        {
            Debug.LogWarning("SetAccessAndRefreshToken: parsed data is null");
            return;
        }

        // If Start() not yet executed, defer initialization until Start completes.
        if (!started)
        {
            onInitializationCompelete += () =>
            {
                PlayerPrefs.SetString("accessToken", data.accessToken);
                PlayerPrefs.SetString("refreshToken", data.refreshToken);
                PlayerPrefs.Save();
                UIManager.Instance.AutoLoginIfPossible();
            };
            return;
        }

    }

    // Called from Kotlin with {"username":"...","password":"..."},
    public void SetLoginCredential(string json)
    {
        if (string.IsNullOrEmpty(json))
        {
            Debug.LogWarning("SetLoginCredential called with empty json");
            return;
        }

        UserData data;
        try
        {
            data = JsonUtility.FromJson<UserData>(json);
        }
        catch (Exception ex)
        {
            Debug.LogWarning("SetLoginCredential: invalid json: " + ex.Message);
            return;
        }

        if (data == null || string.IsNullOrEmpty(data.username) || string.IsNullOrEmpty(data.password))
        {
            Debug.LogWarning("SetLoginCredential: missing username/password");
            return;
        }

        // If Start() not yet executed, defer login until Start completes.
        if (!started)
        {
            onInitializationCompelete += () =>
            {
                PlayerPrefs.SetString("username", data.username);
                PlayerPrefs.SetString("password", data.password);
                PlayerPrefs.Save();
                UIManager.Instance.AutoLoginIfPossible();
            };
            return;
        }
    }
}