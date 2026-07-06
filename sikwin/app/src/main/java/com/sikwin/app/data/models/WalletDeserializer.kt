package com.sikwin.app.data.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Backend may send wallet amounts as JSON numbers or use alternate keys.
 * Default Gson + non-null String fields can fail or yield wrong defaults, so balances show 0.
 */
class WalletDeserializer : JsonDeserializer<Wallet> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Wallet {
        val o = json.asJsonObject

        fun asMoneyString(el: JsonElement?): String? {
            if (el == null || el.isJsonNull) return null
            if (!el.isJsonPrimitive) return null
            val p = el.asJsonPrimitive
            val s = when {
                p.isString -> p.asString.trim()
                p.isNumber -> p.asBigDecimal.stripTrailingZeros().toPlainString()
                else -> null
            }
            return s?.takeIf { it.isNotEmpty() }
        }

        fun pick(vararg keys: String, default: String = "0.00"): String {
            for (k in keys) {
                asMoneyString(o.get(k))?.let { return it }
            }
            return default
        }

        val id: Int? = run {
            val e = o.get("id") ?: return@run null
            if (e.isJsonNull || !e.isJsonPrimitive) return@run null
            val p = e.asJsonPrimitive
            try {
                when {
                    p.isNumber -> p.asBigDecimal.stripTrailingZeros().toInt()
                    p.isString -> p.asString.trim().toIntOrNull()
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        val balance = pick(
            "balance",
            "available_balance",
            "total_balance",
            "wallet_balance",
            "main_balance"
        )
        val unav = pick(
            "unavailable_balance",
            "unavaliable_balance",
            "locked_balance",
            "non_withdrawable_balance",
            "pending_balance"
        )
        val withdrawable = pick("withdrawable_balance", "withdrawable", "available_to_withdraw")

        val user: User? = if (o.has("user") && !o.get("user").isJsonNull) {
            try {
                context.deserialize(o.get("user"), User::class.java)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val unavailableExplicit = asMoneyString(o.get("unavailable_balance"))
            ?: asMoneyString(o.get("unavaliable_balance"))

        return Wallet(
            id = id,
            balance = balance,
            unavaliable_balance = unav,
            unavailable_balance = unavailableExplicit,
            withdrawable_balance = withdrawable,
            user = user
        )
    }
}
