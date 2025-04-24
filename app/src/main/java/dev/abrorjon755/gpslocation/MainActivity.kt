package dev.abrorjon755.gpslocation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import dev.abrorjon755.gpslocation.service.GpsLocationService
import dev.abrorjon755.gpslocation.ui.screen.SendMessageScreen
import dev.abrorjon755.gpslocation.ui.theme.GpsLocationTheme
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val locationPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
    )

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Log.d("MAIN", "Permissions granted")
                startGpsService()
            } else {
                Log.d("MAIN", "Permissions denied")
                // Request permissions again if denied
                requestLocationPermissions()
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GpsLocationTheme {
                SendMessageScreen()
            }
        }
        
        // Start service immediately
        startGpsService()
        
        // Request permissions and disable battery optimization
        requestLocationPermissions()
        askUserToDisableBatteryOptimization(this)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestLocationPermissions() {
        permissionLauncher.launch(locationPermissions)
    }

    @SuppressLint("BatteryLife")
    fun askUserToDisableBatteryOptimization(context: Context): Boolean {
        val powerManager =
            context.getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            // Open battery optimization settings
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:${context.packageName}".toUri()
            context.startActivity(intent)
            return true
        } else {
            return false
        }
    }

    private fun startGpsService() {
        val serviceIntent = Intent(this, GpsLocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d("MAIN", "GpsLocationService started from MainActivity")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop the service when activity is destroyed
        Log.d("MAIN", "MainActivity destroyed, service continues running")
    }
}
