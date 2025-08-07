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
import com.lykos.pointage.adapter.ExpenseAdapter
import com.lykos.pointage.databinding.ActivityExpensesBinding
import com.lykos.pointage.databinding.DialogAddExpenseBinding
import com.lykos.pointage.model.ExpenseItemRequest
import com.lykos.pointage.model.ExpenseResponse
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.viewmodel.ExpenseViewModel
import java.io.File
import java.io.FileOutputStream

class ExpensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpensesBinding
    private lateinit var expenseAdapter: ExpenseAdapter
    private val viewModel: ExpenseViewModel by viewModels()

    private var selectedImageUri: Uri? = null
    private var currentUploadedImageUrl: String? = null

    private val userId = "01987620-49fa-7398-8ce6-17b887e206dd"

    private val cameraPermissionCode = 100
    private val storagePermissionCode = 101

    // Prevent toast spam
    private var lastToastTime = 0L
    private var currentToast: Toast? = null

    private lateinit var userID: String
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

        userID = preferencesManager.getCurrentUserId().toString()

        setupRecyclerView()
        setupFAB()
        observeViewModel()

        // Load initial data
        viewModel.loadUserExpenses(userId)

    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(emptyList()) { expense ->
            showExpenseDetails(expense)
        }

        binding.recyclerViewExpenses.apply {
            layoutManager = LinearLayoutManager(this@ExpensesActivity)
            adapter = expenseAdapter
            // Optimize RecyclerView performance
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupFAB() {
        binding.fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    private fun observeViewModel() {
        // Observe expenses list
        viewModel.expenses.observe(this, Observer { expenses ->
            Log.d("MainActivity", "Expenses received: ${expenses.size}")

            // Update UI on main thread
            runOnUiThread {
                try {
                    expenseAdapter.updateExpenses(expenses)
                    updateEmptyState(expenses.isEmpty())
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error updating expenses UI", e)
                }
            }
        })

        // Observe loading state
        viewModel.isLoading.observe(this, Observer { isLoading ->
            Log.d("MainActivity", "Loading state: $isLoading")

            runOnUiThread {
                try {
                    if (::binding.isInitialized) {
                        binding.progressBar.visibility = if (isLoading)
                            View.VISIBLE else View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error updating loading state", e)
                }
            }
        })

        // Observe error messages with spam prevention
        viewModel.errorMessage.observe(this, Observer { errorMessage ->
            errorMessage?.let {
                Log.e("MainActivity", "Error: $it")
                showToastSafe(it, Toast.LENGTH_LONG)
                viewModel.clearErrorMessage()
            }
        })

        // Observe success messages with spam prevention
        viewModel.successMessage.observe(this, Observer { successMessage ->
            successMessage?.let {
                Log.d("MainActivity", "Success: $it")
                showToastSafe(it, Toast.LENGTH_SHORT)
                viewModel.clearSuccessMessage()
            }
        })

        // Observe uploaded image URL
        viewModel.uploadedImageUrl.observe(this, Observer { imageUrl ->
            currentUploadedImageUrl = imageUrl
            Log.d("MainActivity", "Image uploaded: $imageUrl")
        })
    }

    // Prevent toast spam
    private fun showToastSafe(message: String, duration: Int) {
        val currentTime = System.currentTimeMillis()

        // Prevent showing same toast within 2 seconds
        if (currentTime - lastToastTime < 2000) {
            return
        }

        // Cancel previous toast
        currentToast?.cancel()

        // Show new toast
        currentToast = Toast.makeText(this, message, duration)
        currentToast?.show()

        lastToastTime = currentTime
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        try {
            if (::binding.isInitialized) {
                if (isEmpty) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.recyclerViewExpenses.visibility = View.GONE
                } else {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.recyclerViewExpenses.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating empty state", e)
        }
    }

    private fun showAddExpenseDialog() {
        try {
            val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
            currentUploadedImageUrl = null

            val dialog = AlertDialog.Builder(this)
                .setTitle("إضافة مصروف جديد")
                .setView(dialogBinding.root)
                .setPositiveButton("إضافة") { _, _ ->
                    handleAddExpense(dialogBinding)
                }
                .setNegativeButton("إلغاء", null)
                .create()

            dialogBinding.buttonSelectImage.setOnClickListener {
                showImageSelectionDialog()
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing add expense dialog", e)
            showToastSafe("خطأ في فتح النافذة", Toast.LENGTH_SHORT)
        }
    }

    private fun handleAddExpense(dialogBinding: DialogAddExpenseBinding) {
        try {
            val note = dialogBinding.editTextNote.text.toString().trim()
            val priceText = dialogBinding.editTextPrice.text.toString().trim()

            if (note.isEmpty()) {
                showToastSafe("يرجى إدخال الملاحظة", Toast.LENGTH_SHORT)
                return
            }

            if (priceText.isEmpty()) {
                showToastSafe("يرجى إدخال المبلغ", Toast.LENGTH_SHORT)
                return
            }

            val price = try {
                priceText.toFloat()
            } catch (_: NumberFormatException) {
                showToastSafe("يرجى إدخال مبلغ صحيح", Toast.LENGTH_SHORT)
                return
            }

            if (price <= 0) {
                showToastSafe("يجب أن يكون المبلغ أكبر من صفر", Toast.LENGTH_SHORT)
                return
            }

            val imageUrl = currentUploadedImageUrl ?: ""

            val expenseItem = ExpenseItemRequest(
                note = note,
                price = price,
                image = imageUrl
            )

            Log.d("MainActivity", "Creating expense: $expenseItem")
            viewModel.createExpense(userId, listOf(expenseItem))

        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling add expense", e)
            showToastSafe("خطأ في إضافة المصروف", Toast.LENGTH_SHORT)
        }
    }

    private fun showImageSelectionDialog() {
        try {
            val options = arrayOf("الكاميرا", "المعرض")

            AlertDialog.Builder(this)
                .setTitle("اختر مصدر الصورة")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openCamera()
                        1 -> openGallery()
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing image selection dialog", e)
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
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
                Log.e("MainActivity", "Error opening camera", e)
                showToastSafe("خطأ في فتح الكاميرا", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun openGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                storagePermissionCode
            )
        } else {
            try {
                val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(galleryIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error opening gallery", e)
                showToastSafe("خطأ في فتح المعرض", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): File? {
        return try {
            val uploadDir = File(filesDir, "upload")
            if (!uploadDir.exists()) {
                uploadDir.mkdirs()
            }

            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val file = File(uploadDir, fileName)

            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream) // Reduced quality
            outputStream.flush()
            outputStream.close()

            Log.d("MainActivity", "Image saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving image", e)
            showToastSafe("خطأ في حفظ الصورة", Toast.LENGTH_SHORT)
            null
        }
    }

    private fun showExpenseDetails(expense: ExpenseResponse) {
        try {
            val message = buildString {
                append("المبلغ الإجمالي: ${expense.totalAmount} درهم\n")
                append("تاريخ الإنشاء: ${expense.createdAt}\n\n")
                append("العناصر:\n")
                expense.items.forEachIndexed { index, item ->
                    append("${index + 1}. ${item.note}: ${item.price} درهم\n")
                }
            }

            AlertDialog.Builder(this)
                .setTitle("تفاصيل المصروف")
                .setMessage(message)
                .setPositiveButton("موافق", null)
                .setNegativeButton("حذف") { _, _ ->
                    confirmDeleteExpense(expense.id)
                }
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing expense details", e)
        }
    }

    private fun confirmDeleteExpense(expenseId: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle("تأكيد الحذف")
                .setMessage("هل أنت متأكد من حذف هذا المصروف؟")
                .setPositiveButton("حذف") { _, _ ->
                    viewModel.deleteExpense(expenseId, userId)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing delete confirmation", e)
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
                    showToastSafe("إذن الكاميرا مطلوب", Toast.LENGTH_SHORT)
                }
            }
            storagePermissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    showToastSafe("إذن الوصول للملفات مطلوب", Toast.LENGTH_SHORT)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentToast?.cancel()
        Log.d("MainActivity", "onDestroy called")
    }

    override fun onPause() {
        super.onPause()
        currentToast?.cancel()
        Log.d("MainActivity", "onPause called")
    }
}
