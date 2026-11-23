package com.facilityhub.app.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * FH-009 / R7: metricas de red solo en compilaciones de depuracion (medicion de rendimiento). Registra metodo, ruta,
 * codigo y duracion; nunca cabeceras, token, parametros de consulta ni cuerpos.
 */
private object RegistroRed : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val r = chain.request()
        val inicio = System.nanoTime()
        try {
            val resp = chain.proceed(r)
            Log.d("FHNET", "${r.method} ${r.url.encodedPath} ${resp.code} ${(System.nanoTime() - inicio) / 1_000_000}ms")
            return resp
        } catch (e: Exception) {
            Log.d("FHNET", "${r.method} ${r.url.encodedPath} ERR ${(System.nanoTime() - inicio) / 1_000_000}ms")
            throw e
        }
    }
}

object NetworkProvider {
    // Emulador Android -> backend que se ejecuta en el PC anfitrion.
    // Para un movil real debe sustituirse por la IP local del equipo.
    private const val BASE_URL = "http://10.0.2.2:8080/"

    fun createApi(tokenStore: TokenStore): FacilityApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    tokenStore.token?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            }
            .apply { if (tokenStore.depurable) addInterceptor(RegistroRed) }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FacilityApi::class.java)
    }
}
