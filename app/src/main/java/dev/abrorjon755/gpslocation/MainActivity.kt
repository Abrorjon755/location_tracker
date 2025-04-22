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

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val locationPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Log.d("MAIN", "Permissions granted")
                startGpsService()
            } else {
                Log.d("MAIN", "Permissions denied")
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
        askUserToDisableBatteryOptimization(this)
        requestLocationPermissions()

    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestLocationPermissions() {
        permissionLauncher.launch(locationPermissions)
    }

    @SuppressLint("BatteryLife")
    fun askUserToDisableBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                // Open battery optimization settings
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                return true
            } else {
                return false
            }
        } else {
            return false
        }
    }

    private fun startGpsService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, GpsLocationService::class.java))
        } else {
            startService(Intent(this, GpsLocationService::class.java))
        }
        Log.d("MAIN", "GpsLocationService started from MainActivity")
    }
}
