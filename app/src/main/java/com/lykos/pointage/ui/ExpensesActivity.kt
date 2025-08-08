package com.lykos.pointage.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.lykos.pointage.adapter.ExpenseAdapter
import com.lykos.pointage.databinding.ActivityExpensesBinding
import com.lykos.pointage.databinding.DialogAddExpenseBinding
import com.lykos.pointage.model.ExpenseItemRequest
import com.lykos.pointage.model.ExpenseResponse
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.viewmodel.ExpenseViewModel
import java.io.File
import java.io.FileOutputStream

/**
 * Activity to manage user expenses with image attachments.
 * Features:
 * - View expenses in RecyclerView
 * - Add new expense with note, price, and image
 * - Pull-to-refresh to reload data
 * - Image upload from camera or gallery
 * - Empty state handling
 */
class ExpensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpensesBinding
    private lateinit var expenseAdapter: ExpenseAdapter
    private val viewModel: ExpenseViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    private var currentUploadedImageUrl: String? = null
    private val userId = "01987620-49fa-7398-8ce6-17b887e206dd"
    private val cameraPermissionCode = 100
    private val storagePermissionCode = 101
    private var lastToastTime = 0L
    private var currentToast: Toast? = null
    private lateinit var preferencesManager: PreferencesManager

    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val imageFile = saveImageToInternalStorage(it)
                imageFile?.let { file ->
                    viewModel.uploadImageFromPath(file.absolutePath)
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                viewModel.uploadImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)

        setupRecyclerView()
        setupFAB()
        setupSwipeRefresh()
        observeViewModel()

        viewModel.loadUserExpenses(userId)
    }

    /**
     * Sets up the RecyclerView with adapter and performance optimizations
     */
    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(emptyList()) { expense ->
            showExpenseDetails(expense)
        }
        binding.recyclerViewExpenses.apply {
            layoutManager = LinearLayoutManager(this@ExpensesActivity)
            adapter = expenseAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    /**
     * Sets up the FAB for adding new expenses
     */
    private fun setupFAB() {
        binding.fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    /**
     * Sets up pull-to-refresh functionality
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setOnRefreshListener {
                viewModel.loadUserExpenses(userId)
            }
            setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            )
        }
    }

    /**
     * Observes ViewModel data changes and updates UI
     */
    private fun observeViewModel() {
        // Observe expenses list
        viewModel.expenses.observe(this, Observer { expenses ->
            Log.d("ExpensesActivity", "Expenses received: ${expenses.size}")
            try {
                expenseAdapter.updateExpenses(expenses)
                updateEmptyState(expenses.isEmpty())
                // Stop refresh animation
                binding.swipeRefresh.isRefreshing = false

                // Scroll to top if new data was added
                if (expenses.isNotEmpty()) {
                    binding.recyclerViewExpenses.scrollToPosition(0)
                }
            } catch (e: Exception) {
                Log.e("ExpensesActivity", "Error updating expenses UI", e)
                binding.swipeRefresh.isRefreshing = false
            }
        })

        // Observe loading state
        viewModel.isLoading.observe(this, Observer { isLoading ->
            Log.d("ExpensesActivity", "Loading state: $isLoading")
            runOnUiThread {
                try {
                    if (::binding.isInitialized) {
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        // Also show refresh indicator when loading
                        if (!binding.swipeRefresh.isRefreshing && isLoading) {
                            binding.swipeRefresh.isRefreshing = true
                        } else if (!isLoading) {
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ExpensesActivity", "Error updating loading state", e)
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        })

        // Observe error messages
        viewModel.errorMessage.observe(this, Observer { errorMessage ->
            errorMessage?.let {
                Log.e("ExpensesActivity", "Error: $it")
                showToastSafe(it, Toast.LENGTH_LONG)
                viewModel.clearErrorMessage()
                binding.swipeRefresh.isRefreshing = false
            }
        })

        // Observe success messages
        viewModel.successMessage.observe(this, Observer { successMessage ->
            successMessage?.let {
                Log.d("ExpensesActivity", "Success: $it")
                showToastSafe(it, Toast.LENGTH_SHORT)
                viewModel.clearSuccessMessage()
            }
        })

        // Observe uploaded image URL
        viewModel.uploadedImageUrl.observe(this, Observer { imageUrl ->
            currentUploadedImageUrl = imageUrl
            Log.d("ExpensesActivity", "Image uploaded: $imageUrl")
        })
    }

    /**
     * Prevents toast spam by limiting frequency
     */
    private fun showToastSafe(message: String, duration: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime < 2000) return

        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, duration)
        currentToast?.show()
        lastToastTime = currentTime
    }

    /**
     * Updates visibility of empty state vs. RecyclerView
     */
    private fun updateEmptyState(isEmpty: Boolean) {
        try {
            if (::binding.isInitialized) {
                binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerViewExpenses.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error updating empty state", e)
        }
    }

    /**
     * Shows dialog to add new expense
     */
    private fun showAddExpenseDialog() {
        try {
            val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
            currentUploadedImageUrl = null

            val dialog = AlertDialog.Builder(this)
                .setTitle("Add New Expense")
                .setView(dialogBinding.root)
                .setPositiveButton("Add") { _, _ ->
                    handleAddExpense(dialogBinding)
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialogBinding.buttonSelectImage.setOnClickListener {
                showImageSelectionDialog()
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error showing add expense dialog", e)
            showToastSafe("Error opening dialog", Toast.LENGTH_SHORT)
        }
    }

    /**
     * Handles expense creation with validation
     */
    private fun handleAddExpense(dialogBinding: DialogAddExpenseBinding) {
        try {
            val note = dialogBinding.editTextNote.text.toString().trim()
            val priceText = dialogBinding.editTextPrice.text.toString().trim()

            if (note.isEmpty()) {
                showToastSafe("Please enter a note", Toast.LENGTH_SHORT)
                return
            }
            if (priceText.isEmpty()) {
                showToastSafe("Please enter an amount", Toast.LENGTH_SHORT)
                return
            }

            val price = try {
                priceText.toFloat()
            } catch (_: NumberFormatException) {
                showToastSafe("Please enter a valid amount", Toast.LENGTH_SHORT)
                return
            }

            if (price <= 0) {
                showToastSafe("Amount must be greater than zero", Toast.LENGTH_SHORT)
                return
            }

            val imageUrl = currentUploadedImageUrl ?: ""
            val expenseItem = ExpenseItemRequest(
                note = note,
                price = price,
                image = imageUrl
            )

            Log.d("ExpensesActivity", "Creating expense: $expenseItem")
            viewModel.createExpense(userId, listOf(expenseItem))

            // Clear the current uploaded image URL for next use
            currentUploadedImageUrl = null
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error handling add expense", e)
            showToastSafe("Error adding expense", Toast.LENGTH_SHORT)
        }
    }

    /**
     * Shows dialog to select image source
     */
    private fun showImageSelectionDialog() {
        try {
            val options = arrayOf("Camera", "Gallery")
            AlertDialog.Builder(this)
                .setTitle("Select Image Source")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openCamera()
                        1 -> openGallery()
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error showing image selection dialog", e)
        }
    }

    /**
     * Opens camera with permission check
     */
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        } else {
            try {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(cameraIntent)
            } catch (e: Exception) {
                Log.e("ExpensesActivity", "Error opening camera", e)
                showToastSafe("Error opening camera", Toast.LENGTH_SHORT)
            }
        }
    }

    /**
     * Opens gallery with permission check
     */
    private fun openGallery() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                storagePermissionCode
            )
        } else {
            try {
                val galleryIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(galleryIntent)
            } catch (e: Exception) {
                Log.e("ExpensesActivity", "Error opening gallery", e)
                showToastSafe("Error opening gallery", Toast.LENGTH_SHORT)
            }
        }
    }

    /**
     * Saves bitmap to internal storage
     */
    private fun saveImageToInternalStorage(bitmap: Bitmap): File? {
        return try {
            val uploadDir = File(filesDir, "upload")
            if (!uploadDir.exists()) uploadDir.mkdirs()
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val file = File(uploadDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            Log.d("ExpensesActivity", "Image saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error saving image", e)
            showToastSafe("Error saving image", Toast.LENGTH_SHORT)
            null
        }
    }

    /**
     * Shows expense details and delete option
     */
    private fun showExpenseDetails(expense: ExpenseResponse) {
        try {
            val message = buildString {
                append("Total Amount: ${expense.totalAmount} MAD\n")
                append("Created At: ${expense.createdAt}\n")
                append("Items:\n")
                expense.items.forEachIndexed { index, item ->
                    append("${index + 1}. ${item.note}: ${item.price} MAD\n")
                }
            }

            AlertDialog.Builder(this)
                .setTitle("Expense Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNegativeButton("Delete") { _, _ ->
                    confirmDeleteExpense(expense.id)
                }
                .show()
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error showing expense details", e)
        }
    }

    /**
     * Confirms expense deletion
     */
    private fun confirmDeleteExpense(expenseId: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete this expense?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteExpense(expenseId, userId)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("ExpensesActivity", "Error showing delete confirmation", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            cameraPermissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    showToastSafe("Camera permission required", Toast.LENGTH_SHORT)
                }
            }

            storagePermissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    showToastSafe("Storage access permission required", Toast.LENGTH_SHORT)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentToast?.cancel()
        Log.d("ExpensesActivity", "onDestroy called")
    }

    override fun onPause() {
        super.onPause()
        currentToast?.cancel()
        Log.d("ExpensesActivity", "onPause called")
    }
}
