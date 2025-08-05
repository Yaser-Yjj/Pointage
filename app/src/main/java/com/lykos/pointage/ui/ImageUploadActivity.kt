package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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

class ImageUploadActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonAdd: Button
    private lateinit var buttonSend: Button
    private lateinit var imageAdapter: ImageAdapter
    private val selectedImageUris = mutableListOf<Uri>()

    private lateinit var userID: String


    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = requiredPermissions.all {
            grantResults[it] == true
        }

        if (allGranted) {
            showImageSourceDialog()
        } else {
            val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Images access is required. Go to Settings > Permissions."
            } else {
                "Storage access is required to add images."
            }
            showPermissionDeniedDialog(message)
        }
    }

    private fun showPermissionDeniedDialog(message: String) {
        AlertDialog.Builder(this).setTitle("Permission Required").setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                if (selectedImageUris.size < 10) {
                    selectedImageUris.add(uri)
                    imageAdapter.notifyItemInserted(selectedImageUris.size - 1)
                } else {
                    Toast.makeText(this, "You can only upload up to 10 images.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private var currentCameraPhotoUri: Uri? = null
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentCameraPhotoUri?.let { uri ->
                if (selectedImageUris.size < 10) {
                    selectedImageUris.add(uri)
                    imageAdapter.notifyItemInserted(selectedImageUris.size - 1)
                } else {
                    Toast.makeText(this, "Max 10 images allowed.", Toast.LENGTH_SHORT).show()
                }
            }
            currentCameraPhotoUri = null
        } else {
            currentCameraPhotoUri = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_upload)
        enableEdgeToEdge()
        recyclerView = findViewById(R.id.recyclerViewImages)
        buttonAdd = findViewById(R.id.buttonAddImage)
        buttonSend = findViewById(R.id.buttonSendReport)

        userID = "01987620-49fa-7398-8ce6-17b887e206dd"
        val note = intent.getStringExtra("report_text") ?: ""

        imageAdapter = ImageAdapter(selectedImageUris) { uri ->
            selectedImageUris.remove(uri)
            imageAdapter.notifyItemRemoved(selectedImageUris.indexOf(uri))
            Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show()
        }

        recyclerView.apply {
            layoutManager = GridLayoutManager(this@ImageUploadActivity, 3)
            adapter = imageAdapter
        }

        buttonAdd.setOnClickListener {
            checkPermissionsAndShowImageSourceDialog()
        }

        buttonSend.setOnClickListener {
            if (selectedImageUris.isEmpty()) {
                Toast.makeText(this, "Please add at least one image.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendReport(userID, note)
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun sendReport(userId: String, reportText: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {

                val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
                val reportTextPart = reportText.toRequestBody("text/plain".toMediaTypeOrNull())

                val imageParts = mutableListOf<MultipartBody.Part>()
                for (uri in selectedImageUris) {
                    val file = getFileFromUri(uri) ?: continue
                    val contentType = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        else -> "image/jpeg"
                    }.toMediaTypeOrNull()

                    val requestFile = file.asRequestBody(contentType)
                    val part = MultipartBody.Part.createFormData("images[]", file.name, requestFile)
                    imageParts.add(part)
                }


                val response = RetrofitClient.instance.uploadReport(
                    userIdPart,
                    reportTextPart,
                    imageParts
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ImageUploadActivity, "✅ Report sent!", Toast.LENGTH_LONG).show()
                        selectedImageUris.clear()
                        imageAdapter.notifyDataSetChanged()
                        finishAffinity()
                    } else {
                        val errorMsg = response.body()?.message ?: "Upload failed"
                        Toast.makeText(this@ImageUploadActivity, "❌ $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageUploadActivity, "🌐 Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "temp_image_${System.currentTimeMillis()}.jpg"
            val tempFile = File.createTempFile(fileName, null, cacheDir)
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("Upload", "Error converting URI to File", e)
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

    private fun checkPermissionsAndShowImageSourceDialog() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissions.add(Manifest.permission.CAMERA)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isEmpty()) {
            showImageSourceDialog()
        } else {
            requestPermissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Add Image")
            .setItems(arrayOf("Gallery", "Camera")) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> openCamera()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            // Create a file to save the image
            val photoFile = File(cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            currentCameraPhotoUri = FileProvider.getUriForFile(
                this,
                "com.lykos.pointage.fileprovider",
                photoFile
            )

            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentCameraPhotoUri)

            // Launch using the new launcher
            takePictureLauncher.launch(intent)
        } else {
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show()
        }
    }
}