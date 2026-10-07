package dev.cniekirk.wikidroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val greeting = (application as WikiDroidApp).graph.greeter.greeting
        setContent {
            MaterialTheme {
                HelloScreen(greeting = greeting)
            }
        }
    }
}

@Composable
internal fun HelloScreen(
    greeting: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = greeting, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview
@Composable
private fun HelloScreenPreview() {
    MaterialTheme {
        HelloScreen(greeting = "Hello WikiDroid")
    }
}
