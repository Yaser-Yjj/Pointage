package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lykos.pointage.R
import com.lykos.pointage.adapter.ImageAdapter
import com.lykos.pointage.service.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


@SuppressLint("NotifyDataSetChanged")
class ReportActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextNote: EditText
    private lateinit var buttonAddImage: Button
    private lateinit var buttonSendReport: Button
    private lateinit var recyclerViewImages: RecyclerView
    private lateinit var imageAdapter: ImageAdapter

    private val selectedImageUris = mutableListOf<Uri>()

    // Activity Result Launchers for permissions and image selection
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all required permissions are granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            showImageSourceDialog()
        } else {
            Toast.makeText(this, "Permissions are required to add images.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                // Single image selected
                selectedImageUris.add(uri)
                imageAdapter.notifyItemInserted(selectedImageUris.size - 1)
            }
            result.data?.clipData?.let { clipData ->
                // Multiple images selected
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    selectedImageUris.add(uri)
                }
                imageAdapter.notifyDataSetChanged()
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUris.add(uri)
                imageAdapter.notifyItemInserted(selectedImageUris.size - 1)
            } ?: run {
                Toast.makeText(
                    this,
                    "Image captured, but URI not directly returned. Check pre-defined URI if used.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        editTextUsername = findViewById(R.id.editTextUsername) // Initialize username EditText
        editTextNote = findViewById(R.id.editTextNote)
        buttonAddImage = findViewById(R.id.buttonAddImage)
        buttonSendReport = findViewById(R.id.buttonSendReport)
        recyclerViewImages = findViewById(R.id.recyclerViewImages)

        setupRecyclerView()

        buttonAddImage.setOnClickListener {
            checkPermissionsAndShowImageSourceDialog()
        }

        buttonSendReport.setOnClickListener {
            sendReport()
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(selectedImageUris) { uriToRemove ->
            selectedImageUris.remove(uriToRemove)
            imageAdapter.notifyDataSetChanged() // Notify adapter of change
            Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show()
        }
        recyclerViewImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewImages.adapter = imageAdapter
    }

    private fun checkPermissionsAndShowImageSourceDialog() {
        val permissionsToRequest = mutableListOf<String>()

        // Camera
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Read Storage
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            showImageSourceDialog()
        }
    }


    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this).setTitle("Add Image").setItems(options) { dialog, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }.show()
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            takePictureLauncher.launch(takePictureIntent)
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val pickPhotoIntent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickPhotoIntent.putExtra(
            Intent.EXTRA_ALLOW_MULTIPLE, true
        ) // Allow multiple image selection
        pickImageLauncher.launch(pickPhotoIntent)
    }

    private fun sendReport() {
        val currentUsername = editTextUsername.text.toString().trim() // Get username from EditText
        val reportText = editTextNote.text.toString().trim()

        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "Please enter your full name.", Toast.LENGTH_SHORT).show()
            return
        }

        if (reportText.isEmpty() && selectedImageUris.isEmpty()) {
            Toast.makeText(
                this, "Please add a note or select at least one image.", Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val usernameBody = currentUsername.toRequestBody("text/plain".toMediaTypeOrNull())
                val textBody = reportText.toRequestBody("text/plain".toMediaTypeOrNull())
                val imageParts = mutableListOf<MultipartBody.Part>()

                selectedImageUris.forEachIndexed { index, uri ->
                    val file = getFileFromUri(uri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        // The name "images[]" is crucial for PHP to recognize it as an array of files
                        val part =
                            MultipartBody.Part.createFormData("images[]", file.name, requestFile)
                        imageParts.add(part)
                    } else {
                        Log.e("ReportActivity", "Could not get file from URI: $uri")
                    }
                }

                val response =
                    RetrofitClient.instance.uploadReport(usernameBody, textBody, imageParts)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val reportResponse = response.body()
                        if (reportResponse?.success == true) {
                            Toast.makeText(
                                this@ReportActivity, reportResponse.message, Toast.LENGTH_LONG
                            ).show()
                            // Clear fields after successful upload
                            editTextUsername.text.clear() // Clear username field
                            editTextNote.text.clear()
                            selectedImageUris.clear()
                            imageAdapter.notifyDataSetChanged()
                        } else {
                            Toast.makeText(
                                this@ReportActivity,
                                "Upload failed: ${reportResponse?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Toast.makeText(
                            this@ReportActivity,
                            "Server error: ${response.code()} - $errorBody",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("ReportActivity", "Server error: ${response.code()} - $errorBody")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ReportActivity, "Network error: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                    Log.e("ReportActivity", "Network error", e)
                }
            }
        }
    }

    // Helper function to get a File from a content URI
    // This is crucial for sending images as files in a Multipart request
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.let {
                val fileName = getFileName(uri) ?: "temp_image_${System.currentTimeMillis()}.jpg"
                val file = File(cacheDir, fileName) // Use cache directory
                FileOutputStream(file).use { outputStream ->
                    it.copyTo(outputStream)
                }
                file
            }
        } catch (e: Exception) {
            Log.e("ReportActivity", "Error getting file from URI: $uri", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
}
