package com.lykos.pointage.service

import com.lykos.pointage.data.model.ApiResponse
import com.lykos.pointage.data.model.CreateExpenseRequest
import com.lykos.pointage.data.model.ExpenseResponse
import com.lykos.pointage.data.model.ImageUploadResponse
import com.lykos.pointage.data.model.data.LoginData
import com.lykos.pointage.data.model.response.LoginResponse
import com.lykos.pointage.data.model.response.PvReportResponse
import com.lykos.pointage.data.model.response.ReportResponse
import com.lykos.pointage.data.model.response.SafeZoneResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {
    @Multipart
    @POST("upload_report/")
    suspend fun uploadReport(
        @Part("user_id") userId: RequestBody,
        @Part("report_text") reportText: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ReportResponse>

    @GET("safe-zone")
    suspend fun getSafeZones(
        @Query("user_id") userId: String
    ): Response<SafeZoneResponse>

    @Multipart
    @POST("pv_reports/")
    suspend fun postPvReport(
        @Part("user_id") userId: RequestBody,
        @Part("note") note: RequestBody
    ): Response<PvReportResponse>

    @POST
    @Headers("Accept: application/json")
    suspend fun login(@Url url: String, @Body loginRequest: LoginData): Response<LoginResponse>

    // Create new expense
    @POST("depences/expenses.php")
    suspend fun createExpense(
        @Body request: CreateExpenseRequest
    ): Response<ApiResponse<ExpenseResponse>>

    // Upload image
    @Multipart
    @POST("depences/upload-image.php")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<ImageUploadResponse>>

}
