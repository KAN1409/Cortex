<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(int $status, array $payload): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function multipartField(string $boundary, string $name, string $value): string {
    return '--' . $boundary . "\r\n"
        . 'Content-Disposition: form-data; name="' . $name . '"' . "\r\n\r\n"
        . $value . "\r\n";
}

function multipartFile(string $boundary, string $name, string $filename, string $mime, string $bytes): string {
    $safe = str_replace(["\r", "\n", '"'], '_', $filename);
    return '--' . $boundary . "\r\n"
        . 'Content-Disposition: form-data; name="' . $name . '"; filename="' . $safe . '"' . "\r\n"
        . 'Content-Type: ' . $mime . "\r\n\r\n"
        . $bytes . "\r\n";
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['ok' => false, 'error' => 'POST required', 'retryable' => false]);
}

// Optional deployment-level Cortex token. Keep it in the server environment,
// never in this repository. For a public deployment, place this endpoint behind
// authenticated Cortex sessions/API gateway rather than exposing provider keys.
$requiredToken = trim((string) getenv('CORTEX_APP_TOKEN'));
if ($requiredToken !== '') {
    $auth = (string) ($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    $provided = str_starts_with($auth, 'Bearer ') ? substr($auth, 7) : '';
    if ($provided === '' || !hash_equals($requiredToken, $provided)) {
        respond(401, ['ok' => false, 'error' => 'Unauthorized', 'retryable' => false]);
    }
}

$apiKey = trim((string) getenv('OPENAI_API_KEY'));
if ($apiKey === '') {
    respond(500, ['ok' => false, 'error' => 'Server transcription provider is not configured', 'retryable' => false]);
}

if (!isset($_FILES['audio']) || !is_array($_FILES['audio'])) {
    respond(400, ['ok' => false, 'error' => 'Missing audio upload', 'retryable' => false]);
}

$audio = $_FILES['audio'];
$error = (int) ($audio['error'] ?? UPLOAD_ERR_NO_FILE);
if ($error !== UPLOAD_ERR_OK) {
    respond(400, ['ok' => false, 'error' => 'Audio upload failed with code ' . $error, 'retryable' => false]);
}

$tmp = (string) ($audio['tmp_name'] ?? '');
$name = basename((string) ($audio['name'] ?? 'cortex.wav'));
$size = (int) ($audio['size'] ?? 0);
if ($tmp === '' || !is_uploaded_file($tmp) || $size <= 0) {
    respond(400, ['ok' => false, 'error' => 'Invalid audio upload', 'retryable' => false]);
}
if ($size > 25 * 1024 * 1024) {
    respond(413, ['ok' => false, 'error' => 'Audio exceeds 25 MB', 'retryable' => false]);
}

$finfo = new finfo(FILEINFO_MIME_TYPE);
$mime = (string) ($finfo->file($tmp) ?: 'application/octet-stream');
$allowed = [
    'audio/wav', 'audio/x-wav', 'audio/mpeg', 'audio/mp4', 'audio/x-m4a',
    'audio/webm', 'video/webm', 'video/mp4', 'application/octet-stream'
];
if (!in_array($mime, $allowed, true)) {
    respond(415, ['ok' => false, 'error' => 'Unsupported audio type: ' . $mime, 'retryable' => false]);
}

$audioBytes = file_get_contents($tmp);
if ($audioBytes === false || $audioBytes === '') {
    respond(400, ['ok' => false, 'error' => 'Could not read uploaded audio', 'retryable' => false]);
}

$prompt = 'Verbatim transcription of Egyptian Arabic and English code-switching. '
    . 'Preserve Arabic speech in Arabic script and English speech in Latin letters exactly as spoken. '
    . 'Do not translate. Do not transliterate English into Arabic. '
    . 'Be especially strict at mid-sentence Arabic -> English -> Arabic transitions.';

// OpenAI documents array form-data parameters using repeated [] field names.
$boundary = '----CortexOpenAI' . bin2hex(random_bytes(12));
$requestBody = '';
$requestBody .= multipartField($boundary, 'model', 'gpt-transcribe');
$requestBody .= multipartFile($boundary, 'file', $name, $mime, $audioBytes);
$requestBody .= multipartField($boundary, 'prompt', $prompt);
$requestBody .= multipartField($boundary, 'languages[]', 'ar');
$requestBody .= multipartField($boundary, 'languages[]', 'en');
foreach (['Cortex', 'transcription', 'ASR', 'English', 'Arabic'] as $keyword) {
    $requestBody .= multipartField($boundary, 'keywords[]', $keyword);
}
$requestBody .= multipartField($boundary, 'response_format', 'json');
$requestBody .= '--' . $boundary . "--\r\n";

$ch = curl_init('https://api.openai.com/v1/audio/transcriptions');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 20,
    CURLOPT_TIMEOUT => 180,
    CURLOPT_HTTPHEADER => [
        'Authorization: Bearer ' . $apiKey,
        'Accept: application/json',
        'Content-Type: multipart/form-data; boundary=' . $boundary,
    ],
    CURLOPT_POSTFIELDS => $requestBody,
]);

$body = curl_exec($ch);
if ($body === false) {
    $message = curl_error($ch);
    curl_close($ch);
    respond(503, ['ok' => false, 'error' => 'Transcription provider unavailable: ' . $message, 'retryable' => true]);
}
$status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
curl_close($ch);

$provider = json_decode((string) $body, true);
if (!is_array($provider)) {
    respond(502, ['ok' => false, 'error' => 'Invalid provider response', 'retryable' => true]);
}
if ($status < 200 || $status >= 300) {
    $providerMessage = (string) ($provider['error']['message'] ?? ('Provider HTTP ' . $status));
    $retryable = $status === 408 || $status === 425 || $status === 429 || $status >= 500;
    respond($retryable ? 503 : 502, ['ok' => false, 'error' => $providerMessage, 'retryable' => $retryable]);
}

$text = trim((string) ($provider['text'] ?? ''));
if ($text === '') {
    respond(502, ['ok' => false, 'error' => 'Provider returned an empty transcript', 'retryable' => true]);
}

$durationMs = 0;
if (isset($provider['usage']['seconds']) && is_numeric($provider['usage']['seconds'])) {
    $durationMs = (int) round(((float) $provider['usage']['seconds']) * 1000.0);
}

$languageCodes = [];
$detected = $provider['languages'] ?? [];
if (is_array($detected)) {
    foreach ($detected as $entry) {
        if (is_array($entry) && isset($entry['code']) && is_string($entry['code'])) {
            $code = trim($entry['code']);
            if ($code !== '') $languageCodes[] = $code;
        } elseif (is_string($entry)) {
            $code = trim($entry);
            if ($code !== '') $languageCodes[] = $code;
        }
    }
}
$languageLabel = count($languageCodes) > 0
    ? implode('+', array_values(array_unique($languageCodes)))
    : 'ar-EG+en-codeswitch-auto';

respond(200, [
    'ok' => true,
    'transcript' => $text,
    'engine' => 'gpt-transcribe_cloud',
    'version' => 'cloud-v1',
    'language' => $languageLabel,
    'duration_ms' => $durationMs,
    // gpt-transcribe is used for accuracy/code-switching; timestamp segments are intentionally omitted.
    'segments' => [],
]);
