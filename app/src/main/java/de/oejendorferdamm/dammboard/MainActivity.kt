package de.oejendorferdamm.dammboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.oejendorferdamm.dammboard.ui.AppWurzel
import de.oejendorferdamm.dammboard.ui.TestStartordner
import de.oejendorferdamm.dammboard.ui.canvas.Karten

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Karten.init(this)
        testStartordnerLesen(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppWurzel(onAppSchliessen = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        testStartordnerLesen(intent)
    }

    private fun testStartordnerLesen(intent: android.content.Intent?) {
        if (BuildConfig.DEBUG) intent?.getStringExtra("test_startordner")?.let { TestStartordner.uri = android.net.Uri.parse(it) }
    }
}
