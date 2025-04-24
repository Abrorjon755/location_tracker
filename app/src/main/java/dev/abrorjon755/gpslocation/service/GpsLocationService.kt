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
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import dev.abrorjon755.gpslocation.R
import kotlinx.coroutines.*
import okhttp3.*
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class GpsLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var webSocket: WebSocket
    private var webSocketClient: OkHttpClient? = null
    private var isWebSocketConnected = false
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE", "GpsLocationService onCreate")
        isServiceRunning = true
        startForeground(1, createNotification())

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            setupWebSocket()
        } catch (e: Exception) {
            Log.e("SERVICE", "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICE", "GpsLocationService onDestroy")
        isServiceRunning = false
        serviceScope.cancel()
        webSocket.cancel()
        webSocketClient?.dispatcher?.executorService?.shutdown()
        
        // Restart the service if it was destroyed
        try {
            val intent = Intent(this, GpsLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("SERVICE", "Attempting to restart service")
        } catch (e: Exception) {
            Log.e("SERVICE", "Error restarting service: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVICE", "GpsLocationService onStartCommand")
        // Restart the service if it gets killed
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun setupWebSocket() {
        try {
            Log.d("SERVICE", "Setting up WebSocket")
            webSocketClient = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url("ws://35.184.28.154:8080/ws").build()

            webSocket = webSocketClient!!.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("WebSocket", "Connected")
                    isWebSocketConnected = true
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WebSocket", "Received: $text")
                    if (text.contains("location_request")) {
                        serviceScope.launch {
                            getCurrentLocationAndSend()
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebSocket", "Failure: ${t.message}")
                    isWebSocketConnected = false
                    // Attempt to reconnect after a delay
                    serviceScope.launch {
                        delay(5000) // Wait 5 seconds before reconnecting
                        if (isServiceRunning) {
                            Log.d("WebSocket", "Attempting to reconnect...")
                            setupWebSocket()
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "Closed: $reason")
                    isWebSocketConnected = false
                    // Attempt to reconnect after a delay
                    serviceScope.launch {
                        delay(5000)
                        if (isServiceRunning) {
                            Log.d("WebSocket", "Attempting to reconnect...")
                            setupWebSocket()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("SERVICE", "Error setting up WebSocket: ${e.message}")
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentLocationAndSend() {
        try {
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
                    val message = """
                        {
                          "type": "location_response",
                          "device": "${Build.MODEL}",
                          "latitude": ${location.latitude},
                          "longitude": ${location.longitude},
                          "time": "${LocalTime.now()}"
                        }
                    """.trimIndent()

                    Log.d("SERVICE", "Sending location via WebSocket: $message")
                    webSocket.send(message)
                } else {
                    Log.e("SERVICE", "Failed to get location")
                }
            }.addOnFailureListener { e ->
                Log.e("SERVICE", "Error fetching location: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("SERVICE", "Error in getCurrentLocationAndSend: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotification(): Notification {
        try {
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
                .setContentText("Waiting for WebSocket location requests...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Make notification non-dismissible
                .build()
        } catch (e: Exception) {
            Log.e("SERVICE", "Error creating notification: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
