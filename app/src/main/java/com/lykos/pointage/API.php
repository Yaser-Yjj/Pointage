<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

require_once '../Database/database.php';

$user_id = $_POST['user_id'] ?? null;
$report_text = $_POST['report_text'] ?? '';
$uploadDir = __DIR__ . '/uploads/';
$relativeUploadDir = 'uploads/';
$uploadedRelativePaths = [];

if (!$user_id) {
    http_response_code(400);
    echo json_encode([
        "success" => false,
        "message" => "user_id is required"
    ]);
    exit();
}

if (!is_dir($uploadDir)) {
    if (!mkdir($uploadDir, 0755, true)) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Server failed to create upload directory."]);
        exit();
    }
}

if (!is_writable($uploadDir)) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Upload directory is not writable."]);
    exit();
}

// No files
if (empty($_FILES)) {
    $imagePathsJson = json_encode([]);
    $stmt = $conn->prepare("INSERT INTO daily_reports (user_id, report_text, image_paths) VALUES (?, ?, ?)");
    $stmt->bind_param("sss", $user_id, $report_text, $imagePathsJson);

    if ($stmt->execute()) {
        echo json_encode([
            "success" => true,
            "message" => "Report submitted successfully (no images)",
            "data" => [
                "report_id" => $conn->insert_id,
                "user_id" => $user_id,
                "images_uploaded" => 0,
                "image_paths" => []
            ]
        ]);
    } else {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "DB error: " . $stmt->error]);
    }
    $stmt->close();
    $conn->close();
    exit();
}

// Handle files
$filesToProcess = [];

if (isset($_FILES['images']) && is_array($_FILES['images']['error'])) {
    foreach ($_FILES['images']['name'] as $key => $name) {
        if (!empty($name)) {
            $filesToProcess[] = [
                'name' => $_FILES['images']['name'][$key],
                'tmp_name' => $_FILES['images']['tmp_name'][$key],
                'error' => $_FILES['images']['error'][$key],
                'size' => $_FILES['images']['size'][$key],
                'type' => $_FILES['images']['type'][$key]
            ];
        }
    }
} elseif (isset($_FILES['images']) && !empty($_FILES['images']['name'])) {
    $filesToProcess[] = $_FILES['images'];
} else {
    foreach ($_FILES as $file) {
        if (is_array($file['error'])) {
            foreach ($file['name'] as $i => $name) {
                if (!empty($name)) {
                    $filesToProcess[] = [
                        'name' => $file['name'][$i],
                        'tmp_name' => $file['tmp_name'][$i],
                        'error' => $file['error'][$i],
                        'size' => $file['size'][$i],
                        'type' => $file['type'][$i]
                    ];
                }
            }
        } elseif (!empty($file['name'])) {
            $filesToProcess[] = $file;
        }
    }
}

$allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif'];

foreach ($filesToProcess as $file) {
    if ($file['error'] !== UPLOAD_ERR_OK) continue;
    if (empty($file['tmp_name']) || !is_uploaded_file($file['tmp_name'])) continue;
    if ($file['size'] > 10 * 1024 * 1024) continue;

    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mimeType = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);

    if (!in_array($mimeType, $allowedTypes)) continue;

    $ext = match($mimeType) {
        'image/jpeg', 'image/jpg' => 'jpg',
        'image/png' => 'png',
        'image/gif' => 'gif',
        default => 'jpg'
    };

    $safeUserId = preg_replace('/[^a-zA-Z0-9]/', '', $user_id);
    $newFileName = "report_{$safeUserId}_" . uniqid() . ".$ext";
    $destination = $uploadDir . $newFileName;
    $relativePath = $relativeUploadDir . $newFileName;

    if (move_uploaded_file($file['tmp_name'], $destination)) {
        chmod($destination, 0644);
        $uploadedRelativePaths[] = $relativePath;
    }
}

$imagePathsJson = json_encode($uploadedRelativePaths);
$stmt = $conn->prepare("INSERT INTO daily_reports (user_id, report_text, image_paths) VALUES (?, ?, ?)");
$stmt->bind_param("sss", $user_id, $report_text, $imagePathsJson);

if ($stmt->execute()) {
    echo json_encode([
        "success" => true,
        "message" => "Report submitted successfully!",
        "data" => [
            "report_id" => $conn->insert_id,
            "user_id" => $user_id,
            "images_uploaded" => count($uploadedRelativePaths),
            "image_paths" => $uploadedRelativePaths
        ]
    ]);
} else {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "DB error: " . $stmt->error]);
}

$stmt->close();
$conn->close();
?>
