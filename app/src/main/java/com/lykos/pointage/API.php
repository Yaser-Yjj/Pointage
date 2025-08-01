<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json');

$servername = "localhost";
$dbname = "boombato_pointage";
$username_db = "boombato_yaser";
$password_db = "Myname&mybirthday2004";

$conn = new mysqli($servername, $username_db, $password_db, $dbname);

if ($conn->connect_error) {
    echo json_encode(["success" => false, "message" => "Connection failed: " . $conn->connect_error]);
    exit();
}

$reportText = $_POST['report_text'] ?? '';
$username = $_POST['username'] ?? 'anonymous';

// Use absolute path but store relative paths in database
$uploadDir = __DIR__ . '/upload/';
$relativeUploadDir = 'upload/'; // For storing in database and web access
$uploadedImagePaths = [];
$uploadedRelativePaths = [];

// Create upload directory if it doesn't exist
if (!is_dir($uploadDir)) {
    if (!mkdir($uploadDir, 0755, true)) {
        error_log("Failed to create upload directory: $uploadDir");
        echo json_encode(["success" => false, "message" => "Failed to create upload directory."]);
        exit();
    }
    error_log("Created upload directory: $uploadDir");
}

// Check if directory is writable
if (!is_writable($uploadDir)) {
    error_log("Upload directory is not writable: $uploadDir");
    echo json_encode(["success" => false, "message" => "Upload directory is not writable."]);
    exit();
}

// Enhanced debugging
error_log("=== UPLOAD DEBUG START ===");
error_log("Username: $username");
error_log("Report text: $reportText");
error_log("POST data: " . print_r($_POST, true));
error_log("FILES data: " . print_r($_FILES, true));
error_log("Upload directory: $uploadDir");
error_log("Directory exists: " . (is_dir($uploadDir) ? 'YES' : 'NO'));
error_log("Directory writable: " . (is_writable($uploadDir) ? 'YES' : 'NO'));

// Check if any files were uploaded
if (empty($_FILES)) {
    error_log("No FILES data received");
    // Still save the report even without images
    $imagePathsJson = json_encode([]);
    $stmt = $conn->prepare("INSERT INTO daily_reports (username, report_text, image_paths) VALUES (?, ?, ?)");
    $stmt->bind_param("sss", $username, $reportText, $imagePathsJson);

    if ($stmt->execute()) {
        echo json_encode([
            "success" => true,
            "message" => "Report submitted successfully (no images)!",
            "data" => [
                "image_paths" => [],
                "images_uploaded" => 0,
                "report_id" => $conn->insert_id
            ]
        ]);
    } else {
        echo json_encode(["success" => false, "message" => "Database error: " . $stmt->error]);
    }
    $stmt->close();
    $conn->close();
    exit();
}

// Handle multiple file upload scenarios
$filesToProcess = [];

// Check if files are sent as array (multiple files)
if (isset($_FILES['images']) && is_array($_FILES['images']['error'])) {
    error_log("Processing multiple files as array");
    foreach ($_FILES['images']['name'] as $key => $name) {
        if (!empty($name)) { // Only process if filename is not empty
            $filesToProcess[] = [
                'name' => $_FILES['images']['name'][$key],
                'tmp_name' => $_FILES['images']['tmp_name'][$key],
                'error' => $_FILES['images']['error'][$key],
                'size' => $_FILES['images']['size'][$key],
                'type' => $_FILES['images']['type'][$key]
            ];
        }
    }
}
// Check if single file or different structure
elseif (isset($_FILES['images']) && !empty($_FILES['images']['name'])) {
    error_log("Processing single file");
    $filesToProcess[] = [
        'name' => $_FILES['images']['name'],
        'tmp_name' => $_FILES['images']['tmp_name'],
        'error' => $_FILES['images']['error'],
        'size' => $_FILES['images']['size'],
        'type' => $_FILES['images']['type']
    ];
}
// Try alternative field names that mobile apps might use
else {
    error_log("Checking alternative field names");
    foreach ($_FILES as $fieldName => $fileData) {
        error_log("Found file field: $fieldName");
        if (is_array($fileData['error'])) {
            // Multiple files
            foreach ($fileData['name'] as $key => $name) {
                if (!empty($name)) {
                    $filesToProcess[] = [
                        'name' => $fileData['name'][$key],
                        'tmp_name' => $fileData['tmp_name'][$key],
                        'error' => $fileData['error'][$key],
                        'size' => $fileData['size'][$key],
                        'type' => $fileData['type'][$key]
                    ];
                }
            }
        } else {
            // Single file
            if (!empty($fileData['name'])) {
                $filesToProcess[] = [
                    'name' => $fileData['name'],
                    'tmp_name' => $fileData['tmp_name'],
                    'error' => $fileData['error'],
                    'size' => $fileData['size'],
                    'type' => $fileData['type']
                ];
            }
        }
    }
}

error_log("Files to process: " . count($filesToProcess));

// Process each file
foreach ($filesToProcess as $index => $file) {
    error_log("Processing file $index: " . $file['name']);

    if ($file['error'] !== UPLOAD_ERR_OK) {
        error_log("Upload error code: " . $file['error'] . " for file " . $file['name']);
        continue;
    }

    if (empty($file['tmp_name']) || !file_exists($file['tmp_name'])) {
        error_log("Temporary file does not exist: " . $file['tmp_name']);
        continue;
    }

    if ($file['size'] > 5 * 1024 * 1024) { // 5MB limit
        error_log("File too large: " . $file['name'] . " (" . $file['size'] . " bytes)");
        continue;
    }

    // More flexible file type checking
    $allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif'];
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mimeType = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);

    error_log("Detected MIME type: $mimeType for file: " . $file['name']);

    if (!in_array($mimeType, $allowedTypes) && !in_array($file['type'], $allowedTypes)) {
        error_log("Invalid file type - MIME: $mimeType, Reported: " . $file['type']);
        continue;
    }

    // Generate safe filename with username prefix
    $fileExtension = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (empty($fileExtension)) {
        // Guess extension from MIME type
        $mimeToExt = [
            'image/jpeg' => 'jpg',
            'image/png' => 'png',
            'image/gif' => 'gif'
        ];
        $fileExtension = $mimeToExt[$mimeType] ?? 'jpg';
    }

    // Include username in filename for better organization
    $safeUsername = preg_replace('/[^a-zA-Z0-9_-]/', '_', $username);
    $newFileName = $safeUsername . '_' . uniqid('img_' . date('Ymd_His') . '_') . '.' . $fileExtension;
    $destination = $uploadDir . $newFileName;
    $relativePath = $relativeUploadDir . $newFileName;

    error_log("Attempting to move file from " . $file['tmp_name'] . " to $destination");

    if (move_uploaded_file($file['tmp_name'], $destination)) {
        // Verify file was actually created
        if (file_exists($destination)) {
            $uploadedImagePaths[] = $destination;
            $uploadedRelativePaths[] = $relativePath;
            error_log("Successfully uploaded: $destination (Size: " . filesize($destination) . " bytes)");

            // Set proper permissions
            chmod($destination, 0644);
        } else {
            error_log("File move reported success but file doesn't exist: $destination");
        }
    } else {
        error_log("move_uploaded_file failed for " . $file['tmp_name'] . " to $destination");
        $lastError = error_get_last();
        error_log("Last error: " . ($lastError['message'] ?? 'Unknown error'));
    }
}

error_log("=== UPLOAD DEBUG END ===");
error_log("Total files uploaded: " . count($uploadedImagePaths));

// Always store the report, even if no images were uploaded
// Store relative paths in database for web access
$imagePathsJson = json_encode($uploadedRelativePaths);

$stmt = $conn->prepare("INSERT INTO daily_reports (username, report_text, image_paths) VALUES (?, ?, ?)");
$stmt->bind_param("sss", $username, $reportText, $imagePathsJson);

if ($stmt->execute()) {
    echo json_encode([
        "success" => true,
        "message" => "Report submitted successfully!",
        "data" => [
            "image_paths" => $uploadedRelativePaths,
            "images_uploaded" => count($uploadedImagePaths),
            "report_id" => $conn->insert_id,
            "username" => $username
        ]
    ]);
} else {
    error_log("DB Error: " . $stmt->error);
    echo json_encode(["success" => false, "message" => "Database error: " . $stmt->error]);
}

$stmt->close();
$conn->close();
?>