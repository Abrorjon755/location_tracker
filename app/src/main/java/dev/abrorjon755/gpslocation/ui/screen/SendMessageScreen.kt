package dev.abrorjon755.gpslocation.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.abrorjon755.gpslocation.data.sendMessageToBot
import kotlinx.coroutines.launch

@Composable
fun SendMessageScreen() {
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Enter Message") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Call the function to send the message
                scope.launch {
                    val chatId = "1100168676"  // Replace with your chat ID
                    sendMessageToBot(chatId, message)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Message")
        }
    }
}

@Preview
@Composable
fun PreviewSendMessageScreen() {
    SendMessageScreen()
}
