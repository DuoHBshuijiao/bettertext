package me.rerere.bettertext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.rerere.bettertext.gallery.GalleryApp
import me.rerere.bettertext.ui.theme.BettertextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BettertextTheme {
                GalleryApp()
            }
        }
    }
}
