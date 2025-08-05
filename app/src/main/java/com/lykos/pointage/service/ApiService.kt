package com.lykos.pointage.service

import com.lykos.pointage.model.ReportResponse
import com.lykos.pointage.model.SafeZoneResponse
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
import retrofit2.http.GET
import retrofit2.http.PartMap
import retrofit2.http.Query

interface ApiService {
    @Multipart
    @POST("upload_report/")
    suspend fun uploadReport(
        @Part("user_id") userId: RequestBody,
        @Part("report_text") reportText: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ReportResponse>

    @GET("safe-zone")
    suspend fun getSafeZone(
        @Query("user_id") userId: String
    ): Response<SafeZoneResponse>

}
