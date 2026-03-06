package com.sikwin.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.R
import androidx.compose.ui.res.stringResource
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember { context.applicationContext.getSharedPreferences("gunduata_prefs", Context.MODE_PRIVATE) }

    // Fetched from https://gunduata.club/api/support/contacts/
    var whatsappNumber by remember { mutableStateOf(prefs.getString("support_whatsapp_number", null)) }
    var telegramHandle by remember { mutableStateOf(prefs.getString("support_telegram", null)) }
    var isLoading by remember { mutableStateOf(whatsappNumber == null && telegramHandle == null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // If we already have cached contacts, render instantly and refresh quietly.
        isLoading = (whatsappNumber == null && telegramHandle == null)
        loadError = null
        val result = withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getSupportContacts()
                if (response.isSuccessful) {
                    val contacts = response.body()
                    Triple(contacts?.whatsapp_number, contacts?.telegram, null as String?)
                } else {
                    Triple(null, null, "Failed to load contacts")
                }
            } catch (e: Exception) {
                Triple(null, null, e.message ?: "Failed to load contacts")
            }
        }

        // IMPORTANT: update compose state on Main thread (we are back on Main here).
        whatsappNumber = result.first
        telegramHandle = result.second
        loadError = result.third
        isLoading = false

        // Cache for next time so Help Center opens instantly.
        try {
            prefs.edit()
                .putString("support_whatsapp_number", whatsappNumber)
                .putString("support_telegram", telegramHandle)
                .apply()
        } catch (_: Exception) {}
    }

    // For wa.me we need digits only (no + or spaces)
    fun waNumberForLink(): String? = whatsappNumber?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    fun displayWhatsApp(): String = whatsappNumber?.let { if (it.startsWith('+')) it else "+$it" } ?: "—"

    fun openWhatsApp() {
        val number = waNumberForLink() ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$number")
            intent.setPackage("com.whatsapp")
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$number")
            context.startActivity(intent)
        }
    }

    // Telegram API may return username (e.g. "support") or phone (e.g. "9182351395")
    fun telegramLink(): String? = telegramHandle?.takeIf { it.isNotBlank() }?.let { handle ->
        if (handle.all { it.isDigit() }) "https://t.me/+$handle" else "https://t.me/$handle"
    }
    fun openTelegram() {
        val link = telegramLink() ?: return
        try {
            val uri = telegramHandle?.let { h ->
                if (h.all { it.isDigit() }) "https://t.me/+$h" else "tg://resolve?domain=$h"
            } ?: link
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(link)
            context.startActivity(intent)
        }
    }
    fun displayTelegram(): String = telegramHandle?.let { if (it.all { c -> c.isDigit() }) "+$it" else "@$it" } ?: "—"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.help_center_title),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = BlackBackground
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Hero Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceColor
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Support Icon
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = PrimaryYellow.copy(alpha = 0.2f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HeadsetMic,
                                contentDescription = null,
                                tint = PrimaryYellow,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        stringResource(R.string.need_help),
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        stringResource(R.string.contact_support),
                        color = TextGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = PrimaryYellow,
                    trackColor = SurfaceColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (loadError != null) {
                Text(
                    loadError!!,
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // WhatsApp Card (from API: support/contacts/)
            ContactCard(
                title = stringResource(R.string.whatsapp_support),
                subtitle = if (waNumberForLink() != null) displayWhatsApp() else if (isLoading) "Loading…" else "—",
                description = stringResource(R.string.get_instant_help),
                iconColor = Color(0xFF25D366),
                backgroundColor = Color(0xFF25D366).copy(alpha = 0.1f),
                onClick = { if (waNumberForLink() != null) openWhatsApp() },
                iconPainter = painterResource(id = R.drawable.ic_whatsapp),
                enabled = waNumberForLink() != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Telegram Card (from API: support/contacts/)
            ContactCard(
                title = stringResource(R.string.telegram_support),
                subtitle = if (telegramLink() != null) displayTelegram() else if (isLoading) "Loading…" else "—",
                description = stringResource(R.string.chat_on_telegram),
                iconColor = Color(0xFF0088CC),
                backgroundColor = Color(0xFF0088CC).copy(alpha = 0.1f),
                onClick = { if (telegramLink() != null) openTelegram() },
                iconPainter = null,
                enabled = telegramLink() != null
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Info Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = PrimaryYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.support_hours),
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        stringResource(R.string.support_hours_desc),
                        color = TextGrey,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ContactCard(
    title: String,
    subtitle: String,
    description: String,
    iconColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.2f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconPainter != null) {
                        Image(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    subtitle,
                    color = iconColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    description,
                    color = TextGrey,
                    fontSize = 13.sp
                )
            }
            
            // Arrow Icon
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
