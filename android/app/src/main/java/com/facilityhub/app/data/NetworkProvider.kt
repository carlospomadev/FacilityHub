package com.facilityhub.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FacilityApi::class.java)
    }
}
