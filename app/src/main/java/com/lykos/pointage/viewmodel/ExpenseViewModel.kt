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

/**
 * ViewModel for managing expense data and business logic.
 * Handles CRUD operations, image uploads, and data fetching.
 */
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

    /**
     * Creates a new expense with the given items
     */
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
                    _successMessage.postValue("Expense added successfully")
                    // Force refresh the list after successful creation
                    _expenses.postValue(emptyList()) // Clear current list first
                    loadUserExpenses(userId) // Then reload
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to create expense", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to add expense")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in createExpense", e)
                _errorMessage.postValue("Unexpected error adding expense")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    /**
     * Loads all expenses for a user (fixed to load all pages if needed)
     */
    fun loadUserExpenses(userId: String) {
        if (isOperationInProgress) return
        viewModelScope.launch {
            try {
                isOperationInProgress = true
                _isLoading.postValue(true)
                Log.d("ExpenseViewModel", "Loading ALL expenses for user: $userId")

                val result = withContext(Dispatchers.IO) {
                    repository.getAllUserExpenses(userId)
                }

                result.onSuccess { expenses ->
                    Log.d("ExpenseViewModel", "Loaded ${expenses.size} total expenses")
                    // Force UI update by posting to main thread
                    withContext(Dispatchers.Main) {
                        _expenses.value = expenses
                    }
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to load all expenses", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to load expenses")
                    withContext(Dispatchers.Main) {
                        _expenses.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in loadAllUserExpenses", e)
                _errorMessage.postValue("Unexpected error loading expenses")
                withContext(Dispatchers.Main) {
                    _expenses.value = emptyList()
                }
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    /**
     * Uploads an image from URI
     */
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
                    _successMessage.postValue("Image uploaded successfully")
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to upload image", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to upload image")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in uploadImage", e)
                _errorMessage.postValue("Unexpected error uploading image")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Uploads an image from file path
     */
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
                    _successMessage.postValue("Image uploaded successfully")
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to upload image", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to upload image")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in uploadImageFromPath", e)
                _errorMessage.postValue("Unexpected error uploading image")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Updates an existing expense
     */
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
                    _successMessage.postValue("Expense updated successfully")
                    loadUserExpenses(userId) // Refresh the list
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to update expense", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to update expense")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in updateExpense", e)
                _errorMessage.postValue("Unexpected error updating expense")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    /**
     * Deletes an expense
     */
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
                    _successMessage.postValue("Expense deleted successfully")
                    loadUserExpenses(userId) // Refresh the list
                }.onFailure { exception ->
                    Log.e("ExpenseViewModel", "Failed to delete expense", exception)
                    _errorMessage.postValue(exception.message ?: "Failed to delete expense")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in deleteExpense", e)
                _errorMessage.postValue("Unexpected error deleting expense")
            } finally {
                _isLoading.postValue(false)
                isOperationInProgress = false
            }
        }
    }

    /**
     * Loads expense statistics
     */
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
                    _errorMessage.postValue(exception.message ?: "Failed to load statistics")
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Exception in loadExpenseStats", e)
                _errorMessage.postValue("Unexpected error loading statistics")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Clears error message
     */
    fun clearErrorMessage() {
        _errorMessage.postValue(null)
    }

    /**
     * Clears success message
     */
    fun clearSuccessMessage() {
        _successMessage.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ExpenseViewModel", "ViewModel cleared")
    }
}
