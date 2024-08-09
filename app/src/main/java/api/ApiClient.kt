import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    //private const val BASE_URL = "https://dap13cc0001.dcc.dbna.net/BNA.KT.Autenticador/"
    private const val BASE_URL = "https://dap13cc0001.dcc.dbna.net/BNA.KT.Totem.Tab/"

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
