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

    /**
     * Android-Navigationsleiste ausblenden: Auf den Boards liegt schon der Wisch-Streifen des Herstellers über allem,
     * sonst stehen zwei Striche untereinander. Hochwischen am Rand holt die Leiste kurz zurück.
     */
    private fun leisteAus() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) leisteAus()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        testStartordnerLesen(intent)
    }

    private fun testStartordnerLesen(intent: android.content.Intent?) {
        if (BuildConfig.DEBUG) intent?.getStringExtra("test_startordner")?.let { TestStartordner.uri = android.net.Uri.parse(it) }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("test_ablage", false) == true) TestStartordner.ablage.value = true
    }
}
