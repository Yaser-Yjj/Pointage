package com.lykos.pointage.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lykos.pointage.database.repository.ExpenseRepository
import com.lykos.pointage.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)

    // LiveData for expenses list
    private val _expenses = MutableLiveData<List<ExpenseResponse>>()
    val expenses: LiveData<List<ExpenseResponse>> = _expenses

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // LiveData for success messages
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    // LiveData for image upload result
    private val _uploadedImageUrl = MutableLiveData<String>()
    val uploadedImageUrl: LiveData<String> = _uploadedImageUrl

    // LiveData for statistics
    private val _expenseStats = MutableLiveData<ExpenseStatsResponse>()
    val expenseStats: LiveData<ExpenseStatsResponse> = _expenseStats

    // Prevent multiple simultaneous operations
    private var isOperationInProgress = false

    // Create new expense
    fun createExpense(userId: String, items: List<ExpenseItemRequest>) {
        if (isOperationInProgress) {
            Log.w("ExpenseViewModel", "Operation already in progress, ignoring create expense")
            return
        }

        viewModelScope.launch {
            try {
                isOperationInProgress = true
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Creating expense for user: $userId")

                val result = withContext(Dispatchers.IO) {
                    repository.createExpense(userId, items)
                }

                result.onSuccess { expense ->
                    Log.d("ExpenseViewModel", "Expense created successfully: ${expense.id}")
                    _successMessage.postValue("تم إضافة المصروف بنجاح")
                    loadUserExpenses(userId) // Refresh the list
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to create expense", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في إضافة المصروف")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in createExpense", e)
                _errorMessage.postValue("خطأ غير متوقع في إضافة المصروف")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    // Load user expenses
    fun loadUserExpenses(userId: String, page: Int = 1) {
        if (isOperationInProgress) {
            Log.w("ExpenseViewModel", "Operation already in progress, ignoring load expenses")
            return
        }

        viewModelScope.launch {
            try {
                isOperationInProgress = true
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Loading expenses for user: $userId")

                val result = withContext(Dispatchers.IO) {
                    repository.getUserExpenses(userId, page)
                }

                result.onSuccess { response ->
                    Log.d("ExpenseViewModel", "Loaded ${response.expenses.size} expenses")
                    _expenses.postValue(response.expenses)
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to load expenses", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في جلب المصاريف")
                    // Set empty list on error to prevent UI issues
                    _expenses.postValue(emptyList())
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in loadUserExpenses", e)
                _errorMessage.postValue("خطأ غير متوقع في جلب المصاريف")
                _expenses.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    // Upload image
    fun uploadImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Uploading image: $imageUri")

                val result = withContext(Dispatchers.IO) {
                    repository.uploadImage(imageUri)
                }

                result.onSuccess { response ->
                    Log.d("ExpenseViewModel", "Image uploaded successfully: ${response.url}")
                    _uploadedImageUrl.postValue(response.url)
                    _successMessage.postValue("تم رفع الصورة بنجاح")
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to upload image", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في رفع الصورة")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in uploadImage", e)
                _errorMessage.postValue("خطأ غير متوقع في رفع الصورة")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Upload image from file path
    fun uploadImageFromPath(imagePath: String) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Uploading image from path: $imagePath")

                val result = withContext(Dispatchers.IO) {
                    repository.uploadImageFromPath(imagePath)
                }

                result.onSuccess { response ->
                    Log.d("ExpenseViewModel", "Image uploaded successfully: ${response.url}")
                    _uploadedImageUrl.postValue(response.url)
                    _successMessage.postValue("تم رفع الصورة بنجاح")
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to upload image", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في رفع الصورة")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in uploadImageFromPath", e)
                _errorMessage.postValue("خطأ غير متوقع في رفع الصورة")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Update expense
    fun updateExpense(expenseId: String, items: List<ExpenseItemRequest>, userId: String) {
        if (isOperationInProgress) {
            Log.w("ExpenseViewModel", "Operation already in progress, ignoring update expense")
            return
        }

        viewModelScope.launch {
            try {
                isOperationInProgress = true
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Updating expense: $expenseId")

                val result = withContext(Dispatchers.IO) {
                    repository.updateExpense(expenseId, items)
                }

                result.onSuccess { expense ->
                    Log.d("ExpenseViewModel", "Expense updated successfully: ${expense.id}")
                    _successMessage.postValue("تم تحديث المصروف بنجاح")
                    loadUserExpenses(userId) // Refresh the list
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to update expense", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في تحديث المصروف")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in updateExpense", e)
                _errorMessage.postValue("خطأ غير متوقع في تحديث المصروف")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    // Delete expense
    fun deleteExpense(expenseId: String, userId: String) {
        if (isOperationInProgress) {
            Log.w("ExpenseViewModel", "Operation already in progress, ignoring delete expense")
            return
        }

        viewModelScope.launch {
            try {
                isOperationInProgress = true
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Deleting expense: $expenseId")

                val result = withContext(Dispatchers.IO) {
                    repository.deleteExpense(expenseId)
                }

                result.onSuccess {
                    Log.d("ExpenseViewModel", "Expense deleted successfully: $expenseId")
                    _successMessage.postValue("تم حذف المصروف بنجاح")
                    loadUserExpenses(userId) // Refresh the list
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to delete expense", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في حذف المصروف")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in deleteExpense", e)
                _errorMessage.postValue("خطأ غير متوقع في حذف المصروف")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    // Load expense statistics
    fun loadExpenseStats(userId: String, period: String = "month") {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)

                Log.d("ExpenseViewModel", "Loading stats for user: $userId, period: $period")

                val result = withContext(Dispatchers.IO) {
                    repository.getExpenseStats(userId, period)
                }

                result.onSuccess { stats ->
                    Log.d("ExpenseViewModel", "Stats loaded successfully")
                    _expenseStats.postValue(stats)
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to load stats", exception)
                    _errorMessage.postValue(exception.message ?: "خطأ في جلب الإحصائيات")
                }

            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in loadExpenseStats", e)
                _errorMessage.postValue("خطأ غير متوقع في جلب الإحصائيات")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.postValue(null)
    }

    // Clear success message
    fun clearSuccessMessage() {
        _successMessage.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ExpenseViewModel", "ViewModel cleared")
    }
}
