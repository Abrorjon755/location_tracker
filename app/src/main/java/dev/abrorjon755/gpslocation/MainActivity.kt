package dev.abrorjon755.gpslocation

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import dev.abrorjon755.gpslocation.ui.screen.SendMessageScreen
import dev.abrorjon755.gpslocation.ui.theme.GpsLocationTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GpsLocationTheme {
                SendMessageScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop the service when activity is destroyed
        Log.d("MAIN", "MainActivity destroyed, service continues running")
    }
}
