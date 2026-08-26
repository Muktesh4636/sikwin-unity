package com.sikwin.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R

/**
 * Cock fight — same coming-soon layout as Soccer / Tennis.
 */
@Composable
fun CockFightScreen(
    onBack: () -> Unit
) {
    SportComingSoonScreen(
        title = stringResource(R.string.cock_fight_title),
        emoji = "🐓",
        subtitle = stringResource(R.string.cock_fight_coming_soon),
        onBack = onBack
    )
}
