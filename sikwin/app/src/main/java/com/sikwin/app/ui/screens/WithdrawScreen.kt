package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.theme.AppSubScreenHeader
import com.sikwin.app.ui.theme.rememberAppScreenColors
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.MoneyFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit,
    onAddBankAccount: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.fetchBankDetails()
        viewModel.fetchWallet()
    }

    val bankAccounts = viewModel.bankDetails
    
    var amount by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf(bankAccounts.firstOrNull()) }
    var showBankDropdown by remember { mutableStateOf(false) }

    // Update selected bank when bankAccounts are loaded
    LaunchedEffect(bankAccounts) {
        if (selectedBank == null && bankAccounts.isNotEmpty()) {
            selectedBank = bankAccounts.firstOrNull { it.is_default } ?: bankAccounts.first()
        }
    }

    val colors = rememberAppScreenColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        AppSubScreenHeader(
            title = stringResource(R.string.online_withdrawal),
            colors = colors,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.bank_account),
                color = colors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(colors.accent)
            )
        }

        HorizontalDivider(color = colors.border, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // Wallet Balance Info
        viewModel.wallet?.let { wallet ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(colors.surface, RoundedCornerShape(8.dp))
                    .then(
                        if (colors.listItemBorder != null) {
                            Modifier.border(colors.listItemBorder!!, RoundedCornerShape(8.dp))
                        } else Modifier
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.available_balance), color = colors.textMuted, fontSize = 12.sp)
                        Text(MoneyFormat.formatRupee(wallet.withdrawable_balance), color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.unavailable_balance), color = colors.textMuted, fontSize = 12.sp)
                        Text(MoneyFormat.formatRupee(wallet.unavailableBalanceDisplay), color = colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))


        if (viewModel.isLoadingBankDetails) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (bankAccounts.isEmpty()) {
            // No Bank account added
            Text(
                "No Bank account added, add bank account",
                color = colors.accent,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { onAddBankAccount() }
            )
        } else {
            // Bank account selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bank Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBankDropdown = true },
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedBank?.let { "${it.bank_name}(${it.account_number.takeLast(4)})" } ?: "Select Bank Account",
                                color = colors.text,
                                fontSize = 16.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, null, tint = colors.textMuted)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showBankDropdown,
                        onDismissRequest = { showBankDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(colors.surface)
                    ) {
                        bankAccounts.forEach { bank ->
                            DropdownMenuItem(
                                text = { Text("${bank.bank_name}(${bank.account_number.takeLast(4)})", color = colors.text) },
                                onClick = {
                                    selectedBank = bank
                                    showBankDropdown = false
                                }
                            )
                        }
                        HorizontalDivider(color = colors.border)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_bank_account), color = colors.accent) },
                            onClick = {
                                showBankDropdown = false
                                onAddBankAccount()
                            }
                        )
                    }
                }

                // Amount Input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                    amount = newValue.filter { it.isDigit() }
                },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.enter_amount), color = colors.textMuted) },
                    leadingIcon = { Text("₹", color = colors.text, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        unfocusedBorderColor = colors.border,
                        focusedBorderColor = colors.accent,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    shape = RoundedCornerShape(4.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )


                Spacer(modifier = Modifier.height(8.dp))

                // Error Message
                viewModel.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }

                // Submit Button (show spinner only when submitting, not when loading bank details)
                Button(
                    onClick = { 
                        if (amount.isNotBlank() && selectedBank != null) {
                            viewModel.initiateWithdraw(amount, selectedBank!!) {
                                onBack() 
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !viewModel.isLoading && !viewModel.isLoadingBankDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(color = BlackBackground, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            "Submit",
                            color = BlackBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

