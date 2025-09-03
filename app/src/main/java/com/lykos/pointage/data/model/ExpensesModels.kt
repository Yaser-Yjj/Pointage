package com.lykos.pointage.data.model

import com.google.gson.annotations.SerializedName

// Base API Response
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("data")
    val data: T? = null,
    
    @SerializedName("timestamp")
    val timestamp: String
)

// Create Expense Request
data class CreateExpenseRequest(
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("items")
    val items: List<ExpenseItemRequest>
)

// Expense Item Request
data class ExpenseItemRequest(
    @SerializedName("note")
    val note: String,
    
    @SerializedName("price")
    val price: Float,
    
    @SerializedName("image")
    val image: String = ""
)

// Expense Response
data class ExpenseResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("items")
    val items: List<ExpenseItemResponse>,
    
    @SerializedName("total_amount")
    val totalAmount: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("updated_at")
    val updatedAt: String
)

// Expense Item Response
data class ExpenseItemResponse(
    @SerializedName("note")
    val note: String,
    
    @SerializedName("price")
    val price: Float,
    
    @SerializedName("image")
    val image: String
)

// Image Upload Response
data class ImageUploadResponse(
    @SerializedName("filename")
    val filename: String,
    
    @SerializedName("path")
    val path: String,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("size")
    val size: Long
)
