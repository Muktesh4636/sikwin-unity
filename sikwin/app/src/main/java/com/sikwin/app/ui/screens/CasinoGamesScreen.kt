package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.R
import com.sikwin.app.ui.theme.TextWhite

private data class CasinoPromoCard(
    val title: String,
    val route: String,
    val imageRes: Int
)

@Composable
fun CasinoGamesScreen(
    onBack: () -> Unit,
    onSelectGame: (String) -> Unit
) {
    val games = listOf(
        CasinoPromoCard(
            title = "Gundu Ata",
            route = "gundu_ata_live",
            imageRes = R.drawable.card_gundu_ata
        ),
        CasinoPromoCard(
            title = "Stock Market",
            route = "trading",
            imageRes = R.drawable.card_stock_market
        ),
        CasinoPromoCard(
            title = "Auto Roulette",
            route = "roulette",
            imageRes = R.drawable.card_auto_roulette
        ),
        CasinoPromoCard(
            title = "Chicken Road",
            route = "chicken_road",
            imageRes = R.drawable.card_chicken_road
        ),
        CasinoPromoCard(
            title = "Chicken Road 2",
            route = "chicken_road_2",
            imageRes = R.drawable.card_chicken_road_2
        ),
        CasinoPromoCard(
            title = "Chit Pat",
            route = "coin",
            imageRes = R.drawable.card_chit_pat
        ),
        CasinoPromoCard(
            title = "Rangu",
            route = "colour_game",
            imageRes = R.drawable.card_rangu
        )
    )

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DualGoldMid
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Casino Games",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Select a game to play",
                        color = Color(0xFF8E8E8E),
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(games) { game ->
                CasinoPromoGameCard(
                    title = game.title,
                    imageRes = game.imageRes,
                    onClick = { onSelectGame(game.route) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CasinoPromoGameCard(
    title: String,
    imageRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DualGoldDeep.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
