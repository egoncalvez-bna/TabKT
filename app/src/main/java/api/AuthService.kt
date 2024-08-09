import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class ApplicationID(val applicationID: String)

interface ApiService {
    @Headers("Content-Type: application/json")
    //@Headers("Content-Type: text/xml")
    @POST("WSAuthJWT.asmx/GenerateToken")
    //@POST("Auth/getToken")
    fun generateToken(@Body applicationID: String): Call<String>
}
