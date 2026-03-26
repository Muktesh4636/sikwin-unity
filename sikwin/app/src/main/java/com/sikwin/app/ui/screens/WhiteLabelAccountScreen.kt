package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteLabelAccountScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var validationErrorResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.errorMessage = null
    }

    fun validate(): Boolean {
        return when {
            name.isBlank() -> {
                validationErrorResId = R.string.white_label_validation_name
                false
            }
            phone.isBlank() -> {
                validationErrorResId = R.string.white_label_validation_phone
                false
            }
            else -> {
                validationErrorResId = null
                true
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Text(
                    stringResource(R.string.white_label_success_title),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    stringResource(R.string.white_label_success_message),
                    color = TextGrey,
                    fontSize = 14.sp
                )
            },
            containerColor = SurfaceColor,
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.ok), color = PrimaryYellow)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryYellow,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                stringResource(R.string.white_label_account_title),
                color = PrimaryYellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Subtitle with 50% discount
        Text(
            stringResource(R.string.white_label_account_subtitle),
            color = TextGrey,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WhiteLabelField(
                label = stringResource(R.string.white_label_name),
                value = name,
                onValueChange = { name = it; validationErrorResId = null },
                placeholder = stringResource(R.string.white_label_name_placeholder),
                singleLine = true
            )
            WhiteLabelField(
                label = stringResource(R.string.white_label_phone),
                value = phone,
                onValueChange = { phone = it; validationErrorResId = null },
                placeholder = stringResource(R.string.white_label_phone_placeholder),
                keyboardType = KeyboardType.Phone,
                singleLine = true
            )
            WhiteLabelField(
                label = stringResource(R.string.white_label_message),
                value = message,
                onValueChange = { message = it },
                placeholder = stringResource(R.string.white_label_message_placeholder),
                singleLine = false
            )

            if (validationErrorResId != null) {
                Text(
                    text = stringResource(validationErrorResId!!),
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            viewModel.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (validate()) {
                        viewModel.submitWhitelabelLead(
                            name = name,
                            phone = phone,
                            message = message,
                            onSuccess = { showSuccessDialog = true }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                shape = RoundedCornerShape(8.dp),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        color = BlackBackground,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        stringResource(R.string.white_label_submit),
                        color = BlackBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhiteLabelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = PrimaryYellow,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextGrey, fontSize = 14.sp) },
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = SurfaceColor,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = PrimaryYellow,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
