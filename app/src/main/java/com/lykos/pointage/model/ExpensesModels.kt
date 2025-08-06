package com.lykos.pointage.model

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

// Update Expense Request
data class UpdateExpenseRequest(
    @SerializedName("id")
    val id: String,
    
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

// Expenses List Response
data class ExpensesListResponse(
    @SerializedName("expenses")
    val expenses: List<ExpenseResponse>,
    
    @SerializedName("total_count")
    val totalCount: Int,
    
    @SerializedName("total_amount")
    val totalAmount: Float,
    
    @SerializedName("pagination")
    val pagination: PaginationResponse? = null
)

// Pagination Response
data class PaginationResponse(
    @SerializedName("current_page")
    val currentPage: Int,
    
    @SerializedName("per_page")
    val perPage: Int,
    
    @SerializedName("total_records")
    val totalRecords: Int,
    
    @SerializedName("total_pages")
    val totalPages: Int
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

// Expense Statistics Response
data class ExpenseStatsResponse(
    @SerializedName("period")
    val period: String,
    
    @SerializedName("summary")
    val summary: ExpenseStatsSummary,
    
    @SerializedName("monthly_breakdown")
    val monthlyBreakdown: List<MonthlyStats>
)

data class ExpenseStatsSummary(
    @SerializedName("total_expenses")
    val totalExpenses: Int,
    
    @SerializedName("total_amount")
    val totalAmount: Float,
    
    @SerializedName("average_amount")
    val averageAmount: Float,
    
    @SerializedName("min_amount")
    val minAmount: Float,
    
    @SerializedName("max_amount")
    val maxAmount: Float
)

data class MonthlyStats(
    @SerializedName("month")
    val month: String,
    
    @SerializedName("expense_count")
    val expenseCount: Int,
    
    @SerializedName("total_amount")
    val totalAmount: Float
)
