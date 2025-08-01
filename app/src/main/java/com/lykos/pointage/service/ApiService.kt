package com.lykos.pointage.service

import com.lykos.pointage.model.ReportResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

interface ApiService {
    @Multipart
    @POST("upload_report.php")
    suspend fun uploadReport(
        @Part("username") username: RequestBody,
        @Part("report_text") reportText: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ReportResponse>
}

object RetrofitClient {

    private const val BASE_URL = "https://boombatours.com/Pointage_API/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
