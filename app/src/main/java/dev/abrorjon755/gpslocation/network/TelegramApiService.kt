import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TelegramApiService {
    @GET("sendMessage")
    suspend fun sendMessage(
        @Query("chat_id") chatId: String,
        @Query("text") text: String
    ): Response<Unit>
}
