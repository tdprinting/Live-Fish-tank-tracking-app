package com.reeflive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.Abyss
import com.reeflive.app.ui.theme.ReefGradients
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.core.ReefLiveCore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReefLiveTheme {
                HelloReefScreen()
            }
        }
    }
}

/** M0 placeholder screen: Abyss gradient background with a glass card. */
@Composable
fun HelloReefScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReefGradients.bgAbyss)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Abyss.glass, RoundedCornerShape(20.dp))
                .border(1.dp, Abyss.glassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ReefLiveCore.greeting(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = Abyss.foam,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Abyss.coral, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${ReefLiveCore.NAME} is coming alive",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Abyss.mist,
                )
            }
            Text(
                text = "Milestone 0",
                style = MaterialTheme.typography.labelSmall,
                color = Abyss.kelp,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HelloReefPreview() {
    ReefLiveTheme { HelloReefScreen() }
}
