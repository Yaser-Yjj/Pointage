package com.lykos.pointage.service

import com.lykos.pointage.model.ApiResponse
import com.lykos.pointage.model.CreateExpenseRequest
import com.lykos.pointage.model.ExpenseResponse
import com.lykos.pointage.model.ExpenseStatsResponse
import com.lykos.pointage.model.ExpensesListResponse
import com.lykos.pointage.model.ImageUploadResponse
import com.lykos.pointage.model.UpdateExpenseRequest
import com.lykos.pointage.model.data.LoginData
import com.lykos.pointage.model.response.LoginResponse
import com.lykos.pointage.model.response.PvReportResponse
import com.lykos.pointage.model.response.ReportResponse
import com.lykos.pointage.model.response.SafeZoneResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
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
    suspend fun getSafeZone(
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

    // Get user expenses
    @GET("depences/expenses.php")
    suspend fun getExpense(
        @Query("user_id") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<ExpenseResponse>>

    // Get single expense
    @GET("depences/expenses.php")
    suspend fun getUserExpenses(
        @Query("id") expenseId: String
    ): Response<ApiResponse<ExpensesListResponse>>

    // Update expense
    @PUT("depences/expenses.php")
    suspend fun updateExpense(
        @Body request: UpdateExpenseRequest
    ): Response<ApiResponse<ExpenseResponse>>

    // Delete expense
    @DELETE("depences/expenses.php")
    suspend fun deleteExpense(
        @Query("id") expenseId: String
    ): Response<ApiResponse<Unit>>

    // Upload image
    @Multipart
    @POST("depences/upload-image.php")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<ImageUploadResponse>>

    // Get expense statistics
    @GET("depences/expenses.php")
    suspend fun getExpenseStats(
        @Query("user_id") userId: String,
        @Query("stats") stats: Boolean = true,
        @Query("period") period: String = "month"
    ): Response<ApiResponse<ExpenseStatsResponse>>

}
