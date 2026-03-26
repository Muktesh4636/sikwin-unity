package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextWhite

@Composable
fun MaintenanceScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Maintenance",
                color = PrimaryYellow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = message,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
            ) {
                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

