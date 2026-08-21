<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

final class ProviderFailure extends RuntimeException {
    public string $provider;
    public bool $retryable;
    public function __construct(string $provider, string $message, bool $retryable = true) {
        parent::__construct($message);
        $this->provider = $provider;
        $this->retryable = $retryable;
    }
}

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

function httpPost(string $url, array $headers, string $body, int $timeout = 180): array {
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 20,
        CURLOPT_TIMEOUT => $timeout,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_POSTFIELDS => $body,
    ]);
    $response = curl_exec($ch);
    if ($response === false) {
        $error = curl_error($ch);
        curl_close($ch);
        throw new RuntimeException($error === '' ? 'Network request failed' : $error);
    }
    $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    return [$status, (string) $response];
}

function b64url(string $bytes): string {
    return rtrim(strtr(base64_encode($bytes), '+/', '-_'), '=');
}

function googleAccessToken(): string {
    $direct = trim((string) getenv('GOOGLE_STT_ACCESS_TOKEN'));
    if ($direct !== '') return $direct;

    $raw = trim((string) getenv('GOOGLE_SERVICE_ACCOUNT_JSON'));
    if ($raw === '') throw new ProviderFailure('google_chirp_3', 'Google credentials are not configured', false);
    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        $maybe = base64_decode($raw, true);
        $decoded = $maybe === false ? null : json_decode($maybe, true);
    }
    if (!is_array($decoded)) throw new ProviderFailure('google_chirp_3', 'GOOGLE_SERVICE_ACCOUNT_JSON is invalid', false);

    $email = trim((string) ($decoded['client_email'] ?? ''));
    $privateKey = (string) ($decoded['private_key'] ?? '');
    if ($email === '' || $privateKey === '') throw new ProviderFailure('google_chirp_3', 'Google service account email/private key missing', false);

    $now = time();
    $header = b64url(json_encode(['alg' => 'RS256', 'typ' => 'JWT'], JSON_UNESCAPED_SLASHES));
    $claims = b64url(json_encode([
        'iss' => $email,
        'scope' => 'https://www.googleapis.com/auth/cloud-platform',
        'aud' => 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600,
    ], JSON_UNESCAPED_SLASHES));
    $unsigned = $header . '.' . $claims;
    $signature = '';
    if (!openssl_sign($unsigned, $signature, $privateKey, OPENSSL_ALGO_SHA256)) {
        throw new ProviderFailure('google_chirp_3', 'Could not sign Google service-account assertion', false);
    }
    $assertion = $unsigned . '.' . b64url($signature);
    $tokenBody = http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $assertion,
    ]);
    try {
        [$status, $body] = httpPost('https://oauth2.googleapis.com/token', [
            'Content-Type: application/x-www-form-urlencoded',
            'Accept: application/json',
        ], $tokenBody, 30);
    } catch (Throwable $e) {
        throw new ProviderFailure('google_chirp_3', 'Google OAuth unavailable: ' . $e->getMessage(), true);
    }
    $json = json_decode($body, true);
    $token = is_array($json) ? trim((string) ($json['access_token'] ?? '')) : '';
    if ($status < 200 || $status >= 300 || $token === '') {
        $message = is_array($json) ? (string) ($json['error_description'] ?? $json['error'] ?? ('OAuth HTTP ' . $status)) : ('OAuth HTTP ' . $status);
        throw new ProviderFailure('google_chirp_3', $message, $status >= 500 || $status === 429);
    }
    return $token;
}

function transcribeOpenAI(string $name, string $mime, string $audioBytes, string $prompt): array {
    $apiKey = trim((string) getenv('OPENAI_API_KEY'));
    if ($apiKey === '') throw new ProviderFailure('gpt-transcribe', 'OPENAI_API_KEY is not configured', false);

    $boundary = '----CortexPrimeOpenAI' . bin2hex(random_bytes(12));
    $requestBody = '';
    $requestBody .= multipartField($boundary, 'model', 'gpt-transcribe');
    $requestBody .= multipartFile($boundary, 'file', $name, $mime, $audioBytes);
    $requestBody .= multipartField($boundary, 'prompt', $prompt);
    $requestBody .= multipartField($boundary, 'languages[]', 'ar');
    $requestBody .= multipartField($boundary, 'languages[]', 'en');
    foreach (['Cortex Prime', 'transcription', 'ASR', 'English', 'Arabic'] as $keyword) {
        $requestBody .= multipartField($boundary, 'keywords[]', $keyword);
    }
    $requestBody .= multipartField($boundary, 'response_format', 'json');
    $requestBody .= '--' . $boundary . "--\r\n";

    try {
        [$status, $body] = httpPost('https://api.openai.com/v1/audio/transcriptions', [
            'Authorization: Bearer ' . $apiKey,
            'Accept: application/json',
            'Content-Type: multipart/form-data; boundary=' . $boundary,
        ], $requestBody);
    } catch (Throwable $e) {
        throw new ProviderFailure('gpt-transcribe', 'OpenAI unavailable: ' . $e->getMessage(), true);
    }

    $json = json_decode($body, true);
    if (!is_array($json)) throw new ProviderFailure('gpt-transcribe', 'OpenAI returned invalid JSON', true);
    if ($status < 200 || $status >= 300) {
        $message = (string) ($json['error']['message'] ?? ('OpenAI HTTP ' . $status));
        throw new ProviderFailure('gpt-transcribe', $message, $status === 408 || $status === 425 || $status === 429 || $status >= 500);
    }
    $text = trim((string) ($json['text'] ?? ''));
    if ($text === '') throw new ProviderFailure('gpt-transcribe', 'OpenAI returned an empty transcript', true);

    $codes = [];
    foreach (($json['languages'] ?? []) as $entry) {
        if (is_array($entry) && is_string($entry['code'] ?? null)) $codes[] = trim((string) $entry['code']);
        elseif (is_string($entry)) $codes[] = trim($entry);
    }
    $codes = array_values(array_unique(array_filter($codes)));
    $durationMs = isset($json['usage']['seconds']) && is_numeric($json['usage']['seconds'])
        ? (int) round(((float) $json['usage']['seconds']) * 1000.0)
        : 0;

    return [
        'transcript' => $text,
        'engine' => 'gpt-transcribe_cloud',
        'language' => $codes ? implode('+', $codes) : 'ar-EG+en-codeswitch-auto',
        'duration_ms' => $durationMs,
        'segments' => [],
    ];
}

function transcribeGoogle(string $audioBytes): array {
    $project = trim((string) getenv('GOOGLE_STT_PROJECT_ID'));
    if ($project === '') throw new ProviderFailure('google_chirp_3', 'GOOGLE_STT_PROJECT_ID is not configured', false);
    $location = trim((string) getenv('GOOGLE_STT_LOCATION'));
    if ($location === '') $location = 'us';
    $token = googleAccessToken();

    $endpoint = 'https://' . rawurlencode($location) . '-speech.googleapis.com/v2/projects/'
        . rawurlencode($project) . '/locations/' . rawurlencode($location) . '/recognizers/_:recognize';
    $payload = json_encode([
        'config' => [
            'autoDecodingConfig' => (object) [],
            'languageCodes' => ['ar-EG', 'en-US'],
            'model' => 'chirp_3',
            'features' => ['enableAutomaticPunctuation' => true],
        ],
        'content' => base64_encode($audioBytes),
    ], JSON_UNESCAPED_SLASHES);
    if ($payload === false) throw new ProviderFailure('google_chirp_3', 'Could not encode Google request', false);

    try {
        [$status, $body] = httpPost($endpoint, [
            'Authorization: Bearer ' . $token,
            'Accept: application/json',
            'Content-Type: application/json',
        ], $payload);
    } catch (Throwable $e) {
        throw new ProviderFailure('google_chirp_3', 'Google Chirp 3 unavailable: ' . $e->getMessage(), true);
    }

    $json = json_decode($body, true);
    if (!is_array($json)) throw new ProviderFailure('google_chirp_3', 'Google returned invalid JSON', true);
    if ($status < 200 || $status >= 300) {
        $message = (string) ($json['error']['message'] ?? ('Google HTTP ' . $status));
        throw new ProviderFailure('google_chirp_3', $message, $status === 408 || $status === 429 || $status >= 500);
    }

    $parts = [];
    $segments = [];
    $codes = [];
    $previousEnd = 0;
    foreach (($json['results'] ?? []) as $result) {
        if (!is_array($result)) continue;
        $alt = $result['alternatives'][0] ?? null;
        if (!is_array($alt)) continue;
        $text = trim((string) ($alt['transcript'] ?? ''));
        if ($text === '') continue;
        $parts[] = $text;
        $code = trim((string) ($result['languageCode'] ?? ''));
        if ($code !== '') $codes[] = $code;
        $endOffset = trim((string) ($result['resultEndOffset'] ?? ''));
        $endMs = $previousEnd;
        if ($endOffset !== '' && str_ends_with($endOffset, 's') && is_numeric(substr($endOffset, 0, -1))) {
            $endMs = (int) round(((float) substr($endOffset, 0, -1)) * 1000.0);
        }
        $confidence = isset($alt['confidence']) && is_numeric($alt['confidence']) ? (float) $alt['confidence'] : 0.0;
        $segments[] = ['start_ms' => $previousEnd, 'end_ms' => max($previousEnd, $endMs), 'text' => $text, 'confidence' => $confidence];
        $previousEnd = max($previousEnd, $endMs);
    }
    $text = trim(implode(' ', $parts));
    if ($text === '') throw new ProviderFailure('google_chirp_3', 'Google returned an empty transcript', true);
    $codes = array_values(array_unique(array_filter($codes)));

    return [
        'transcript' => $text,
        'engine' => 'google_chirp_3_cloud',
        'language' => $codes ? implode('+', $codes) : 'ar-EG+en-codeswitch-auto',
        'duration_ms' => $previousEnd,
        'segments' => $segments,
    ];
}

function transcribeAzure(string $name, string $mime, string $audioBytes): array {
    $key = trim((string) getenv('AZURE_SPEECH_KEY'));
    $resource = trim((string) getenv('AZURE_SPEECH_RESOURCE'));
    $endpoint = trim((string) getenv('AZURE_SPEECH_ENDPOINT'));
    if ($key === '') throw new ProviderFailure('azure_speech', 'AZURE_SPEECH_KEY is not configured', false);
    if ($endpoint === '') {
        if ($resource === '') throw new ProviderFailure('azure_speech', 'AZURE_SPEECH_RESOURCE/AZURE_SPEECH_ENDPOINT is not configured', false);
        $endpoint = 'https://' . $resource . '.cognitiveservices.azure.com';
    }
    $url = rtrim($endpoint, '/') . '/speechtotext/transcriptions:transcribe?api-version=2025-10-15';
    $boundary = '----CortexPrimeAzure' . bin2hex(random_bytes(12));
    $definition = json_encode(['locales' => ['ar-EG', 'en-US']], JSON_UNESCAPED_SLASHES);
    if ($definition === false) throw new ProviderFailure('azure_speech', 'Could not encode Azure request', false);
    $requestBody = multipartFile($boundary, 'audio', $name, $mime, $audioBytes)
        . multipartField($boundary, 'definition', $definition)
        . '--' . $boundary . "--\r\n";

    try {
        [$status, $body] = httpPost($url, [
            'Ocp-Apim-Subscription-Key: ' . $key,
            'Accept: application/json',
            'Content-Type: multipart/form-data; boundary=' . $boundary,
        ], $requestBody);
    } catch (Throwable $e) {
        throw new ProviderFailure('azure_speech', 'Azure Speech unavailable: ' . $e->getMessage(), true);
    }

    $json = json_decode($body, true);
    if (!is_array($json)) throw new ProviderFailure('azure_speech', 'Azure returned invalid JSON', true);
    if ($status < 200 || $status >= 300) {
        $message = (string) ($json['error']['message'] ?? $json['message'] ?? ('Azure HTTP ' . $status));
        throw new ProviderFailure('azure_speech', $message, $status === 408 || $status === 429 || $status >= 500);
    }

    $parts = [];
    foreach (($json['combinedPhrases'] ?? []) as $phrase) {
        if (is_array($phrase)) {
            $text = trim((string) ($phrase['text'] ?? ''));
            if ($text !== '') $parts[] = $text;
        }
    }
    if (!$parts) {
        foreach (($json['phrases'] ?? []) as $phrase) {
            if (is_array($phrase)) {
                $text = trim((string) ($phrase['text'] ?? ''));
                if ($text !== '') $parts[] = $text;
            }
        }
    }
    $text = trim(implode(' ', $parts));
    if ($text === '') throw new ProviderFailure('azure_speech', 'Azure returned an empty transcript', true);

    $segments = [];
    $codes = [];
    foreach (($json['phrases'] ?? []) as $phrase) {
        if (!is_array($phrase)) continue;
        $segmentText = trim((string) ($phrase['text'] ?? ''));
        if ($segmentText === '') continue;
        $start = isset($phrase['offsetMilliseconds']) && is_numeric($phrase['offsetMilliseconds']) ? (int) $phrase['offsetMilliseconds'] : 0;
        $duration = isset($phrase['durationMilliseconds']) && is_numeric($phrase['durationMilliseconds']) ? (int) $phrase['durationMilliseconds'] : 0;
        $locale = trim((string) ($phrase['locale'] ?? ''));
        if ($locale !== '') $codes[] = $locale;
        $segments[] = ['start_ms' => $start, 'end_ms' => $start + max(0, $duration), 'text' => $segmentText, 'confidence' => 0.0];
    }
    $codes = array_values(array_unique(array_filter($codes)));
    $durationMs = isset($json['durationMilliseconds']) && is_numeric($json['durationMilliseconds']) ? (int) $json['durationMilliseconds'] : 0;

    return [
        'transcript' => $text,
        'engine' => 'azure_speech_cloud',
        'language' => $codes ? implode('+', $codes) : 'ar-EG+en-codeswitch-auto',
        'duration_ms' => $durationMs,
        'segments' => $segments,
    ];
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['ok' => false, 'error' => 'POST required', 'retryable' => false]);
}

if (!isset($_FILES['audio']) || !is_array($_FILES['audio'])) {
    respond(400, ['ok' => false, 'error' => 'Missing audio upload', 'retryable' => false]);
}

$audio = $_FILES['audio'];
$uploadError = (int) ($audio['error'] ?? UPLOAD_ERR_NO_FILE);
if ($uploadError !== UPLOAD_ERR_OK) {
    respond(400, ['ok' => false, 'error' => 'Audio upload failed with code ' . $uploadError, 'retryable' => false]);
}

$tmp = (string) ($audio['tmp_name'] ?? '');
$name = basename((string) ($audio['name'] ?? 'cortex-prime.wav'));
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

$providers = [
    'gpt-transcribe' => fn() => transcribeOpenAI($name, $mime, $audioBytes, $prompt),
    'google_chirp_3' => fn() => transcribeGoogle($audioBytes),
    'azure_speech' => fn() => transcribeAzure($name, $mime, $audioBytes),
];

$errors = [];
$tried = [];
foreach ($providers as $provider => $run) {
    try {
        $tried[] = $provider;
        $result = $run();
        $result['ok'] = true;
        $result['version'] = 'cloud-v2';
        $result['provider'] = $provider;
        $result['provider_chain'] = $tried;
        $result['fallback_errors'] = $errors;
        respond(200, $result);
    } catch (ProviderFailure $e) {
        $errors[] = ['provider' => $e->provider, 'error' => $e->getMessage(), 'retryable' => $e->retryable];
    } catch (Throwable $e) {
        $errors[] = ['provider' => $provider, 'error' => $e->getMessage(), 'retryable' => true];
    }
}

respond(503, [
    'ok' => false,
    'error' => 'All configured cloud transcription providers failed',
    'retryable' => true,
    'provider_chain' => $tried,
    'provider_errors' => $errors,
]);
