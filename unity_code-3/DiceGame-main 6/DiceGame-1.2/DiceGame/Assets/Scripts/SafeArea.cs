using UnityEngine;

[ExecuteAlways]
public class SafeArea : MonoBehaviour
{
    private RectTransform panel;
    private Rect safeArea;
    private Canvas canvas;

    private void Awake()
    {
        panel = GetComponent<RectTransform>();
        canvas = GetComponentInParent<Canvas>();

        ApplySafeArea();
    }

    private void OnEnable()
    {
        ApplySafeArea();
    }

#if UNITY_EDITOR
    private void Update()
    {
        // Re-apply in editor when resolution changes
        ApplySafeArea();
    }
#endif

    void ApplySafeArea()
    {
        if (panel == null || canvas == null)
            return;

        Rect currentSafeArea = GetEffectiveSafeArea();

        if (currentSafeArea != safeArea)
        {
            safeArea = currentSafeArea;
            Apply();
        }
    }

    /// <summary>
    /// WebGL in the browser does not report safe-area insets like iOS; bad rects collapse the gameplay UI
    /// (e.g. bottom betting bar missing). Mobile native builds still use Screen.safeArea.
    /// </summary>
    private static Rect GetEffectiveSafeArea()
    {
#if UNITY_WEBGL
        return new Rect(0, 0, Screen.width, Screen.height);
#else
        Rect sa = Screen.safeArea;
        if (sa.width < 2f || sa.height < 2f)
            return new Rect(0, 0, Screen.width, Screen.height);
        return sa;
#endif
    }

    void Apply()
    {
#if UNITY_WEBGL
        // Browser builds: never shrink the gameplay root from Screen.safeArea math — some runtimes
        // report odd sizes and the whole HUD ends up in the top ~40% with green camera clear below.
        if (panel != null)
        {
            panel.anchorMin = Vector2.zero;
            panel.anchorMax = Vector2.one;
            panel.offsetMin = Vector2.zero;
            panel.offsetMax = Vector2.zero;
        }
        return;
#endif
        // Convert safe area rectangle from screen space → anchor space
        Vector2 anchorMin = safeArea.position;
        Vector2 anchorMax = safeArea.position + safeArea.size;

        float w = Mathf.Max(1, Screen.width);
        float h = Mathf.Max(1, Screen.height);
        anchorMin.x /= w;
        anchorMin.y /= h;
        anchorMax.x /= w;
        anchorMax.y /= h;

        panel.anchorMin = anchorMin;
        panel.anchorMax = anchorMax;
    }
}
