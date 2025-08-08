package com.lykos.pointage.database.repository

import android.content.Context
import android.net.Uri
import com.lykos.pointage.model.*
import com.lykos.pointage.service.RetrofitClient
import com.lykos.pointage.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ExpenseRepository(private val context: Context) {
    
    private val apiService = RetrofitClient.apiService

    suspend fun createExpense(
        userId: String,
        items: List<ExpenseItemRequest>
    ): Result<ExpenseResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CreateExpenseRequest(userId, items)
            val response = apiService.createExpense(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { data ->
                    Result.success(data)
                } ?: Result.failure(Exception("No data received"))
            } else {
                val errorMessage = response.body()?.message ?: "Unknown error"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadImage(imageUri: Uri): Result<ImageUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val imageFile = FileUtils.getFileFromUri(context, imageUri)
                ?: return@withContext Result.failure(Exception("Failed to process image"))

            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
            
            val response = apiService.uploadImage(imagePart)
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { data ->
                    Result.success(data)
                } ?: Result.failure(Exception("No data received"))
            } else {
                val errorMessage = response.body()?.message ?: "Failed to upload image"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadImageFromPath(imagePath: String): Result<ImageUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@withContext Result.failure(Exception("Image file not found"))
            }
            
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
            
            val response = apiService.uploadImage(imagePart)
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { data ->
                    Result.success(data)
                } ?: Result.failure(Exception("No data received"))
            } else {
                val errorMessage = response.body()?.message ?: "Failed to upload image"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
