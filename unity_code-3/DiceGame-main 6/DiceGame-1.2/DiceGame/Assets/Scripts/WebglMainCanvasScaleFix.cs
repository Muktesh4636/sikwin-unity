using System.Collections;
using UnityEngine;

/// <summary>
/// Some WebGL builds had the root gameplay canvas at localScale (0,0,0): 3D table visible, green clear below, HUD missing.
/// Retries for several frames so late initializers cannot undo the fix immediately.
/// </summary>
public sealed class WebglMainCanvasScaleFix : MonoBehaviour
{
    private const int RetryFrames = 24;

    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
    private static void Bootstrap()
    {
#if UNITY_WEBGL && !UNITY_EDITOR
        var go = new GameObject("__WebglMainCanvasScaleFix");
        DontDestroyOnLoad(go);
        go.hideFlags = HideFlags.HideAndDontSave;
        go.AddComponent<WebglMainCanvasScaleFix>();
#endif
    }

    private void Start()
    {
#if UNITY_WEBGL && !UNITY_EDITOR
        Apply();
        StartCoroutine(FixLoop());
#endif
    }

    private IEnumerator FixLoop()
    {
        for (var i = 0; i < RetryFrames; i++)
        {
            yield return null;
            Apply();
        }

        yield return new WaitForEndOfFrame();
        Apply();
        Destroy(gameObject);
    }

    private static void Apply()
    {
        TryFix("MainCanvas ");
        TryFix("MainCanvas");
        TryFix("GameplayUI");
        StretchAllSafeAreasUnderMainCanvas();

        foreach (var canvas in Object.FindObjectsByType<Canvas>(FindObjectsInactive.Include, FindObjectsSortMode.None))
        {
            if (canvas == null) continue;
            var scene = canvas.gameObject.scene;
            if (!scene.IsValid() || !scene.isLoaded) continue;
            var rt = canvas.transform as RectTransform;
            if (rt != null && rt.localScale.sqrMagnitude < 1e-6f)
                rt.localScale = Vector3.one;
        }
    }

    private static void StretchAllSafeAreasUnderMainCanvas()
    {
        var main = GameObject.Find("MainCanvas ");
        if (main == null) main = GameObject.Find("MainCanvas");
        if (main == null) return;
        foreach (var sa in main.GetComponentsInChildren<SafeArea>(true))
        {
            var rt = sa.GetComponent<RectTransform>();
            if (rt == null) continue;
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
        }
    }

    private static void TryFix(string objectName)
    {
        var go = GameObject.Find(objectName);
        if (go == null) return;
        var rt = go.GetComponent<RectTransform>();
        if (rt == null) return;
        if (rt.localScale.sqrMagnitude < 1e-6f)
            rt.localScale = Vector3.one;
    }
}
