package com.lykos.pointage.ui.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lykos.pointage.data.model.ExpenseItemRequest
import com.lykos.pointage.databinding.ActivityExpensesBinding
import com.lykos.pointage.ui.viewmodel.ExpenseViewModel
import java.io.File
import java.io.FileOutputStream

/**
 * Add Expense screen (no list, no dialog).
 * - Shows inline form: note, amount, add receipt, save
 * - On successful creation, redirects to MainActivity
 */
class ExpensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpensesBinding
    private val viewModel: ExpenseViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    private var currentUploadedImageUrl: String? = null

    // Example user id (replace with real one from your auth/session)
    private val userId = "01987620-49fa-7398-8ce6-17b887e206dd"

    private val cameraPermissionCode = 100
    private val storagePermissionCode = 101

    private var lastToastTime = 0L
    private var currentToast: Toast? = null

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val imageFile = saveImageToInternalStorage(it)
                imageFile?.let { file ->
                    viewModel.uploadImageFromPath(file.absolutePath)
                    showPreview(file)
                }
            }
        }
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                viewModel.uploadImage(uri)
                showPreview(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupForm()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupForm() {
        currentUploadedImageUrl = null

        binding.buttonSelectImage.setOnClickListener {
            showImageSelectionDialog()
        }

        binding.buttonCreateExpense.setOnClickListener {
            handleCreateExpense()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Log.e("ExpensesActivity", "Error: $it")
                showToastSafe(it, Toast.LENGTH_LONG)
                viewModel.clearErrorMessage()
            }
        }

        viewModel.successMessage.observe(this) { message ->
            message?.let {
                // Show success toasts for uploads and create
                showToastSafe(it, Toast.LENGTH_SHORT)
                viewModel.clearSuccessMessage()
            }
        }

        viewModel.uploadedImageUrl.observe(this) { imageUrl ->
            currentUploadedImageUrl = imageUrl
            Log.d("ExpensesActivity", "Image uploaded: $imageUrl")
        }

        viewModel.expenseCreated.observe(this) { created ->
            if (created == true) {
                // Navigate to MainActivity and finish
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
                // consume event
                viewModel.clearExpenseCreated()
            }
        }
    }

    private fun handleCreateExpense() {
        val note = binding.editTextNote.text?.toString()?.trim().orEmpty()
        val priceText = binding.editTextPrice.text?.toString()?.trim().orEmpty()

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
    }

    private fun showImageSelectionDialog() {
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
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
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

    private fun openGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
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

    private fun showPreview(uriOrFile: Any) {
        val imageView: ImageView = binding.imagePreview
        try {
            imageView.visibility = View.VISIBLE
            when (uriOrFile) {
                is Uri -> imageView.setImageURI(uriOrFile)
                is File -> imageView.setImageURI(Uri.fromFile(uriOrFile))
            }
        } catch (_: Exception) {
            imageView.visibility = View.GONE
        }
    }

    private fun showToastSafe(message: String, duration: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime < 800) return
        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, duration)
        currentToast?.show()
        lastToastTime = currentTime
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
    }

    override fun onPause() {
        super.onPause()
        currentToast?.cancel()
    }
}
