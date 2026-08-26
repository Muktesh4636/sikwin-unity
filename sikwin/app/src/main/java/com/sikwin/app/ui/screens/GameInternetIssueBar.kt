package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite

/**
 * Bottom banner shown when Roulette / Stock Market (or similar) fails to load.
 */
@Composable
fun GameInternetIssueBar(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "No internet connection. Please check your network and try again."
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF2111111))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = TextGrey,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

/** Full-screen offline state for Sports / Casino / Gundu web (hides Chrome error page). */
@Composable
fun NoInternetConnectionOverlay(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF0A0A0A)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = PrimaryYellow,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "No internet connection",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Please check your network and try again.",
                color = TextGrey,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
