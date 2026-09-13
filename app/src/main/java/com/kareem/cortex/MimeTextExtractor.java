package com.kareem.cortex;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small transport-side MIME decoder for bridge verdict emails.
 * Supports text/plain, multipart/*, base64 and quoted-printable without pulling
 * a mail framework into Cortex. It is deliberately read-only and transport scoped.
 */
public final class MimeTextExtractor {
    private static final int MAX_DEPTH = 8;
    private MimeTextExtractor() {}

    public static String extractBestText(String rawMime) {
        if (rawMime == null || rawMime.isEmpty()) return "";
        Part p = parsePart(normalize(rawMime), 0);
        return p == null ? "" : p.bestText;
    }

    private static Part parsePart(String raw, int depth) {
        if (depth > MAX_DEPTH || raw == null) return new Part("", "");
        Split split = splitHeaders(raw);
        Map<String,String> headers = parseHeaders(split.headers);
        String contentType = header(headers, "content-type").toLowerCase(Locale.ROOT);
        String transfer = header(headers, "content-transfer-encoding").toLowerCase(Locale.ROOT);

        if (contentType.startsWith("multipart/")) {
            String boundary = parameter(header(headers, "content-type"), "boundary");
            if (!boundary.isEmpty()) {
                List<String> children = splitMultipart(split.body, boundary);
                String firstText = "";
                String firstAny = "";
                for (String childRaw : children) {
                    Part child = parsePart(childRaw, depth + 1);
                    if (child == null || child.bestText.isEmpty()) continue;
                    if (firstAny.isEmpty()) firstAny = child.bestText;
                    if (child.contentType.startsWith("text/plain")) {
                        firstText = child.bestText;
                        break;
                    }
                }
                return new Part(contentType, !firstText.isEmpty() ? firstText : firstAny);
            }
        }

        byte[] decoded = decodeTransfer(split.body, transfer);
        String charset = parameter(header(headers, "content-type"), "charset");
        String text;
        try {
            text = charset.isEmpty()
                    ? new String(decoded, StandardCharsets.UTF_8)
                    : new String(decoded, java.nio.charset.Charset.forName(stripQuotes(charset)));
        } catch (Throwable ignored) {
            text = new String(decoded, StandardCharsets.UTF_8);
        }
        return new Part(contentType, text.trim());
    }

    private static byte[] decodeTransfer(String body, String transfer) {
        String t = transfer == null ? "" : transfer.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.equals("base64")) {
                String compact = body.replaceAll("\\s+", "");
                return Base64.getMimeDecoder().decode(compact);
            }
            if (t.equals("quoted-printable")) return decodeQuotedPrintable(body);
        } catch (Throwable ignored) {
            // Fall through to raw UTF-8 bytes. Invalid MIME must not crash polling.
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeQuotedPrintable(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] src = value.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < src.length; i++) {
            int c = src[i] & 0xff;
            if (c == '=') {
                if (i + 1 < src.length && src[i + 1] == '\n') { i += 1; continue; }
                if (i + 2 < src.length && src[i + 1] == '\r' && src[i + 2] == '\n') { i += 2; continue; }
                if (i + 2 < src.length) {
                    int hi = hex(src[i + 1]);
                    int lo = hex(src[i + 2]);
                    if (hi >= 0 && lo >= 0) { out.write((hi << 4) | lo); i += 2; continue; }
                }
            }
            out.write(c);
        }
        return out.toByteArray();
    }

    private static int hex(byte b) {
        int c = b & 0xff;
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        return -1;
    }

    private static List<String> splitMultipart(String body, String boundary) {
        ArrayList<String> out = new ArrayList<>();
        String marker = "--" + stripQuotes(boundary);
        String end = marker + "--";
        String[] lines = normalize(body).split("\\n", -1);
        StringBuilder current = null;
        for (String line : lines) {
            if (line.equals(marker) || line.equals(end)) {
                if (current != null && current.length() > 0) out.add(current.toString());
                current = line.equals(end) ? null : new StringBuilder();
                if (line.equals(end)) break;
            } else if (current != null) {
                current.append(line).append('\n');
            }
        }
        if (current != null && current.length() > 0) out.add(current.toString());
        return out;
    }

    private static Split splitHeaders(String raw) {
        int p = raw.indexOf("\n\n");
        if (p < 0) return new Split("", raw);
        return new Split(raw.substring(0, p), raw.substring(p + 2));
    }

    private static Map<String,String> parseHeaders(String headerBlock) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        String current = null;
        for (String line : normalize(headerBlock).split("\\n", -1)) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && current != null) {
                out.put(current, out.get(current) + " " + line.trim());
                continue;
            }
            int c = line.indexOf(':');
            if (c <= 0) continue;
            current = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            out.put(current, line.substring(c + 1).trim());
        }
        return out;
    }

    private static String parameter(String header, String name) {
        if (header == null) return "";
        String[] parts = header.split(";");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            if (p.substring(0, eq).trim().equalsIgnoreCase(name)) return stripQuotes(p.substring(eq + 1).trim());
        }
        return "";
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String header(Map<String,String> headers, String name) {
        String v = headers.get(name.toLowerCase(Locale.ROOT));
        return v == null ? "" : v;
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class Split {
        final String headers;
        final String body;
        Split(String headers, String body) { this.headers = headers; this.body = body; }
    }

    private static final class Part {
        final String contentType;
        final String bestText;
        Part(String contentType, String bestText) { this.contentType = contentType == null ? "" : contentType; this.bestText = bestText == null ? "" : bestText; }
    }
}
