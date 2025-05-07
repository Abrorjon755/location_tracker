@file:Suppress("DEPRECATION")

package dev.abrorjon755.gpslocation.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@SuppressLint("AutoboxingStateCreation")
@Composable
fun SendMessageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<String?>(null) }
    var isRequesting by remember { mutableStateOf(false) }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isConnected by remember { mutableStateOf(false) }

    // ExoPlayer setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(audioFile) {
        audioFile?.let {
            val mediaItem = MediaItem.fromUri(it.toURI().toString())
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://35.184.28.154:8080/ws")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                message = try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "location_response" -> {
                            latitude = json.optDouble("latitude", 0.0)
                            longitude = json.optDouble("longitude", 0.0)
                            audioFile = saveAudioToFile(context, json.optString("audio").toByteArray())
                            "Location: Lat = $latitude, Lon = $longitude"
                        }

                        else -> json.optString("text").takeIf { it.isNotEmpty() } ?: text
                    }
                } catch (_: Exception) {
                    "Error processing message"
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                message = "Received audio (${bytes.size} bytes)"
                audioFile = saveAudioToFile(context, bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                message = "Error: ${t.message}"
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        }

        webSocket = client.newWebSocket(request, listener)

        onDispose {
            webSocket?.close(1000, "Closed by user")
            client.dispatcher.executorService.shutdown()
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "WebSocket Audio and Location Receiver",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = {
                isRequesting = true
                scope.launch {
                    try {
                        webSocket?.send("""{"type": "audio_request"}""")
                        message = "Audio request sent..."
                    } catch (e: Exception) {
                        message = "Error: ${e.message}"
                    } finally {
                        isRequesting = false
                    }
                }
            },
            enabled = !isRequesting && isConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRequesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Request Audio")
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        latitude?.let {
            Text(
                text = "Latitude: $it",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        longitude?.let {
            Text(
                text = "Longitude: $it",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = if (audioFile != null) "Saved to: ${audioFile!!.absolutePath}" else "No audio received yet",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Text(
            text = "WebSocket: ${if (isConnected) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

fun saveAudioToFile(context: Context, audioData: ByteArray): File {
    val file = File.createTempFile("received_audio", ".mp3", context.cacheDir)
    FileOutputStream(file).use { it.write(audioData) }
    return file
}
