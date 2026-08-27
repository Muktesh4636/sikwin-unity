package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

@Composable
fun PersonalInfoScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit
) {
    val user = viewModel.userProfile
    var showEditDialog by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    val colors = rememberAppScreenColors()

    if (showEditDialog != null) {
        val field = showEditDialog!!
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text(stringResource(R.string.edit_field, field), color = colors.text) },
            containerColor = colors.surface,
            text = {
                if (field == "Gender") {
                    Column {
                        listOf("Male", "Female", "Other").forEach { gender ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateProfile(mapOf("gender" to gender.uppercase()))
                                        showEditDialog = null
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = user?.gender == gender.uppercase(),
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryYellow)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(gender, color = colors.text)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text(stringResource(R.string.new_field, field), color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            focusedBorderColor = PrimaryYellow,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        )
                    )
                }
            },
            confirmButton = {
                if (field != "Gender") {
                    Button(
                        onClick = {
                            val key = when (field) {
                                "Name" -> "username"
                                "Email" -> "email"
                                "Telegram" -> "telegram"
                                "Date of Birth" -> "date_of_birth"
                                else -> field.lowercase().replace(" ", "_")
                            }
                            viewModel.updateProfile(mapOf(key to editValue))
                            showEditDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                    ) {
                        Text(stringResource(R.string.save), color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) {
                    Text(stringResource(R.string.cancel), color = PrimaryYellow)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AppSubScreenHeader(title = "My Information", colors = colors, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val items = listOf(
                InfoRowData("Name", user?.username ?: "", editable = false),
                InfoRowData("Gender", user?.gender ?: "", showArrow = true, editable = true),
                InfoRowData("Email", user?.email ?: "", showArrow = true, editable = true),
                InfoRowData("Telegram", user?.telegram ?: "", showArrow = true, editable = true),
                InfoRowData("Date of Birth", user?.date_of_birth ?: "", showArrow = true, editable = true)
            )

            items.forEach { item ->
                InfoRow(item) {
                    if (item.editable) {
                        editValue = item.value
                        showEditDialog = item.label
                    }
                }
                HorizontalDivider(
                    color = colors.border,
                    thickness = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun InfoRow(data: InfoRowData, onClick: () -> Unit) {
    val colors = rememberAppScreenColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(enabled = data.editable) { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.label,
            color = colors.textMuted,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = data.value,
            color = if (data.value.isEmpty()) colors.textMuted else colors.text,
            fontSize = 15.sp,
            textAlign = TextAlign.End
        )

        if (data.showArrow) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        } else if (!data.editable) {
             Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

data class InfoRowData(
    val label: String,
    val value: String,
    val showArrow: Boolean = false,
    val editable: Boolean = false
)

fun maskPhoneNumber(phone: String): String {
    if (phone.length < 7) return phone
    val prefix = phone.take(3)
    val suffix = phone.takeLast(3)
    return "$prefix****$suffix"
}
