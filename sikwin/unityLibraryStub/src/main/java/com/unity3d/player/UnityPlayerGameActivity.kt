package com.unity3d.player

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Lightweight stand-in for the real Unity host while Unity is temporarily removed from the APK.
 * Finishes immediately so Gundu Ata (Unity) does not crash the app.
 */
class UnityPlayerGameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "Gundu Ata Unity is temporarily unavailable. Use Live or other games.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}
