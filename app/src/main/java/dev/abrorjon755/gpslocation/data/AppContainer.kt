package dev.abrorjon755.gpslocation.data

import RetrofitClient
import android.util.Log
import retrofit2.Response

suspend fun sendMessageToBot(chatId: String, message: String) {
    try {
        val response: Response<Unit> =
            RetrofitClient.telegramApiService.sendMessage(chatId, message)
        if (response.isSuccessful) {
            Log.d("TelegramBot", "Message sent successfully")
        } else {
            val errorResponse = response.errorBody()?.string()
            Log.e("TelegramBot", "Failed to send message. Response Code: ${response.code()}. Error: $errorResponse")
        }
    } catch (e: Exception) {
        Log.e("TelegramBot", "Error: ${e.message}")
    }
}
