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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing expense data and business logic.
 * Handles CRUD operations, image uploads, and data fetching.
 */
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)

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

    // Event: Expense was created successfully
    private val _expenseCreated = MutableLiveData<Boolean>()
    val expenseCreated: LiveData<Boolean> = _expenseCreated

    // Prevent multiple simultaneous operations
    private var isOperationInProgress = false

    /**
     * Creates a new expense with the given items.
     * On success, sets expenseCreated = true (no list reload here).
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
                    _expenseCreated.postValue(true)
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
     * Optional: other existing methods kept as-is for other screens
     */
    fun clearErrorMessage() {
        _errorMessage.postValue(null)
    }

    fun clearSuccessMessage() {
        _successMessage.postValue(null)
    }

    fun clearExpenseCreated() {
        _expenseCreated.postValue(false)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ExpenseViewModel", "ViewModel cleared")
    }
}
