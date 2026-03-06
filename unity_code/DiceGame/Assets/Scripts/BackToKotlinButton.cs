using UnityEngine;
using UnityEngine.UI;

/// <summary>
/// Attach this to any back Button (e.g. top-left in game). On click, returns to Kotlin app home.
/// Alternatively assign the back button to GameplayUIManager.backButton.
/// </summary>
[RequireComponent(typeof(Button))]
public class BackToKotlinButton : MonoBehaviour
{
    private void Awake()
    {
        if (GetComponent<Button>() is Button btn)
            btn.onClick.AddListener(BackToKotlin.GoBackToKotlin);
    }
}
