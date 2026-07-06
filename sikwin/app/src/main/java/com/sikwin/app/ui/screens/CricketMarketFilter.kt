package com.sikwin.app.ui.screens

import com.sikwin.app.data.models.CricketLiveMarket
import java.util.Locale

/**
 * Maps each market to a filter tab (1=Main, 2=Over by over, 3=Special, 4=Players) using title heuristics.
 * API does not expose categories; this matches common sportsbook wording.
 */
object CricketMarketFilter {

    private val overLineRegex = Regex("""over\s+\d+[\d.]?""", RegexOption.IGNORE_CASE)

    fun categoryIndex(market: CricketLiveMarket): Int {
        val d = market.description?.lowercase(Locale.US)?.trim().orEmpty()
        if (d.isEmpty()) return 1
        if (isPlayers(d)) return 4
        if (isOverByOver(d)) return 2
        if (isSpecial(d)) return 3
        return 1
    }

    fun filter(markets: List<CricketLiveMarket>, chipIndex: Int): List<CricketLiveMarket> {
        if (chipIndex == 0) return markets
        return markets.filter { categoryIndex(it) == chipIndex }
    }

    fun countFor(markets: List<CricketLiveMarket>, chipIndex: Int): Int =
        when (chipIndex) {
            0 -> markets.size
            else -> markets.count { categoryIndex(it) == chipIndex }
        }

    private fun isPlayers(d: String): Boolean {
        if (d.contains("batter") || d.contains("batsman") || d.contains("bowler")) return true
        if (d.contains("player") && !d.contains("team")) return true
        if (d.contains("top ") && (d.contains("run") || d.contains("wicket") || d.contains("score"))) return true
        return false
    }

    private fun isOverByOver(d: String): Boolean {
        if (d.contains("over by over") || d.contains("over-by-over")) return true
        if (d.contains("over/under") || d.contains("o/u")) return true
        if (d.contains("over ") && d.contains("under")) return true
        if (overLineRegex.containsMatchIn(d)) return true
        return false
    }

    private fun isSpecial(d: String): Boolean {
        if (d.contains("six") || d.contains("four") || d.contains("boundary")) return true
        if (d.contains("coin") || d.contains("odd") || d.contains("even")) return true
        if (d.contains("method of") || d.contains("special")) return true
        return false
    }
}
