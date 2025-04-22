import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL =
        "https://api.telegram.org/bot1280298156:AAFFEnGVLbjsg4CwP_tGA3-vwDM4H6G5KxE/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val telegramApiService: TelegramApiService by lazy {
        retrofit.create(TelegramApiService::class.java)
    }
}
