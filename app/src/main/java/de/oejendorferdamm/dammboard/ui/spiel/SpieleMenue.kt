package de.oejendorferdamm.dammboard.ui.spiel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Die Spiele hinter dem Würfel (vorher teils in den Extras versteckt). */
enum class Spiel(val titel: String, val zeichen: String) {
    FUSSBALL("Tafelfußball", "⚽"),
    WOERTER("Wörter raten", "🌼"),
    NACHSPUREN("Nachspuren", "✏️"),
    WUERFEL("Würfel", "🎲"),
}

/** Auswahl über dem Würfel unten links; Tipp daneben schließt sie. */
@Composable
fun SpieleMenue(onWahl: (Spiel) -> Unit, onSchliessen: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSchliessen)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 150.dp, bottom = 96.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF7F7F5))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(18.dp)
        ) {
            Text("Spiele", color = Color(0xFF2B2B2B), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spiel.entries.forEach { s ->
                    Column(
                        Modifier
                            .size(124.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .clickable(role = Role.Button) { onWahl(s) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(s.zeichen, fontSize = 44.sp)
                        Text(s.titel, color = Color(0xFF2B2B2B), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
