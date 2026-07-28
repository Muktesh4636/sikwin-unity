package com.sikwin.app.utils

import java.text.NumberFormat
import java.util.Locale

/**
 * Indian grouping for money display: 1,00,000.00 (not 100,000.00).
 */
object MoneyFormat {
    private val indian: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun format(value: String?): String {
        val n = value
            ?.replace(",", "")
            ?.replace("₹", "")
            ?.trim()
            ?.toDoubleOrNull()
            ?: 0.0
        return indian.format(n)
    }

    fun format(value: Double): String = indian.format(value)

    fun formatRupee(value: String?): String = "₹${format(value)}"

    fun formatRupee(value: Double): String = "₹${format(value)}"
}
