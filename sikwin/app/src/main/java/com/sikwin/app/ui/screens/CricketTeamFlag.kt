package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sikwin.app.ui.theme.CricketAccentGold

/**
 * Resolves cricket team / country names to a flag image URL.
 * International sides map by team name; club sides fall back to competition country.
 */
fun resolveCricketFlagUrl(teamName: String?, countryHint: String? = null): String? {
    val fromTeam = flagCodeForLabel(teamName)
    if (fromTeam != null) return flagUrlForCode(fromTeam)
    val fromHint = flagCodeForLabel(countryHint)
    if (fromHint != null) return flagUrlForCode(fromHint)
    return null
}

private fun flagUrlForCode(code: String): String = when (code) {
    // Windies board flag (not an ISO country on flagcdn)
    "wi" ->
        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/" +
            "West_Indies_Cricket_Board_Flag.svg/160px-West_Indies_Cricket_Board_Flag.svg.png"
    else -> "https://flagcdn.com/w80/$code.png"
}

private fun flagCodeForLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val key = normalizeCricketLabel(raw)

    // Direct / alias lookup
    TEAM_FLAG_CODES[key]?.let { return it }

    // Prefix match for names like "india u19", "england women"
    TEAM_FLAG_CODES.entries
        .sortedByDescending { it.key.length }
        .firstOrNull { key == it.key || key.startsWith("${it.key} ") || key.startsWith("${it.key}-") }
        ?.let { return it.value }

    // Contained country word (e.g. "t20 pondicherry premier league" → india via pondicherry)
    REGION_HINT_CODES.entries
        .sortedByDescending { it.key.length }
        .firstOrNull { key.contains(it.key) }
        ?.let { return it.value }

    return null
}

private fun normalizeCricketLabel(raw: String): String =
    raw.trim()
        .lowercase()
        .replace('&', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .replace(
            Regex(
                """\b(u19|u\-?19|women|woman|men|mens|xi|cricket board|board xi|cricket)\b"""
            ),
            " "
        )
        .replace(Regex("\\s+"), " ")
        .trim()

/** Full / short names → flagcdn ISO (or `wi` special). */
private val TEAM_FLAG_CODES: Map<String, String> = mapOf(
    "india" to "in",
    "ind" to "in",
    "england" to "gb-eng",
    "eng" to "gb-eng",
    "australia" to "au",
    "aus" to "au",
    "pakistan" to "pk",
    "pak" to "pk",
    "south africa" to "za",
    "rsa" to "za",
    "sa" to "za",
    "new zealand" to "nz",
    "nz" to "nz",
    "west indies" to "wi",
    "windies" to "wi",
    "wi" to "wi",
    "sri lanka" to "lk",
    "sl" to "lk",
    "bangladesh" to "bd",
    "ban" to "bd",
    "afghanistan" to "af",
    "afg" to "af",
    "ireland" to "ie",
    "ire" to "ie",
    "scotland" to "gb-sct",
    "sco" to "gb-sct",
    "netherlands" to "nl",
    "holland" to "nl",
    "ned" to "nl",
    "zimbabwe" to "zw",
    "zim" to "zw",
    "namibia" to "na",
    "nepal" to "np",
    "oman" to "om",
    "uae" to "ae",
    "united arab emirates" to "ae",
    "usa" to "us",
    "united states" to "us",
    "united states of america" to "us",
    "canada" to "ca",
    "hong kong" to "hk",
    "singapore" to "sg",
    "malaysia" to "my",
    "kenya" to "ke",
    "uganda" to "ug",
    "nigeria" to "ng",
    "papua new guinea" to "pg",
    "png" to "pg",
    "fiji" to "fj",
    "samoa" to "ws",
    "wales" to "gb-wls",
    "jersey" to "je",
    "guernsey" to "gg",
    "germany" to "de",
    "italy" to "it",
    "spain" to "es",
    "portugal" to "pt",
    "france" to "fr",
    "belgium" to "be",
    "denmark" to "dk",
    "sweden" to "se",
    "norway" to "no",
    "finland" to "fi",
    "czech republic" to "cz",
    "czechia" to "cz",
    "austria" to "at",
    "switzerland" to "ch",
    "romania" to "ro",
    "hungary" to "hu",
    "croatia" to "hr",
    "serbia" to "rs",
    "greece" to "gr",
    "turkey" to "tr",
    "qatar" to "qa",
    "bahrain" to "bh",
    "kuwait" to "kw",
    "saudi arabia" to "sa",
    "maldives" to "mv",
    "bhutan" to "bt",
    "thailand" to "th",
    "indonesia" to "id",
    "japan" to "jp",
    "china" to "cn",
    "south korea" to "kr",
    "korea" to "kr",
    "philippines" to "ph",
    "tanzania" to "tz",
    "botswana" to "bw",
    "rwanda" to "rw",
    "ghana" to "gh",
    "bermuda" to "bm",
    "cayman islands" to "ky",
    "argentina" to "ar",
    "brazil" to "br",
    "chile" to "cl",
    "mexico" to "mx",
    "jamaica" to "jm",
    "barbados" to "bb",
    "trinidad and tobago" to "tt",
    "trinidad" to "tt",
    "tobago" to "tt",
    "guyana" to "gy",
    "antigua and barbuda" to "ag",
    "antigua" to "ag",
    "saint lucia" to "lc",
    "st lucia" to "lc",
    "dominica" to "dm",
    "grenada" to "gd",
    "saint vincent" to "vc",
    "st vincent" to "vc",
    "montserrat" to "ms",
    "international" to "un"
)

/** Competition / venue hints for domestic club sides. */
private val REGION_HINT_CODES: Map<String, String> = mapOf(
    "pondicherry" to "in",
    "puducherry" to "in",
    "ipl" to "in",
    "ranji" to "in",
    "syed mushtaq" to "in",
    "vijay hazare" to "in",
    "tnpl" to "in",
    "kerala" to "in",
    "mumbai" to "in",
    "delhi" to "in",
    "chennai" to "in",
    "kolkata" to "in",
    "bangalore" to "in",
    "bengaluru" to "in",
    "hyderabad" to "in",
    "bbl" to "au",
    "big bash" to "au",
    "sheffield shield" to "au",
    "psl" to "pk",
    "pakistan super" to "pk",
    "bpl" to "bd",
    "bangladesh premier" to "bd",
    "lpl" to "lk",
    "lanka premier" to "lk",
    "cpl" to "wi",
    "caribbean premier" to "wi",
    "the hundred" to "gb-eng",
    "county championship" to "gb-eng",
    "vitality blast" to "gb-eng",
    "sa20" to "za",
    "csa" to "za",
    "super smash" to "nz",
    "finland" to "fi",
    "finnish" to "fi",
    "kuwait" to "kw",
    "portugal" to "pt",
    "scotland" to "gb-sct",
    "sri lanka" to "lk",
    "india" to "in",
    "england" to "gb-eng",
    "australia" to "au",
    "pakistan" to "pk",
    "west indies" to "wi",
    "south africa" to "za",
    "new zealand" to "nz",
    "bangladesh" to "bd",
    "afghanistan" to "af",
    "ireland" to "ie",
    "netherlands" to "nl",
    "zimbabwe" to "zw",
    "uae" to "ae",
    "united arab" to "ae",
    "usa" to "us",
    "canada" to "ca",
    "nepal" to "np",
    "oman" to "om",
    "namibia" to "na",
    "hong kong" to "hk"
)

@Composable
fun CricketTeamFlag(
    teamName: String?,
    countryHint: String? = null,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val url = remember(teamName, countryHint) { resolveCricketFlagUrl(teamName, countryHint) }
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .border(0.5.dp, Color(0xFF3A3A3A), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = teamName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.SportsCricket,
                contentDescription = null,
                tint = CricketAccentGold.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
